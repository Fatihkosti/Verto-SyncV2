package com.verto.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.verto.core.designsystem.R

val CairoFontFamily = FontFamily(
    Font(R.font.cairo_regular, FontWeight.Normal),
    Font(R.font.cairo_regular, FontWeight.Medium),
    Font(R.font.cairo_bold, FontWeight.SemiBold),
    Font(R.font.cairo_bold, FontWeight.Bold),
)

val TajawalFontFamily = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_regular, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.SemiBold),
    Font(R.font.tajawal_bold, FontWeight.Bold),
)

private fun vertoTextStyle(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
) = TextStyle(
    fontFamily = CairoFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
)

/** Arabic-first, compact mobile type scale. */
val VertoTypography = Typography(
    displayLarge = vertoTextStyle(48, 60, FontWeight.Bold),
    displayMedium = vertoTextStyle(40, 52, FontWeight.Bold),
    displaySmall = vertoTextStyle(36, 46, FontWeight.Bold),
    headlineLarge = vertoTextStyle(32, 42, FontWeight.Bold),
    headlineMedium = vertoTextStyle(28, 38, FontWeight.Bold),
    headlineSmall = vertoTextStyle(24, 34, FontWeight.Bold),
    titleLarge = vertoTextStyle(20, 30, FontWeight.Bold),
    titleMedium = vertoTextStyle(18, 28, FontWeight.Bold),
    titleSmall = vertoTextStyle(16, 24, FontWeight.SemiBold),
    bodyLarge = vertoTextStyle(16, 26),
    bodyMedium = vertoTextStyle(14, 22),
    bodySmall = vertoTextStyle(12, 18),
    labelLarge = vertoTextStyle(14, 20, FontWeight.Bold),
    labelMedium = vertoTextStyle(12, 18, FontWeight.SemiBold),
    labelSmall = vertoTextStyle(11, 16, FontWeight.SemiBold),
)

/** Stable semantic roles; screens should not invent a second typography hierarchy. */
object VertoTypographyRoles {
    val pageTitle = VertoTypography.headlineSmall
    val sectionTitle = VertoTypography.titleLarge
    val cardTitle = VertoTypography.titleMedium
    val body = VertoTypography.bodyLarge
    val bodySecondary = VertoTypography.bodyMedium
    val label = VertoTypography.labelLarge
    val metadata = VertoTypography.bodySmall
    val amount = VertoTypography.headlineSmall
    val action = VertoTypography.labelLarge
}
