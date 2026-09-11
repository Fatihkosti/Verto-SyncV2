package com.verto.app.feature.party.domain.model

enum class PartyKind { PERSON, ORGANIZATION, UNKNOWN }
enum class PartyRole { CUSTOMER, SUPPLIER }
enum class RoleStatus { ACTIVE, ARCHIVED }
enum class SupplierScope { LOCAL, INTERNATIONAL, UNKNOWN }
enum class CustomerSegment {
    INDIVIDUAL, COMPANY, WORKSHOP_OWNER, MARKETER, TRADER, DISTRIBUTOR,
}

/**
 * One-release compatibility parser for values written by pre-v388 builds.
 * Runtime/customer UI exposes only the six approved groups above.
 */
fun customerSegmentFromStorage(raw: String?): CustomerSegment? = when (raw?.trim()?.uppercase()) {
    "INDIVIDUAL", "CAR_OWNER", "OTHER" -> CustomerSegment.INDIVIDUAL
    "COMPANY", "INSTITUTION" -> CustomerSegment.COMPANY
    "WORKSHOP_OWNER", "MECHANIC" -> CustomerSegment.WORKSHOP_OWNER
    "MARKETER" -> CustomerSegment.MARKETER
    "TRADER", "SHOP_OWNER", "COMPETITOR" -> CustomerSegment.TRADER
    "DISTRIBUTOR", "WHOLESALE_TRADER" -> CustomerSegment.DISTRIBUTOR
    else -> null
}

data class Party(
    val id: String,
    val name: String,
    val phone: String,
    val address: String = "",
    val generalNote: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val createdBy: String = "",
    val kind: PartyKind = PartyKind.UNKNOWN,
)

data class PartyRoleRecord(
    val partyId: String,
    val organizationId: String,
    val role: PartyRole,
    val status: RoleStatus,
)

data class CustomerProfile(
    val partyId: String,
    val segment: CustomerSegment,
    val ageYears: Int? = null,
    val purchaseContactName: String = "",
    val businessActivity: String = "",
    val workplaceName: String = "",
    val shopName: String = "",
    val workshopName: String = "",
    val vehicleModels: List<String> = emptyList(),
    val workshopWorkerCount: Int? = null,
)

/** Customer-only editable fields. No field changes meaning by segment. */
data class CustomerProfileDraft(
    val segment: CustomerSegment,
    val ageYears: Int? = null,
    val purchaseContactName: String = "",
    val businessActivity: String = "",
    val workplaceName: String = "",
    val shopName: String = "",
    val workshopName: String = "",
    val vehicleModels: List<String> = emptyList(),
    val workshopWorkerCount: Int? = null,
)

data class SupplierProfile(
    val partyId: String,
    val scope: SupplierScope,
    val country: String = "",
    val currencyCode: String = "",
    val specialty: String = "",
)
