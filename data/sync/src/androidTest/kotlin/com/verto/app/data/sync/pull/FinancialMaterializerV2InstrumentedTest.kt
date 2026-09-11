package com.verto.app.data.sync.pull

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.*
import com.verto.app.data.sync.*
import com.verto.app.data.sync.ownership.SyncPendingProtection
import com.verto.app.money.Money
import com.verto.app.utils.SearchTextNormalizer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real Room tests against AppDatabase; no MockContext, mocked DAO or imitation materializer. */
@RunWith(AndroidJUnit4::class)
class FinancialMaterializerV2InstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var materializer: FinancialMaterializerV2
    private lateinit var protection: SyncPendingProtection
    private lateinit var applier: UnifiedSyncChangeApplier
    private val scope = SyncScope("org-1", "principal-1", "scope-1", scopeDefinitionVersion = 1)
    private val json = SyncContractV2Codec.json

    @Before fun setup() = runBlocking<Unit> {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        protection = SyncPendingProtection(database)
        materializer = FinancialMaterializerV2(database, protection, FinancialEffectVerifierV2(database, protection))
        applier = UnifiedSyncChangeApplier(database, UnifiedStrongerSyncChangeApplier(database, materializer))
        database.clientDao().insertClient(PartyIdentityEntity(id = "party-1", name = "Fixture party", phone = "249", isDirty = false))
        database.partyRoleDao().insertRole(PartyRoleEntity("party-1:CUSTOMER", "party-1", "org-1", "CUSTOMER", createdAt = 1, updatedAt = 1, dirty = false))
        database.partyRoleDao().insertRole(PartyRoleEntity("party-1:SUPPLIER", "party-1", "org-1", "SUPPLIER", createdAt = 1, updatedAt = 1, dirty = false))
    }

    @After fun teardown() = database.close()

    @Test fun allNineTablesEveryDtoFieldAndMinorReadBackExactly() = runBlocking {
        val s = fixture()
        materializer.materialize(context(s))
        assertEquals(s, materializer.readPersisted(s))
        assertEquals(listOf(1L, 2L, 2L, 2L, 2L, 1L, 1L, 1L, 1L), domainTables.map(::count))
        val invoice = database.invoiceDao().readRemoteInvoice(s.invoiceId)!!
        assertFalse(invoice.isDirty)
        assertEquals(SearchTextNormalizer.identifier("741"), invoice.invoiceNumberSearch)
        assertEquals(Money.ofMinor(s.header.totalAmountMinor).toLegacyDouble(), invoice.totalAmount, 0.0)
        assertEquals(1L, authority()!!.appliedServerVersion)
        assertEquals(s.businessContentHash, authority()!!.appliedContentHash)
        assertEquals(0L, queryLong("SELECT COUNT(*) FROM pragma_foreign_key_check"))
        assertNoProducedEffects()
    }

    @Test fun duplicateInvoiceAndExplicitPaymentMessageDoNotDuplicateAnyFact() = runBlocking {
        val s = fixture()
        materializer.materialize(context(s))
        materializer.materialize(context(s, revision = 2, type = "PAYMENT", paymentId = "z-payment"))
        materializer.materialize(context(s, revision = 3, type = "PAYMENT", paymentId = "a-reversal"))
        assertEquals(s, materializer.readPersisted(s))
        assertEquals(2L, count("payments"))
        assertEquals(8000L, queryLong("SELECT SUM(amount_minor) FROM payments"))
        assertNoProducedEffects()
    }

    @Test fun multipleReversalsFromOneWriteApplyAndReplayAsDistinctFacts() = runBlocking {
        val s = withSharedReversalWrite()
        materializer.materialize(context(s))
        materializer.materialize(context(s, revision = 2, type = "PAYMENT", paymentId = "second-reversal"))
        assertEquals(s, materializer.readPersisted(s))
        assertEquals(4L, count("payments"))
        assertEquals(2L, queryLong("SELECT COUNT(*) FROM payments WHERE write_id='shared-void-write'"))
        assertEquals(FinancialMaterializationContractV2.minorTotals(s),
            FinancialMaterializationContractV2.minorTotals(materializer.readPersisted(s)))
        assertEquals(1L, authority()!!.appliedServerVersion)
        assertNoProducedEffects()
    }

    @Test fun sharedWriteDoesNotPermitChangingAnExistingPaymentFact() = runBlocking {
        val s = withSharedReversalWrite()
        materializer.materialize(context(s))
        val invalid = seal(s.copy(financialStreamVersion = 2, expectedFinancialStreamVersion = 1,
            payments = s.payments.map { if (it.id == "second-reversal") it.copy(note = "conflicting immutable fact") else it }))
        expectFailure("IMMUTABLE_FACT_CONFLICT") { materializer.materialize(context(invalid, revision = 2)) }
        assertEquals(s, materializer.readPersisted(s))
        assertEquals(4L, count("payments"))
        assertEquals(1L, authority()!!.appliedServerVersion)
        assertNoProducedEffects()
    }

    private fun withSharedReversalWrite(): FinancialAggregateSnapshotV2 {
        val s = fixture()
        val original = s.payments.single { it.reversedPaymentId == null }
        val reversal = s.payments.single { it.reversedPaymentId != null }.copy(writeId = "shared-void-write")
        return seal(s.copy(payments = listOf(original, reversal,
            original.copy(id = "second-original", writeId = "original-write-2"),
            reversal.copy(id = "second-reversal", reversedPaymentId = "second-original"))))
    }

    @Test fun reversalOrderingUsesOriginalDependencyNotIdOrPaidAt() = runBlocking {
        val s = fixture()
        assertEquals("a-reversal", s.payments.first().id)
        assertTrue(s.payments.first().paidAt < s.payments.last().paidAt)
        assertEquals(listOf("z-payment", "a-reversal"), FinancialMaterializationContractV2.paymentsInDependencyOrder(s.payments).map { it.id })
        materializer.materialize(context(s))
        assertEquals("z-payment", database.invoiceDao().readRemotePayment("a-reversal")!!.reversedPaymentId)
        assertEquals(s.payments, materializer.readPersisted(s).payments)
    }

    @Test fun selectiveMutableUpdatePreservesReturnFactsAndLocalImageUri() = runBlocking {
        val first = fixture()
        materializer.materialize(context(first))
        database.openHelper.writableDatabase.execSQL("UPDATE invoices SET imageUri='content://local/image' WHERE id='invoice-1'")
        val next = seal(first.copy(financialStreamVersion = 2, expectedFinancialStreamVersion = 1,
            header = first.header.copy(description = "Changed description", invoiceNumber = 742),
            items = first.items.map { it.copy(itemName = it.itemName + " updated", sellPriceMinor = it.sellPriceMinor + 1) }))
        materializer.materialize(context(next, 2))
        assertEquals(next, materializer.readPersisted(next))
        assertEquals("content://local/image", database.invoiceDao().readRemoteInvoice("invoice-1")!!.imageUri)
        assertEquals("742", database.invoiceDao().readRemoteInvoice("invoice-1")!!.invoiceNumberSearch)
        assertEquals(1L, count("invoice_return_documents"))
        assertEquals(first.returnLines, materializer.readPersisted(next).returnLines)
    }

    @Test fun explicitUnreferencedItemAndDueTombstonesDoNotCascade() = runBlocking {
        val first = fixture(); materializer.materialize(context(first))
        val next = seal(first.copy(financialStreamVersion = 2, expectedFinancialStreamVersion = 1,
            items = first.items.filterNot { it.id == "line-2" }, dueInstallments = first.dueInstallments.filterNot { it.id == "due-1" },
            explicitTombstones = listOf(ExplicitTombstoneV2("INVOICE_ITEM", "line-2", 1, "explicit correction"),
                ExplicitTombstoneV2("INVOICE_DUE_INSTALLMENT", "due-1", 1, "approved schedule correction"))))
        materializer.materialize(context(next, 2))
        assertEquals(next, materializer.readPersisted(next))
        assertNull(database.invoiceDao().readRemoteInvoiceItem("line-2"))
        assertEquals(1L, count("invoice_return_lines")); assertEquals(2L, count("payments"))
        materializer.materialize(context(next, 3)) // the already-proved tombstone replay is a no-op
    }

    @Test fun referencedLineAndImmutableFactTombstonesCannotDestroyHistory() = runBlocking {
        val first = fixture(); materializer.materialize(context(first))
        database.withTransaction {
            assertTrue(database.invoiceDao().remoteInvoiceItemHasProtectedReference("line-1"))
            assertEquals(0, database.invoiceDao().deleteRemoteInvoiceItem("org-1", "invoice-1", "line-1"))
        }
        val bad = seal(first.copy(financialStreamVersion = 2, expectedFinancialStreamVersion = 1,
            explicitTombstones = listOf(ExplicitTombstoneV2("PAYMENT", "z-payment", 1, "not a legal deletion"))))
        expectFailure { materializer.materialize(context(bad, 2)) }
        assertEquals(first, materializer.readPersisted(first)); assertEquals(1L, authority()!!.appliedServerVersion)
    }

    @Test fun omittedFullRowsWithoutTombstoneArePreservedAndNotApplied() = runBlocking {
        val first = fixture(); materializer.materialize(context(first))
        val next = seal(first.copy(financialStreamVersion = 2, expectedFinancialStreamVersion = 1,
            items = first.items.take(1), dueInstallments = first.dueInstallments.take(1)))
        expectFailure("FINANCIAL_FULL_SNAPSHOT_OMISSION") { materializer.materialize(context(next, 2)) }
        assertEquals(first, materializer.readPersisted(first)); assertEquals(1L, authority()!!.appliedServerVersion)
    }

    @Test fun allSixImmutableTablesRejectSameIdDifferentContent() = runBlocking {
        val first = fixture(); materializer.materialize(context(first))
        val variants = listOf(
            first.copy(payments = first.payments.map { it.copy(note = it.note + " changed") }),
            first.copy(paymentAllocations = first.paymentAllocations.map { it.copy(allocatedTransactionAmountMinor = it.allocatedTransactionAmountMinor + 1) }),
            first.copy(realizedFxEvents = first.realizedFxEvents.map { it.copy(differenceMinor = 1) }),
            first.copy(returnDocuments = first.returnDocuments.map { it.copy(reason = "changed reason") }),
            first.copy(returnLines = first.returnLines.map { it.copy(historicalCostAmountMinor = it.historicalCostAmountMinor + 1) }),
            first.copy(returnPaymentAllocations = first.returnPaymentAllocations.map { it.copy(allocatedFunctionalAmountMinor = it.allocatedFunctionalAmountMinor + 1) }),
        )
        for (variant in variants) {
            val next = seal(variant.copy(financialStreamVersion = 2, expectedFinancialStreamVersion = 1))
            expectFailure("IMMUTABLE_FACT_CONFLICT") { materializer.materialize(context(next, 2)) }
            assertEquals(first, materializer.readPersisted(first)); assertEquals(1L, authority()!!.appliedServerVersion)
        }
    }

    @Test fun crossTenantPartyAndExistingChildCannotBeAdoptedBySnapshotNames() = runBlocking {
        val first = fixture()
        val missing = seal(first.copy(header = first.header.copy(clientId = "cash_client_main"),
            payments = first.payments.map { it.copy(clientId = "cash_client_main") },
            returnDocuments = first.returnDocuments.map { it.copy(clientId = "cash_client_main") }))
        expectFailure("WAITING_DEPENDENCY") { materializer.materialize(context(missing)) }
        assertNull(database.clientDao().getClientIdentityByIdSync("cash_client_main"))
        database.openHelper.writableDatabase.execSQL("UPDATE party_roles SET organization_id='other-org'")
        expectFailure("WAITING_DEPENDENCY") { materializer.materialize(context(first)) }
        assertEquals(0L, count("invoices"))
    }

    @Test fun existingProjectCashIdentityIsUsedWithoutManufacturingAnotherParty() = runBlocking {
        database.clientDao().insertClient(PartyIdentityEntity(id = "cash_client_main", name = "Existing cash identity", phone = "", isDirty = false))
        database.partyRoleDao().insertRole(PartyRoleEntity("cash:CUSTOMER", "cash_client_main", "org-1", "CUSTOMER", createdAt = 1, updatedAt = 1, dirty = false))
        val first = fixture()
        val cash = seal(first.copy(header = first.header.copy(clientId = "cash_client_main"),
            payments = first.payments.map { it.copy(clientId = "cash_client_main") },
            returnDocuments = first.returnDocuments.map { it.copy(clientId = "cash_client_main") }))
        materializer.materialize(context(cash))
        assertEquals("cash_client_main", database.invoiceDao().readRemoteInvoice("invoice-1")!!.clientId)
        assertEquals(2L, count("clients"))
    }

    @Test fun mismatchedScopeRootAndFinancialVersionAreRejectedBeforeDomainWrites() = runBlocking {
        val first = fixture()
        val c = context(first)
        val changes = listOf(c.copy(syncScopeId = ""), c.copy(organizationId = "other-org"), c.copy(aggregateId = "z-payment"), c.copy(entityVersion = 99), c.copy(payloadVersion = 1))
        for (bad in changes) expectFailure { materializer.materialize(ChangeMaterialization(bad)) }
        assertEquals(0L, count("invoices")); assertNull(authority())
    }

    @Test fun defaultsNullListsAndAllNestedRequiredFieldsCannotBeSilentlyFilled() = runBlocking {
        val first = fixture()
        val base = json.parseToJsonElement(SyncContractV2Codec.encode(first)).jsonObject
        val cases = mutableListOf<JsonObject>()
        for (key in base.keys) cases += JsonObject(base - key)
        for (key in base.getValue("header").jsonObject.keys) cases += JsonObject(base + ("header" to JsonObject(base.getValue("header").jsonObject - key)))
        for (listName in listOf("items", "dueInstallments", "payments", "paymentAllocations", "realizedFxEvents", "returnDocuments", "returnLines", "returnPaymentAllocations")) {
            val array = base.getValue(listName).jsonArray
            for (key in array.first().jsonObject.keys) cases += JsonObject(base + (listName to JsonArray(listOf(JsonObject(array.first().jsonObject - key)) + array.drop(1))))
        }
        for (bad in cases) expectFailure { materializer.materialize(rawContext(first, bad)) }
        assertEquals(0L, count("invoices")); assertEquals(0L, count("sync_entity_version"))
    }

    @Test fun partialQuotedMinorBadHashEnumAndCyclicReversalAreRejected() = runBlocking {
        val first = fixture()
        val base = json.parseToJsonElement(SyncContractV2Codec.encode(first)).jsonObject
        val quotedHeader = JsonObject(base.getValue("header").jsonObject + ("totalAmountMinor" to JsonPrimitive("10000")))
        val raws = listOf(JsonObject(base + ("snapshotKind" to JsonPrimitive("PARTIAL"))),
            JsonObject(base + ("header" to quotedHeader)), JsonObject(base + ("businessContentHash" to JsonPrimitive("f".repeat(64)))))
        for (bad in raws) expectFailure { materializer.materialize(rawContext(first, bad)) }
        expectFailure { materializer.materialize(context(seal(first.copy(header = first.header.copy(category = "UNKNOWN"))))) }
        val cyclic = seal(first.copy(payments = first.payments.map { if (it.id == "z-payment") it.copy(reversedPaymentId = "a-reversal") else it }))
        expectFailure { materializer.materialize(context(cyclic)) }
        assertEquals(0L, count("invoices"))
    }

    @Test fun pendingOrphanOnAChildAndDirtyFlagsPreserveLocalState() = runBlocking {
        val first = fixture(); materializer.materialize(context(first))
        database.unifiedSyncDao().insertPendingReferences(listOf(SyncPendingReferenceEntity("org-1", "financial_outbox", "missing-source", "INVOICE_ITEM", "line-1", 1, "a".repeat(64), "ITEM")))
        val next = seal(first.copy(financialStreamVersion = 2, expectedFinancialStreamVersion = 1, header = first.header.copy(description = "changed")))
        expectFailure("PENDING_LOCAL_MUTATION") { materializer.materialize(context(next, 2)) }
        database.unifiedSyncDao().deleteSourcePendingReferences("org-1", "financial_outbox", "missing-source")
        database.openHelper.writableDatabase.execSQL("UPDATE payments SET isDirty=1 WHERE id='z-payment'")
        expectFailure("PENDING_LOCAL_MUTATION") { materializer.materialize(context(next, 2)) }
        assertEquals(first, materializer.readPersisted(first)); assertEquals(1L, authority()!!.appliedServerVersion)
    }

    @Test fun observedVersionNeverBecomesAppliedBasisAndOlderPaymentCannotOverwrite() = runBlocking {
        val first = fixture(); materializer.materialize(context(first))
        database.unifiedSyncDao().recordObservedVersion("org-1", "scope-1", "FINANCIAL_INVOICE", "invoice-1", 8, 2)
        val next = seal(first.copy(financialStreamVersion = 2, expectedFinancialStreamVersion = 1, header = first.header.copy(description = "newer root")))
        materializer.materialize(context(next, 2))
        assertEquals(2L, authority()!!.appliedServerVersion); assertEquals(8L, authority()!!.observedServerVersion)
        expectFailure("FINANCIAL_VERSION_CONFLICT") { materializer.materialize(context(first, 3, "PAYMENT", "z-payment")) }
        val gap = seal(next.copy(financialStreamVersion = 9, expectedFinancialStreamVersion = 8))
        expectFailure("WAITING_DEPENDENCY") { materializer.materialize(context(gap, 9)) }
        val sameVersionDifferent = seal(next.copy(header = next.header.copy(description = "same version, different content")))
        expectFailure("FINANCIAL_VERSION_CONFLICT") { materializer.materialize(context(sameVersionDifferent, 10)) }
        assertEquals(next, materializer.readPersisted(next))
    }

    @Test fun unversionedDifferentLocalProjectionWaitsForRepair() = runBlocking {
        val first = fixture()
        database.invoiceDao().insertRemoteInvoice(first.header.copy(description = "unproven local edit").toRemoteEntityV2())
        expectFailure("FINANCIAL_AUTHORITY_MISSING") { materializer.materialize(context(first)) }
        assertEquals("unproven local edit", database.invoiceDao().readRemoteInvoice("invoice-1")!!.description)
        assertNull(authority()); assertEquals(0L, count("payments"))
    }

    @Test fun dueSequenceSwapsAreAtomicAndNeverLeaveTemporarySlots() = runBlocking {
        val first = fixture(); materializer.materialize(context(first))
        val next = seal(first.copy(financialStreamVersion = 2, expectedFinancialStreamVersion = 1,
            dueInstallments = first.dueInstallments.map { it.copy(sequence = if (it.sequence == 1) 2 else 1) }))
        materializer.materialize(context(next, 2))
        assertEquals(next.dueInstallments, database.invoiceDao().getDueInstallments("invoice-1").map { it.toDtoV2() })
        assertEquals(0L, queryLong("SELECT COUNT(*) FROM invoice_due_installments WHERE sequence<0"))
    }

    @Test fun longBeyondDoubleIntegerPrecisionSurvivesAndOverflowFailsClosed() = runBlocking {
        val first = fixture(); val exact = 9_007_199_254_740_993L
        val large = seal(first.copy(header = first.header.copy(totalAmountMinor = exact),
            payments = first.payments.map { if (it.id == "z-payment") it.copy(amountMinor = exact) else it }))
        materializer.materialize(context(large))
        assertEquals(exact, database.invoiceDao().readRemoteInvoice("invoice-1")!!.totalAmountMinor)
        assertEquals(exact, database.invoiceDao().readRemotePayment("z-payment")!!.amountMinor)
        assertEquals(large, materializer.readPersisted(large))
        val overflow = seal(large.copy(financialStreamVersion = 2, expectedFinancialStreamVersion = 1,
            items = large.items.map { it.copy(totalPriceMinor = Long.MAX_VALUE) }))
        expectFailure { materializer.materialize(context(overflow, 2)) }
        assertEquals(large, materializer.readPersisted(large))
    }

    @Test fun unknownLegacyCurrenciesStayExplicitlyUnknownAndRecordedRatesStayExact() = runBlocking {
        val first = fixture()
        val unknown = seal(first.copy(header = first.header.copy(transactionCurrencyCode = "", functionalCurrencyCode = "", invoiceExchangeRateSnapshot = "", legacyCurrencyStatus = "UNKNOWN"),
            payments = first.payments.map { it.copy(paymentCurrencyCode = "", paymentExchangeRate = "", legacyCurrencyStatus = "UNKNOWN") },
            dueInstallments = first.dueInstallments.map { it.copy(currencyCode = "") },
            realizedFxEvents = first.realizedFxEvents.map { it.copy(functionalCurrencyCode = "") },
            returnDocuments = first.returnDocuments.map { it.copy(transactionCurrencyCode = "", functionalCurrencyCode = "") }))
        materializer.materialize(context(unknown))
        assertEquals(unknown, materializer.readPersisted(unknown))
        assertEquals("", database.invoiceDao().readRemoteInvoice("invoice-1")!!.transactionCurrencyCode)
    }

    @Test fun unscopedInventoryIsNotAdoptedButScopedPredecessorProvidesReferenceProof() = runBlocking {
        database.inventoryDao().insertItem(InventoryItemEntity(id = "inventory-1", partNumber = "P1", name = "Part"))
        val first = fixture(); val s = withInventory(first)
        expectFailure("WAITING_DEPENDENCY") { materializer.materialize(context(s)) }
        database.withTransaction {
            val batch = applier.beginFinancialBatch("org-1", "scope-1")
            applier.apply(inventoryChange(), batch)
            applier.apply(context(s), batch)
            assertNull(authority())
            applier.completeFinancialBatch(batch)
        }
        assertEquals(s, materializer.readPersisted(s))
        // The previously applied financial binding is now durable tenant proof; no fake global item org.
        materializer.materialize(context(s, 3))
        assertEquals(0L, count("inventory_movements"))
    }

    @Test fun absentEffectRollsBackAndExistingSameTransactionFactIsNotReposted() = runBlocking {
        seedInventoryAuthority()
        val s = withMovement(withInventory(fixture()))
        expectFailure("BATCH_DEPENDENCY_MISSING") { materializer.materialize(context(s)) }
        assertEquals(0L, count("invoices")); assertNull(authority())
        database.withTransaction {
            val batch = materializer.beginBatch("org-1", "scope-1")
            materializer.apply(ChangeMaterialization(context(s)), batch)
            assertNull(authority())
            database.inventoryDao().insertMovement(movement()) // fixture for an actual owner fact, not a financial effect producer
            materializer.completeBatch(batch)
        }
        materializer.materialize(context(s, 2))
        assertEquals(1L, count("inventory_movements"))
        assertEquals(-2L, queryLong("SELECT SUM(signed_base_quantity) FROM inventory_movements"))
        assertEquals(0L, count("cash_register_movements"))
    }

    @Test fun wrongEffectHashRollsBackFinancialWritesWithoutDeletingExistingFact() = runBlocking {
        seedInventoryAuthority(); database.inventoryDao().insertMovement(movement())
        val good = withMovement(withInventory(fixture()))
        val bad = good.copy(effectReferences = good.effectReferences.map { if (it.factType == "INVENTORY_MOVEMENT") it.copy(contentHash = "0".repeat(64)) else it })
        expectFailure("IMMUTABLE_FACT_CONFLICT") { materializer.materialize(context(bad)) }
        assertEquals(0L, count("invoices")); assertEquals(1L, count("inventory_movements")); assertNull(authority())
    }

    @Test fun otherEffectOwnersWithoutProvenAppliedFactsCannotBecomeApplied() = runBlocking {
        val first = fixture()
        for ((type, owner) in mapOf("CASH_MOVEMENT" to "sync_outbox", "CLIENT_CREDIT" to "sync_outbox", "COMMISSION_PAYMENT" to "sync_outbox", "INVENTORY_COST_REVISION" to "inventory_cost_outbox")) {
            val s = seal(first.copy(effectReferences = first.effectReferences + EffectReferenceV2(owner, type, "missing-fact", "missing-fact", "a".repeat(64))))
            expectFailure("BATCH_DEPENDENCY_MISSING") { materializer.materialize(context(s)) }
            assertEquals(0L, count("invoices")); assertNull(authority())
        }
    }

    @Test fun purchaseLocalAndInternationalUseTheSameCompleteFinancialMaterializer() = runBlocking {
        val first = fixture()
        val purchase = seal(first.copy(header = first.header.copy(category = "PURCHASE", isOwedToMe = false, purchaseScope = "LOCAL", supplierInvoiceReference = " Ab-١٢ ", supplierInvoiceReferenceNormalized = "ab١٢"),
            items = first.items.map { it.copy(isOwedToMe = false) },
            returnDocuments = first.returnDocuments.map { it.copy(documentType = "PURCHASE_RETURN_DEBIT_NOTE") }))
        materializer.materialize(context(purchase))
        val international = seal(purchase.copy(financialStreamVersion = 2, expectedFinancialStreamVersion = 1,
            header = purchase.header.copy(purchaseScope = "INTERNATIONAL", invoiceExchangeRateSnapshot = "1.000000000000000001")))
        materializer.materialize(context(international, 2))
        assertEquals(international, materializer.readPersisted(international))
        assertEquals("1.000000000000000001", database.invoiceDao().readRemoteInvoice("invoice-1")!!.invoiceExchangeRateSnapshot)
    }

    @Test fun realPullLateSqlFailurePreservesReceiptButNotBusinessAuthorityOrCheckpoint() = runBlocking {
        seedCursor()
        database.openHelper.writableDatabase.execSQL("""CREATE TRIGGER b09_injected_failure BEFORE INSERT ON invoice_return_payment_allocations
            BEGIN SELECT RAISE(ABORT,'B09 injected late failure'); END""")
        val engine = engine(listOf(context(fixture(), revision = 41)))
        expectFailure { engine.pull("org-1") }
        assertEquals(0L, count("invoices")); assertEquals(0L, count("payments")); assertEquals(1L, count("sync_inbox"))
        assertNull(authority()?.appliedServerVersion)
        assertEquals("cursor-1", database.unifiedSyncDao().getCursor("scope-1")!!.receivedCursorToken)
        assertNull(database.unifiedSyncDao().getCursor("scope-1")!!.appliedCheckpoint)
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER b09_injected_failure")
        assertEquals(UnifiedSyncPullOutcome.CAUGHT_UP, engine.pull("org-1").outcome)
        assertEquals(fixture(), materializer.readPersisted(fixture()))
        val cursor = database.unifiedSyncDao().getCursor("scope-1")!!
        assertEquals("cursor-1", cursor.cursorToken); assertEquals("cursor-1", cursor.receivedCursorToken)
        assertEquals(41L, cursor.appliedCheckpoint)
        assertEquals("APPLIED", database.unifiedSyncDao().getInbox("scope-1", 41)!!.applyState)
        assertEquals(0L, count("financial_inbox")) // not the old placeholder receive path
    }

    @Test fun realPullGroupPaymentAndInvoiceShareOneAuthorityAndOneSetOfFacts() = runBlocking {
        seedCursor(); val s = fixture()
        val first = context(s, 41).copy(transactionId = "same-group", transactionSize = 2, transactionOrder = 0)
        val second = context(s, 42, "PAYMENT", "z-payment").copy(transactionId = "same-group", transactionSize = 2, transactionOrder = 1)
        engine(listOf(first, second)).pull("org-1")
        assertEquals(2L, count("payments")); assertEquals(2L, count("sync_inbox"))
        assertEquals(1L, authority()!!.appliedServerVersion); assertEquals(42L, authority()!!.lastAppliedRevision)
        assertEquals(42L, database.unifiedSyncDao().getCursor("scope-1")!!.appliedCheckpoint)
        assertNoProducedEffects()
    }

    @Test fun bootstrapUsesSameMaterializerWithoutFakeInboxAndRequiresActualBaseline() = runBlocking {
        val s = fixture()
        val row = SyncSnapshotMaterialization("scope-1", "org-1", "INVOICE", "invoice-1", 1, 2,
            json.parseToJsonElement(SyncContractV2Codec.encode(s)).jsonObject, 100)
        materializer.materialize(row)
        assertEquals(s, materializer.readPersisted(s)); assertEquals(0L, count("sync_inbox")); assertEquals(0L, count("sync_cursor"))
        assertEquals(100L, authority()!!.lastAppliedRevision)
        expectFailure { materializer.materialize(row.copy(revision = 0)) }
    }

    @Test fun eachAtomicBoundaryRollsBackWithoutDiscardingCommittedReceipts() = runBlocking {
        val cuts = domainTables.map { "BEFORE INSERT ON $it" } + listOf(
            "BEFORE INSERT ON sync_entity_version", "BEFORE INSERT ON sync_inbox",
            "BEFORE UPDATE ON sync_inbox WHEN NEW.apply_state='APPLIED'",
            "BEFORE UPDATE ON sync_cursor WHEN NEW.applied_checkpoint IS NOT OLD.applied_checkpoint",
            "BEFORE UPDATE ON sync_cursor WHEN NEW.received_cursor_token IS NOT OLD.received_cursor_token",
        )
        for ((index, cut) in cuts.withIndex()) {
            if (index > 0) { database.close(); setup() }
            seedCursor()
            database.openHelper.writableDatabase.execSQL("CREATE TRIGGER b09_cut $cut BEGIN SELECT RAISE(ABORT,'B09 cut'); END")
            expectFailure { engine(listOf(context(fixture(), 41))).pull("org-1") }
            for (table in domainTables) assertEquals("$cut $table", 0L, count(table))
            val failedDuringReceive = cut.contains("INSERT ON sync_inbox") || cut.contains("INSERT ON sync_entity_version") || cut.contains("received_cursor_token")
            assertEquals(if (failedDuringReceive) 0L else 1L, count("sync_inbox"))
            assertEquals(if (failedDuringReceive) "cursor-0" else "cursor-1",
                database.unifiedSyncDao().getCursor("scope-1")!!.receivedCursorToken)
            assertNull(authority()?.appliedServerVersion)
            assertNull(database.unifiedSyncDao().getCursor("scope-1")!!.appliedCheckpoint)
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER b09_cut")
            engine(listOf(context(fixture(), 41))).pull("org-1")
            assertEquals(fixture(), materializer.readPersisted(fixture()))
        }
    }

    @Test fun childIdAlreadyOwnedByAnotherInvoiceIsNeverReparented() = runBlocking {
        val s = fixture()
        val otherHeader = s.header.copy(id = "other-invoice", invoiceNumber = 800).toRemoteEntityV2()
        database.invoiceDao().insertRemoteInvoice(otherHeader)
        database.invoiceDao().insertRemoteInvoiceItem(s.items.last().copy(invoiceId = "other-invoice").toRemoteEntityV2())
        expectFailure("PENDING_LOCAL_MUTATION") { materializer.materialize(context(s)) }
        assertEquals("other-invoice", database.invoiceDao().readRemoteInvoiceItem("line-2")!!.invoiceId)
        assertNull(database.invoiceDao().readRemoteInvoice("invoice-1"))
        assertEquals(0L, count("payments"))
    }

    @Test fun purchaseMatchingAlsoProtectsAnExplicitlyTombstonedItem() = runBlocking {
        val s = fixture(); materializer.materialize(context(s))
        seedSql("purchase_orders", mapOf("id" to "po-1", "organization_id" to "org-1", "supplier_id" to "party-1"))
        seedSql("purchase_order_lines", mapOf("id" to "po-line-1", "purchase_order_id" to "po-1"))
        seedSql("purchase_invoice_matches", mapOf("id" to "match-1", "organization_id" to "org-1", "invoice_id" to "invoice-1", "purchase_order_id" to "po-1"))
        seedSql("purchase_invoice_match_lines", mapOf("id" to "match-line-1", "match_id" to "match-1", "invoice_item_id" to "line-2", "purchase_order_line_id" to "po-line-1"))
        val next = seal(s.copy(financialStreamVersion = 2, expectedFinancialStreamVersion = 1,
            items = s.items.take(1), explicitTombstones = listOf(ExplicitTombstoneV2("INVOICE_ITEM", "line-2", 1, "explicit correction"))))
        expectFailure("IMMUTABLE_FACT_CONFLICT") { materializer.materialize(context(next, 2)) }
        assertEquals(s, materializer.readPersisted(s)); assertEquals(1L, count("purchase_invoice_match_lines"))
    }

    @Test fun preexistingCashCreditCommissionAndCostFactsAreVerifiedNeverReproduced() = runBlocking {
        seedInventoryAuthority()
        seedSql("inventory_cost_revisions", mapOf("cost_revision_id" to "cost-1", "organization_id" to "org-1", "item_id" to "inventory-1", "revision_kind" to "COST_CORRECTION", "idempotency_key" to "cost-key", "approved_inventory_cost_minor" to 700L, "cost_sequence" to 1L))
        seedSql("cash_register_movements", mapOf("id" to "cash-1", "movementType" to "PAYMENT_RECEIVED", "amount_minor" to 2000L))
        seedSql("client_credits", mapOf("id" to "credit-1", "clientId" to "party-1", "amount_minor" to 3000L))
        seedSql("commission_payments", mapOf("id" to "commission-1", "clientId" to "party-1", "totalAmount" to 4.0))
        val refs = listOf(
            EffectReferenceV2("inventory_cost_outbox", "INVENTORY_COST_REVISION", "cost-1", "cost-key", "1".repeat(64)),
            EffectReferenceV2("sync_outbox", "CASH_MOVEMENT", "cash-1", "cash-1", "2".repeat(64)),
            EffectReferenceV2("sync_outbox", "CLIENT_CREDIT", "credit-1", "credit-1", "3".repeat(64)),
            EffectReferenceV2("sync_outbox", "COMMISSION_PAYMENT", "commission-1", "commission-1", "4".repeat(64)),
        )
        // These hashes are test fixtures for already accepted owner authority, not a new hash algorithm.
        for (ref in refs) database.unifiedSyncDao().recordAppliedVersion("org-1", "scope-1", ref.factType, ref.factId, 1, 1, ref.contentHash, false, 1)
        val base = fixture(); val s = seal(base.copy(effectReferences = base.effectReferences + refs))
        materializer.materialize(context(s)); materializer.materialize(context(s, 2, "PAYMENT", "z-payment"))
        assertEquals(1L, count("inventory_cost_revisions")); assertEquals(1L, count("cash_register_movements"))
        assertEquals(1L, count("client_credits")); assertEquals(1L, count("commission_payments"))
        assertEquals(2000L, queryLong("SELECT SUM(amount_minor) FROM cash_register_movements"))
        assertEquals(3000L, queryLong("SELECT SUM(amount_minor) FROM client_credits"))
        assertEquals(4L, queryLong("SELECT SUM(totalAmount) FROM commission_payments"))
        assertEquals(0L, count("sync_outbox")); assertEquals(0L, count("financial_outbox"))
    }

    /** Test-only reference/owner seed using real schema defaults; never used by remote production code. */
    private fun seedSql(table: String, supplied: Map<String, Any?>) {
        val values = linkedMapOf<String, Any?>()
        database.openHelper.readableDatabase.query("PRAGMA table_info($table)").use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1)
                if (name in supplied) values[name] = supplied[name]
                else if (cursor.isNull(4)) values[name] = if (cursor.getInt(3) == 0) null else if (cursor.getString(2) == "TEXT") "" else 0L
            }
        }
        check(supplied.keys.all { name -> name in values }) { "test seed column typo: $table" }
        val columns = values.keys.joinToString(",") { "`$it`" }
        val marks = values.keys.joinToString(",") { "?" }
        database.openHelper.writableDatabase.execSQL("INSERT OR ABORT INTO $table ($columns) VALUES ($marks)", values.values.toTypedArray())
    }

    private fun fixture(): FinancialAggregateSnapshotV2 = InstrumentationRegistry.getInstrumentation().context.assets
        .open("sync/b09/financial-full-v2.json").bufferedReader().use { SyncContractV2Codec.decodeFinancial(it.readText()) }

    private fun seal(value: FinancialAggregateSnapshotV2): FinancialAggregateSnapshotV2 {
        val paymentRefs = value.payments.map { EffectReferenceV2("financial_outbox", "PAYMENT", it.id,
            it.writeId.ifBlank { it.id }, FinancialMaterializationContractV2.sha256(SyncContractV2Codec.encode(it))) }
        val s = value.copy(items = value.items.sortedBy { it.id }, payments = value.payments.sortedBy { it.id },
            dueInstallments = value.dueInstallments.sortedWith(compareBy<InvoiceDueInstallmentDtoV2> { it.sequence }.thenBy { it.id }),
            explicitTombstones = value.explicitTombstones.sortedWith(compareBy<ExplicitTombstoneV2> { it.entityType }.thenBy { it.id }),
            effectReferences = (value.effectReferences.filterNot { it.factType == "PAYMENT" } + paymentRefs).sortedWith(compareBy<EffectReferenceV2> { it.owner }.thenBy { it.factType }.thenBy { it.factId }))
        return s.copy(businessContentHash = SyncContractV2Codec.financialBusinessHash(s))
    }

    private fun context(s: FinancialAggregateSnapshotV2, revision: Long = 1, type: String = "INVOICE", paymentId: String? = null): SyncChange {
        val payment = paymentId?.let { id -> s.payments.single { it.id == id } }
        val payload = buildJsonObject {
            put("financialSnapshot", json.parseToJsonElement(SyncContractV2Codec.encode(s)))
            put("event", buildJsonObject {
                put("eventId", "event-$revision-$type"); put("writeId", paymentId ?: "invoice-write-$revision")
                put("domainOperation", if (type == "PAYMENT") { if (payment!!.reversedPaymentId == null) "PAYMENT_RECORDED" else "PAYMENT_REVERSED" } else "INVOICE_UPDATED")
                put("schemaVersion", 2); put("domainPayloadVersion", 2)
                if (type == "PAYMENT") { put("paymentId", paymentId); put("reversedPaymentId", payment!!.reversedPaymentId?.let(::JsonPrimitive) ?: JsonNull) }
            })
        }
        return SyncChange(revision, "org-1", "scope-1", type, "invoice-1", SyncMutationOperation.UPSERT,
            s.financialStreamVersion, 2, payload, null, "transaction-$revision", 0, 1, null, 123)
    }

    private fun rawContext(s: FinancialAggregateSnapshotV2, raw: JsonObject): UnifiedRemoteMaterialization {
        val c = context(s)
        return ChangeMaterialization(c.copy(payload = JsonObject(c.payload + ("financialSnapshot" to raw))))
    }

    private suspend fun FinancialMaterializerV2.materialize(change: SyncChange) = materialize(ChangeMaterialization(change))
    private suspend fun authority() = database.unifiedSyncDao().readEntityVersion("org-1", "scope-1", "FINANCIAL_INVOICE", "invoice-1")
    private fun count(table: String) = queryLong("SELECT COUNT(*) FROM $table")
    private fun queryLong(sql: String): Long = database.openHelper.readableDatabase.query(sql).use { it.moveToFirst(); it.getLong(0) }
    private fun assertNoProducedEffects() { for (table in listOf("cash_register_movements", "inventory_movements", "commission_payments", "financial_outbox", "sync_outbox", "sync_mutation_packet", "inventory_stock_outbox", "inventory_cost_outbox")) assertEquals(table, 0L, count(table)) }
    private suspend fun expectFailure(code: String? = null, block: suspend () -> Unit) {
        try { block() } catch (failure: Exception) {
            if (code != null) assertTrue("${failure.message} did not contain $code", failure.message.orEmpty().contains(code))
            return
        }
        fail("expected transaction to fail without committing")
    }
    private fun withInventory(s: FinancialAggregateSnapshotV2) = seal(s.copy(items = s.items.map { if (it.id == "line-1") it.copy(inventoryItemId = "inventory-1") else it }, returnLines = s.returnLines.map { it.copy(inventoryItemId = "inventory-1") }))
    private fun movement() = InventoryMovementEntity(id = "movement-1", itemId = "inventory-1", invoiceId = "invoice-1", clientId = "party-1",
        movementType = MovementType.OUT, quantity = 2, quantityBefore = 10, quantityAfter = 8, unitPrice = 20.0, unitPriceMinor = 2000,
        organizationId = "org-1", signedBaseQuantity = -2, idempotencyKey = "movement-key", sourceType = "INVOICE", sourceId = "invoice-1",
        movementKind = InventoryMovementKind.SALE, commandId = "command-1", writeId = "movement-write", serverSequence = 1, createdAt = 1)
    private fun withMovement(s: FinancialAggregateSnapshotV2): FinancialAggregateSnapshotV2 {
        val row = movement()
        val hash = FinancialMaterializationContractV2.sha256(listOf(row.id, row.itemId, row.signedBaseQuantity, row.unitPriceMinor, row.sourceType, row.sourceId).joinToString("\u0000"))
        return seal(s.copy(effectReferences = s.effectReferences + EffectReferenceV2("inventory_stock_outbox", "INVENTORY_MOVEMENT", row.id, "movement-key", hash)))
    }
    private suspend fun seedInventoryAuthority() {
        database.inventoryDao().insertItem(InventoryItemEntity(id = "inventory-1", partNumber = "P1", name = "Part", isDirty = false))
        database.unifiedSyncDao().recordAppliedVersion("org-1", "scope-1", "INVENTORY_ITEM", "inventory-1", 1, 1, "a".repeat(64), false, 1)
    }
    private fun inventoryChange() = SyncChange(1, "org-1", "scope-1", "INVENTORY_ITEM", "inventory-1", SyncMutationOperation.UPSERT, 1,
        UnifiedSyncAggregateRegistry.requireById("INVENTORY_ITEM").payloadVersion,
        buildJsonObject { put("name", "Part"); put("partNumber", "P1") }, null, "inventory-transaction", 0, 1, null, 1)
    private suspend fun seedCursor() = database.unifiedSyncDao().insertInitialCursor(SyncCursorEntity(
        scopeId = scope.scopeId, organizationId = scope.organizationId, syncPrincipalId = scope.syncPrincipalId,
        contractFamily = scope.contractFamily, contractVersion = scope.contractVersion, scopeDefinitionVersion = 1,
        cursorToken = "cursor-0", lastAppliedChangeRevision = null, pageHighWatermark = null, minAvailableRevision = null, state = "ACTIVE", updatedAt = 1))
    private fun engine(changes: List<SyncChange>): UnifiedSyncPullEngine {
        val mapper = UnifiedSyncInboxMapper()
        val validator = DurableInboxPageValidator(mapper)
        val inbox = DurableInboxApplyCoordinator(database, mapper, validator, UnifiedSyncPullRegistry(), applier, protection,
            DurableInboxEchoReconciler(database, FrozenMutationStore(database, protection), mapper), InboxStorageProbe { Long.MAX_VALUE })
        val remote = object : UnifiedSyncPullRemote {
            override suspend fun resolveScope() = scope
            override suspend fun pull(scope: SyncScope, afterCursor: String, limit: Int): SyncPullPage {
                val members = if (afterCursor == "cursor-0") changes else emptyList()
                return SyncPullPage(members, "cursor-1", false,
                    pageHighWatermark = changes.last().revision, endsAtTransactionBoundary = true, scopeIdentity = scope,
                    fromCursor = afterCursor, coveredThroughRevision = changes.last().revision,
                    groups = members.groupBy { it.transactionId }.values.map { validator.manifest(it) })
            }
        }
        return UnifiedSyncPullEngine(database, remote, inbox)
    }

    @Test fun durablePaymentBeforeOriginalWaitsThenResumesWithoutFabricatingOriginal() = runBlocking {
        seedCursor()
        val full = fixture()
        val origin = seal(full.copy(payments = full.payments.filter { it.reversedPaymentId == null },
            paymentAllocations = full.paymentAllocations.filter { it.paymentId == "z-payment" }))
        val payment = seal(full.copy(financialStreamVersion = 2, expectedFinancialStreamVersion = 1))
        val mapper = UnifiedSyncInboxMapper(); val validator = DurableInboxPageValidator(mapper)
        val inbox = DurableInboxApplyCoordinator(database, mapper, validator, UnifiedSyncPullRegistry(), applier, protection,
            DurableInboxEchoReconciler(database, FrozenMutationStore(database, protection), mapper), InboxStorageProbe { Long.MAX_VALUE })
        val early = context(payment, 1, "PAYMENT", "a-reversal").copy(transactionId = "reversal-before-original")
        val later = context(origin, 2).copy(transactionId = "original")
        fun page(c: SyncChange, from: String, to: String, deps: List<String>) = SyncPullPage(listOf(c), to, false,
            pageHighWatermark = c.revision, endsAtTransactionBoundary = true, scopeIdentity = scope,
            fromCursor = from, coveredThroughRevision = c.revision, groups = listOf(validator.manifest(listOf(c), dependencies = deps)))
        inbox.receive(scope, "cursor-0", page(early, "cursor-0", "cursor-1", listOf("original")))
        inbox.drain(scope)
        assertEquals("WAITING_DEPENDENCY", database.unifiedSyncDao().getInboxGroup(scope.scopeId, early.transactionId)!!.state)
        assertEquals(0L, count("invoices")); assertEquals(0L, count("payments"))
        assertNull(database.unifiedSyncDao().getCursor(scope.scopeId)!!.appliedCheckpoint)
        inbox.receive(scope, "cursor-1", page(later, "cursor-1", "cursor-2", emptyList()))
        repeat(3) { inbox.drain(scope) }
        assertEquals("APPLIED", database.unifiedSyncDao().getInboxGroup(scope.scopeId, early.transactionId)!!.state)
        assertEquals(2L, authority()!!.appliedServerVersion)
        assertEquals(2L, count("payments")); assertEquals("z-payment", database.invoiceDao().readRemotePayment("a-reversal")!!.reversedPaymentId)
        assertEquals(2L, database.unifiedSyncDao().getCursor(scope.scopeId)!!.appliedCheckpoint)
        assertNoProducedEffects()
    }

    companion object {
        val domainTables = listOf("invoices", "invoice_items", "invoice_due_installments", "payments", "payment_allocations", "realized_fx_events", "invoice_return_documents", "invoice_return_lines", "invoice_return_payment_allocations")
    }
}
