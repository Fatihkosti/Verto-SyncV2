package com.verto.app.data.sync.expense

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** B12 semantic snapshot. Minor units are authoritative; the expense identity never changes. */
data class ExpenseRevisionSnapshotB12(
    val id: String,
    val category: String,
    val item: String,
    val amountMinor: Long,
    val note: String,
    val date: Long,
    val lifecycleState: String,
    val voidedAt: Long?,
    val voidReason: String?,
    val reversalWriteId: String?,
)

fun expenseEffectiveMinorB12(snapshot: ExpenseRevisionSnapshotB12?): Long = when {
    snapshot == null -> 0L
    snapshot.lifecycleState == "ACTIVE" -> snapshot.amountMinor
    snapshot.lifecycleState == "VOID" -> 0L
    else -> error("BLOCKED_EXPENSE_DOMAIN_DRIFT: unsupported lifecycle=${snapshot.lifecycleState}")
}

/** Cash fact sign: positive adds cash, negative removes cash. */
fun expenseCashDeltaMinorB12(
    before: ExpenseRevisionSnapshotB12?,
    after: ExpenseRevisionSnapshotB12,
): Long = Math.negateExact(Math.subtractExact(expenseEffectiveMinorB12(after), expenseEffectiveMinorB12(before)))

/**
 * Cross-platform B12 fingerprint contract.
 * Each nullable UTF-8 field is length-prefixed, avoiding JSON whitespace/key-order ambiguity.
 */
fun expenseContentHashB12(snapshot: ExpenseRevisionSnapshotB12): String {
    fun token(value: String?): String = if (value == null) "-1:" else {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        "${bytes.size}:$value"
    }
    val material = listOf(
        "B12",
        token(snapshot.id),
        token(snapshot.category),
        token(snapshot.item),
        token(snapshot.amountMinor.toString()),
        token(snapshot.note),
        token(snapshot.date.toString()),
        token(snapshot.lifecycleState),
        token(snapshot.voidedAt?.toString()),
        token(snapshot.voidReason),
        token(snapshot.reversalWriteId),
    ).joinToString("|")
    return MessageDigest.getInstance("SHA-256")
        .digest(material.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

fun expenseSnapshotMapB12(snapshot: ExpenseRevisionSnapshotB12): Map<String, Any?> = linkedMapOf(
    "id" to snapshot.id,
    "category" to snapshot.category,
    "item" to snapshot.item,
    "amountMinor" to snapshot.amountMinor,
    "note" to snapshot.note,
    "date" to snapshot.date,
    "lifecycleState" to snapshot.lifecycleState,
    "voidedAt" to snapshot.voidedAt,
    "voidReason" to snapshot.voidReason,
    "reversalWriteId" to snapshot.reversalWriteId,
)
