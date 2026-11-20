package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CanvasBlack
import com.example.ui.theme.FrostPrimary
import com.example.ui.theme.SurfaceOne
import com.example.ui.theme.SurfaceTwo
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * A mostly still backdrop. Motion is reserved for the droplet itself; a moving
 * full-screen shader behind a translucent panel makes text shimmer and reads
 * as decoration rather than material.
 */
@Composable
fun LiquidSilkBackdrop(
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0C0E12),
                        CanvasBlack,
                        Color(0xFF050607)
                    )
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.09f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.02f),
                    radius = size.width * 0.9f
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.025f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.1f, size.height * 0.85f),
                    radius = size.width * 0.75f
                )
            )
        }
    }
}

@Composable
fun LiquidGlassPanel(
    modifier: Modifier = Modifier,
    accentGlow: Color = FrostPrimary,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val baseModifier = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    SurfaceTwo.copy(alpha = 0.86f),
                    SurfaceOne.copy(alpha = 0.92f)
                )
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.10f),
                    accentGlow.copy(alpha = 0.10f),
                    Color.White.copy(alpha = 0.04f)
                )
            ),
            shape = shape
        )

    val interactiveModifier = if (onClick != null) {
        baseModifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(color = Color.White),
            onClick = onClick
        )
    } else {
        baseModifier
    }

    Column(
        modifier = interactiveModifier.padding(horizontal = 18.dp, vertical = 16.dp),
        content = content
    )
}

@Composable
fun OpticalSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = TextPrimary,
                activeTrackColor = FrostPrimary,
                inactiveTrackColor = Color.White.copy(alpha = 0.10f)
            )
        )
    }
}

@Composable
fun StatusPillBadge(
    text: String,
    isActive: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 9.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isActive) activeColor else TextMuted)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) TextPrimary else TextMuted
            )
        }
    }
}
