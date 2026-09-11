package com.verto.app.feature.payment.presentation.invoiceeditor

import com.verto.feature.payment.R

import androidx.annotation.StringRes

internal enum class InvoiceTypeOption(val isSale: Boolean, @StringRes val labelResId: Int) {
    SALE(isSale = true, labelResId = R.string.payment_invoice_type_sale),
    PURCHASE(isSale = false, labelResId = R.string.payment_invoice_type_purchase),
}

internal fun visibleInvoiceTypeOptions(
    canCreateSales: Boolean,
    canCreatePurchases: Boolean,
): List<InvoiceTypeOption> = buildList {
    if (canCreateSales) add(InvoiceTypeOption.SALE)
    if (canCreatePurchases) add(InvoiceTypeOption.PURCHASE)
}

internal fun resolveAllowedInvoiceType(
    currentIsSale: Boolean,
    canCreateSales: Boolean,
    canCreatePurchases: Boolean,
): Boolean? {
    val options = visibleInvoiceTypeOptions(canCreateSales, canCreatePurchases)
    return options.firstOrNull { option -> option.isSale == currentIsSale }?.isSale
        ?: options.firstOrNull()?.isSale
}
