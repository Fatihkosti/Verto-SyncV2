package com.verto.app.feature.auth.application

/** عقد تنسيق ما بعد نجاح المصادقة؛ التنفيذ Android-owned داخل app. */
interface AuthSessionCoordinator {
    suspend fun completeAuthentication(organizationId: String)
    suspend fun restoreAuthentication(organizationId: String)
}
