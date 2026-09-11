package com.verto.feature.dashboard.api

import kotlinx.coroutines.flow.Flow

/** Educational-only content. It intentionally has no urgency, destination, or commercial action fields. */
enum class EducationalContentCategory {
    INVENTORY,
    SALES,
    PAYMENTS,
    CUSTOMERS,
    OPERATIONS,
}

enum class EducationalTargetType {
    ALL,
    ROLE,
    USER,
}

data class EducationalContentTarget(
    val type: EducationalTargetType,
    val value: String = ALL_VALUE,
) {
    init {
        require(value.isNotBlank()) { "educational target value must not be blank" }
        if (type == EducationalTargetType.ALL) {
            require(value == ALL_VALUE) { "ALL target must use the canonical value" }
        }
    }

    companion object {
        const val ALL_VALUE: String = "*"
        val Everyone = EducationalContentTarget(EducationalTargetType.ALL)
    }
}

data class EducationalContent(
    val id: String,
    val title: String,
    val summary: String,
    val fullContent: String,
    val category: EducationalContentCategory,
    val organizationId: String = "",
    val isActive: Boolean = true,
    val targets: Set<EducationalContentTarget> = setOf(EducationalContentTarget.Everyone),
    val createdByUserId: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isDirty: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "educational content id must not be blank" }
        require(title.isNotBlank()) { "educational content title must not be blank" }
        require(summary.isNotBlank()) { "educational content summary must not be blank" }
        require(fullContent.isNotBlank()) { "educational content body must not be blank" }
        require(targets.isNotEmpty()) { "educational content must have an audience" }
    }
}

data class EducationalContentDraft(
    val title: String,
    val summary: String,
    val fullContent: String,
    val category: EducationalContentCategory,
    val isActive: Boolean = true,
    val targets: Set<EducationalContentTarget>,
) {
    init {
        require(title.isNotBlank()) { "educational content title must not be blank" }
        require(summary.isNotBlank()) { "educational content summary must not be blank" }
        require(fullContent.isNotBlank()) { "educational content body must not be blank" }
        require(targets.isNotEmpty()) { "educational content must have an audience" }
    }
}

data class EducationalAudienceContext(
    val organizationId: String,
    val userId: String,
    val role: String,
) {
    init {
        require(organizationId.isNotBlank()) { "organization id is required" }
        require(userId.isNotBlank()) { "user id is required" }
    }
}

data class EducationalAudienceOption(
    val id: String,
    val label: String,
)

/** Adapter owned by the application shell. Dashboard never depends on employee persistence or network DTOs. */
interface EducationalAudienceDirectory {
    suspend fun roles(): List<EducationalAudienceOption>
    suspend fun users(): List<EducationalAudienceOption>
}

/** Local-first source of truth. Every write is tenant-scoped and guarded again inside the repository. */
interface EducationalContentRepository {
    fun observeVisible(context: EducationalAudienceContext): Flow<List<EducationalContent>>
    fun observeOrganizationTopics(organizationId: String): Flow<List<EducationalContent>>
    suspend fun getById(organizationId: String, topicId: String): EducationalContent?
    suspend fun create(draft: EducationalContentDraft): EducationalContent
    suspend fun update(topicId: String, draft: EducationalContentDraft): EducationalContent
    suspend fun delete(topicId: String)
    suspend fun setActive(topicId: String, active: Boolean): EducationalContent
}
