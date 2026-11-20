package com.example.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.OpticsPreferencesRepository
import com.example.model.ActionSemantic
import com.example.model.CameraPreset
import com.example.model.DetectedCutoutInfo
import com.example.model.DropAction
import com.example.model.DropCategory
import com.example.model.DropMessageLine
import com.example.model.DropNotification
import com.example.model.DropProgressInfo
import com.example.model.DropStage
import com.example.model.GlassOpticsConfig
import com.example.model.GlassStyle
import com.example.model.MediaCommandAction
import com.example.system.LiquidOverlayService
import com.example.system.LiveNotificationStore
import com.example.system.SystemBridgeHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class StudioTab {
    STAGE,
    OPTICS,
    BRIDGE
}

enum class RealOsAlertType(val label: String) {
    MESSAGE("Message"),
    DELIVERY("Delivery"),
    OTP("2FA Code"),
    MEDIA("Media")
}

data class StudioUiState(
    val selectedTab: StudioTab = StudioTab.STAGE,
    val config: GlassOpticsConfig = GlassOpticsConfig(),
    val detectedCutout: DetectedCutoutInfo = DetectedCutoutInfo(),
    val previewDrop: DropNotification? = null,
    val forcedStage: DropStage? = null,
    val isNotificationAccessGranted: Boolean = false,
    val isOverlayPermissionGranted: Boolean = false,
    val isPostNotificationsGranted: Boolean = true,
    val isListenerConnected: Boolean = false,
    val recentDrops: List<DropNotification> = emptyList(),
    val feedbackBanner: String? = null,
    val lastOverlayError: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = OpticsPreferencesRepository(application.applicationContext)

    private val _selectedTab = MutableStateFlow(StudioTab.STAGE)
    private val _previewDrop = MutableStateFlow<DropNotification?>(sampleDrops().first())
    private val _forcedStage = MutableStateFlow<DropStage?>(null)
    private val _permissions = MutableStateFlow(PermissionSnapshot())
    private val _feedbackBanner = MutableStateFlow<String?>(null)

    private val baseFlow = combine(
        _selectedTab,
        repository.configFlow,
        LiveNotificationStore.detectedCutout,
        _previewDrop,
        _forcedStage
    ) { tab, config, cutout, preview, forced ->
        LiveNotificationStore.updateConfig(config)
        StudioUiState(
            selectedTab = tab,
            config = config,
            detectedCutout = cutout,
            previewDrop = preview,
            forcedStage = forced
        )
    }

    private val statusFlow = combine(
        _permissions,
        LiveNotificationStore.isListenerConnected,
        LiveNotificationStore.recentDrops,
        _feedbackBanner,
        LiveNotificationStore.lastOverlayError
    ) { perms, listenerConnected, recent, feedback, overlayErr ->
        StatusTuple(perms, listenerConnected, recent, feedback, overlayErr)
    }

    val uiState: StateFlow<StudioUiState> = combine(
        baseFlow,
        statusFlow
    ) { state, status ->
        state.copy(
            isNotificationAccessGranted = status.perms.notificationAccess,
            isOverlayPermissionGranted = status.perms.overlayAccess,
            isPostNotificationsGranted = status.perms.postNotifications,
            isListenerConnected = status.listenerConnected,
            recentDrops = status.recent,
            feedbackBanner = status.feedback,
            lastOverlayError = status.overlayError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StudioUiState(previewDrop = sampleDrops().first())
    )

    init {
        refreshPermissions()
    }

    fun selectTab(tab: StudioTab) {
        _selectedTab.value = tab
    }

    fun refreshPermissions() {
        val ctx = getApplication<Application>()
        val notifAccess = SystemBridgeHelper.isNotificationListenerEnabled(ctx)
        val overlayAccess = SystemBridgeHelper.canDrawOverlays(ctx)
        val postGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        _permissions.value = PermissionSnapshot(
            notificationAccess = notifAccess,
            overlayAccess = overlayAccess,
            postNotifications = postGranted
        )
        if (notifAccess && !LiveNotificationStore.isListenerConnected.value) {
            SystemBridgeHelper.requestListenerRebind(ctx)
        }
    }

    fun reconnectListener() {
        val ctx = getApplication<Application>()
        refreshPermissions()
        if (_permissions.value.notificationAccess) {
            SystemBridgeHelper.requestListenerRebind(ctx)
            _feedbackBanner.value = "Requested OS notification listener rebind"
        } else {
            SystemBridgeHelper.openNotificationListenerSettings(ctx)
        }
    }

    fun updateDetectedCutout(info: DetectedCutoutInfo) {
        LiveNotificationStore.updateDetectedCutout(info)
    }

    fun setForcedStage(stage: DropStage?) {
        _forcedStage.value = stage
        if (_previewDrop.value == null && stage != null) {
            _previewDrop.value = sampleDrops().first().copy(
                id = "inspect_${System.currentTimeMillis()}"
            )
        }
    }

    fun triggerPreviewPreset(index: Int) {
        val presets = sampleDrops()
        val chosen = presets[index.coerceIn(0, presets.lastIndex)]
        _forcedStage.value = null
        _previewDrop.value = chosen.copy(
            id = "preview_${chosen.packageName}_${System.currentTimeMillis()}",
            timestampMillis = System.currentTimeMillis()
        )
    }

    fun handlePreviewReply(drop: DropNotification, replyText: String): Boolean {
        val clean = replyText.trim()
        if (clean.isEmpty()) return false

        val ctx = getApplication<Application>()
        if (drop.isLiveSystem) {
            val sent = LiveNotificationStore.sendInlineReply(
                drop = drop,
                replyText = clean,
                context = ctx,
                actionIndex = drop.replyActionIndex
            )
            if (!sent) {
                _feedbackBanner.value = "This alert doesn't provide an inline reply action"
                return false
            }
        }

        // Keep the card visible and append the message so the reply is
        // confirmed in place instead of silently making the drop disappear.
        val youLine = DropMessageLine("You", clean, System.currentTimeMillis())
        _previewDrop.value = drop.copy(
            text = clean,
            messageLines = (drop.messageLines + youLine).takeLast(3),
            updateCount = drop.updateCount + 1
        )
        _feedbackBanner.value = "Reply sent"
        return true
    }

    fun handlePreviewOpen(drop: DropNotification) {
        val ctx = getApplication<Application>()
        if (drop.isLiveSystem && LiveNotificationStore.openNotification(drop, ctx)) {
            _feedbackBanner.value = "Opened ${drop.appName}"
        } else {
            _feedbackBanner.value = "This is a preview; no source app is attached"
        }
    }

    fun handlePreviewAction(drop: DropNotification, actionIndex: Int) {
        val ctx = getApplication<Application>()
        val action = drop.actions.getOrNull(actionIndex)
        if (drop.isLiveSystem) {
            LiveNotificationStore.triggerAction(drop, actionIndex, ctx)
            _feedbackBanner.value = "Executed system action: ${action?.title ?: "Action"}"
            return
        }

        // Walk the sample delivery forward, never backwards.
        if (drop.category == DropCategory.DELIVERY && action?.semantic == ActionSemantic.TRACK_ORDER) {
            val currentStep = (drop.progressInfo?.activeMilestoneIndex ?: 1).coerceIn(0, 3)
            if (currentStep >= 3) {
                _feedbackBanner.value = "This order is already delivered"
                return
            }
            val nextStep = currentStep + 1
            val pcts = listOf(25, 55, 80, 100)
            val etas = listOf("14 min", "8 min", "2 min", "Delivered")
            val texts = listOf(
                "Restaurant received your order",
                "Courier picked up your order",
                "Courier is 220 m away on Orchard Blvd",
                "Order arrived at the lobby reception"
            )
            _previewDrop.value = drop.copy(
                title = if (nextStep == 3) "Delivery · Delivered" else "Delivery · ${etas[nextStep]} away",
                text = texts[nextStep],
                progressInfo = drop.progressInfo?.copy(
                    progress = pcts[nextStep],
                    activeMilestoneIndex = nextStep,
                    etaLabel = if (nextStep == 3) "Delivered" else etas[nextStep]
                ),
                updateCount = drop.updateCount + 1
            )
            _feedbackBanner.value = if (nextStep == 3) "Order marked as delivered" else "Updated: ${texts[nextStep]}"
            return
        }

        _feedbackBanner.value = "${action?.title ?: "Action"} triggered"
        if (_forcedStage.value == null) {
            _previewDrop.value = null
        }
    }

    fun handlePreviewMediaControl(drop: DropNotification, cmd: MediaCommandAction) {
        if (drop.category != DropCategory.MEDIA) return
        when (cmd) {
            MediaCommandAction.PLAY_PAUSE -> {
                val nextPlaying = !drop.isMediaPlaying
                _previewDrop.value = drop.copy(
                    isMediaPlaying = nextPlaying,
                    subText = if (nextPlaying) "Playing" else "Paused",
                    updateCount = drop.updateCount + 1
                )
            }
            MediaCommandAction.NEXT -> {
                _previewDrop.value = drop.copy(
                    title = "Glass Prism",
                    text = "Aether · Spatial Mix",
                    isMediaPlaying = true,
                    updateCount = drop.updateCount + 1
                )
            }
            MediaCommandAction.PREVIOUS -> {
                _previewDrop.value = drop.copy(
                    title = "Midnight Refraction",
                    text = "Solstice · Lossless 24-bit",
                    isMediaPlaying = true,
                    updateCount = drop.updateCount + 1
                )
            }
        }
    }

    fun handleCopyOtp(code: String) {
        val ctx = getApplication<Application>()
        if (LiveNotificationStore.copyOtpToClipboard(code, ctx)) {
            _feedbackBanner.value = "Copied verification code $code to clipboard"
        }
    }

    fun dismissPreviewDrop() {
        if (_forcedStage.value == null) {
            _previewDrop.value = null
        }
    }

    fun showFeedback(message: String) {
        _feedbackBanner.value = message
    }

    fun clearFeedback() {
        _feedbackBanner.value = null
    }

    fun replayOnSystemOverlay(drop: DropNotification) {
        val ctx = getApplication<Application>()
        if (SystemBridgeHelper.canDrawOverlays(ctx)) {
            LiveNotificationStore.publishSystemDrop(drop.copy(id = "replay_${System.currentTimeMillis()}"), null)
            LiquidOverlayService.showCurrentDrop(ctx)
            _feedbackBanner.value = "Launched Liquid Glass overlay below selfie lens"
        } else {
            _forcedStage.value = null
            _previewDrop.value = drop.copy(id = "replay_${System.currentTimeMillis()}")
            _feedbackBanner.value = "Previewing in Stage (grant Display Over Apps for system-wide popup)"
        }
    }

    fun updateConfig(transform: (GlassOpticsConfig) -> GlassOpticsConfig) {
        viewModelScope.launch {
            repository.updateConfig(transform)
        }
    }

    fun applyOppoCph2773Preset() {
        updateConfig { current ->
            GlassOpticsConfig.oppoCph2773Default(current)
        }
        _feedbackBanner.value = "Calibrated to OPPO CPH2773 center punch-hole optics"
    }

    fun applyHardwareCutoutPreset() {
        val detected = LiveNotificationStore.detectedCutout.value
        updateConfig { current ->
            current.copy(
                preset = CameraPreset.HARDWARE_AUTO,
                cameraOffsetXdp = detected.centerXdp,
                cameraTopMarginDp = detected.topDp.coerceIn(4f, 28f),
                cameraRadiusDp = (detected.widthDp / 2f).coerceIn(8f, 18f)
            )
        }
        _feedbackBanner.value = if (detected.hasHardwareCutout) {
            "Locked to hardware DisplayCutout (${detected.widthDp.toInt()}×${detected.heightDp.toInt()}dp)"
        } else {
            "No physical cutout reported by emulator; using CPH2773 optical center"
        }
    }

    fun setGlassStyle(style: GlassStyle) {
        updateConfig { it.copy(glassStyle = style) }
    }

    fun togglePackageBlocked(packageName: String) {
        updateConfig { current ->
            val next = current.blockedPackages.toMutableSet()
            if (!next.add(packageName)) {
                next.remove(packageName)
            }
            current.copy(blockedPackages = next)
        }
    }

    fun sendLiveTestNotification(type: RealOsAlertType = RealOsAlertType.MESSAGE) {
        val ctx = getApplication<Application>()
        refreshPermissions()
        val perms = _permissions.value
        if (!perms.postNotifications) {
            _feedbackBanner.value = "Grant Alert Permission to post real OS notifications"
            return
        }
        when (type) {
            RealOsAlertType.MESSAGE -> SystemBridgeHelper.postTestSystemNotification(ctx)
            RealOsAlertType.DELIVERY -> SystemBridgeHelper.postTestDeliveryNotification(ctx, step = 1)
            RealOsAlertType.OTP -> SystemBridgeHelper.postTestOtpNotification(ctx)
            RealOsAlertType.MEDIA -> SystemBridgeHelper.postTestMediaNotification(ctx, isPlaying = true)
        }

        if (perms.notificationAccess && perms.overlayAccess) {
            _feedbackBanner.value = "Posted real OS ${type.label} alert — intercepted below selfie camera"
        } else {
            val fallbackIndex = when (type) {
                RealOsAlertType.MESSAGE -> 0
                RealOsAlertType.DELIVERY -> 1
                RealOsAlertType.MEDIA -> 2
                RealOsAlertType.OTP -> 3
            }
            triggerPreviewPreset(fallbackIndex)
            _feedbackBanner.value = "Posted real OS ${type.label} alert. Enable Bridge permissions for system-wide overlay."
        }
    }

    private data class PermissionSnapshot(
        val notificationAccess: Boolean = false,
        val overlayAccess: Boolean = false,
        val postNotifications: Boolean = true
    )

    private data class StatusTuple(
        val perms: PermissionSnapshot,
        val listenerConnected: Boolean,
        val recent: List<DropNotification>,
        val feedback: String?,
        val overlayError: String?
    )

    companion object {
        fun sampleDrops(): List<DropNotification> = listOf(
            DropNotification(
                id = "sample_msg",
                packageName = "com.whatsapp",
                appName = "Messages",
                title = "Maya Lin",
                text = "I'm downstairs — can you buzz me in?",
                messageLines = listOf(
                    DropMessageLine("Maya Lin", "Landed a little early."),
                    DropMessageLine("Maya Lin", "I'm downstairs — can you buzz me in?")
                ),
                category = DropCategory.MESSAGE,
                accentColorArgb = 0xFFAFC8D8.toInt(),
                hasReplyAction = true,
                replyLabel = "Reply",
                replyActionIndex = 0,
                actions = listOf(
                    DropAction(0, "Reply", isReply = true, semantic = ActionSemantic.REPLY),
                    DropAction(1, "Mark as Read", isReply = false, semantic = ActionSemantic.MARK_READ),
                    DropAction(2, "Archive", isReply = false, semantic = ActionSemantic.ARCHIVE)
                )
            ),
            DropNotification(
                id = "sample_delivery",
                packageName = "com.grab.delivery",
                appName = "Courier Live",
                title = "Delivery · 2 min away",
                text = "Courier is 220 m away on Orchard Blvd",
                subText = "2 min",
                category = DropCategory.DELIVERY,
                accentColorArgb = 0xFF9DC5AA.toInt(),
                progressInfo = DropProgressInfo(
                    progress = 80,
                    max = 100,
                    milestoneLabels = listOf("Ordered", "Picked Up", "En Route", "Arrived"),
                    activeMilestoneIndex = 2,
                    etaLabel = "2 min"
                ),
                actions = listOf(
                    DropAction(0, "Next Milestone", semantic = ActionSemantic.TRACK_ORDER),
                    DropAction(1, "Mark Delivered", semantic = ActionSemantic.MARK_READ)
                )
            ),
            DropNotification(
                id = "sample_media",
                packageName = "com.spotify.music",
                appName = "Music",
                title = "Midnight Refraction",
                text = "Solstice · Lossless 24-bit",
                subText = "Playing",
                category = DropCategory.MEDIA,
                accentColorArgb = 0xFF9DC8C2.toInt(),
                isMediaPlaying = true,
                actions = listOf(
                    DropAction(0, "Previous", semantic = ActionSemantic.MEDIA_PREV),
                    DropAction(1, "Pause", semantic = ActionSemantic.MEDIA_PLAY_PAUSE),
                    DropAction(2, "Next", semantic = ActionSemantic.MEDIA_NEXT)
                )
            ),
            DropNotification(
                id = "sample_otp",
                packageName = "com.google.android.apps.messaging",
                appName = "2FA Auth",
                title = "Security code",
                text = "Your verification code is 849201. It expires in 5 minutes.",
                category = DropCategory.OTP,
                accentColorArgb = 0xFFD8B98B.toInt(),
                detectedOtpCode = "849201",
                actions = listOf(
                    DropAction(0, "Mark as Read", semantic = ActionSemantic.MARK_READ)
                )
            ),
            DropNotification(
                id = "sample_call",
                packageName = "com.android.dialer",
                appName = "Phone",
                title = "Julian Mercer",
                text = "Incoming call · Mobile",
                category = DropCategory.CALL,
                accentColorArgb = 0xFF9DC5AA.toInt(),
                actions = listOf(
                    DropAction(0, "Answer", semantic = ActionSemantic.ANSWER),
                    DropAction(1, "Decline", semantic = ActionSemantic.DECLINE)
                )
            )
        )
    }
}
