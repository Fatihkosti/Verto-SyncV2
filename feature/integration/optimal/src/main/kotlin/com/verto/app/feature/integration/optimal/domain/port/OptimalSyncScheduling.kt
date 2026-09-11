package com.verto.app.feature.integration.optimal.domain.port

/** Identity captured when a sync request is scheduled. */
data class OptimalSyncScope(
    val organizationId: String,
    val userId: String,
) {
    init {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(userId.isNotBlank()) { "userId is required" }
    }
}

/** Work scheduling boundary; WorkManager stays in the application composition layer. */
interface OptimalSyncScheduler {
    fun enqueueImmediate(scope: OptimalSyncScope)
    fun ensurePeriodic(scope: OptimalSyncScope)
    fun cancel(scope: OptimalSyncScope)
    fun cancelAll()
}

/** Application-facing orchestration contract; callers depend on this API, not its use-case implementation. */
interface OptimalSyncCoordinator {
    suspend fun immediateForCurrentSession(): Boolean
    suspend fun periodicForCurrentSession(): Boolean
    fun immediate(scope: OptimalSyncScope)
    fun periodic(scope: OptimalSyncScope)
    fun cancel(scope: OptimalSyncScope)
    fun cancelAll()
}

/** Local session guard checked before each Outbox event is sent. */
fun interface OptimalSyncSessionGuard {
    suspend fun isActive(scope: OptimalSyncScope): Boolean
}
