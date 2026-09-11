package com.verto.app.data.operations.transaction

import androidx.room.withTransaction
import com.verto.app.core.transaction.DatabaseTransactionRunner
import com.verto.app.data.local.AppDatabase
import javax.inject.Inject

class RoomDatabaseTransactionRunner @Inject constructor(
    private val database: AppDatabase,
) : DatabaseTransactionRunner {
    override suspend fun <T> inTransaction(block: suspend () -> T): T =
        database.withTransaction { block() }
}
