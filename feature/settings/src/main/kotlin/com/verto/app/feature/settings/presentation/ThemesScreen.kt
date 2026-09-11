package com.verto.app.feature.settings.presentation

import com.verto.feature.settings.R
import com.verto.app.ui.components.SettingsSectionHeader
import com.verto.app.ui.components.SettingsDivider

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.feature.settings.presentation.appearance.AppearanceSettingsEvent
import com.verto.app.feature.settings.presentation.appearance.AppearanceSettingsViewModel
import com.verto.app.ui.components.SettingsCard
import com.verto.app.ui.theme.*
import com.verto.app.utils.AppFontSize
import com.verto.app.utils.ThemeMode
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoTopAppBar

enum class ThemesLanding { ALL, APPEARANCE, STYLE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemesScreen(
    onBack : () -> Unit = {},
    vm     : AppearanceSettingsViewModel,
    landing: ThemesLanding = ThemesLanding.ALL,
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val themeMode = uiState.themeMode
    val appFontSize = uiState.appFontSize

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            VertoTopAppBar(
                title  = { Text(androidx.compose.ui.res.stringResource(R.string.ds_d8dfff3e6ded), color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    VertoIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SettingsDimensions.dp16, vertical = SettingsDimensions.dp8),
            verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp8)
        ) {

            // ── وضع العرض ─────────────────────────────────────────────────────
            if (landing != ThemesLanding.STYLE) {
                SettingsSectionHeader("وضع العرض")
                SettingsCard {
                ThemeModeOption(
                    title    = androidx.compose.ui.res.stringResource(R.string.ds_1dccac8503d2),
                    subtitle = "خلفية بيضاء مريحة للنهار",
                    icon     = Icons.Filled.LightMode,
                    selected = themeMode == ThemeMode.LIGHT,
                    onClick  = { vm.onEvent(AppearanceSettingsEvent.ThemeModeChanged(ThemeMode.LIGHT)) }
                )
                SettingsDivider()
                ThemeModeOption(
                    title    = androidx.compose.ui.res.stringResource(R.string.ds_7766dcbe8f61),
                    subtitle = "خلفية داكنة مريحة للعين",
                    icon     = Icons.Filled.DarkMode,
                    selected = themeMode == ThemeMode.DARK,
                    onClick  = { vm.onEvent(AppearanceSettingsEvent.ThemeModeChanged(ThemeMode.DARK)) }
                )
                SettingsDivider()
                ThemeModeOption(
                    title    = androidx.compose.ui.res.stringResource(R.string.ds_bbf6d1ebddd6),
                    subtitle = "يتبع إعداد الجهاز",
                    icon     = Icons.Filled.Contrast,
                    selected = themeMode == ThemeMode.AUTO,
                    onClick  = { vm.onEvent(AppearanceSettingsEvent.ThemeModeChanged(ThemeMode.AUTO)) }
                )
                }
            }

            // ── حجم خط التطبيق ────────────────────────────────────────────────
            if (landing != ThemesLanding.APPEARANCE) {
                SettingsSectionHeader("حجم الخط")
                SettingsCard {
                Column(Modifier.padding(SettingsDimensions.dp16), verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp12)) {
                    Text(androidx.compose.ui.res.stringResource(R.string.ds_b6ec333ca6e7), color = TextPrimary, fontSize = SettingsTextScale.sp14, fontWeight = FontWeight.Medium)
                    Text(androidx.compose.ui.res.stringResource(R.string.ds_3c173a2971d9),
                        color = TextSecondary, fontSize = SettingsTextScale.sp11)
                    Spacer(Modifier.height(SettingsDimensions.dp4))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.dp8)
                    ) {
                        FontSizeChip(
                            label    = androidx.compose.ui.res.stringResource(R.string.ds_e476741781b2),
                            example  = "أ",
                            fontSize = SettingsTextScale.sp12,
                            selected = appFontSize == AppFontSize.SMALL,
                            modifier = Modifier.weight(1f),
                            onClick  = { vm.onEvent(AppearanceSettingsEvent.FontSizeChanged(AppFontSize.SMALL)) }
                        )
                        FontSizeChip(
                            label    = androidx.compose.ui.res.stringResource(R.string.ds_91fa23bdc1fb),
                            example  = "أ",
                            fontSize = SettingsTextScale.sp16,
                            selected = appFontSize == AppFontSize.MEDIUM,
                            modifier = Modifier.weight(1f),
                            onClick  = { vm.onEvent(AppearanceSettingsEvent.FontSizeChanged(AppFontSize.MEDIUM)) }
                        )
                        FontSizeChip(
                            label    = androidx.compose.ui.res.stringResource(R.string.ds_11da4f18dd02),
                            example  = "أ",
                            fontSize = SettingsTextScale.sp20,
                            selected = appFontSize == AppFontSize.LARGE,
                            modifier = Modifier.weight(1f),
                            onClick  = { vm.onEvent(AppearanceSettingsEvent.FontSizeChanged(AppFontSize.LARGE)) }
                        )
                    }
                }
                }
            }

            // ── ملاحظة ────────────────────────────────────────────────────────
            Spacer(Modifier.height(SettingsDimensions.dp4))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(SettingsDimensions.dp10))
                    .background(AccentDim.copy(alpha = 0.4f))
                    .padding(SettingsDimensions.dp12),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.dp8)
            ) {
                Icon(Icons.Filled.Info, null, tint = AccentPrimary, modifier = Modifier.size(SettingsDimensions.dp16))
                Text(
                    androidx.compose.ui.res.stringResource(R.string.ds_31c6db6de638),
                    color = AccentLight, fontSize = SettingsTextScale.sp11, lineHeight = SettingsTextScale.sp16
                )
            }

            Spacer(Modifier.height(SettingsDimensions.dp24))
        }
    }
}

// ── ThemeModeOption ────────────────────────────────────────────────────────────

@Composable
private fun ThemeModeOption(
    title    : String,
    subtitle : String,
    icon     : androidx.compose.ui.graphics.vector.ImageVector,
    selected : Boolean,
    onClick  : () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = SettingsDimensions.dp16, vertical = SettingsDimensions.dp14),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null,
            tint     = if (selected) AccentPrimary else TextMuted,
            modifier = Modifier.size(SettingsDimensions.dp20))
        Spacer(Modifier.width(SettingsDimensions.dp14))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (selected) TextPrimary else TextSecondary,
                fontSize = SettingsTextScale.sp14, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            Text(subtitle, color = TextMuted, fontSize = SettingsTextScale.sp11)
        }
        if (selected) {
            Icon(Icons.Filled.CheckCircle, null,
                tint = AccentPrimary, modifier = Modifier.size(SettingsDimensions.dp20))
        }
    }
}

// ── FontSizeChip ───────────────────────────────────────────────────────────────

@Composable
private fun FontSizeChip(
    label    : String,
    example  : String,
    fontSize : androidx.compose.ui.unit.TextUnit,
    selected : Boolean,
    modifier : Modifier = Modifier,
    onClick  : () -> Unit
) {
    val borderColor = if (selected) AccentPrimary else BorderColor
    val bgColor     = if (selected) AccentDim.copy(alpha = 0.5f) else BgDeep

    Column(
        modifier
            .clip(RoundedCornerShape(SettingsDimensions.dp10))
            .background(bgColor)
            .border(SettingsDimensions.dp1, borderColor, RoundedCornerShape(SettingsDimensions.dp10))
            .clickable { onClick() }
            .padding(vertical = SettingsDimensions.dp12),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp4)
    ) {
        Text(example, color = if (selected) AccentPrimary else TextSecondary,
            fontSize = fontSize, fontWeight = FontWeight.Bold)
        Text(label, color = if (selected) AccentLight else TextMuted, fontSize = SettingsTextScale.sp11)
    }
}
