package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.ui.components.VertoOutlinedTextField

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.verto.app.feature.shipment.domain.model.LogisticsPartner
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import com.verto.app.feature.shipment.domain.policy.LogisticsCurrencyPolicy
import com.verto.app.ui.theme.VertoSpacing
import java.math.BigDecimal


private const val IMAGE_MIME_TYPE = "image/*"
private const val PDF_MIME_TYPE = "application/pdf"
internal data class LogisticsMovementTransportDraft(
    val carrierPartnerId: String = "",
    val carrierName: String = "",
    val representativeName: String = "",
    val representativePhone: String = "",
    val packageCount: String = "",
    val weightKg: String = "",
)

internal data class LogisticsMovementFinancialDraft(
    val amount: String = "",
    val currency: String = LogisticsCurrencyPolicy.BASE_CURRENCY,
    val exchangeRate: String = "1",
    val confirmPaid: Boolean = false,
    val proof: LogisticsCostProofDraft? = null,
)

internal data class LogisticsMovementPreparationDraft(
    val legId: String,
    val transport: LogisticsMovementTransportDraft = LogisticsMovementTransportDraft(),
    val financial: LogisticsMovementFinancialDraft = LogisticsMovementFinancialDraft(),
) {
    val carrierPartnerId: String get() = transport.carrierPartnerId
    val carrierName: String get() = transport.carrierName
    val representativeName: String get() = transport.representativeName
    val representativePhone: String get() = transport.representativePhone
    val packageCount: String get() = transport.packageCount
    val weightKg: String get() = transport.weightKg
    val amount: String get() = financial.amount
    val currency: String get() = financial.currency
    val exchangeRate: String get() = financial.exchangeRate
    val confirmPaid: Boolean get() = financial.confirmPaid
    val proof: LogisticsCostProofDraft? get() = financial.proof
    val packageCountValue: Int? get() = packageCount.toIntOrNull()
    val weightKgValue: BigDecimal? get() = weightKg.toBigDecimalOrNull()
    val amountValue: BigDecimal? get() = amount.trim().takeIf(String::isNotBlank)?.toBigDecimalOrNull()
    val exchangeRateValue: BigDecimal? get() = exchangeRate.toBigDecimalOrNull()
    val hasCost: Boolean get() = amount.isNotBlank()
    val normalizedPhone: String get() = v237NormalizePhone(representativePhone)
    val phoneValid: Boolean get() = v237PhoneIsValid(representativePhone)
    val isValid: Boolean
        get() = legId.isNotBlank() && carrierName.isNotBlank() &&
            (packageCountValue ?: 0) > 0 && (weightKgValue?.signum() ?: 0) > 0 && phoneValid &&
            (!hasCost || ((amountValue?.signum() ?: 0) > 0 && currency.isNotBlank() &&
                (currency == LogisticsCurrencyPolicy.BASE_CURRENCY || (exchangeRateValue?.signum() ?: 0) > 0))) &&
            (!confirmPaid || hasCost) && (proof == null || confirmPaid)

    fun updateTransport(block: (LogisticsMovementTransportDraft) -> LogisticsMovementTransportDraft) =
        copy(transport = block(transport))

    fun updateFinancial(block: (LogisticsMovementFinancialDraft) -> LogisticsMovementFinancialDraft) =
        copy(financial = block(financial))
}

internal fun LogisticsShipmentLeg.v239IsPrepared(): Boolean =
    status.name == "PLANNED" && carrierPartnerId.isNotBlank() && packageCount?.let { it > 0 } == true && weightKg?.signum() == 1

internal fun LogisticsShipmentLeg.v239PreparationDefaults(
    carriers: List<LogisticsPartner>,
): LogisticsMovementPreparationDraft {
    val selectedCarrierId = carrierPartnerId.ifBlank { plannedCarrierPartnerId.orEmpty() }
    val selectedCarrier = carriers.firstOrNull { it.id == selectedCarrierId }
    val plannedCost = plannedCost
    return LogisticsMovementPreparationDraft(
        legId = id,
        transport = LogisticsMovementTransportDraft(
            carrierPartnerId = selectedCarrierId,
            carrierName = selectedCarrier?.name ?: plannedCarrierNameSnapshot.orEmpty(),
            representativeName = representativeNameSnapshot ?: plannedRepresentativeNameSnapshot ?: selectedCarrier?.representativeName.orEmpty(),
            representativePhone = representativePhoneSnapshot ?: plannedRepresentativePhoneSnapshot ?: selectedCarrier?.representativePhone ?: selectedCarrier?.phone.orEmpty(),
            packageCount = (packageCount ?: plannedPackageCount)?.toString().orEmpty(),
            weightKg = (weightKg ?: plannedWeightKg)?.toPlainString().orEmpty(),
        ),
        financial = LogisticsMovementFinancialDraft(
            amount = plannedCost?.amount?.toPlainString().orEmpty(),
            currency = plannedCost?.currency ?: LogisticsCurrencyPolicy.BASE_CURRENCY,
            exchangeRate = plannedCost?.exchangeRate?.toPlainString() ?: "1",
        ),
    )
}

@Composable
internal fun LogisticsMovementPreparationDialog(
    leg: LogisticsShipmentLeg,
    carriers: List<LogisticsPartner>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LogisticsMovementPreparationDraft) -> Unit,
) {
    val context = LocalContext.current
    var draft by remember(leg.id) { mutableStateOf(leg.v239PreparationDefaults(carriers)) }
    val proofPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            draft = draft.updateFinancial {
                it.copy(
                    confirmPaid = true,
                    proof = LogisticsCostProofDraft(uri.toString(), context.displayName(uri), mime),
                )
            }
        }
    }
    val isForeign = draft.currency.trim().uppercase() != LogisticsCurrencyPolicy.BASE_CURRENCY
    val baseValue = draft.amountValue?.let { amount ->
        val rate = if (isForeign) draft.exchangeRateValue else BigDecimal.ONE
        rate?.takeIf { it.signum() > 0 }?.let(amount::multiply)
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_62e4ae2a295b)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = LogisticsV2Tokens.movementDialogMaxHeight).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(VertoSpacing.sm),
            ) {
                Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_ea6d5ad33a0f), style = MaterialTheme.typography.bodySmall)
                Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_0880750b7114, leg.mode.name), style = MaterialTheme.typography.labelMedium)

                MovementCarrierFields(draft, carriers) { draft = it }
                MovementCargoFields(draft) { draft = it }
                MovementFinancialFields(
                    draft = draft,
                    busy = busy,
                    baseValue = baseValue,
                    onChange = { draft = it },
                    onPickProof = { proofPicker.launch(arrayOf(IMAGE_MIME_TYPE, PDF_MIME_TYPE)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(draft) }, enabled = draft.isValid && !busy) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_a8476b3530ba)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel)) } },
    )
}

@Composable
private fun MovementCarrierFields(
    draft: LogisticsMovementPreparationDraft,
    carriers: List<LogisticsPartner>,
    onChange: (LogisticsMovementPreparationDraft) -> Unit,
) {
    VertoOutlinedTextField(
        value = draft.carrierName,
        onValueChange = { value -> onChange(draft.updateTransport { it.copy(carrierName = value, carrierPartnerId = "") }) },
        label = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_4b67ad725b05)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    if (carriers.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
            carriers.forEach { partner ->
                FilterChip(
                    selected = draft.carrierPartnerId == partner.id,
                    onClick = {
                        onChange(draft.updateTransport {
                            it.copy(
                                carrierPartnerId = partner.id,
                                carrierName = partner.name,
                                representativeName = partner.representativeName.orEmpty(),
                                representativePhone = partner.representativePhone ?: partner.phone.orEmpty(),
                            )
                        })
                    },
                    label = { Text(partner.name) },
                )
            }
        }
    }
    LogisticsTextField(
        value = draft.representativeName,
        onValueChange = { value -> onChange(draft.updateTransport { it.copy(representativeName = value) }) },
        label = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_205ed3834fae),
    )
    LogisticsTextField(
        value = draft.representativePhone,
        onValueChange = { value ->
            onChange(draft.updateTransport { it.copy(representativePhone = value.filter { c -> c.isDigit() || c == '+' || c.isWhitespace() }) })
        },
        label = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_phone_number),
        keyboardType = KeyboardType.Phone,
        isError = !draft.phoneValid,
        errorText = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_0ac01876821e),
    )
}

@Composable
private fun MovementCargoFields(
    draft: LogisticsMovementPreparationDraft,
    onChange: (LogisticsMovementPreparationDraft) -> Unit,
) {
    LogisticsTextField(
        value = draft.packageCount,
        onValueChange = { value -> onChange(draft.updateTransport { it.copy(packageCount = value.filter(Char::isDigit)) }) },
        label = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_6737a33bcd5f),
        keyboardType = KeyboardType.Number,
    )
    LogisticsTextField(
        value = draft.weightKg,
        onValueChange = { value -> onChange(draft.updateTransport { it.copy(weightKg = v237DecimalInput(value)) }) },
        label = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_0fd0048ad712),
        keyboardType = KeyboardType.Decimal,
    )
}

@Composable
private fun MovementFinancialFields(
    draft: LogisticsMovementPreparationDraft,
    busy: Boolean,
    baseValue: BigDecimal?,
    onChange: (LogisticsMovementPreparationDraft) -> Unit,
    onPickProof: () -> Unit,
) {
    val isForeign = draft.currency.trim().uppercase() != LogisticsCurrencyPolicy.BASE_CURRENCY
    Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_169fef970f7c), style = MaterialTheme.typography.titleSmall)
    LogisticsTextField(
        value = draft.amount,
        onValueChange = { value -> onChange(draft.updateFinancial { it.copy(amount = v237DecimalInput(value)) }) },
        label = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_9e822fac2834),
        keyboardType = KeyboardType.Decimal,
    )
    LogisticsTextField(
        value = draft.currency,
        onValueChange = { value ->
            val currency = value.uppercase().filter(Char::isLetter).take(3)
            onChange(draft.updateFinancial { it.copy(currency = currency, exchangeRate = if (currency == LogisticsCurrencyPolicy.BASE_CURRENCY) "1" else it.exchangeRate) })
        },
        label = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_currency),
    )
    if (isForeign) {
        LogisticsTextField(
            value = draft.exchangeRate,
            onValueChange = { value -> onChange(draft.updateFinancial { it.copy(exchangeRate = v237DecimalInput(value)) }) },
            label = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_fd829449a723),
            keyboardType = KeyboardType.Decimal,
        )
    }
    baseValue?.let { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_eea522667a22, it.toPlainString(), LogisticsCurrencyPolicy.BASE_CURRENCY), style = MaterialTheme.typography.bodySmall) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
        Checkbox(
            checked = draft.confirmPaid,
            onCheckedChange = { checked -> onChange(draft.updateFinancial { it.copy(confirmPaid = checked, proof = if (checked) it.proof else null) }) },
            enabled = draft.hasCost,
        )
        Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_27c41cd45093), modifier = Modifier.weight(1f))
    }
    TextButton(onClick = onPickProof, enabled = draft.hasCost && !busy) {
        Text(draft.proof?.displayName?.let { androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_973761a6404f, it) } ?: androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_ab82ad3d39a2))
    }
}
