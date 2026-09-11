package com.verto.app.feature.auth.domain.model

/** الحد الأدنى من بيانات المستخدم الذي تحتاجه ميزة المصادقة بعد نجاح الدخول. */
data class AuthenticatedUser(
    val userId: String,
    val organizationId: String,
    val name: String,
    val role: String
)
