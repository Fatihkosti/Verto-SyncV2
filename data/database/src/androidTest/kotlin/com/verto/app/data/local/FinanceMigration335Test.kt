package com.verto.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinanceMigration335Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migration_82_83_adds_finance_indexes_and_retires_cash_register_client_commands() {
        val name = "finance-v335-migration"
        helper.createDatabase(name, 82).apply {
            execSQL(
                "INSERT INTO sync_outbox(mutation_id,organization_id,aggregate_type,aggregate_id,operation_type,local_sequence,aggregate_sequence,payload_version,payload_json,semantic_fingerprint,state,created_at) " +
                    "VALUES('m1','org','CASH_REGISTER','main','COMMAND',1,1,1,'{}','fp','PENDING',1)"
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 83, true, MIGRATION_82_83)
        db.query("SELECT state,last_error_code FROM sync_outbox WHERE mutation_id='m1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("REJECTED", cursor.getString(0))
            assertEquals("SERVER_AUTHORITATIVE_NO_CLIENT_PUSH", cursor.getString(1))
        }
        val indexes = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type='index'").use { cursor ->
            while (cursor.moveToNext()) indexes += cursor.getString(0)
        }
        assertTrue("index_expenses_lifecycle_date" in indexes)
        assertTrue("index_expenses_lifecycle_category_date" in indexes)
        assertTrue("index_cash_register_movements_type_reference" in indexes)
        db.close()
    }
}
