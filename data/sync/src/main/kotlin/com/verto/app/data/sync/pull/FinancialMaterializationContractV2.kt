@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.verto.app.data.sync.pull

import com.verto.app.data.local.entity.FUNCTIONAL_PER_TRANSACTION
import com.verto.app.data.local.entity.LegacyCurrencyStatus
import com.verto.app.data.sync.*
import com.verto.app.utils.normalizeSupplierInvoiceReference
import java.math.BigDecimal
import java.security.MessageDigest
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.json.*

/** Strict receive-side validation. Missing, null, empty, numeric strings and defaults are distinct. */
internal object FinancialMaterializationContractV2 {
    fun decode(context: UnifiedRemoteMaterialization): FinancialAggregateSnapshotV2 {
        requireContract(context.scopeId.isNotBlank() && context.organizationId.isNotBlank(), "SCOPE_MISMATCH", "scope")
        requireContract(context.aggregateType in setOf("INVOICE", "PAYMENT"), "CONTRACT_UNSUPPORTED", "financial aggregate")
        requireContract(context.payloadVersion == 2 && context.entityVersion != null && context.entityVersion!! > 0,
            "CONTRACT_FIELD_MISSING", "authoritative financial version/payloadVersion")
        requireContract(context.operationType == SyncMutationOperation.UPSERT && context.deletedAtEpochMillis == null,
            "CONTRACT_UNSUPPORTED", "financial deletion requires a lifecycle/child tombstone, not root DELETE")
        requireContract(context.revision > 0, "CONTRACT_FIELD_INVALID", "server revision/baseline")
        // Exactly two documented v2 shapes: live financialSnapshot+event; bootstrap may contain the DTO directly.
        val root = if (context.payload.containsKey("financialSnapshot")) {
            requireContract(context.payload.keys.all { it == "financialSnapshot" || it == "event" },
                "CONTRACT_FIELD_INVALID", "financial envelope keys")
            context.payload["financialSnapshot"] as? JsonObject
                ?: fail("CONTRACT_FIELD_INVALID", "financialSnapshot must be an object")
        } else {
            requireContract(context.isBootstrap, "CONTRACT_FIELD_MISSING", "financialSnapshot")
            context.payload
        }
        requireShape(root, FinancialAggregateSnapshotV2.serializer().descriptor, "financialSnapshot")
        val snapshot = try {
            SyncContractV2Codec.decodeFinancial(root.toString()).also(SyncContractV2Codec::requireValid)
        } catch (failure: IllegalArgumentException) {
            throw UnifiedSyncPullFailure("CONTRACT_INVALID", failure.message.orEmpty(), failure)
        }
        requireContract(snapshot.organizationId == context.organizationId && snapshot.invoiceId == context.aggregateId,
            "SCOPE_MISMATCH", "financial root")
        requireContract(snapshot.financialStreamVersion == context.entityVersion,
            "FINANCIAL_VERSION_MISMATCH", "DTO and accepted envelope disagree")
        validate(snapshot)
        if (!context.isBootstrap || context.payload.containsKey("event")) validateEvent(context, snapshot)
        return snapshot
    }

    fun validate(snapshot: FinancialAggregateSnapshotV2) {
        SyncContractV2Codec.requireValid(snapshot)
        // Converting also validates each enum; no default enum or replacement currency is permitted.
        snapshot.header.toRemoteEntityV2()
        snapshot.items.forEach { it.toRemoteEntityV2() }
        snapshot.payments.forEach { it.toRemoteEntityV2() }
        val h = snapshot.header
        requireText(h.clientId, "header.clientId") // includes the project's existing cash-client identities, unchanged
        requireContract(h.lifecycleVersion > 0, "CONTRACT_FIELD_INVALID", "lifecycleVersion")
        requireContract(h.supplierInvoiceReferenceNormalized == normalizeSupplierInvoiceReference(h.supplierInvoiceReference),
            "CONTRACT_FIELD_INVALID", "supplierInvoiceReferenceNormalized")
        requireContract(h.exchangeRateDirection == FUNCTIONAL_PER_TRANSACTION,
            "CONTRACT_FIELD_INVALID", "exchangeRateDirection")
        validateCurrency(h.legacyCurrencyStatus, h.transactionCurrencyCode, h.functionalCurrencyCode, h.invoiceExchangeRateSnapshot)
        snapshot.expectedFinancialStreamVersion?.let {
            requireContract(it < snapshot.financialStreamVersion, "FINANCIAL_VERSION_MISMATCH", "expected version must precede accepted version")
        }
        uniqueSorted(snapshot.items.map { it.id }, "items")
        uniqueSorted(snapshot.payments.map { it.id }, "payments")
        uniqueSorted(snapshot.paymentAllocations.map { it.id }, "paymentAllocations")
        uniqueSorted(snapshot.realizedFxEvents.map { it.id }, "realizedFxEvents")
        uniqueSorted(snapshot.returnDocuments.map { it.id }, "returnDocuments")
        uniqueSorted(snapshot.returnLines.map { it.id }, "returnLines")
        uniqueSorted(snapshot.returnPaymentAllocations.map { it.id }, "returnPaymentAllocations")
        unique(snapshot.dueInstallments.map { it.id }, "dueInstallments")
        requireContract(snapshot.dueInstallments == snapshot.dueInstallments.sortedWith(compareBy<InvoiceDueInstallmentDtoV2> { it.sequence }.thenBy { it.id }),
            "CONTRACT_FIELD_INVALID", "dueInstallments canonical order")
        unique(snapshot.dueInstallments.map { it.sequence }, "dueInstallments.sequence")
        snapshot.dueInstallments.forEach {
            requireContract(it.sequence > 0, "CONTRACT_FIELD_INVALID", "dueInstallments.sequence")
            requireContract(it.currencyCode == h.transactionCurrencyCode, "CONTRACT_REFERENCE_INVALID", "installment currency")
        }
        snapshot.items.forEach { requireContract(it.quantity > 0, "CONTRACT_FIELD_INVALID", "item quantity") }
        val payments = snapshot.payments.associateBy { it.id }
        snapshot.payments.forEach {
            requireText(it.clientId, "payment.clientId")
            requireContract(it.sourceVersion > 0, "CONTRACT_FIELD_INVALID", "payment.sourceVersion")
            requireContract(it.paymentExchangeRateDirection == FUNCTIONAL_PER_TRANSACTION,
                "CONTRACT_FIELD_INVALID", "paymentExchangeRateDirection")
            validateCurrency(it.legacyCurrencyStatus, it.paymentCurrencyCode, h.functionalCurrencyCode, it.paymentExchangeRate)
            it.reversedPaymentId?.let { original ->
                requireContract(original.isNotBlank() && original != it.id && original in payments,
                    "WAITING_DEPENDENCY", "payment reversal original")
            }
        }
        paymentsInDependencyOrder(snapshot.payments) // cycle check, not JSON ordering or paidAt
        unique(snapshot.paymentAllocations.map { it.paymentId to it.invoiceId }, "payment allocation economic identity")
        snapshot.paymentAllocations.forEach {
            requireContract(it.paymentId in payments && it.sourceVersion > 0, "CONTRACT_REFERENCE_INVALID", "payment allocation")
        }
        snapshot.realizedFxEvents.forEach {
            requireContract(it.paymentId in payments && it.sourceVersion > 0 && it.functionalCurrencyCode == h.functionalCurrencyCode,
                "CONTRACT_REFERENCE_INVALID", "realized FX")
        }
        val returns = snapshot.returnDocuments.associateBy { it.id }
        val items = snapshot.items.associateBy { it.id }
        unique(snapshot.returnDocuments.map { it.organizationId to it.writeId }, "return write identity")
        snapshot.returnDocuments.forEach {
            requireText(it.writeId, "return.writeId")
            requireContract(it.clientId == h.clientId && it.sourceVersion > 0,
                "CONTRACT_REFERENCE_INVALID", "return party/version")
            requireContract(it.documentType == if (h.category == "SALE") "SALES_RETURN_CREDIT_NOTE" else "PURCHASE_RETURN_DEBIT_NOTE",
                "CONTRACT_FIELD_INVALID", "return document type")
            requireContract(it.settlementMode in setOf("CREDIT_BALANCE", "CASH_REFUND"), "CONTRACT_FIELD_INVALID", "return settlement mode")
            requireContract(it.transactionCurrencyCode == h.transactionCurrencyCode && it.functionalCurrencyCode == h.functionalCurrencyCode,
                "CONTRACT_REFERENCE_INVALID", "return currency")
        }
        unique(snapshot.returnLines.map { it.returnId to it.originalInvoiceItemId }, "return line economic identity")
        snapshot.returnLines.forEach {
            requireContract(it.returnId in returns && it.originalInvoiceItemId in items && it.quantity > 0,
                "CONTRACT_REFERENCE_INVALID", "return line parents")
            requireContract(it.inventoryItemId == items.getValue(it.originalInvoiceItemId).inventoryItemId,
                "CONTRACT_REFERENCE_INVALID", "return inventory identity")
        }
        unique(snapshot.returnPaymentAllocations.map { it.returnId to it.paymentId }, "return payment economic identity")
        snapshot.returnPaymentAllocations.forEach {
            requireContract(it.returnId in returns && it.paymentId in payments, "CONTRACT_REFERENCE_INVALID", "return allocation parents")
        }
        unique(snapshot.explicitTombstones.map { it.entityType to it.id }, "tombstones")
        requireContract(snapshot.explicitTombstones == snapshot.explicitTombstones.sortedWith(compareBy<ExplicitTombstoneV2> { it.entityType }.thenBy { it.id }),
            "CONTRACT_FIELD_INVALID", "tombstone order")
        snapshot.explicitTombstones.forEach {
            requireText(it.id, "tombstone.id"); requireText(it.reason, "tombstone.reason")
            requireContract(it.entityType in setOf("INVOICE_ITEM", "INVOICE_DUE_INSTALLMENT"),
                "IMMUTABLE_FACT_CONFLICT", "only mutable financial children may be tombstoned; reverse immutable facts instead")
            requireContract(it.previousVersion > 0 && it.previousVersion < snapshot.financialStreamVersion,
                "FINANCIAL_VERSION_MISMATCH", "tombstone.previousVersion")
            val present = if (it.entityType == "INVOICE_ITEM") snapshot.items.any { row -> row.id == it.id }
                else snapshot.dueInstallments.any { row -> row.id == it.id }
            requireContract(!present, "CONTRACT_FIELD_INVALID", "live row and tombstone share identity")
        }
        unique(snapshot.effectReferences.map { Triple(it.owner, it.factType, it.factId) }, "effect fact identity")
        // A financial write may reverse several payments (InvoiceVoidCoordinator). PAYMENT's
        // businessIdentity is the existing writeId correlation, not a unique payment key. Keep
        // every paymentId and its immutable hash; never deduplicate distinct facts by writeId.
        unique(snapshot.effectReferences.filterNot { it.factType == "PAYMENT" }
            .map { Triple(it.owner, it.factType, it.businessIdentity) }, "effect business identity")
        requireContract(snapshot.effectReferences == snapshot.effectReferences.sortedWith(compareBy<EffectReferenceV2> { it.owner }.thenBy { it.factType }.thenBy { it.factId }),
            "CONTRACT_FIELD_INVALID", "effect order")
        snapshot.effectReferences.forEach {
            requireText(it.factId, "effect.factId"); requireText(it.businessIdentity, "effect.businessIdentity")
            requireContract(OWNER_BY_FACT[it.factType] == it.owner, "CONTRACT_UNSUPPORTED", "effect owner")
            if (it.factType == "PAYMENT") requireContract(it.factId in payments, "CONTRACT_REFERENCE_INVALID", "payment effect missing DTO")
        }
        // B06 captures every original/reversal payment as one owned fact; missing references are incomplete, not zero.
        requireContract(snapshot.payments.all { payment -> snapshot.effectReferences.any { it.factType == "PAYMENT" && it.factId == payment.id } },
            "BATCH_DEPENDENCY_MISSING", "payment owner reference")
        minorTotals(snapshot) // exact, checked sums; no Double or currency mixing
    }

    fun paymentsInDependencyOrder(rows: List<PaymentDtoV2>): List<PaymentDtoV2> {
        val remaining = rows.associateBy { it.id }.toMutableMap()
        val result = mutableListOf<PaymentDtoV2>()
        val emitted = hashSetOf<String>()
        while (remaining.isNotEmpty()) {
            val ready = remaining.values.filter { it.reversedPaymentId == null || it.reversedPaymentId in emitted }.sortedBy { it.id }
            requireContract(ready.isNotEmpty(), "CONTRACT_REFERENCE_INVALID", "missing/cyclic payment reversal dependency")
            ready.forEach { result += it; emitted += it.id; remaining.remove(it.id) }
        }
        return result
    }

    /** Diagnostic/test parity summary only. It does not recalculate or post any business effects. */
    fun minorTotals(s: FinancialAggregateSnapshotV2): Map<String, Long> = buildMap {
        fun total(name: String, values: List<Long>) {
            put(name, values.fold(0L) { sum, value ->
                try { Math.addExact(sum, value) } catch (failure: ArithmeticException) {
                    throw UnifiedSyncPullFailure("CONTRACT_FIELD_INVALID", "Minor overflow: $name", failure)
                }
            })
        }
        total("items.totalPriceMinor", s.items.map { it.totalPriceMinor })
        total("items.lineCostSnapshotMinor", s.items.map { it.lineCostSnapshotMinor })
        total("dues.amountMinor", s.dueInstallments.map { it.amountMinor })
        s.payments.groupBy { it.paymentCurrencyCode }.forEach { (currency, rows) -> total("payments.$currency.amountMinor", rows.map { it.amountMinor }) }
        total("allocations.historicalFunctionalAmountMinor", s.paymentAllocations.map { it.historicalFunctionalAmountMinor })
        total("fx.differenceMinor", s.realizedFxEvents.map { it.differenceMinor })
        total("returns.functionalAmountMinor", s.returnDocuments.map { it.functionalAmountMinor })
        total("returnLines.historicalCostAmountMinor", s.returnLines.map { it.historicalCostAmountMinor })
        total("returnAllocations.allocatedFunctionalAmountMinor", s.returnPaymentAllocations.map { it.allocatedFunctionalAmountMinor })
    }

    fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun validateEvent(context: UnifiedRemoteMaterialization, snapshot: FinancialAggregateSnapshotV2) {
        val event = context.payload["event"] as? JsonObject ?: fail("CONTRACT_FIELD_MISSING", "event")
        fun text(name: String): String {
            val value = event[name] as? JsonPrimitive
            requireContract(value != null && value.isString && value.content.isNotBlank(), "CONTRACT_FIELD_MISSING", "event.$name")
            return value!!.content
        }
        text("eventId"); text("writeId")
        val operation = text("domainOperation")
        requireContract((event["schemaVersion"] as? JsonPrimitive)?.let { !it.isString && it.intOrNull == 2 } == true &&
            (event["domainPayloadVersion"] as? JsonPrimitive)?.let { !it.isString && it.intOrNull == 2 } == true,
            "CONTRACT_FIELD_INVALID", "event schema/payload version")
        if (context.aggregateType == "PAYMENT") {
            requireContract(operation in setOf("PAYMENT_RECORDED", "PAYMENT_REVERSED"), "CONTRACT_FIELD_INVALID", "payment operation")
            val paymentId = text("paymentId")
            val payment = snapshot.payments.singleOrNull { it.id == paymentId }
                ?: fail("CONTRACT_REFERENCE_INVALID", "explicit paymentId is absent from FULL snapshot")
            requireContract(event.containsKey("reversedPaymentId"), "CONTRACT_FIELD_MISSING", "event.reversedPaymentId")
            requireContract((operation == "PAYMENT_REVERSED") == (payment.reversedPaymentId != null),
                "CONTRACT_REFERENCE_INVALID", "payment operation/original relationship")
            val expected = payment.reversedPaymentId?.let(::JsonPrimitive) ?: JsonNull
            requireContract(event["reversedPaymentId"] == expected, "CONTRACT_REFERENCE_INVALID", "event reversal identity")
        } else {
            requireContract(operation in setOf("INVOICE_CREATED", "INVOICE_UPDATED", "INVOICE_VOIDED", "INVOICE_RETURN_POSTED"),
                "CONTRACT_FIELD_INVALID", "invoice operation")
        }
    }

    private fun validateCurrency(status: String, transaction: String, functional: String, rate: String) {
        if (status == LegacyCurrencyStatus.KNOWN.name) {
            requireText(transaction, "known transaction currency"); requireText(functional, "known functional currency")
            requireText(rate, "known exchange rate")
        }
        if (rate.isNotEmpty()) requireContract(DECIMAL.matches(rate) && runCatching { BigDecimal(rate).signum() > 0 }.getOrDefault(false),
            "CONTRACT_FIELD_INVALID", "recorded decimal exchange rate")
    }

    private fun requireShape(element: JsonElement, descriptor: SerialDescriptor, path: String) {
        if (element == JsonNull) {
            requireContract(descriptor.isNullable, "CONTRACT_FIELD_INVALID", "$path cannot be null")
            return
        }
        when (descriptor.kind) {
            StructureKind.CLASS -> {
                val obj = element as? JsonObject ?: fail("CONTRACT_FIELD_INVALID", "$path must be an object")
                val names = (0 until descriptor.elementsCount).map(descriptor::getElementName).toSet()
                requireContract(obj.keys == names, "CONTRACT_FIELD_MISSING", "$path: missing=${names - obj.keys}; unknown=${obj.keys - names}")
                repeat(descriptor.elementsCount) { index ->
                    val name = descriptor.getElementName(index)
                    requireShape(obj.getValue(name), descriptor.getElementDescriptor(index), "$path.$name")
                }
            }
            StructureKind.LIST -> {
                val array = element as? JsonArray ?: fail("CONTRACT_FIELD_INVALID", "$path must be an array")
                array.forEachIndexed { index, child -> requireShape(child, descriptor.getElementDescriptor(0), "$path[$index]") }
            }
            PrimitiveKind.STRING, SerialKind.ENUM -> requireContract(element is JsonPrimitive && element.isString, "CONTRACT_FIELD_INVALID", "$path must be a string")
            PrimitiveKind.LONG -> requireContract(element is JsonPrimitive && !element.isString && element.longOrNull != null, "CONTRACT_FIELD_INVALID", "$path must be an exact Long")
            PrimitiveKind.INT -> requireContract(element is JsonPrimitive && !element.isString && element.intOrNull != null, "CONTRACT_FIELD_INVALID", "$path must be an exact Int")
            PrimitiveKind.BOOLEAN -> requireContract(element is JsonPrimitive && !element.isString && element.booleanOrNull != null, "CONTRACT_FIELD_INVALID", "$path must be a Boolean")
            else -> fail("CONTRACT_UNSUPPORTED", "$path unsupported serializer kind")
        }
    }

    private fun uniqueSorted(values: List<String>, field: String) {
        unique(values, field)
        requireContract(values == values.sorted(), "CONTRACT_FIELD_INVALID", "$field canonical order")
    }
    private fun <T> unique(values: List<T>, field: String) {
        requireContract(values.size == values.toSet().size, "CONTRACT_FIELD_INVALID", "duplicate $field")
        values.filterIsInstance<String>().forEach { requireText(it, "$field.id") }
    }
    private fun requireText(value: String, field: String) = requireContract(value.isNotBlank(), "CONTRACT_FIELD_MISSING", field)
    internal fun requireContract(condition: Boolean, code: String, detail: String) { if (!condition) fail(code, detail) }
    internal fun fail(code: String, detail: String): Nothing = throw UnifiedSyncPullFailure(code, detail)
    private val DECIMAL = Regex("[0-9]+(?:\\.[0-9]+)?")
    internal val OWNER_BY_FACT = mapOf("PAYMENT" to "financial_outbox", "INVENTORY_MOVEMENT" to "inventory_stock_outbox",
        "INVENTORY_COST_REVISION" to "inventory_cost_outbox", "CASH_MOVEMENT" to "sync_outbox",
        "CLIENT_CREDIT" to "sync_outbox", "COMMISSION_PAYMENT" to "sync_outbox")
}
