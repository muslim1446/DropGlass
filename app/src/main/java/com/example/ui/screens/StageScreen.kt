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
import com.example.ui.theme.GlassTextMuted
import com.example.ui.theme.GlassTextPrimary
import com.example.ui.theme.GlassTextSecondary
import com.example.ui.theme.IceCyanPrimary
import com.example.ui.theme.LiquidAmber
import com.example.ui.theme.LiquidEmerald
import com.example.ui.theme.PrismAqua

@Composable
fun StageScreen(
    uiState: StudioUiState,
    bottomContentPadding: PaddingValues,
    onTriggerPreset: (Int) -> Unit,
    onSelectForcedStage: (DropStage?) -> Unit,
    onReplyPreview: (DropNotification, String) -> Unit,
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
        uiState.previewDrop?.let { Color(it.accentColorArgb) } ?: IceCyanPrimary
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Selfie Camera Optical Stage (pinned to y=0 so droplet merges with physical cutout)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(312.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    WaterDropOverlayHost(
                        notification = uiState.previewDrop,
                        config = uiState.config,
                        detectedCutout = uiState.detectedCutout,
                        forcedStage = uiState.forcedStage,
                        showCameraRingGuide = true,
                        onOpenNotification = { onTriggerPreset(0) },
                        onSendReply = onReplyPreview,
                        onTriggerAction = onActionPreview,
                        onMediaControl = onMediaPreview,
                        onCopyOtp = { _, code -> onCopyOtp(code) },
                        onDismiss = { onDismissPreview() }
                    )
                }
            }

            // Editorial Title + Bridge Live Status
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
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.displaySmall,
                            color = GlassTextPrimary
                        )
                        val systemReady = uiState.isNotificationAccessGranted &&
                            uiState.isOverlayPermissionGranted
                        StatusPillBadge(
                            text = if (systemReady) "SYSTEM BRIDGE LIVE" else "SETUP BRIDGE",
                            isActive = systemReady,
                            activeColor = LiquidEmerald,
                            modifier = Modifier
                                .clickable(onClick = onGoToBridge)
                                .testTag("stage_bridge_badge")
                        )
                    }
                    Text(
                        text = stringResource(R.string.hero_subtitle),
                        style = MaterialTheme.typography.labelMedium,
                        color = IceCyanPrimary
                    )
                    Text(
                        text = stringResource(R.string.stage_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTextSecondary
                    )
                }
            }

            // Stage Size Inspector: Freeze & Inspect Lens Seed -> Small -> Medium -> Biggest
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WaterDrop,
                                contentDescription = null,
                                tint = IceCyanPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Water-Drop Morph Stages",
                                style = MaterialTheme.typography.titleMedium,
                                color = GlassTextPrimary
                            )
                        }
                        Text(
                            text = uiState.forcedStage?.label ?: "Auto Sequence",
                            style = MaterialTheme.typography.labelSmall,
                            color = IceCyanPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StageSelectorChip(
                            label = "Auto Flow",
                            selected = uiState.forcedStage == null,
                            accent = IceCyanPrimary,
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

            // Notification Scenarios Dock
            item {
                LiquidGlassPanel(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    accentGlow = activeAccent
                ) {
                    Text(
                        text = "Interactive Drop Templates",
                        style = MaterialTheme.typography.titleMedium,
                        color = GlassTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Test inline Reply, Mark as Read, Delivery milestone bars, 2FA Copy, Media controls, and Call buttons.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTextMuted
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ScenarioTile(
                            title = "Message",
                            icon = Icons.Rounded.Sms,
                            accent = IceCyanPrimary,
                            onClick = { onTriggerPreset(0) },
                            modifier = Modifier.weight(1f),
                            testTag = "scenario_message"
                        )
                        ScenarioTile(
                            title = "Delivery",
                            icon = Icons.Rounded.LocalShipping,
                            accent = LiquidEmerald,
                            onClick = { onTriggerPreset(1) },
                            modifier = Modifier.weight(1f),
                            testTag = "scenario_delivery"
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ScenarioTile(
                            title = "Media",
                            icon = Icons.Rounded.GraphicEq,
                            accent = PrismAqua,
                            onClick = { onTriggerPreset(2) },
                            modifier = Modifier.weight(1f),
                            testTag = "scenario_audio"
                        )
                        ScenarioTile(
                            title = "2FA OTP",
                            icon = Icons.Rounded.Key,
                            accent = LiquidAmber,
                            onClick = { onTriggerPreset(3) },
                            modifier = Modifier.weight(1f),
                            testTag = "scenario_otp"
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    ScenarioTile(
                        title = "Incoming Call (Answer / Decline)",
                        icon = Icons.Rounded.Call,
                        accent = LiquidEmerald,
                        onClick = { onTriggerPreset(4) },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "scenario_call"
                    )
                }
            }

            // Real Android OS Notification Launcher (fires real NotificationManager + PendingIntent + RemoteInput)
            item {
                LiquidGlassPanel(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    accentGlow = IceCyanPrimary
                ) {
                    Text(
                        text = "Dispatch Real Android OS Alert",
                        style = MaterialTheme.typography.titleMedium,
                        color = GlassTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Posts an actual NotificationManager alert with live PendingIntent & RemoteInput actions for end-to-end testing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTextMuted
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
                                accent = IceCyanPrimary,
                                onClick = { selectedRealAlertType = alertType },
                                testTag = "real_type_${alertType.name.lowercase()}"
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { onSendLiveTest(selectedRealAlertType) },
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = IceCyanPrimary,
                                contentColor = Color(0xFF041928)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("fire_real_os_notification_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Post ${selectedRealAlertType.label}",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        OutlinedButton(
                            onClick = onGoToOptics,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("stage_tune_optics_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "Tune Optics",
                                tint = GlassTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Tune",
                                style = MaterialTheme.typography.labelLarge,
                                color = GlassTextPrimary
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
            .height(48.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = accent),
                onClick = onClick
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (selected) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
                .border(
                    width = 1.dp,
                    color = if (selected) accent else Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) GlassTextPrimary else GlassTextSecondary
            )
        }
    }
}

@Composable
private fun ScenarioTile(
    title: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Row(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = accent),
                onClick = onClick
            )
            .padding(horizontal = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = GlassTextPrimary
        )
    }
}
