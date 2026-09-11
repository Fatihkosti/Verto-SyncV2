package com.verto.app.feature.settings.presentation

import com.verto.feature.settings.R

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.ui.theme.*
import com.verto.app.ui.components.SettingsCard
import com.verto.app.utils.InvoiceFont
import com.verto.app.utils.InvoiceTemplate

// ── SectionInfoCard ────────────────────────────────────────────────────────────

@Composable
fun SectionInfoCard(
    icon        : androidx.compose.ui.graphics.vector.ImageVector,
    title       : String,
    description : String
) {
    SettingsCard {
        Row(
            Modifier.padding(SettingsDimensions.dp16),
            horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.dp16),
            verticalAlignment     = Alignment.Top
        ) {
            Box(
                Modifier.size(SettingsDimensions.dp44).clip(RoundedCornerShape(SettingsDimensions.dp12)).background(AccentDim.copy(0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = AccentPrimary, modifier = Modifier.size(SettingsDimensions.dp24))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp6)) {
                Text(title, color = TextPrimary, fontSize = SettingsTextScale.sp14, fontWeight = FontWeight.Bold)
                Text(description, color = TextMuted, fontSize = SettingsTextScale.sp12)
            }
        }
    }
}

// ── TemplateSelector ────────────────────────────────────────────────────────────

@Composable
fun TemplateSelector(
    selected : InvoiceTemplate,
    onSelect : (InvoiceTemplate) -> Unit
) {
    val templates = InvoiceTemplate.values()
    Column(verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp8)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.dp8)) {
            templates.take(2).forEach { t ->
                TemplateCard(t, selected == t, Modifier.weight(1f)) { onSelect(t) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.dp8)) {
            templates.drop(2).forEach { t ->
                TemplateCard(t, selected == t, Modifier.weight(1f)) { onSelect(t) }
            }
        }
    }
}

@Composable
private fun TemplateCard(
    template : InvoiceTemplate,
    selected : Boolean,
    modifier : Modifier = Modifier,
    onClick  : () -> Unit
) {
    val borderColor = if (selected) AccentPrimary else BorderColor
    val bgColor     = if (selected) AccentDim.copy(alpha = 0.25f) else BgCard

    Column(
        modifier
            .clip(RoundedCornerShape(SettingsDimensions.dp12))
            .background(bgColor)
            .border(if (selected) SettingsDimensions.dp2 else SettingsDimensions.dp1, borderColor, RoundedCornerShape(SettingsDimensions.dp12))
            .clickable { onClick() }
            .padding(SettingsDimensions.dp10),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp6)
    ) {
        TemplateMiniIcon(template, selected)

        Text(
            template.displayName,
            color      = if (selected) AccentPrimary else TextPrimary,
            fontSize   = SettingsTextScale.sp12,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
        Text(
            template.description,
            color     = TextMuted,
            fontSize  = SettingsTextScale.sp9,
            textAlign = TextAlign.Center,
            maxLines  = 2,
            overflow  = TextOverflow.Ellipsis
        )
        if (selected) {
            Icon(
                Icons.Filled.CheckCircle, null,
                tint     = AccentPrimary,
                modifier = Modifier.size(SettingsDimensions.dp14)
            )
        }
    }
}

/** أيقونة مصغرة تمثل هيكل كل قالب */
@Composable
private fun TemplateMiniIcon(template: InvoiceTemplate, selected: Boolean) {
    val accent = if (selected) AccentPrimary else TextMuted.copy(alpha = 0.45f)
    val line   = if (selected) BorderColor else BorderColor.copy(0.6f)

    Box(
        Modifier
            .fillMaxWidth()
            .height(SettingsDimensions.dp64)
            .clip(RoundedCornerShape(SettingsDimensions.dp6))
            .background(Color.White.copy(alpha = 0.95f))
    ) {
        when (template) {

            InvoiceTemplate.CLASSIC -> Column(
                Modifier.fillMaxSize().padding(SettingsDimensions.dp5),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp3)
            ) {
                Box(Modifier.size(SettingsDimensions.dp12).clip(RoundedCornerShape(SettingsDimensions.dp3)).background(accent))
                Box(Modifier.fillMaxWidth(0.65f).height(SettingsDimensions.dp3).background(accent))
                Box(Modifier.fillMaxWidth(0.45f).height(1.5f.dp).background(line))
                Box(Modifier.fillMaxWidth().height(SettingsDimensions.dp0_5).background(line))
                repeat(2) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Box(Modifier.fillMaxWidth(0.55f).height(SettingsDimensions.dp2).background(line))
                        Box(Modifier.fillMaxWidth(0.3f).height(SettingsDimensions.dp2).background(line))
                    }
                }
                Box(Modifier.fillMaxWidth().height(SettingsDimensions.dp0_5).background(accent.copy(0.35f)))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Box(Modifier.fillMaxWidth(0.38f).height(SettingsDimensions.dp2).background(accent))
                }
            }

            InvoiceTemplate.MODERN -> Column(Modifier.fillMaxSize()) {
                // شريط علوي ملون
                Box(
                    Modifier.fillMaxWidth().height(SettingsDimensions.dp16)
                        .background(ModernAccent.copy(if (selected) 1f else 0.5f))
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = SettingsDimensions.dp4),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(SettingsDimensions.dp6).clip(RoundedCornerShape(SettingsDimensions.dp1)).background(Color.White.copy(0.9f)))
                        Box(Modifier.fillMaxWidth(0.45f).height(SettingsDimensions.dp2).background(Color.White.copy(0.7f)))
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(SettingsDimensions.dp4),
                    horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.dp4)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp2)) {
                        repeat(2) { Box(Modifier.fillMaxWidth().height(SettingsDimensions.dp2).background(line)) }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp2)) {
                        repeat(2) { Box(Modifier.fillMaxWidth().height(SettingsDimensions.dp2).background(line)) }
                    }
                }
                Box(Modifier.fillMaxWidth().height(SettingsDimensions.dp0_5).background(ModernAccent.copy(0.25f)))
                Column(Modifier.padding(SettingsDimensions.dp4), verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp2)) {
                    repeat(2) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Box(Modifier.fillMaxWidth(0.55f).height(SettingsDimensions.dp2).background(line))
                            Box(Modifier.fillMaxWidth(0.3f).height(SettingsDimensions.dp2).background(line))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = SettingsDimensions.dp4), horizontalArrangement = Arrangement.End) {
                    Box(Modifier.fillMaxWidth(0.35f).height(SettingsDimensions.dp2).background(ModernAccent.copy(if (selected) 0.8f else 0.4f)))
                }
            }

            InvoiceTemplate.PROFESSIONAL -> Column(
                Modifier.fillMaxSize().padding(SettingsDimensions.dp5),
                verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp3)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(SettingsDimensions.dp10).clip(RoundedCornerShape(SettingsDimensions.dp2)).background(accent))
                    Box(Modifier.fillMaxWidth(0.5f).height(SettingsDimensions.dp3).background(accent.copy(0.8f)))
                }
                Box(Modifier.fillMaxWidth().height(SettingsDimensions.dp2).background(accent))
                Box(
                    Modifier.fillMaxWidth().height(SettingsDimensions.dp11)
                        .border(SettingsDimensions.dp1, line, RoundedCornerShape(SettingsDimensions.dp2))
                        .padding(SettingsDimensions.dp2)
                ) {
                    Box(Modifier.fillMaxWidth(0.65f).height(SettingsDimensions.dp2).background(line).align(Alignment.Center))
                }
                repeat(2) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Box(Modifier.fillMaxWidth(0.55f).height(SettingsDimensions.dp2).background(line))
                        Box(Modifier.fillMaxWidth(0.3f).height(SettingsDimensions.dp2).background(line))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Box(
                        Modifier.size(SettingsDimensions.dp28, SettingsDimensions.dp9)
                            .border(SettingsDimensions.dp1, accent.copy(0.4f), RoundedCornerShape(SettingsDimensions.dp2))
                    )
                }
            }

            InvoiceTemplate.THERMAL -> Column(
                Modifier.fillMaxSize().padding(horizontal = SettingsDimensions.dp10, vertical = SettingsDimensions.dp5),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp3)
            ) {
                Box(Modifier.fillMaxWidth(0.6f).height(SettingsDimensions.dp2).background(accent))
                Box(Modifier.fillMaxWidth(0.4f).height(SettingsDimensions.dp1).background(line))
                Box(Modifier.fillMaxWidth().height(SettingsDimensions.dp0_5).background(line))
                repeat(3) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Box(Modifier.fillMaxWidth(0.5f).height(SettingsDimensions.dp2).background(line))
                        Box(Modifier.fillMaxWidth(0.35f).height(SettingsDimensions.dp2).background(line))
                    }
                }
                Box(Modifier.fillMaxWidth().height(SettingsDimensions.dp0_5).background(accent.copy(0.3f)))
                Box(Modifier.fillMaxWidth(0.45f).height(SettingsDimensions.dp2).background(accent.copy(0.6f)))
            }
        }
    }
}

// ── FontSelector ────────────────────────────────────────────────────────────────

@Composable
fun FontSelector(
    selected : InvoiceFont,
    onSelect : (InvoiceFont) -> Unit
) {
    val fonts = InvoiceFont.values()
    SettingsCard {
        Column(
            Modifier.padding(SettingsDimensions.dp16),
            verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp10)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.dp8)) {
                fonts.take(2).forEach { f ->
                    FontChip(f, selected == f, Modifier.weight(1f)) { onSelect(f) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.dp8)) {
                fonts.drop(2).forEach { f ->
                    FontChip(f, selected == f, Modifier.weight(1f)) { onSelect(f) }
                }
            }
            Text(
                androidx.compose.ui.res.stringResource(R.string.ds_9f3fcc0a5cd5),
                color    = TextMuted,
                fontSize = SettingsTextScale.sp9
            )
        }
    }
}

@Composable
private fun FontChip(
    font     : InvoiceFont,
    selected : Boolean,
    modifier : Modifier = Modifier,
    onClick  : () -> Unit
) {
    val borderColor = if (selected) AccentPrimary else BorderColor
    val bgColor     = if (selected) AccentDim.copy(alpha = 0.25f) else BgDeep

    Column(
        modifier
            .clip(RoundedCornerShape(SettingsDimensions.dp10))
            .background(bgColor)
            .border(if (selected) SettingsDimensions.dp2 else SettingsDimensions.dp1, borderColor, RoundedCornerShape(SettingsDimensions.dp10))
            .clickable { onClick() }
            .padding(vertical = SettingsDimensions.dp10, horizontal = SettingsDimensions.dp8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp2)
    ) {
        Text(
            font.arabicName,
            color      = if (selected) AccentPrimary else TextPrimary,
            fontSize   = SettingsTextScale.sp15,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontFamily = font.toFontFamily()
        )
        Text(font.displayName, color = TextMuted, fontSize = SettingsTextScale.sp10)
        if (selected) {
            Icon(Icons.Filled.CheckCircle, null, tint = AccentPrimary, modifier = Modifier.size(SettingsDimensions.dp12))
        }
    }
}

// ── UI Helpers ─────────────────────────────────────────────────────────────────

@Composable
fun OrgDataRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = SettingsDimensions.dp16, vertical = SettingsDimensions.dp12),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(label, color = TextSecondary, fontSize = SettingsTextScale.sp13)
        Text(
            value,
            color      = TextPrimary,
            fontSize   = SettingsTextScale.sp13,
            fontWeight = FontWeight.Medium,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            modifier   = Modifier.weight(1f, fill = false).padding(start = SettingsDimensions.dp8),
            textAlign  = TextAlign.End
        )
    }
}
