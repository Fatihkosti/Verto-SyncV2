package com.verto.app.feature.invoice.application

import com.verto.app.data.model.EmployeePermissions

fun EmployeePermissions.canViewInvoiceCategory(category: InvoiceCategory): Boolean = when (category) {
    InvoiceCategory.SALE -> salesView
    InvoiceCategory.PURCHASE -> purchasesView
}

fun EmployeePermissions.canEditInvoiceCategory(category: InvoiceCategory): Boolean = when (category) {
    InvoiceCategory.SALE -> salesEdit
    InvoiceCategory.PURCHASE -> purchasesEdit
}

fun EmployeePermissions.canExportInvoiceCategory(category: InvoiceCategory): Boolean = when (category) {
    InvoiceCategory.SALE -> salesExport
    InvoiceCategory.PURCHASE -> purchasesExport
}

fun viewInvoiceDeniedMessage(category: InvoiceCategory): String = when (category) {
    InvoiceCategory.SALE -> "لا تملك صلاحية عرض فواتير البيع"
    InvoiceCategory.PURCHASE -> "لا تملك صلاحية عرض فواتير الشراء"
}
