package com.verto.app.data.local.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.verto.app.data.local.*
import com.verto.app.data.local.entity.*
import com.verto.app.money.Money
import kotlinx.coroutines.flow.Flow

interface InventoryStockTargetDao : InventoryCatalogReadDao {
@Query("SELECT * FROM inventory_items WHERE linkedUnitItemId=:unitItemId AND isUnitItem=0 AND is_archived=0 LIMIT 1")
suspend fun getBaseItemByUnitAlias(unitItemId: String): InventoryItemEntity?

@Query("SELECT quantity_per_unit_base FROM inventory_units WHERE id=:unitId LIMIT 1")
suspend fun getUnitBaseFactor(unitId: String): Long?

suspend fun resolveBaseStockTarget(itemId: String, quantity: Int, unitPrice: Double): InventoryBaseStockTarget {
    val requested = getItemByIdSync(itemId) ?: error("القطعة غير موجودة")
    if (!requested.isUnitItem) return InventoryBaseStockTarget(requested, quantity, 1L, Money.fromLegacyDouble(unitPrice))
    val directBase = requested.linkedUnitItemId?.let { getItemByIdSync(it) }?.takeUnless { it.isUnitItem }
    val base = directBase ?: getBaseItemByUnitAlias(requested.id)
        ?: error("وحدة المخزون غير مرتبطة بصنف أساسي")
    val factor = requested.quantityPerUnit.takeIf { it > 0.0 }?.let { raw ->
        require(raw.isFinite() && raw % 1.0 == 0.0) { "عامل تحويل الوحدة التاريخي غير صحيح" }
        raw.toLong()
    } ?: base.unitId?.let { getUnitBaseFactor(it) }?.takeIf { it > 0L }
        ?: error("عامل تحويل الوحدة الأساسية مفقود")
    val baseQuantity = Math.multiplyExact(quantity.toLong(), factor).also {
        require(it <= Int.MAX_VALUE) { "كمية الوحدة تتجاوز النطاق المدعوم" }
    }.toInt()
    val unitMinor = Money.fromLegacyDouble(unitPrice).amountMinor
    val quotient = unitMinor / factor
    val remainder = unitMinor % factor
    val roundedMinor = Math.addExact(quotient, if (remainder * 2L >= factor) 1L else 0L)
    return InventoryBaseStockTarget(base, baseQuantity, factor, Money.ofMinor(roundedMinor))
}
}

