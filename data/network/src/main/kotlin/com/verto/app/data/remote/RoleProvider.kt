package com.verto.app.data.remote

import android.util.Log
import com.verto.app.core.concurrency.AppCoroutineScope
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.core.session.domain.SessionWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * واجهة توافقية لدور المستخدم الحالي.
 *
 * مصدر التخزين لم يعد معروفًا لهذه الطبقة؛ القراءة والكتابة تمران عبر عقود الجلسة.
 * التحصين النهائي يبقى سيرفريًا عبر RLS وRPCs.
 */
class RoleProvider(
    private val authRepository: AuthRepository,
    sessionReader: SessionReader,
    private val sessionWriter: SessionWriter,
    private val appScope: AppCoroutineScope,
) {

    /** `null` يعني أن الدور لم يُحمّل بعد. */
    val role: StateFlow<String?> =
        sessionReader.role.map { it.ifBlank { null } }
            .stateIn(appScope, SharingStarted.Eagerly, null)

    val isAdmin: StateFlow<Boolean> =
        role.map { it == "admin" }.stateIn(appScope, SharingStarted.Eagerly, false)

    fun refreshAsync() { appScope.launch { refresh() } }

    suspend fun refresh() {
        runCatching {
            val profile = authRepository.getMyProfile() ?: return
            if (profile.role.isNotBlank()) sessionWriter.setRole(profile.role)
        }.onFailure { failure ->
            if (failure is CancellationException) throw failure
            Log.w("RoleProvider", "Role refresh failed: ${failure::class.java.simpleName}")
        }
    }

    suspend fun clear() { sessionWriter.setRole("") }
}
