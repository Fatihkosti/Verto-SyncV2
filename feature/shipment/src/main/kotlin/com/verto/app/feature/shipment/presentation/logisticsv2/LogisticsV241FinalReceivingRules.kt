package com.verto.app.feature.shipment.presentation.logisticsv2

/** Pure v241 mapping kept out of Compose so final-receiving rules can be unit tested. */

internal fun restoreFinalReceivingLines(
    currentLines: List<LogisticsReceivingLineDraft>,
    savedDraft: LogisticsFinalReceivingDraftSnapshot?,
): List<LogisticsReceivingLineDraft> =
    savedDraft?.lines?.takeIf { it.isNotEmpty() }?.map { line ->
        LogisticsReceivingLineDraft(
            shipmentLineId = line.shipmentLineId,
            sourceInvoiceId = line.sourceInvoiceId,
            sourceInvoiceNumber = line.sourceInvoiceNumber,
            itemName = line.itemName,
            expectedQuantity = line.expectedQuantity,
            alreadyReceivedQuantity = line.alreadyReceivedQuantity,
        )
    } ?: currentLines

internal fun buildFinalReceivingDraft(
    lines: List<LogisticsReceivingLineDraft>,
    receivedCompletely: Boolean,
    missingInputs: Map<String, String>,
): LogisticsReceivingDraft {
    val finalLines = lines.map { line ->
        val rawMissing = missingInputs[line.shipmentLineId].orEmpty()
        val missing = when {
            receivedCompletely || rawMissing.isBlank() -> 0
            else -> rawMissing.toIntOrNull() ?: Int.MAX_VALUE
        }
        val receivedNow = line.remainingBeforeBatch - missing
        line.copy(
            receivedQuantity = receivedNow,
            acceptedQuantity = receivedNow,
            damagedQuantity = 0,
            rejectedQuantity = 0,
            quarantinedQuantity = 0,
        )
    }
    return LogisticsReceivingDraft(receivedCompletely = receivedCompletely, lines = finalLines)
}
