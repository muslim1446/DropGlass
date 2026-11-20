package com.example.system

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.R
import com.example.model.MediaCommandAction
import com.example.ui.glass.WaterDropOverlayHost
import com.example.ui.theme.DropGlassTheme

class LiquidOverlayService : Service(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        windowManager = getSystemService(WindowManager::class.java)
        SystemBridgeHelper.ensureChannels(this)
        startForegroundCompat()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> {
                detachOverlay()
                LiveNotificationStore.setOverlayShowing(false)
                stopSelf()
            }
            else -> {
                if (SystemBridgeHelper.canDrawOverlays(this)) {
                    attachOverlayIfNeeded()
                    LiveNotificationStore.setOverlayShowing(true)
                } else {
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val notification = NotificationCompat.Builder(this, SystemBridgeHelper.OVERLAY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.overlay_channel_desc))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()
        startForeground(FOREGROUND_ID, notification)
    }

    private fun attachOverlayIfNeeded() {
        if (overlayView != null) return
        val wm = windowManager ?: return

        val detected = SystemBridgeHelper.detectHardwareCutoutFromService(this)
        if (detected.hasHardwareCutout) {
            LiveNotificationStore.updateDetectedCutout(detected)
        }

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) WindowManager.LayoutParams.FLAG_BLUR_BEHIND else 0)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 0
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blurBehindRadius = LiveNotificationStore.latestConfig.value.blurRadiusDp.toInt().coerceIn(8, 64)
            }
        }
        layoutParams = params

        val composeView = ComposeView(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setViewTreeLifecycleOwner(this@LiquidOverlayService)
            setViewTreeViewModelStoreOwner(this@LiquidOverlayService)
            setViewTreeSavedStateRegistryOwner(this@LiquidOverlayService)
            setContent {
                val activeDrop by LiveNotificationStore.activeSystemDrop.collectAsState()
                val queuedDrops by LiveNotificationStore.queuedDrops.collectAsState()
                val config by LiveNotificationStore.latestConfig.collectAsState()
                val cutout by LiveNotificationStore.detectedCutout.collectAsState()

                DropGlassTheme(useSystemDynamicColor = config.useSystemDynamicColor) {
                    WaterDropOverlayHost(
                        notification = activeDrop,
                        queuedCount = queuedDrops.size,
                        config = config,
                        detectedCutout = cutout,
                        onFocusRequested = { focusable ->
                            setOverlayFocusable(focusable)
                        },
                        onCycleQueue = {
                            LiveNotificationStore.cycleQueuedDrop()
                        },
                        onOpenNotification = { drop ->
                            LiveNotificationStore.openNotification(drop, this@LiquidOverlayService)
                            LiveNotificationStore.clearActiveSystemDrop(drop.key ?: drop.id)
                            hideIfEmpty()
                        },
                        onSendReply = { drop, replyText ->
                            LiveNotificationStore.sendInlineReply(
                                drop = drop,
                                replyText = replyText,
                                context = this@LiquidOverlayService,
                                actionIndex = drop.replyActionIndex
                            )
                            LiveNotificationStore.clearActiveSystemDrop(drop.key ?: drop.id)
                            hideIfEmpty()
                        },
                        onTriggerAction = { drop, index ->
                            val keepOpen = drop.actions.getOrNull(index)?.title?.contains("Milestone", ignoreCase = true) == true ||
                                drop.hasMediaSession || drop.category == com.example.model.DropCategory.MEDIA
                            LiveNotificationStore.triggerAction(drop, index, this@LiquidOverlayService)
                            if (!keepOpen) {
                                LiveNotificationStore.clearActiveSystemDrop(drop.key ?: drop.id)
                                hideIfEmpty()
                            }
                        },
                        onMediaControl = { drop, cmd ->
                            val mapped = when (cmd) {
                                MediaCommandAction.PREVIOUS -> MediaCommand.PREVIOUS
                                MediaCommandAction.PLAY_PAUSE -> MediaCommand.PLAY_PAUSE
                                MediaCommandAction.NEXT -> MediaCommand.NEXT
                            }
                            if (!LiveNotificationStore.controlMedia(drop, mapped)) {
                                // Fallback to matching action buttons if no direct MediaController token
                                val idx = drop.actions.indexOfFirst {
                                    when (cmd) {
                                        MediaCommandAction.PREVIOUS -> it.semantic == com.example.model.ActionSemantic.MEDIA_PREV
                                        MediaCommandAction.PLAY_PAUSE -> it.semantic == com.example.model.ActionSemantic.MEDIA_PLAY_PAUSE
                                        MediaCommandAction.NEXT -> it.semantic == com.example.model.ActionSemantic.MEDIA_NEXT
                                    }
                                }
                                if (idx >= 0) {
                                    LiveNotificationStore.triggerAction(drop, idx, this@LiquidOverlayService)
                                }
                            }
                        },
                        onCopyOtp = { _, code ->
                            LiveNotificationStore.copyOtpToClipboard(code, this@LiquidOverlayService)
                        },
                        onSnooze = { drop ->
                            LiveNotificationStore.snoozeNotification(drop)
                            hideIfEmpty()
                        },
                        onDismiss = { drop ->
                            if (drop != null) {
                                LiveNotificationStore.dismissNotification(drop)
                            } else {
                                LiveNotificationStore.clearActiveSystemDrop()
                            }
                            hideIfEmpty()
                        }
                    )
                }
            }
        }

        runCatching {
            wm.addView(composeView, params)
            overlayView = composeView
        }.onFailure {
            stopSelf()
        }
    }

    private fun setOverlayFocusable(focusable: Boolean) {
        val wm = windowManager ?: return
        val view = overlayView ?: return
        val params = layoutParams ?: return
        if (focusable) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        runCatching {
            wm.updateViewLayout(view, params)
            if (focusable) {
                view.requestFocus()
            }
        }
    }

    private fun hideIfEmpty() {
        if (LiveNotificationStore.activeSystemDrop.value == null) {
            detachOverlay()
            LiveNotificationStore.setOverlayShowing(false)
            stopSelf()
        }
    }

    private fun detachOverlay() {
        val view = overlayView ?: return
        runCatching {
            windowManager?.removeViewImmediate(view)
        }
        overlayView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        detachOverlay()
        LiveNotificationStore.setOverlayShowing(false)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val FOREGROUND_ID = 7731
        private const val ACTION_SHOW = "com.example.ACTION_SHOW_DROP"
        private const val ACTION_HIDE = "com.example.ACTION_HIDE_DROP"

        fun showCurrentDrop(context: Context) {
            val intent = Intent(context, LiquidOverlayService::class.java).apply {
                action = ACTION_SHOW
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun hideDrop(context: Context) {
            val intent = Intent(context, LiquidOverlayService::class.java).apply {
                action = ACTION_HIDE
            }
            context.startService(intent)
        }
    }
}
