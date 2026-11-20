package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DropNotification
import com.example.model.DropStage
import com.example.model.MediaCommandAction
import com.example.ui.RealOsAlertType
import com.example.ui.StudioUiState
import com.example.ui.components.LiquidGlassPanel
import com.example.ui.components.StatusPillBadge
import com.example.ui.glass.WaterDropOverlayHost
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentSage
import com.example.ui.theme.AccentTeal
import com.example.ui.theme.CanvasBlack
import com.example.ui.theme.FrostPrimary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private enum class StageBackdropMode(val label: String) {
    LIVE_TINT("Live Tint"),
    BRIGHT_GLASS("Bright Card"),
    OBSIDIAN("Obsidian")
}

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
    val activeAccent = remember(uiState.previewDrop?.accentColorArgb) {
        uiState.previewDrop?.let { Color(it.accentColorArgb) } ?: FrostPrimary
    }
    var selectedRealAlertType by remember { mutableStateOf(RealOsAlertType.MESSAGE) }
    var backdropMode by remember { mutableStateOf(StageBackdropMode.LIVE_TINT) }

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Camera stage at y = 0 with switchable background so glass transparency & live color are clearly visible
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 276.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    StageOpticalCanvas(
                        mode = backdropMode,
                        accentColor = activeAccent,
                        modifier = Modifier.matchParentSize()
                    )
                    WaterDropOverlayHost(
                        notification = uiState.previewDrop,
                        config = uiState.config,
                        detectedCutout = uiState.detectedCutout,
                        forcedStage = uiState.forcedStage,
                        showCameraRingGuide = true,
                        onOpenNotification = onOpenPreview,
                        onSendReply = onReplyPreview,
                        onTriggerAction = onActionPreview,
                        onMediaControl = onMediaPreview,
                        onCopyOtp = { _, code -> onCopyOtp(code) },
                        onDismiss = { onDismissPreview() }
                    )
                }
            }

            // Header + live status + backdrop toggle
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(activeAccent)
                            )
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineMedium,
                                color = TextPrimary
                            )
                        }
                        val systemReady = uiState.isNotificationAccessGranted &&
                            uiState.isOverlayPermissionGranted
                        StatusPillBadge(
                            text = if (systemReady) "Bridge Active" else "Setup Needed",
                            isActive = systemReady,
                            activeColor = AccentSage,
                            modifier = Modifier
                                .clickable(onClick = onGoToBridge)
                                .testTag("stage_bridge_badge")
                        )
                    }
                    Text(
                        text = stringResource(R.string.stage_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StageBackdropMode.entries.forEach { mode ->
                            StageSelectorChip(
                                label = mode.label,
                                selected = backdropMode == mode,
                                accent = activeAccent,
                                onClick = { backdropMode = mode },
                                testTag = "backdrop_chip_${mode.name.lowercase()}"
                            )
                        }
                    }
                }
            }

            // Size inspector
            item {
                LiquidGlassPanel(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    accentGlow = activeAccent
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Drop Stage",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = uiState.forcedStage?.label ?: "Automatic",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StageSelectorChip(
                            label = "Auto",
                            selected = uiState.forcedStage == null,
                            accent = FrostPrimary,
                            onClick = { onSelectForcedStage(null) },
                            testTag = "stage_chip_auto"
                        )
                        DropStage.entries.forEach { stage ->
                            StageSelectorChip(
                                label = stage.label,
                                selected = uiState.forcedStage == stage,
                                accent = activeAccent,
                                onClick = { onSelectForcedStage(stage) },
                                testTag = "stage_chip_${stage.name.lowercase()}"
                            )
                        }
                    }
                }
            }

            // Preset notifications
            item {
                LiquidGlassPanel(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    accentGlow = activeAccent
                ) {
                    Text(
                        text = "Interactive Previews",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Tap any category to watch the glass tint and layout adapt live.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScenarioTile(
                            title = "Message",
                            subtitle = "Inline reply",
                            icon = Icons.Rounded.Sms,
                            accent = Color(0xFF3AD17A),
                            onClick = { onTriggerPreset(0) },
                            modifier = Modifier.weight(1f),
                            testTag = "scenario_message"
                        )
                        ScenarioTile(
                            title = "Delivery",
                            subtitle = "Live milestones",
                            icon = Icons.Rounded.LocalShipping,
                            accent = AccentSage,
                            onClick = { onTriggerPreset(1) },
                            modifier = Modifier.weight(1f),
                            testTag = "scenario_delivery"
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScenarioTile(
                            title = "Media",
                            subtitle = "Artwork tint",
                            icon = Icons.Rounded.GraphicEq,
                            accent = AccentTeal,
                            onClick = { onTriggerPreset(2) },
                            modifier = Modifier.weight(1f),
                            testTag = "scenario_audio"
                        )
                        ScenarioTile(
                            title = "2FA Code",
                            subtitle = "One-tap copy",
                            icon = Icons.Rounded.Key,
                            accent = AccentAmber,
                            onClick = { onTriggerPreset(3) },
                            modifier = Modifier.weight(1f),
                            testTag = "scenario_otp"
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    ScenarioTile(
                        title = "Incoming Call",
                        subtitle = "Live timer & actions",
                        icon = Icons.Rounded.Call,
                        accent = Color(0xFF5CE19A),
                        onClick = { onTriggerPreset(4) },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "scenario_call"
                    )
                }
            }

            // Real OS alert
            item {
                LiquidGlassPanel(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    accentGlow = activeAccent
                ) {
                    Text(
                        text = "Real System Test",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Dispatches an actual Android notification to verify background interception & replies.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RealOsAlertType.entries.forEach { alertType ->
                            StageSelectorChip(
                                label = alertType.label,
                                selected = selectedRealAlertType == alertType,
                                accent = activeAccent,
                                onClick = { selectedRealAlertType = alertType },
                                testTag = "real_type_${alertType.name.lowercase()}"
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onSendLiveTest(selectedRealAlertType) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FrostPrimary,
                                contentColor = CanvasBlack
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("fire_real_os_notification_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = "Send ${selectedRealAlertType.label}",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        OutlinedButton(
                            onClick = onGoToOptics,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("stage_tune_optics_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "Appearance",
                                tint = TextSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Optics",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextSecondary
                            )
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
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        when (mode) {
            StageBackdropMode.LIVE_TINT -> {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.34f),
                            accentColor.copy(alpha = 0.14f),
                            CanvasBlack
                        )
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.42f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.25f, size.height * 0.42f),
                        radius = size.width * 0.48f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.78f, size.height * 0.35f),
                        radius = size.width * 0.42f
                    )
                )
            }
            StageBackdropMode.BRIGHT_GLASS -> {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFDDE3EC),
                            Color(0xFFB7C3D6),
                            CanvasBlack
                        )
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.45f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.4f),
                        radius = size.width * 0.52f
                    )
                )
            }
            StageBackdropMode.OBSIDIAN -> {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0A0C10),
                            CanvasBlack
                        )
                    )
                )
            }
        }
    }
}

@Composable
private fun StageSelectorChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f))
            .border(
                width = 1.dp,
                color = if (selected) accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = accent),
                onClick = onClick
            )
            .padding(horizontal = 14.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) TextPrimary else TextSecondary
        )
    }
}

@Composable
private fun ScenarioTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Row(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.035f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = accent),
                onClick = onClick
            )
            .padding(horizontal = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(17.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                maxLines = 1
            )
        }
    }
}
