package com.kian.perficon.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = PixelInk,
    onPrimary = PixelSurface,
    primaryContainer = PixelBlueSoft,
    onPrimaryContainer = PixelInk,
    secondary = PixelBlue,
    onSecondary = PixelSurface,
    secondaryContainer = PixelGreenSoft,
    onSecondaryContainer = PixelInk,
    tertiary = PixelPurple,
    onTertiary = PixelSurface,
    tertiaryContainer = PixelOrangeSoft,
    onTertiaryContainer = PixelInk,
    background = PixelPaper,
    onBackground = PixelInk,
    surface = PixelSurface,
    onSurface = PixelInk,
    surfaceVariant = PixelPaper,
    onSurfaceVariant = Color(0xFF5C5B59),
    outline = PixelLine,
    error = PixelRed,
    errorContainer = PixelRedSoft,
    onErrorContainer = PixelInk
)

private val PixelShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(5.dp),
    medium = RoundedCornerShape(7.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(10.dp)
)

@Composable
fun PerficonTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = PixelShapes,
        content = content
    )
}
