package com.verto.app.feature.inventory.domain.port

data class InventoryLandedCostAdjustment(
    val target: Target,
    val mutations: Mutations,
    val ordering: Ordering,
) {
    data class Target(
        val organizationId: String, val itemId: String, val newBuyPrice: Double,
        val movementId: String, val movementNote: String, val sourceExpenseId: String,
    )
    data class Mutations(val itemMutationId: String, val movementMutationId: String)
    data class Ordering(val commandBatchId: String, val commandOrder: Int)

    val organizationId get() = target.organizationId
    val itemId get() = target.itemId
    val newBuyPrice get() = target.newBuyPrice
    val movementId get() = target.movementId
    val movementNote get() = target.movementNote
    val sourceExpenseId get() = target.sourceExpenseId
    val itemMutationId get() = mutations.itemMutationId
    val movementMutationId get() = mutations.movementMutationId
    val commandBatchId get() = ordering.commandBatchId
    val commandOrder get() = ordering.commandOrder
}

interface InventoryLandedCostAdjustmentPort {
    suspend fun apply(command: InventoryLandedCostAdjustment): Boolean
}
