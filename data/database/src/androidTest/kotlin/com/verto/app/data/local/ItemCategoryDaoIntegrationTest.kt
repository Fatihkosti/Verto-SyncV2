package com.verto.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.verto.app.data.local.entity.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ItemCategoryDaoIntegrationTest {
    private lateinit var db:AppDatabase
    @Before fun createDb(){ db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),AppDatabase::class.java).allowMainThreadQueries().build() }
    @After fun closeDb(){db.close()}
    @Test fun distinctCategoriesAreSortedAndReplaceKeepsIdentity() = runTest {
        db.inventoryDao().insertItem(InventoryItemEntity(id="i",name="Item"))
        db.itemCategoryDao().insertCategory(ItemCategoryEntity("1","i","Zeta")); db.itemCategoryDao().insertCategory(ItemCategoryEntity("2","i","Alpha")); db.itemCategoryDao().insertCategory(ItemCategoryEntity("3","i","Alpha"))
        assertEquals(listOf("Alpha","Zeta"),db.itemCategoryDao().getAllDistinctCategories().first())
        db.itemCategoryDao().insertCategory(ItemCategoryEntity("1","i","Beta"))
        assertEquals("Beta",db.itemCategoryDao().getAllItemCategoriesSync().first{it.id=="1"}.category)
    }
    @Test fun archivingItemPreservesCategoryHistory() = runTest {
        val item=InventoryItemEntity(id="i",name="Item"); db.inventoryDao().insertItem(item); db.itemCategoryDao().insertCategory(ItemCategoryEntity("1","i","A")); db.inventoryDao().deleteItem(item.id); assertEquals(1, db.itemCategoryDao().getAllItemCategoriesSync().size)
    }
}
