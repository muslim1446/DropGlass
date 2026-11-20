package com.example.ui.glass

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.example.ui.theme.GlassTextMuted
import com.example.ui.theme.GlassTextPrimary
import com.example.ui.theme.GlassTextSecondary
import com.example.ui.theme.IceCyanPrimary
import com.example.ui.theme.LiquidAmber
import com.example.ui.theme.LiquidCrimson
import com.example.ui.theme.LiquidEmerald
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WaterDropOverlayHost(
    notification: DropNotification?,
    config: GlassOpticsConfig,
    detectedCutout: DetectedCutoutInfo,
    modifier: Modifier = Modifier,
    queuedCount: Int = 0,
    forcedStage: DropStage? = null,
    showCameraRingGuide: Boolean = false,
    onFocusRequested: (Boolean) -> Unit = {},
    onCycleQueue: () -> Unit = {},
    onOpenNotification: (DropNotification) -> Unit = {},
    onSendReply: (DropNotification, String) -> Unit = { _, _ -> },
    onTriggerAction: (DropNotification, Int) -> Unit = { _, _ -> },
    onMediaControl: (DropNotification, MediaCommandAction) -> Unit = { _, _ -> },
    onCopyOtp: (DropNotification, String) -> Unit = { _, _ -> },
    onSnooze: (DropNotification) -> Unit = {},
    onDismiss: (DropNotification?) -> Unit = {}
) {
    var currentStage by remember { mutableStateOf(DropStage.CAMERA_SEED) }
    var isReplying by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    var copiedOtpFeedback by remember { mutableStateOf(false) }
    var isInteracting by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

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
        copiedOtpFeedback = false
        dragOffsetY = 0f
    }

    // Choreographed Water-Drop Evolution: Camera Seed -> Small Drop -> Medium Pill -> Biggest Glass
    LaunchedEffect(notification?.id, forcedStage, config.viscositySpeed, config.autoDismissSeconds) {
        if (notification == null) {
            currentStage = DropStage.CAMERA_SEED
            return@LaunchedEffect
        }

        if (forcedStage != null) {
            currentStage = forcedStage
            return@LaunchedEffect
        }

        val speedFactor = (1f / config.viscositySpeed.coerceIn(0.4f, 2.0f))
        currentStage = DropStage.CAMERA_SEED
        delay((65f * speedFactor).toLong())
        currentStage = DropStage.SMALL_DROP
        delay((185f * speedFactor).toLong())
        currentStage = DropStage.MEDIUM_PILL
        delay((340f * speedFactor).toLong())
        currentStage = DropStage.BIGGEST_GLASS

        val dismissMs = (config.autoDismissSeconds * 1000f).toLong().coerceAtLeast(2000L)
        var elapsed = 0L
        val step = 150L
        while (elapsed < dismissMs) {
            delay(step)
            if (!isInteracting && !isReplying && forcedStage == null) {
                elapsed += step
            }
        }

        currentStage = DropStage.MEDIUM_PILL
        delay((140f * speedFactor).toLong())
        currentStage = DropStage.SMALL_DROP
        delay((125f * speedFactor).toLong())
        currentStage = DropStage.CAMERA_SEED
        delay((100f * speedFactor).toLong())
        onDismiss(notification)
    }

    val activeStage = forcedStage ?: currentStage
    val springSpecDp = spring<Dp>(
        dampingRatio = 0.68f,
        stiffness = 380f * config.viscositySpeed.coerceIn(0.5f, 1.8f)
    )
    val springSpecFloat = spring<Float>(
        dampingRatio = 0.72f,
        stiffness = 400f * config.viscositySpeed.coerceIn(0.5f, 1.8f)
    )

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        val density = LocalDensity.current
        val availableWidthDp = maxWidth
        val maxCardWidth = (availableWidthDp - 24.dp).coerceIn(268.dp, 380.dp)

        // Compute dynamic expanded height cleanly based on notification content richness
        val expandedHeightDp = remember(
            notification?.id,
            notification?.progressInfo != null,
            notification?.messageLines?.size,
            notification?.largePictureBitmap != null,
            notification?.detectedOtpCode != null,
            notification?.category,
            notification?.actions?.size,
            isReplying
        ) {
            if (notification == null) 130.dp
            else {
                var h = 114f
                if (notification.messageLines.size > 1) h += 28f
                if (notification.progressInfo != null) h += 50f
                if (notification.detectedOtpCode != null) h += 40f
                if (notification.largePictureBitmap != null) h += 52f
                if (notification.category == DropCategory.MEDIA) h += 16f
                if (notification.actions.isNotEmpty() || notification.hasReplyAction || notification.isLiveSystem) h += 44f
                if (isReplying) h += 78f
                h.coerceIn(146f, 336f).dp
            }
        }

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
            DropStage.BIGGEST_GLASS -> expandedHeightDp
        }
        val targetCorner = when (activeStage) {
            DropStage.CAMERA_SEED -> 40.dp
            DropStage.SMALL_DROP -> 26.dp
            DropStage.MEDIUM_PILL -> 28.dp
            DropStage.BIGGEST_GLASS -> 30.dp
        }
        val camBottomDp = effectiveCamTopMarginDp + (effectiveCamRadiusDp * 2f)
        val targetDropTopDp = when (activeStage) {
            DropStage.CAMERA_SEED -> effectiveCamTopMarginDp - 2f
            DropStage.SMALL_DROP -> camBottomDp + (config.dropGapDp * 0.45f)
            DropStage.MEDIUM_PILL -> camBottomDp + config.dropGapDp
            DropStage.BIGGEST_GLASS -> camBottomDp + config.dropGapDp + 2f
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

        val pulseScale = remember { Animatable(1f) }
        LaunchedEffect(notification?.id) {
            if (notification != null) {
                pulseScale.snapTo(0.88f)
                pulseScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 420f))
            }
        }

        val accentColor = remember(notification?.accentColorArgb) {
            if (notification != null) Color(notification.accentColorArgb) else IceCyanPrimary
        }

        val totalStageHeight = (animTop + animHeight + 18.dp).coerceAtLeast(92.dp)

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
            // Layer 1: Optical Liquid Glass + Metaball Meniscus Canvas
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
                        color = Color(0xFF030508),
                        radius = camRadiusPx,
                        center = Offset(camCenterX, camCenterY)
                    )
                    drawCircle(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.60f),
                                accentColor.copy(alpha = 0.50f),
                                Color.White.copy(alpha = 0.18f)
                            )
                        ),
                        radius = camRadiusPx + 1.5.dp.toPx(),
                        center = Offset(camCenterX, camCenterY),
                        style = Stroke(width = 1.4.dp.toPx())
                    )
                }
            }

            // Layer 2: Interactive Liquid Drop Content
            if (notification != null && activeStage != DropStage.CAMERA_SEED) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = effectiveCamCenterXdp.dp.roundToPx(),
                                y = animTop.roundToPx()
                            )
                        }
                        .width(animWidth)
                        .height(animHeight)
                        .alpha(contentAlpha)
                        .clip(RoundedCornerShape(animCorner))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = accentColor)
                        ) {
                            when (activeStage) {
                                DropStage.SMALL_DROP, DropStage.MEDIUM_PILL -> {
                                    currentStage = DropStage.BIGGEST_GLASS
                                }
                                DropStage.BIGGEST_GLASS -> {
                                    if (!isReplying) {
                                        onOpenNotification(notification)
                                    }
                                }
                                else -> Unit
                            }
                        }
                        .testTag("liquid_drop_container"),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = activeStage,
                        transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(120)) },
                        label = "dropStageContent"
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
                            DropStage.BIGGEST_GLASS -> BiggestGlassContent(
                                notification = notification,
                                queuedCount = queuedCount,
                                accentColor = accentColor,
                                isReplying = isReplying,
                                replyText = replyText,
                                copiedOtpFeedback = copiedOtpFeedback,
                                onReplyTextChange = { replyText = it },
                                onToggleReply = {
                                    isReplying = !isReplying
                                    isInteracting = isReplying
                                },
                                onSendReply = { textToSend ->
                                    val clean = textToSend.trim()
                                    if (clean.isNotEmpty()) {
                                        onSendReply(notification, clean)
                                        isReplying = false
                                        isInteracting = false
                                        replyText = ""
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
                            DropStage.CAMERA_SEED -> Unit
                        }
                    }
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
                    Color(0xCC0D1624),
                    accentColor.copy(alpha = 0.28f * neckStrength)
                ),
                startY = camCenter.y,
                endY = dropTopY + 8f
            )
        )
    }

    val bodyAlpha = when (style) {
        GlassStyle.CRYSTAL_CLEAR -> 0.78f
        GlassStyle.OBSIDIAN_SMOKE -> 0.90f
        GlassStyle.IRIDESCENT_PRISM -> 0.80f
        GlassStyle.COLOROS_ADAPTIVE -> 0.84f
    }
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF142136).copy(alpha = bodyAlpha),
                Color(0xFF070B13).copy(alpha = bodyAlpha + 0.08f)
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
                Color.White.copy(alpha = 0.65f * specular),
                accentColor.copy(alpha = 0.35f * specular),
                Color.White.copy(alpha = 0.16f * specular)
            ),
            start = dropTopLeft,
            end = Offset(dropTopLeft.x + dropSize.width, dropTopLeft.y + dropSize.height)
        ),
        topLeft = dropTopLeft,
        size = dropSize,
        cornerRadius = CornerRadius(cornerRadius, cornerRadius),
        style = Stroke(width = 1.4.dp.toPx())
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
            orbSize = 34.dp
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = notification.title,
                style = MaterialTheme.typography.labelLarge,
                color = GlassTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (notification.progressInfo != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(notification.progressInfo.fraction.coerceAtLeast(0.08f))
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(accentColor)
                    )
                }
            } else {
                Text(
                    text = notification.text,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.5.sp),
                    color = GlassTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (notification.detectedOtpCode != null) {
            Text(
                text = notification.detectedOtpCode,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor
            )
        } else if (notification.progressInfo?.etaLabel != null) {
            Text(
                text = notification.progressInfo.etaLabel,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor
            )
        } else {
            Box(
                modifier = Modifier
                    .size(8.dp)
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
    val timeString = remember(notification.timestampMillis) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(notification.timestampMillis))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header row: App icon orb + App/Time/Conversation + Queue badge + Snooze + Dismiss
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NotificationGlyphOrb(
                notification = notification,
                accentColor = accentColor,
                orbSize = 36.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = (notification.conversationTitle ?: notification.appName).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "· $timeString",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlassTextMuted
                    )
                }
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = GlassTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (queuedCount > 0) {
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clickable(onClick = onCycleQueue)
                        .padding(horizontal = 4.dp)
                        .testTag("drop_queue_badge"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(accentColor.copy(alpha = 0.20f))
                            .border(0.8.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "+$queuedCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = GlassTextPrimary
                        )
                    }
                }
            }
            if (notification.isLiveSystem) {
                IconButton(
                    onClick = onSnooze,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("drop_snooze_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Snooze,
                        contentDescription = "Snooze 15m",
                        tint = GlassTextMuted,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("drop_dismiss_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_dismiss),
                    tint = GlassTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Body Content: Rich Picture + Messaging lines / Text + Delivery Tracker / OTP Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 46.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (notification.messageLines.size > 1) {
                    notification.messageLines.takeLast(2).forEach { line ->
                        val prefix = if (line.sender.isNotBlank()) "${line.sender}: " else ""
                        Text(
                            text = "$prefix${line.text}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
                            color = GlassTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = notification.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTextSecondary,
                        maxLines = 2,
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
                        .clip(RoundedCornerShape(12.dp))
                        .border(0.8.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                )
            }
        }

        // Delivery / Journey / Download Progress Tracker
        if (notification.progressInfo != null) {
            DeliveryProgressTracker(
                progressInfo = notification.progressInfo,
                accentColor = accentColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 46.dp, top = 4.dp)
            )
        }

        // Detected OTP Verification Code Pill
        if (notification.detectedOtpCode != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 46.dp, top = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accentColor.copy(alpha = 0.14f))
                    .border(0.8.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .clickable { onCopyOtp(notification.detectedOtpCode) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
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
                        style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 2.sp),
                        color = GlassTextPrimary
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (copiedOtpFeedback) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = "Copy code",
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (copiedOtpFeedback) "Copied" else "Copy",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor
                    )
                }
            }
        }

        // Inline Reply Box (with auto-focus, IME Send, and quick reply chips)
        AnimatedVisibility(visible = isReplying) {
            LiquidReplyBox(
                replyText = replyText,
                accentColor = accentColor,
                onReplyTextChange = onReplyTextChange,
                onSend = onSendReply,
                onCancel = onToggleReply
            )
        }

        // Real Action Pills Bar (Reply + Mark as Read + Archive + Media Deck + Delivery + Open)
        if (!isReplying) {
            if (notification.category == DropCategory.MEDIA) {
                MediaTransportBar(
                    isPlaying = notification.isMediaPlaying,
                    accentColor = accentColor,
                    onMediaControl = onMediaControl,
                    onOpen = onOpen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 42.dp, top = 2.dp)
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 42.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Show ALL real notification actions (Reply, Mark as Read, Archive, Next Milestone, Answer, Decline...)
                    notification.actions.forEach { action ->
                        if (action.isReply) {
                            GlassActionPill(
                                label = action.title,
                                icon = Icons.AutoMirrored.Rounded.Reply,
                                accentColor = accentColor,
                                onClick = onToggleReply,
                                testTag = "drop_reply_pill"
                            )
                        } else {
                            val pillColor = when (action.semantic) {
                                ActionSemantic.DECLINE, ActionSemantic.DELETE -> LiquidCrimson
                                ActionSemantic.ANSWER, ActionSemantic.MARK_READ -> LiquidEmerald
                                else -> accentColor
                            }
                            GlassActionPill(
                                label = action.title,
                                icon = semanticActionIcon(action.semantic),
                                accentColor = pillColor,
                                onClick = { onTriggerAction(action.index) },
                                testTag = "drop_action_pill_${action.index}"
                            )
                        }
                    }

                    if (notification.detectedOtpCode != null &&
                        notification.actions.none { it.semantic == ActionSemantic.COPY_OTP }
                    ) {
                        GlassActionPill(
                            label = if (copiedOtpFeedback) "Copied" else "Copy ${notification.detectedOtpCode}",
                            icon = if (copiedOtpFeedback) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                            accentColor = LiquidAmber,
                            onClick = { onCopyOtp(notification.detectedOtpCode) },
                            testTag = "drop_copy_otp_pill"
                        )
                    }

                    GlassActionPill(
                        label = stringResource(R.string.action_open),
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        accentColor = Color.White.copy(alpha = 0.85f),
                        onClick = onOpen,
                        testTag = "drop_open_pill"
                    )
                }
            }
        }
    }
}

@Composable
private fun DeliveryProgressTracker(
    progressInfo: DropProgressInfo,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val animatedFraction by animateFloatAsState(
        targetValue = progressInfo.fraction.coerceIn(0.06f, 1f),
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 260f),
        label = "deliveryFraction"
    )
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trackY = size.height / 2f
                val trackH = 5.dp.toPx()
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.14f),
                    topLeft = Offset(0f, trackY - trackH / 2f),
                    size = Size(size.width, trackH),
                    cornerRadius = CornerRadius(trackH / 2f, trackH / 2f)
                )
                val fillW = (size.width * animatedFraction).coerceAtLeast(trackH)
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(accentColor.copy(alpha = 0.7f), accentColor)
                    ),
                    topLeft = Offset(0f, trackY - trackH / 2f),
                    size = Size(fillW, trackH),
                    cornerRadius = CornerRadius(trackH / 2f, trackH / 2f)
                )
                // Milestone nodes along the track
                val count = progressInfo.milestoneLabels.size.coerceAtLeast(2)
                for (i in 0 until count) {
                    val nx = if (count == 1) size.width else (size.width * (i.toFloat() / (count - 1)))
                    val reached = i <= progressInfo.activeMilestoneIndex ||
                        (i.toFloat() / (count - 1)) <= animatedFraction + 0.02f
                    drawCircle(
                        color = if (reached) Color.White else Color(0xFF1F2B3E),
                        radius = 4.dp.toPx(),
                        center = Offset(nx.coerceIn(4.dp.toPx(), size.width - 4.dp.toPx()), trackY)
                    )
                    if (reached) {
                        drawCircle(
                            color = accentColor,
                            radius = 2.5.dp.toPx(),
                            center = Offset(nx.coerceIn(4.dp.toPx(), size.width - 4.dp.toPx()), trackY)
                        )
                    }
                }
            }
        }
        if (progressInfo.milestoneLabels.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                progressInfo.milestoneLabels.forEachIndexed { idx, label ->
                    val active = idx == progressInfo.activeMilestoneIndex
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = if (active) accentColor else GlassTextMuted,
                        maxLines = 1
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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = { onMediaControl(MediaCommandAction.PREVIOUS) },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("media_prev_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous",
                    tint = GlassTextPrimary
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = { onMediaControl(MediaCommandAction.PLAY_PAUSE) })
                    .testTag("media_play_pause_button"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.24f))
                        .border(1.dp, accentColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = GlassTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            IconButton(
                onClick = { onMediaControl(MediaCommandAction.NEXT) },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("media_next_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    tint = GlassTextPrimary
                )
            }
        }

        GlassActionPill(
            label = stringResource(R.string.action_open),
            icon = Icons.AutoMirrored.Rounded.OpenInNew,
            accentColor = Color.White.copy(alpha = 0.85f),
            onClick = onOpen,
            testTag = "drop_open_pill"
        )
    }
}

@Composable
private fun LiquidReplyBox(
    replyText: String,
    accentColor: Color,
    onReplyTextChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onCancel: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val quickReplies = remember { listOf("On my way", "Got it!", "In 5 min", "Thanks") }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
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
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(0.8.dp, accentColor.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
                        .clickable { onSend(preset) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .testTag("quick_reply_${preset.lowercase().replace(" ", "_")}")
                ) {
                    Text(
                        text = preset,
                        style = MaterialTheme.typography.labelSmall,
                        color = GlassTextSecondary
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.09f))
                .border(1.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
                .padding(start = 14.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = replyText,
                onValueChange = onReplyTextChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = GlassTextPrimary),
                cursorBrush = SolidColor(accentColor),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend(replyText) }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .testTag("drop_reply_input"),
                decorationBox = { inner ->
                    if (replyText.isEmpty()) {
                        Text(
                            text = stringResource(R.string.reply_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = GlassTextMuted
                        )
                    }
                    inner()
                }
            )
            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("drop_cancel_reply_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Cancel reply",
                    tint = GlassTextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(
                onClick = { onSend(replyText) },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("drop_send_reply_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = stringResource(R.string.action_send),
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
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
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
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
                .height(34.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .border(0.8.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                .padding(horizontal = 11.dp),
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
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = GlassTextPrimary,
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
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.38f),
                        Color(0xFF0E1726)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
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
                tint = Color.White,
                modifier = Modifier.size(orbSize * 0.54f)
            )
        }
    }
}
