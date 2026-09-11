package com.verto.app.feature.party.application

import com.verto.app.feature.party.domain.model.CustomerSegment
import com.verto.app.feature.party.domain.model.PartyRole
import com.verto.app.feature.party.domain.model.SupplierScope

data class LegacyPartyMapping(
    val roles: Set<PartyRole>,
    val customerSegment: CustomerSegment?,
    val supplierScope: SupplierScope?,
    val unknownTokens: Set<String>,
)

/**
 * Compatibility-only translator for pre-Party-V2 classification strings.
 * Customer output is always one of the six approved v388 segments.
 * COMPETITOR is no longer a customer segment: it means CUSTOMER + SUPPLIER.
 */
object LegacyPartyRoleMapper {
    private val legacyCustomerSegments: Map<String, CustomerSegment> = mapOf(
        "INDIVIDUAL" to CustomerSegment.INDIVIDUAL,
        "CAR_OWNER" to CustomerSegment.INDIVIDUAL,
        "OTHER" to CustomerSegment.INDIVIDUAL,
        "COMPANY" to CustomerSegment.COMPANY,
        "INSTITUTION" to CustomerSegment.COMPANY,
        "WORKSHOP_OWNER" to CustomerSegment.WORKSHOP_OWNER,
        "MECHANIC" to CustomerSegment.WORKSHOP_OWNER,
        "MARKETER" to CustomerSegment.MARKETER,
        "TRADER" to CustomerSegment.TRADER,
        "SHOP_OWNER" to CustomerSegment.TRADER,
        "COMPETITOR" to CustomerSegment.TRADER,
        "DISTRIBUTOR" to CustomerSegment.DISTRIBUTOR,
        "WHOLESALE_TRADER" to CustomerSegment.DISTRIBUTOR,
    )
    private val known = legacyCustomerSegments.keys + setOf("SUPPLIER", "GLOBAL_SUPPLIER")

    fun map(raw: String): LegacyPartyMapping {
        val tokens = raw.split(',').map(String::trim).filter(String::isNotBlank).map(String::uppercase).toSet()
        val competitor = "COMPETITOR" in tokens
        val supplier = competitor || "SUPPLIER" in tokens || "GLOBAL_SUPPLIER" in tokens
        val customerSegment = tokens.firstNotNullOfOrNull(legacyCustomerSegments::get)
        val customer = customerSegment != null
        return LegacyPartyMapping(
            roles = buildSet {
                if (customer) add(PartyRole.CUSTOMER)
                if (supplier) add(PartyRole.SUPPLIER)
            },
            customerSegment = customerSegment,
            supplierScope = when {
                "GLOBAL_SUPPLIER" in tokens -> SupplierScope.INTERNATIONAL
                "SUPPLIER" in tokens -> SupplierScope.LOCAL
                competitor -> SupplierScope.UNKNOWN
                else -> null
            },
            unknownTokens = tokens - known,
        )
    }
}
