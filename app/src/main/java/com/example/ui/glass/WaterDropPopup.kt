package com.example.ui.glass

import android.os.Build
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.ActionSemantic
import com.example.model.CameraPreset
import com.example.model.DetectedCutoutInfo
import com.example.model.DropAction
import com.example.model.DropCategory
import com.example.model.DropNotification
import com.example.model.DropProgressInfo
import com.example.model.DropStage
import com.example.model.GlassOpticsConfig
import com.example.model.GlassStyle
import com.example.model.MediaCommandAction
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentRose
import com.example.ui.theme.CanvasBlack
import com.example.ui.theme.FrostPrimary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun WaterDropOverlayHost(
    notification: DropNotification?,
    config: GlassOpticsConfig,
    detectedCutout: DetectedCutoutInfo,
    modifier: Modifier = Modifier,
    queuedCount: Int = 0,
    forcedStage: DropStage? = null,
    showCameraRingGuide: Boolean = false,
    avoidSystemBanners: Boolean = false,
    onFocusRequested: (Boolean) -> Unit = {},
    onCycleQueue: () -> Unit = {},
    onOpenNotification: (DropNotification) -> Unit = {},
    onSendReply: (DropNotification, String) -> Boolean = { _, _ -> false },
    onTriggerAction: (DropNotification, Int) -> Unit = { _, _ -> },
    onMediaControl: (DropNotification, MediaCommandAction) -> Unit = { _, _ -> },
    onCopyOtp: (DropNotification, String) -> Unit = { _, _ -> },
    onSnooze: (DropNotification) -> Unit = {},
    onDismiss: (DropNotification?) -> Unit = {}
) {
    var currentStage by remember { mutableStateOf(DropStage.CAMERA_SEED) }
    var isReplying by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    var replyError by remember { mutableStateOf<String?>(null) }
    var copiedOtpFeedback by remember { mutableStateOf(false) }
    var isInteracting by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var fullContentHeightPx by remember { mutableIntStateOf(0) }

    val effectiveCamCenterXdp = when (config.preset) {
        CameraPreset.HARDWARE_AUTO -> if (detectedCutout.hasHardwareCutout) detectedCutout.centerXdp else config.cameraOffsetXdp
        else -> config.cameraOffsetXdp
    }
    val effectiveCamRadiusDp = when (config.preset) {
        CameraPreset.HARDWARE_AUTO -> if (detectedCutout.hasHardwareCutout) (detectedCutout.widthDp / 2f).coerceIn(8f, 18f) else config.cameraRadiusDp
        else -> config.cameraRadiusDp
    }
    val effectiveCamTopMarginDp = when (config.preset) {
        CameraPreset.HARDWARE_AUTO -> if (detectedCutout.hasHardwareCutout) detectedCutout.topDp else config.cameraTopMarginDp
        else -> config.cameraTopMarginDp
    }

    DisposableEffect(isReplying) {
        onFocusRequested(isReplying)
        onDispose { onFocusRequested(false) }
    }

    // Reset reply state only when notification.id changes (not on live progress updates)
    LaunchedEffect(notification?.id) {
        isReplying = false
        replyText = ""
        replyError = null
        copiedOtpFeedback = false
        dragOffsetY = 0f
        fullContentHeightPx = 0
    }

    // Camera seed -> drop -> pill -> full glass. Entry runs once per drop so
    // live progress updates do not replay the animation.
    LaunchedEffect(notification?.id, forcedStage, config.viscositySpeed) {
        if (notification == null) {
            currentStage = DropStage.CAMERA_SEED
            return@LaunchedEffect
        }

        if (forcedStage != null) {
            currentStage = forcedStage
            return@LaunchedEffect
        }

        val speedFactor = 1f / config.viscositySpeed.coerceIn(0.4f, 2.0f)
        currentStage = DropStage.CAMERA_SEED
        delay((65f * speedFactor).toLong())
        currentStage = DropStage.SMALL_DROP
        delay((185f * speedFactor).toLong())
        currentStage = DropStage.MEDIUM_PILL
        delay((340f * speedFactor).toLong())
        currentStage = DropStage.BIGGEST_GLASS
    }

    // Dwell timer. It restarts when the notification is updated in place, so a
    // live download or delivery stays visible while its numbers are moving.
    LaunchedEffect(
        notification?.id,
        notification?.updateCount,
        notification?.progressInfo?.progress,
        isReplying,
        isInteracting,
        forcedStage,
        config.viscositySpeed,
        config.autoDismissSeconds
    ) {
        if (notification == null || forcedStage != null) return@LaunchedEffect

        val speedFactor = 1f / config.viscositySpeed.coerceIn(0.4f, 2.0f)
        delay((620f * speedFactor).toLong())

        val baseMs = (config.autoDismissSeconds * 1000f).toLong().coerceAtLeast(2500L)
        val dismissMs = if (notification.progressInfo != null) maxOf(baseMs, 9000L) else baseMs
        var elapsed = 0L
        val step = 120L
        while (elapsed < dismissMs) {
            delay(step)
            if (!isInteracting && !isReplying) {
                elapsed += step
            }
        }

        currentStage = DropStage.MEDIUM_PILL
        delay((140f * speedFactor).toLong())
        currentStage = DropStage.SMALL_DROP
        delay((125f * speedFactor).toLong())
        currentStage = DropStage.CAMERA_SEED
        delay((90f * speedFactor).toLong())
        onDismiss(notification)
    }

    val activeStage = forcedStage ?: currentStage
    val springSpecDp = spring<Dp>(
        dampingRatio = 0.78f,
        stiffness = 360f * config.viscositySpeed.coerceIn(0.5f, 1.8f)
    )
    val springSpecFloat = spring<Float>(
        dampingRatio = 0.82f,
        stiffness = 420f * config.viscositySpeed.coerceIn(0.5f, 1.8f)
    )

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        val density = LocalDensity.current
        val availableWidthDp = maxWidth
        val maxCardWidth = (availableWidthDp - 24.dp).coerceIn(268.dp, 380.dp)
        val fullContentHeightDp = with(density) { fullContentHeightPx.toDp() }

        val targetDropWidth = when (activeStage) {
            DropStage.CAMERA_SEED -> (effectiveCamRadiusDp * 2f + 4f).dp
            DropStage.SMALL_DROP -> 52.dp
            DropStage.MEDIUM_PILL -> (maxCardWidth * 0.72f).coerceIn(220.dp, 276.dp)
            DropStage.BIGGEST_GLASS -> maxCardWidth
        }
        val targetDropHeight = when (activeStage) {
            DropStage.CAMERA_SEED -> (effectiveCamRadiusDp * 2f + 4f).dp
            DropStage.SMALL_DROP -> 52.dp
            DropStage.MEDIUM_PILL -> 56.dp
            DropStage.BIGGEST_GLASS -> if (fullContentHeightPx > 0) {
                fullContentHeightDp.coerceAtLeast(150.dp)
            } else {
                220.dp
            }
        }
        val targetCorner = when (activeStage) {
            DropStage.CAMERA_SEED -> 40.dp
            DropStage.SMALL_DROP -> 26.dp
            DropStage.MEDIUM_PILL -> 28.dp
            DropStage.BIGGEST_GLASS -> 24.dp
        }
        val camBottomDp = effectiveCamTopMarginDp + (effectiveCamRadiusDp * 2f)
        val rawDropTopDp = when (activeStage) {
            DropStage.CAMERA_SEED -> effectiveCamTopMarginDp - 2f
            DropStage.SMALL_DROP -> camBottomDp + (config.dropGapDp * 0.45f)
            DropStage.MEDIUM_PILL -> camBottomDp + config.dropGapDp
            DropStage.BIGGEST_GLASS -> camBottomDp + config.dropGapDp + 2f
        }
        // Stock heads-up banners own the very top of the screen and are drawn
        // above overlay windows. Keep the drop below that zone so the two never
        // stack on top of each other.
        val bannerClearanceDp = if (avoidSystemBanners) {
            detectedCutout.statusBarHeightDp.coerceIn(20f, 48f) + 104f
        } else {
            0f
        }
        val targetDropTopDp = if (activeStage == DropStage.CAMERA_SEED) {
            rawDropTopDp
        } else {
            maxOf(rawDropTopDp, bannerClearanceDp)
        }
        val targetNeckBlend = when (activeStage) {
            DropStage.CAMERA_SEED -> 26f
            DropStage.SMALL_DROP -> 28f
            DropStage.MEDIUM_PILL -> 12f
            DropStage.BIGGEST_GLASS -> 0f
        }

        val animWidth by animateDpAsState(targetDropWidth, springSpecDp, label = "dropWidth")
        val animHeight by animateDpAsState(targetDropHeight, springSpecDp, label = "dropHeight")
        val animCorner by animateDpAsState(targetCorner, springSpecDp, label = "dropCorner")
        val animTop by animateDpAsState(
            (targetDropTopDp + (dragOffsetY / density.density) * 0.35f).coerceAtLeast(effectiveCamTopMarginDp).dp,
            springSpecDp,
            label = "dropTop"
        )
        val animNeckBlend by animateFloatAsState(targetNeckBlend, springSpecFloat, label = "neckBlend")
        val contentAlpha by animateFloatAsState(
            targetValue = if (notification == null || activeStage == DropStage.CAMERA_SEED) 0f else 1f,
            animationSpec = tween(180, easing = FastOutSlowInEasing),
            label = "contentAlpha"
        )
        val fullContentAlpha by animateFloatAsState(
            targetValue = if (activeStage == DropStage.BIGGEST_GLASS) 1f else 0f,
            animationSpec = tween(durationMillis = 200, delayMillis = 90),
            label = "fullContentAlpha"
        )

        val pulseScale = remember { Animatable(1f) }
        LaunchedEffect(notification?.id) {
            if (notification != null) {
                pulseScale.snapTo(0.88f)
                pulseScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 420f))
            }
        }

        val accentColor = remember(notification?.accentColorArgb) {
            if (notification != null) Color(notification.accentColorArgb) else FrostPrimary
        }

        val measuredStageHeight = if (activeStage == DropStage.BIGGEST_GLASS) {
            maxOf(animHeight, fullContentHeightDp)
        } else {
            animHeight
        }
        val totalStageHeight = (animTop + measuredStageHeight + 18.dp).coerceAtLeast(92.dp)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalStageHeight)
                .pointerInput(notification?.id, activeStage) {
                    if (notification == null) return@pointerInput
                    detectVerticalDragGestures(
                        onDragStart = { isInteracting = true },
                        onDragEnd = {
                            isInteracting = false
                            if (dragOffsetY < -55f) {
                                onDismiss(notification)
                            } else if (dragOffsetY > 45f && activeStage != DropStage.BIGGEST_GLASS) {
                                currentStage = DropStage.BIGGEST_GLASS
                            }
                            dragOffsetY = 0f
                        },
                        onDragCancel = {
                            isInteracting = false
                            dragOffsetY = 0f
                        },
                        onVerticalDrag = { _, dragAmount ->
                            dragOffsetY = (dragOffsetY + dragAmount).coerceIn(-120f, 140f)
                        }
                    )
                },
            contentAlignment = Alignment.TopCenter
        ) {
            // Layer 1: optical glass + metaball meniscus
            Canvas(modifier = Modifier.fillMaxSize()) {
                val camCenterX = size.width / 2f + effectiveCamCenterXdp.dp.toPx()
                val camRadiusPx = effectiveCamRadiusDp.dp.toPx()
                val camCenterY = effectiveCamTopMarginDp.dp.toPx() + camRadiusPx

                val dropW = animWidth.toPx() * pulseScale.value
                val dropH = animHeight.toPx() * pulseScale.value
                val dropCenterX = camCenterX
                val dropTopPx = animTop.toPx()
                val dropCenterY = dropTopPx + dropH / 2f
                val cornerPx = animCorner.toPx()

                if (notification != null && (activeStage != DropStage.CAMERA_SEED || showCameraRingGuide)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val brush = LiquidGlassShaders.createLiquidDropBrush(
                            canvasWidth = size.width,
                            canvasHeight = size.height,
                            camCenterX = camCenterX,
                            camCenterY = camCenterY,
                            camRadius = camRadiusPx + 2.dp.toPx(),
                            dropCenterX = dropCenterX,
                            dropCenterY = dropCenterY,
                            dropHalfW = dropW / 2f,
                            dropHalfH = dropH / 2f,
                            dropCorner = cornerPx,
                            neckBlend = animNeckBlend.dp.toPx(),
                            specular = config.specularRimIntensity,
                            refraction = config.refractionIntensity,
                            accentColor = accentColor,
                            styleMode = config.glassStyle.ordinal
                        )
                        drawRect(brush = brush)
                    } else {
                        drawFallbackWaterDropGlass(
                            camCenter = Offset(camCenterX, camCenterY),
                            camRadius = camRadiusPx,
                            dropTopLeft = Offset(dropCenterX - dropW / 2f, dropTopPx),
                            dropSize = Size(dropW, dropH),
                            cornerRadius = cornerPx,
                            neckStrength = (animNeckBlend / 28f).coerceIn(0f, 1f),
                            accentColor = accentColor,
                            style = config.glassStyle,
                            specular = config.specularRimIntensity
                        )
                    }
                }

                if (showCameraRingGuide) {
                    drawCircle(
                        color = Color(0xFF030406),
                        radius = camRadiusPx,
                        center = Offset(camCenterX, camCenterY)
                    )
                    drawCircle(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.42f),
                                accentColor.copy(alpha = 0.30f),
                                Color.White.copy(alpha = 0.10f)
                            )
                        ),
                        radius = camRadiusPx + 1.5.dp.toPx(),
                        center = Offset(camCenterX, camCenterY),
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                }
            }

            val camOffsetX = effectiveCamCenterXdp.dp.roundToPx()

            // Layer 2a: compact stages
            if (notification != null &&
                (activeStage == DropStage.SMALL_DROP || activeStage == DropStage.MEDIUM_PILL)
            ) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(x = camOffsetX, y = animTop.roundToPx()) }
                        .size(animWidth, animHeight)
                        .alpha(contentAlpha)
                        .clip(RoundedCornerShape(animCorner))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = accentColor)
                        ) {
                            currentStage = DropStage.BIGGEST_GLASS
                        }
                        .testTag("liquid_drop_container"),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = activeStage,
                        transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(100)) },
                        label = "compactDropContent"
                    ) { stage ->
                        when (stage) {
                            DropStage.SMALL_DROP -> SmallDropContent(
                                notification = notification,
                                accentColor = accentColor
                            )
                            DropStage.MEDIUM_PILL -> MediumPillContent(
                                notification = notification,
                                accentColor = accentColor
                            )
                            else -> Unit
                        }
                    }
                }
            }

            // Layer 2b: full glass. The content is always laid out at its final
            // width and measured, so the glass grows to the content instead of
            // forcing text into a guessed fixed height.
            if (notification != null && activeStage == DropStage.BIGGEST_GLASS) {
                Column(
                    modifier = Modifier
                        .offset { IntOffset(x = camOffsetX, y = animTop.roundToPx()) }
                        .width(maxCardWidth)
                        .alpha(fullContentAlpha)
                        .onSizeChanged { fullContentHeightPx = it.height }
                        .clip(RoundedCornerShape(animCorner))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = accentColor)
                        ) {
                            if (!isReplying) {
                                if (notification.isLiveSystem) {
                                    onOpenNotification(notification)
                                } else {
                                    currentStage = DropStage.MEDIUM_PILL
                                }
                            }
                        }
                        .testTag("liquid_drop_container"),
                ) {
                    BiggestGlassContent(
                        notification = notification,
                        queuedCount = queuedCount,
                        accentColor = accentColor,
                        isReplying = isReplying,
                        replyText = replyText,
                        replyError = replyError,
                        copiedOtpFeedback = copiedOtpFeedback,
                        onReplyTextChange = { replyText = it },
                        onToggleReply = {
                            isReplying = !isReplying
                            isInteracting = isReplying
                            replyError = null
                        },
                        onSendReply = { textToSend ->
                            val clean = textToSend.trim()
                            if (clean.isNotEmpty()) {
                                val sent = onSendReply(notification, clean)
                                if (sent) {
                                    isReplying = false
                                    isInteracting = false
                                    replyText = ""
                                    replyError = null
                                } else {
                                    replyError = "This app doesn't accept inline replies"
                                }
                            }
                        },
                        onOpen = { onOpenNotification(notification) },
                        onTriggerAction = { idx -> onTriggerAction(notification, idx) },
                        onMediaControl = { cmd -> onMediaControl(notification, cmd) },
                        onCopyOtp = { code ->
                            copiedOtpFeedback = true
                            onCopyOtp(notification, code)
                        },
                        onSnooze = { onSnooze(notification) },
                        onCycleQueue = onCycleQueue,
                        onDismiss = { onDismiss(notification) }
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFallbackWaterDropGlass(
    camCenter: Offset,
    camRadius: Float,
    dropTopLeft: Offset,
    dropSize: Size,
    cornerRadius: Float,
    neckStrength: Float,
    accentColor: Color,
    style: GlassStyle,
    specular: Float
) {
    if (neckStrength > 0.05f) {
        val neckHalfTop = camRadius * (0.65f + 0.25f * neckStrength)
        val neckHalfBot = (dropSize.width * 0.28f).coerceAtMost(camRadius * 1.35f)
        val dropTopY = dropTopLeft.y + 4f
        val path = Path().apply {
            moveTo(camCenter.x - neckHalfTop, camCenter.y + camRadius * 0.3f)
            cubicTo(
                camCenter.x - neckHalfTop * 0.45f,
                (camCenter.y + dropTopY) * 0.5f,
                camCenter.x - neckHalfBot * 0.6f,
                dropTopY - 6f,
                camCenter.x - neckHalfBot,
                dropTopY + 4f
            )
            lineTo(camCenter.x + neckHalfBot, dropTopY + 4f)
            cubicTo(
                camCenter.x + neckHalfBot * 0.6f,
                dropTopY - 6f,
                camCenter.x + neckHalfTop * 0.45f,
                (camCenter.y + dropTopY) * 0.5f,
                camCenter.x + neckHalfTop,
                camCenter.y + camRadius * 0.3f
            )
            close()
        }
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xF20B0D11),
                    Color(0xE60E1115)
                ),
                startY = camCenter.y,
                endY = dropTopY + 8f
            )
        )
    }

    val bodyAlpha = when (style) {
        GlassStyle.CRYSTAL_CLEAR -> 0.88f
        GlassStyle.OBSIDIAN_SMOKE -> 0.94f
        GlassStyle.IRIDESCENT_PRISM -> 0.90f
        GlassStyle.COLOROS_ADAPTIVE -> 0.90f
    }
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF14171C).copy(alpha = bodyAlpha),
                Color(0xFF0A0C0F).copy(alpha = bodyAlpha)
            ),
            startY = dropTopLeft.y,
            endY = dropTopLeft.y + dropSize.height
        ),
        topLeft = dropTopLeft,
        size = dropSize,
        cornerRadius = CornerRadius(cornerRadius, cornerRadius)
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.48f * specular),
                accentColor.copy(alpha = 0.30f * specular),
                Color.White.copy(alpha = 0.10f * specular)
            ),
            start = dropTopLeft,
            end = Offset(dropTopLeft.x + dropSize.width, dropTopLeft.y + dropSize.height)
        ),
        topLeft = dropTopLeft,
        size = dropSize,
        cornerRadius = CornerRadius(cornerRadius, cornerRadius),
        style = Stroke(width = 1.2.dp.toPx())
    )
}

@Composable
private fun SmallDropContent(
    notification: DropNotification,
    accentColor: Color
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        NotificationGlyphOrb(
            notification = notification,
            accentColor = accentColor,
            orbSize = 32.dp
        )
    }
}

@Composable
private fun MediumPillContent(
    notification: DropNotification,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NotificationGlyphOrb(
            notification = notification,
            accentColor = accentColor,
            orbSize = 32.dp
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = notification.title,
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (notification.progressInfo != null) {
                val info = notification.progressInfo
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.10f))
                ) {
                    if (info.indeterminate) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.35f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(accentColor.copy(alpha = 0.8f))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(info.fraction)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(accentColor)
                        )
                    }
                }
            } else {
                Text(
                    text = notification.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        when {
            notification.progressInfo?.etaLabel != null -> Text(
                text = notification.progressInfo.etaLabel!!,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                maxLines = 1
            )
            notification.detectedOtpCode != null -> Text(
                text = notification.detectedOtpCode!!,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                color = accentColor
            )
            else -> Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
        }
    }
}

@Composable
private fun BiggestGlassContent(
    notification: DropNotification,
    queuedCount: Int,
    accentColor: Color,
    isReplying: Boolean,
    replyText: String,
    replyError: String?,
    copiedOtpFeedback: Boolean,
    onReplyTextChange: (String) -> Unit,
    onToggleReply: () -> Unit,
    onSendReply: (String) -> Unit,
    onOpen: () -> Unit,
    onTriggerAction: (Int) -> Unit,
    onMediaControl: (MediaCommandAction) -> Unit,
    onCopyOtp: (String) -> Unit,
    onSnooze: () -> Unit,
    onCycleQueue: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val timeString = remember(notification.timestampMillis) {
        DateFormat.getTimeFormat(context).format(Date(notification.timestampMillis))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NotificationGlyphOrb(
                notification = notification,
                accentColor = accentColor,
                orbSize = 34.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = notification.conversationTitle ?: notification.appName,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = "  $timeString",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        maxLines = 1
                    )
                }
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (queuedCount > 0) {
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clickable(onClick = onCycleQueue)
                        .padding(horizontal = 4.dp)
                        .testTag("drop_queue_badge"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+$queuedCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            if (notification.isLiveSystem) {
                IconButton(
                    onClick = onSnooze,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("drop_snooze_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Snooze,
                        contentDescription = "Snooze for 15 minutes",
                        tint = TextMuted,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("drop_dismiss_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_dismiss),
                    tint = TextSecondary,
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        // Body
        if (!isReplying) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (notification.messageLines.size > 1) {
                        notification.messageLines.takeLast(2).forEach { line ->
                            Text(
                                text = if (line.sender.isNotBlank()) "${line.sender}: ${line.text}" else line.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Text(
                            text = notification.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (notification.largePictureBitmap != null) {
                    val picBmp = remember(notification.id, notification.largePictureBitmap) {
                        notification.largePictureBitmap.asImageBitmap()
                    }
                    Image(
                        bitmap = picBmp,
                        contentDescription = "Attachment",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                    )
                }
            }
        }

        if (!isReplying && notification.progressInfo != null) {
            ProgressTracker(
                progressInfo = notification.progressInfo,
                accentColor = accentColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 44.dp, top = 2.dp)
            )
        }

        if (!isReplying && notification.detectedOtpCode != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .clickable { onCopyOtp(notification.detectedOtpCode) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("drop_otp_banner"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = notification.detectedOtpCode,
                        style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 1.5.sp),
                        color = TextPrimary
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (copiedOtpFeedback) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (copiedOtpFeedback) accentColor else TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (copiedOtpFeedback) "Copied" else "Copy",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (copiedOtpFeedback) accentColor else TextSecondary
                    )
                }
            }
        }

        // Inline reply
        AnimatedVisibility(visible = isReplying) {
            LiquidReplyBox(
                replyText = replyText,
                accentColor = accentColor,
                errorText = replyError,
                onReplyTextChange = onReplyTextChange,
                onSend = onSendReply,
                onCancel = onToggleReply
            )
        }

        // Actions
        if (!isReplying) {
            if (notification.category == DropCategory.MEDIA) {
                MediaTransportBar(
                    isPlaying = notification.isMediaPlaying,
                    accentColor = accentColor,
                    onMediaControl = onMediaControl,
                    onOpen = onOpen,
                    showOpen = notification.isLiveSystem,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 44.dp)
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 44.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    notification.actions.forEach { action ->
                        if (action.isReply) {
                            GlassActionPill(
                                label = action.title.ifBlank { "Reply" },
                                icon = Icons.AutoMirrored.Rounded.Reply,
                                accentColor = accentColor,
                                onClick = onToggleReply,
                                testTag = "drop_reply_pill"
                            )
                        } else {
                            GlassActionPill(
                                label = action.title,
                                icon = semanticActionIcon(action.semantic),
                                accentColor = when (action.semantic) {
                                    ActionSemantic.DECLINE, ActionSemantic.DELETE -> AccentRose
                                    ActionSemantic.ANSWER, ActionSemantic.MARK_READ -> accentColor
                                    else -> TextSecondary
                                },
                                onClick = { onTriggerAction(action.index) },
                                testTag = "drop_action_pill_${action.index}"
                            )
                        }
                    }

                    if (notification.detectedOtpCode != null &&
                        notification.actions.none { it.semantic == ActionSemantic.COPY_OTP }
                    ) {
                        GlassActionPill(
                            label = if (copiedOtpFeedback) "Copied" else "Copy code",
                            icon = if (copiedOtpFeedback) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                            accentColor = AccentAmber,
                            onClick = { onCopyOtp(notification.detectedOtpCode) },
                            testTag = "drop_copy_otp_pill"
                        )
                    }

                    if (notification.isLiveSystem) {
                        GlassActionPill(
                            label = stringResource(R.string.action_open),
                            icon = Icons.AutoMirrored.Rounded.OpenInNew,
                            accentColor = TextSecondary,
                            onClick = onOpen,
                            testTag = "drop_open_pill"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressTracker(
    progressInfo: DropProgressInfo,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val targetFraction = progressInfo.fraction.coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "progressFraction"
    )
    val percent = (targetFraction * 100f).roundToInt()
    val hasMilestones = progressInfo.milestoneLabels.isNotEmpty()
    val currentMilestone = progressInfo.milestoneLabels
        .getOrNull(progressInfo.activeMilestoneIndex)
    val statusText = when {
        hasMilestones -> currentMilestone ?: progressInfo.milestoneLabels.first()
        !progressInfo.etaLabel.isNullOrBlank() -> progressInfo.etaLabel
        else -> if (progressInfo.indeterminate) "Working…" else "In progress"
    }

    var indeterminatePhase by remember { mutableFloatStateOf(0f) }
    if (progressInfo.indeterminate) {
        val transition = rememberInfiniteTransition(label = "indeterminateProgress")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
            label = "indeterminatePhase"
        )
        indeterminatePhase = phase
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (progressInfo.indeterminate) "—" else "$percent%",
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        ) {
            val trackHeight = size.height
            drawRoundRect(
                color = Color.White.copy(alpha = 0.10f),
                size = size,
                cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
            )
            if (progressInfo.indeterminate) {
                val segmentWidth = size.width * 0.32f
                val startX = (size.width + segmentWidth) * indeterminatePhase - segmentWidth
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            accentColor,
                            Color.Transparent
                        ),
                        startX = startX,
                        endX = startX + segmentWidth
                    ),
                    topLeft = Offset(startX.coerceIn(0f, size.width), 0f),
                    size = Size(segmentWidth.coerceAtMost(size.width - startX.coerceIn(0f, size.width)), trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                )
            } else {
                val fillWidth = size.width * animatedFraction
                if (fillWidth > 0f) {
                    drawRoundRect(
                        color = accentColor,
                        size = Size(fillWidth.coerceAtLeast(trackHeight / 2f), trackHeight),
                        cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                    )
                }
            }
        }

        if (hasMilestones) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                progressInfo.milestoneLabels.forEachIndexed { idx, label ->
                    val isCurrent = idx == progressInfo.activeMilestoneIndex
                    val isDone = idx < progressInfo.activeMilestoneIndex ||
                        (!progressInfo.indeterminate && targetFraction >= (idx + 0.5f) / progressInfo.milestoneLabels.size)
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            isCurrent -> accentColor
                            isDone -> TextSecondary
                            else -> TextMuted
                        },
                        fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                        textAlign = when (idx) {
                            0 -> TextAlign.Start
                            progressInfo.milestoneLabels.lastIndex -> TextAlign.End
                            else -> TextAlign.Center
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaTransportBar(
    isPlaying: Boolean,
    accentColor: Color,
    onMediaControl: (MediaCommandAction) -> Unit,
    onOpen: () -> Unit,
    showOpen: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = { onMediaControl(MediaCommandAction.PREVIOUS) },
                modifier = Modifier
                    .size(40.dp)
                    .testTag("media_prev_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f))
                    .clickable(onClick = { onMediaControl(MediaCommandAction.PLAY_PAUSE) })
                    .testTag("media_play_pause_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = { onMediaControl(MediaCommandAction.NEXT) },
                modifier = Modifier
                    .size(40.dp)
                    .testTag("media_next_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (showOpen) {
            GlassActionPill(
                label = stringResource(R.string.action_open),
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                accentColor = TextSecondary,
                onClick = onOpen,
                testTag = "drop_open_pill"
            )
        }
    }
}

@Composable
private fun LiquidReplyBox(
    replyText: String,
    accentColor: Color,
    errorText: String?,
    onReplyTextChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onCancel: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val canSend = replyText.isNotBlank()
    val quickReplies = remember { listOf("On my way", "Got it", "In 5 min", "Thanks") }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quickReplies.forEach { preset ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(9.dp))
                        .clickable { onSend(preset) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("quick_reply_${preset.lowercase().replace(" ", "_")}")
                ) {
                    Text(
                        text = preset,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = replyText,
                onValueChange = onReplyTextChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                cursorBrush = SolidColor(accentColor),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (replyText.isNotBlank()) onSend(replyText.trim())
                    }
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .testTag("drop_reply_input"),
                decorationBox = { inner ->
                    if (replyText.isEmpty()) {
                        Text(
                            text = stringResource(R.string.reply_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                    inner()
                }
            )
            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("drop_cancel_reply_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Cancel reply",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (canSend) accentColor else Color.White.copy(alpha = 0.06f))
                    .clickable(enabled = canSend) { onSend(replyText.trim()) }
                    .testTag("drop_send_reply_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = stringResource(R.string.action_send),
                    tint = if (canSend) CanvasBlack else TextMuted,
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        if (errorText != null) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.labelSmall,
                color = AccentRose,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

private fun semanticActionIcon(semantic: ActionSemantic): ImageVector? = when (semantic) {
    ActionSemantic.REPLY -> Icons.AutoMirrored.Rounded.Reply
    ActionSemantic.MARK_READ -> Icons.Rounded.DoneAll
    ActionSemantic.ARCHIVE -> Icons.Rounded.Archive
    ActionSemantic.DELETE -> Icons.Rounded.DeleteOutline
    ActionSemantic.ANSWER -> Icons.Rounded.Call
    ActionSemantic.DECLINE -> Icons.Rounded.CallEnd
    ActionSemantic.MEDIA_PREV -> Icons.Rounded.SkipPrevious
    ActionSemantic.MEDIA_PLAY_PAUSE -> Icons.Rounded.PlayArrow
    ActionSemantic.MEDIA_NEXT -> Icons.Rounded.SkipNext
    ActionSemantic.TRACK_ORDER -> Icons.AutoMirrored.Rounded.DirectionsBike
    ActionSemantic.COPY_OTP -> Icons.Rounded.ContentCopy
    ActionSemantic.GENERIC -> null
}

@Composable
private fun GlassActionPill(
    label: String,
    icon: ImageVector?,
    accentColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = accentColor),
                onClick = onClick
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(9.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun NotificationGlyphOrb(
    notification: DropNotification,
    accentColor: Color,
    orbSize: Dp
) {
    val iconBitmap = remember(notification.id, notification.appIconBitmap) {
        notification.appIconBitmap?.asImageBitmap()
    }
    Box(
        modifier = Modifier
            .size(orbSize)
            .clip(CircleShape)
            .background(accentColor.copy(alpha = 0.16f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = notification.appName,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .clip(CircleShape)
            )
        } else {
            val glyph = when (notification.category) {
                DropCategory.CALL -> Icons.Rounded.Call
                DropCategory.MEDIA -> Icons.Rounded.GraphicEq
                DropCategory.DELIVERY -> Icons.Rounded.LocalShipping
                DropCategory.OTP -> Icons.Rounded.Key
                DropCategory.MESSAGE -> Icons.Rounded.Sms
                DropCategory.SYSTEM -> Icons.Rounded.NotificationsActive
            }
            Icon(
                imageVector = glyph,
                contentDescription = notification.appName,
                tint = accentColor,
                modifier = Modifier.size(orbSize * 0.52f)
            )
        }
    }
}
