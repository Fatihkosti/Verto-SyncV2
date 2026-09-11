package com.verto.app.feature.party.data

import androidx.room.withTransaction
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.dao.ClientCreditDao
import com.verto.app.data.local.dao.ClientReminderDao
import com.verto.app.data.local.entity.ClientReminderEntity
import com.verto.app.data.remote.PermissionProvider
import com.verto.app.data.sync.SyncManager
import com.verto.app.data.sync.SyncRequestReason
import com.verto.app.data.sync.UnifiedOutboxWriter
import com.verto.app.feature.party.application.model.ClientReminderViewData
import com.verto.app.feature.party.application.model.PartyPermissionsViewData
import com.verto.app.feature.party.application.port.PartyPresentationCommand
import com.verto.app.feature.party.application.port.PartyPresentationQuery
import com.verto.app.money.Money
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PartyPresentationAdapter @Inject constructor(
    private val reminderDao: ClientReminderDao,
    private val creditDao: ClientCreditDao,
    private val syncManager: SyncManager,
    private val permissionProvider: PermissionProvider,
    private val database: AppDatabase,
    private val sessionReader: SessionReader,
    private val outbox: UnifiedOutboxWriter,
) : PartyPresentationQuery, PartyPresentationCommand {

    override fun getRemindersForClient(clientId: String): Flow<List<ClientReminderViewData>> =
        reminderDao.getRemindersForClient(clientId).map { reminders -> reminders.map { it.toViewData() } }

    override fun getNetCreditForClient(clientId: String): Flow<Double> =
        creditDao.getNetCreditMinorForClient(clientId).map { Money.ofMinor(it).toLegacyDouble() }

    override val permissions: Flow<PartyPermissionsViewData?> =
        permissionProvider.permissions.map { permissions ->
            permissions?.let {
                PartyPermissionsViewData(
                    salesCreate = it.salesCreate,
                    clientsEdit = it.clientsEdit,
                    clientsDelete = it.clientsDelete,
                    clientsAddPayment = it.clientsAddPayment,
                    suppliersAddPayment = it.suppliersAddPayment
                )
            }
        }

    override suspend fun canEditClientsNow(): Boolean =
        permissionProvider.canNow { it.clientsEdit }

    override suspend fun insertReminder(reminder: ClientReminderViewData) {
        val organizationId = trustedOrganizationId()
        database.withTransaction {
            reminderDao.insertReminder(reminder.toEntity())
            outbox.enqueue(
                organizationId, "REMINDER", reminder.id, "UPSERT",
                reminderPayload(reminder),
            )
        }
    }

    override suspend fun deleteReminder(reminder: ClientReminderViewData) {
        val organizationId = trustedOrganizationId()
        database.withTransaction {
            reminderDao.deleteReminder(reminder.toEntity())
            outbox.enqueue(
                organizationId, "REMINDER", reminder.id, "DELETE",
                mapOf("clientId" to reminder.clientId, "deleted" to true),
            )
        }
    }

    override suspend fun markReminderDone(id: String) {
        val organizationId = trustedOrganizationId()
        database.withTransaction {
            reminderDao.markAsDone(id)
            outbox.enqueue(
                organizationId, "REMINDER", id, "UPSERT",
                mapOf("id" to id, "isDone" to true),
            )
        }
    }

    private suspend fun trustedOrganizationId(): String = sessionReader.snapshot().organization.id.trim().also {
        require(it.isNotBlank()) { "FAIL_ORG_SCOPE" }
    }

    private fun reminderPayload(reminder: ClientReminderViewData) = mapOf(
        "clientId" to reminder.clientId,
        "createdAt" to reminder.createdAt,
        "isDone" to reminder.isDone,
        "note" to reminder.note,
        "reminderAt" to reminder.reminderAt,
    )

    override suspend fun fullSync(): Result<Unit> = syncManager.request(SyncRequestReason.OUTBOX_WRITE).map { Unit }
}

private fun ClientReminderEntity.toViewData() = ClientReminderViewData(
    id = id,
    clientId = clientId,
    note = note,
    reminderAt = reminderAt,
    isDone = isDone,
    createdAt = createdAt
)

private fun ClientReminderViewData.toEntity() = ClientReminderEntity(
    id = id,
    clientId = clientId,
    note = note,
    reminderAt = reminderAt,
    isDone = isDone,
    createdAt = createdAt
)
