package com.verto.app.ui.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.core.session.domain.DrawerSectionStateStore
import com.verto.app.data.model.EmployeePermissions
import com.verto.app.data.remote.PermissionProvider
import com.verto.app.data.remote.RoleProvider
import com.verto.app.data.repository.OrgSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DrawerOrganizationHeaderState(
    val logoUrl: String? = null,
    val organizationName: String = "",
    val branchLabel: String? = null,
)

data class DrawerNavigationUiState(
    val permissions: EmployeePermissions? = null,
    val role: String? = null,
    val expandedSection: DrawerSection? = null,
    val organizationHeader: DrawerOrganizationHeaderState = DrawerOrganizationHeaderState(),
) {
    val isReady: Boolean get() = !role.isNullOrBlank() && permissions != null
}

@HiltViewModel
class DrawerNavigationViewModel @Inject constructor(
    permissionProvider: PermissionProvider,
    roleProvider: RoleProvider,
    orgSettingsRepository: OrgSettingsRepository,
    private val sectionStateStore: DrawerSectionStateStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val restorationState = savedStateHandle
    private val hasRestoredSection = savedStateHandle.contains(EXPANDED_SECTION_KEY)
    private val expandedSection = MutableStateFlow<DrawerSection?>(null)

    val uiState: StateFlow<DrawerNavigationUiState> = combine(
        permissionProvider.permissions,
        roleProvider.role,
        expandedSection,
        orgSettingsRepository.orgSettings,
    ) { permissions, role, section, org ->
        DrawerNavigationUiState(
            permissions = permissions,
            role = role,
            expandedSection = section,
            organizationHeader = DrawerOrganizationHeaderState(
                logoUrl = org.logoUrl.takeIf { it.isNotBlank() },
                organizationName = org.shopName,
                branchLabel = org.city.trim().takeIf { it.isNotBlank() },
            ),
        )
    }.distinctUntilChanged().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DrawerNavigationUiState(),
    )

    init {
        if (hasRestoredSection) {
            expandedSection.value = DrawerSection.fromStorageKey(savedStateHandle.get<String>(EXPANDED_SECTION_KEY).orEmpty())
        } else {
            viewModelScope.launch {
                val restored = DrawerSection.fromStorageKey(sectionStateStore.lastOpenSection.first())
                expandedSection.value = restored
                restorationState[EXPANDED_SECTION_KEY] = restored?.storageKey.orEmpty()
            }
        }
    }

    fun toggleSection(section: DrawerSection) {
        val next = if (expandedSection.value == section) null else section
        expandedSection.value = next
        restorationState[EXPANDED_SECTION_KEY] = next?.storageKey.orEmpty()
        viewModelScope.launch { sectionStateStore.setLastOpenSection(next?.storageKey.orEmpty()) }
    }

    /** Route has priority only on drawer-open / meaningful route change, never on recomposition. */
    fun alignToCurrentRoute(section: DrawerSection?) {
        if (section != null) expandedSection.value = section
    }

    private companion object { const val EXPANDED_SECTION_KEY = "drawer.expanded_section" }
}
