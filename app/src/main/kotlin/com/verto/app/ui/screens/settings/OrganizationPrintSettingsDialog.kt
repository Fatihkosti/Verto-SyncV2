package com.verto.app.ui.screens.settings

import com.verto.app.R

import com.verto.app.ui.components.VertoButton

import com.verto.app.ui.components.DialogTextField
import com.verto.app.feature.settings.presentation.SettingsDimensions
import com.verto.app.feature.settings.presentation.SettingsTextScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.feature.organization.domain.model.OrganizationSettings
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BgDeep
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary

/** App-shell dialog because it coordinates Organization state inside the Settings route. */
@Composable
fun OrgSettingsDialog(
    current: OrganizationSettings,
    isSaving: Boolean,
    onSave: (OrganizationSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var currency by remember { mutableStateOf(current.currency) }
    var taxNumber by remember { mutableStateOf(current.taxNumber) }
    var invoiceFooter by remember { mutableStateOf(current.invoiceFooter) }
    var logoUrl by remember { mutableStateOf(current.logoUrl) }
    var signatureUrl by remember { mutableStateOf(current.signatureUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = { Text(androidx.compose.ui.res.stringResource(R.string.ds_4b56bc3ee3fb), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = SettingsTextScale.sp15) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp10)
            ) {
                DialogTextField(label = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_currency), value = currency, onValueChange = { currency = it })
                DialogTextField(label = androidx.compose.ui.res.stringResource(R.string.ds_472787ffb3d8), value = taxNumber, onValueChange = { taxNumber = it })
                DialogTextField(label = androidx.compose.ui.res.stringResource(R.string.ds_85cbe220fab7), value = invoiceFooter, onValueChange = { invoiceFooter = it })
                DialogTextField(label = androidx.compose.ui.res.stringResource(R.string.ds_80009e4f9a61), value = logoUrl, onValueChange = { logoUrl = it })
                DialogTextField(label = androidx.compose.ui.res.stringResource(R.string.ds_a17018a47293), value = signatureUrl, onValueChange = { signatureUrl = it })
                Text(
                    androidx.compose.ui.res.stringResource(R.string.ds_fa88c30b75df),
                    color = TextMuted,
                    fontSize = SettingsTextScale.sp10
                )
            }
        },
        confirmButton = {
            VertoButton(
                onClick = {
                    onSave(
                        OrganizationSettings(
                            currency = currency.trim(),
                            taxNumber = taxNumber.trim(),
                            invoiceFooter = invoiceFooter.trim(),
                            logoUrl = logoUrl.trim(),
                            signatureUrl = signatureUrl.trim()
                        )
                    )
                },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                shape = RoundedCornerShape(SettingsDimensions.dp10)
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(SettingsDimensions.dp16), color = TextPrimary, strokeWidth = SettingsDimensions.dp2)
                else Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextMuted) } }
    )
}
