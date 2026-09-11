package com.verto.app.feature.inventory.application

import com.verto.app.feature.inventory.domain.model.*
import com.verto.app.feature.inventory.domain.port.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class InventoryPriceCoordinatorTest {
    @Test fun `negative prices are clamped and item becomes dirty`() = runTest {
        val store=FakeStore(item())
        val updated=InventoryPriceCoordinator(store).update(InventoryPriceChange(item(),-1.0,-5.0,99L))
        assertEquals(0.0,updated.buyPrice,0.0); assertEquals(0.0,updated.sellPrice,0.0); assertEquals(99L,updated.updatedAt); assertTrue(updated.isDirty)
    }
    @Test fun `linked unit price follows piece price times unit quantity`() = runTest {
        val piece=item(unitId="u", linkedUnitItemId="box", buy=2.0, sell=3.0)
        val linked=item(id="box", name="Box")
        val store=FakeStore(piece, linked).apply { units["u"]=InventoryUnit("u","box",12.0) }
        InventoryPriceCoordinator(store).update(InventoryPriceChange(piece,4.0,5.0,10L))
        val box=store.items.getValue("box"); assertEquals(48.0,box.buyPrice,0.0); assertEquals(60.0,box.sellPrice,0.0)
    }
    @Test fun `batch ignores duplicate ids and no op changes`() = runTest {
        val a=item(id="a", buy=1.0,sell=2.0); val b=item(id="b",buy=5.0,sell=6.0); val events=FakeEvents(); val store=FakeStore(a,b)
        val result=InventoryPriceCoordinator(store,events).updateBatch("batch", listOf(InventoryPriceChange(a,2.0,3.0,1), InventoryPriceChange(a,9.0,9.0,2), InventoryPriceChange(b,5.0,6.0,3)))
        assertEquals(listOf("a"),result.map{it.id}); assertTrue(events.records.isEmpty())
    }
    @Test(expected=IllegalArgumentException::class) fun `blank batch id is rejected`() = runTest { InventoryPriceCoordinator(FakeStore()).updateBatch(" ", emptyList()) }

    private fun item(id:String="i",name:String="Piece",unitId:String?=null,linkedUnitItemId:String?=null,buy:Double=1.0,sell:Double=2.0)=InventoryItem(id=id,name=name,unitId=unitId,linkedUnitItemId=linkedUnitItemId,buyPrice=buy,sellPrice=sell,createdAt=1,updatedAt=1)
    private class FakeStore(vararg initial:InventoryItem):InventoryStorePort { val items=initial.associateBy{it.id}.toMutableMap(); val units=mutableMapOf<String,InventoryUnit>(); override suspend fun getItem(itemId:String)=items[itemId]; override suspend fun getAllItems()=items.values.toList(); override suspend fun saveItem(item:InventoryItem){items[item.id]=item}; override suspend fun deleteItem(itemId:String){items.remove(itemId)}; override suspend fun replaceCategories(itemId:String,categories:List<String>){}; override suspend fun getUnit(unitId:String)=units[unitId]; override suspend fun saveUnit(unit:InventoryUnit){units[unit.id]=unit} }
    private class FakeEvents:InventoryPriceBatchEventPort { val records=mutableListOf<List<InventoryPriceBatchChangeRecord>>(); override suspend fun recordBatch(batchId:String,changes:List<InventoryPriceBatchChangeRecord>){records+=changes} }
}
