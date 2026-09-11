package com.verto.app.feature.party.presentation.addclient

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.verto.app.feature.party.application.ClientTypeCatalog
import com.verto.app.feature.party.application.model.BankAccount
import com.verto.app.feature.party.application.model.ClientType
import com.verto.app.feature.party.application.model.toBankAccountList
import com.verto.app.feature.party.application.model.toSecondaryPhoneList
import com.verto.app.feature.party.domain.model.PartyClient
import com.verto.app.feature.party.domain.model.SupplierProfile
import com.verto.app.feature.party.domain.model.CustomerProfile
import com.verto.app.feature.party.domain.model.SupplierScope

@Stable
internal class AddEditClientFormState {
    var name by mutableStateOf("")
    var primaryPhone by mutableStateOf("")
    var address by mutableStateOf("")
    var workplaceField by mutableStateOf("")
    var specialtyField by mutableStateOf("")
    var carType by mutableStateOf("")
    var extraCars by mutableStateOf(listOf<String>())
    var cars by mutableStateOf(listOf<String>())
    var carCountInput by mutableStateOf("")
    var workerCount by mutableStateOf("")
    var note by mutableStateOf("")
    var bankAccounts by mutableStateOf(listOf<BankAccount>())
    var country by mutableStateOf("")
    var currency by mutableStateOf("")
    var selectedType by mutableStateOf(ClientType.INDIVIDUAL)
    var selectedSupplierType by mutableStateOf(ClientType.SUPPLIER)
    var nameError by mutableStateOf(false)
    var typeExpanded by mutableStateOf(false)
    var supplierTypeExpanded by mutableStateOf(false)

    fun load(
        client: PartyClient,
        isSupplier: Boolean,
        supplierProfile: SupplierProfile? = null,
        customerProfile: CustomerProfile? = null,
    ) {
        name = client.name
        address = client.address
        note = client.generalNote
        bankAccounts = client.bankAccount.toBankAccountList()
        if (isSupplier) {
            selectedSupplierType = when {
                customerProfile?.segment == com.verto.app.feature.party.domain.model.CustomerSegment.DISTRIBUTOR -> ClientType.DISTRIBUTOR
                customerProfile != null && supplierProfile != null -> ClientType.COMPETITOR
                supplierProfile?.scope == SupplierScope.INTERNATIONAL || client.supplierScope == SupplierScope.INTERNATIONAL -> ClientType.GLOBAL_SUPPLIER
                else -> ClientType.SUPPLIER
            }
            primaryPhone = client.phone
            workplaceField = client.workplace
            specialtyField = supplierProfile?.specialty?.takeIf { it.isNotBlank() } ?: client.specialty
            if (selectedSupplierType == ClientType.GLOBAL_SUPPLIER) {
                // Normalized profile is authoritative. Legacy fields are read only as migration fallback.
                country = supplierProfile?.country?.takeIf { it.isNotBlank() } ?: client.carType
                currency = supplierProfile?.currencyCode?.takeIf { it.isNotBlank() } ?: client.secondaryPhones
            }
        } else {
            val type = customerProfile?.segment?.name
                ?.let { segment -> runCatching { ClientType.valueOf(segment) }.getOrNull() }
                ?.takeIf { it in ClientTypeCatalog.customerTypes }
                ?: client.customerSegment?.name
                    ?.let { segment -> runCatching { ClientType.valueOf(segment) }.getOrNull() }
                    ?.takeIf { it in ClientTypeCatalog.customerTypes }
                ?: ClientType.INDIVIDUAL
            selectedType = type
            primaryPhone = client.phone

            if (customerProfile != null) {
                when (type) {
                    ClientType.INDIVIDUAL -> {
                        workplaceField = customerProfile.workplaceName
                        specialtyField = customerProfile.ageYears?.toString().orEmpty()
                        carType = customerProfile.vehicleModels.firstOrNull().orEmpty()
                        extraCars = customerProfile.vehicleModels.drop(1)
                    }
                    ClientType.COMPANY -> {
                        specialtyField = customerProfile.purchaseContactName
                        workplaceField = customerProfile.businessActivity
                        cars = customerProfile.vehicleModels
                        carCountInput = if (cars.isNotEmpty()) cars.size.toString() else ""
                    }
                    ClientType.WORKSHOP_OWNER -> {
                        workplaceField = customerProfile.workshopName
                        specialtyField = customerProfile.businessActivity
                        workerCount = customerProfile.workshopWorkerCount?.toString().orEmpty()
                    }
                    ClientType.TRADER -> {
                        workplaceField = customerProfile.shopName
                        specialtyField = customerProfile.businessActivity
                    }
                    ClientType.DISTRIBUTOR -> workplaceField = customerProfile.businessActivity
                    else -> Unit
                }
            } else {
                // One-release migration fallback only; Room 91->92 materializes these into CustomerProfile.
                workplaceField = client.workplace
                specialtyField = client.specialty
                when (type) {
                    ClientType.INDIVIDUAL -> {
                        carType = client.carType
                        extraCars = client.secondaryPhones.toSecondaryPhoneList()
                    }
                    ClientType.COMPANY -> {
                        cars = client.carType.split(",").filter(String::isNotBlank)
                        carCountInput = if (cars.isNotEmpty()) cars.size.toString() else ""
                    }
                    ClientType.WORKSHOP_OWNER -> workerCount = client.secondaryPhones
                    else -> Unit
                }
            }
        }
    }
}
