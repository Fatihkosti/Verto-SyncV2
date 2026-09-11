package com.verto.app.feature.settings.bridge
import android.content.Context
import android.net.Uri
import com.verto.app.data.backup.BackupManager
import com.verto.app.data.backup.ShareTarget
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.model.PermissionDeniedException
import com.verto.app.data.remote.AuthRepository
import com.verto.app.data.remote.PermissionProvider
import com.verto.app.data.remote.RoleProvider
import com.verto.app.data.sync.RealtimeManager
import com.verto.app.data.sync.SyncManager
import com.verto.app.data.sync.SyncRequestReason
import com.verto.app.data.workers.RfmCalculationWorker
import com.verto.app.notifications.FcmTokenStore
import com.verto.app.utils.CrashReporter
import com.verto.app.feature.settings.domain.model.SettingsBackupTarget
import com.verto.app.feature.integration.optimal.domain.port.OptimalSyncCoordinator
import com.verto.app.feature.settings.domain.model.SettingsDataScope
import com.verto.app.feature.settings.domain.repository.SettingsOperationsGateway
import com.verto.app.core.audit.domain.WriteAuditPort
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.core.session.model.ManagementOptimalPermission
import com.verto.app.utils.ErrorHumanizer
import com.verto.app.utils.PreferencesManager
import com.verto.app.core.audit.domain.logPermissionDenied
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** محول انتقالي يحصر العمليات العامة القديمة خلف عقد واحد خاص بشاشة الإعدادات. */
@Singleton
class SettingsOperationsGatewayAdapter @Inject constructor(
    private val preferences: PreferencesManager,
    private val authRepository: AuthRepository,
    private val database: AppDatabase,
    private val syncManager: SyncManager,
    private val backupManager: BackupManager,
    private val roleProvider: RoleProvider,
    private val permissionProvider: PermissionProvider,
    private val auditLogger: WriteAuditPort,
    private val sessionReader: SessionReader,
    private val realtimeManager: RealtimeManager,
    private val scheduleOptimalSync: OptimalSyncCoordinator,
    @ApplicationContext private val appContext: Context
) : SettingsOperationsGateway {

    override suspend fun currentUserIsAdmin(): Boolean =
        runCatching { authRepository.getMyProfile()?.role == "admin" }.getOrDefault(false)

    override suspend fun currentUserCanViewManagement(): Boolean =
        permissionProvider.canPermissionNow(ManagementOptimalPermission.VIEW_MANAGEMENT)

    override suspend fun syncNow(): Result<Unit> = syncManager.request(SyncRequestReason.MANUAL).map { Unit }

    override fun observeLastSuccessfulSyncAt(): Flow<Long?> =
        syncManager.persistedReport
            .map { it?.lastSuccessfulAtMillis }
            .onStart { syncManager.restoreLastReportForCurrentSession() }

    override suspend fun exportBackup(target: SettingsBackupTarget): Result<Unit> = runCatching {
        requireAdmin("backup_export", "تصدير النسخة الاحتياطية متاح للمدير فقط")
        backupManager.exportAndShare(target.toLegacyTarget())
    }

    override suspend fun importBackup(uri: String): Result<String> = runCatching {
        requireAdmin("backup_import", "استيراد النسخة الاحتياطية متاح للمدير فقط")
        backupManager.importFromUri(Uri.parse(uri)).getOrThrow()
    }

    override suspend fun resetData(scope: SettingsDataScope): Result<Unit> = runCatching {
        requireAdmin("settings_reset", "إعادة تعيين البيانات متاحة للمدير فقط")
        when (scope) {
            SettingsDataScope.SALES_INVOICES -> database.invoiceDao().deleteAllSalesInvoices()
            SettingsDataScope.PURCHASE_INVOICES -> database.invoiceDao().deleteAllPurchaseInvoices()
            SettingsDataScope.CLIENTS -> database.clientDao().deleteAllClients()
            SettingsDataScope.INVENTORY -> database.inventoryDao().archiveAllItems()
            SettingsDataScope.CASH_REGISTER -> database.cashRegisterDao().resetRegister()
            SettingsDataScope.ALL -> {
                database.invoiceDao().deleteAllInvoices()
                database.clientDao().deleteAllClients()
                database.inventoryDao().archiveAllItems()
                database.cashRegisterDao().resetRegister()
            }
        }
    }

    override suspend fun logout(): Result<Unit> = runCatching {
        // M07: a manual request is durable acceptance only; never treat wake acceptance as completed sync.
        syncManager.request(SyncRequestReason.MANUAL).getOrThrow()
        syncManager.ensureSessionCanEnd()

        realtimeManager.stop()
        scheduleOptimalSync.cancelAll()
        RfmCalculationWorker.cancel(appContext)
        roleProvider.clear()
        permissionProvider.clear()

        val logoutResult = authRepository.logout()
        syncManager.clearLocalData().getOrThrow()
        FcmTokenStore.clearSession(appContext)
        CrashReporter.clearUser()
        logoutResult.getOrThrow()
    }

    private suspend fun requireAdmin(action: String, message: String) {
        if (!currentUserIsAdmin()) {
            runCatching { auditLogger.logPermissionDenied(action, message, sessionReader) }
            throw PermissionDeniedException(message)
        }
    }

    private fun SettingsBackupTarget.toLegacyTarget(): ShareTarget = when (this) {
        SettingsBackupTarget.LOCAL -> ShareTarget.LOCAL
        SettingsBackupTarget.WHATSAPP -> ShareTarget.WHATSAPP
        SettingsBackupTarget.TELEGRAM -> ShareTarget.TELEGRAM
    }
}
