package com.verto.app.data.sync.pull

import com.verto.app.data.sync.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Actual kotlinx.serialization runtime tests; no fake codec on the Gradle test path. */
class DurableInboxPageValidatorTest {
    private val scope = SyncScope("org", "principal", "scope", scopeDefinitionVersion = 1)
    private val mapper = UnifiedSyncInboxMapper()
    private val validator = DurableInboxPageValidator(mapper)
    private fun change(revision: Long = 1, text: String = "صنف", tx: String = "g") = SyncChange(
        revision, "org", "scope", "CATEGORY", "category-$revision", SyncMutationOperation.UPSERT,
        1, 1, buildJsonObject { put("name", text) }, null, tx, 0, 1, null, 1)
    private fun page(changes: List<SyncChange>) = SyncPullPage(changes, "opaque-1", false,
        pageHighWatermark = changes.last().revision, endsAtTransactionBoundary = true, scopeIdentity = scope,
        fromCursor = "opaque-0", coveredThroughRevision = changes.last().revision,
        groups = changes.groupBy { it.transactionId }.values.map { validator.manifest(it) })
    private fun rejects(block: () -> Unit) {
        try { block(); fail("contract must reject") }
        catch (_: UnifiedSyncPullFailure) { }
        catch (_: SyncContractViolation) { }
    }
    @Test fun validGroupAndImmutableRoundTripKeepAllFields() {
        val c = change()
        assertEquals(1, validator.validate(scope, "opaque-0", page(listOf(c))).size)
        assertEquals(c, mapper.toChange(mapper.toEntity(c, 17)))
    }
    @Test fun manifestHashAndByteCountCoverUtf8NotCharacterCount() {
        val c = change(text = "عربية🚗")
        val m = validator.manifest(listOf(c))
        val body = validator.canonicalBody(listOf(c), m.touchedKeys, m.dependsOnTransactionIds)
        assertTrue(body.toByteArray(Charsets.UTF_8).size > body.length)
        assertEquals(body.toByteArray(Charsets.UTF_8).size.toLong(), m.serializedBytes)
        assertEquals(sha256Utf8(body), m.contentSha256)
    }
    @Test fun keyOrderDoesNotChangeCanonicalFingerprint() {
        val c = change().copy(payload = buildJsonObject { put("z", 1); put("a", "ن") })
        val reordered = c.copy(payload = buildJsonObject { put("a", "ن"); put("z", 1) })
        assertEquals(validator.manifest(listOf(c)), validator.manifest(listOf(reordered)))
        assertEquals(mapper.toEntity(c, 1).contentFingerprint, mapper.toEntity(reordered, 2).contentFingerprint)
    }
    @Test fun corruptedHashByteCountAndCountAreRejected() {
        val p = page(listOf(change())); val m = p.groups.single()
        for (invalid in listOf(m.copy(contentSha256 = "a".repeat(64)), m.copy(serializedBytes = m.serializedBytes + 1), m.copy(memberCount = 2)))
            rejects { validator.validate(scope, "opaque-0", p.copy(groups = listOf(invalid))) }
    }
    @Test fun missingManifestOrMissingDerivedTouchedKeyIsRejected() {
        val p = page(listOf(change()))
        rejects { validator.validate(scope, "opaque-0", p.copy(groups = emptyList())) }
        val c = p.changes.single(); val keys = listOf(SyncTouchedKey("CATEGORY", "other"))
        val body = validator.canonicalBody(listOf(c), keys, emptyList())
        val forged = p.groups.single().copy(touchedKeys = keys, contentSha256 = sha256Utf8(body), serializedBytes = body.toByteArray().size.toLong())
        rejects { validator.validate(scope, "opaque-0", p.copy(groups = listOf(forged))) }
    }
    @Test fun wrongTenantPrincipalFilteredPageAndWrongCursorFailClosed() {
        val p = page(listOf(change()))
        rejects { validator.validate(scope, "other", p) }
        rejects { validator.validate(scope.copy(syncPrincipalId = "other"), "opaque-0", p) }
        rejects { validator.validate(scope, "opaque-0", p.copy(changes = listOf(change().copy(organizationId = "other")))) }
        rejects { validator.validate(scope, "opaque-0", p.copy(coverage = SyncPullCoverage.FILTERED_SCOPE, advancesGlobalCursor = false)) }
    }
    @Test fun splitGroupReorderedMembersAndDuplicateRevisionFailClosed() {
        val first = change().copy(transactionSize = 2)
        val second = change(2).copy(transactionSize = 2, transactionOrder = 1)
        val p = page(listOf(first, second))
        rejects { validator.validate(scope, "opaque-0", p.copy(changes = listOf(first))) }
        rejects { validator.validate(scope, "opaque-0", p.copy(changes = listOf(second, first))) }
        rejects { validator.validate(scope, "opaque-0", p.copy(changes = listOf(first, second.copy(revision = 1)))) }
    }
    @Test fun canonicalExactlyTwoMiBPassesAndNextByteIsPermanentContractError() {
        val base = change(text = "")
        val n = (DurableInboxPolicy.MAX_GROUP_BYTES - validator.manifest(listOf(base)).serializedBytes).toInt()
        val legal = change(text = "x".repeat(n))
        assertEquals(DurableInboxPolicy.MAX_GROUP_BYTES, validator.manifest(listOf(legal)).serializedBytes)
        validator.validate(scope, "opaque-0", page(listOf(legal)))
        val oversize = change(text = "x".repeat(n + 1))
        try { validator.validate(scope, "opaque-0", page(listOf(oversize))); fail("oversize") }
        catch (e: UnifiedSyncPullFailure) { assertEquals("CONTRACT_GROUP_TOO_LARGE", e.code) }
    }
    @Test fun legal1001MemberGroupIsOneCompleteGroup() {
        val changes = (1..1001).map { change(it.toLong()).copy(transactionOrder = it - 1, transactionSize = 1001) }
        assertEquals(1001, validator.validate(scope, "opaque-0", page(changes)).single().changes.size)
    }
    @Test fun dependenciesAreHashedAndMustBeSortedUniqueNonSelf() {
        val c = change(); val m = validator.manifest(listOf(c), dependencies = listOf("origin"))
        validator.validateGroup(m, listOf(c))
        rejects { validator.validateGroup(m.copy(dependsOnTransactionIds = emptyList()), listOf(c)) }
        rejects { validator.validateGroup(m.copy(dependsOnTransactionIds = listOf("g")), listOf(c)) }
        rejects { validator.validateGroup(m.copy(dependsOnTransactionIds = listOf("origin", "origin")), listOf(c)) }
    }
    @Test fun emptyPageCannotAdvanceOrRequestAnotherEmptyPage() {
        val empty = SyncPullPage(emptyList(), "opaque-0", false, pageHighWatermark = 0,
            endsAtTransactionBoundary = true, scopeIdentity = scope, fromCursor = "opaque-0", coveredThroughRevision = 0)
        validator.validate(scope, "opaque-0", empty)
        rejects { validator.validate(scope, "opaque-0", empty.copy(nextCursor = "opaque-1")) }
        rejects { validator.validate(scope, "opaque-0", empty.copy(hasMore = true)) }
    }
    @Test fun serverWatermarkNeverPretendsToCoverUnreceivedGroups() {
        val p = page(listOf(change()))
        validator.validate(scope, "opaque-0", p.copy(pageHighWatermark = 500))
        rejects { validator.validate(scope, "opaque-0", p.copy(pageHighWatermark = 500, coveredThroughRevision = 500)) }
    }
}
