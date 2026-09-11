package com.verto.app.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedSyncAggregateRegistryV304Test {
    private val expectedIds = setOf(
        "PARTY_IDENTITY", "PARTY_ROLE", "CUSTOMER_PROFILE", "SUPPLIER_PROFILE", "NOTE", "REMINDER",
        "INVOICE", "PAYMENT", "CLIENT_CREDIT", "PURCHASE_ORDER", "GOODS_RECEIPT", "PURCHASE_MATCH",
        "PURCHASE_PAYMENT_OVERRIDE", "INVENTORY_ITEM", "INVENTORY_UNIT", "CATEGORY", "ITEM_CATEGORY",
        "INVENTORY_MOVEMENT", "INVENTORY_COST_REVISION", "COST_ALLOCATION", "EXPENSE", "BUDGET",
        "CASH_REGISTER", "CASH_MOVEMENT", "CASH_RECONCILIATION", "COMMISSION_PAYMENT", "PRICE_LIST",
        "NOTIFICATION", "ORGANIZATION_SETTINGS", "SHIPMENT", "EDUCATIONAL_CONTENT", "OPTIMAL_VEHICLE",
        "OPTIMAL_MAINTENANCE", "OPTIMAL_FOLLOW_UP", "TEAM_OBSERVATION",
    )

    @Test fun `registry contains the complete official v304 aggregate set exactly once`() {
        assertEquals(expectedIds, UnifiedSyncAggregateRegistry.all.map { it.id }.toSet())
        assertEquals(expectedIds.size, UnifiedSyncAggregateRegistry.all.size)
        assertEquals(expectedIds.size, UnifiedSyncAggregateRegistry.byId.size)
    }

    @Test fun `registry is deterministic and upper snake case`() {
        val ids = UnifiedSyncAggregateRegistry.all.map { it.id }
        assertEquals(ids.sorted(), ids)
        assertTrue(ids.all { it.matches(Regex("[A-Z][A-Z0-9_]*")) })
    }

    @Test fun `payload versions are positive and party remains on proven v2 contract`() {
        assertTrue(UnifiedSyncAggregateRegistry.all.all { it.payloadVersion > 0 })
        listOf("PARTY_IDENTITY", "PARTY_ROLE", "CUSTOMER_PROFILE", "SUPPLIER_PROFILE").forEach {
            assertEquals(2, UnifiedSyncAggregateRegistry.requireById(it).payloadVersion)
        }
    }

    @Test fun `financial and ledger aggregates never use scoped lww`() {
        val unsafe = UnifiedSyncAggregateRegistry.all.filter {
            it.financialSensitivity != UnifiedSyncFinancialSensitivity.NONE &&
                it.conflictPolicy == UnifiedSyncConflictPolicy.EXPLICIT_SCOPED_LWW
        }
        assertTrue(unsafe.isEmpty())
    }

    @Test fun `payment and cash movements are append only idempotent`() {
        assertEquals(UnifiedSyncConflictPolicy.APPEND_ONLY_IDEMPOTENT, UnifiedSyncAggregateRegistry.requireById("PAYMENT").conflictPolicy)
        assertEquals(UnifiedSyncConflictPolicy.APPEND_ONLY_IDEMPOTENT, UnifiedSyncAggregateRegistry.requireById("CASH_MOVEMENT").conflictPolicy)
    }

    @Test fun `inventory ledger keeps append only and immutable revision semantics`() {
        assertEquals(UnifiedSyncConflictPolicy.APPEND_ONLY_IDEMPOTENT, UnifiedSyncAggregateRegistry.requireById("INVENTORY_MOVEMENT").conflictPolicy)
        assertEquals(UnifiedSyncConflictPolicy.IMMUTABLE_REVISION, UnifiedSyncAggregateRegistry.requireById("INVENTORY_COST_REVISION").conflictPolicy)
    }

    @Test fun `shipment remains server state machine and cancel transition`() {
        val shipment = UnifiedSyncAggregateRegistry.requireById("SHIPMENT")
        assertEquals(UnifiedSyncConflictPolicy.SERVER_STATE_MACHINE, shipment.conflictPolicy)
        assertEquals(UnifiedSyncDeletePolicy.CANCEL_STATE_TRANSITION, shipment.deletePolicy)
        assertEquals(UnifiedSyncAttachmentPolicy.METADATA_ONLY_EXTERNAL_BINARY, shipment.attachmentPolicy)
    }

    @Test fun `team observation matches live server state machine contract`() {
        val team = UnifiedSyncAggregateRegistry.requireById("TEAM_OBSERVATION")
        assertEquals(1, team.payloadVersion)
        assertEquals(UnifiedSyncConflictPolicy.SERVER_STATE_MACHINE, team.conflictPolicy)
        assertEquals(UnifiedSyncDeletePolicy.NO_CLIENT_DELETE, team.deletePolicy)
        assertTrue("verto_apply_team_observation_v403" in team.currentServerPaths)
    }

    @Test fun `binary attachment aggregates synchronize metadata only`() {
        val binaryAggregates = UnifiedSyncAggregateRegistry.all.filter {
            it.attachmentPolicy == UnifiedSyncAttachmentPolicy.METADATA_ONLY_EXTERNAL_BINARY
        }.map { it.id }.toSet()
        assertEquals(setOf("GOODS_RECEIPT", "SHIPMENT", "OPTIMAL_MAINTENANCE"), binaryAggregates)
    }

    @Test fun `all registry server paths are explicit and never wildcard placeholders`() {
        assertTrue(UnifiedSyncAggregateRegistry.all.all { it.currentServerPaths.isNotEmpty() })
        assertFalse(UnifiedSyncAggregateRegistry.all.flatMap { it.currentServerPaths }.any { "*" in it })
    }

    @Test fun `unknown aggregate fails closed`() {
        try {
            UnifiedSyncAggregateRegistry.requireById("UNKNOWN")
            throw AssertionError("Expected contract violation")
        } catch (e: SyncContractViolation) {
            assertEquals("CONTRACT_UNSUPPORTED", e.code)
        }
    }
}
