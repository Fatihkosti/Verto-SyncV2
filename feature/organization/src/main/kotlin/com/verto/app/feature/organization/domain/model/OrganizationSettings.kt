package com.verto.app.feature.organization.domain.model

/** بيانات المؤسسة المشتركة بين الإعدادات والطباعة والتقارير. */
data class OrganizationSettings(
    val shopName: String = "",
    val shopPhone: String = "",
    val address: String = "",
    val city: String = "",
    val currency: String = "",
    val invoiceFooter: String = "",
    val taxNumber: String = "",
    val logoUrl: String = "",
    val signatureUrl: String = ""
) {
    val isConfigured: Boolean
        get() = shopName.isNotBlank() || currency.isNotBlank() || invoiceFooter.isNotBlank()
}
