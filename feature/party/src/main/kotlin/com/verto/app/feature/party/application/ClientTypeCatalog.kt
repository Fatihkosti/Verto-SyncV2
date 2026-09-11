package com.verto.app.feature.party.application

import com.verto.app.feature.party.application.model.ClientType

/** Party-owned customer classifications. Exactly the six approved groups are exposed to UI. */
object ClientTypeCatalog {
    val customerTypes: List<ClientType> = listOf(
        ClientType.INDIVIDUAL,
        ClientType.COMPANY,
        ClientType.WORKSHOP_OWNER,
        ClientType.MARKETER,
        ClientType.TRADER,
        ClientType.DISTRIBUTOR,
    )
}
