package com.verto.app.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Fixed-point money used by financial domain rules.
 *
 * The source of truth is [amountMinor]. Major-unit Double values are allowed only at
 * legacy/UI/network compatibility boundaries and must never be used for calculation.
 */
data class Money private constructor(
    val amountMinor: Long,
    val currencyCode: String,
) : Comparable<Money> {

    init {
        require(currencyCode.isNotBlank()) { "Currency code is required" }
    }

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(Math.addExact(amountMinor, other.amountMinor), currencyCode)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(Math.subtractExact(amountMinor, other.amountMinor), currencyCode)
    }

    operator fun times(quantity: Quantity): Money =
        Money(Math.multiplyExact(amountMinor, quantity.units.toLong()), currencyCode)

    fun negate(): Money = Money(Math.negateExact(amountMinor), currencyCode)

    fun isPositive(): Boolean = amountMinor > 0L
    fun isNegative(): Boolean = amountMinor < 0L
    fun isZero(): Boolean = amountMinor == 0L

    fun toMajorDecimal(): BigDecimal = BigDecimal.valueOf(amountMinor, MINOR_SCALE)

    fun toPlainString(): String = toMajorDecimal().setScale(MINOR_SCALE).toPlainString()

    /** Compatibility only. Do not calculate with the returned value. */
    fun toLegacyDouble(): Double = toMajorDecimal().toDouble()

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return amountMinor.compareTo(other.amountMinor)
    }

    private fun requireSameCurrency(other: Money) {
        require(currencyCode == other.currencyCode) {
            "Currency mismatch: $currencyCode vs ${other.currencyCode}"
        }
    }

    companion object {
        const val MINOR_SCALE: Int = 2
        val ROUNDING: RoundingMode = RoundingMode.HALF_UP

        /** Temporary transaction-currency marker until F246 introduces persisted currency snapshots. */
        const val TRANSACTION_CURRENCY: String = "TXN"

        fun zero(currencyCode: String = TRANSACTION_CURRENCY): Money = Money(0L, normalizeCurrency(currencyCode))

        fun ofMinor(amountMinor: Long, currencyCode: String = TRANSACTION_CURRENCY): Money =
            Money(amountMinor, normalizeCurrency(currencyCode))

        fun fromMajor(
            value: BigDecimal,
            currencyCode: String = TRANSACTION_CURRENCY,
        ): Money {
            val scaled = value.setScale(MINOR_SCALE, ROUNDING)
            val minor = try {
                scaled.movePointRight(MINOR_SCALE).longValueExact()
            } catch (error: ArithmeticException) {
                throw IllegalArgumentException("Money value is outside supported range", error)
            }
            return Money(minor, normalizeCurrency(currencyCode))
        }

        fun parse(
            raw: String,
            currencyCode: String = TRANSACTION_CURRENCY,
        ): Money = fromMajor(
            value = BigDecimal(NumberText.normalizeDecimal(raw)),
            currencyCode = currencyCode,
        )

        fun parseOrNull(
            raw: String,
            currencyCode: String = TRANSACTION_CURRENCY,
        ): Money? = runCatching { parse(raw, currencyCode) }.getOrNull()

        /** Compatibility conversion for already-materialized legacy values. */
        fun fromLegacyDouble(
            value: Double,
            currencyCode: String = TRANSACTION_CURRENCY,
        ): Money {
            require(value.isFinite()) { "Money value must be finite" }
            return fromMajor(BigDecimal.valueOf(value), currencyCode)
        }

        private fun normalizeCurrency(value: String): String = value.trim().uppercase().also {
            require(it.isNotBlank()) { "Currency code is required" }
            require(it.length <= 12) { "Currency code is too long" }
        }
    }
}

/** Whole-piece quantity. Fractional quantity can be introduced as a separate fixed-point type when required. */
data class Quantity private constructor(val units: Int) {
    init {
        require(units > 0) { "Quantity must be greater than zero" }
    }

    companion object {
        fun of(units: Int): Quantity = Quantity(units)

        fun parse(raw: String): Quantity {
            val normalized = NumberText.normalizeInteger(raw)
            val value = normalized.toLongOrNull()
                ?: throw IllegalArgumentException("Quantity must be a whole number")
            require(value in 1..Int.MAX_VALUE.toLong()) { "Quantity is outside supported range" }
            return Quantity(value.toInt())
        }

        fun parseOrNull(raw: String): Quantity? = runCatching { parse(raw) }.getOrNull()
    }
}

/** Fixed-point exchange rate. F246 will persist its currency direction and timestamp. */
data class ExchangeRate private constructor(
    val baseCurrency: String,
    val quoteCurrency: String,
    val scaledValue: Long,
    val scale: Int,
) {
    init {
        require(scale in 0..MAX_SCALE) { "Unsupported exchange-rate scale" }
        require(scaledValue > 0L) { "Exchange rate must be greater than zero" }
        require(baseCurrency.isNotBlank() && quoteCurrency.isNotBlank()) { "Exchange-rate currencies are required" }
    }

    fun asDecimal(): BigDecimal = BigDecimal.valueOf(scaledValue, scale)

    fun convert(amount: Money): Money = Money.fromMajor(
        amount.toMajorDecimal().multiply(asDecimal()),
        quoteCurrency,
    )

    companion object {
        const val MAX_SCALE: Int = 8

        fun one(
            baseCurrency: String = Money.TRANSACTION_CURRENCY,
            quoteCurrency: String = Money.TRANSACTION_CURRENCY,
        ): ExchangeRate = ExchangeRate(baseCurrency, quoteCurrency, 1L, 0)

        fun parse(
            raw: String,
            baseCurrency: String = Money.TRANSACTION_CURRENCY,
            quoteCurrency: String = Money.TRANSACTION_CURRENCY,
        ): ExchangeRate {
            val decimal = BigDecimal(NumberText.normalizeDecimal(raw))
            require(decimal.signum() > 0) { "Exchange rate must be greater than zero" }
            val normalized = decimal.stripTrailingZeros()
            val actualScale = normalized.scale().coerceAtLeast(0)
            require(actualScale <= MAX_SCALE) { "Exchange rate supports at most $MAX_SCALE decimal places" }
            val scaled = try {
                normalized.movePointRight(actualScale).longValueExact()
            } catch (error: ArithmeticException) {
                throw IllegalArgumentException("Exchange rate is outside supported range", error)
            }
            return ExchangeRate(
                baseCurrency = baseCurrency.trim().uppercase(),
                quoteCurrency = quoteCurrency.trim().uppercase(),
                scaledValue = scaled,
                scale = actualScale,
            )
        }

        fun fromLegacyDouble(
            value: Double,
            baseCurrency: String = Money.TRANSACTION_CURRENCY,
            quoteCurrency: String = Money.TRANSACTION_CURRENCY,
        ): ExchangeRate {
            require(value.isFinite() && value > 0.0) { "Exchange rate must be finite and greater than zero" }
            return parse(BigDecimal.valueOf(value).toPlainString(), baseCurrency, quoteCurrency)
        }
    }
}

/** Shared Arabic/English numeric normalization. Invalid text fails closed. */
object NumberText {
    private val arabicDigits = mapOf(
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9',
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
        '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9',
    )

    fun normalizeDecimal(raw: String): String {
        var text = normalizeDigits(raw).trim()
        require(text.isNotEmpty()) { "Number is required" }
        text = text.replace("\u00A0", "").replace(" ", "").replace("_", "")
            .replace('٫', '.')
            .replace('٬', ',')
            .replace('،', ',')

        val commaCount = text.count { it == ',' }
        val dotCount = text.count { it == '.' }
        text = when {
            commaCount == 0 -> text
            dotCount > 0 -> text.replace(",", "")
            commaCount == 1 -> {
                val idx = text.indexOf(',')
                val fractionDigits = text.length - idx - 1
                if (fractionDigits in 1..2) text.replace(',', '.') else text.replace(",", "")
            }
            else -> text.replace(",", "")
        }

        require(text.count { it == '.' } <= 1) { "Invalid decimal number" }
        require(text.matches(Regex("[+-]?\\d+(\\.\\d+)?"))) { "Invalid decimal number" }
        return text
    }

    fun normalizeInteger(raw: String): String {
        val text = normalizeDigits(raw).trim()
            .replace("\u00A0", "")
            .replace(" ", "")
            .replace("_", "")
            .replace("٬", "")
            .replace(",", "")
            .replace("،", "")
        require(text.matches(Regex("[+]?\\d+"))) { "Invalid whole number" }
        return text.removePrefix("+")
    }

    private fun normalizeDigits(raw: String): String = buildString(raw.length) {
        raw.forEach { append(arabicDigits[it] ?: it) }
    }
}
