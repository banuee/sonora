package com.sonora.player.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = SonoraDarkPrimary,
    onPrimary = SonoraDarkOnPrimary,
    primaryContainer = SonoraDarkPrimaryContainer,
    onPrimaryContainer = SonoraDarkOnPrimaryContainer,
    surface = SonoraDarkSurface,
    onSurface = SonoraDarkOnSurface,
    surfaceVariant = SonoraDarkSurfaceVariant,
    onSurfaceVariant = SonoraDarkOnSurfaceVariant,
    surfaceContainer = SonoraDarkSurfaceContainer,
    surfaceContainerHigh = SonoraDarkSurfaceContainerHigh
)

private val LightColorScheme = lightColorScheme(
    primary = SonoraLightPrimary,
    onPrimary = SonoraLightOnPrimary,
    primaryContainer = SonoraLightPrimaryContainer,
    onPrimaryContainer = SonoraLightOnPrimaryContainer,
    surface = SonoraLightSurface,
    onSurface = SonoraLightOnSurface,
    surfaceVariant = SonoraLightSurfaceVariant,
    onSurfaceVariant = SonoraLightOnSurfaceVariant,
    surfaceContainer = SonoraLightSurfaceContainer,
    surfaceContainerHigh = SonoraLightSurfaceContainerHigh
)

val SonoraShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SonoraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        shapes = SonoraShapes,
        content = content
    )
}
