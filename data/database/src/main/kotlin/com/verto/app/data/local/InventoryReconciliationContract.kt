package com.verto.app.data.local

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

const val INVENTORY_RECONCILIATION_CONTRACT_VERSION: Int = 2
const val INVENTORY_RECONCILIATION_CONTROL_KEY: String = "inventory-v2"
const val INVENTORY_RECONCILIATION_BATCH_SIZE: Int = 200

private val allowedAuthorityKinds = setOf("CENTRAL", "OWNER_DEVICE")

data class AuthoritativeInventoryReconciliationMarker(
    val organizationId: String,
    val itemId: String,
    val contractVersion: Int,
    val authorityKind: String,
    val sourceDeviceId: String?,
    val canonicalSnapshot: Long,
    val authoritativeLegacyBalance: Long,
    val reconciliationDelta: Long,
    val reconciliationMovementId: String?,
    val idempotencyKey: String,
    val serverSequence: Long?,
    val markerChecksum: String,
    val approvedAt: Long,
    val serverAcceptedAt: Long,
    val approvedBy: String,
) {
    fun deterministicIdempotencyKey(): String =
        "inventory-reconcile:$contractVersion:$organizationId:$itemId"

    fun checksumPayload(): String = listOf(
        contractVersion.toString(), organizationId, itemId,
        canonicalSnapshot.toString(), authoritativeLegacyBalance.toString(), reconciliationDelta.toString(),
        reconciliationMovementId.orEmpty(), idempotencyKey, serverSequence?.toString().orEmpty(),
        authorityKind, sourceDeviceId.orEmpty(),
    ).joinToString("|")

    fun calculatedChecksum(): String = checksumPayload().sha256Hex()

    fun requireValid(): AuthoritativeInventoryReconciliationMarker = apply {
        require(contractVersion == INVENTORY_RECONCILIATION_CONTRACT_VERSION) { "unsupported reconciliation contract" }
        require(organizationId.isNotBlank() && itemId.isNotBlank()) { "reconciliation identity is required" }
        require(authorityKind in allowedAuthorityKinds) { "authority must be centrally locked" }
        require(authorityKind != "OWNER_DEVICE" || !sourceDeviceId.isNullOrBlank()) {
            "owner-device authority requires the locked source device"
        }
        require(canonicalSnapshot in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "canonical snapshot exceeds local quantity range" }
        require(authoritativeLegacyBalance in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "legacy balance exceeds local audit range" }
        require(Math.subtractExact(canonicalSnapshot, authoritativeLegacyBalance) == reconciliationDelta) {
            "reconciliation delta does not match canonical snapshot"
        }
        require(idempotencyKey == deterministicIdempotencyKey()) { "non-deterministic reconciliation idempotency key" }
        require(approvedAt >= 0L && serverAcceptedAt >= 0L && approvedBy.isNotBlank()) { "server approval metadata is required" }
        if (reconciliationDelta == 0L) {
            require(reconciliationMovementId.isNullOrBlank() && serverSequence == null) {
                "zero-delta reconciliation must not invent a movement"
            }
        } else {
            require(!reconciliationMovementId.isNullOrBlank() && serverSequence != null && serverSequence >= 0L) {
                "non-zero reconciliation requires the server-approved movement identity"
            }
            require(kotlin.math.abs(reconciliationDelta) <= Int.MAX_VALUE.toLong()) {
                "reconciliation delta exceeds legacy audit quantity range"
            }
        }
        require(markerChecksum.equals(calculatedChecksum(), ignoreCase = true)) { "reconciliation checksum mismatch" }
    }
}

fun String.sha256Hex(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
