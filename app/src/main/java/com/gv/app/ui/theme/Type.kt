package com.gv.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val GvTypography = Typography(
    displaySmall   = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    titleLarge     = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleMedium    = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    titleSmall     = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge      = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodyMedium     = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    labelLarge     = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium    = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall     = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
)

val TimerDisplay = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 24.sp,
    fontWeight = FontWeight.Bold,
    fontFeatureSettings = "tnum",
)
