package com.verto.app.feature.party.application.port

import com.verto.app.feature.party.application.model.ClientReminderViewData
import com.verto.app.feature.party.application.model.PartyPermissionsViewData
import kotlinx.coroutines.flow.Flow

interface PartyPresentationQuery {
    fun getRemindersForClient(clientId: String): Flow<List<ClientReminderViewData>>
    fun getNetCreditForClient(clientId: String): Flow<Double>
    val permissions: Flow<PartyPermissionsViewData?>
    suspend fun canEditClientsNow(): Boolean
}

interface PartyPresentationCommand {
    suspend fun insertReminder(reminder: ClientReminderViewData)
    suspend fun deleteReminder(reminder: ClientReminderViewData)
    suspend fun markReminderDone(id: String)
    suspend fun fullSync(): Result<Unit>
}
