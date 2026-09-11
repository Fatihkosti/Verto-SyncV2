package com.verto.app.utils

import androidx.room.withTransaction
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.dao.CashRegisterDao
import com.verto.app.data.local.entity.CashMovementType
import com.verto.app.data.local.entity.CashRegisterEntity
import com.verto.app.data.local.entity.CashRegisterMovementEntity
import com.verto.app.data.sync.UnifiedOutboxWriter
import com.verto.app.money.Money
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

internal data class CashMovementIntent(
    val type: CashMovementType,
    val amountMinor: Long,
    val referenceId: String = "",
    val note: String = "",
    val source: CashMovementSource = CashMovementSource(),
    val identity: CashMovementIdentity = CashMovementIdentity(),
)
internal data class CashMovementSource(val type: String = "", val id: String = "", val version: Int = 1)
internal data class CashMovementIdentity(
    val writeId: String = "",
    val operation: String = "COMMAND",
    val dependsOnMutationId: String? = null,
    val commandBatchId: String? = null,
    val commandOrder: Int? = null,
)

internal data class CashMovementWriteResult(
    val movementId: String,
    val mutationId: String,
    val signedAmountMinor: Long,
)

/** Atomically persists the local cash fact and immutable V2 command; never pushes a balance snapshot. */
@Singleton
class CashMovementSyncWriter @Inject constructor(
    private val database: AppDatabase,
    private val cashDao: CashRegisterDao,
    private val outbox: UnifiedOutboxWriter,
    private val sessionReader: SessionReader,
) {
    internal suspend fun record(intent: CashMovementIntent) {
        recordWithResult(intent)
    }

    internal suspend fun recordWithResult(intent: CashMovementIntent): CashMovementWriteResult {
        require(intent.amountMinor > 0L) { "Cash movement amount must be positive" }
        require(intent.identity.operation in setOf("COMMAND", "REVERSE")) { "unsupported cash operation" }
        val organizationId = sessionReader.snapshot().organization.id.trim()
        require(organizationId.isNotBlank()) { "FAIL_ORG_SCOPE" }
        val writeId = intent.identity.writeId.trim().ifBlank { UUID.randomUUID().toString() }
        val movementId = stableMovementId(organizationId, writeId)
        return database.withTransaction {
            val signedMinor = signedDelta(intent.type, intent.amountMinor)
            val existing = cashDao.getMovementByIdSync(movementId)
            if (existing != null) {
                require(matches(existing, intent, signedMinor, writeId)) { "FAIL_IDEMPOTENCY_CONFLICT" }
            } else {
                val current = cashDao.getRegisterSync()
                    ?: CashRegisterEntity(balance = 0.0, balanceMinor = 0L).also { cashDao.upsertRegister(it) }
                val afterMinor = Math.addExact(current.balanceMinor, signedMinor)
                val row = CashRegisterMovementEntity(
                    id = movementId, movementType = intent.type,
                    amount = Money.ofMinor(signedMinor).toLegacyDouble(), amountMinor = signedMinor,
                    balanceBefore = Money.ofMinor(current.balanceMinor).toLegacyDouble(), balanceBeforeMinor = current.balanceMinor,
                    balanceAfter = Money.ofMinor(afterMinor).toLegacyDouble(), balanceAfterMinor = afterMinor,
                    referenceId = intent.referenceId, note = intent.note, sourceType = intent.source.type,
                    sourceId = intent.source.id.ifBlank { intent.referenceId }, sourceVersion = intent.source.version,
                    writeId = writeId,
                )
                check(cashDao.updateBalance(row.balanceAfter, afterMinor) == 1) { "cash register update must affect one row" }
                check(cashDao.insertMovement(row) != -1L) { "cash movement insert was ignored unexpectedly" }
            }
            val row = requireNotNull(cashDao.getMovementByIdSync(movementId))
            val cashMutationId = "cash:${row.id}"
            outbox.enqueue(
                organizationId = organizationId, aggregateType = "CASH_MOVEMENT", aggregateId = row.id,
                operationType = "COMMAND", payload = payload(row), mutationId = cashMutationId,
                commandBatchId = intent.identity.commandBatchId, commandOrder = intent.identity.commandOrder,
                dependsOnMutationId = intent.identity.dependsOnMutationId, createdAt = row.createdAt,
            )
            return@withTransaction CashMovementWriteResult(row.id, cashMutationId, signedMinor)
        }
    }

    private fun stableMovementId(org: String, writeId: String): String =
        UUID.nameUUIDFromBytes("verto-cash-v335\u0000$org\u0000$writeId".toByteArray(StandardCharsets.UTF_8)).toString()

    private fun signedDelta(type: CashMovementType, amountMinor: Long): Long = when (type) {
        CashMovementType.SALE_CASH, CashMovementType.PAYMENT_RECEIVED, CashMovementType.MANUAL_ADD -> amountMinor
        else -> Math.negateExact(amountMinor)
    }

    private fun matches(row: CashRegisterMovementEntity, intent: CashMovementIntent, signed: Long, writeId: String): Boolean =
        row.movementType == intent.type && row.amountMinor == signed && row.referenceId == intent.referenceId &&
            row.note == intent.note && row.sourceType == intent.source.type &&
            row.sourceId == intent.source.id.ifBlank { intent.referenceId } && row.sourceVersion == intent.source.version && row.writeId == writeId

    private fun payload(row: CashRegisterMovementEntity): Map<String, Any?> = mapOf("materialization" to mapOf(
        "id" to row.id,
        "movementType" to row.movementType.name, "amountMinor" to row.amountMinor,
        "balanceBeforeMinor" to row.balanceBeforeMinor, "balanceAfterMinor" to row.balanceAfterMinor,
        "referenceId" to row.referenceId, "note" to row.note, "sourceType" to row.sourceType,
        "sourceId" to row.sourceId, "sourceVersion" to row.sourceVersion,
        "writeId" to row.writeId, "createdAt" to row.createdAt,
    ))
}
