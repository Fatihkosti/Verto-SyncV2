package com.verto.app.feature.profile.domain.repository

import com.verto.app.feature.profile.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/** الحد الذي تحتاجه واجهة الملف الشخصي دون معرفة التخزين المحلي أو عميل الشبكة. */
interface ProfileGateway {
    val profile: Flow<UserProfile>

    suspend fun canEditName(): Boolean
    suspend fun updateName(name: String)
    suspend fun updatePhone(phone: String)
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit>
}
