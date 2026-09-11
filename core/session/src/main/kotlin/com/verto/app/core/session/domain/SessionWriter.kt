package com.verto.app.core.session.domain

/** عقد الكتابة الوحيد لهوية الجلسة وكاش الوصول. */
interface SessionWriter {
    suspend fun setUserId(value: String)
    suspend fun setUserName(value: String)
    suspend fun setUserPhone(value: String)
    suspend fun setOrganizationId(value: String)
    suspend fun setRole(value: String)
    suspend fun setPermissionsJson(value: String)

    suspend fun clearAccess()
}
