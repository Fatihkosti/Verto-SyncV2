package com.verto.app.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LogisticsShipmentNumberConcurrencyTest {
    @Test
    fun concurrent_allocation_is_unique_per_organization() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.logisticsShipmentNumberDao()
            val numbers = (1..64).map {
                async(Dispatchers.IO) { dao.allocateNext("org-a") }
            }.awaitAll().filterNotNull()

            assertEquals(64, numbers.size)
            assertEquals((1..64).toSet(), numbers.toSet())
            assertEquals(1, dao.allocateNext("org-b"))
        } finally {
            db.close()
        }
    }
}
