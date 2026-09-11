package com.verto.app.feature.sync.di

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.remote.PermissionProvider
import com.verto.app.data.sync.conflict.SyncConflictDecisionActor
import com.verto.app.data.sync.conflict.SyncConflictDecisionAuthorizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionSyncConflictDecisionAuthorizer @Inject constructor(
    private val sessionReader: SessionReader,
    private val permissionProvider: PermissionProvider,
) : SyncConflictDecisionAuthorizer {
    override suspend fun requireAuthorized(organizationId: String): SyncConflictDecisionActor {
        val snapshot = sessionReader.snapshot()
        require(snapshot.organization.id.isNotBlank() && snapshot.organization.id == organizationId) { "SCOPE_MISMATCH" }
        require(snapshot.user.id.isNotBlank()) { "AUTH_REQUIRED" }
        // Conflict resolution is intentionally privileged and fail-closed; no entity permission is guessed.
        val settingsOrgData = permissionProvider.canNow { it.settingsOrgData }
        val allowed = ConflictResolutionPermissionPolicy.allowed(snapshot.role, settingsOrgData)
        if (!allowed) throw SecurityException("CONFLICT_RESOLUTION_PERMISSION_DENIED")
        return SyncConflictDecisionActor(snapshot.user.id, snapshot.role.ifBlank { "unknown" })
    }
}

internal object ConflictResolutionPermissionPolicy {
    fun allowed(role: String, settingsOrgData: Boolean): Boolean = role == "admin" || settingsOrgData
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncConflictReviewModule {
    @Binds
    @Singleton
    abstract fun bindSyncConflictDecisionAuthorizer(
        implementation: SessionSyncConflictDecisionAuthorizer,
    ): SyncConflictDecisionAuthorizer
}
