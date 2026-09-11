package com.verto.app.feature.shipment.presentation.logisticsv2

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.verto.app.feature.shipment.domain.model.LogisticsPartnerRole
import com.verto.app.ui.components.VertoPrimaryButton
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.feature.shipment.R

@Composable
fun LogisticsPartnerDirectoryScreen(
    state: LogisticsPartnerDirectoryUi,
    operationState: LogisticsOperationUiState = LogisticsOperationUiState.Idle,
    onBack: () -> Unit,
    onSave: (LogisticsPartnerDraft) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var representativeName by remember { mutableStateOf("") }
    var representativePhone by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(LogisticsPartnerRole.CARRIER) }
    val draft = LogisticsPartnerDraft(
        name = name,
        role = role,
        phone = phone,
        representativeName = representativeName,
        representativePhone = representativePhone,
        notes = notes,
    )

    LogisticsV2Screen(
        title = stringResource(R.string.logistics_contact_title),
        subtitle = stringResource(R.string.logistics_contact_subtitle),
        onBack = onBack,
    ) {
        LogisticsSection(stringResource(R.string.logistics_contact_directory), trailing = state.partners.size.toString()) {
            if (state.partners.isEmpty()) LogisticsEmpty(stringResource(R.string.logistics_contact_empty))
            state.partners.filter { it.role in SHIPPING_CONTACT_ROLES }.forEach { partner ->
                val contact = listOfNotNull(
                    partner.representativeName?.takeIf(String::isNotBlank),
                    partner.representativePhone?.takeIf(String::isNotBlank),
                    partner.phone?.takeIf(String::isNotBlank),
                ).joinToString(" • ")
                LogisticsLabeledValue(
                    partner.name,
                    listOf(partner.role.arabicLabel(), contact).filter(String::isNotBlank).joinToString(" • "),
                )
            }
        }

        if (state.canManage) {
            LogisticsSection(stringResource(R.string.logistics_contact_add)) {
                LogisticsTextField(name, { name = it }, stringResource(R.string.logistics_contact_name))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs),
                ) {
                    SHIPPING_CONTACT_ROLES.forEach { option ->
                        FilterChip(
                            selected = role == option,
                            onClick = { role = option },
                            label = { Text(option.arabicLabel()) },
                        )
                    }
                }
                LogisticsTextField(phone, { phone = it }, stringResource(R.string.logistics_contact_phone))
                LogisticsTextField(representativeName, { representativeName = it }, stringResource(R.string.logistics_contact_representative))
                LogisticsTextField(representativePhone, { representativePhone = it }, stringResource(R.string.logistics_contact_representative_phone))
                LogisticsTextField(notes, { notes = it }, stringResource(R.string.logistics_contact_notes), singleLine = false)
                LogisticsOperationFeedback(operationState)
                VertoPrimaryButton(
                    text = stringResource(R.string.logistics_contact_save),
                    onClick = {
                        onSave(draft)
                        name = ""
                        phone = ""
                        representativeName = ""
                        representativePhone = ""
                        notes = ""
                        role = LogisticsPartnerRole.CARRIER
                    },
                    enabled = draft.isValid && !operationState.isWorking,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private val SHIPPING_CONTACT_ROLES = listOf(
    LogisticsPartnerRole.CARRIER,
    LogisticsPartnerRole.CUSTOMS_BROKER,
)

@Composable
private fun LogisticsPartnerRole.arabicLabel(): String = when (this) {
    LogisticsPartnerRole.CARRIER -> stringResource(R.string.logistics_contact_carrier)
    LogisticsPartnerRole.FREIGHT_FORWARDER -> stringResource(R.string.logistics_contact_forwarder)
    LogisticsPartnerRole.CUSTOMS_BROKER -> stringResource(R.string.logistics_contact_broker)
    LogisticsPartnerRole.INSPECTOR -> stringResource(R.string.logistics_contact_inspector)
    LogisticsPartnerRole.INSURER -> stringResource(R.string.logistics_contact_insurer)
    LogisticsPartnerRole.OTHER -> stringResource(R.string.logistics_contact_other)
}
