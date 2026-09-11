package com.verto.app.feature.payment.bridge

import com.verto.app.data.local.dao.PurchaseCycleDao
import com.verto.app.data.local.entity.PurchasePaymentOverrideEntity
import com.verto.app.feature.payment.domain.port.PurchasePaymentGuardRequest
import com.verto.app.feature.payment.domain.port.PurchaseReceiptPaymentGuardPort
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject

/** F253: blocks supplier payment above the currently GRN-backed payable amount. */
class RoomPurchaseReceiptPaymentGuardAdapter @Inject constructor(
    private val dao: PurchaseCycleDao,
) : PurchaseReceiptPaymentGuardPort {
    override suspend fun enforce(request: PurchasePaymentGuardRequest) {
        val purchaseOrderId = dao.getInvoicePurchaseOrderId(request.invoiceId) ?: return
        require(purchaseOrderId.isNotBlank()) { "رابط أمر الشراء غير صالح" }
        val match = requireNotNull(dao.getInvoiceMatch(request.invoiceId)) {
            "لا يمكن دفع فاتورة مرتبطة بأمر شراء قبل المطابقة الثلاثية"
        }
        val currentPayable = dao.getCurrentReceivedPayableMinor(request.invoiceId)
            .coerceAtMost(match.invoiceAmountMinor)
        val paid = dao.getPaidMinor(request.invoiceId)
        val projected = Math.addExact(paid, request.requestedAmountMinor)
        if (projected <= currentPayable) return

        val reason = request.overrideReason?.trim().orEmpty()
        require(reason.isNotEmpty()) { "لا يمكن دفع قيمة كمية غير مستلمة دون Override موثق" }
        require(request.overrideAuthorized) { "لا تملك صلاحية دفع كمية غير مستلمة" }
        val id = UUID.nameUUIDFromBytes(
            "purchase-payment-override|${request.organizationId}|${request.paymentRequestId}".toByteArray(StandardCharsets.UTF_8)
        ).toString()
        val row = PurchasePaymentOverrideEntity(
            id = id,
            organizationId = request.organizationId,
            invoiceId = request.invoiceId,
            paymentRequestId = request.paymentRequestId,
            requestedAmountMinor = projected,
            payableBeforeOverrideMinor = currentPayable,
            reason = reason,
            approvedBy = request.approvedBy,
            approvedByName = request.approvedByName,
            createdAt = request.createdAt,
        )
        val inserted = dao.insertPaymentOverride(row)
        if (inserted == -1L) {
            require(dao.getPaymentOverride(request.organizationId, request.paymentRequestId) == row) {
                "تعارض هوية استثناء دفع المشتريات"
            }
        }
    }
}
