package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.example.ui.components.PixelGroupPosition
import com.example.ui.components.PixelGroupedCard
import com.example.ui.components.PixelSectionHeader
import com.example.ui.components.PixelSwitchRow
import com.example.ui.components.StatusPillBadge

@Composable
fun BridgeScreen(
    uiState: StudioUiState,
    bottomContentPadding: PaddingValues,
    onOpenNotificationAccess: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenAppNotifications: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onReconnectListener: () -> Unit,
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
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.bridge_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Permissions Grouped Card Stack
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    PixelSectionHeader(
                        title = "Permissions",
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    PermissionGroupRow(
                        position = PixelGroupPosition.FIRST,
                        title = stringResource(R.string.perm_notification_access),
                        subtitle = if (uiState.isListenerConnected) {
                            "Connected and listening for incoming notifications"
                        } else {
                            "Needed to show messages, deliveries, media, and quick replies"
                        },
                        icon = Icons.Rounded.Notifications,
                        granted = uiState.isNotificationAccessGranted,
                        onClick = onOpenNotificationAccess,
                        testTag = "perm_notification_access_row"
                    )
                    PermissionGroupRow(
                        position = PixelGroupPosition.MIDDLE,
                        title = stringResource(R.string.perm_overlay_window),
                        subtitle = "Allows floating bubbles over other apps",
                        icon = Icons.Rounded.Layers,
                        granted = uiState.isOverlayPermissionGranted,
                        onClick = onOpenOverlaySettings,
                        testTag = "perm_overlay_window_row"
                    )
                    PermissionGroupRow(
                        position = PixelGroupPosition.LAST,
                        title = stringResource(R.string.perm_post_notifications),
                        subtitle = "Allows sending test notifications on Android 13+",
                        icon = Icons.Rounded.NotificationsActive,
                        granted = uiState.isPostNotificationsGranted,
                        onClick = onRequestPostNotifications,
                        testTag = "perm_post_notifications_row"
                    )
                }
            }

            // Behavior Switches Grouped Card Stack
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    PixelSectionHeader(
                        title = "Behavior",
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    PixelGroupedCard(position = PixelGroupPosition.FIRST) {
                        PixelSwitchRow(
                            title = "Bubble overlay",
                            subtitle = "Show floating notifications below your front camera on any screen",
                            checked = uiState.config.enableSystemOverlay,
                            onCheckedChange = { checked ->
                                onUpdateConfig { it.copy(enableSystemOverlay = checked) }
                            },
                            testTag = "switch_enable_overlay"
                        )
                    }
                    PixelGroupedCard(position = PixelGroupPosition.MIDDLE) {
                        PixelSwitchRow(
                            title = "Hide duplicate banners",
                            subtitle = "Replace standard Android heads-up popups once DropGlass handles them",
                            checked = uiState.config.replaceSystemHeadsUp,
                            onCheckedChange = { checked ->
                                onUpdateConfig { it.copy(replaceSystemHeadsUp = checked) }
                            },
                            testTag = "switch_replace_system_heads_up"
                        )
                    }
                    PixelGroupedCard(position = PixelGroupPosition.LAST) {
                        PixelSwitchRow(
                            title = "Skip silent background apps",
                            subtitle = "Ignore low-priority status items while keeping calls, downloads, timers, and media",
                            checked = uiState.config.suppressOngoing,
                            onCheckedChange = { checked ->
                                onUpdateConfig { it.copy(suppressOngoing = checked) }
                            },
                            testTag = "switch_suppress_ongoing"
                        )
                    }
                }
            }

            // Device Setup Tips & Quick Shortcuts
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PixelSectionHeader(title = "Device shortcuts")
                    LiquidGlassPanel {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PhoneAndroid,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = stringResource(R.string.coloros_guide_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.coloros_guide_step1),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.coloros_guide_step2),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.coloros_guide_step3),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onOpenBatterySettings,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("bridge_battery_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.BatterySaver,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Battery", style = MaterialTheme.typography.labelLarge)
                            }
                            FilledTonalButton(
                                onClick = onOpenAppNotifications,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("bridge_app_notif_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Channels", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onOpenAppInfo,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("bridge_app_info_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.btn_app_info),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            FilledTonalButton(
                                onClick = onReconnectListener,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("bridge_reconnect_listener_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.NotificationsActive,
                                    contentDescription = null,
                                    tint = if (uiState.isListenerConnected) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.btn_reconnect_listener),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                        if (!uiState.lastOverlayError.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Note: ${uiState.lastOverlayError}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Recent Notifications History
            item {
                PixelSectionHeader(title = "Recent notifications")
            }

            if (uiState.recentDrops.isEmpty()) {
                item {
                    LiquidGlassPanel {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Notifications,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "No notifications captured yet",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Incoming alerts will appear here so you can replay or mute apps anytime.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(uiState.recentDrops, key = { _, item -> item.id }) { index, drop ->
                    val position = when {
                        uiState.recentDrops.size == 1 -> PixelGroupPosition.ONLY
                        index == 0 -> PixelGroupPosition.FIRST
                        index == uiState.recentDrops.lastIndex -> PixelGroupPosition.LAST
                        else -> PixelGroupPosition.MIDDLE
                    }
                    val isMuted = drop.packageName in uiState.config.blockedPackages
                    CapturedNotificationRow(
                        position = position,
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
private fun PermissionGroupRow(
    position: PixelGroupPosition,
    title: String,
    subtitle: String,
    icon: ImageVector,
    granted: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val iconContainer = if (granted) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val iconTint = if (granted) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    PixelGroupedCard(
        position = position,
        onClick = onClick,
        modifier = Modifier.testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            StatusPillBadge(
                text = if (granted) stringResource(R.string.status_active) else stringResource(R.string.status_required),
                isActive = granted,
                activeColor = MaterialTheme.colorScheme.tertiary
            )
            if (!granted) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun CapturedNotificationRow(
    position: PixelGroupPosition,
    drop: DropNotification,
    isMuted: Boolean,
    onReplay: () -> Unit,
    onToggleMute: () -> Unit
) {
    val bmp = remember(drop.id, drop.appIconBitmap) {
        drop.appIconBitmap?.asImageBitmap()
    }
    PixelGroupedCard(position = position) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(drop.accentColorArgb).copy(alpha = 0.16f)),
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
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${drop.appName} · ${drop.title}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = drop.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    contentDescription = "Replay bubble",
                    tint = MaterialTheme.colorScheme.primary
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
                    tint = if (isMuted) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
