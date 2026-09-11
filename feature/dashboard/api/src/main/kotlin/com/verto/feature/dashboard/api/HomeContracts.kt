package com.verto.feature.dashboard.api

import kotlinx.coroutines.flow.Flow

/** Tenant and user scope supplied to every Home contribution. */
data class HomePermissionContext(
    val organizationId: String,
    val userId: String,
    val grantedPermissions: Set<String>
) {
    init {
        require(organizationId.isNotBlank()) { "organizationId must not be blank" }
        require(userId.isNotBlank()) { "userId must not be blank" }
    }

    fun allows(requiredPermission: String?): Boolean =
        requiredPermission == null || requiredPermission in grantedPermissions
}

/** Navigation-safe destination identifier. The app shell owns its route mapping. */
data class HomeDestination(
    val id: String,
    val arguments: Map<String, String> = emptyMap()
) {
    init {
        require(id.isNotBlank()) { "destination id must not be blank" }
        require(arguments.keys.none(String::isBlank)) { "destination argument keys must not be blank" }
    }
}

data class HomeAction(
    val id: String,
    val label: String,
    val destination: HomeDestination,
    val requiredPermission: String? = null
) {
    init {
        require(id.isNotBlank()) { "action id must not be blank" }
        require(label.isNotBlank()) { "action label must not be blank" }
    }
}

enum class HomeSearchKind {
    PARTY,
    INVOICE,
    INVENTORY_ITEM,
    PAYMENT,
    SCREEN,
    ACTION,
    OTHER
}

data class HomeSearchQuery(
    val text: String,
    val limit: Int = DEFAULT_LIMIT
) {
    init {
        require(text.isNotBlank()) { "search text must not be blank" }
        require(limit in 1..MAX_LIMIT) { "search limit must be between 1 and $MAX_LIMIT" }
    }

    companion object {
        const val DEFAULT_LIMIT: Int = 24
        const val MAX_LIMIT: Int = 100
    }
}

data class HomeSearchResult(
    val key: String,
    val providerId: String = "",
    val title: String,
    val subtitle: String? = null,
    val kind: HomeSearchKind,
    val destination: HomeDestination,
    val actions: List<HomeAction> = emptyList(),
    val requiredPermission: String? = null,
    val updatedAtEpochMillis: Long? = null
) {
    init {
        require(key.isNotBlank()) { "search result key must not be blank" }
        require(title.isNotBlank()) { "search result title must not be blank" }
    }
}

interface HomeSearchProvider {
    val providerId: String

    suspend fun search(
        query: HomeSearchQuery,
        context: HomePermissionContext
    ): List<HomeSearchResult>
}

enum class PendingActionPriority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL
}

enum class PendingActionSection {
    CUSTOMER,
    SUPPLIER,
    INVENTORY,
    OTHER,
}

/**
 * Creates a deterministic, collision-safe key for one concrete pending condition.
 * Providers must use opaque stable source IDs rather than names or other mutable display text.
 */
object PendingActionEventKey {
    private const val VERSION_PREFIX: String = "pa1"

    fun create(
        providerId: String,
        eventType: String,
        sourceId: String,
    ): String {
        val canonicalProvider = canonicalSegment(providerId, "providerId").lowercase()
        val canonicalType = canonicalSegment(eventType, "eventType").lowercase()
        val canonicalSource = canonicalSegment(sourceId, "sourceId")
        return listOf(
            VERSION_PREFIX,
            lengthPrefixed(canonicalProvider),
            lengthPrefixed(canonicalType),
            lengthPrefixed(canonicalSource),
        ).joinToString(":")
    }

    private fun canonicalSegment(value: String, label: String): String =
        value.trim().also { require(it.isNotEmpty()) { "$label must not be blank" } }

    private fun lengthPrefixed(value: String): String = "${value.length}:$value"
}

data class PendingActionDetailField(
    val label: String,
    val value: String,
) {
    init {
        require(label.isNotBlank()) { "pending action detail label must not be blank" }
        require(value.isNotBlank()) { "pending action detail value must not be blank" }
    }
}

data class PendingActionDetailRow(
    val title: String,
    val fields: List<PendingActionDetailField>,
) {
    init {
        require(title.isNotBlank()) { "pending action detail row title must not be blank" }
        require(fields.isNotEmpty()) { "pending action detail row fields must not be empty" }
    }
}

data class PendingActionDetails(
    val title: String,
    val rows: List<PendingActionDetailRow>,
    val copyText: String? = null,
) {
    init {
        require(title.isNotBlank()) { "pending action details title must not be blank" }
        require(rows.isNotEmpty()) { "pending action details rows must not be empty" }
        require(copyText == null || copyText.isNotBlank()) {
            "pending action details copyText must not be blank"
        }
    }
}

data class PendingAction(
    val eventKey: String,
    val title: String,
    val summary: String,
    val occurredAtEpochMillis: Long,
    val priority: PendingActionPriority,
    val destination: HomeDestination,
    val actions: List<HomeAction> = emptyList(),
    val details: PendingActionDetails? = null,
    val section: PendingActionSection = PendingActionSection.OTHER,
    val requiredPermission: String? = null
) {
    init {
        require(eventKey.isNotBlank()) { "pending action eventKey must not be blank" }
        require(title.isNotBlank()) { "pending action title must not be blank" }
        require(summary.isNotBlank()) { "pending action summary must not be blank" }
        require(occurredAtEpochMillis >= 0L) { "pending action timestamp must not be negative" }
        require(actions.map(HomeAction::id).distinct().size == actions.size) {
            "pending action ids must be unique within one event"
        }
    }

    fun isAllowedBy(context: HomePermissionContext): Boolean =
        context.allows(requiredPermission)
}

interface PendingActionProvider {
    val providerId: String

    fun observePendingActions(context: HomePermissionContext): Flow<List<PendingAction>>
}

enum class ActivityEventKind {
    INVOICE,
    PAYMENT,
    INVENTORY,
    PURCHASE,
    PARTY,
    PRICE_CHANGE,
    CANCELLATION,
    MAINTENANCE,
    SHIPMENT,
    OTHER
}

/** Stable, separator-safe identity for one domain activity. */
object ActivityEventKey {
    private const val VERSION_PREFIX: String = "ae1"

    fun create(
        providerId: String,
        eventType: String,
        sourceId: String,
    ): String {
        val canonicalProvider = canonicalSegment(providerId, "providerId").lowercase()
        val canonicalType = canonicalSegment(eventType, "eventType").lowercase()
        val canonicalSource = canonicalSegment(sourceId, "sourceId")
        return listOf(
            VERSION_PREFIX,
            lengthPrefixed(canonicalProvider),
            lengthPrefixed(canonicalType),
            lengthPrefixed(canonicalSource),
        ).joinToString(":")
    }

    private fun canonicalSegment(value: String, label: String): String =
        value.trim().also { require(it.isNotEmpty()) { "$label must not be blank" } }

    private fun lengthPrefixed(value: String): String = "${value.length}:$value"
}

enum class ActivityEventStatus {
    CREATED,
    UPDATED,
    COMPLETED,
    CANCELLED,
    REVERSED,
    OTHER,
}

data class ActivityEventActor(
    val displayName: String,
    val id: String? = null,
) {
    init {
        require(displayName.isNotBlank()) { "activity actor displayName must not be blank" }
        require(id == null || id.isNotBlank()) { "activity actor id must not be blank" }
    }
}

data class ActivityEventSubject(
    val label: String,
    val id: String? = null,
) {
    init {
        require(label.isNotBlank()) { "activity subject label must not be blank" }
        require(id == null || id.isNotBlank()) { "activity subject id must not be blank" }
    }
}

data class ActivityEventValue(
    val text: String,
    val label: String? = null,
) {
    init {
        require(text.isNotBlank()) { "activity value text must not be blank" }
        require(label == null || label.isNotBlank()) { "activity value label must not be blank" }
    }
}

/**
 * Provider-owned, organization-scoped activity read model.
 * The destination is an opaque application-shell identifier, never a raw navigation route.
 */
data class ActivityEvent(
    val eventKey: String,
    val organizationId: String,
    val title: String,
    val description: String,
    val occurredAtEpochMillis: Long,
    val kind: ActivityEventKind,
    val status: ActivityEventStatus,
    val destination: HomeDestination,
    val actor: ActivityEventActor? = null,
    val subject: ActivityEventSubject? = null,
    val value: ActivityEventValue? = null,
    val requiredPermission: String? = null,
) {
    init {
        require(eventKey.isNotBlank()) { "activity eventKey must not be blank" }
        require(organizationId.isNotBlank()) { "activity organizationId must not be blank" }
        require(title.isNotBlank()) { "activity title must not be blank" }
        require(description.isNotBlank()) { "activity description must not be blank" }
        require(occurredAtEpochMillis >= 0L) { "activity timestamp must not be negative" }
    }

    fun isAllowedBy(context: HomePermissionContext): Boolean =
        organizationId == context.organizationId && context.allows(requiredPermission)
}

interface ActivityEventProvider {
    val providerId: String

    fun observeActivityEvents(
        context: HomePermissionContext,
        sinceEpochMillis: Long
    ): Flow<List<ActivityEvent>>
}

enum class QuickActionIcon {
    SALE_INVOICE,
    PURCHASE,
    INTERNATIONAL_PURCHASE,
    CLIENT,
    SUPPLIER,
    INVENTORY_ITEM,
    EXPENSE,
    PRICE_LIST,
    PAYMENT,
    STOCK_COUNT,
    GENERIC,
}

data class QuickAction(
    val id: String,
    val label: String,
    val destination: HomeDestination,
    val icon: QuickActionIcon = QuickActionIcon.GENERIC,
    val defaultOrder: Int = Int.MAX_VALUE,
    val requiredPermission: String? = null,
    val anyOfPermissions: Set<String> = emptySet(),
    val alwaysVisible: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "quick action id must not be blank" }
        require(label.isNotBlank()) { "quick action label must not be blank" }
        require(defaultOrder >= 0) { "quick action defaultOrder must not be negative" }
        require(anyOfPermissions.none(String::isBlank)) {
            "quick action anyOfPermissions must not contain blank values"
        }
    }

    /** Defense-in-depth execution permission check. */
    fun isAllowedBy(context: HomePermissionContext): Boolean =
        context.allows(requiredPermission) &&
            (anyOfPermissions.isEmpty() || anyOfPermissions.any(context.grantedPermissions::contains))

    /** Visibility can be broader than execution permission for discoverable fixed shortcuts. */
    fun isVisibleBy(context: HomePermissionContext): Boolean = alwaysVisible || isAllowedBy(context)
}

interface QuickActionProvider {
    val providerId: String

    /** Stable declarations let the registry reject cross-feature ID collisions at startup. */
    val actionIds: Set<String>
        get() = emptySet()

    fun observeQuickActions(context: HomePermissionContext): Flow<List<QuickAction>>
}

/** Stable tenant/user key for Home-only local state. */
data class HomeStorageScope(
    val organizationId: String,
    val userId: String,
) {
    init {
        require(organizationId.isNotBlank()) { "organizationId must not be blank" }
        require(userId.isNotBlank()) { "userId must not be blank" }
    }
}

data class HomeEventState(
    val eventKey: String,
    val seenAtEpochMillis: Long? = null,
    val snoozedUntilEpochMillis: Long? = null,
    val dismissedAtEpochMillis: Long? = null,
) {
    init {
        require(eventKey.isNotBlank()) { "eventKey must not be blank" }
        listOf(seenAtEpochMillis, snoozedUntilEpochMillis, dismissedAtEpochMillis)
            .filterNotNull()
            .forEach { require(it >= 0L) { "event timestamps must not be negative" } }
        require(
            seenAtEpochMillis != null ||
                snoozedUntilEpochMillis != null ||
                dismissedAtEpochMillis != null,
        ) { "at least one event state timestamp is required" }
    }
}

interface QuickActionOrderStore {
    fun observeOrder(scope: HomeStorageScope): Flow<List<String>>

    suspend fun saveOrder(
        scope: HomeStorageScope,
        orderedActionIds: List<String>,
        updatedAtEpochMillis: Long,
    )

    suspend fun clearOrder(scope: HomeStorageScope)
}

interface HomeEventStateStore {
    fun observeStates(scope: HomeStorageScope): Flow<List<HomeEventState>>

    suspend fun markSeen(
        scope: HomeStorageScope,
        eventKey: String,
        seenAtEpochMillis: Long,
    )

    suspend fun snooze(
        scope: HomeStorageScope,
        eventKey: String,
        snoozedUntilEpochMillis: Long,
    )

    suspend fun dismiss(
        scope: HomeStorageScope,
        eventKey: String,
        dismissedAtEpochMillis: Long,
    )

    /** Clears expired snooze state while preserving valid seen/dismissed history. */
    suspend fun clearExpiredSnoozes(
        scope: HomeStorageScope,
        nowEpochMillis: Long,
    ): Int
}
