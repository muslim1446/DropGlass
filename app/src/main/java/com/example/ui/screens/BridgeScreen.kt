package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DropNotification
import com.example.model.GlassOpticsConfig
import com.example.ui.StudioUiState
import com.example.ui.components.LiquidGlassPanel
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.FrostPrimary
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentSage

@Composable
fun BridgeScreen(
    uiState: StudioUiState,
    bottomContentPadding: PaddingValues,
    onOpenNotificationAccess: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenAppNotifications: () -> Unit,
    onRequestPostNotifications: () -> Unit,
    onUpdateConfig: ((GlassOpticsConfig) -> GlassOpticsConfig) -> Unit,
    onReplayDrop: (DropNotification) -> Unit,
    onTogglePackageMute: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .align(Alignment.TopCenter)
                .testTag("bridge_lazy_list"),
            contentPadding = PaddingValues(
                top = statusTop + 16.dp,
                bottom = bottomContentPadding.calculateBottomPadding() + 24.dp,
                start = 20.dp,
                end = 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.bridge_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = stringResource(R.string.bridge_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            // Permissions Bridge Card
            item {
                LiquidGlassPanel(
                    accentGlow = if (uiState.isNotificationAccessGranted && uiState.isOverlayPermissionGranted) {
                        AccentSage
                    } else {
                        AccentAmber
                    }
                ) {
                    PermissionRow(
                        title = stringResource(R.string.perm_notification_access),
                        subtitle = if (uiState.isListenerConnected) {
                            "Listener bound to OS notification stream"
                        } else {
                            "Required to read incoming alerts, delivery progress & inline replies"
                        },
                        icon = Icons.Rounded.Notifications,
                        granted = uiState.isNotificationAccessGranted,
                        onClick = onOpenNotificationAccess,
                        testTag = "perm_notification_access_row"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    PermissionRow(
                        title = stringResource(R.string.perm_overlay_window),
                        subtitle = "Draws Liquid Glass drop over other apps below camera",
                        icon = Icons.Rounded.Layers,
                        granted = uiState.isOverlayPermissionGranted,
                        onClick = onOpenOverlaySettings,
                        testTag = "perm_overlay_window_row"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    PermissionRow(
                        title = stringResource(R.string.perm_post_notifications),
                        subtitle = "Allows posting live test alerts on Android 13+",
                        icon = Icons.Rounded.NotificationsActive,
                        granted = uiState.isPostNotificationsGranted,
                        onClick = onRequestPostNotifications,
                        testTag = "perm_post_notifications_row"
                    )
                }
            }

            // Master Switches
            item {
                LiquidGlassPanel {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "System Popup Overlay",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Pop Liquid Glass water drop below selfie camera on any screen",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = uiState.config.enableSystemOverlay,
                            onCheckedChange = { checked ->
                                onUpdateConfig { it.copy(enableSystemOverlay = checked) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF041928),
                                checkedTrackColor = FrostPrimary
                            ),
                            modifier = Modifier.testTag("switch_enable_overlay")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ignore Silent Ongoing",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Skip background tasks (keeps calls, media & live deliveries active)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = uiState.config.suppressOngoing,
                            onCheckedChange = { checked ->
                                onUpdateConfig { it.copy(suppressOngoing = checked) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF041928),
                                checkedTrackColor = FrostPrimary
                            ),
                            modifier = Modifier.testTag("switch_suppress_ongoing")
                        )
                    }
                }
            }

            // OPPO CPH2773 / ColorOS Native Guide + Quick System Settings Shortcuts
            item {
                LiquidGlassPanel(accentGlow = FrostPrimary) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhoneAndroid,
                            contentDescription = null,
                            tint = FrostPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.coloros_guide_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.coloros_guide_step1),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.coloros_guide_step2),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.coloros_guide_step3),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenBatterySettings,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("bridge_battery_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BatterySaver,
                                contentDescription = null,
                                tint = FrostPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Battery",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary
                            )
                        }
                        OutlinedButton(
                            onClick = onOpenAppNotifications,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("bridge_app_notif_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                                tint = FrostPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Channels",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            // Captured Real Notifications History
            item {
                Text(
                    text = "Captured System Alerts",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }

            if (uiState.recentDrops.isEmpty()) {
                item {
                    LiquidGlassPanel {
                        Text(
                            text = "No live system notifications intercepted yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Once Notification Access is granted, incoming messages, deliveries, media, and OTPs appear here for instant replay or per-app filtering.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
            } else {
                items(uiState.recentDrops, key = { it.id }) { drop ->
                    val isMuted = drop.packageName in uiState.config.blockedPackages
                    CapturedNotificationRow(
                        drop = drop,
                        isMuted = isMuted,
                        onReplay = { onReplayDrop(drop) },
                        onToggleMute = { onTogglePackageMute(drop.packageName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    granted: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(
                width = 1.dp,
                color = if (granted) AccentSage.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.10f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = FrostPrimary),
                onClick = onClick
            )
            .padding(14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background((if (granted) AccentSage else FrostPrimary).copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (granted) AccentSage else FrostPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        StatusPillBadge(
            text = if (granted) stringResource(R.string.status_active) else stringResource(R.string.status_required),
            isActive = granted,
            activeColor = AccentSage
        )
        if (!granted) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun CapturedNotificationRow(
    drop: DropNotification,
    isMuted: Boolean,
    onReplay: () -> Unit,
    onToggleMute: () -> Unit
) {
    val bmp = remember(drop.id, drop.appIconBitmap) {
        drop.appIconBitmap?.asImageBitmap()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xB80E1624))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(drop.accentColorArgb).copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = drop.appName,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Notifications,
                    contentDescription = drop.appName,
                    tint = Color(drop.accentColorArgb),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${drop.appName} · ${drop.title}",
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = drop.text,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = onReplay,
            modifier = Modifier
                .size(48.dp)
                .testTag("replay_drop_${drop.packageName}")
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "Replay droplet",
                tint = FrostPrimary
            )
        }
        IconButton(
            onClick = onToggleMute,
            modifier = Modifier
                .size(48.dp)
                .testTag("mute_app_${drop.packageName}")
        ) {
            Icon(
                imageVector = Icons.Rounded.VisibilityOff,
                contentDescription = if (isMuted) "Unmute app" else "Mute app",
                tint = if (isMuted) AccentAmber else TextMuted
            )
        }
    }
}
