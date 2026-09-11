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

internal val ClassicPLBanner = SettingsPrintPalette.cFF1E3A5F
internal val ClassicPLHdr    = SettingsPrintPalette.cFF2C5282
internal val ClassicPLAccent = SettingsPrintPalette.cFF4299E1

@Composable
internal fun ClassicPriceListPreview(
    orgName    : String,
    orgAddress : String,
    orgTax     : String,
    orgFooter  : String,
    userName   : String,
    ff         : FontFamily,
    bodySize   : TextUnit,
    smallSize  : TextUnit,
    titleSize  : TextUnit
) {
    Column(Modifier.fillMaxWidth()) {
        // ── Banner ──
        Box(
            Modifier.fillMaxWidth().background(ClassicPLBanner).padding(horizontal = SettingsDimensions.dp16, vertical = SettingsDimensions.dp12),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp2)) {
                Text(orgName,    fontFamily = ff, fontSize = bodySize,  fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
                if (orgAddress.isNotBlank()) Text(orgAddress, fontFamily = ff, fontSize = smallSize, color = SettingsPrintPalette.cFFBDD5EA, textAlign = TextAlign.Center)
                if (userName.isNotBlank())   Text(userName,   fontFamily = ff, fontSize = smallSize, color = SettingsPrintPalette.cFFBDD5EA, textAlign = TextAlign.Center)
            }
        }
        Box(Modifier.fillMaxWidth().height(SettingsDimensions.dp3).background(ClassicPLAccent))
        // ── Column headers ──
        Row(Modifier.fillMaxWidth().background(ClassicPLHdr).padding(vertical = SettingsDimensions.dp5, horizontal = SettingsDimensions.dp8)) {
            Text(androidx.compose.ui.res.stringResource(R.string.ds_3a4ffd0856f9), Modifier.weight(0.6f), fontFamily = ff, fontSize = smallSize, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.End)
            Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_name), Modifier.weight(3f),   fontFamily = ff, fontSize = smallSize, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.End)
            Text(androidx.compose.ui.res.stringResource(R.string.ds_b6aa0c7d7a21), Modifier.weight(1.5f), fontFamily = ff, fontSize = smallSize, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.End)
        }
        PriceListSampleRows(ff, smallSize, SettingsPrintPalette.cFF111827, accentColor = SettingsPrintPalette.cFF1E3A5F, altBg = SettingsPrintPalette.cFFEBF0F8)
    }
}

// ── Modern — كشف أسعار ─────────────────────────────────────────────────────────

internal val ModernPLBanner = SettingsPrintPalette.cFF134E4A
internal val ModernPLHdr    = SettingsPrintPalette.cFF0D9488
internal val ModernPLAccent = SettingsPrintPalette.cFF2DD4BF

@Composable
internal fun ModernPriceListPreview(
    orgName    : String,
    orgAddress : String,
    orgTax     : String,
    orgFooter  : String,
    userName   : String,
    ff         : FontFamily,
    bodySize   : TextUnit,
    smallSize  : TextUnit,
    titleSize  : TextUnit
) {
    Column(Modifier.fillMaxWidth()) {
        // ── Banner ──
        Box(
            Modifier.fillMaxWidth().background(ModernPLBanner).padding(horizontal = SettingsDimensions.dp16, vertical = SettingsDimensions.dp12),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp2)) {
                Text(orgName,    fontFamily = ff, fontSize = bodySize,  fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
                if (orgAddress.isNotBlank()) Text(orgAddress, fontFamily = ff, fontSize = smallSize, color = SettingsPrintPalette.cFFCCFBF1, textAlign = TextAlign.Center)
                if (userName.isNotBlank())   Text(userName,   fontFamily = ff, fontSize = smallSize, color = SettingsPrintPalette.cFFCCFBF1, textAlign = TextAlign.Center)
            }
        }
        Box(Modifier.fillMaxWidth().height(SettingsDimensions.dp4).background(ModernPLAccent))
        // ── Column headers ──
        Row(Modifier.fillMaxWidth().background(ModernPLHdr).padding(vertical = SettingsDimensions.dp5, horizontal = SettingsDimensions.dp8)) {
            Text(androidx.compose.ui.res.stringResource(R.string.ds_3a4ffd0856f9), Modifier.weight(0.6f), fontFamily = ff, fontSize = smallSize, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.End)
            Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_name), Modifier.weight(3f),   fontFamily = ff, fontSize = smallSize, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.End)
            Text(androidx.compose.ui.res.stringResource(R.string.ds_b6aa0c7d7a21), Modifier.weight(1.5f), fontFamily = ff, fontSize = smallSize, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.End)
        }
        PriceListSampleRows(ff, smallSize, SettingsPrintPalette.cFF0D1F17, accentColor = SettingsPrintPalette.cFF065F46, altBg = SettingsPrintPalette.cFFD1FAF0)
    }
}

// ── Professional — كشف أسعار ───────────────────────────────────────────────────

internal val ProfPLBanner = SettingsPrintPalette.cFF1E1B4B
internal val ProfPLHdr    = SettingsPrintPalette.cFF4C1D95
internal val ProfPLAccent = SettingsPrintPalette.cFFA78BFA

@Composable
internal fun ProfessionalPriceListPreview(
    orgName    : String,
    orgAddress : String,
    orgTax     : String,
    orgFooter  : String,
    userName   : String,
    ff         : FontFamily,
    bodySize   : TextUnit,
    smallSize  : TextUnit,
    titleSize  : TextUnit
) {
    Column(Modifier.fillMaxWidth()) {
        // ── Banner ──
        Box(
            Modifier.fillMaxWidth().background(ProfPLBanner).padding(horizontal = SettingsDimensions.dp16, vertical = SettingsDimensions.dp12),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp2)) {
                Text(orgName,    fontFamily = ff, fontSize = bodySize,  fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
                if (orgAddress.isNotBlank()) Text(orgAddress, fontFamily = ff, fontSize = smallSize, color = SettingsPrintPalette.cFFDDD6FE, textAlign = TextAlign.Center)
                if (userName.isNotBlank())   Text(userName,   fontFamily = ff, fontSize = smallSize, color = SettingsPrintPalette.cFFDDD6FE, textAlign = TextAlign.Center)
            }
        }
        Box(Modifier.fillMaxWidth().height(SettingsDimensions.dp4).background(ProfPLAccent))
        // ── Column headers ──
        Row(Modifier.fillMaxWidth().background(ProfPLHdr).padding(vertical = SettingsDimensions.dp5, horizontal = SettingsDimensions.dp8)) {
            Text(androidx.compose.ui.res.stringResource(R.string.ds_3a4ffd0856f9), Modifier.weight(0.6f), fontFamily = ff, fontSize = smallSize, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.End)
            Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_name), Modifier.weight(3f),   fontFamily = ff, fontSize = smallSize, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.End)
            Text(androidx.compose.ui.res.stringResource(R.string.ds_b6aa0c7d7a21), Modifier.weight(1.5f), fontFamily = ff, fontSize = smallSize, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.End)
        }
        PriceListSampleRows(ff, smallSize, SettingsPrintPalette.cFF0F0A2E, accentColor = SettingsPrintPalette.cFF5B21B6, altBg = SettingsPrintPalette.cFFEDE9FE)
    }
}

// ── Thermal/Luxury — كشف أسعار ─────────────────────────────────────────────────

internal val LuxuryPLBanner = SettingsPrintPalette.cFF0D0D0D
internal val LuxuryPLHdr    = SettingsPrintPalette.cFF1A1208
internal val LuxuryPLGold   = SettingsPrintPalette.cFFD4AF37

@Composable
internal fun ThermalPriceListPreview(
    orgName    : String,
    orgAddress : String,
    orgTax     : String,
    orgFooter  : String,
    userName   : String,
    ff         : FontFamily,
    bodySize   : TextUnit,
    smallSize  : TextUnit,
    titleSize  : TextUnit
) {
    Column(Modifier.fillMaxWidth()) {
        // ── Gold top line ──
        Box(Modifier.fillMaxWidth().height(SettingsDimensions.dp3).background(LuxuryPLGold))
        // ── Banner ──
        Box(
            Modifier.fillMaxWidth().background(LuxuryPLBanner).padding(horizontal = SettingsDimensions.dp16, vertical = SettingsDimensions.dp12),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp2)) {
                Text(orgName,    fontFamily = ff, fontSize = bodySize,  fontWeight = FontWeight.Bold, color = LuxuryPLGold, textAlign = TextAlign.Center)
                if (orgAddress.isNotBlank()) Text(orgAddress, fontFamily = ff, fontSize = smallSize, color = SettingsPrintPalette.cFFC9A84C, textAlign = TextAlign.Center)
                if (userName.isNotBlank())   Text(userName,   fontFamily = ff, fontSize = smallSize, color = SettingsPrintPalette.cFFC9A84C, textAlign = TextAlign.Center)
            }
        }
        Box(Modifier.fillMaxWidth().height(SettingsDimensions.dp4).background(LuxuryPLGold))
        // ── Column headers ──
        Row(Modifier.fillMaxWidth().background(LuxuryPLHdr).padding(vertical = SettingsDimensions.dp5, horizontal = SettingsDimensions.dp8)) {
            Text(androidx.compose.ui.res.stringResource(R.string.ds_3a4ffd0856f9), Modifier.weight(0.6f), fontFamily = ff, fontSize = smallSize, fontWeight = FontWeight.Bold, color = LuxuryPLGold, textAlign = TextAlign.End)
            Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_name), Modifier.weight(3f),   fontFamily = ff, fontSize = smallSize, fontWeight = FontWeight.Bold, color = LuxuryPLGold, textAlign = TextAlign.End)
            Text(androidx.compose.ui.res.stringResource(R.string.ds_b6aa0c7d7a21), Modifier.weight(1.5f), fontFamily = ff, fontSize = smallSize, fontWeight = FontWeight.Bold, color = LuxuryPLGold, textAlign = TextAlign.End)
        }
        PriceListSampleRows(ff, smallSize, SettingsPrintPalette.cFF1A1208, accentColor = SettingsPrintPalette.cFF78570A, altBg = SettingsPrintPalette.cFFFFF3CC)
    }
}

// ── PriceListSampleRows ────────────────────────────────────────────────────────

@Composable
internal fun PriceListSampleRows(
    ff          : FontFamily,
    textSize    : TextUnit,
    color       : Color,
    accentColor : Color = color,
    altBg       : Color = Color.Transparent,
    paddingH    : Int   = 8
) {
    listOf(
        Triple("1", "صنف أول",   "50.00"),
        Triple("2", "صنف ثانٍ",  "30.00"),
        Triple("3", "صنف ثالث",  "75.00")
    ).forEachIndexed { idx, (num, name, price) ->
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (idx % 2 != 0) altBg else Color.Transparent)
                .padding(horizontal = paddingH.dp, vertical = SettingsDimensions.dp3)
        ) {
            Text(num,   Modifier.weight(0.6f), fontFamily = ff, fontSize = textSize, color = accentColor, textAlign = TextAlign.End)
            Text(name,  Modifier.weight(3f),   fontFamily = ff, fontSize = textSize, color = color)
            Text(price, Modifier.weight(1.5f), fontFamily = ff, fontSize = textSize, color = accentColor, textAlign = TextAlign.End)
        }
    }
}

// ── Classic ────────────────────────────────────────────────────────────────────
