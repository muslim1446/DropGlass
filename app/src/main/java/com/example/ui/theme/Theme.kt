package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val PixelLightColorScheme = lightColorScheme(
    primary = PixelLightPrimary,
    onPrimary = PixelLightOnPrimary,
    primaryContainer = PixelLightPrimaryContainer,
    onPrimaryContainer = PixelLightOnPrimaryContainer,
    secondary = PixelLightSecondary,
    onSecondary = Color.White,
    secondaryContainer = PixelLightSecondaryContainer,
    onSecondaryContainer = PixelLightOnSecondaryContainer,
    tertiary = PixelLightTertiary,
    onTertiary = Color.White,
    tertiaryContainer = PixelLightTertiaryContainer,
    onTertiaryContainer = Color(0xFF072711),
    background = PixelLightBackground,
    onBackground = PixelLightOnSurface,
    surface = PixelLightSurface,
    onSurface = PixelLightOnSurface,
    surfaceVariant = PixelLightSurfaceContainer,
    onSurfaceVariant = PixelLightOnSurfaceVariant,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = PixelLightSurfaceLow,
    surfaceContainer = PixelLightSurfaceContainer,
    surfaceContainerHigh = PixelLightSurfaceHigh,
    surfaceContainerHighest = PixelLightSurfaceHighest,
    outline = PixelLightOutline,
    outlineVariant = PixelLightOutlineVariant,
    error = Color(0xFFB3261E),
    onError = Color.White
)

private val PixelDarkColorScheme = darkColorScheme(
    primary = PixelDarkPrimary,
    onPrimary = PixelDarkOnPrimary,
    primaryContainer = PixelDarkPrimaryContainer,
    onPrimaryContainer = PixelDarkOnPrimaryContainer,
    secondary = PixelDarkSecondary,
    onSecondary = Color(0xFF003556),
    secondaryContainer = PixelDarkSecondaryContainer,
    onSecondaryContainer = PixelDarkOnSecondaryContainer,
    tertiary = PixelDarkTertiary,
    onTertiary = Color(0xFF003919),
    tertiaryContainer = PixelDarkTertiaryContainer,
    onTertiaryContainer = Color(0xFFC4EED0),
    background = PixelDarkBackground,
    onBackground = PixelDarkOnSurface,
    surface = PixelDarkSurface,
    onSurface = PixelDarkOnSurface,
    surfaceVariant = PixelDarkSurfaceContainer,
    onSurfaceVariant = PixelDarkOnSurfaceVariant,
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = PixelDarkSurfaceLow,
    surfaceContainer = PixelDarkSurfaceContainer,
    surfaceContainerHigh = PixelDarkSurfaceHigh,
    surfaceContainerHighest = PixelDarkSurfaceHighest,
    outline = PixelDarkOutline,
    outlineVariant = PixelDarkOutlineVariant,
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410)
)

val DropGlassShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun DropGlassTheme(
    useSystemDynamicColor: Boolean = true,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        useSystemDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> PixelDarkColorScheme
        else -> PixelLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DropGlassTypography,
        shapes = DropGlassShapes,
        content = content
    )
}
