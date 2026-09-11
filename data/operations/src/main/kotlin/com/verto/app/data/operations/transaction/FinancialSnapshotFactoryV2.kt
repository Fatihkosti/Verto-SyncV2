package com.verto.app.data.operations.transaction

import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.*
import com.verto.app.data.sync.*
import javax.inject.Inject
import javax.inject.Singleton
import java.security.MessageDigest

/** Reads one complete financial aggregate while the caller owns the producer Room transaction. */
@Singleton
class FinancialSnapshotFactoryV2 @Inject constructor(
    private val database: AppDatabase,
) {
    suspend fun capture(
        organizationId: String,
        invoiceId: String,
        financialStreamVersion: Long,
        expectedFinancialStreamVersion: Long?,
        explicitTombstones: List<ExplicitTombstoneV2> = emptyList(),
        effectReferences: List<EffectReferenceV2> = emptyList(),
    ): FinancialAggregateSnapshotV2 {
        check(database.inTransaction()) { "FINANCIAL_SNAPSHOT_TRANSACTION_REQUIRED" }
        val invoice = requireNotNull(database.invoiceDao().getInvoiceByIdSync(invoiceId)) {
            "CONTRACT_FIELD_MISSING: invoice"
        }
        require(invoice.organizationId == organizationId) { "SCOPE_MISMATCH: invoice.organizationId" }

        val returns = database.invoiceReturnDao().getForInvoice(invoiceId).sortedBy { it.id }
        val returnIds = returns.mapTo(hashSetOf()) { it.id }
        val payments = database.paymentDao().getPaymentsForInvoiceSync(invoiceId).sortedBy { it.id }
        val inferredEffects = payments.map { payment ->
            val dto = payment.toDtoV2()
            EffectReferenceV2("financial_outbox", "PAYMENT", payment.id, payment.writeId.ifBlank { payment.id }, sha256(SyncContractV2Codec.encode(dto)))
        } + database.inventoryDao().getMovementsForInvoice(invoiceId).sortedBy { it.id }.map { movement ->
            val identity = movement.idempotencyKey?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("CONTRACT_FIELD_MISSING: inventory movement idempotencyKey")
            EffectReferenceV2("inventory_stock_outbox", "INVENTORY_MOVEMENT", movement.id, identity,
                sha256(listOf(movement.id, movement.itemId, movement.signedBaseQuantity, movement.unitPriceMinor, movement.sourceType, movement.sourceId).joinToString("\u0000")))
        }
        val snapshot = FinancialAggregateSnapshotV2(
            organizationId = organizationId,
            invoiceId = invoiceId,
            financialStreamVersion = financialStreamVersion,
            expectedFinancialStreamVersion = expectedFinancialStreamVersion,
            header = invoice.toDtoV2(),
            items = database.invoiceDao().getInvoiceItemsSync(invoiceId).sortedBy { it.id }.map { it.toDtoV2() },
            dueInstallments = database.invoiceDao().getDueInstallments(invoiceId)
                .sortedWith(compareBy<InvoiceDueInstallmentEntity> { it.sequence }.thenBy { it.id }).map { it.toDtoV2() },
            payments = payments.map { it.toDtoV2() },
            paymentAllocations = database.paymentDao().getPaymentAllocationsForInvoiceSync(invoiceId).sortedBy { it.id }.map { it.toDtoV2() },
            realizedFxEvents = database.paymentDao().getRealizedFxEventsForInvoiceSync(invoiceId).sortedBy { it.id }.map { it.toDtoV2() },
            returnDocuments = returns.map { it.toDtoV2() },
            returnLines = returns.flatMap { database.invoiceReturnDao().getLines(it.id) }
                .filter { it.returnId in returnIds }.sortedBy { it.id }.map { it.toDtoV2() },
            returnPaymentAllocations = returns.flatMap { database.invoiceReturnDao().getPaymentAllocations(it.id) }
                .filter { it.returnId in returnIds }.sortedBy { it.id }.map { it.toDtoV2() },
            explicitTombstones = explicitTombstones.sortedWith(compareBy<ExplicitTombstoneV2> { it.entityType }.thenBy { it.id }),
            effectReferences = (effectReferences + inferredEffects).distinctBy { Triple(it.owner, it.factType, it.factId) }
                .sortedWith(compareBy<EffectReferenceV2> { it.owner }.thenBy { it.factType }.thenBy { it.factId }),
            businessContentHash = ZERO_HASH,
        )
        val complete = snapshot.copy(businessContentHash = SyncContractV2Codec.financialBusinessHash(snapshot))
        SyncContractV2Codec.requireValid(complete)
        return complete
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    companion object { private const val ZERO_HASH = "0000000000000000000000000000000000000000000000000000000000000000" }
}
