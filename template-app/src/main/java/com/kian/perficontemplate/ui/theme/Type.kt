package com.kian.perficontemplate.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

fun getTemplateTypography(fontFamily: FontFamily): Typography {
    return Typography(
        displayLarge  = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.W400, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
        displayMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.W400, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = 0.sp),
        displaySmall  = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.W400, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = 0.sp),
        headlineLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.W400, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
        headlineMedium= TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.W400, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
        headlineSmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.W400, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
        titleLarge    = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.W400, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
        titleMedium   = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
        titleSmall    = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
        bodyLarge     = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.W400, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
        bodyMedium    = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.W400, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
        bodySmall     = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.W400, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
        labelLarge    = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
        labelMedium   = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
        labelSmall    = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
    )
}
