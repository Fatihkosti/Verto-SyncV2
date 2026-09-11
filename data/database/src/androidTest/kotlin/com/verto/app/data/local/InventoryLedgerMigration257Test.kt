package com.verto.app.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InventoryLedgerMigration257Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun schema_72_to_73_preserves_legacy_and_adds_identity_constraints() {
        val dbName = "inventory-v257-72-73"
        helper.createDatabase(dbName, 72).apply {
            execSQL(
                """
                INSERT INTO inventory_items(
                    id,partNumber,name,barcode,isUnitItem,quantityPerUnit,isService,buyPrice,sellPrice,
                    quantity,minQuantity,location,note,createdAt,updatedAt,isDirty
                ) VALUES ('item-1','P1','Item','','0',0,0,100,120,10,1,'','',1,1,0)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO inventory_movements(
                    id,itemId,invoiceId,clientId,movementType,quantity,quantityBefore,quantityAfter,
                    unitPrice,note,shipmentId,createdAt
                ) VALUES ('legacy-1','item-1','','','ADJUST',1,9,10,0,'legacy','',1)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 73, true, MIGRATION_72_73)
        db.query(
            "SELECT contract_version, movement_kind, signed_base_quantity, idempotency_key " +
                "FROM inventory_movements WHERE id='legacy-1'"
        ).use { cursor ->
            check(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertEquals(true, cursor.isNull(1))
            assertEquals(true, cursor.isNull(2))
            assertEquals(true, cursor.isNull(3))
        }

        insertCanonicalMovement(db, id = "m1", idempotency = "sale:invoice-1:line-1", reversal = null)
        assertThrows(SQLiteConstraintException::class.java) {
            insertCanonicalMovement(db, id = "m2", idempotency = "sale:invoice-1:line-1", reversal = null)
        }

        insertCanonicalMovement(db, id = "r1", idempotency = "reverse:1", reversal = "m1")
        assertThrows(SQLiteConstraintException::class.java) {
            insertCanonicalMovement(db, id = "r2", idempotency = "reverse:2", reversal = "m1")
        }

        insertCostRevision(db, id = "c1", idempotency = "cost:1", reversal = null)
        assertThrows(SQLiteConstraintException::class.java) {
            insertCostRevision(db, id = "c2", idempotency = "cost:1", reversal = null)
        }
        db.close()
    }

    private fun insertCanonicalMovement(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        id: String,
        idempotency: String,
        reversal: String?,
    ) {
        val kind = if (reversal == null) "SALE" else "REVERSAL"
        val reverseSql = reversal?.let { "'$it'" } ?: "NULL"
        db.execSQL(
            """
            INSERT INTO inventory_movements(
                id,itemId,invoiceId,clientId,movementType,quantity,quantityBefore,quantityAfter,
                unitPrice,note,shipmentId,source_type,source_id,source_version,write_id,createdAt,
                organization_id,movement_kind,signed_base_quantity,source_line_id,command_id,
                idempotency_key,posting_group_id,reverses_movement_id,occurred_at,recorded_at,
                created_by,device_id,contract_version
            ) VALUES (
                '$id','item-1','invoice-1','','OUT',2,10,8,0,'','',
                'INVOICE','invoice-1',1,'write-$id',1,
                'org-1','$kind',-2,'line-1','cmd-$id','$idempotency','invoice-1',$reverseSql,
                100,101,'user-1','device-1',2
            )
            """.trimIndent()
        )
    }

    private fun insertCostRevision(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        id: String,
        idempotency: String,
        reversal: String?,
    ) {
        val kind = if (reversal == null) "LOCAL_PURCHASE_APPROVED" else "REVERSAL"
        val reverseSql = reversal?.let { "'$it'" } ?: "NULL"
        db.execSQL(
            """
            INSERT INTO inventory_cost_revisions(
                cost_revision_id,organization_id,item_id,source_type,source_id,source_line_id,
                revision_kind,direct_purchase_cost_minor,landed_cost_per_base_unit_minor,
                approved_inventory_cost_minor,currency_code,exchange_rate_snapshot,allocation_basis,
                allocation_residual_minor,is_provisional,reverses_cost_revision_id,command_id,
                idempotency_key,cost_sequence,approved_at,recorded_at,created_by,device_id,contract_version
            ) VALUES (
                '$id','org-1','item-1','PURCHASE_INVOICE','purchase-1','line-1','$kind',
                10000,0,10000,'SDG','1.000000','',0,0,$reverseSql,'cmd-$id','$idempotency',
                NULL,100,101,'user-1','device-1',2
            )
            """.trimIndent()
        )
    }
}
