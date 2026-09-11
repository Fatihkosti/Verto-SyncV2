package com.verto.app.data.local.entity

import androidx.room.*
import kotlinx.serialization.Serializable
import java.util.UUID
import com.verto.app.money.Money

@Serializable
@Entity(
    tableName = "cash_reconciliation_sessions",
    indices = [
        Index("startedAt"),
        Index("endedAt"),
        Index("employeeId"),
        Index("status")
    ]
)
data class CashReconciliationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),

    val employeeId: String = "",
    val employeeName: String = "",

    val openingBalance: Double = 0.0,

    val totalSales: Double = 0.0,
    val totalRefunds: Double = 0.0,
    val totalCashIn: Double = 0.0,
    val totalCashOut: Double = 0.0,

    val expectedBalance: Double = 0.0,
    val actualCountedBalance: Double = 0.0,
    val variance: Double = 0.0,
    val varianceReason: String = "",

    val status: ReconciliationStatus = ReconciliationStatus.OPEN,

    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val notes: String = "",

    @ColumnInfo(name = "opening_balance_minor", defaultValue = "0")
    val openingBalanceMinor: Long = Money.fromLegacyDouble(openingBalance).amountMinor,
    @ColumnInfo(name = "total_sales_minor", defaultValue = "0")
    val totalSalesMinor: Long = Money.fromLegacyDouble(totalSales).amountMinor,
    @ColumnInfo(name = "total_refunds_minor", defaultValue = "0")
    val totalRefundsMinor: Long = Money.fromLegacyDouble(totalRefunds).amountMinor,
    @ColumnInfo(name = "total_cash_in_minor", defaultValue = "0")
    val totalCashInMinor: Long = Money.fromLegacyDouble(totalCashIn).amountMinor,
    @ColumnInfo(name = "total_cash_out_minor", defaultValue = "0")
    val totalCashOutMinor: Long = Money.fromLegacyDouble(totalCashOut).amountMinor,
    @ColumnInfo(name = "expected_balance_minor", defaultValue = "0")
    val expectedBalanceMinor: Long = Money.fromLegacyDouble(expectedBalance).amountMinor,
    @ColumnInfo(name = "actual_counted_balance_minor", defaultValue = "0")
    val actualCountedBalanceMinor: Long = Money.fromLegacyDouble(actualCountedBalance).amountMinor,
    @ColumnInfo(name = "variance_minor", defaultValue = "0")
    val varianceMinor: Long = Money.fromLegacyDouble(variance).amountMinor,
)
