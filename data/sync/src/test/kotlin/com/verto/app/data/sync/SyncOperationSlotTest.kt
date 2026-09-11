package com.verto.app.data.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncOperationSlotTest {

    @Test
    fun `complete central sync plan has unique stage order pairs`() {
        val duplicates = SyncOperationSlot.entries
            .groupBy { it.stage to it.order }
            .filterValues { it.size > 1 }

        assertTrue("Duplicate central sync slots: $duplicates", duplicates.isEmpty())
    }

    @Test
    fun `organization settings and inventory categories push slots never collide`() {
        val organization = SyncOperationSlot.PUSH_ORGANIZATION_SETTINGS
        val inventory = SyncOperationSlot.PUSH_INVENTORY_CATEGORIES

        assertEquals(SyncStage.PUSH, organization.stage)
        assertEquals(SyncStage.PUSH, inventory.stage)
        assertTrue(
            "organization settings must remain after inventory categories in PUSH dependency order",
            organization.order > inventory.order,
        )
        assertEquals(170, organization.order)
        assertEquals(160, inventory.order)
    }

    @Test
    fun `runtime duplicate guard identifies conflicting participants and operations`() {
        val operations = listOf(
            SyncOperation(
                stage = SyncStage.PUSH,
                order = 160,
                label = "رفع إعدادات المؤسسة المعدلة",
                failureMode = SyncFailureMode.COLLECT,
                execute = {},
                participantKey = "organization",
            ),
            SyncOperation(
                stage = SyncStage.PUSH,
                order = 160,
                label = "push التصنيفات",
                failureMode = SyncFailureMode.COLLECT,
                execute = {},
                participantKey = "inventory",
            ),
        )

        val failure = runCatching { validateSyncOperationPlan(operations) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        val message = failure?.message.orEmpty()
        assertTrue(message.contains("stage=PUSH order=160"))
        assertTrue(message.contains("organization:رفع إعدادات المؤسسة المعدلة"))
        assertTrue(message.contains("inventory:push التصنيفات"))
    }

    @Test
    fun `executor always runs push then delete then pull`() = runTest {
        val executed = mutableListOf<String>()
        val operations = listOf(
            SyncOperation(SyncOperationSlot.PULL_NOTIFICATIONS, "pull", SyncFailureMode.ABORT, { executed += "PULL" }),
            SyncOperation(SyncOperationSlot.DELETE_EXPENSES, "delete", SyncFailureMode.ABORT, { executed += "DELETE" }),
            SyncOperation(SyncOperationSlot.PUSH_EXPENSES, "push", SyncFailureMode.ABORT, { executed += "PUSH" }),
        )

        executeSyncOperations(operations)

        assertEquals(listOf("PUSH", "DELETE", "PULL"), executed)
    }
}
