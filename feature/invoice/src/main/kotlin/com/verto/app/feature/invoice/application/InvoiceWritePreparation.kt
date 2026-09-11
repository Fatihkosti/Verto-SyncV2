package com.verto.app.feature.invoice.application

import com.verto.app.feature.invoice.domain.model.SaveInvoiceCommand
import com.verto.app.feature.invoice.domain.port.InvoiceNumberPort
import javax.inject.Inject

/** Deterministic validation/draft boundary used by the invoice write orchestrator. */
internal class InvoiceWritePreparation @Inject constructor(
    private val numberPort: InvoiceNumberPort,
    private val validator: InvoiceSaveValidator,
    private val draftFactory: InvoiceDraftFactory,
    private val identityFactory: InvoiceWriteIdentityFactory,
) {
    fun newId(): String = identityFactory.newId()
    fun nowMillis(): Long = identityFactory.nowMillis()

    fun validate(command: SaveInvoiceCommand): ValidatedInvoiceSave = validator.validate(command)

    fun requiresReferencedInventory(command: SaveInvoiceCommand): Boolean = validator.requiresReferencedInventory(command)

    fun validateReferencedInventory(
        command: SaveInvoiceCommand,
        inventoryItems: Collection<com.verto.app.feature.invoice.domain.model.InvoiceStockItem>,
    ) = validator.validateReferencedInventory(command, inventoryItems)

    suspend fun createDraft(
        command: SaveInvoiceCommand,
        validated: ValidatedInvoiceSave,
        actorId: String,
    ): PreparedInvoiceDraft {
        val creatingNew = command.existingInvoiceId == null
        val createdAt = command.originalCreatedAt ?: identityFactory.nowMillis()
        val serverNumber = if (creatingNew && command.originalInvoiceNumber == null) numberPort.allocate() else null
        return draftFactory.create(
            command,
            validated,
            InvoiceDraftSeed(
                invoiceId = command.existingInvoiceId ?: identityFactory.newId(),
                invoiceNumber = command.originalInvoiceNumber ?: serverNumber ?: 0,
                createdAt = createdAt,
                createdBy = actorId,
            ),
            lineIdProvider = identityFactory::newId,
        )
    }
}
