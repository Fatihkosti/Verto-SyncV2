package com.verto.app.feature.shipment.presentation.logisticsv2

import androidx.compose.runtime.Composable
import com.verto.app.feature.shipment.application.model.ShipmentClosedDetailReadModel

@Composable
internal fun LogisticsClosedSummaryContent(
    shipmentNumber: String,
    closed: ShipmentClosedDetailReadModel,
) {
    LogisticsSection("الملخص النهائي") {
        LogisticsLabeledValue("رقم الشحنة", shipmentNumber)
        LogisticsLabeledValue("الموردون", closed.suppliers.joinToString("، ").ifBlank { "—" })
        LogisticsLabeledValue("الفواتير", closed.invoices.joinToString("، ").ifBlank { "—" })
        LogisticsLabeledValue("المسار المخطط", closed.plannedRoute.joinToString(" ← ").ifBlank { "—" })
        LogisticsLabeledValue("المسار الفعلي", closed.actualRoute.joinToString(" ← ").ifBlank { "—" })
        LogisticsLabeledValue("الموظف المسؤول", closed.employee.employeeName ?: "—")
        LogisticsLabeledValue("البدء المخطط", closed.plannedStartAt?.let(::formatLogisticsDate) ?: "—")
        LogisticsLabeledValue("النهاية المخططة", closed.plannedEndAt?.let(::formatLogisticsDate) ?: "—")
        LogisticsLabeledValue("البدء الفعلي", closed.actualStartAt?.let(::formatLogisticsDate) ?: "—")
        LogisticsLabeledValue("النهاية الفعلية", closed.actualEndAt?.let(::formatLogisticsDate) ?: "—")
        if (closed.timing.delayMillis > 0L) {
            LogisticsLabeledValue("موقع التأخير", closed.timing.delayLocation ?: "—")
            LogisticsLabeledValue("سبب التأخير", closed.timing.delayReason ?: "غير مسجل")
            LogisticsLabeledValue("مدة التأخير", delayLabel(closed.timing.delayMillis))
        }
        LogisticsLabeledValue("قيمة المقبول", closed.acceptedValue.toPlainString())
        LogisticsLabeledValue("قيمة الناقص", closed.missingValue.toPlainString())
        LogisticsLabeledValue("قيمة المسترد", closed.recoveredValue.toPlainString())
        LogisticsLabeledValue("تكلفة الشحنة", closed.shipmentCostTotal.toPlainString())
        LogisticsLabeledValue("تكلفة الاسترداد", closed.recoveryCostTotal.toPlainString())
        LogisticsLabeledValue("قيمة المخزون النهائية", closed.finalInventoryValue.toPlainString())
    }
    LogisticsSection("التكاليف والمدفوعات النهائية", trailing = closed.costsAndPayments.size.toString()) {
        if (closed.costsAndPayments.isEmpty()) LogisticsEmpty("لا توجد تكاليف نهائية.")
        closed.costsAndPayments.forEach { row ->
            LogisticsLabeledValue(
                row.description,
                "${row.costAmount.toPlainString()} ${row.costCurrency} • أساس ${row.baseAmount.toPlainString()} • ${row.paymentState} • مدفوع ${row.paidAmount.toPlainString()}" +
                    (row.proofDocumentName?.let { " • إثبات: $it" } ?: ""),
            )
        }
    }
    LogisticsSection("حالة النواقص النهائية", trailing = closed.shortages.size.toString()) {
        if (closed.shortages.isEmpty()) LogisticsEmpty("لا توجد نواقص.")
        closed.shortages.forEach { shortage ->
            LogisticsLabeledValue(
                shortage.itemName,
                "${shortage.status} • الأصل ${shortage.originalMissingQuantity} • المتبقي ${shortage.remainingMissingQuantity}" +
                    (shortage.settlement?.let { " • تسوية $it" } ?: ""),
            )
        }
    }
    LogisticsSection("المرفقات النهائية", trailing = closed.attachments.size.toString()) {
        if (closed.attachments.isEmpty()) LogisticsEmpty("لا توجد مرفقات.")
        closed.attachments.forEach { attachment ->
            LogisticsLabeledValue(
                attachment.displayName,
                "${attachment.type} • ${formatLogisticsDate(attachment.createdAt)}" +
                    (attachment.employeeName?.let { " • $it" } ?: ""),
            )
        }
    }
}
