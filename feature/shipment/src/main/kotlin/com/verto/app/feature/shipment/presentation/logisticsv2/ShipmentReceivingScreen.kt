package com.verto.app.feature.shipment.presentation.logisticsv2

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import com.verto.app.ui.components.VertoPrimaryButton
import com.verto.app.ui.components.VertoSecondaryButton
import com.verto.app.ui.theme.TextMuted

/** v241 final receiving: one decision, shortage detail only when needed, and durable draft persistence. */
@Composable
internal fun ShipmentReceivingScreen(
    shipmentId: String,
    shipmentNumber: String,
    initialLines: List<LogisticsReceivingLineDraft>,
    savedDraft: LogisticsFinalReceivingDraftSnapshot? = null,
    access: LogisticsV2Access,
    operationState: LogisticsOperationUiState = LogisticsOperationUiState.Idle,
    onBack: () -> Unit,
    onSubmit: (LogisticsReceivingDraft) -> Unit,
    viewModel: LogisticsV2ViewModel = hiltViewModel(),
) {
    val workingLines = remember(shipmentId, initialLines, savedDraft?.updatedAt) {
        restoreFinalReceivingLines(initialLines, savedDraft)
    }
    val eligibleIds = remember(workingLines) { workingLines.mapTo(mutableSetOf()) { it.shipmentLineId } }
    var receivedCompletely by remember(shipmentId, savedDraft?.updatedAt) {
        mutableStateOf(savedDraft?.receivedCompletely)
    }
    var missingInputs by remember(shipmentId, savedDraft?.updatedAt) {
        mutableStateOf(
            savedDraft?.missingQuantities
                ?.filterKeys { it in eligibleIds }
                ?.mapValues { (_, value) -> value.filter(Char::isDigit) }
                .orEmpty(),
        )
    }

    fun persist(decision: Boolean? = receivedCompletely, inputs: Map<String, String> = missingInputs) {
        viewModel.saveFinalReceivingDraft(shipmentId, decision, inputs, workingLines)
    }

    LogisticsV2Screen(title = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_0b6066a53cf1), subtitle = shipmentNumber, onBack = onBack) {
        if (!access.canConfirm) {
            LogisticsPermissionDenied()
        } else {
            FinalReceivingContent(
                lines = workingLines,
                receivedCompletely = receivedCompletely,
                missingInputs = missingInputs,
                operationState = operationState,
                onMissingChange = { lineId, raw ->
                    val clean = raw.filter(Char::isDigit)
                    missingInputs = if (clean.isBlank()) missingInputs - lineId else missingInputs + (lineId to clean)
                    persist(inputs = missingInputs)
                },
                onChangeDecision = {
                    receivedCompletely = null
                    missingInputs = emptyMap()
                    persist(decision = null, inputs = emptyMap())
                },
                onSubmit = {
                    val decision = receivedCompletely ?: return@FinalReceivingContent
                    onSubmit(buildFinalReceivingDraft(workingLines, decision, missingInputs))
                },
            )
        }
    }

    if (receivedCompletely == null && workingLines.isNotEmpty()) {
        FinalReceivingQuestionDialog(
            onDismiss = onBack,
            onYes = {
                receivedCompletely = true
                missingInputs = emptyMap()
                persist(decision = true, inputs = emptyMap())
            },
            onNo = {
                receivedCompletely = false
                missingInputs = emptyMap()
                persist(decision = false, inputs = emptyMap())
            },
        )
    }
}

@Composable
private fun FinalReceivingContent(
    lines: List<LogisticsReceivingLineDraft>,
    receivedCompletely: Boolean?,
    missingInputs: Map<String, String>,
    operationState: LogisticsOperationUiState,
    onMissingChange: (String, String) -> Unit,
    onChangeDecision: () -> Unit,
    onSubmit: () -> Unit,
) {
    if (lines.isEmpty()) {
        LogisticsEmpty("لا توجد كميات متبقية للاستلام")
        return
    }

    when (receivedCompletely) {
        true -> {
            LogisticsSection("استلام كامل") {
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_8275b9e73947),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        false -> ShortageInvoices(lines, missingInputs, onMissingChange)
        null -> Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_ae31208e7d7a), color = TextMuted)
    }

    if (receivedCompletely != null) {
        VertoSecondaryButton(
            text = "تغيير الإجابة",
            onClick = onChangeDecision,
            enabled = !operationState.isWorking,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    LogisticsOperationFeedback(operationState)
    val draft = receivedCompletely?.let { buildFinalReceivingDraft(lines, it, missingInputs) }
    VertoPrimaryButton(
        text = "تأكيد الاستلام وإغلاق الشحنة",
        onClick = onSubmit,
        enabled = draft?.isValid == true && !operationState.isWorking,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ShortageInvoices(
    lines: List<LogisticsReceivingLineDraft>,
    missingInputs: Map<String, String>,
    onMissingChange: (String, String) -> Unit,
) {
    Text(
        androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_64f7b98137d5),
        color = TextMuted,
        style = MaterialTheme.typography.bodySmall,
    )
    lines.groupBy { it.sourceInvoiceId to it.sourceInvoiceNumber }.forEach { (invoice, invoiceLines) ->
        val invoiceLabel = invoice.second.ifBlank { invoice.first }.ifBlank { "غير محددة" }
        LogisticsSection("فاتورة $invoiceLabel") {
            invoiceLines.forEach { line ->
                Text(line.itemName, style = MaterialTheme.typography.bodyMedium)
                LogisticsLabeledValue("الكمية المشحونة", line.expectedQuantity.toString())
                if (line.alreadyReceivedQuantity > 0) {
                    LogisticsLabeledValue("مستلم سابقًا", line.alreadyReceivedQuantity.toString())
                }
                LogisticsTextField(
                    value = missingInputs[line.shipmentLineId].orEmpty(),
                    onValueChange = { onMissingChange(line.shipmentLineId, it) },
                    label = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_876323dc365e),
                    singleLine = true,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.fillMaxWidth(),
                )
                val rawMissing = missingInputs[line.shipmentLineId].orEmpty()
                val missing = rawMissing.toIntOrNull()
                val error = when {
                    rawMissing.isNotBlank() && missing == null -> "أدخل رقمًا ضمن النطاق المسموح"
                    missing != null && missing > line.remainingBeforeBatch -> "النقص لا يمكن أن يتجاوز ${line.remainingBeforeBatch}"
                    else -> null
                }
                error?.let { message ->
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
