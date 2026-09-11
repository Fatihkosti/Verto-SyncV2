package com.verto.app.feature.payment.presentation.invoiceeditor

import com.verto.app.feature.payment.application.model.*
import java.util.UUID

internal fun String?.isCompanyCustomerSegment(): Boolean =
    this?.trim()?.equals("COMPANY", ignoreCase = true) == true

internal data class InvoiceMaintenanceFormState(
    val isEnabled: Boolean = false,
    val isExpanded: Boolean = false,
    val ownerOrganizationId: String = "",
    val ownerClientId: String = "",
    val recordId: String = "",
    val createdAt: Long = 0L,
    val vehicleQuery: String = "",
    val selectedOfficialVehicle: VehicleSuggestion? = null,
    val plateNumber: String = "",
    val driverOrDelegate: String = "",
    val notes: String = "",
    val images: List<CompanyMaintenanceImageData> = emptyList(),
) {
    fun bindClient(
        organizationId: String,
        clientId: String,
        hasCompanyType: Boolean,
    ): InvoiceMaintenanceFormState {
        val normalizedOrganizationId = organizationId.trim()
        val normalizedClientId = clientId.trim()
        if (!hasCompanyType || normalizedOrganizationId.isEmpty() || normalizedClientId.isEmpty()) {
            return Empty
        }
        if (
            ownerOrganizationId != normalizedOrganizationId ||
            ownerClientId != normalizedClientId
        ) {
            return InvoiceMaintenanceFormState(
                isEnabled = true,
                ownerOrganizationId = normalizedOrganizationId,
                ownerClientId = normalizedClientId,
                recordId = UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis(),
            )
        }
        return copy(isEnabled = true)
    }

    fun toggleExpanded(): InvoiceMaintenanceFormState =
        if (isEnabled) copy(isExpanded = !isExpanded) else this

    fun updateVehicleQuery(value: String): InvoiceMaintenanceFormState {
        val selected = selectedOfficialVehicle
        if (selected == null || value == selected.displayLabel()) {
            return copy(vehicleQuery = value)
        }
        return copy(
            vehicleQuery = value,
            selectedOfficialVehicle = null,
            plateNumber = plateNumber.takeUnless { it == selected.plateNumber }.orEmpty(),
        )
    }

    fun selectSuggestion(suggestion: VehicleSuggestion): InvoiceMaintenanceFormState {
        require(
            isEnabled &&
                ownerOrganizationId == suggestion.organizationId.trim() &&
                ownerClientId == suggestion.clientId.trim(),
        ) {
            "vehicle suggestion does not belong to the selected organization and company client"
        }
        return copy(
            vehicleQuery = suggestion.displayLabel(),
            selectedOfficialVehicle = suggestion,
            plateNumber = suggestion.plateNumber,
        )
    }

    fun updatePlateNumber(value: String): InvoiceMaintenanceFormState = copy(plateNumber = value)

    fun updateDriverOrDelegate(value: String): InvoiceMaintenanceFormState =
        copy(driverOrDelegate = value)

    fun updateNotes(value: String): InvoiceMaintenanceFormState = copy(notes = value)

    fun addImages(newImages: List<CompanyMaintenanceImageData>): InvoiceMaintenanceFormState {
        if (!isEnabled || newImages.isEmpty()) return this
        val existingUris = images.mapTo(mutableSetOf()) { it.localUri }
        val appended = newImages.filter { image ->
            image.localUri.isNotBlank() && existingUris.add(image.localUri)
        }
        return copy(images = (images + appended).take(MaxImages).reindexed())
    }

    fun removeImage(imageId: String): InvoiceMaintenanceFormState =
        copy(images = images.filterNot { it.imageId == imageId }.reindexed())

    fun toDomainOrNull(): CompanyMaintenanceData? {
        if (!isEnabled) return null
        val official = selectedOfficialVehicle
        val data = CompanyMaintenanceData(
            recordId = recordId.ifBlank { UUID.randomUUID().toString() },
            officialVehicleOrganizationId = official?.organizationId,
            officialVehicleId = official?.remoteVehicleId,
            vehicleName = official?.name?.trim().orEmpty().ifBlank { vehicleQuery.trim() },
            vehicleType = official?.vehicleType?.trim().orEmpty(),
            plateNumber = plateNumber.trim(),
            driverOrDelegate = driverOrDelegate.trim(),
            notes = notes.trim(),
            images = images.reindexed(),
            createdAt = createdAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
        )
        return data.takeIf { it.hasContent() }
    }

    companion object {
        const val MaxImages: Int = 10
        val Empty = InvoiceMaintenanceFormState()
    }
}

internal fun VehicleSuggestion.displayLabel(): String =
    name.ifBlank { vehicleType }.ifBlank { plateNumber }

private fun List<CompanyMaintenanceImageData>.reindexed(): List<CompanyMaintenanceImageData> =
    mapIndexed { index, image -> image.copy(sortOrder = index) }
