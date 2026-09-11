package com.verto.app.feature.integration.optimal.domain.port

import com.verto.app.feature.integration.optimal.domain.model.OptimalOutboxEvent
import com.verto.app.feature.integration.optimal.domain.model.OptimalOutboxExecutionResult

/** Remote operation boundary. v76 defines lifecycle only; concrete dispatch is wired in v77. */
fun interface OptimalOutboxEventExecutor {
    suspend fun execute(event: OptimalOutboxEvent): OptimalOutboxExecutionResult
}

fun interface OptimalLeaseTokenFactory {
    fun create(): String
}
