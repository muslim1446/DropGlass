package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DropNotification
import com.example.model.DropStage
import com.example.model.MediaCommandAction
import com.example.system.ScreenEnvironmentMonitor
import com.example.ui.RealOsAlertType
import com.example.ui.StudioUiState
import com.example.ui.components.LiquidGlassPanel
import com.example.ui.components.PixelGroupPosition
import com.example.ui.components.PixelGroupedCard
import com.example.ui.components.PixelSectionHeader
import com.example.ui.components.StatusPillBadge
import com.example.ui.glass.WaterDropOverlayHost

private enum class StageBackdropMode(val label: String) {
    LIVE_TINT("Wallpaper"),
    BRIGHT_GLASS("Light App"),
    OBSIDIAN("Dark App")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StageScreen(
    uiState: StudioUiState,
    bottomContentPadding: PaddingValues,
    onTriggerPreset: (Int) -> Unit,
    onSelectForcedStage: (DropStage?) -> Unit,
    onReplyPreview: (DropNotification, String) -> Boolean,
    onOpenPreview: (DropNotification) -> Unit,
    onActionPreview: (DropNotification, Int) -> Unit,
    onMediaPreview: (DropNotification, MediaCommandAction) -> Unit,
    onCopyOtp: (String) -> Unit,
    onDismissPreview: () -> Unit,
    onSendLiveTest: (RealOsAlertType) -> Unit,
    onGoToBridge: () -> Unit,
    onGoToOptics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val initialEnv = remember { ScreenEnvironmentMonitor.snapshot(context) }
    val screenEnv by remember(context) { ScreenEnvironmentMonitor.observe(context) }
        .collectAsState(initial = initialEnv)

    val fallbackAccent = MaterialTheme.colorScheme.primary
    val activeAccent = remember(uiState.previewDrop?.accentColorArgb, fallbackAccent) {
        uiState.previewDrop?.let { Color(it.accentColorArgb) } ?: fallbackAccent
    }
    var selectedRealAlertType by remember { mutableStateOf(RealOsAlertType.MESSAGE) }
    var backdropMode by remember { mutableStateOf(StageBackdropMode.LIVE_TINT) }
    val previewLumaHint = when (backdropMode) {
        StageBackdropMode.LIVE_TINT -> if (screenEnv.isLightUiMode) 0.68f else 0.36f
        StageBackdropMode.BRIGHT_GLASS -> 0.90f
        StageBackdropMode.OBSIDIAN -> 0.10f
    }

    val backgroundEndColor = MaterialTheme.colorScheme.background
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val tertiaryContainerColor = MaterialTheme.colorScheme.tertiaryContainer
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .align(Alignment.TopCenter)
                .testTag("stage_lazy_list"),
            contentPadding = PaddingValues(
                top = 0.dp,
                bottom = bottomContentPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live camera cutout optical preview canvas anchored at y = 0.
            // Crisp backdrop elements live inside backdropContent so the AGSL lensing RenderEffect
            // bends, disperses, and scatters real pixels inside the glass droplet!
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 276.dp)
                        .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)),
                    contentAlignment = Alignment.TopCenter
                ) {
                    WaterDropOverlayHost(
                        notification = uiState.previewDrop,
                        config = uiState.config,
                        detectedCutout = uiState.detectedCutout,
                        forcedStage = uiState.forcedStage,
                        showCameraRingGuide = true,
                        screenEnvironment = screenEnv,
                        screenLuminanceHint = previewLumaHint,
                        backdropContent = {
                            StageOpticalCanvas(
                                mode = backdropMode,
                                accentColor = activeAccent,
                                primaryColor = primaryColor,
                                secondaryColor = secondaryColor,
                                primaryContainer = primaryContainerColor,
                                tertiaryContainer = tertiaryContainerColor,
                                fadeBottomColor = backgroundEndColor,
                                modifier = Modifier.fillMaxSize()
                            )
                        },
                        onOpenNotification = onOpenPreview,
                        onSendReply = onReplyPreview,
                        onTriggerAction = onActionPreview,
                        onMediaControl = onMediaPreview,
                        onCopyOtp = { _, code -> onCopyOtp(code) },
                        onDismiss = { onDismissPreview() }
                    )
                }
            }

            // Pixel Editorial Title + Ready Pill + Backdrop Segmented Control
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        val systemReady = uiState.isNotificationAccessGranted &&
                            uiState.isOverlayPermissionGranted
                        StatusPillBadge(
                            text = if (systemReady) "Ready" else "Setup needed",
                            isActive = systemReady,
                            activeColor = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .clickable(onClick = onGoToBridge)
                                .testTag("stage_bridge_badge")
                        )
                    }
                    Text(
                        text = stringResource(R.string.stage_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        StageBackdropMode.entries.forEachIndexed { idx, mode ->
                            SegmentedButton(
                                selected = backdropMode == mode,
                                onClick = { backdropMode = mode },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = idx,
                                    count = StageBackdropMode.entries.size
                                ),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("backdrop_chip_${mode.name.lowercase()}")
                            ) {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }

            // Bubble Size Controls
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PixelSectionHeader(title = "Bubble size")
                        Text(
                            text = uiState.forcedStage?.label ?: "Automatic",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    LiquidGlassPanel {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PixelStageChip(
                                label = "Auto",
                                selected = uiState.forcedStage == null,
                                onClick = { onSelectForcedStage(null) },
                                testTag = "stage_chip_auto"
                            )
                            DropStage.entries.forEach { stage ->
                                PixelStageChip(
                                    label = stage.label,
                                    selected = uiState.forcedStage == stage,
                                    onClick = { onSelectForcedStage(stage) },
                                    testTag = "stage_chip_${stage.name.lowercase()}"
                                )
                            }
                        }
                    }
                }
            }

            // Sample Notifications (Pixel Grouped Cards)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    PixelSectionHeader(
                        title = "Sample notifications",
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    PixelGroupedCard(position = PixelGroupPosition.FIRST) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PixelScenarioTile(
                                title = "Message",
                                subtitle = "Quick reply",
                                icon = Icons.Rounded.Sms,
                                onClick = { onTriggerPreset(0) },
                                modifier = Modifier.weight(1f),
                                testTag = "scenario_message"
                            )
                            PixelScenarioTile(
                                title = "Delivery",
                                subtitle = "Live ETA",
                                icon = Icons.Rounded.LocalShipping,
                                onClick = { onTriggerPreset(1) },
                                modifier = Modifier.weight(1f),
                                testTag = "scenario_delivery"
                            )
                        }
                    }
                    PixelGroupedCard(position = PixelGroupPosition.MIDDLE) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PixelScenarioTile(
                                title = "Media",
                                subtitle = "Album tint",
                                icon = Icons.Rounded.GraphicEq,
                                onClick = { onTriggerPreset(2) },
                                modifier = Modifier.weight(1f),
                                testTag = "scenario_audio"
                            )
                            PixelScenarioTile(
                                title = "2FA Code",
                                subtitle = "One-tap copy",
                                icon = Icons.Rounded.Key,
                                onClick = { onTriggerPreset(3) },
                                modifier = Modifier.weight(1f),
                                testTag = "scenario_otp"
                            )
                        }
                    }
                    PixelGroupedCard(position = PixelGroupPosition.LAST) {
                        PixelScenarioTile(
                            title = "Incoming Call",
                            subtitle = "Live timer & call actions",
                            icon = Icons.Rounded.Call,
                            onClick = { onTriggerPreset(4) },
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "scenario_call"
                        )
                    }
                }
            }

            // Send Real System Notification
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PixelSectionHeader(title = "Live system test")
                    LiquidGlassPanel {
                        Text(
                            text = "Posts a real Android notification so you can test the floating overlay over any screen.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RealOsAlertType.entries.forEach { alertType ->
                                PixelStageChip(
                                    label = alertType.label,
                                    selected = selectedRealAlertType == alertType,
                                    onClick = { selectedRealAlertType = alertType },
                                    testTag = "real_type_${alertType.name.lowercase()}"
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onSendLiveTest(selectedRealAlertType) },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("fire_real_os_notification_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.NotificationsActive,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Send ${selectedRealAlertType.label}",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            FilledTonalButton(
                                onClick = onGoToOptics,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("stage_tune_optics_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = "Style",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.tab_optics),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StageOpticalCanvas(
    mode: StageBackdropMode,
    accentColor: Color,
    primaryColor: Color,
    secondaryColor: Color,
    primaryContainer: Color,
    tertiaryContainer: Color,
    fadeBottomColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        when (mode) {
            StageBackdropMode.LIVE_TINT -> {
                // Crisp Pixel Material You botanical/geometric backdrop shapes so AGSL lensing visibly bends & scatters color
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            primaryContainer,
                            tertiaryContainer.copy(alpha = 0.85f),
                            fadeBottomColor
                        )
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.72f),
                            accentColor.copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.24f, size.height * 0.42f),
                        radius = size.width * 0.36f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.58f),
                            secondaryColor.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.76f, size.height * 0.34f),
                        radius = size.width * 0.34f
                    )
                )
                // Crisp pill geometry behind the droplet rim to showcase edge refraction
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.26f),
                    topLeft = Offset(size.width * 0.14f, size.height * 0.22f),
                    size = Size(size.width * 0.72f, size.height * 0.36f),
                    cornerRadius = CornerRadius(42f, 42f)
                )
                drawCircle(
                    color = accentColor.copy(alpha = 0.55f),
                    radius = 28f,
                    center = Offset(size.width * 0.30f, size.height * 0.39f)
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.45f),
                    topLeft = Offset(size.width * 0.40f, size.height * 0.32f),
                    size = Size(size.width * 0.34f, 14f),
                    cornerRadius = CornerRadius(7f, 7f)
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.30f),
                    topLeft = Offset(size.width * 0.40f, size.height * 0.42f),
                    size = Size(size.width * 0.26f, 11f),
                    cornerRadius = CornerRadius(6f, 6f)
                )
            }
            StageBackdropMode.BRIGHT_GLASS -> {
                // High-luminance Pixel Light App surface with crisp cards & dark typography strokes
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFF6F8FC),
                            Color(0xFFE9EFF8),
                            fadeBottomColor
                        )
                    )
                )
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(size.width * 0.08f, size.height * 0.14f),
                    size = Size(size.width * 0.84f, size.height * 0.52f),
                    cornerRadius = CornerRadius(36f, 36f)
                )
                drawCircle(
                    color = primaryColor.copy(alpha = 0.78f),
                    radius = 26f,
                    center = Offset(size.width * 0.20f, size.height * 0.28f)
                )
                drawRoundRect(
                    color = Color(0xFF1A1D24).copy(alpha = 0.80f),
                    topLeft = Offset(size.width * 0.28f, size.height * 0.22f),
                    size = Size(size.width * 0.46f, 14f),
                    cornerRadius = CornerRadius(7f, 7f)
                )
                drawRoundRect(
                    color = Color(0xFF475569).copy(alpha = 0.55f),
                    topLeft = Offset(size.width * 0.28f, size.height * 0.31f),
                    size = Size(size.width * 0.54f, 11f),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawRoundRect(
                    color = accentColor.copy(alpha = 0.55f),
                    topLeft = Offset(size.width * 0.14f, size.height * 0.44f),
                    size = Size(size.width * 0.72f, 18f),
                    cornerRadius = CornerRadius(9f, 9f)
                )
            }
            StageBackdropMode.OBSIDIAN -> {
                // Crisp dark OLED app surface with luminous card accents
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0E1117),
                            Color(0xFF151922),
                            fadeBottomColor
                        )
                    )
                )
                drawRoundRect(
                    color = Color(0xFF1D222D),
                    topLeft = Offset(size.width * 0.08f, size.height * 0.14f),
                    size = Size(size.width * 0.84f, size.height * 0.52f),
                    cornerRadius = CornerRadius(36f, 36f)
                )
                drawCircle(
                    color = accentColor.copy(alpha = 0.65f),
                    radius = 28f,
                    center = Offset(size.width * 0.22f, size.height * 0.30f)
                )
                drawRoundRect(
                    color = Color(0xFFE2E8F0).copy(alpha = 0.42f),
                    topLeft = Offset(size.width * 0.32f, size.height * 0.24f),
                    size = Size(size.width * 0.44f, 13f),
                    cornerRadius = CornerRadius(7f, 7f)
                )
                drawRoundRect(
                    color = Color(0xFF94A3B8).copy(alpha = 0.28f),
                    topLeft = Offset(size.width * 0.32f, size.height * 0.33f),
                    size = Size(size.width * 0.52f, 11f),
                    cornerRadius = CornerRadius(6f, 6f)
                )
            }
        }
    }
}

@Composable
private fun PixelStageChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge
            )
        },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag(testTag)
    )
}

@Composable
private fun PixelScenarioTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Row(
        modifier = modifier
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = MaterialTheme.colorScheme.primary),
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(19.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
