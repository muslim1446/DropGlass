package com.example.model

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable

enum class DropStage(val label: String, val index: Int) {
    CAMERA_SEED("Lens Seed", 0),
    SMALL_DROP("Small Drop", 1),
    MEDIUM_PILL("Medium Pill", 2),
    BIGGEST_GLASS("Full Glass", 3)
}

enum class GlassStyle(val displayName: String, val subtitle: String) {
    CRYSTAL_CLEAR("Clear", "Refractive crystal that flips for light or dark backdrops"),
    OBSIDIAN_SMOKE("Smoked", "Deep translucent glass for high contrast anywhere"),
    IRIDESCENT_PRISM("Prism", "Chromatic dispersion with spectral edge shimmer"),
    COLOROS_ADAPTIVE("Tinted", "Subsurface color bleed from each notification")
}

enum class CameraPreset(val label: String) {
    OPPO_CPH2773("OPPO CPH2773"),
    HARDWARE_AUTO("Auto Cutout"),
    CUSTOM("Manual Tune")
}

@Immutable
data class GlassOpticsConfig(
    val preset: CameraPreset = CameraPreset.OPPO_CPH2773,
    val cameraOffsetXdp: Float = 0f,
    val cameraTopMarginDp: Float = 12f,
    val cameraRadiusDp: Float = 11.5f,
    val dropGapDp: Float = 7f,
    val glassStyle: GlassStyle = GlassStyle.CRYSTAL_CLEAR,
    val refractionIntensity: Float = 0.92f,
    val blurRadiusDp: Float = 28f,
    val specularRimIntensity: Float = 0.95f,
    val viscositySpeed: Float = 1.0f,
    val autoDismissSeconds: Float = 5.0f,
    val enableSystemOverlay: Boolean = true,
    val replaceSystemHeadsUp: Boolean = true,
    val suppressOngoing: Boolean = true,
    val avoidSystemBanners: Boolean = true,
    val useSystemDynamicColor: Boolean = true,
    val adaptiveContrastBlur: Boolean = true,
    val blockedPackages: Set<String> = emptySet()
) {
    companion object {
        fun oppoCph2773Default(current: GlassOpticsConfig = GlassOpticsConfig()): GlassOpticsConfig =
            current.copy(
                preset = CameraPreset.OPPO_CPH2773,
                cameraOffsetXdp = 0f,
                cameraTopMarginDp = 12f,
                cameraRadiusDp = 11.5f,
                dropGapDp = 7f
            )
    }
}

@Immutable
data class DetectedCutoutInfo(
    val hasHardwareCutout: Boolean = false,
    val centerXdp: Float = 0f,
    val topDp: Float = 12f,
    val widthDp: Float = 23f,
    val heightDp: Float = 23f,
    val statusBarHeightDp: Float = 38f,
    val deviceModel: String = android.os.Build.MODEL.orEmpty(),
    val screenWidthDp: Float = 0f,
    val screenHeightDp: Float = 0f
)

enum class DropCategory {
    MESSAGE,
    CALL,
    MEDIA,
    DELIVERY,
    OTP,
    SYSTEM
}

enum class MediaCommandAction {
    PREVIOUS,
    PLAY_PAUSE,
    NEXT
}

enum class ActionSemantic {
    REPLY,
    MARK_READ,
    ARCHIVE,
    DELETE,
    ANSWER,
    DECLINE,
    MEDIA_PREV,
    MEDIA_PLAY_PAUSE,
    MEDIA_NEXT,
    TRACK_ORDER,
    COPY_OTP,
    GENERIC
}

@Immutable
data class DropAction(
    val index: Int,
    val title: String,
    val isReply: Boolean = false,
    val semantic: ActionSemantic = ActionSemantic.GENERIC
)

@Immutable
data class DropProgressInfo(
    val progress: Int = 0,
    val max: Int = 100,
    val indeterminate: Boolean = false,
    val milestoneLabels: List<String> = emptyList(),
    val activeMilestoneIndex: Int = -1,
    val segmentColors: List<Int> = emptyList(),
    val etaLabel: String? = null,
    val detailLabel: String? = null,
    val showPercentage: Boolean = true
) {
    val fraction: Float
        get() = if (max > 0) (progress.toFloat() / max.toFloat()).coerceIn(0f, 1f) else 0f
}

@Immutable
data class DropMessageLine(
    val sender: String,
    val text: String,
    val timestampMillis: Long = 0L
)

@Immutable
data class DropNotification(
    val id: String,
    val key: String? = null,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val subText: String? = null,
    val conversationTitle: String? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
    val usesChronometer: Boolean = false,
    val chronometerBaseMillis: Long = 0L,
    val isChronometerCountDown: Boolean = false,
    val category: DropCategory = DropCategory.MESSAGE,
    val accentColorArgb: Int = 0xFFDCE3EC.toInt(),
    val isLiveSystem: Boolean = false,
    val hasReplyAction: Boolean = false,
    val replyLabel: String? = null,
    val replyActionIndex: Int = -1,
    val actions: List<DropAction> = emptyList(),
    val progressInfo: DropProgressInfo? = null,
    val messageLines: List<DropMessageLine> = emptyList(),
    val detectedOtpCode: String? = null,
    val appIconBitmap: Bitmap? = null,
    val largePictureBitmap: Bitmap? = null,
    val isMediaPlaying: Boolean = false,
    val hasMediaSession: Boolean = false,
    val updateCount: Int = 0
)

@Immutable
data class AppFilterItem(
    val packageName: String,
    val appName: String,
    val isEnabled: Boolean,
    val lastSeenMillis: Long
)
