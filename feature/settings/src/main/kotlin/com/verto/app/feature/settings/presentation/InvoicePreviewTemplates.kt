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

@Composable
internal fun ClassicPreview(
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
    Column(
        Modifier.fillMaxWidth().padding(SettingsDimensions.dp16),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp6)
    ) {
        // شعار
        Box(
            Modifier.size(SettingsDimensions.dp48).clip(RoundedCornerShape(SettingsDimensions.dp10)).background(ClassicAccent.copy(0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Business, null, tint = ClassicAccent, modifier = Modifier.size(SettingsDimensions.dp26))
        }

        Text(orgName,    fontFamily = ff, fontSize = titleSize, fontWeight = FontWeight.Bold, color = Color.Black, textAlign = TextAlign.Center)
        Text(orgAddress, fontFamily = ff, fontSize = smallSize, color = Color.Gray, textAlign = TextAlign.Center)
        if (orgTax.isNotBlank()) Text(androidx.compose.ui.res.stringResource(R.string.ds_c25cca87f6aa, orgTax), fontFamily = ff, fontSize = smallSize, color = Color.Gray)

        HorizontalDivider(color = ClassicAccent.copy(0.2f), thickness = SettingsDimensions.dp1)

        Text(androidx.compose.ui.res.stringResource(R.string.ds_80151b526402), fontFamily = ff, fontSize = bodySize, fontWeight = FontWeight.Bold, color = ClassicAccent)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(androidx.compose.ui.res.stringResource(R.string.ds_20f8c0e15895),          fontFamily = ff, fontSize = smallSize, color = Color.Gray)
            Text(androidx.compose.ui.res.stringResource(R.string.ds_13ac16b30e80), fontFamily = ff, fontSize = smallSize, color = Color.Gray)
        }
        Row(Modifier.fillMaxWidth()) {
            Text(androidx.compose.ui.res.stringResource(R.string.ds_74734c95fb59), fontFamily = ff, fontSize = smallSize, color = Color.Gray)
        }

        HorizontalDivider(color = Color.LightGray.copy(0.6f))

        // رأس الجدول
        Row(
            Modifier.fillMaxWidth().background(ClassicAccent.copy(0.06f)).padding(vertical = SettingsDimensions.dp4, horizontal = SettingsDimensions.dp4)
        ) {
            Text(androidx.compose.ui.res.stringResource(R.string.ds_2a4bfa9d9d0e),   Modifier.weight(3f), fontFamily = ff, fontSize = smallSize, fontWeight = FontWeight.Bold, color = Color.Black)
            Text(androidx.compose.ui.res.stringResource(R.string.ds_a95134401afe),  Modifier.weight(1f), fontFamily = ff, fontSize = smallSize, fontWeight = FontWeight.Bold, color = Color.Black, textAlign = TextAlign.Center)
            Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_sum), Modifier.weight(1.5f), fontFamily = ff, fontSize = smallSize, fontWeight = FontWeight.Bold, color = Color.Black, textAlign = TextAlign.End)
        }
        InvoiceSampleRows(ff, smallSize, Color.DarkGray)

        HorizontalDivider(color = ClassicAccent.copy(0.2f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(androidx.compose.ui.res.stringResource(R.string.ds_d14f6a42eb1c), fontFamily = ff, fontSize = bodySize, fontWeight = FontWeight.Bold, color = Color.Black)
            Text(androidx.compose.ui.res.stringResource(R.string.ds_3fb3e7012797),    fontFamily = ff, fontSize = bodySize, fontWeight = FontWeight.Bold, color = ClassicAccent)
        }

        HorizontalDivider(color = Color.LightGray.copy(0.4f))
        Text(orgFooter, fontFamily = ff, fontSize = smallSize, color = Color.Gray, textAlign = TextAlign.Center)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(userName, fontFamily = ff, fontSize = smallSize, color = Color.Gray)
            SignatureBox(ff)
        }
    }
}

// ── Modern ─────────────────────────────────────────────────────────────────────

@Composable
internal fun ModernPreview(
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
        // شريط علوي
        Box(
            Modifier.fillMaxWidth().background(ModernAccent).padding(horizontal = SettingsDimensions.dp16, vertical = SettingsDimensions.dp14)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text(orgName,    fontFamily = ff, fontSize = bodySize,  fontWeight = FontWeight.Bold, color = Color.White)
                    Text(orgAddress, fontFamily = ff, fontSize = smallSize, color = Color.White.copy(0.8f))
                }
                Box(
                    Modifier.size(SettingsDimensions.dp40).clip(RoundedCornerShape(SettingsDimensions.dp8)).background(Color.White.copy(0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Business, null, tint = Color.White, modifier = Modifier.size(SettingsDimensions.dp22))
                }
            }
        }

        // عمودان
        Row(
            Modifier.fillMaxWidth().padding(SettingsDimensions.dp12),
            horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.dp8)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp3)) {
                Text(androidx.compose.ui.res.stringResource(R.string.ds_4b56bc3ee3fb), fontFamily = ff, fontSize = smallSize, fontWeight = FontWeight.Bold, color = ModernAccent)
                if (orgTax.isNotBlank()) Text(androidx.compose.ui.res.stringResource(R.string.ds_bafd0c38d6a7, orgTax), fontFamily = ff, fontSize = smallSize, color = Color.Gray)
                Text(userName, fontFamily = ff, fontSize = smallSize, color = Color.Gray)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp3)) {
                Text(androidx.compose.ui.res.stringResource(R.string.ds_80151b526402), fontFamily = ff, fontSize = smallSize, fontWeight = FontWeight.Bold, color = ModernAccent)
                Text(androidx.compose.ui.res.stringResource(R.string.ds_20f8c0e15895),           fontFamily = ff, fontSize = smallSize, color = Color.Gray)
                Text(androidx.compose.ui.res.stringResource(R.string.ds_7fe18368ad1e),   fontFamily = ff, fontSize = smallSize, color = Color.Gray)
            }
        }

        HorizontalDivider(color = ModernAccent.copy(0.25f), thickness = SettingsDimensions.dp2)

        Column(Modifier.padding(SettingsDimensions.dp12), verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp4)) {
            Row(Modifier.fillMaxWidth().background(ModernAccent.copy(0.07f)).padding(vertical = SettingsDimensions.dp4, horizontal = SettingsDimensions.dp4)) {
                Text(androidx.compose.ui.res.stringResource(R.string.ds_2a4bfa9d9d0e),   Modifier.weight(3f), fontFamily = ff, fontSize = smallSize, fontWeight = FontWeight.Bold, color = Color.Black)
                Text(androidx.compose.ui.res.stringResource(R.string.ds_a95134401afe),  Modifier.weight(1f), fontFamily = ff, fontSize = smallSize, fontWeight = FontWeight.Bold, color = Color.Black, textAlign = TextAlign.Center)
                Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_sum), Modifier.weight(1.5f), fontFamily = ff, fontSize = smallSize, fontWeight = FontWeight.Bold, color = Color.Black, textAlign = TextAlign.End)
            }
            InvoiceSampleRows(ff, smallSize, Color.DarkGray)
            HorizontalDivider(color = Color.LightGray.copy(0.5f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(androidx.compose.ui.res.stringResource(R.string.ds_7ce64e47fa4a), fontFamily = ff, fontSize = bodySize, fontWeight = FontWeight.Bold, color = Color.Black)
                Text(androidx.compose.ui.res.stringResource(R.string.ds_3fb3e7012797),     fontFamily = ff, fontSize = bodySize, fontWeight = FontWeight.Bold, color = ModernAccent)
            }
        }

        Box(Modifier.fillMaxWidth().background(ModernAccent.copy(0.08f)).padding(SettingsDimensions.dp8)) {
            Text(orgFooter, fontFamily = ff, fontSize = smallSize, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ── Professional ───────────────────────────────────────────────────────────────

@Composable
internal fun ProfessionalPreview(
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
    Column(Modifier.fillMaxWidth().padding(SettingsDimensions.dp16), verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp8)) {
        // الرأس: شعار يمين + عنوان يسار
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.Top
        ) {
            Column {
                Text(androidx.compose.ui.res.stringResource(R.string.ds_80151b526402),       fontFamily = ff, fontSize = titleSize, fontWeight = FontWeight.Bold, color = ProfessionalAccent)
                Text(androidx.compose.ui.res.stringResource(R.string.ds_5b4621538771), fontFamily = ff, fontSize = smallSize, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Box(
                    Modifier.size(SettingsDimensions.dp44).clip(RoundedCornerShape(SettingsDimensions.dp8)).background(ProfessionalAccent.copy(0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Business, null, tint = ProfessionalAccent, modifier = Modifier.size(SettingsDimensions.dp24))
                }
                Text(orgName, fontFamily = ff, fontSize = smallSize, fontWeight = FontWeight.Bold, color = ProfessionalAccent)
                Text(orgAddress, fontFamily = ff, fontSize = (smallSize.value - 1).coerceAtLeast(7f).sp, color = Color.Gray)
            }
        }

        // خط فاصل غليظ
        Box(Modifier.fillMaxWidth().height(SettingsDimensions.dp3).background(ProfessionalAccent))

        // بيانات العميل في إطار
        Box(
            Modifier.fillMaxWidth()
                .border(SettingsDimensions.dp1, Color.LightGray, RoundedCornerShape(SettingsDimensions.dp6))
                .padding(SettingsDimensions.dp10)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp3)) {
                Text(androidx.compose.ui.res.stringResource(R.string.ds_43afc69a3c52),         fontFamily = ff, fontSize = smallSize, fontWeight = FontWeight.Bold, color = ProfessionalAccent)
                Text(androidx.compose.ui.res.stringResource(R.string.ds_d7d9343e7bd1),    fontFamily = ff, fontSize = smallSize, color = Color.Gray)
                Text(androidx.compose.ui.res.stringResource(R.string.ds_3a2205ac11c5),    fontFamily = ff, fontSize = smallSize, color = Color.Gray)
            }
        }

        // الجدول
        Column {
            Row(
                Modifier.fillMaxWidth().background(ProfessionalAccent).padding(vertical = SettingsDimensions.dp6, horizontal = SettingsDimensions.dp6)
            ) {
                Text(androidx.compose.ui.res.stringResource(R.string.ds_2a4bfa9d9d0e),   Modifier.weight(3f), fontFamily = ff, fontSize = smallSize, fontWeight = FontWeight.Bold, color = Color.White)
                Text(androidx.compose.ui.res.stringResource(R.string.ds_a95134401afe),  Modifier.weight(1f), fontFamily = ff, fontSize = smallSize, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
                Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_sum), Modifier.weight(1.5f), fontFamily = ff, fontSize = smallSize, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.End)
            }
            InvoiceSampleRows(ff, smallSize, Color.DarkGray, paddingH = 6)
            HorizontalDivider(color = Color.LightGray.copy(0.5f))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = SettingsDimensions.dp6, vertical = SettingsDimensions.dp4),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(androidx.compose.ui.res.stringResource(R.string.ds_d14f6a42eb1c),fontFamily = ff, fontSize = bodySize, fontWeight = FontWeight.Bold, color = ProfessionalAccent)
                Text(androidx.compose.ui.res.stringResource(R.string.ds_3fb3e7012797),    fontFamily = ff, fontSize = bodySize, fontWeight = FontWeight.Bold, color = ProfessionalAccent)
            }
        }

        // التذييل والتوقيع
        HorizontalDivider(color = Color.LightGray.copy(0.4f))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.Bottom
        ) {
            Column(Modifier.weight(1f)) {
                if (orgTax.isNotBlank()) Text(androidx.compose.ui.res.stringResource(R.string.ds_bafd0c38d6a7, orgTax), fontFamily = ff, fontSize = smallSize, color = Color.Gray)
                Text(orgFooter, fontFamily = ff, fontSize = smallSize, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SignatureBox(ff, width = 60)
                Text(userName, fontFamily = ff, fontSize = smallSize, color = Color.Gray)
            }
        }
    }
}

// ── Thermal ────────────────────────────────────────────────────────────────────

@Composable
internal fun ThermalPreview(
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
    Column(
        Modifier.fillMaxWidth().padding(horizontal = SettingsDimensions.dp28, vertical = SettingsDimensions.dp14),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp5)
    ) {
        Text(orgName,    fontFamily = ff, fontSize = bodySize,  fontWeight = FontWeight.Bold, color = ThermalColor, textAlign = TextAlign.Center)
        Text(orgAddress, fontFamily = ff, fontSize = smallSize, color = Color.Gray, textAlign = TextAlign.Center)
        if (orgTax.isNotBlank()) Text(androidx.compose.ui.res.stringResource(R.string.ds_bafd0c38d6a7, orgTax), fontFamily = ff, fontSize = smallSize, color = Color.Gray, textAlign = TextAlign.Center)

        ThermalDivider()

        Text(androidx.compose.ui.res.stringResource(R.string.ds_80151b526402),      fontFamily = ff, fontSize = bodySize,  fontWeight = FontWeight.Bold, color = ThermalColor, textAlign = TextAlign.Center)
        Text(androidx.compose.ui.res.stringResource(R.string.ds_0cfd78e2fe5c), fontFamily = ff, fontSize = smallSize, color = Color.Gray, textAlign = TextAlign.Center)
        Text(androidx.compose.ui.res.stringResource(R.string.ds_74734c95fb59), fontFamily = ff, fontSize = smallSize, color = Color.Gray, textAlign = TextAlign.Center)

        ThermalDivider()

        // بنود
        Row(Modifier.fillMaxWidth()) {
            Text(androidx.compose.ui.res.stringResource(R.string.ds_2a4bfa9d9d0e),   Modifier.weight(2.5f), fontFamily = ff, fontSize = smallSize, fontWeight = FontWeight.Bold, color = ThermalColor)
            Text(androidx.compose.ui.res.stringResource(R.string.ds_66dcc319bde3),       Modifier.weight(0.5f), fontFamily = ff, fontSize = smallSize, fontWeight = FontWeight.Bold, color = ThermalColor, textAlign = TextAlign.Center)
            Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_sum), Modifier.weight(1.5f), fontFamily = ff, fontSize = smallSize, fontWeight = FontWeight.Bold, color = ThermalColor, textAlign = TextAlign.End)
        }
        listOf("صنف أول" to "50.00", "صنف ثانٍ" to "60.00").forEach { (name, price) ->
            Row(Modifier.fillMaxWidth()) {
                Text(name,  Modifier.weight(2.5f), fontFamily = ff, fontSize = smallSize, color = ThermalColor)
                Text(androidx.compose.ui.res.stringResource(R.string.ds_356a192b7913),   Modifier.weight(0.5f), fontFamily = ff, fontSize = smallSize, color = ThermalColor, textAlign = TextAlign.Center)
                Text(price, Modifier.weight(1.5f), fontFamily = ff, fontSize = smallSize, color = ThermalColor, textAlign = TextAlign.End)
            }
        }

        ThermalDivider()

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_total), fontFamily = ff, fontSize = bodySize, fontWeight = FontWeight.Bold, color = ThermalColor)
            Text(androidx.compose.ui.res.stringResource(R.string.ds_3fb3e7012797),   fontFamily = ff, fontSize = bodySize, fontWeight = FontWeight.Bold, color = ThermalColor)
        }

        ThermalDivider()

        Text(orgFooter, fontFamily = ff, fontSize = smallSize, color = Color.Gray, textAlign = TextAlign.Center)
        Text(userName,  fontFamily = ff, fontSize = smallSize, color = Color.Gray, textAlign = TextAlign.Center)
    }
}

// ── مساعدات المعاينة ───────────────────────────────────────────────────────────

@Composable
internal fun InvoiceSampleRows(
    ff       : FontFamily,
    textSize : TextUnit,
    color    : Color,
    paddingH : Int = 4
) {
    listOf("صنف أول" to "50.00", "صنف ثانٍ" to "60.00").forEach { (name, price) ->
        Row(Modifier.fillMaxWidth().padding(horizontal = paddingH.dp, vertical = SettingsDimensions.dp3)) {
            Text(name,   Modifier.weight(3f),   fontFamily = ff, fontSize = textSize, color = color)
            Text(androidx.compose.ui.res.stringResource(R.string.ds_356a192b7913),    Modifier.weight(1f),   fontFamily = ff, fontSize = textSize, color = color, textAlign = TextAlign.Center)
            Text(price,  Modifier.weight(1.5f), fontFamily = ff, fontSize = textSize, color = color, textAlign = TextAlign.End)
        }
    }
}

@Composable
internal fun SignatureBox(ff: FontFamily, width: Int = 50) {
    Box(
        Modifier.size(width.dp, SettingsDimensions.dp30).border(SettingsDimensions.dp1, Color.LightGray, RoundedCornerShape(SettingsDimensions.dp4)),
        contentAlignment = Alignment.Center
    ) {
        Text(androidx.compose.ui.res.stringResource(R.string.ds_ab4b3ec7bdd2), fontFamily = ff, fontSize = SettingsTextScale.sp8, color = Color.LightGray)
    }
}

@Composable
internal fun ThermalDivider() {
    Text(
        androidx.compose.ui.res.stringResource(R.string.ds_7325af6f8e49),
        color     = Color.LightGray,
        fontSize  = SettingsTextScale.sp8,
        textAlign = TextAlign.Center,
        modifier  = Modifier.fillMaxWidth()
    )
}
