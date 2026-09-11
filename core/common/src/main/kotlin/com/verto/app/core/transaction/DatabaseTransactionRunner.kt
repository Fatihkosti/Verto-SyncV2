package com.verto.app.core.transaction

/**
 * Shared transaction boundary implemented by the persistence owner.
 * Feature modules depend on this contract without importing Room.
 */
interface DatabaseTransactionRunner {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}
