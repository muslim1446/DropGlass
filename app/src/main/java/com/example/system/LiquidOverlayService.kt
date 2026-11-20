package com.example.system

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
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
import com.example.MainActivity
import com.example.R
import com.example.model.MediaCommandAction
import com.example.ui.glass.WaterDropOverlayHost
import com.example.ui.theme.DropGlassTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LiquidOverlayService : Service(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        windowManager = getSystemService(WindowManager::class.java)
        SystemBridgeHelper.ensureChannels(this)
        startForegroundCompat()
        observeStore()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                detachOverlay()
                stopSelf()
            }
            ACTION_HIDE -> {
                detachOverlay()
                // Keep the engine alive so the next system notification can still
                // surface outside the app without a fresh foreground start.
            }
            else -> {
                if (!canHostOverlay()) {
                    stopSelf()
                } else if (LiveNotificationStore.activeSystemDrop.value != null) {
                    attachOverlayIfNeeded()
                }
            }
        }
        return START_STICKY
    }

    private fun canHostOverlay(): Boolean =
        SystemBridgeHelper.canDrawOverlays(this) &&
            LiveNotificationStore.latestConfig.value.enableSystemOverlay

    private fun observeStore() {
        serviceScope.launch {
            LiveNotificationStore.latestConfig.collect { config ->
                if (!config.enableSystemOverlay || !SystemBridgeHelper.canDrawOverlays(this@LiquidOverlayService)) {
                    detachOverlay()
                    stopSelf()
                } else if (LiveNotificationStore.activeSystemDrop.value != null) {
                    attachOverlayIfNeeded()
                }
            }
        }
        serviceScope.launch {
            LiveNotificationStore.activeSystemDrop.collect { drop ->
                if (drop == null) {
                    detachOverlay()
                } else if (canHostOverlay()) {
                    attachOverlayIfNeeded()
                }
            }
        }
    }

    private fun startForegroundCompat() {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, SystemBridgeHelper.OVERLAY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.overlay_channel_desc))
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                FOREGROUND_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(FOREGROUND_ID, notification)
        }
    }

    private fun attachOverlayIfNeeded() {
        if (overlayView != null) return
        if (LiveNotificationStore.activeSystemDrop.value == null) return
        if (!SystemBridgeHelper.canDrawOverlays(this)) return
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
                            val opened = LiveNotificationStore.openNotification(drop, this@LiquidOverlayService)
                            if (opened) {
                                LiveNotificationStore.clearActiveSystemDrop(drop.key ?: drop.id)
                            }
                        },
                        onSendReply = { drop, replyText ->
                            val sent = LiveNotificationStore.sendInlineReply(
                                drop = drop,
                                replyText = replyText,
                                context = this@LiquidOverlayService,
                                actionIndex = drop.replyActionIndex
                            )
                            if (sent) {
                                LiveNotificationStore.clearActiveSystemDrop(drop.key ?: drop.id)
                            }
                            sent
                        },
                        onTriggerAction = { drop, index ->
                            val keepOpen = drop.actions.getOrNull(index)?.title?.contains("Milestone", ignoreCase = true) == true ||
                                drop.hasMediaSession || drop.category == com.example.model.DropCategory.MEDIA
                            val executed = LiveNotificationStore.triggerAction(drop, index, this@LiquidOverlayService)
                            if (executed && !keepOpen) {
                                LiveNotificationStore.clearActiveSystemDrop(drop.key ?: drop.id)
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
                        },
                        onDismiss = { drop ->
                            if (drop != null) {
                                LiveNotificationStore.dismissNotification(drop)
                            } else {
                                LiveNotificationStore.clearActiveSystemDrop()
                            }
                        }
                    )
                }
            }
        }

        runCatching {
            wm.addView(composeView, params)
            overlayView = composeView
            LiveNotificationStore.setOverlayShowing(true)
        }.onFailure {
            overlayView = null
            LiveNotificationStore.setOverlayShowing(false)
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

    private fun detachOverlay() {
        val view = overlayView ?: run {
            LiveNotificationStore.setOverlayShowing(false)
            return
        }
        runCatching {
            windowManager?.removeViewImmediate(view)
        }
        overlayView = null
        layoutParams = null
        LiveNotificationStore.setOverlayShowing(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        detachOverlay()
        serviceScope.cancel()
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
        private const val ACTION_STOP = "com.example.ACTION_STOP_OVERLAY_ENGINE"

        @Volatile
        var isRunning: Boolean = false
            private set

        private fun startEngine(context: Context, action: String) {
            val intent = Intent(context, LiquidOverlayService::class.java).apply {
                this.action = action
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        /** Start (or reuse) the persistent overlay engine without showing an empty window. */
        fun ensureRunning(context: Context) {
            startEngine(context, ACTION_SHOW)
        }

        /** Wake the engine and surface the currently pending drop, if any. */
        fun showCurrentDrop(context: Context) {
            startEngine(context, ACTION_SHOW)
        }

        /** Hide the current drop but keep the engine alive for the next alert. */
        fun hideDrop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, LiquidOverlayService::class.java).apply { action = ACTION_HIDE }
                )
            }
        }

        /** Tear the engine down completely (overlay disabled or permission revoked). */
        fun stop(context: Context) {
            if (!isRunning) return
            runCatching {
                context.startService(
                    Intent(context, LiquidOverlayService::class.java).apply { action = ACTION_STOP }
                )
            }
        }
    }
}
