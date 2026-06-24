package com.kian.perficontemplate.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val TemplateColorScheme = lightColorScheme(
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
    small      = RoundedCornerShape(5.dp),
    medium     = RoundedCornerShape(7.dp),
    large      = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(10.dp)
)

@Composable
fun PerficonTemplateTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    val context = LocalContext.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = TemplateColorScheme.background.toArgb()
            window.navigationBarColor = TemplateColorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
        }
    }
    
    val fontId = context.resources.getIdentifier("fusion_pixel_zh_hans", "font", context.packageName)
    val fontFamily = if (fontId != 0) FontFamily(Font(fontId)) else FontFamily.Default
    val typography = getTemplateTypography(fontFamily)

    MaterialTheme(
        colorScheme = TemplateColorScheme,
        typography  = typography,
        shapes      = PixelShapes,
        content     = content
    )
}
