package com.verto.app.utils

/** Session 336 explicit parent metadata for reverse cash effects. */
data class CashReverseContext336(
    val sourceType: String = "",
    val dependsOnMutationId: String? = null,
)

internal fun expenseCashDependency336(sourceType: String, writeId: String): String? =
    writeId.takeIf { sourceType.startsWith("EXPENSE") && it.isNotBlank() }
