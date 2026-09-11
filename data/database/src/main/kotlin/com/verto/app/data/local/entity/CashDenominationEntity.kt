package com.verto.app.data.local.entity

import androidx.room.*
import kotlinx.serialization.Serializable
import java.util.UUID
import com.verto.app.money.Money

@Serializable
@Entity(
    tableName = "cash_denominations",
    foreignKeys = [ForeignKey(
        entity = CashReconciliationEntity::class,
        parentColumns = ["id"],
        childColumns = ["reconciliationId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("reconciliationId")]
)
data class CashDenominationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),

    val reconciliationId: String,

    val denominationValue: Double,
    val count: Int = 0,
    val subtotal: Double = 0.0,

    val isCoin: Boolean = false,

    @ColumnInfo(name = "denomination_value_minor", defaultValue = "0")
    val denominationValueMinor: Long = Money.fromLegacyDouble(denominationValue).amountMinor,
    @ColumnInfo(name = "subtotal_minor", defaultValue = "0")
    val subtotalMinor: Long = Money.fromLegacyDouble(subtotal).amountMinor,
)
