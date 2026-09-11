package com.verto.app.data.sync.pull

import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.dao.CashMovementProjectionUpdate336
import com.verto.app.data.local.entity.CashMovementType
import com.verto.app.data.local.entity.CashRegisterMovementEntity
import com.verto.app.money.Money
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** Session 336 separates immutable cash intent from server-authoritative causal balance projection. */
internal suspend fun applyCashMovement336(database: AppDatabase, change: UnifiedRemoteMaterialization) {
    val remote = canonicalAuthoritativeCashMovement336(decodeCashMovement336(change))
    val dao = database.cashRegisterDao()
    val local = dao.getMovementByIdSync(change.aggregateId)
    if (local == null) {
        check(dao.insertMovement(remote) != -1L) { "cash movement authoritative insert failed" }
        return
    }
    require(sameCashMovementIntent336(local, remote)) { "IMMUTABLE_CASH_MOVEMENT_CONFLICT" }
    if (cashProjectionDiffers336(local, remote)) {
        check(dao.reconcileMovementAuthoritativeProjection(remote.toProjection336()) == 1) {
            "cash movement authoritative projection reconciliation affected unexpected rows"
        }
    }
}

internal fun sameCashMovementIntent336(local: CashRegisterMovementEntity, remote: CashRegisterMovementEntity): Boolean =
    local.id == remote.id && local.movementType == remote.movementType && local.amountMinor == remote.amountMinor &&
        local.referenceId == remote.referenceId && local.note == remote.note && local.sourceType == remote.sourceType &&
        local.sourceId == remote.sourceId && local.sourceVersion == remote.sourceVersion &&
        local.writeId == remote.writeId && local.createdAt == remote.createdAt

internal fun canonicalAuthoritativeCashMovement336(remote: CashRegisterMovementEntity): CashRegisterMovementEntity =
    remote.copy(
        amount = Money.ofMinor(remote.amountMinor).toLegacyDouble(),
        balanceBefore = Money.ofMinor(remote.balanceBeforeMinor).toLegacyDouble(),
        balanceAfter = Money.ofMinor(remote.balanceAfterMinor).toLegacyDouble(),
    )

internal fun cashProjectionDiffers336(local: CashRegisterMovementEntity, canonicalRemote: CashRegisterMovementEntity): Boolean =
    local.amount != canonicalRemote.amount || local.balanceBefore != canonicalRemote.balanceBefore ||
        local.balanceBeforeMinor != canonicalRemote.balanceBeforeMinor || local.balanceAfter != canonicalRemote.balanceAfter ||
        local.balanceAfterMinor != canonicalRemote.balanceAfterMinor

private fun decodeCashMovement336(change: UnifiedRemoteMaterialization): CashRegisterMovementEntity {
    val payload = (change.payload["materialization"] as? JsonObject) ?: change.payload
    val movement = runCatching { CashMovementType.valueOf(payload.requiredString336("movementType")) }
        .getOrElse { throw UnifiedSyncPullFailure("VALIDATION", "invalid cash movementType") }
    return CashRegisterMovementEntity(
        id = change.aggregateId, movementType = movement, amount = 0.0, amountMinor = payload.requiredLong336("amountMinor"),
        balanceBefore = 0.0, balanceBeforeMinor = payload.requiredLong336("balanceBeforeMinor"),
        balanceAfter = 0.0, balanceAfterMinor = payload.requiredLong336("balanceAfterMinor"),
        referenceId = payload.string336("referenceId"), note = payload.string336("note"),
        sourceType = payload.string336("sourceType"), sourceId = payload.string336("sourceId"),
        sourceVersion = payload.int336("sourceVersion") ?: 1, writeId = payload.requiredString336("writeId"),
        createdAt = payload.requiredLong336("createdAt"),
    )
}

private fun CashRegisterMovementEntity.toProjection336() = CashMovementProjectionUpdate336(
    id = id, amount = amount, balanceBefore = balanceBefore, balanceBeforeMinor = balanceBeforeMinor,
    balanceAfter = balanceAfter, balanceAfterMinor = balanceAfterMinor,
)

private fun JsonObject.primitive336(name: String) = this[name] as? JsonPrimitive
private fun JsonObject.string336(name: String) = primitive336(name)?.content ?: ""
private fun JsonObject.int336(name: String) = primitive336(name)?.intOrNull
private fun JsonObject.requiredLong336(name: String) = primitive336(name)?.longOrNull
    ?: throw UnifiedSyncPullFailure("VALIDATION", "missing field $name")
private fun JsonObject.requiredString336(name: String) = string336(name).takeIf { it.isNotBlank() }
    ?: throw UnifiedSyncPullFailure("VALIDATION", "missing field $name")
