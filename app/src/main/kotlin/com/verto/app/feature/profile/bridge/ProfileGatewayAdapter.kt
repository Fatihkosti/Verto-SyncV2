package com.verto.app.feature.profile.bridge
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.core.session.domain.SessionWriter
import com.verto.app.data.remote.AuthRepository
import com.verto.app.feature.profile.domain.model.UserProfile
import com.verto.app.feature.profile.domain.repository.ProfileGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/** محول انتقالي يحافظ على السلوك الحالي خلف عقد الملف الشخصي وعقود الجلسة. */
@Singleton
class ProfileGatewayAdapter @Inject constructor(
    sessionReader: SessionReader,
    private val sessionWriter: SessionWriter,
    private val authRepository: AuthRepository
) : ProfileGateway {

    override val profile: Flow<UserProfile> = combine(
        sessionReader.userName,
        sessionReader.userPhone
    ) { name, phone ->
        UserProfile(name = name, phone = phone)
    }

    override suspend fun canEditName(): Boolean =
        runCatching { authRepository.getMyProfile()?.role == "admin" }
            .getOrDefault(false)

    override suspend fun updateName(name: String) {
        authRepository.updateMyName(name).getOrThrow()
    }

    override suspend fun updatePhone(phone: String) {
        sessionWriter.setUserPhone(phone)
    }

    override suspend fun changePassword(
        currentPassword: String,
        newPassword: String
    ): Result<Unit> = authRepository.changePassword(currentPassword, newPassword)
}
