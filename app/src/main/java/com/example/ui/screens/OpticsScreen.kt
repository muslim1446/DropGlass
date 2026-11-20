package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraFront
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.CameraPreset
import com.example.model.DropStage
import com.example.model.GlassOpticsConfig
import com.example.model.GlassStyle
import com.example.system.ScreenEnvironmentCalculator
import com.example.system.ScreenEnvironmentMonitor
import com.example.ui.MainViewModel
import com.example.ui.StudioUiState
import com.example.ui.components.LiquidGlassPanel
import com.example.ui.components.OpticalSliderRow
import com.example.ui.components.PixelGroupPosition
import com.example.ui.components.PixelGroupedCard
import com.example.ui.components.PixelSectionHeader
import com.example.ui.components.PixelSwitchRow
import com.example.ui.components.StatusPillBadge
import com.example.ui.glass.WaterDropOverlayHost
import kotlin.math.roundToInt

@Composable
fun OpticsScreen(
    uiState: StudioUiState,
    bottomContentPadding: PaddingValues,
    onApplyCph2773: () -> Unit,
    onApplyAutoCutout: () -> Unit,
    onSelectGlassStyle: (GlassStyle) -> Unit,
    onUpdateConfig: ((GlassOpticsConfig) -> GlassOpticsConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val initialEnv = remember { ScreenEnvironmentMonitor.snapshot(context) }
    val screenEnv by remember(context) { ScreenEnvironmentMonitor.observe(context) }
        .collectAsState(initial = initialEnv)

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val config = uiState.config
    val cutout = uiState.detectedCutout
    val calibrationSample = remember { MainViewModel.sampleDrops().first() }
    val blurProfile = remember(config, screenEnv) {
        ScreenEnvironmentCalculator.computeProfile(config, screenEnv)
    }

    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
    val fadeBackground = MaterialTheme.colorScheme.background
    val accent = remember(calibrationSample.accentColorArgb) { Color(calibrationSample.accentColorArgb) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .align(Alignment.TopCenter)
                .testTag("optics_lazy_list"),
            contentPadding = PaddingValues(
                top = 0.dp,
                bottom = bottomContentPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live front-camera calibration ring preview anchored at y = 0 with optical backdrop
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((statusTop + 86.dp).coerceAtLeast(116.dp))
                        .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)),
                    contentAlignment = Alignment.TopCenter
                ) {
                    WaterDropOverlayHost(
                        notification = calibrationSample,
                        config = config,
                        detectedCutout = cutout,
                        forcedStage = DropStage.SMALL_DROP,
                        showCameraRingGuide = true,
                        screenEnvironment = screenEnv,
                        backdropContent = {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(primaryContainer, tertiaryContainer.copy(alpha = 0.7f), fadeBackground)
                                    )
                                )
                                drawCircle(
                                    color = accent.copy(alpha = 0.45f),
                                    radius = size.width * 0.25f,
                                    center = Offset(size.width * 0.5f, size.height * 0.45f)
                                )
                            }
                        }
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Appearance",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Watch the ring above align with your front camera as you adjust size, glass finish, and optical physics.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Camera Cutout Alignment
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PixelSectionHeader(title = "Camera cutout")
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
                                    imageVector = Icons.Rounded.CameraFront,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (cutout.hasHardwareCutout) {
                                        "${cutout.deviceModel.ifBlank { "Device" }} cutout detected"
                                    } else {
                                        "Active preset: ${config.preset.label}"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (cutout.hasHardwareCutout) {
                                        "${cutout.widthDp.roundToInt()}×${cutout.heightDp.roundToInt()} dp · ${cutout.topDp.roundToInt()} dp from top"
                                    } else {
                                        "Centered above the status bar"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (cutout.hasHardwareCutout) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (config.preset == CameraPreset.OPPO_CPH2773) {
                                Button(
                                    onClick = onApplyCph2773,
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("preset_cph2773_button")
                                ) {
                                    Text(text = "OPPO CPH2773", style = MaterialTheme.typography.labelLarge)
                                }
                            } else {
                                FilledTonalButton(
                                    onClick = onApplyCph2773,
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("preset_cph2773_button")
                                ) {
                                    Text(text = "OPPO CPH2773", style = MaterialTheme.typography.labelLarge)
                                }
                            }

                            if (config.preset == CameraPreset.HARDWARE_AUTO) {
                                Button(
                                    onClick = onApplyAutoCutout,
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("preset_auto_cutout_button")
                                ) {
                                    Text(text = "Auto Detect", style = MaterialTheme.typography.labelLarge)
                                }
                            } else {
                                FilledTonalButton(
                                    onClick = onApplyAutoCutout,
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("preset_auto_cutout_button")
                                ) {
                                    Text(text = "Auto Detect", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }

            // Glass Finish Expressive Grouped Radio List
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    PixelSectionHeader(
                        title = "Bubble finish",
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    GlassStyle.entries.forEachIndexed { index, style ->
                        val selected = config.glassStyle == style
                        val position = when (index) {
                            0 -> PixelGroupPosition.FIRST
                            GlassStyle.entries.lastIndex -> PixelGroupPosition.LAST
                            else -> PixelGroupPosition.MIDDLE
                        }
                        val container = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }

                        PixelGroupedCard(
                            position = position,
                            containerColor = container,
                            onClick = { onSelectGlassStyle(style) },
                            modifier = Modifier.testTag("glass_style_${style.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = style.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = style.subtitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                RadioButton(
                                    selected = selected,
                                    onClick = { onSelectGlassStyle(style) }
                                )
                            }
                        }
                    }
                }
            }

            // Geometry Sliders
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PixelSectionHeader(title = "Position & size")
                    LiquidGlassPanel {
                        OpticalSliderRow(
                            label = "Camera radius",
                            valueText = "${String.format("%.1f", config.cameraRadiusDp)} dp",
                            value = config.cameraRadiusDp,
                            valueRange = 8f..18f,
                            onValueChange = { v ->
                                onUpdateConfig {
                                    it.copy(preset = CameraPreset.CUSTOM, cameraRadiusDp = v)
                                }
                            }
                        )
                        OpticalSliderRow(
                            label = "Top offset",
                            valueText = "${config.cameraTopMarginDp.roundToInt()} dp",
                            value = config.cameraTopMarginDp,
                            valueRange = 4f..28f,
                            onValueChange = { v ->
                                onUpdateConfig {
                                    it.copy(preset = CameraPreset.CUSTOM, cameraTopMarginDp = v)
                                }
                            }
                        )
                        OpticalSliderRow(
                            label = "Horizontal alignment",
                            valueText = "${config.cameraOffsetXdp.roundToInt()} dp",
                            value = config.cameraOffsetXdp,
                            valueRange = -24f..24f,
                            onValueChange = { v ->
                                onUpdateConfig {
                                    it.copy(preset = CameraPreset.CUSTOM, cameraOffsetXdp = v)
                                }
                            }
                        )
                        OpticalSliderRow(
                            label = "Gap below camera",
                            valueText = "${config.dropGapDp.roundToInt()} dp",
                            value = config.dropGapDp,
                            valueRange = 2f..22f,
                            onValueChange = { v ->
                                onUpdateConfig { it.copy(dropGapDp = v) }
                            }
                        )
                    }
                }
            }

            // Optical Physics, Theme & Timing
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    PixelSectionHeader(
                        title = "Optical physics & theme",
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    PixelGroupedCard(position = PixelGroupPosition.FIRST) {
                        PixelSwitchRow(
                            title = "Material You colors",
                            subtitle = "Match app surfaces to your Pixel wallpaper palette",
                            checked = config.useSystemDynamicColor,
                            onCheckedChange = { checked ->
                                onUpdateConfig { it.copy(useSystemDynamicColor = checked) }
                            },
                            testTag = "switch_dynamic_color"
                        )
                    }
                    PixelGroupedCard(position = PixelGroupPosition.MIDDLE) {
                        PixelSwitchRow(
                            title = "Adaptive luminance & blur",
                            subtitle = "Flips glass polarity and scales scattering over light or dark screens",
                            checked = config.adaptiveContrastBlur,
                            onCheckedChange = { checked ->
                                onUpdateConfig { it.copy(adaptiveContrastBlur = checked) }
                            },
                            testTag = "switch_adaptive_contrast_blur"
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val polarityLabel = if (blurProfile.isLightGlassMode) "Light glass" else "Dark glass"
                            StatusPillBadge(
                                text = if (blurProfile.useCrossWindowBlur) {
                                    "Live blur · $polarityLabel"
                                } else {
                                    "Adaptive frost · $polarityLabel"
                                },
                                isActive = blurProfile.useCrossWindowBlur,
                                activeColor = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = "${blurProfile.effectiveBlurDp.roundToInt()} dp",
                                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    PixelGroupedCard(position = PixelGroupPosition.LAST) {
                        OpticalSliderRow(
                            label = "Light scattering blur",
                            valueText = "${config.blurRadiusDp.roundToInt()} dp",
                            value = config.blurRadiusDp,
                            valueRange = 8f..52f,
                            onValueChange = { v ->
                                onUpdateConfig { it.copy(blurRadiusDp = v) }
                            }
                        )
                        OpticalSliderRow(
                            label = "Fresnel rim highlight",
                            valueText = "${(config.specularRimIntensity * 100).roundToInt()}%",
                            value = config.specularRimIntensity,
                            valueRange = 0.2f..1.3f,
                            onValueChange = { v ->
                                onUpdateConfig { it.copy(specularRimIntensity = v) }
                            }
                        )
                        OpticalSliderRow(
                            label = "Lensing & dispersion",
                            valueText = "${(config.refractionIntensity * 100).roundToInt()}%",
                            value = config.refractionIntensity,
                            valueRange = 0.1f..1.4f,
                            onValueChange = { v ->
                                onUpdateConfig { it.copy(refractionIntensity = v) }
                            }
                        )
                        OpticalSliderRow(
                            label = "Animation speed",
                            valueText = "${String.format("%.2f", config.viscositySpeed)}×",
                            value = config.viscositySpeed,
                            valueRange = 0.5f..1.6f,
                            onValueChange = { v ->
                                onUpdateConfig { it.copy(viscositySpeed = v) }
                            }
                        )
                        OpticalSliderRow(
                            label = "Auto-hide timer",
                            valueText = "${String.format("%.1f", config.autoDismissSeconds)} s",
                            value = config.autoDismissSeconds,
                            valueRange = 2.0f..9.0f,
                            onValueChange = { v ->
                                onUpdateConfig { it.copy(autoDismissSeconds = v) }
                            }
                        )
                    }
                }
            }
        }
    }
}
