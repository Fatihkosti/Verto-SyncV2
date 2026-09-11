package com.verto.app.feature.settings.presentation

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import com.verto.feature.settings.R
import com.verto.app.ui.theme.*
import com.verto.app.utils.InvoiceFont
import com.verto.app.utils.InvoiceTemplate
import com.verto.app.ui.components.VertoCard
import androidx.compose.foundation.layout.PaddingValues
import com.verto.app.ui.theme.VertoSpacing

internal fun InvoiceFont.toFontFamily(): FontFamily = when (this) {
    InvoiceFont.CAIRO   -> FontFamily(Font(R.font.cairo_regular),   Font(R.font.cairo_bold,   FontWeight.Bold))
    InvoiceFont.TAJAWAL -> FontFamily(Font(R.font.tajawal_regular), Font(R.font.tajawal_bold, FontWeight.Bold))
    InvoiceFont.AMIRI   -> FontFamily(Font(R.font.amiri_regular),   Font(R.font.amiri_bold,   FontWeight.Bold))
    InvoiceFont.ALMARAI -> FontFamily(Font(R.font.almarai_regular), Font(R.font.almarai_bold, FontWeight.Bold))
}

// ── ألوان قالب الفاتورة ────────────────────────────────────────────────────────

internal val ClassicAccent      = SettingsPrintPalette.cFF2563EB
internal val ModernAccent       = SettingsPrintPalette.cFF0F766E
internal val ProfessionalAccent = SettingsPrintPalette.cFF1E3A5F
internal val ThermalColor       = SettingsPrintPalette.cFF111111

// ── InvoicePreviewCard ─────────────────────────────────────────────────────────

@Composable
fun InvoicePreviewCard(
    template   : InvoiceTemplate,
    font       : InvoiceFont,
    fontSize   : Int,
    orgName    : String,
    orgAddress : String,
    orgTax     : String,
    orgFooter  : String,
    userName   : String,
    userPhone  : String
) {
    val ff        = font.toFontFamily()
    val bodySize  = fontSize.sp
    val smallSize = (fontSize - 2).coerceAtLeast(8).sp
    val titleSize = (fontSize + 3).sp

    Column(verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp6)) {
        // شريط المعاينة
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = SettingsDimensions.dp10, topEnd = SettingsDimensions.dp10))
                .background(BgCard)
                .padding(horizontal = SettingsDimensions.dp12, vertical = SettingsDimensions.dp8),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.dp6),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Visibility, null, tint = AccentPrimary, modifier = Modifier.size(SettingsDimensions.dp14))
                Text(androidx.compose.ui.res.stringResource(R.string.ds_2fa8ba8357cd, template.displayName), color = AccentPrimary, fontSize = SettingsTextScale.sp11, fontWeight = FontWeight.Bold)
            }
            Text(androidx.compose.ui.res.stringResource(R.string.ds_15e308e6cd25, font.arabicName, fontSize), color = TextMuted, fontSize = SettingsTextScale.sp10)
        }

        // الفاتورة
        VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(bottomStart = SettingsDimensions.dp12, bottomEnd = SettingsDimensions.dp12),
            colors   = CardDefaults.cardColors(containerColor = Color.White),
            border   = BorderStroke(SettingsDimensions.dp1, BorderColor)
        ) {
            when (template) {
                InvoiceTemplate.CLASSIC      -> ClassicPreview(orgName, orgAddress, orgTax, orgFooter, userName, ff, bodySize, smallSize, titleSize)
                InvoiceTemplate.MODERN       -> ModernPreview(orgName, orgAddress, orgTax, orgFooter, userName, ff, bodySize, smallSize, titleSize)
                InvoiceTemplate.PROFESSIONAL -> ProfessionalPreview(orgName, orgAddress, orgTax, orgFooter, userName, ff, bodySize, smallSize, titleSize)
                InvoiceTemplate.THERMAL      -> ThermalPreview(orgName, orgAddress, orgTax, orgFooter, userName, ff, bodySize, smallSize, titleSize)
            }
        }

        Text(
            androidx.compose.ui.res.stringResource(R.string.ds_e485c0db44f7),
            color    = TextMuted,
            fontSize = SettingsTextScale.sp9,
            modifier = Modifier.padding(horizontal = SettingsDimensions.dp4)
        )
    }
}

// ── PriceListPreviewCard ───────────────────────────────────────────────────────

@Composable
fun PriceListPreviewCard(
    template   : InvoiceTemplate,
    font       : InvoiceFont,
    fontSize   : Int,
    orgName    : String,
    orgAddress : String,
    orgTax     : String,
    orgFooter  : String,
    userName   : String
) {
    val ff        = font.toFontFamily()
    val bodySize  = fontSize.sp
    val smallSize = (fontSize - 2).coerceAtLeast(8).sp
    val titleSize = (fontSize + 3).sp

    Column(verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp6)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = SettingsDimensions.dp10, topEnd = SettingsDimensions.dp10))
                .background(BgCard)
                .padding(horizontal = SettingsDimensions.dp12, vertical = SettingsDimensions.dp8),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.dp6), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Visibility, null, tint = AccentPrimary, modifier = Modifier.size(SettingsDimensions.dp14))
                Text(androidx.compose.ui.res.stringResource(R.string.ds_2fa8ba8357cd, template.displayName), color = AccentPrimary, fontSize = SettingsTextScale.sp11, fontWeight = FontWeight.Bold)
            }
            Text(androidx.compose.ui.res.stringResource(R.string.ds_15e308e6cd25, font.arabicName, fontSize), color = TextMuted, fontSize = SettingsTextScale.sp10)
        }

        VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(bottomStart = SettingsDimensions.dp12, bottomEnd = SettingsDimensions.dp12),
            colors   = CardDefaults.cardColors(containerColor = Color.White),
            border   = BorderStroke(SettingsDimensions.dp1, BorderColor)
        ) {
            when (template) {
                InvoiceTemplate.CLASSIC      -> ClassicPriceListPreview(orgName, orgAddress, orgTax, orgFooter, userName, ff, bodySize, smallSize, titleSize)
                InvoiceTemplate.MODERN       -> ModernPriceListPreview(orgName, orgAddress, orgTax, orgFooter, userName, ff, bodySize, smallSize, titleSize)
                InvoiceTemplate.PROFESSIONAL -> ProfessionalPriceListPreview(orgName, orgAddress, orgTax, orgFooter, userName, ff, bodySize, smallSize, titleSize)
                InvoiceTemplate.THERMAL      -> ThermalPriceListPreview(orgName, orgAddress, orgTax, orgFooter, userName, ff, bodySize, smallSize, titleSize)
            }
        }

        Text(
            androidx.compose.ui.res.stringResource(R.string.ds_7781a1476e71, template.displayName),
            color    = TextMuted,
            fontSize = SettingsTextScale.sp9,
            modifier = Modifier.padding(horizontal = SettingsDimensions.dp4)
        )
    }
}

// ── Classic — كشف أسعار ────────────────────────────────────────────────────────
