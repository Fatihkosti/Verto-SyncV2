package com.verto.app.feature.organization.bridge
import com.verto.app.data.repository.OrgSettingsRepository
import com.verto.app.data.sync.SyncFailureMode
import com.verto.app.data.sync.SyncOperation
import com.verto.app.data.sync.SyncParticipant
import com.verto.app.data.sync.SyncRunContext
import com.verto.app.data.sync.SyncRuntime
import com.verto.app.data.sync.SyncOperationSlot
import com.verto.app.data.sync.pullNotifications

class OrganizationSyncParticipant(
    private val runtime: SyncRuntime,
    private val orgSettingsRepository: OrgSettingsRepository
) : SyncParticipant {
    override val key: String = "organization"

    override fun operations(context: SyncRunContext): List<SyncOperation> = listOf(
        SyncOperation(SyncOperationSlot.PUSH_ORGANIZATION_SETTINGS, "رفع إعدادات المؤسسة المعدلة", SyncFailureMode.COLLECT, execute = {
            orgSettingsRepository.pushPendingToSupabase()
        }),
        SyncOperation(SyncOperationSlot.PULL_NOTIFICATIONS, "pull الإشعارات", SyncFailureMode.COLLECT, execute = {
            runtime.pullNotifications(context.organizationId)
        }),
        SyncOperation(SyncOperationSlot.PULL_ORGANIZATION_SETTINGS, "إعدادات المؤسسة", SyncFailureMode.COLLECT, execute = {
            orgSettingsRepository.syncFromSupabase()
        })
    )
}
