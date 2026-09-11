package com.verto.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration96To97SyncRepairInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "sync-repair-96-97.db"
    private val invalidMoneyDbName = "sync-repair-invalid-money-96-97.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @After
    fun cleanup() {
        context.deleteDatabase(dbName)
        context.deleteDatabase(invalidMoneyDbName)
    }

    @Test
    fun nonEmpty96MigratesWithoutLosingPendingStateAndBackfillsExactMinorValues() {
        helper.createDatabase(dbName, 96).apply {
            execSQL(
                """INSERT INTO sync_outbox(
                    mutation_id,organization_id,aggregate_type,aggregate_id,operation_type,
                    base_version,local_sequence,aggregate_sequence,payload_version,payload_json,
                    semantic_fingerprint,state,attempt_count,next_attempt_at,created_at
                ) VALUES('m1','org1','EXPENSE','e1','UPSERT',NULL,1,1,1,'{}','fp','PENDING',0,0,1)"""
            )
            execSQL(
                """INSERT INTO sync_cursor(
                    scope_id,organization_id,sync_principal_id,contract_family,contract_version,
                    scope_definition_version,cursor_token,last_applied_change_revision,
                    page_high_watermark,min_available_revision,state,updated_at
                ) VALUES('scope1','org1','principal1','verto-unified-sync',1,1,'opaque-token',7,9,0,'ACTIVE',10)"""
            )
            execSQL(
                """INSERT INTO cash_reconciliation_sessions(
                    id,employeeId,employeeName,openingBalance,totalSales,totalRefunds,totalCashIn,
                    totalCashOut,expectedBalance,actualCountedBalance,variance,varianceReason,
                    status,startedAt,endedAt,notes
                ) VALUES('r1','employee','Employee',12.34,20.00,1.25,2.50,3.75,29.84,30.00,0.16,'','CLOSED',1,2,'')"""
            )
            execSQL(
                """INSERT INTO cash_denominations(
                    id,reconciliationId,denominationValue,count,subtotal,isCoin
                ) VALUES('d1','r1',0.50,3,1.50,1)"""
            )
            execSQL(
                """INSERT INTO sync_attachment_transfer(
                    transfer_id,organization_id,aggregate_type,aggregate_id,local_uri,object_key,
                    content_checksum,state,attempt_count,created_at
                ) VALUES('t1','org1','SHIPMENT_DOCUMENT','doc1','content://local/doc1',
                    'organizations/org1/shipments/s1/documents/doc1/hash','hash','PENDING',0,1)"""
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 97, true, MIGRATION_96_97)
        db.query("SELECT mutation_id,state FROM sync_outbox").use {
            assertTrue(it.moveToFirst())
            assertEquals("m1", it.getString(0))
            assertEquals("PENDING", it.getString(1))
            assertFalse(it.moveToNext())
        }
        db.query(
            """SELECT received_cursor_token,received_high_watermark,applied_checkpoint
               FROM sync_cursor WHERE scope_id='scope1'"""
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals("opaque-token", it.getString(0))
            assertEquals(9L, it.getLong(1))
            assertEquals(7L, it.getLong(2))
        }
        db.query(
            """SELECT opening_balance_minor,total_refunds_minor,variance_minor
               FROM cash_reconciliation_sessions WHERE id='r1'"""
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals(1234L, it.getLong(0))
            assertEquals(125L, it.getLong(1))
            assertEquals(16L, it.getLong(2))
        }
        db.query(
            """SELECT denomination_value_minor,subtotal_minor
               FROM cash_denominations WHERE id='d1'"""
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals(50L, it.getLong(0))
            assertEquals(150L, it.getLong(1))
        }
        db.query(
            """SELECT next_attempt_at,last_error_code,remote_checksum,remote_byte_size,
                      remote_verified_at,metadata_mutation_id,cancel_reason
               FROM sync_attachment_transfer WHERE transfer_id='t1'"""
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals(0L, it.getLong(0))
            for (index in 1..6) assertTrue(it.isNull(index))
        }
        val expectedTables = setOf(
            "sync_entity_version", "sync_local_generation", "sync_mutation_packet",
            "sync_pending_reference", "sync_write_batch", "sync_write_batch_member",
            "sync_inbox_group", "sync_migration_evidence_v2", "sync_snapshot_blob",
            "expense_revision_history",
        )
        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name IN (${expectedTables.joinToString { "'$it'" }})"
        ).use {
            val found = mutableSetOf<String>()
            while (it.moveToNext()) found += it.getString(0)
            assertEquals(expectedTables, found)
        }

        val hash = "a".repeat(64)
        db.execSQL(
            """INSERT INTO sync_mutation_packet(
                organization_id,mutation_id,source_owner,source_id,business_identity,intent_json,
                intent_hash,captured_generation,captured_content_hash,version_family,created_at
            ) VALUES('org1','packet1','sync_outbox','m1','expense:e1','{}',?,1,?,'expense',11)""",
            arrayOf(hash, hash),
        )
        db.execSQL(
            """INSERT INTO sync_migration_evidence_v2(
                organization_id,source_kind,source_id,source_content_hash,repair_version,raw_type,
                operation_type,source_state,classification,evidence_type,target_mutation_id,
                disposition,created_at,updated_at
            ) VALUES('org1','sync_outbox','m1',?,1,'EXPENSE','UPSERT','PENDING',
                'MIGRATED','CONTENT_HASH','packet1','PRESERVED',11,11)""",
            arrayOf(hash),
        )
        db.execSQL("DELETE FROM sync_mutation_packet WHERE organization_id='org1' AND mutation_id='packet1'")
        db.query("SELECT count(*) FROM sync_migration_evidence_v2 WHERE target_mutation_id='packet1'").use {
            assertTrue(it.moveToFirst())
            assertEquals(1L, it.getLong(0))
        }
        try {
            db.execSQL(
                """INSERT INTO sync_write_batch_member(
                    organization_id,batch_id,member_order,mutation_id,source_owner,source_id
                ) VALUES('org1','bad-batch',-1,'bad-mutation','sync_outbox','m1')"""
            )
            throw AssertionError("negative member order must violate the SQLite constraint")
        } catch (_: SQLiteConstraintException) {
            // Expected: evidence has no cascade, while structural constraints remain enforced.
        }
        db.close()
    }

    @Test
    fun nonFiniteLegacyMoneyFailsClosedForManualReview() {
        helper.createDatabase(invalidMoneyDbName, 96).apply {
            execSQL(
                """INSERT INTO cash_reconciliation_sessions(
                    id,employeeId,employeeName,openingBalance,totalSales,totalRefunds,totalCashIn,
                    totalCashOut,expectedBalance,actualCountedBalance,variance,varianceReason,
                    status,startedAt,endedAt,notes
                ) VALUES('invalid','employee','Employee',1e999,0,0,0,0,0,0,0,'','OPEN',1,NULL,'')"""
            )
            close()
        }

        try {
            helper.runMigrationsAndValidate(invalidMoneyDbName, 97, true, MIGRATION_96_97).close()
            throw AssertionError("non-finite legacy money must not be silently rounded")
        } catch (failure: Throwable) {
            val messages = generateSequence(failure) { it.cause }.mapNotNull { it.message }.joinToString(" | ")
            assertTrue(messages, "MONEY_MIGRATION_REVIEW_REQUIRED" in messages)
        }
    }
}
