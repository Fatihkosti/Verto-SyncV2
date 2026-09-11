package com.verto.app.data.sync

/** Snapshot of pending local deletions used as pull guards. */
data class SyncDeletionSnapshot(
    val clientIds: Set<String>,
    val invoiceIds: Set<String>,
    val inventoryIds: Set<String>,
    val expenseIds: Set<String>,
    val categoryIds: Set<String>,
    val commissionIds: Set<String>,
    val unitIds: Set<String>,
    val budgetIds: Set<String>,
    val reconciliationIds: Set<String>
)
