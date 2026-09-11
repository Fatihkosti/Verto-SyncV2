package com.verto.app.core.format

/** تنسيق المبالغ بالسلوك نفسه المستخدم قبل الفصل عن قناة المشاركة. */
object AmountFormatter {
    fun format(amount: Double): String =
        if (amount == amount.toLong().toDouble()) amount.toLong().toString()
        else "%.2f".format(amount)
}
