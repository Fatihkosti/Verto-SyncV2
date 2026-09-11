package com.verto.app.feature.settings.domain.repository

import com.verto.app.feature.settings.domain.model.SettingsBackupTarget
import com.verto.app.feature.settings.domain.model.SettingsDataScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** حد عمليات شاشة الإعدادات العامة دون معرفة تفاصيل التخزين أو الجلسة أو المزامنة. */
interface SettingsOperationsGateway {
    suspend fun currentUserIsAdmin(): Boolean
    suspend fun currentUserCanViewManagement(): Boolean
    suspend fun syncNow(): Result<Unit>
    fun observeLastSuccessfulSyncAt(): Flow<Long?> = flowOf(null)
    suspend fun exportBackup(target: SettingsBackupTarget): Result<Unit>
    suspend fun importBackup(uri: String): Result<String>
    suspend fun resetData(scope: SettingsDataScope): Result<Unit>
    suspend fun logout(): Result<Unit>
}
