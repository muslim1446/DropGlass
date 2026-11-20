package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DropGlassColorScheme = darkColorScheme(
    primary = FrostPrimary,
    onPrimary = Color(0xFF0A0C10),
    primaryContainer = Color(0xFF1D2129),
    onPrimaryContainer = FrostBright,
    secondary = AccentTeal,
    onSecondary = Color(0xFF071714),
    secondaryContainer = Color(0xFF132623),
    onSecondaryContainer = Color(0xFFD9F7F2),
    tertiary = AccentLilac,
    onTertiary = Color(0xFF161326),
    background = CanvasBlack,
    onBackground = TextPrimary,
    surface = SurfaceOne,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceTwo,
    onSurfaceVariant = TextSecondary,
    outline = Hairline,
    outlineVariant = Color(0xFF1C2028),
    error = AccentRose,
    onError = Color(0xFF2B0E13)
)

val DropGlassShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun DropGlassTheme(
    useSystemDynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val base = DropGlassColorScheme
    val colorScheme = if (useSystemDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val system = dynamicDarkColorScheme(context)
        base.copy(
            primary = system.primary,
            secondary = system.secondary,
            tertiary = system.tertiary
        )
    } else {
        base
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DropGlassTypography,
        shapes = DropGlassShapes,
        content = content
    )
}
