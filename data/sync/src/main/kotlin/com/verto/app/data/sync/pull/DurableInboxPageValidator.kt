package com.verto.app.data.sync.pull

import com.verto.app.data.sync.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.*

/** Structural/wire verification only. Domain failures are saved as group waits/reviews during apply. */
@Singleton
class DurableInboxPageValidator @Inject constructor(private val mapper: UnifiedSyncInboxMapper) {
    data class Group(val manifest: SyncInboxGroupManifestV2, val changes: List<SyncChange>)

    fun validate(scope: SyncScope, expectedCursor: String, page: SyncPullPage): List<Group> {
        UnifiedSyncContractRules.requireValidPullPage(page, scope)
        checkContract(page.coverage == SyncPullCoverage.GLOBAL_SCOPE && page.advancesGlobalCursor,
            "SCOPE_MISMATCH", "only an authorized global-scope page can advance this cursor")
        checkContract(page.fromCursor == expectedCursor, "CURSOR_STALE", "page does not cover the requested opaque cursor")
        checkContract(page.pageHighWatermark != null && page.coveredThroughRevision != null,
            "CONTRACT_FIELD_MISSING", "page coverage and server watermark are required")
        val covered = checkNotNull(page.coveredThroughRevision)
        checkContract(covered >= 0L && covered <= checkNotNull(page.pageHighWatermark),
            "INBOX_COVERAGE_INVALID", "coverage exceeds server watermark")
        if (page.changes.isEmpty()) {
            checkContract(page.groups.isEmpty() && !page.hasMore && page.nextCursor == expectedCursor,
                "CURSOR_STALE", "empty page cannot advance or loop")
            return emptyList()
        }
        checkContract(page.nextCursor != expectedCursor, "CURSOR_STALE", "nonempty page did not advance")
        checkContract(covered == page.changes.last().revision, "INBOX_COVERAGE_INVALID", "cursor must end at the last complete visible group")
        var previous = 0L
        val idsInOrder = mutableListOf<String>()
        for (change in page.changes) {
            checkContract(change.revision > previous, "DUPLICATE_REVISION_CONTENT", "revision order is not strictly increasing")
            previous = change.revision
            if (idsInOrder.lastOrNull() != change.transactionId) idsInOrder += change.transactionId
        }
        checkContract(idsInOrder.distinct().size == idsInOrder.size, "INCOMPLETE_TRANSACTION_GROUP", "interleaved groups")
        checkContract(page.groups.map { it.transactionId } == idsInOrder,
            "CONTRACT_GROUP_MANIFEST_MISSING", "one ordered manifest is required for each complete group")
        val members = page.changes.groupBy { it.transactionId }
        var pageBytes = 0L
        return page.groups.map { manifest ->
            val changes = checkNotNull(members[manifest.transactionId])
            validateGroup(manifest, changes)
            pageBytes = Math.addExact(pageBytes, manifest.serializedBytes)
            checkContract(pageBytes <= DurableInboxPolicy.MAX_PAGE_BYTES,
                "CONTRACT_PAGE_TOO_LARGE", "page exceeds the maximum inbox reservation")
            Group(manifest, changes)
        }
    }

    fun validateGroup(manifest: SyncInboxGroupManifestV2, changes: List<SyncChange>) {
        checkContract(changes.isNotEmpty() && manifest.transactionId.isNotBlank(), "INCOMPLETE_TRANSACTION_GROUP", "empty group")
        checkContract(manifest.memberCount == changes.size && manifest.firstRevision == changes.first().revision &&
            manifest.lastRevision == changes.last().revision, "INCOMPLETE_TRANSACTION_GROUP", "manifest count or endpoints differ")
        checkContract(changes.withIndex().all { (index, c) -> c.transactionId == manifest.transactionId &&
            c.transactionSize == changes.size && c.transactionOrder == index }, "INCOMPLETE_TRANSACTION_GROUP", "group member order differs")
        checkContract(manifest.touchedKeys.isNotEmpty() && manifest.touchedKeys.all { it.type.isNotBlank() && it.id.isNotBlank() } &&
            manifest.touchedKeys == manifest.touchedKeys.distinct().sortedWith(keyOrder),
            "CONTRACT_TOUCHED_KEYS_INVALID", "keys must be unique, nonblank and ordered")
        checkContract(manifest.dependsOnTransactionIds.all { it.isNotBlank() && it != manifest.transactionId } &&
            manifest.dependsOnTransactionIds == manifest.dependsOnTransactionIds.distinct().sorted(),
            "CONTRACT_DEPENDENCIES_INVALID", "dependencies must be unique, ordered and not self references")
        checkContract(DurableInboxPolicy.groupSizeIsLegal(manifest.serializedBytes),
            "CONTRACT_GROUP_TOO_LARGE", "canonical group exceeds 2097152 bytes")
        val body = canonicalBody(changes, manifest.touchedKeys, manifest.dependsOnTransactionIds)
        val bytes = body.toByteArray(Charsets.UTF_8).size.toLong()
        checkContract(DurableInboxPolicy.groupSizeIsLegal(bytes), "CONTRACT_GROUP_TOO_LARGE", "canonical group exceeds 2097152 bytes")
        checkContract(bytes == manifest.serializedBytes && manifest.contentSha256 == sha256Utf8(body),
            "CONTRACT_GROUP_HASH_MISMATCH", "canonical body hash/byte count differs")
        val derived = changes.flatMap { DurableInboxTouchedKeys.derive(it).keys }.toSet()
        checkContract(manifest.touchedKeys.toSet().containsAll(derived),
            "CONTRACT_TOUCHED_KEYS_INCOMPLETE", "manifest omits keys written by its DTOs")
    }

    fun canonicalBody(changes: List<SyncChange>, keys: List<SyncTouchedKey>, dependencies: List<String>): String =
        mapper.canonicalJson(SyncContractV2Codec.json.encodeToJsonElement(
            SyncInboxGroupBodyV2(changes.first().transactionId, changes, keys, dependencies)))

    /** Fixture/server-contract helper; receive never manufactures a missing server manifest. */
    fun manifest(changes: List<SyncChange>, additionalKeys: List<SyncTouchedKey> = emptyList(), dependencies: List<String> = emptyList()): SyncInboxGroupManifestV2 {
        require(changes.isNotEmpty())
        val keys = (changes.flatMap { DurableInboxTouchedKeys.derive(it).keys } + additionalKeys).distinct().sortedWith(keyOrder)
        val deps = dependencies.distinct().sorted()
        val body = canonicalBody(changes, keys, deps)
        return SyncInboxGroupManifestV2(changes.first().transactionId, changes.size, changes.first().revision,
            changes.last().revision, sha256Utf8(body), body.toByteArray(Charsets.UTF_8).size.toLong(), keys, deps)
    }

    private fun checkContract(condition: Boolean, code: String, detail: String) {
        if (!condition) throw UnifiedSyncPullFailure(code, detail)
    }

    private companion object {
        val keyOrder = compareBy<SyncTouchedKey> { it.type }.thenBy { it.id }
    }
}

/** Derive writes as well as their protective roots; a server cannot omit a child to bypass a wait. */
internal object DurableInboxTouchedKeys {
    fun derive(change: SyncChange): Map<SyncTouchedKey, SyncTouchedKey> = buildMap {
        val root = SyncTouchedKey(if (change.aggregateType == "PAYMENT") "INVOICE" else change.aggregateType, change.aggregateId)
        put(root, root)
        fun child(type: String, id: String?) { if (!id.isNullOrBlank()) put(SyncTouchedKey(type, id), root) }
        fun rows(value: JsonObject, name: String, type: String) {
            (value[name] as? JsonArray)?.forEach { row -> child(type, (row as? JsonObject)?.text("id")) }
        }
        val payload = change.payload
        val p = payload["materialization"] as? JsonObject ?: payload
        if (change.aggregateType == "INVOICE" || change.aggregateType == "PAYMENT") {
            val s = payload["financialSnapshot"] as? JsonObject
            if (s != null) {
                child("INVOICE", s.text("invoiceId"))
                mapOf("items" to "INVOICE_ITEM", "dueInstallments" to "INVOICE_DUE_INSTALLMENT", "payments" to "PAYMENT",
                    "paymentAllocations" to "PAYMENT_ALLOCATION", "realizedFxEvents" to "REALIZED_FX_EVENT",
                    "returnDocuments" to "INVOICE_RETURN", "returnLines" to "INVOICE_RETURN_LINE",
                    "returnPaymentAllocations" to "INVOICE_RETURN_PAYMENT_ALLOCATION").forEach { (field, type) -> rows(s, field, type) }
                (s["explicitTombstones"] as? JsonArray)?.forEach { row ->
                    val o = row as? JsonObject
                    o?.text("entityType")?.let { child(it, o.text("id")) }
                }
                (s["effectReferences"] as? JsonArray)?.forEach { row ->
                    val o = row as? JsonObject
                    val type = o?.text("factType"); val id = o?.text("factId")
                    if (!type.isNullOrBlank() && !id.isNullOrBlank()) {
                        val key = SyncTouchedKey(type, id)
                        put(key, if (type == "PAYMENT") root else key)
                    }
                }
            }
        }
        when (change.aggregateType) {
            "CASH_RECONCILIATION" -> rows(p, "denominations", "CASH_DENOMINATION")
            "PURCHASE_ORDER" -> rows(p, "lines", "PURCHASE_ORDER_LINE")
            "GOODS_RECEIPT" -> rows(p, "lines", "GOODS_RECEIPT_LINE")
            "PURCHASE_MATCH" -> rows(p, "lines", "PURCHASE_MATCH_LINE")
            "PRICE_LIST" -> {
                rows(p, "items", "PRICE_LIST_ITEM")
                p.text("itemIds")?.split(',')?.map(String::trim)?.filter(String::isNotBlank)?.forEach {
                    child("PRICE_LIST_ITEM", "${change.aggregateId}:$it")
                }
            }
            "CUSTOMER_PROFILE", "SUPPLIER_PROFILE" -> {
                val partyId = p.text("partyId") ?: change.aggregateId
                put(SyncTouchedKey(change.aggregateType, partyId), SyncTouchedKey(change.aggregateType, partyId))
                put(SyncTouchedKey("PARTY_IDENTITY", partyId), SyncTouchedKey("PARTY_IDENTITY", partyId))
            }
            "PARTY_ROLE" -> p.text("partyId")?.let { put(SyncTouchedKey("PARTY_IDENTITY", it), SyncTouchedKey("PARTY_IDENTITY", it)) }
        }
        // CASH_REGISTER always projects the one real register, whatever alias the envelope carries.
        if (change.aggregateType == "CASH_REGISTER") put(SyncTouchedKey("CASH_REGISTER", "main"), SyncTouchedKey("CASH_REGISTER", "main"))
    }
    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
