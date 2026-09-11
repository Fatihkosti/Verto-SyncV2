package com.verto.app.feature.settings.presentation

import androidx.compose.ui.res.stringResource

import com.verto.feature.settings.R

import com.verto.app.ui.components.VertoOutlinedButton

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.feature.settings.domain.model.SettingsBackupTarget
import com.verto.app.feature.settings.presentation.main.SectionState
import com.verto.app.ui.components.SettingsCard
import com.verto.app.ui.theme.*

@Composable
fun BackupSection(
    backupState   : SectionState,
    backupMessage : String?,
    onExport      : () -> Unit,
    onImport      : () -> Unit
) {
    SettingsCard {
        Column(Modifier.padding(SettingsDimensions.dp16)) {
            // حالة التحميل أو الرسالة
            if (backupState is SectionState.Loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(SettingsDimensions.dp16),
                        color       = AccentPrimary,
                        strokeWidth = SettingsDimensions.dp2
                    )
                    Spacer(Modifier.width(SettingsDimensions.dp8))
                    Text(androidx.compose.ui.res.stringResource(R.string.ds_fe80d85fe94a), color = AccentPrimary, fontSize = SettingsTextScale.sp12)
                }
                Spacer(Modifier.height(SettingsDimensions.dp12))
            } else if (backupMessage != null) {
                Text(
                    text      = backupMessage,
                    color     = if (backupState is SectionState.Error) ErrorColor else SuccessColor,
                    fontSize  = SettingsTextScale.sp12
                )
                Spacer(Modifier.height(SettingsDimensions.dp12))
            } else if (backupState is SectionState.Error) {
                Text(
                    text     = androidx.compose.ui.res.stringResource(R.string.ds_b9af71e5dd05, backupState.msg),
                    color    = ErrorColor,
                    fontSize = SettingsTextScale.sp12
                )
                Spacer(Modifier.height(SettingsDimensions.dp12))
            }

            // الأزرار
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.dp10)
            ) {
                // تصدير
                VertoOutlinedButton(
                    onClick  = onExport,
                    enabled  = backupState !is SectionState.Loading,
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(SettingsDimensions.dp10),
                    border   = androidx.compose.foundation.BorderStroke(SettingsDimensions.dp1, AccentPrimary)
                ) {
                    Icon(
                        Icons.Filled.Upload,
                        contentDescription = null,
                        tint     = AccentPrimary,
                        modifier = Modifier.size(SettingsDimensions.dp16)
                    )
                    Spacer(Modifier.width(SettingsDimensions.dp6))
                    Text(androidx.compose.ui.res.stringResource(R.string.ds_fc6ea67b8134), color = AccentPrimary, fontSize = SettingsTextScale.sp13)
                }

                // استيراد
                VertoOutlinedButton(
                    onClick  = onImport,
                    enabled  = backupState !is SectionState.Loading,
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(SettingsDimensions.dp10),
                    border   = androidx.compose.foundation.BorderStroke(SettingsDimensions.dp1, TextSecondary)
                ) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = null,
                        tint     = TextSecondary,
                        modifier = Modifier.size(SettingsDimensions.dp16)
                    )
                    Spacer(Modifier.width(SettingsDimensions.dp6))
                    Text(androidx.compose.ui.res.stringResource(R.string.ds_5e029fef2ea5), color = TextSecondary, fontSize = SettingsTextScale.sp13)
                }
            }

            Spacer(Modifier.height(SettingsDimensions.dp8))
            Text(
                androidx.compose.ui.res.stringResource(R.string.ds_22e22d02c082),
                color    = TextMuted,
                fontSize = SettingsTextScale.sp11
            )
        }
    }
}

@Composable
fun BackupShareDialog(
    onDismiss : () -> Unit,
    onSelect  : (SettingsBackupTarget) -> Unit
) {
    AlertDialog(
        onDismissRequest  = onDismiss,
        containerColor    = BgCard,
        title = {
            Text(androidx.compose.ui.res.stringResource(R.string.ds_c9122a52fd6b), color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp8)) {
                listOf(
                    SettingsBackupTarget.LOCAL    to Pair(Icons.Filled.Folder,   stringResource(R.string.legacy_ui_13bd8b6d3a76)),
                    SettingsBackupTarget.WHATSAPP to Pair(Icons.Filled.Send,     stringResource(R.string.legacy_ui_ad3a174b36ba)),
                    SettingsBackupTarget.TELEGRAM to Pair(Icons.Filled.Send,     stringResource(R.string.legacy_ui_855dd95e9e00)),
                ).forEach { (target, iconLabel) ->
                    val (icon, label) = iconLabel
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(SettingsDimensions.dp10))
                            .clickable { onSelect(target) }
                            .padding(SettingsDimensions.dp12),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, null, tint = AccentPrimary, modifier = Modifier.size(SettingsDimensions.dp20))
                        Spacer(Modifier.width(SettingsDimensions.dp12))
                        Text(label, color = TextPrimary, fontSize = SettingsTextScale.sp14)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextSecondary)
            }
        }
    )
}
