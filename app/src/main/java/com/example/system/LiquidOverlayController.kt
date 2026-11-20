package com.example.system

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
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
import com.example.model.ActionSemantic
import com.example.model.DropCategory
import com.example.model.MediaCommandAction
import com.example.ui.glass.WaterDropOverlayHost
import com.example.ui.theme.DropGlassTheme

class LiquidOverlayController(
    private val context: Context,
    private val onAllDismissed: () -> Unit = {}
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private val displayUiContext: Context by lazy {
        val display = context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
        val windowCtx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && display != null) {
            runCatching {
                context.createDisplayContext(display)
                    .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
            }.getOrElse { context }
        } else if (display != null) {
            runCatching { context.createDisplayContext(display) }.getOrElse { context }
        } else {
            context
        }
        ContextThemeWrapper(windowCtx, R.style.Theme_MyApplication)
    }

    private val windowManager: WindowManager? by lazy {
        displayUiContext.getSystemService(WindowManager::class.java)
            ?: context.getSystemService(WindowManager::class.java)
    }

    private var overlayView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var viewTreeOwner: OverlayViewTreeOwner? = null
    private var wantsFocus = false

    fun showOrUpdate(): Boolean {
        if (!SystemBridgeHelper.canDrawOverlays(context)) {
            LiveNotificationStore.setOverlayError("Display Over Apps permission not granted")
            return false
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { attachOverlayIfNeeded() }
            return true
        }
        return attachOverlayIfNeeded()
    }

    fun hideIfEmpty() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hideIfEmpty() }
            return
        }
        if (LiveNotificationStore.activeSystemDrop.value == null) {
            detach()
            onAllDismissed()
        }
    }

    fun detach() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { detach() }
            return
        }
        wantsFocus = false
        val view = overlayView
        if (view != null) {
            runCatching {
                windowManager?.removeViewImmediate(view)
            }
            overlayView = null
        }
        layoutParams = null
        viewTreeOwner?.destroy()
        viewTreeOwner = null
        LiveNotificationStore.setOverlayShowing(false)
    }

    private fun attachOverlayIfNeeded(): Boolean {
        val existing = overlayView
        if (existing != null) {
            if (existing.visibility != View.VISIBLE) {
                existing.visibility = View.VISIBLE
            }
            LiveNotificationStore.setOverlayShowing(true)
            return true
        }
        val wm = windowManager ?: return false

        val detected = SystemBridgeHelper.detectHardwareCutoutFromService(displayUiContext)
        if (detected.hasHardwareCutout) {
            LiveNotificationStore.updateDetectedCutout(detected)
        }

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 0
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val owner = OverlayViewTreeOwner().also { it.start() }
        val composeView = ComposeView(displayUiContext).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            viewTreeObserver.addOnWindowFocusChangeListener { hasFocus ->
                if (hasFocus && wantsFocus) {
                    requestFocus()
                    val imm = displayUiContext.getSystemService(InputMethodManager::class.java)
                    imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                }
            }
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
                        avoidSystemBanners = config.avoidSystemBanners && !config.replaceSystemHeadsUp,
                        onFocusRequested = { focusable ->
                            setOverlayFocusable(focusable)
                        },
                        onCycleQueue = {
                            LiveNotificationStore.cycleQueuedDrop()
                        },
                        onOpenNotification = { drop ->
                            val opened = LiveNotificationStore.openNotification(drop, displayUiContext)
                            if (opened) {
                                LiveNotificationStore.clearActiveSystemDrop(drop.key ?: drop.id)
                                hideIfEmpty()
                            }
                        },
                        onSendReply = { drop, replyText ->
                            val sent = LiveNotificationStore.sendInlineReply(
                                drop = drop,
                                replyText = replyText,
                                context = displayUiContext,
                                actionIndex = drop.replyActionIndex
                            )
                            if (sent) {
                                LiveNotificationStore.clearActiveSystemDrop(drop.key ?: drop.id)
                                hideIfEmpty()
                            }
                            sent
                        },
                        onTriggerAction = { drop, index ->
                            val keepOpen = drop.actions.getOrNull(index)?.title?.contains("Milestone", ignoreCase = true) == true ||
                                drop.hasMediaSession || drop.category == DropCategory.MEDIA
                            val executed = LiveNotificationStore.triggerAction(drop, index, displayUiContext)
                            if (executed && !keepOpen) {
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
                                val idx = drop.actions.indexOfFirst {
                                    when (cmd) {
                                        MediaCommandAction.PREVIOUS -> it.semantic == ActionSemantic.MEDIA_PREV
                                        MediaCommandAction.PLAY_PAUSE -> it.semantic == ActionSemantic.MEDIA_PLAY_PAUSE
                                        MediaCommandAction.NEXT -> it.semantic == ActionSemantic.MEDIA_NEXT
                                    }
                                }
                                if (idx >= 0) {
                                    LiveNotificationStore.triggerAction(drop, idx, displayUiContext)
                                }
                            }
                        },
                        onCopyOtp = { _, code ->
                            LiveNotificationStore.copyOtpToClipboard(code, displayUiContext)
                        },
                        onSnooze = { drop ->
                            LiveNotificationStore.snoozeNotification(drop)
                            hideIfEmpty()
                        },
                        onAutoHide = { drop ->
                            LiveNotificationStore.clearActiveSystemDrop(drop?.key ?: drop?.id)
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

        return runCatching {
            wm.addView(composeView, params)
            overlayView = composeView
            layoutParams = params
            viewTreeOwner = owner
            LiveNotificationStore.setOverlayShowing(true)
            LiveNotificationStore.setOverlayError(null)
            true
        }.getOrElse { err ->
            owner.destroy()
            LiveNotificationStore.setOverlayShowing(false)
            LiveNotificationStore.setOverlayError(err.message ?: err::class.java.simpleName)
            false
        }
    }

    private fun setOverlayFocusable(focusable: Boolean) {
        wantsFocus = focusable
        val wm = windowManager ?: return
        val view = overlayView ?: return
        val params = layoutParams ?: return
        if (focusable) {
            params.flags = params.flags and
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv() and
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM.inv()
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        }
        runCatching {
            wm.updateViewLayout(view, params)
            if (focusable) {
                view.post {
                    view.requestFocus()
                }
            }
        }
    }

    private class OverlayViewTreeOwner :
        LifecycleOwner,
        ViewModelStoreOwner,
        SavedStateRegistryOwner {

        private val lifecycleRegistry = LifecycleRegistry(this)
        private val store = ViewModelStore()
        private val savedStateController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val viewModelStore: ViewModelStore get() = store
        override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

        fun start() {
            savedStateController.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun destroy() {
            if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
                if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                }
                if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                }
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            }
            store.clear()
        }
    }
}
