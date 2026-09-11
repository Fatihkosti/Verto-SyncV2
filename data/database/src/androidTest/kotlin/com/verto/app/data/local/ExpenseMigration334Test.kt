package com.verto.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpenseMigration334Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun schema_81_to_82_backfills_minor_units_without_losing_identity_or_lifecycle() {
        val name = "expense-v334-migration"
        helper.createDatabase(name, 81).apply {
            execSQL("INSERT INTO expenses(id,category,item,amount,note,date,isDirty,lifecycle_state) VALUES('active','ops','fuel',10.10,'',1,1,'ACTIVE')")
            execSQL("INSERT INTO expenses(id,category,item,amount,note,date,isDirty,lifecycle_state,voided_at,void_reason,reversal_write_id) VALUES('void','admin','fee',0.01,'',2,1,'VOID',3,'USER_DELETE','rw')")
            execSQL("INSERT INTO expenses(id,category,item,amount,note,date,isDirty,lifecycle_state) VALUES('large','ops','rent',999999.99,'',4,1,'ACTIVE')")
            execSQL("INSERT INTO expenses(id,category,item,amount,note,date,isDirty,lifecycle_state) VALUES('halfup','ops','edge',1.005,'',5,1,'ACTIVE')")
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 82, true, MIGRATION_81_82)
        db.query("SELECT id,lifecycle_state,amount_minor FROM expenses ORDER BY id").use { cursor ->
            assertEquals(4, cursor.count)
            val rows = buildMap {
                while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1) to cursor.getLong(2))
            }
            assertEquals("ACTIVE" to 1010L, rows["active"])
            assertEquals("VOID" to 1L, rows["void"])
            assertEquals("ACTIVE" to 99_999_999L, rows["large"])
            assertEquals("ACTIVE" to 101L, rows["halfup"])
        }
        db.execSQL("INSERT INTO expenses(id,category,item,amount,amount_minor,note,date,isDirty,lifecycle_state) VALUES('new','ops','paper',12.34,1234,'',5,1,'ACTIVE')")
        db.query("SELECT amount_minor FROM expenses WHERE id='new'").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1234L, cursor.getLong(0))
        }
        db.close()
    }
}
