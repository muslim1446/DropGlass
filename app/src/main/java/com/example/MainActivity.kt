package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.system.LiquidOverlayService
import com.example.system.SystemBridgeHelper
import com.example.ui.MainViewModel
import com.example.ui.StudioTab
import com.example.ui.components.LiquidSilkBackdrop
import com.example.ui.screens.BridgeScreen
import com.example.ui.screens.OpticsScreen
import com.example.ui.screens.StageScreen
import com.example.ui.theme.DropGlassTheme
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.FrostPrimary
import com.example.ui.theme.SurfaceOne

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        setContent {
            val viewModel: MainViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                window.decorView.post {
                    val info = SystemBridgeHelper.detectHardwareCutout(this@MainActivity)
                    viewModel.updateDetectedCutout(info)
                }
            }

            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }

            DropGlassTheme(useSystemDynamicColor = uiState.config.useSystemDynamicColor) {
                DropGlassAppShell(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun DropGlassAppShell(viewModel: MainViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val postNotificationsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshPermissions()
        viewModel.showFeedback(
            if (granted) "Alert permission granted" else "Alert permission denied"
        )
    }

    LaunchedEffect(uiState.feedbackBanner) {
        val message = uiState.feedbackBanner ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearFeedback()
    }

    // Keep the overlay engine alive in the background so alerts surface over other
    // apps, not only while DropGlass itself is on screen.
    LaunchedEffect(uiState.config.enableSystemOverlay, uiState.isOverlayPermissionGranted) {
        if (uiState.config.enableSystemOverlay && uiState.isOverlayPermissionGranted) {
            LiquidOverlayService.ensureRunning(context)
        } else {
            LiquidOverlayService.stop(context)
        }
    }

    if (uiState.selectedTab != StudioTab.STAGE) {
        BackHandler {
            viewModel.selectTab(StudioTab.STAGE)
        }
    }

    val activeAccent = remember(uiState.previewDrop?.accentColorArgb) {
        uiState.previewDrop?.let { Color(it.accentColorArgb) } ?: FrostPrimary
    }

    val requestPostNotifications = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.refreshPermissions()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        LiquidSilkBackdrop(accentColor = activeAccent)

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                )
            },
            bottomBar = {
                if (!useRail) {
                    DropGlassBottomBar(
                        selectedTab = uiState.selectedTab,
                        onSelectTab = viewModel::selectTab
                    )
                }
            }
        ) { innerPadding ->
            Row(modifier = Modifier.fillMaxSize()) {
                if (useRail) {
                    DropGlassNavRail(
                        selectedTab = uiState.selectedTab,
                        onSelectTab = viewModel::selectTab
                    )
                }

                val contentBottomPadding = PaddingValues(
                    bottom = innerPadding.calculateBottomPadding()
                )

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (uiState.selectedTab) {
                        StudioTab.STAGE -> StageScreen(
                            uiState = uiState,
                            bottomContentPadding = contentBottomPadding,
                            onTriggerPreset = viewModel::triggerPreviewPreset,
                            onSelectForcedStage = viewModel::setForcedStage,
                            onReplyPreview = viewModel::handlePreviewReply,
                            onOpenPreview = viewModel::handlePreviewOpen,
                            onActionPreview = viewModel::handlePreviewAction,
                            onMediaPreview = viewModel::handlePreviewMediaControl,
                            onCopyOtp = viewModel::handleCopyOtp,
                            onDismissPreview = viewModel::dismissPreviewDrop,
                            onSendLiveTest = { alertType ->
                                if (!uiState.isPostNotificationsGranted &&
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                ) {
                                    requestPostNotifications()
                                } else {
                                    viewModel.sendLiveTestNotification(alertType)
                                }
                            },
                            onGoToBridge = { viewModel.selectTab(StudioTab.BRIDGE) },
                            onGoToOptics = { viewModel.selectTab(StudioTab.OPTICS) }
                        )

                        StudioTab.OPTICS -> OpticsScreen(
                            uiState = uiState,
                            bottomContentPadding = contentBottomPadding,
                            onApplyCph2773 = viewModel::applyOppoCph2773Preset,
                            onApplyAutoCutout = viewModel::applyHardwareCutoutPreset,
                            onSelectGlassStyle = viewModel::setGlassStyle,
                            onUpdateConfig = viewModel::updateConfig
                        )

                        StudioTab.BRIDGE -> BridgeScreen(
                            uiState = uiState,
                            bottomContentPadding = contentBottomPadding,
                            onOpenNotificationAccess = {
                                SystemBridgeHelper.openNotificationListenerSettings(context)
                            },
                            onOpenOverlaySettings = {
                                SystemBridgeHelper.openOverlaySettings(context)
                            },
                            onOpenBatterySettings = {
                                SystemBridgeHelper.openBatteryOptimizationSettings(context)
                            },
                            onOpenAppNotifications = {
                                SystemBridgeHelper.openAppNotificationSettings(context)
                            },
                            onRequestPostNotifications = requestPostNotifications,
                            onUpdateConfig = viewModel::updateConfig,
                            onReplayDrop = viewModel::replayOnSystemOverlay,
                            onTogglePackageMute = viewModel::togglePackageBlocked
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DropGlassBottomBar(
    selectedTab: StudioTab,
    onSelectTab: (StudioTab) -> Unit
) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.06f))
        )
        NavigationBar(
            containerColor = CanvasBlack.copy(alpha = 0.96f),
            contentColor = TextPrimary,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets.navigationBars
        ) {
            StudioTab.entries.forEach { tab ->
                val selected = selectedTab == tab
                val label = when (tab) {
                    StudioTab.STAGE -> stringResource(R.string.tab_stage)
                    StudioTab.OPTICS -> stringResource(R.string.tab_optics)
                    StudioTab.BRIDGE -> stringResource(R.string.tab_bridge)
                }
                val icon = when (tab) {
                    StudioTab.STAGE -> if (selected) Icons.Rounded.WaterDrop else Icons.Outlined.WaterDrop
                    StudioTab.OPTICS -> if (selected) Icons.Rounded.Tune else Icons.Outlined.Tune
                    StudioTab.BRIDGE -> if (selected) Icons.Rounded.Hub else Icons.Outlined.Hub
                }
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelectTab(tab) },
                    icon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = FrostPrimary,
                        selectedTextColor = TextPrimary,
                        indicatorColor = Color.White.copy(alpha = 0.07f),
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
                )
            }
        }
    }
}

@Composable
private fun DropGlassNavRail(
    selectedTab: StudioTab,
    onSelectTab: (StudioTab) -> Unit
) {
    NavigationRail(
        containerColor = CanvasBlack,
        contentColor = TextPrimary,
        modifier = Modifier
            .fillMaxHeight()
            .background(CanvasBlack)
            .padding(top = 24.dp, start = 8.dp, end = 8.dp)
    ) {
        StudioTab.entries.forEach { tab ->
            val selected = selectedTab == tab
            val label = when (tab) {
                StudioTab.STAGE -> stringResource(R.string.tab_stage)
                StudioTab.OPTICS -> stringResource(R.string.tab_optics)
                StudioTab.BRIDGE -> stringResource(R.string.tab_bridge)
            }
            val icon = when (tab) {
                StudioTab.STAGE -> if (selected) Icons.Rounded.WaterDrop else Icons.Outlined.WaterDrop
                StudioTab.OPTICS -> if (selected) Icons.Rounded.Tune else Icons.Outlined.Tune
                StudioTab.BRIDGE -> if (selected) Icons.Rounded.Hub else Icons.Outlined.Hub
            }
            NavigationRailItem(
                selected = selected,
                onClick = { onSelectTab(tab) },
                icon = { Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(22.dp)) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = FrostPrimary,
                    selectedTextColor = TextPrimary,
                    indicatorColor = Color.White.copy(alpha = 0.07f),
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted
                ),
                modifier = Modifier.testTag("rail_${tab.name.lowercase()}")
            )
        }
    }
}
