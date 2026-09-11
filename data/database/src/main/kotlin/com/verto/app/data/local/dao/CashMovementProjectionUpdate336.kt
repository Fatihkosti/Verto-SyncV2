package com.verto.app.data.local.dao

import androidx.room.ColumnInfo

/** Session 336: only server-authoritative presentation/projection columns may be reconciled. */
data class CashMovementProjectionUpdate336(
    val id: String,
    val amount: Double,
    val balanceBefore: Double,
    @ColumnInfo(name = "balance_before_minor") val balanceBeforeMinor: Long,
    val balanceAfter: Double,
    @ColumnInfo(name = "balance_after_minor") val balanceAfterMinor: Long,
)
