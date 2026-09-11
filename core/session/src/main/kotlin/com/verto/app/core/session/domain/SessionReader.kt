package com.verto.app.core.session.domain

import com.verto.app.core.session.model.CurrentOrganization
import com.verto.app.core.session.model.CurrentUser
import com.verto.app.core.session.model.SessionState
import kotlinx.coroutines.flow.Flow

/** عقد القراءة الوحيد لهوية الجلسة وكاش الوصول. */
interface SessionReader {
    val userId: Flow<String>
    val userName: Flow<String>
    val userPhone: Flow<String>
    val role: Flow<String>
    val permissionsJson: Flow<String>
    val organizationId: Flow<String>

    val currentUser: Flow<CurrentUser>
    val currentOrganization: Flow<CurrentOrganization>

    suspend fun snapshot(): SessionState
}
