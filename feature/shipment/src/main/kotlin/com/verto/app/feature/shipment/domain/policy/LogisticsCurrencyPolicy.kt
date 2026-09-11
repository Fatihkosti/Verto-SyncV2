package com.verto.app.feature.shipment.domain.policy

import java.math.BigDecimal
import java.util.Currency

/** Money contract: ISO 4217 codes and a single exchange direction: 1 foreign unit = X SDG. */
object LogisticsCurrencyPolicy {
    const val BASE_CURRENCY = "SDG"

    fun normalizeIso4217(raw: String): String {
        val code = raw.trim().uppercase()
        require(code.length == 3) { "currency must be a 3-letter ISO 4217 code" }
        runCatching { Currency.getInstance(code) }
            .getOrElse { throw IllegalArgumentException("Unsupported ISO 4217 currency: $code") }
        return code
    }

    fun requireRate(code: String, rate: BigDecimal, exchangeRateDate: Long?) {
        require(rate.signum() > 0) { "exchange rate must be positive" }
        if (code == BASE_CURRENCY) {
            require(rate.compareTo(BigDecimal.ONE) == 0) { "SDG exchange rate must be 1" }
        } else {
            requireNotNull(exchangeRateDate) { "exchangeRateDate is required for foreign currency" }
            require(exchangeRateDate >= 0L) { "exchangeRateDate must be non-negative" }
        }
    }

    fun toBaseCurrencyAmount(
        amount: BigDecimal,
        currency: String,
        exchangeRate: BigDecimal,
        exchangeRateDate: Long?,
    ): BigDecimal {
        val normalized = normalizeIso4217(currency)
        require(amount.signum() >= 0) { "amount must be non-negative" }
        requireRate(normalized, exchangeRate, exchangeRateDate)
        return amount.multiply(exchangeRate)
    }
}
