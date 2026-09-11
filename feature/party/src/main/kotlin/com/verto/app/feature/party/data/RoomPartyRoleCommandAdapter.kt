package com.verto.app.feature.party.data

import androidx.room.withTransaction
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.CustomerProfileEntity
import com.verto.app.data.local.entity.PartyRoleAuditEntity
import com.verto.app.data.local.entity.PartyRoleEntity
import com.verto.app.data.local.entity.SupplierProfileEntity
import com.verto.app.feature.party.application.PartyRoleCommandPort
import com.verto.app.feature.party.domain.model.PartyRole
import com.verto.app.feature.party.domain.model.PartyRoleRecord
import com.verto.app.feature.party.domain.model.RoleStatus
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomPartyRoleCommandAdapter @Inject constructor(
    private val database: AppDatabase,
) : PartyRoleCommandPort {
    private val dao get() = database.partyRoleDao()

    override suspend fun attach(partyId: String, organizationId: String, role: PartyRole, actorId: String): PartyRoleRecord {
        require(organizationId.isNotBlank()) { "FAIL_ORG_SCOPE" }
        return database.withTransaction {
            val now = System.currentTimeMillis()
            val existing = dao.getRole(partyId, organizationId, role.name)
            if (existing?.status == RoleStatus.ACTIVE.name) return@withTransaction existing.toDomain()
            if (existing == null) {
                dao.insertRole(PartyRoleEntity(roleId(partyId, organizationId, role), partyId, organizationId, role.name, createdAt = now, updatedAt = now))
            } else if (existing.status == RoleStatus.ARCHIVED.name) {
                dao.setRoleStatus(partyId, organizationId, role.name, RoleStatus.ACTIVE.name, now, null, null, null)
            }
            when (role) {
                PartyRole.CUSTOMER -> dao.insertCustomerProfile(CustomerProfileEntity(organizationId = organizationId, partyId = partyId, segment = "INDIVIDUAL", updatedAt = now))
                PartyRole.SUPPLIER -> dao.insertSupplierProfile(SupplierProfileEntity(organizationId = organizationId, partyId = partyId, scope = "UNKNOWN", updatedAt = now))
            }
            dao.insertAudit(PartyRoleAuditEntity(UUID.randomUUID().toString(), partyId, organizationId, role.name, "ATTACH", actorId, "", now))
            database.clientDao().markClientDirty(partyId)
            checkNotNull(dao.getRole(partyId, organizationId, role.name)).toDomain()
        }
    }

    override suspend fun setStatus(
        partyId: String, organizationId: String, role: PartyRole, status: RoleStatus, actorId: String, reason: String,
    ): PartyRoleRecord {
        require(organizationId.isNotBlank()) { "FAIL_ORG_SCOPE" }
        return database.withTransaction {
        require(reason.isNotBlank()) { "Archive/restore reason is required" }
        val now = System.currentTimeMillis()
        val existing = requireNotNull(dao.getRole(partyId, organizationId, role.name)) { "Party role does not exist" }
        val changed = dao.setRoleStatus(
            partyId, organizationId, role.name, status.name, now,
            now.takeIf { status == RoleStatus.ARCHIVED }, actorId.takeIf { status == RoleStatus.ARCHIVED }, reason,
        )
        require(changed == 1) { "Party role does not exist" }
        dao.insertAudit(PartyRoleAuditEntity(UUID.randomUUID().toString(), partyId, organizationId, role.name, status.name, actorId, reason, now))
        database.clientDao().markClientDirty(partyId)
        checkNotNull(dao.getRole(partyId, organizationId, role.name)).toDomain()
        }
    }

    private fun roleId(partyId: String, organizationId: String, role: PartyRole) = "$organizationId:$partyId:${role.name}"
    private fun PartyRoleEntity.toDomain() = PartyRoleRecord(partyId, organizationId, PartyRole.valueOf(role), RoleStatus.valueOf(status))
}
