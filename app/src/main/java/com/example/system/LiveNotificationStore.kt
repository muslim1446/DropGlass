package com.example.system

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.os.Build
import android.os.Bundle
import com.example.model.DetectedCutoutInfo
import com.example.model.DropNotification
import com.example.model.GlassOpticsConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

data class LiveSystemActions(
    val contentIntent: PendingIntent?,
    val actions: List<Notification.Action>,
    val replyAction: Notification.Action?,
    val remoteInput: RemoteInput?,
    val mediaController: MediaController? = null
)

enum class MediaCommand {
    PREVIOUS,
    PLAY_PAUSE,
    NEXT
}

object LiveNotificationStore {

    private val _activeSystemDrop = MutableStateFlow<DropNotification?>(null)
    val activeSystemDrop: StateFlow<DropNotification?> = _activeSystemDrop.asStateFlow()

    private val _queuedDrops = MutableStateFlow<List<DropNotification>>(emptyList())
    val queuedDrops: StateFlow<List<DropNotification>> = _queuedDrops.asStateFlow()

    private val _recentDrops = MutableStateFlow<List<DropNotification>>(emptyList())
    val recentDrops: StateFlow<List<DropNotification>> = _recentDrops.asStateFlow()

    private val _isListenerConnected = MutableStateFlow(false)
    val isListenerConnected: StateFlow<Boolean> = _isListenerConnected.asStateFlow()

    private val _isOverlayWindowShowing = MutableStateFlow(false)
    val isOverlayWindowShowing: StateFlow<Boolean> = _isOverlayWindowShowing.asStateFlow()

    private val _latestConfig = MutableStateFlow(GlassOpticsConfig())
    val latestConfig: StateFlow<GlassOpticsConfig> = _latestConfig.asStateFlow()

    private val _detectedCutout = MutableStateFlow(DetectedCutoutInfo())
    val detectedCutout: StateFlow<DetectedCutoutInfo> = _detectedCutout.asStateFlow()

    private val systemActionsMap = ConcurrentHashMap<String, LiveSystemActions>()

    @Volatile
    var listenerCallback: SystemNotificationBridge? = null

    interface SystemNotificationBridge {
        fun dismissFromSystem(key: String)
        fun snoozeFromSystem(key: String, durationMillis: Long)
    }

    fun updateConfig(config: GlassOpticsConfig) {
        _latestConfig.value = config
    }

    fun updateDetectedCutout(info: DetectedCutoutInfo) {
        _detectedCutout.value = info
    }

    fun setListenerConnected(connected: Boolean) {
        _isListenerConnected.value = connected
    }

    fun setOverlayShowing(showing: Boolean) {
        _isOverlayWindowShowing.value = showing
    }

    fun publishSystemDrop(drop: DropNotification, actions: LiveSystemActions?) {
        val lookupKey = drop.key ?: drop.id
        if (actions != null) {
            systemActionsMap[lookupKey] = actions
        }

        val current = _activeSystemDrop.value
        if (current != null && (current.key != null && current.key == drop.key || current.id == drop.id)) {
            // Update existing active droplet in place without restarting entry choreography
            _activeSystemDrop.value = drop.copy(
                id = current.id,
                updateCount = current.updateCount + 1
            )
        } else if (current != null && _isOverlayWindowShowing.value) {
            // Different notification while one is active: keep current or swap?
            // Put previous in queue so user never loses a burst of alerts, and surface the newest!
            _queuedDrops.update { q ->
                val withoutDup = q.filterNot { it.id == drop.id || (drop.key != null && it.key == drop.key) }
                (listOf(current) + withoutDup).take(6)
            }
            _activeSystemDrop.value = drop
        } else {
            _activeSystemDrop.value = drop
        }

        _recentDrops.update { list ->
            val filtered = list.filterNot { it.id == drop.id || (drop.key != null && it.key == drop.key) }
            (listOf(drop) + filtered).take(30)
        }
    }

    fun clearActiveSystemDrop(key: String? = null) {
        val current = _activeSystemDrop.value
        if (key == null || current?.key == key || current?.id == key) {
            val nextQueued = _queuedDrops.value.firstOrNull()
            if (nextQueued != null && key != null) {
                _queuedDrops.update { it.drop(1) }
                _activeSystemDrop.value = nextQueued
            } else {
                _activeSystemDrop.value = null
            }
        } else {
            _queuedDrops.update { q -> q.filterNot { it.key == key || it.id == key } }
        }
        if (key != null) {
            systemActionsMap.remove(key)
        }
    }

    fun cycleQueuedDrop() {
        val next = _queuedDrops.value.firstOrNull() ?: return
        val current = _activeSystemDrop.value
        _queuedDrops.update { q ->
            val tail = q.drop(1)
            if (current != null) tail + current else tail
        }
        _activeSystemDrop.value = next
    }

    fun openNotification(drop: DropNotification, context: Context): Boolean {
        val lookupKey = drop.key ?: drop.id
        val pending = systemActionsMap[lookupKey]?.contentIntent ?: return false
        return sendPendingIntentSafely(pending, context, fillIn = null)
    }

    fun triggerAction(drop: DropNotification, actionIndex: Int, context: Context): Boolean {
        val lookupKey = drop.key ?: drop.id
        val action = systemActionsMap[lookupKey]?.actions?.getOrNull(actionIndex) ?: return false
        val pending = action.actionIntent ?: return false
        return sendPendingIntentSafely(pending, context, fillIn = null)
    }

    fun sendInlineReply(
        drop: DropNotification,
        replyText: String,
        context: Context,
        actionIndex: Int = -1
    ): Boolean {
        val lookupKey = drop.key ?: drop.id
        val entry = systemActionsMap[lookupKey] ?: return false
        val targetAction = if (actionIndex >= 0) {
            entry.actions.getOrNull(actionIndex)?.takeIf { !it.remoteInputs.isNullOrEmpty() } ?: entry.replyAction
        } else {
            entry.replyAction
        } ?: return false

        val remoteInput = targetAction.remoteInputs?.firstOrNull() ?: entry.remoteInput ?: return false
        val intent = Intent()
        val bundle = Bundle().apply {
            putCharSequence(remoteInput.resultKey, replyText)
        }
        RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)
        return sendPendingIntentSafely(targetAction.actionIntent, context, fillIn = intent)
    }

    fun controlMedia(drop: DropNotification, command: MediaCommand): Boolean {
        val lookupKey = drop.key ?: drop.id
        val controller = systemActionsMap[lookupKey]?.mediaController ?: return false
        val controls = controller.transportControls
        when (command) {
            MediaCommand.PREVIOUS -> controls.skipToPrevious()
            MediaCommand.PLAY_PAUSE -> {
                if (drop.isMediaPlaying) controls.pause() else controls.play()
            }
            MediaCommand.NEXT -> controls.skipToNext()
        }
        return true
    }

    fun copyOtpToClipboard(code: String, context: Context): Boolean {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return false
        cm.setPrimaryClip(ClipData.newPlainText("Verification Code", code))
        return true
    }

    fun snoozeNotification(drop: DropNotification, durationMillis: Long = 15 * 60 * 1000L) {
        val key = drop.key
        if (key != null) {
            listenerCallback?.snoozeFromSystem(key, durationMillis)
        }
        clearActiveSystemDrop(key ?: drop.id)
    }

    fun dismissNotification(drop: DropNotification) {
        val key = drop.key
        if (key != null) {
            listenerCallback?.dismissFromSystem(key)
        }
        clearActiveSystemDrop(key ?: drop.id)
    }

    private fun sendPendingIntentSafely(
        pendingIntent: PendingIntent,
        context: Context,
        fillIn: Intent?
    ): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val options = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                    .toBundle()
                if (fillIn != null) {
                    pendingIntent.send(context, 0, fillIn, null, null, null, options)
                } else {
                    pendingIntent.send(context, 0, null, null, null, null, options)
                }
            } else {
                if (fillIn != null) {
                    pendingIntent.send(context, 0, fillIn)
                } else {
                    pendingIntent.send()
                }
            }
            true
        } catch (_: Throwable) {
            runCatching {
                if (fillIn != null) pendingIntent.send(context, 0, fillIn) else pendingIntent.send()
                true
            }.getOrDefault(false)
        }
    }
}
