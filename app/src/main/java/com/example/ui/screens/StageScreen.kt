package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WaterDrop
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

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .align(Alignment.TopCenter)
                .testTag("stage_lazy_list"),
            contentPadding = PaddingValues(
                top = 0.dp,
                bottom = bottomContentPadding.calculateBottomPadding() + 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // The droplet lives at the camera cutout, pinned to y = 0.
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 272.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
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

            // Title + live bridge status
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
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        val systemReady = uiState.isNotificationAccessGranted &&
                            uiState.isOverlayPermissionGranted
                        StatusPillBadge(
                            text = if (systemReady) "Connected" else "Set up",
                            isActive = systemReady,
                            activeColor = AccentSage,
                            modifier = Modifier
                                .clickable(onClick = onGoToBridge)
                                .testTag("stage_bridge_badge")
                        )
                    }
                    Text(
                        text = stringResource(R.string.hero_subtitle),
                        style = MaterialTheme.typography.labelMedium,
                        color = FrostPrimary
                    )
                    Text(
                        text = stringResource(R.string.stage_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
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
                            text = "Preview size",
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
                        text = "Preview an alert",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Sample the layouts used for messages, deliveries, media, codes and calls.",
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
                            subtitle = "Reply & read",
                            icon = Icons.Rounded.Sms,
                            accent = FrostPrimary,
                            onClick = { onTriggerPreset(0) },
                            modifier = Modifier.weight(1f),
                            testTag = "scenario_message"
                        )
                        ScenarioTile(
                            title = "Delivery",
                            subtitle = "Milestones",
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
                            subtitle = "Playback",
                            icon = Icons.Rounded.GraphicEq,
                            accent = AccentTeal,
                            onClick = { onTriggerPreset(2) },
                            modifier = Modifier.weight(1f),
                            testTag = "scenario_audio"
                        )
                        ScenarioTile(
                            title = "2FA",
                            subtitle = "Copy code",
                            icon = Icons.Rounded.Key,
                            accent = AccentAmber,
                            onClick = { onTriggerPreset(3) },
                            modifier = Modifier.weight(1f),
                            testTag = "scenario_otp"
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    ScenarioTile(
                        title = "Incoming call",
                        subtitle = "Answer or decline",
                        icon = Icons.Rounded.Call,
                        accent = AccentSage,
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
                    accentGlow = FrostPrimary
                ) {
                    Text(
                        text = "Send a real alert",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Posts a system notification with working actions, so you can test the complete flow.",
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
                                accent = FrostPrimary,
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
                                text = "Looks",
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
            .background(if (selected) accent.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.035f))
            .border(
                width = 1.dp,
                color = if (selected) accent.copy(alpha = 0.50f) else Color.White.copy(alpha = 0.08f),
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
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.035f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(12.dp))
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
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp)
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
