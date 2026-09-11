package com.verto.app.feature.party.presentation.addclient

import com.verto.app.feature.party.application.PartyApplicationService
import com.verto.app.feature.party.domain.model.*
import com.verto.app.feature.party.application.model.*

import androidx.lifecycle.ViewModel
import com.verto.app.core.session.domain.SessionReader
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.verto.app.ui.components.*
import com.verto.app.ui.theme.*
import com.verto.app.utils.ErrorHumanizer
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddEditClientViewModel @Inject constructor(
    private val partyService: PartyApplicationService,
    private val sessionReader: SessionReader
) : ViewModel() {

    private val _saved = MutableStateFlow(false)
    val saved = _saved.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun loadClient(
        id: String,
        onLoaded: (PartyClient, SupplierProfile?, CustomerProfile?) -> Unit,
    ) = viewModelScope.launch {
        combine(
            partyService.getClientById(id),
            partyService.observeSupplierProfile(id),
            partyService.observeCustomerProfile(id),
        ) { client, supplierProfile, customerProfile -> Triple(client, supplierProfile, customerProfile) }
            .collect { (client, supplierProfile, customerProfile) ->
                client?.let { onLoaded(it, supplierProfile, customerProfile) }
            }
    }

    fun saveClient(
        id: String?,
        name: String,
        phone: String,
        address: String,
        note: String,
        bankAccount: String,
        supplierScope: SupplierScope? = null,
        supplierCustomerSegment: CustomerSegment? = null,
        customerProfileDraft: CustomerProfileDraft? = null,
        supplierWorkplace: String = "",
        supplierSpecialty: String = "",
        supplierCountry: String = "",
        supplierCurrencyCode: String = "",
    ) = viewModelScope.launch {
        val createdBy = if (id == null) {
            sessionReader.userId.first()
        } else {
            partyService.getClientByIdSync(id)?.createdBy?.takeIf { it.isNotBlank() }
                ?: sessionReader.userId.first()
        }
        val partyId = id ?: UUID.randomUUID().toString()
        val isSupplierRole = supplierScope != null

        // Party V2: identity is stable; role/profile data never enters the identity record.
        val entity = PartyClient(
            id              = partyId,
            name            = name.trim(),
            phone           = phone.trim(),
            address         = address.trim(),
            workplace       = if (isSupplierRole) supplierWorkplace.trim() else "",
            generalNote     = note.trim(),
            carType         = "",
            bankAccount     = bankAccount,
            specialty       = if (isSupplierRole) supplierSpecialty.trim() else "",
            secondaryPhones = "",
            createdBy       = createdBy,
            customerSegment = customerProfileDraft?.segment ?: supplierCustomerSegment,
            supplierScope   = supplierScope,
        )
        val customerProfile = customerProfileDraft?.let { draft ->
            CustomerProfile(
                partyId = partyId,
                segment = draft.segment,
                ageYears = draft.ageYears,
                purchaseContactName = draft.purchaseContactName.trim(),
                businessActivity = draft.businessActivity.trim(),
                workplaceName = draft.workplaceName.trim(),
                shopName = draft.shopName.trim(),
                workshopName = draft.workshopName.trim(),
                vehicleModels = draft.vehicleModels.map(String::trim).filter(String::isNotBlank),
                workshopWorkerCount = draft.workshopWorkerCount?.takeIf { it >= 0 },
            )
        } ?: supplierCustomerSegment?.let { segment ->
            // Supplier-side editing of dual-role parties (e.g. distributor/competitor) must not drop CUSTOMER.
            val existing = id?.let { partyService.observeCustomerProfile(it).first() }
            existing?.copy(partyId = partyId, segment = segment)
                ?: CustomerProfile(partyId = partyId, segment = segment)
        }
        // supplier country/currency no longer overload identity fields; SupplierProfile remains authoritative.
        val supplierProfile = supplierScope?.let { scope ->
            SupplierProfile(
                partyId = partyId,
                scope = scope,
                country = supplierCountry.trim(),
                currencyCode = supplierCurrencyCode.trim().uppercase(),
                specialty = supplierSpecialty.trim(),
            )
        }
        val result = if (id == null) {
            partyService.insertParty(entity, customerProfile, supplierProfile)
        } else {
            partyService.updateParty(entity, customerProfile, supplierProfile)
        }
        result
            .onSuccess { _saved.value = true }
            .onFailure { _error.value = ErrorHumanizer.humanize(it, "حفظ العميل") }
    }

    fun clearError() { _error.value = null }
}
