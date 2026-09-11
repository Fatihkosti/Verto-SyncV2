package com.verto.app.data.sync.pull

import com.verto.app.data.sync.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Actual codec + validator + shared production mapping. No SQLite or fake Room in these JVM tests. */
class FinancialMaterializationContractV2Test {
    private val json = SyncContractV2Codec.json
    private fun fixture() = javaClass.getResourceAsStream("/sync/b09/financial-full-v2.json")!!
        .bufferedReader().use { SyncContractV2Codec.decodeFinancial(it.readText()) }
    private fun context(snapshot: FinancialAggregateSnapshotV2, raw: JsonObject? = null) = SyncSnapshotMaterialization(
        scopeId = "scope-1", organizationId = "org-1", aggregateType = "INVOICE", aggregateId = "invoice-1",
        entityVersion = snapshot.financialStreamVersion, payloadVersion = 2,
        payload = raw ?: json.parseToJsonElement(SyncContractV2Codec.encode(snapshot)).jsonObject, revision = 41,
    )
    private fun fails(block: () -> Unit) {
        try { block() } catch (_: IllegalArgumentException) { return } catch (_: UnifiedSyncPullFailure) { return }
        fail("invalid contract accepted")
    }

    @Test fun fullGoldenIsAcceptedWithFixedBusinessHashAndIndependentLifecycleVersion() {
        val snapshot = fixture()
        assertEquals("b096cf576d76a62c9a5f54450e1d666e7f6a57d47893508e9d67453a43cd8b5f", SyncContractV2Codec.financialBusinessHash(snapshot))
        assertEquals(snapshot, FinancialMaterializationContractV2.decode(context(snapshot)))
        assertEquals(7, snapshot.header.lifecycleVersion)
        assertEquals(1L, snapshot.financialStreamVersion)
    }

    @Test fun distinctPaymentReversalsCanShareOneWriteWithoutCollapsingFactIdentity() {
        val s = withSharedReversalWrite()
        assertEquals(4, s.payments.size)
        assertEquals(2, s.effectReferences.count { it.businessIdentity == "shared-void-write" })
        assertEquals(s, FinancialMaterializationContractV2.decode(context(s)))
    }

    @Test fun sharedWriteDoesNotAllowDuplicatePaymentFactIds() {
        val s = withSharedReversalWrite()
        val invalid = hashed(s.copy(effectReferences = s.effectReferences + s.effectReferences.first()))
        fails { FinancialMaterializationContractV2.decode(context(invalid)) }
    }

    @Test fun nonPaymentIdempotencyKeysRemainUniqueAcrossDistinctFacts() {
        val s = withSharedReversalWrite()
        val refs = s.effectReferences + listOf(
            EffectReferenceV2("inventory_stock_outbox", "INVENTORY_MOVEMENT", "movement-a", "same-movement-key", "a".repeat(64)),
            EffectReferenceV2("inventory_stock_outbox", "INVENTORY_MOVEMENT", "movement-b", "same-movement-key", "b".repeat(64)),
        )
        val invalid = hashed(s.copy(effectReferences = refs.sortedWith(
            compareBy<EffectReferenceV2> { it.owner }.thenBy { it.factType }.thenBy { it.factId })))
        fails { FinancialMaterializationContractV2.decode(context(invalid)) }
    }

    private fun withSharedReversalWrite(): FinancialAggregateSnapshotV2 {
        val s = fixture()
        val original = s.payments.single { it.reversedPaymentId == null }
        val reversal = s.payments.single { it.reversedPaymentId != null }.copy(writeId = "shared-void-write")
        val payments = listOf(original, reversal,
            original.copy(id = "second-original", writeId = "original-write-2"),
            reversal.copy(id = "second-reversal", reversedPaymentId = "second-original")).sortedBy { it.id }
        val refs = payments.map { EffectReferenceV2("financial_outbox", "PAYMENT", it.id,
            it.writeId.ifBlank { it.id }, FinancialMaterializationContractV2.sha256(SyncContractV2Codec.encode(it))) }
        return hashed(s.copy(payments = payments, effectReferences = refs))
    }

    private fun hashed(s: FinancialAggregateSnapshotV2) =
        s.copy(businessContentHash = SyncContractV2Codec.financialBusinessHash(s))

    @Test fun nineSharedMappingsPreserveEveryBusinessField() {
        val s = fixture()
        assertEquals(s.header, s.header.toRemoteEntityV2().toDtoV2())
        assertEquals(s.items, s.items.map { it.toRemoteEntityV2().toDtoV2() })
        assertEquals(s.dueInstallments, s.dueInstallments.map { it.toRemoteEntityV2().toDtoV2() })
        assertEquals(s.payments, s.payments.map { it.toRemoteEntityV2().toDtoV2() })
        assertEquals(s.paymentAllocations, s.paymentAllocations.map { it.toRemoteEntityV2().toDtoV2() })
        assertEquals(s.realizedFxEvents, s.realizedFxEvents.map { it.toRemoteEntityV2().toDtoV2() })
        assertEquals(s.returnDocuments, s.returnDocuments.map { it.toRemoteEntityV2().toDtoV2() })
        assertEquals(s.returnLines, s.returnLines.map { it.toRemoteEntityV2().toDtoV2() })
        assertEquals(s.returnPaymentAllocations, s.returnPaymentAllocations.map { it.toRemoteEntityV2().toDtoV2() })
    }

    @Test fun missingEveryTopLevelFieldIncludingDefaultOrNullableIsRejected() {
        val s = fixture(); val raw = context(s).payload
        for (key in raw.keys) fails { FinancialMaterializationContractV2.decode(context(s, JsonObject(raw - key))) }
    }

    @Test fun explicitNullEmptyAndAbsenceAreDistinct() {
        val s = fixture(); val raw = context(s).payload
        val header = raw.getValue("header").jsonObject
        val missingNullable = JsonObject(raw + ("header" to JsonObject(header - "supplierInvoiceReference")))
        fails { FinancialMaterializationContractV2.decode(context(s, missingNullable)) }
        val empty = s.copy(header = s.header.copy(supplierInvoiceReference = ""))
        val sealed = empty.copy(businessContentHash = SyncContractV2Codec.financialBusinessHash(empty))
        assertEquals("", FinancialMaterializationContractV2.decode(context(sealed)).header.supplierInvoiceReference)
        assertNull(FinancialMaterializationContractV2.decode(context(s)).header.supplierInvoiceReference)
    }

    @Test fun quotedLongBooleanAndUnknownFieldsCannotPassStrictShapeValidation() {
        val s = fixture(); val raw = context(s).payload
        val header = raw.getValue("header").jsonObject
        for ((field, value) in mapOf("totalAmountMinor" to JsonPrimitive("10000"), "notificationsEnabled" to JsonPrimitive("false"))) {
            val bad = JsonObject(raw + ("header" to JsonObject(header + (field to value))))
            fails { FinancialMaterializationContractV2.decode(context(s, bad)) }
        }
        fails { FinancialMaterializationContractV2.decode(context(s, JsonObject(raw + ("invented" to JsonNull)))) }
    }

    @Test fun reversalGraphIsIndependentOfDtoIdAndClientClockOrdering() {
        val s = fixture()
        assertEquals(listOf("a-reversal", "z-payment"), s.payments.map { it.id })
        assertEquals(listOf("z-payment", "a-reversal"), FinancialMaterializationContractV2.paymentsInDependencyOrder(s.payments).map { it.id })
        fails { FinancialMaterializationContractV2.paymentsInDependencyOrder(s.payments.map { it.copy(reversedPaymentId = if (it.id == "z-payment") "a-reversal" else "z-payment") }) }
    }

    @Test fun canonicalLongMinorIsNotRoundTrippedThroughCompatibilityDouble() {
        for (value in listOf(9_007_199_254_740_993L, Long.MAX_VALUE, Long.MIN_VALUE)) {
            val row = fixture().header.copy(totalAmountMinor = value)
            assertEquals(value, row.toRemoteEntityV2().toDtoV2().totalAmountMinor)
        }
    }

    @Test fun checkedMinorSumsRejectOverflowAndKeepPaymentCurrenciesSeparate() {
        val s = fixture()
        val overflow = s.copy(items = s.items.map { it.copy(totalPriceMinor = Long.MAX_VALUE) })
        fails { FinancialMaterializationContractV2.minorTotals(overflow) }
        val mixed = s.copy(payments = s.payments.map { if (it.id == "z-payment") it.copy(paymentCurrencyCode = "USD") else it })
        val totals = FinancialMaterializationContractV2.minorTotals(mixed)
        assertEquals(10000L, totals["payments.USD.amountMinor"])
        assertEquals(-2000L, totals["payments.SDG.amountMinor"])
    }

    @Test fun serverVersionAndTenantAreNotDerivedFromSnapshotTextOrTimestamp() {
        val s = fixture(); val c = context(s)
        for (bad in listOf(c.copy(scopeId = ""), c.copy(organizationId = "other"), c.copy(aggregateId = "payment-id"),
            c.copy(entityVersion = 7), c.copy(entityVersion = null), c.copy(revision = 0))) {
            fails { FinancialMaterializationContractV2.decode(bad) }
        }
    }
}
