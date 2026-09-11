package com.verto.app.feature.auth.domain.model

/** بيانات الدعوة التي تحتاجها شاشة الانضمام دون كشف DTO أو تفاصيل Supabase. */
data class AuthInviteDetails(
    val code: String,
    val employeeName: String,
    val jobTitle: String,
    val actualJoinDate: String
)
