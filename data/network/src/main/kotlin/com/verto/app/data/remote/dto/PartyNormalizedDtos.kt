package com.verto.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Normalized Party V2 projections; legacy transport may hydrate them only when rollout falls back. */
@Serializable
data class PartyRoleDto(
    val id: String = "",
    @SerialName("organization_id") val organizationId: String = "",
    @SerialName("party_id") val partyId: String = "",
    val role: String = "",
    val status: String = "ACTIVE",
    @SerialName("server_revision") val serverRevision: Long = 0,
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    @SerialName("archived_at") val archivedAt: String? = null,
    @SerialName("archived_by") val archivedBy: String? = null,
    @SerialName("archive_reason") val archiveReason: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class CustomerProfileDto(
    @SerialName("party_id") val partyId: String = "",
    @SerialName("organization_id") val organizationId: String = "",
    val segment: String = "OTHER",
    @SerialName("age_years") val ageYears: Int? = null,
    @SerialName("purchase_contact_name") val purchaseContactName: String = "",
    @SerialName("business_activity") val businessActivity: String = "",
    @SerialName("workplace_name") val workplaceName: String = "",
    @SerialName("shop_name") val shopName: String = "",
    @SerialName("workshop_name") val workshopName: String = "",
    @SerialName("vehicle_models") val vehicleModels: String = "",
    @SerialName("workshop_worker_count") val workshopWorkerCount: Int? = null,
    @SerialName("server_revision") val serverRevision: Long = 0,
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class SupplierProfileDto(
    @SerialName("party_id") val partyId: String = "",
    @SerialName("organization_id") val organizationId: String = "",
    val scope: String = "UNKNOWN",
    val country: String = "",
    @SerialName("currency_code") val currencyCode: String = "",
    val specialty: String = "",
    @SerialName("server_revision") val serverRevision: Long = 0,
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
)
