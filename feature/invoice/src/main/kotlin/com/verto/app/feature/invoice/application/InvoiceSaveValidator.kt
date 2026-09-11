package com.verto.app.feature.invoice.application

import com.verto.app.feature.invoice.domain.model.InvoiceDraftItem
import com.verto.app.feature.invoice.domain.model.InvoiceStockItem
import com.verto.app.feature.invoice.domain.model.SaveInvoiceCommand
import com.verto.app.money.Money
import com.verto.app.money.Quantity
import javax.inject.Inject

internal data class ValidatedInvoiceItem(
    val draft: InvoiceDraftItem,
    val quantity: Quantity,
    val buyPrice: Money,
    val sellPrice: Money,
    val lineTotal: Money,
)

internal data class ValidatedInvoiceSave(
    val items: List<ValidatedInvoiceItem>,
    val total: Money,
) {
    /** Compatibility projection only. Financial calculation uses [total]. */
    val totalAmount: Double get() = total.toLegacyDouble()
}

internal class InvoiceSaveValidator @Inject constructor() {

    fun validate(command: SaveInvoiceCommand): ValidatedInvoiceSave {
        require(command.items.isNotEmpty()) { "لا يمكن حفظ فاتورة بدون بنود" }
        require(!command.isSale || command.supplierInvoiceReference.isNullOrBlank()) {
            "رقم فاتورة المورد الخارجي متاح لفواتير الشراء فقط"
        }
        require(!command.isSale || command.purchaseOrderId.isNullOrBlank()) {
            "أمر الشراء لا يمكن ربطه بفاتورة بيع"
        }
        require(command.purchaseQuantityToleranceUnits >= 0) { "حد سماح الكمية لا يمكن أن يكون سالباً" }
        require(command.purchasePriceToleranceMinor >= 0L) { "حد سماح السعر لا يمكن أن يكون سالباً" }

        val parsed = command.items.mapIndexed { index, item ->
            parseItem(command, item, index)
        }
        val grossTotal = parsed.fold(Money.zero()) { sum, item -> sum + item.lineTotal }
        require(grossTotal.isPositive()) { "إجمالي الفاتورة يجب أن يكون أكبر من صفر" }
        require(!command.discount.isNegative()) { "خصم الفاتورة لا يمكن أن يكون سالباً" }
        require(command.discount < grossTotal) { "خصم الفاتورة يجب أن يكون أقل من إجمالي البنود" }
        val total = grossTotal - command.discount
        require(total.isPositive()) { "صافي الفاتورة يجب أن يكون أكبر من صفر" }

        val initialPayment = command.initialPayment
        require(!initialPayment.isNegative()) { "الدفعة المقدمة لا يمكن أن تكون سالبة" }
        require(initialPayment <= total) { "الدفعة المقدمة أكبر من صافي الفاتورة" }

        require(!command.commission.isNegative()) { "العمولة لا يمكن أن تكون سالبة" }
        val commissionSource = command.commissionSource.trim().uppercase()
        require(commissionSource in setOf("NONE", "BUYER", "REFERRER")) { "مصدر العمولة غير صالح" }
        val beneficiary = command.commissionBeneficiaryClientId?.trim()?.takeIf { it.isNotEmpty() }
        when (commissionSource) {
            "NONE" -> require(beneficiary == null && !command.commission.isPositive()) {
                "العمولة الموجبة تحتاج مستفيداً ومصدر إسناد"
            }
            "BUYER" -> require(beneficiary != null && beneficiary == command.clientId.trim()) {
                "مستفيد عمولة المشتري يجب أن يكون المشتري نفسه"
            }
            "REFERRER" -> require(beneficiary != null && beneficiary != command.clientId.trim()) {
                "المحيل يجب أن يكون طرفاً مختلفاً عن المشتري"
            }
        }

        return ValidatedInvoiceSave(items = parsed, total = total)
    }

    private fun parseItem(
        command: SaveInvoiceCommand,
        item: InvoiceDraftItem,
        index: Int,
    ): ValidatedInvoiceItem {
        val position = index + 1
        require(item.name.isNotBlank()) { "اسم الصنف مطلوب في البند $position" }

        val quantity = parseQuantity(item.quantity, position)
        val buyPrice = parsePrice(
            raw = item.buyPrice,
            label = "سعر الشراء",
            position = position,
            requiredPositive = !command.isSale,
        )
        val sellPrice = parsePrice(
            raw = item.sellPrice,
            label = "سعر البيع",
            position = position,
            requiredPositive = command.isSale,
        )
        val activePrice = if (command.isSale) sellPrice else buyPrice
        return ValidatedInvoiceItem(
            draft = item,
            quantity = quantity,
            buyPrice = buyPrice,
            sellPrice = sellPrice,
            lineTotal = activePrice * quantity,
        )
    }

    private fun parseQuantity(raw: String, position: Int): Quantity = try {
        Quantity.parse(raw)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("الكمية في البند $position يجب أن تكون عدداً صحيحاً أكبر من صفر")
    }

    private fun parsePrice(
        raw: String,
        label: String,
        position: Int,
        requiredPositive: Boolean,
    ): Money {
        if (raw.isBlank()) {
            require(!requiredPositive) { "$label مطلوب في البند $position" }
            return Money.zero()
        }
        val value = try {
            Money.parse(raw)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("$label غير صالح في البند $position")
        }
        require(!value.isNegative()) { "$label لا يمكن أن يكون سالباً في البند $position" }
        if (requiredPositive) require(value.isPositive()) { "$label يجب أن يكون أكبر من صفر في البند $position" }
        return value
    }

    fun requiresReferencedInventory(command: SaveInvoiceCommand): Boolean =
        command.isSale && command.items.any { it.inventoryItemId.trim().isNotEmpty() }

    fun validateReferencedInventory(
        command: SaveInvoiceCommand,
        inventoryItems: Collection<InvoiceStockItem>,
    ) {
        if (!command.isSale) return
        val referencedIds = command.items
            .mapNotNull { it.inventoryItemId.trim().ifBlank { null } }
            .toSet()
        if (referencedIds.isEmpty()) return

        val existingIds = inventoryItems.mapTo(HashSet()) { it.id }
        val missing = referencedIds - existingIds
        require(missing.isEmpty()) {
            "بند بيع مرتبط بصنف مخزون غير موجود — حدّث الصنف أو أزل الربط قبل الحفظ"
        }
    }
}
