package com.example.ui.components

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.glass.LiquidGlassShaders
import com.example.ui.theme.GlassTextMuted
import com.example.ui.theme.GlassTextPrimary
import com.example.ui.theme.GlassTextSecondary
import com.example.ui.theme.IceCyanPrimary
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid

@Composable
fun LiquidSilkBackdrop(
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "silkTime")
    val timeSec by transition.animateFloat(
        initialValue = 0f,
        targetValue = 62.83f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 36000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shaderTime"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val brush = LiquidGlassShaders.createSilkBackdropBrush(
                widthPx = size.width,
                heightPx = size.height,
                timeSeconds = timeSec,
                accentColor = accentColor
            )
            drawRect(brush = brush)
        } else {
            drawRect(color = ObsidianVoid)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.22f),
                        Color(0xFF0A1B33).copy(alpha = 0.4f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.14f),
                    radius = size.width * 0.85f
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1A4B8C).copy(alpha = 0.26f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.85f, size.height * 0.58f),
                    radius = size.width * 0.7f
                )
            )
        }
    }
}

@Composable
fun LiquidGlassPanel(
    modifier: Modifier = Modifier,
    accentGlow: Color = IceCyanPrimary,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(26.dp)
    val baseModifier = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xCC121B2B),
                    ObsidianSurface.copy(alpha = 0.88f)
                )
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.26f),
                    accentGlow.copy(alpha = 0.18f),
                    Color.White.copy(alpha = 0.06f)
                )
            ),
            shape = shape
        )

    val interactiveModifier = if (onClick != null) {
        baseModifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(color = accentGlow),
            onClick = onClick
        )
    } else {
        baseModifier
    }

    Column(
        modifier = interactiveModifier.padding(18.dp),
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
                color = GlassTextPrimary
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                color = IceCyanPrimary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = IceCyanPrimary,
                inactiveTrackColor = Color.White.copy(alpha = 0.12f)
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
    val pillColor = if (isActive) activeColor else GlassTextMuted
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(pillColor.copy(alpha = 0.14f))
            .border(0.8.dp, pillColor.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
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
                    .background(pillColor)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) GlassTextPrimary else GlassTextSecondary
            )
        }
    }
}
