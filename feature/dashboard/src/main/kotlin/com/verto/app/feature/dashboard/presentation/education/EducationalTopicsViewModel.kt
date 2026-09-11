package com.verto.app.feature.dashboard.presentation.education

import com.verto.app.utils.ErrorHumanizer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.core.session.domain.SessionReader
import com.verto.feature.dashboard.api.EducationalAudienceDirectory
import com.verto.feature.dashboard.api.EducationalAudienceOption
import com.verto.feature.dashboard.api.EducationalContent
import com.verto.feature.dashboard.api.EducationalContentCategory
import com.verto.feature.dashboard.api.EducationalContentDraft
import com.verto.feature.dashboard.api.EducationalContentRepository
import com.verto.feature.dashboard.api.EducationalContentTarget
import com.verto.feature.dashboard.api.EducationalTargetType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class EducationalTopicEditorState(
    val topicId: String? = null,
    val title: String = "",
    val summary: String = "",
    val fullContent: String = "",
    val category: EducationalContentCategory = EducationalContentCategory.OPERATIONS,
    val isActive: Boolean = true,
    val everyone: Boolean = true,
    val roleIds: Set<String> = emptySet(),
    val userIds: Set<String> = emptySet(),
    val isSaving: Boolean = false,
    val error: String? = null,
)

internal data class EducationalTopicsUiState(
    val isAdmin: Boolean = false,
    val topics: List<EducationalContent> = emptyList(),
    val roles: List<EducationalAudienceOption> = emptyList(),
    val users: List<EducationalAudienceOption> = emptyList(),
    val editor: EducationalTopicEditorState? = null,
    val deletingTopicId: String? = null,
    val isLoadingAudience: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
internal class EducationalTopicsViewModel @Inject constructor(
    private val repository: EducationalContentRepository,
    private val audienceDirectory: EducationalAudienceDirectory,
    private val sessionReader: SessionReader,
) : ViewModel() {
    private val transient = MutableStateFlow(EducationalTopicsUiState())

    private val topics = combine(sessionReader.organizationId, sessionReader.role) { org, role ->
        org.trim() to role.trim().lowercase()
    }.flatMapLatest { (organizationId, role) ->
        if (organizationId.isBlank() || role != ADMIN_ROLE) flowOf(emptyList())
        else repository.observeOrganizationTopics(organizationId)
    }

    val uiState: StateFlow<EducationalTopicsUiState> = combine(
        transient,
        topics,
        sessionReader.role,
    ) { local, visibleTopics, role ->
        local.copy(
            isAdmin = role.trim().lowercase() == ADMIN_ROLE,
            topics = visibleTopics,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        EducationalTopicsUiState(),
    )

    init {
        loadAudience()
    }

    fun startCreate() {
        transient.update { it.copy(editor = EducationalTopicEditorState(), message = null) }
    }

    fun startEdit(topic: EducationalContent) {
        transient.update {
            it.copy(
                editor = EducationalTopicEditorState(
                    topicId = topic.id,
                    title = topic.title,
                    summary = topic.summary,
                    fullContent = topic.fullContent,
                    category = topic.category,
                    isActive = topic.isActive,
                    everyone = topic.targets.any { target -> target.type == EducationalTargetType.ALL },
                    roleIds = topic.targets.filter { target -> target.type == EducationalTargetType.ROLE }
                        .mapTo(linkedSetOf(), EducationalContentTarget::value),
                    userIds = topic.targets.filter { target -> target.type == EducationalTargetType.USER }
                        .mapTo(linkedSetOf(), EducationalContentTarget::value),
                ),
                message = null,
            )
        }
    }

    fun closeEditor() = transient.update { it.copy(editor = null) }

    fun updateTitle(value: String) = edit { copy(title = value, error = null) }
    fun updateSummary(value: String) = edit { copy(summary = value, error = null) }
    fun updateFullContent(value: String) = edit { copy(fullContent = value, error = null) }
    fun updateCategory(value: EducationalContentCategory) = edit { copy(category = value, error = null) }
    fun updateActive(value: Boolean) = edit { copy(isActive = value, error = null) }
    fun updateEveryone(value: Boolean) = edit { copy(everyone = value, error = null) }

    fun toggleRole(roleId: String) = edit {
        copy(roleIds = roleIds.toggle(roleId), error = null)
    }

    fun toggleUser(userId: String) = edit {
        copy(userIds = userIds.toggle(userId), error = null)
    }

    fun save(savedMessage: String) {
        val editor = transient.value.editor ?: return
        val validation = validate(editor)
        if (validation != null) {
            edit { copy(error = validation) }
            return
        }
        val targets = buildSet {
            if (editor.everyone) add(EducationalContentTarget.Everyone)
            editor.roleIds.forEach { add(EducationalContentTarget(EducationalTargetType.ROLE, it)) }
            editor.userIds.forEach { add(EducationalContentTarget(EducationalTargetType.USER, it)) }
        }
        val draft = EducationalContentDraft(
            title = editor.title.trim(),
            summary = editor.summary.trim(),
            fullContent = editor.fullContent.trim(),
            category = editor.category,
            isActive = editor.isActive,
            targets = targets,
        )
        edit { copy(isSaving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                if (editor.topicId == null) repository.create(draft)
                else repository.update(editor.topicId, draft)
            }.onSuccess {
                transient.update { it.copy(editor = null, message = savedMessage) }
            }.onFailure { error ->
                edit { copy(isSaving = false, error = ErrorHumanizer.humanize(error, "حفظ الموضوع")) }
            }
        }
    }

    fun requestDelete(topicId: String) = transient.update { it.copy(deletingTopicId = topicId) }
    fun cancelDelete() = transient.update { it.copy(deletingTopicId = null) }

    fun confirmDelete(deletedMessage: String, deleteErrorMessage: String) {
        val topicId = transient.value.deletingTopicId ?: return
        viewModelScope.launch {
            runCatching { repository.delete(topicId) }
                .onSuccess { transient.update { it.copy(deletingTopicId = null, message = deletedMessage) } }
                .onFailure { error ->
                    transient.update {
                        it.copy(deletingTopicId = null, message = ErrorHumanizer.humanize(error, "حذف الموضوع"))
                    }
                }
        }
    }

    fun setActive(topic: EducationalContent, active: Boolean, errorMessage: String) {
        viewModelScope.launch {
            runCatching { repository.setActive(topic.id, active) }
                .onFailure { error ->
                    transient.update { it.copy(message = ErrorHumanizer.humanize(error, "تحديث الموضوع")) }
                }
        }
    }

    fun clearMessage() = transient.update { it.copy(message = null) }

    private fun loadAudience() {
        transient.update { it.copy(isLoadingAudience = true) }
        viewModelScope.launch {
            val roles = runCatching { audienceDirectory.roles() }.getOrDefault(emptyList())
            val users = runCatching { audienceDirectory.users() }.getOrDefault(emptyList())
            transient.update { it.copy(roles = roles, users = users, isLoadingAudience = false) }
        }
    }

    private fun edit(transform: EducationalTopicEditorState.() -> EducationalTopicEditorState) {
        transient.update { state -> state.copy(editor = state.editor?.transform()) }
    }

    private fun validate(editor: EducationalTopicEditorState): String? = when {
        editor.title.isBlank() -> "العنوان مطلوب"
        editor.summary.isBlank() -> "الملخص مطلوب"
        editor.fullContent.isBlank() -> "المحتوى الكامل مطلوب"
        !editor.everyone && editor.roleIds.isEmpty() && editor.userIds.isEmpty() -> "حدد جمهورًا واحدًا على الأقل"
        else -> null
    }

    private fun Set<String>.toggle(value: String): Set<String> =
        if (value in this) this - value else this + value

    private companion object {
        const val ADMIN_ROLE = "admin"
    }
}
