package com.verto.app.feature.party.presentation.shared

import com.verto.app.feature.party.application.PartyCurrencyAmount
import com.verto.app.money.Money
import com.verto.app.utils.WhatsAppUtils
import kotlin.math.abs

/** UI-only formatting; calculation remains fixed-point and currency-separated. */
internal fun formatPartyCurrencyAmounts(
    amounts: List<PartyCurrencyAmount>,
    absolute: Boolean = false,
): String {
    if (amounts.isEmpty()) return "0"
    return amounts.joinToString(" • ") { entry ->
        val major = Money.ofMinor(entry.amountMinor, entry.currencyCode).toLegacyDouble()
        val shown = if (absolute) abs(major) else major
        val code = entry.currencyCode.takeUnless { it == "UNKNOWN" } ?: "?"
        "${WhatsAppUtils.formatAmount(shown)} $code"
    }
}
