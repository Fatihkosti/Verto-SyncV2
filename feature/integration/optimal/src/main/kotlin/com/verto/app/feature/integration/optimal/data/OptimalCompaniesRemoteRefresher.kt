package com.verto.app.feature.integration.optimal.data

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.core.session.domain.SessionWriter
import com.verto.app.data.local.dao.OptimalCompanyLinkDao
import com.verto.app.data.local.entity.OptimalCompanyLinkEntity
import com.verto.app.data.remote.VertoSupabase
import com.verto.app.feature.integration.optimal.domain.port.OptimalPartyMirrorPort
import com.verto.app.feature.integration.optimal.domain.port.OptimalPartyMirrorRecord
import com.verto.app.utils.SupabaseDateParser
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Serializable
private data class OptimalCompanyLinkRemoteDto(
    @SerialName("optimal_company_id") val optimalCompanyId: String,
    @SerialName("verto_client_id") val vertoClientId: String,
    @SerialName("verto_organization_id") val vertoOrganizationId: String,
    @SerialName("linked_at") val linkedAt: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
)

@Serializable
private data class OptimalCompanyPartyRemoteDto(
    @SerialName("organization_id") val organizationId: String,
    @SerialName("party_id") val partyId: String,
    val name: String,
    val phone: String = "",
    val address: String = "",
    @SerialName("general_note") val generalNote: String = "",
    val segment: String = "COMPANY",
    @SerialName("age_years") val ageYears: Int? = null,
    @SerialName("purchase_contact_name") val purchaseContactName: String = "",
    @SerialName("business_activity") val businessActivity: String = "",
    @SerialName("workplace_name") val workplaceName: String = "",
    @SerialName("shop_name") val shopName: String = "",
    @SerialName("workshop_name") val workshopName: String = "",
    @SerialName("vehicle_models") val vehicleModels: String = "",
    @SerialName("workshop_worker_count") val workshopWorkerCount: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
)

/**
 * Refreshes the two remote sources used by the Room-backed Optimal companies screen.
 *
 * Company clients can be created by Optimal while Verto is not running, so relying only on
 * Verto's incremental background pull leaves the screen stale. The server link is also stored in
 * a separate table and must be mirrored locally for the linked/unlinked state to be correct.
 */
data class OptimalCompaniesRefreshSnapshot(
    val organizationId: String,
    val remoteCompanyClientIds: Set<String>,
)

@Singleton
class OptimalCompaniesRemoteRefresher @Inject constructor(
    private val sessionReader: SessionReader,
    private val sessionWriter: SessionWriter,
    private val partyMirror: OptimalPartyMirrorPort,
    private val companyLinkDao: OptimalCompanyLinkDao,
) {
    private val refreshMutex = Mutex()

    suspend fun refresh(): Result<OptimalCompaniesRefreshSnapshot> = runCatching {
        refreshMutex.withLock {
            val localOrganizationId = sessionReader.snapshot().organization.id.trim()
            require(localOrganizationId.isNotEmpty()) { "optimal_company_refresh_session_required" }

            val companyRefresh = refreshCompanyClients(localOrganizationId)
            val organizationId = companyRefresh.organizationId
            if (organizationId != localOrganizationId) {
                sessionWriter.setOrganizationId(organizationId)
            }
            refreshCompanyLinks(organizationId)
            OptimalCompaniesRefreshSnapshot(
                organizationId = organizationId,
                remoteCompanyClientIds = companyRefresh.remoteCompanyClientIds,
            )
        }
    }

    private data class CompanyClientsRefresh(
        val organizationId: String,
        val remoteCompanyClientIds: Set<String>,
    )

    private suspend fun refreshCompanyClients(localOrganizationId: String): CompanyClientsRefresh {
        val remoteCompanies = VertoSupabase.client.postgrest
            .rpc("verto_optimal_company_parties_v2")
            .decodeList<OptimalCompanyPartyRemoteDto>()

        val remoteOrganizationIds = remoteCompanies
            .map { it.organizationId.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        require(remoteOrganizationIds.size <= 1) { "optimal_company_refresh_cross_tenant_response" }
        val organizationId = remoteOrganizationIds.singleOrNull() ?: localOrganizationId
        require(organizationId == localOrganizationId) { "optimal_company_refresh_org_mismatch" }

        remoteCompanies.forEach { dto -> partyMirror.mergeRemoteCompany(dto.toMirrorRecord()) }
        return CompanyClientsRefresh(
            organizationId = organizationId,
            remoteCompanyClientIds = remoteCompanies.mapTo(linkedSetOf()) { it.partyId },
        )
    }

    private suspend fun refreshCompanyLinks(organizationId: String) {
        // optimal_verto_links is intentionally not exposed to authenticated clients. Read it
        // through the tenant-scoped SECURITY DEFINER RPC instead of granting table access.
        val remoteLinks = VertoSupabase.client.postgrest
            .rpc("verto_list_optimal_links")
            .decodeList<OptimalCompanyLinkRemoteDto>()
            .filter {
                it.isActive &&
                    it.vertoOrganizationId == organizationId &&
                    it.vertoClientId.isNotBlank() &&
                    it.optimalCompanyId.isNotBlank()
            }
            .map { dto ->
                OptimalCompanyLinkEntity(
                    organizationId = organizationId,
                    clientId = dto.vertoClientId,
                    optimalCompanyId = dto.optimalCompanyId,
                    linkedAt = SupabaseDateParser.parseOrNow(dto.linkedAt),
                )
            }

        val remoteByClient = remoteLinks.associateBy(OptimalCompanyLinkEntity::clientId)
        val localLinks = companyLinkDao.observeForOrganization(organizationId).first()

        localLinks
            .filter { it.clientId !in remoteByClient }
            .forEach { companyLinkDao.deleteForClient(organizationId, it.clientId) }

        remoteLinks.forEach { remote ->
            val existing = companyLinkDao.get(organizationId, remote.clientId)
            if (existing == null) {
                companyLinkDao.insert(remote)
            } else if (existing != remote) {
                companyLinkDao.update(remote)
            }
        }
    }
}

private fun OptimalCompanyPartyRemoteDto.toMirrorRecord() = OptimalPartyMirrorRecord(
    id = partyId,
    name = name,
    phone = phone,
    address = address,
    generalNote = generalNote,
    segment = segment,
    ageYears = ageYears,
    purchaseContactName = purchaseContactName,
    businessActivity = businessActivity,
    workplaceName = workplaceName,
    shopName = shopName,
    workshopName = workshopName,
    vehicleModels = vehicleModels,
    workshopWorkerCount = workshopWorkerCount,
    createdAt = SupabaseDateParser.parseOrNow(createdAt),
    createdBy = createdBy.orEmpty(),
)
