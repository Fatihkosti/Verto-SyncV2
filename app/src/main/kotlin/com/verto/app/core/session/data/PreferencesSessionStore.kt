package com.verto.app.core.session.data

import com.verto.app.core.session.domain.DrawerSectionStateStore
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.core.session.domain.SessionWriter
import com.verto.app.core.session.model.CurrentOrganization
import com.verto.app.core.session.model.CurrentUser
import com.verto.app.core.session.model.SessionState
import com.verto.app.utils.PreferencesManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** تنفيذ DataStore المخفي خلف عقدي القراءة والكتابة. */
@Singleton
class PreferencesSessionStore @Inject constructor(
    private val preferences: PreferencesManager
) : SessionReader, SessionWriter, DrawerSectionStateStore {

    override val userId: Flow<String> = preferences.userId
    override val userName: Flow<String> = preferences.userName
    override val userPhone: Flow<String> = preferences.userPhone
    override val role: Flow<String> = preferences.userRole
    override val permissionsJson: Flow<String> = preferences.userPermissionsJson
    override val organizationId: Flow<String> = preferences.lastOrgId
    override val lastOpenSection: Flow<String> = preferences.lastDrawerOpenSection

    override val currentUser: Flow<CurrentUser> = combine(
        userId,
        userName,
        userPhone
    ) { id, name, phone ->
        CurrentUser(id = id, name = name, phone = phone)
    }

    override val currentOrganization: Flow<CurrentOrganization> =
        organizationId.map(::CurrentOrganization)

    override suspend fun snapshot(): SessionState = SessionState(
        user = currentUser.first(),
        organization = currentOrganization.first(),
        role = role.first(),
        permissionsJson = permissionsJson.first()
    )

    override suspend fun setUserId(value: String) = preferences.setUserId(value)
    override suspend fun setUserName(value: String) = preferences.setUserName(value)
    override suspend fun setUserPhone(value: String) = preferences.setUserPhone(value)
    override suspend fun setOrganizationId(value: String) = preferences.setLastOrgId(value)
    override suspend fun setRole(value: String) = preferences.setUserRole(value)
    override suspend fun setPermissionsJson(value: String) = preferences.setUserPermissionsJson(value)
    override suspend fun setLastOpenSection(value: String) =
        preferences.setLastDrawerOpenSection(value)

    override suspend fun clearAccess() {
        preferences.setUserRole("")
        preferences.setUserPermissionsJson("")
    }
}
