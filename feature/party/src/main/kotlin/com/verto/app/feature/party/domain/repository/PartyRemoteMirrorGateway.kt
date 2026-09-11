package com.verto.app.feature.party.domain.repository

import com.verto.app.feature.party.domain.model.CustomerProfile
import com.verto.app.feature.party.domain.model.Party

/** Party V2 remote mirror command. No legacy clientTypes/profile-overloaded fields are accepted. */
interface PartyRemoteMirrorGateway {
    suspend fun mergeRemoteCustomer(party: Party, profile: CustomerProfile)
}
