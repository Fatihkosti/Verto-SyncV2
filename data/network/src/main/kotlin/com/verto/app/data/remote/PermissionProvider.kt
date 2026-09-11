package com.verto.app.data.remote

import android.util.Log
import com.verto.app.core.concurrency.AppCoroutineScope
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.core.session.domain.SessionWriter
import com.verto.app.data.model.EmployeePermissions
import com.verto.app.core.session.model.ManagementOptimalPermission
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * واجهة توافقية لصلاحيات المستخدم الحالي.
 *
 * كاش الدور والصلاحيات يمر عبر عقود الجلسة، لذلك لا تعرف هذه الطبقة تنفيذ التخزين أو
 * مدير التفضيلات. القاعدة: المدير كامل الصلاحيات، والموظف غير المحمّل يُرفض افتراضيًا.
 */
class PermissionProvider(
    private val authRepository: AuthRepository,
    private val sessionReader: SessionReader,
    private val sessionWriter: SessionWriter,
    private val appScope: AppCoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val permissions: StateFlow<EmployeePermissions?> =
        combine(sessionReader.role, sessionReader.permissionsJson) { role, permsJson ->
            when {
                role == "admin" -> EmployeePermissions.fullAccess()
                permsJson.isNotBlank() -> runCatching {
                    json.decodeFromString<EmployeePermissions>(permsJson)
                }.getOrNull()
                else -> null
            }
        }.stateIn(appScope, SharingStarted.Eagerly, null)

    fun can(check: (EmployeePermissions) -> Boolean): Boolean {
        val p = permissions.value ?: return false
        return check(p)
    }

    fun canPermission(permission: ManagementOptimalPermission): Boolean =
        permissions.value?.allows(permission) == true

    suspend fun canNow(check: (EmployeePermissions) -> Boolean): Boolean {
        val role = sessionReader.role.first()
        if (role == "admin") return true
        val permsJson = sessionReader.permissionsJson.first()
        if (permsJson.isBlank()) return false
        val p = runCatching { json.decodeFromString<EmployeePermissions>(permsJson) }.getOrNull()
            ?: return false
        return check(p)
    }

    suspend fun canPermissionNow(permission: ManagementOptimalPermission): Boolean =
        canNow { it.allows(permission) }

    fun refreshAsync() { appScope.launch { refresh() } }

    suspend fun refresh() {
        authRepository.fetchMyPermissions()
            .onSuccess { sessionWriter.setPermissionsJson(json.encodeToString(it)) }
            .onFailure { Log.w("PermissionProvider", "Permission refresh failed: ${it::class.java.simpleName}") }
    }

    suspend fun clear() { sessionWriter.setPermissionsJson("") }
}
