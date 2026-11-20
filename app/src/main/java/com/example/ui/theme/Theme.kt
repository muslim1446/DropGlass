package com.example.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val DropGlassDarkColorScheme = darkColorScheme(
    primary = IceCyanPrimary,
    onPrimary = Color(0xFF041A29),
    primaryContainer = Color(0xFF10324E),
    onPrimaryContainer = IceCyanBright,
    secondary = PrismAqua,
    onSecondary = Color(0xFF03211E),
    secondaryContainer = Color(0xFF0E3036),
    onSecondaryContainer = Color(0xFFB9FFF5),
    tertiary = PrismViolet,
    onTertiary = Color(0xFF0B1433),
    background = ObsidianVoid,
    onBackground = GlassTextPrimary,
    surface = ObsidianSurface,
    onSurface = GlassTextPrimary,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = GlassTextSecondary,
    outline = ObsidianOutline,
    outlineVariant = Color(0xFF1D293D),
    error = LiquidCrimson,
    onError = Color(0xFF2B040E)
)

val DropGlassShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun DropGlassTheme(
    useSystemDynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = if (useSystemDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context).copy(
            background = ObsidianVoid,
            surface = ObsidianSurface,
            surfaceVariant = ObsidianSurfaceHigh
        )
    } else {
        DropGlassDarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DropGlassTypography,
        shapes = DropGlassShapes,
        content = content
    )
}
