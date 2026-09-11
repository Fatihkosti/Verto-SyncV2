package com.verto.app.feature.integration.optimal.bridge

import com.verto.app.feature.integration.optimal.domain.port.OptimalPartyMirrorPort
import com.verto.app.feature.integration.optimal.domain.port.OptimalPartyMirrorRecord
import com.verto.app.feature.party.domain.model.CustomerProfile
import com.verto.app.feature.party.domain.model.CustomerSegment
import com.verto.app.feature.party.domain.model.customerSegmentFromStorage
import com.verto.app.feature.party.domain.model.Party
import com.verto.app.feature.party.domain.model.PartyKind
import com.verto.app.feature.party.domain.repository.PartyRemoteMirrorGateway
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OptimalPartyMirrorBridge @Inject constructor(
    private val parties: PartyRemoteMirrorGateway,
) : OptimalPartyMirrorPort {
    override suspend fun mergeRemoteCompany(record: OptimalPartyMirrorRecord) {
        val segment = customerSegmentFromStorage(record.segment) ?: CustomerSegment.COMPANY
        parties.mergeRemoteCustomer(
            party = Party(
                id = record.id,
                name = record.name,
                phone = record.phone,
                address = record.address,
                generalNote = record.generalNote,
                createdAt = record.createdAt,
                createdBy = record.createdBy,
                kind = PartyKind.ORGANIZATION,
            ),
            profile = CustomerProfile(
                partyId = record.id,
                segment = segment,
                ageYears = record.ageYears,
                purchaseContactName = record.purchaseContactName,
                businessActivity = record.businessActivity,
                workplaceName = record.workplaceName,
                shopName = record.shopName,
                workshopName = record.workshopName,
                vehicleModels = record.vehicleModels.split("||").map(String::trim).filter(String::isNotBlank),
                workshopWorkerCount = record.workshopWorkerCount,
            ),
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object OptimalPartyMirrorBridgeModule {
    @Provides @Singleton
    fun provideOptimalPartyMirrorPort(bridge: OptimalPartyMirrorBridge): OptimalPartyMirrorPort = bridge
}
