package com.verto.app.feature.inventory.application.activityevent

import dagger.hilt.android.qualifiers.ApplicationContext

import android.content.Context

import com.verto.app.core.session.domain.SessionReader
import com.verto.feature.dashboard.api.ActivityEvent
import com.verto.feature.dashboard.api.ActivityEventKey
import com.verto.feature.dashboard.api.ActivityEventKind
import com.verto.feature.dashboard.api.ActivityEventProvider
import com.verto.feature.dashboard.api.ActivityEventStatus
import com.verto.feature.dashboard.api.ActivityEventSubject
import com.verto.feature.dashboard.api.ActivityEventValue
import com.verto.feature.dashboard.api.HomeDestination
import com.verto.feature.dashboard.api.HomeDestinationIds
import com.verto.feature.dashboard.api.HomePermissionContext
import com.verto.feature.dashboard.api.HomePermissionKeys
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

enum class InventoryActivityMovement { IN, OUT, ADJUST, RETURN }

data class InventoryActivityRecord(
    val movementId: String,
    val itemId: String,
    val itemName: String,
    val movement: InventoryActivityMovement,
    val quantity: Int,
    val quantityBefore: Int,
    val quantityAfter: Int,
    val note: String,
    val occurredAtEpochMillis: Long,
)

interface InventoryActivityEventSource {
    fun observeSince(organizationId: String, sinceEpochMillis: Long, limit: Int): Flow<List<InventoryActivityRecord>>
}

class InventoryActivityEventProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val source: InventoryActivityEventSource,
    private val sessionReader: SessionReader,
) : ActivityEventProvider {
    override val providerId: String = PROVIDER_ID

    override fun observeActivityEvents(
        context: HomePermissionContext,
        sinceEpochMillis: Long,
    ): Flow<List<ActivityEvent>> = flow {
        val session = sessionReader.snapshot()
        if (context.organizationId != session.organization.id || context.userId != session.user.id ||
            !context.allows(HomePermissionKeys.INVENTORY_VIEW)
        ) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            source.observeSince(context.organizationId, sinceEpochMillis, SOURCE_LIMIT).map { records ->
                records.map { it.toEvent(context.organizationId) }
            },
        )
    }

    private fun InventoryActivityRecord.toEvent(organizationId: String): ActivityEvent {
        val (title, description, status, eventType) = when (movement) {
            InventoryActivityMovement.IN -> EventText(
                "دخول مخزون", note.ifBlank { "أضيفت $quantity وحدة" }, ActivityEventStatus.COMPLETED, "stock_in",
            )
            InventoryActivityMovement.OUT -> EventText(
                "خروج مخزون", note.ifBlank { "صُرفت $quantity وحدة" }, ActivityEventStatus.COMPLETED, "stock_out",
            )
            InventoryActivityMovement.ADJUST -> EventText(
                "تعديل مخزون", note.ifBlank { "الكمية $quantityBefore ← $quantityAfter" }, ActivityEventStatus.UPDATED, "stock_adjusted",
            )
            InventoryActivityMovement.RETURN -> EventText(
                "مرتجع مخزون", note.ifBlank { "أُعيدت $quantity وحدة" }, ActivityEventStatus.COMPLETED, "stock_returned",
            )
        }
        return ActivityEvent(
            eventKey = ActivityEventKey.create(PROVIDER_ID, eventType, movementId),
            organizationId = organizationId,
            title = title,
            description = description,
            occurredAtEpochMillis = occurredAtEpochMillis.coerceAtLeast(0L),
            kind = ActivityEventKind.INVENTORY,
            status = status,
            destination = HomeDestination(
                id = HomeDestinationIds.INVENTORY_ITEM_DETAILS,
                arguments = mapOf("itemId" to itemId),
            ),
            subject = ActivityEventSubject(label = itemName.trim().ifEmpty { context.getString(com.verto.feature.inventory.R.string.inventory_v298_38b4ae76c09d) }, id = itemId),
            value = ActivityEventValue(text = quantity.toString(), label = "الكمية"),
            requiredPermission = HomePermissionKeys.INVENTORY_VIEW,
        )
    }

    private data class EventText(
        val title: String,
        val description: String,
        val status: ActivityEventStatus,
        val eventType: String,
    )

    companion object {
        const val PROVIDER_ID = "inventory.activity"
        const val SOURCE_LIMIT = 100
    }
}
