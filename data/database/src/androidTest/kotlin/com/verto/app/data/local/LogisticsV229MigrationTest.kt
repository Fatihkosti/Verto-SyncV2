package com.verto.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LogisticsV229MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun every_exported_supported_schema_migrates_to_current() {
        for (from in 39..55) {
            val dbName = "verto-v229-migration-$from"
            helper.createDatabase(dbName, from).close()
            helper.runMigrationsAndValidate(
                dbName,
                ROOM_SCHEMA_VERSION,
                true,
                *migrationPath(from).toTypedArray(),
            ).close()
        }
    }
}
