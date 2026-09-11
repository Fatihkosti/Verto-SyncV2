package com.verto.app.feature.party.data

import androidx.room.withTransaction
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.PartyIdentityEntity
import com.verto.app.data.local.entity.CustomerProfileEntity
import com.verto.app.data.local.entity.PartyRoleEntity
import com.verto.app.feature.party.domain.model.CustomerProfile
import com.verto.app.feature.party.domain.model.Party
import com.verto.app.feature.party.domain.repository.PartyRemoteMirrorGateway
import com.verto.app.utils.SearchTextNormalizer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomPartyRemoteMirrorAdapter @Inject constructor(
    private val database: AppDatabase,
    private val sessionReader: SessionReader,
) : PartyRemoteMirrorGateway {
    override suspend fun mergeRemoteCustomer(party: Party, profile: CustomerProfile) {
        require(profile.partyId == party.id) { "party_profile_identity_mismatch" }
        val organizationId = sessionReader.snapshot().organization.id.trim().also {
            require(it.isNotBlank()) { "FAIL_ORG_SCOPE" }
        }
        val clientDao = database.clientDao()
        val roleDao = database.partyRoleDao()
        val local = clientDao.getClientByIdSync(organizationId, party.id)

        database.withTransaction {
            if (local == null) {
                // Transitional storage only: normalized Party V2 is authoritative for role/profile.
                clientDao.insertClientsFromRemote(
                    listOf(
                        PartyIdentityEntity(
                            id = party.id,
                            name = party.name,
                            phone = party.phone,
                            address = party.address,
                            generalNote = party.generalNote,
                            createdAt = party.createdAt,
                            createdBy = party.createdBy,
                            isDirty = false,
                        )
                    )
                )
            } else if (!local.isDirty) {
                clientDao.updatePartyIdentityFromRemote(
                    id = party.id,
                    name = party.name,
                    phone = party.phone,
                    nameSearch = SearchTextNormalizer.text(party.name),
                    phoneSearch = SearchTextNormalizer.phone(party.phone),
                    address = party.address,
                    generalNote = party.generalNote,
                    createdBy = party.createdBy,
                )
            }

            val existingRole = roleDao.getRole(party.id, organizationId, "CUSTOMER")
            if (existingRole?.dirty != true && !roleDao.hasActivePartyMutation("ROLE", party.id)) {
                val now = System.currentTimeMillis()
                roleDao.upsertRoleFromRemote(
                    PartyRoleEntity(
                        id = existingRole?.id ?: UUID.nameUUIDFromBytes("$organizationId:${party.id}:CUSTOMER".toByteArray()).toString(),
                        partyId = party.id,
                        organizationId = organizationId,
                        role = "CUSTOMER",
                        status = "ACTIVE",
                        createdAt = existingRole?.createdAt ?: party.createdAt,
                        updatedAt = now,
                        syncRevision = existingRole?.syncRevision ?: 0,
                        dirty = false,
                    )
                )
            }

            val existingProfile = roleDao.getCustomerProfileSync(organizationId, party.id)
            if (existingProfile?.dirty != true && !roleDao.hasActivePartyMutation("CUSTOMER_PROFILE", party.id)) {
                roleDao.saveCustomerProfile(
                    CustomerProfileEntity(
                        organizationId = organizationId,
                        partyId = party.id,
                        segment = profile.segment.name,
                        ageYears = profile.ageYears,
                        purchaseContactName = profile.purchaseContactName,
                        businessActivity = profile.businessActivity,
                        workplaceName = profile.workplaceName,
                        shopName = profile.shopName,
                        workshopName = profile.workshopName,
                        vehicleModels = profile.vehicleModels.joinToString("||"),
                        workshopWorkerCount = profile.workshopWorkerCount,
                        updatedAt = System.currentTimeMillis(),
                        syncRevision = existingProfile?.syncRevision ?: 0,
                        dirty = false,
                    )
                )
            }
        }
    }
}
