package com.verto.app.utils

import com.verto.app.money.Money
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Legacy compatibility math for screens/features not yet migrated to [Money].
 * New financial domain code must use Money/Quantity/ExchangeRate directly.
 */
object MoneyMath {
    private const val SCALE = Money.MINOR_SCALE
    private val ROUNDING = Money.ROUNDING

    private fun Double.bd(): BigDecimal {
        require(isFinite()) { "Money value must be finite" }
        return BigDecimal.valueOf(this)
    }

    private fun BigDecimal.moneyDouble(): Double = setScale(SCALE, ROUNDING).toDouble()

    fun add(a: Double, b: Double): Double = a.bd().add(b.bd()).moneyDouble()

    fun subtract(a: Double, b: Double): Double = a.bd().subtract(b.bd()).moneyDouble()

    fun multiply(price: Double, quantity: Int): Double =
        price.bd().multiply(BigDecimal.valueOf(quantity.toLong())).moneyDouble()

    fun multiply(a: Double, b: Double): Double = a.bd().multiply(b.bd()).moneyDouble()

    fun divide(a: Double, b: Double): Double {
        require(b != 0.0) { "Division by zero is not a financial value" }
        return a.bd().divide(b.bd(), SCALE, ROUNDING).toDouble()
    }

    fun Iterable<Double>.moneySum(): Double =
        fold(BigDecimal.ZERO) { acc, value -> acc.add(value.bd()) }.moneyDouble()

    fun isGreaterThan(a: Double, b: Double): Boolean =
        a.bd().setScale(SCALE, ROUNDING).compareTo(b.bd().setScale(SCALE, ROUNDING)) > 0

    fun isLessOrEqual(a: Double, b: Double): Boolean =
        a.bd().setScale(SCALE, ROUNDING).compareTo(b.bd().setScale(SCALE, ROUNDING)) <= 0

    fun isEffectivelyZero(amount: Double): Boolean =
        amount.bd().setScale(SCALE, ROUNDING).compareTo(BigDecimal.ZERO) == 0

    fun round(amount: Double): Double = amount.bd().moneyDouble()
}
