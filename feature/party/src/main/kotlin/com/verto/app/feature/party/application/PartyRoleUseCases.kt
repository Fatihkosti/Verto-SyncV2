package com.verto.app.feature.party.application

import com.verto.app.feature.party.domain.model.PartyRole
import com.verto.app.feature.party.domain.model.PartyRoleRecord
import com.verto.app.feature.party.domain.model.RoleStatus
import javax.inject.Inject

interface PartyRoleCommandPort {
    suspend fun attach(partyId: String, organizationId: String, role: PartyRole, actorId: String): PartyRoleRecord
    suspend fun setStatus(partyId: String, organizationId: String, role: PartyRole, status: RoleStatus, actorId: String, reason: String): PartyRoleRecord
}

class AttachCustomerRoleToExistingParty @Inject constructor(private val port: PartyRoleCommandPort) {
    suspend operator fun invoke(partyId: String, organizationId: String, actorId: String) =
        port.attach(partyId, organizationId, PartyRole.CUSTOMER, actorId)
}

class AttachSupplierRoleToExistingParty @Inject constructor(private val port: PartyRoleCommandPort) {
    suspend operator fun invoke(partyId: String, organizationId: String, actorId: String) =
        port.attach(partyId, organizationId, PartyRole.SUPPLIER, actorId)
}

class ArchivePartyRole @Inject constructor(private val port: PartyRoleCommandPort) {
    suspend operator fun invoke(partyId: String, organizationId: String, role: PartyRole, actorId: String, reason: String) =
        port.setStatus(partyId, organizationId, role, RoleStatus.ARCHIVED, actorId, reason)
}

class RestorePartyRole @Inject constructor(private val port: PartyRoleCommandPort) {
    suspend operator fun invoke(partyId: String, organizationId: String, role: PartyRole, actorId: String, reason: String) =
        port.setStatus(partyId, organizationId, role, RoleStatus.ACTIVE, actorId, reason)
}
