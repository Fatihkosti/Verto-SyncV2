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
class TeamObservationCategoryMigration359Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migration_87_88_adds_category_with_idea_default() {
        val name = "team-observation-category-v359"
        helper.createDatabase(name, 87).apply {
            execSQL(
                "INSERT INTO team_observations(" +
                    "organization_id,observation_id,text,author_user_id,author_name,status,is_important,created_at,updated_at,updated_by_user_id,is_dirty" +
                    ") VALUES(" +
                    "'00000000-0000-0000-0000-000000000001'," +
                    "'00000000-0000-0000-0000-000000000002'," +
                    "'legacy note'," +
                    "'00000000-0000-0000-0000-000000000003'," +
                    "'Tester','NEW',0,1,1," +
                    "'00000000-0000-0000-0000-000000000003',1" +
                    ")"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 88, true, MIGRATION_87_88)
        db.query("SELECT category FROM team_observations WHERE text='legacy note'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("IDEA", cursor.getString(0))
        }
        db.close()
    }
}
