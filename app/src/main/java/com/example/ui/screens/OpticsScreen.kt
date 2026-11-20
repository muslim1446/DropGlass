package com.example.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraFront
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LensBlur
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.CameraPreset
import com.example.model.DropStage
import com.example.model.GlassOpticsConfig
import com.example.model.GlassStyle
import com.example.ui.MainViewModel
import com.example.ui.StudioUiState
import com.example.ui.components.LiquidGlassPanel
import com.example.ui.components.OpticalSliderRow
import com.example.ui.glass.WaterDropOverlayHost
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.FrostPrimary
import com.example.ui.theme.AccentSage
import com.example.ui.theme.CanvasBlack
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
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val config = uiState.config
    val cutout = uiState.detectedCutout
    val calibrationSample = remember { MainViewModel.sampleDrops().first() }

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
            // Live Top Selfie-Camera Calibration Ring + Small Drop Preview at y = 0
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((statusTop + 82.dp).coerceAtLeast(112.dp)),
                    contentAlignment = Alignment.TopCenter
                ) {
                    WaterDropOverlayHost(
                        notification = calibrationSample,
                        config = config,
                        detectedCutout = cutout,
                        forcedStage = DropStage.SMALL_DROP,
                        showCameraRingGuide = true
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Camera & Optics",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Watch the live lens ring above align directly around your OPPO CPH2773 selfie camera as you adjust.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            // Hardware Cutout Telemetry + CPH2773 One-Tap Preset
            item {
                LiquidGlassPanel(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    accentGlow = FrostPrimary
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CameraFront,
                            contentDescription = null,
                            tint = FrostPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Selfie Cutout Telemetry",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (cutout.hasHardwareCutout) {
                            "Hardware Cutout: ${cutout.deviceModel} · ${cutout.widthDp.roundToInt()}×${cutout.heightDp.roundToInt()}dp · Top ${cutout.topDp.roundToInt()}dp · Center Δ${cutout.centerXdp.roundToInt()}dp"
                        } else {
                            "Device: ${cutout.deviceModel.ifBlank { "Android" }} (${cutout.screenWidthDp.roundToInt()}×${cutout.screenHeightDp.roundToInt()}dp) · Profile: ${config.preset.label}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (cutout.hasHardwareCutout) AccentSage else TextSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onApplyCph2773,
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (config.preset == CameraPreset.OPPO_CPH2773) {
                                    FrostPrimary
                                } else {
                                    Color.White.copy(alpha = 0.12f)
                                },
                                contentColor = if (config.preset == CameraPreset.OPPO_CPH2773) {
                                    CanvasBlack
                                } else {
                                    TextPrimary
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("preset_cph2773_button")
                        ) {
                            Text(
                                text = "OPPO CPH2773",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        OutlinedButton(
                            onClick = onApplyAutoCutout,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("preset_auto_cutout_button")
                        ) {
                            Text(
                                text = "Auto Hardware",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            // Apple Liquid Glass Material Finish
            item {
                LiquidGlassPanel(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    accentGlow = FrostPrimary
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LensBlur,
                            contentDescription = null,
                            tint = FrostPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Liquid Glass Material",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassStyle.entries.forEach { style ->
                            val selected = config.glassStyle == style
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (selected) FrostPrimary.copy(alpha = 0.16f)
                                        else Color.White.copy(alpha = 0.04f)
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) FrostPrimary else Color.White.copy(alpha = 0.10f),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(color = FrostPrimary)
                                    ) { onSelectGlassStyle(style) }
                                    .padding(horizontal = 14.dp)
                                    .testTag("glass_style_${style.name.lowercase()}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = style.displayName,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = style.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = FrostPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Cutout & Drop Geometry Sliders
            item {
                LiquidGlassPanel(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = "Punch-Hole Geometry",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OpticalSliderRow(
                        label = "Camera Radius",
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
                        label = "Top Bezel Offset",
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
                        label = "Horizontal Alignment",
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
                        label = "Drop Gap Below Selfie",
                        valueText = "${config.dropGapDp.roundToInt()} dp",
                        value = config.dropGapDp,
                        valueRange = 2f..22f,
                        onValueChange = { v ->
                            onUpdateConfig { it.copy(dropGapDp = v) }
                        }
                    )
                }
            }

            // Optical Refraction & Timing Sliders
            item {
                LiquidGlassPanel(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = "Light & Surface Tension",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OpticalSliderRow(
                        label = "Specular Rim Glint",
                        valueText = "${(config.specularRimIntensity * 100).roundToInt()}%",
                        value = config.specularRimIntensity,
                        valueRange = 0.2f..1.3f,
                        onValueChange = { v ->
                            onUpdateConfig { it.copy(specularRimIntensity = v) }
                        }
                    )
                    OpticalSliderRow(
                        label = "Edge Refraction & Prism",
                        valueText = "${(config.refractionIntensity * 100).roundToInt()}%",
                        value = config.refractionIntensity,
                        valueRange = 0.1f..1.4f,
                        onValueChange = { v ->
                            onUpdateConfig { it.copy(refractionIntensity = v) }
                        }
                    )
                    OpticalSliderRow(
                        label = "Window Blur Depth",
                        valueText = "${config.blurRadiusDp.roundToInt()} dp",
                        value = config.blurRadiusDp,
                        valueRange = 8f..52f,
                        onValueChange = { v ->
                            onUpdateConfig { it.copy(blurRadiusDp = v) }
                        }
                    )
                    OpticalSliderRow(
                        label = "Liquid Viscosity Speed",
                        valueText = "${String.format("%.2f", config.viscositySpeed)}×",
                        value = config.viscositySpeed,
                        valueRange = 0.5f..1.6f,
                        onValueChange = { v ->
                            onUpdateConfig { it.copy(viscositySpeed = v) }
                        }
                    )
                    OpticalSliderRow(
                        label = "Auto-Retract Timer",
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
