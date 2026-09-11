package com.verto.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F251 migration gate. This deliberately spans every schema in the supported Room chain.
 * It must not be weakened to the currently-present schema assets: missing exports are a release blocker.
 */
@RunWith(AndroidJUnit4::class)
class InvoiceFinancialMigration251Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun every_supported_exported_schema_migrates_to_current() {
        for (from in 39 until ROOM_SCHEMA_VERSION) {
            val dbName = "invoice-f251-migration-$from"
            helper.createDatabase(dbName, from).close()
            helper.runMigrationsAndValidate(
                dbName,
                ROOM_SCHEMA_VERSION,
                true,
                *migrationPath(from).toTypedArray(),
            ).close()
        }
    }
    @Test
    fun schema_67_advance_credit_backfills_exact_minor_units() {
        val dbName = "invoice-f251-credit-money"
        helper.createDatabase(dbName, 67).apply {
            execSQL(
                """
                INSERT INTO client_credits(
                    id,clientId,amount,note,sourcePaymentId,createdAt,employeeId,employeeName,isDirty
                ) VALUES ('credit-a','client',0.1,'','','1','','',0),
                         ('credit-b','client',0.2,'','','2','','',0)
                """.trimIndent()
            )
            close()
        }
        val migrated = helper.runMigrationsAndValidate(
            dbName, ROOM_SCHEMA_VERSION, true, *migrationPath(67).toTypedArray()
        )
        migrated.query("SELECT SUM(amount_minor) FROM client_credits WHERE clientId='client'").use { cursor ->
            check(cursor.moveToFirst())
            org.junit.Assert.assertEquals(30L, cursor.getLong(0))
        }
        migrated.close()
    }

    @Test
    fun legacy_international_invoice_migrates_fail_closed_as_unknown_currency() {
        val dbName = "invoice-f251-legacy-international"
        helper.createDatabase(dbName, 61).apply {
            execSQL(
                """
                INSERT INTO clients(
                    id,name,phone,nameSearch,phoneSearch,address,workplace,generalNote,clientType,
                    carType,bankAccount,specialty,secondaryPhones,createdAt,createdBy,isDirty
                ) VALUES ('supplier','Supplier','249','','','','','','SUPPLIER','','','','',1,'tester',0)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO invoices(
                    id,invoiceNumber,invoiceNumberSearch,clientId,type,category,description,totalAmount,
                    createdAt,dueDate,notifyDaysBefore,notifyRepeatDays,notificationsEnabled,notes,isOwedToMe,
                    imageUri,status,commission,shipmentId,purchase_scope,createdBy,voided,isDirty
                ) VALUES (
                    'intl-legacy',251,'251','supplier','GOODS','PURCHASE','legacy international',123.45,
                    1,1,'',0,0,'',0,'','CLOSED_CASH',0,NULL,'INTERNATIONAL','tester',0,0
                )
                """.trimIndent()
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            dbName,
            ROOM_SCHEMA_VERSION,
            true,
            *migrationPath(61).toTypedArray(),
        )
        migrated.query(
            "SELECT total_amount_minor, transaction_amount_minor, legacy_currency_status " +
                "FROM invoices WHERE id = 'intl-legacy'"
        ).use { cursor ->
            check(cursor.moveToFirst())
            org.junit.Assert.assertEquals(12_345L, cursor.getLong(0))
            org.junit.Assert.assertEquals(12_345L, cursor.getLong(1))
            org.junit.Assert.assertEquals("UNKNOWN", cursor.getString(2))
        }
        migrated.close()
    }

}
