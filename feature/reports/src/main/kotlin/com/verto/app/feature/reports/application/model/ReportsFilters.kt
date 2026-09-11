/**
 * Sales analytical segment filters.
 *
 * These are intentionally NOT global dashboard filters. The reporting period is the
 * only cross-domain filter; payment/category/cashier are scoped to Sales presentation.
 */
package com.verto.app.feature.reports.application.model

data class ReportsFilters(
    val paymentMethod: String? = null,
    val category: String? = null,
    val cashierName: String? = null
) {
    val isActive: Boolean get() = paymentMethod != null || category != null || cashierName != null
    val activeCount: Int get() = listOfNotNull(paymentMethod, category, cashierName).size
}
