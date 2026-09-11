package com.verto.app.feature.payment.application.model

import java.util.UUID

enum class ClientType(val label: String) {
    INDIVIDUAL("عميل"), COMPANY("شركة"), INSTITUTION("مؤسسة"), WORKSHOP_OWNER("صاحب ورشة"),
    MARKETER("مسوق"), TRADER("تاجر"), DISTRIBUTOR("موزع"), COMPETITOR("منافس"),
    SUPPLIER("مورد"), GLOBAL_SUPPLIER("مورد عالمي"), WHOLESALE_TRADER("تاجر جملة"),
    CAR_OWNER("صاحب سيارة"), MECHANIC("ميكانيكي"), SHOP_OWNER("صاحب محل"), OTHER("أخرى")
}

data class ClientItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String,
    val address: String = "",
    val workplace: String = "",
    val generalNote: String = "",
    val carType: String = "",
    val bankAccount: String = "",
    val specialty: String = "",
    val secondaryPhones: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val createdBy: String = "",
    val isDirty: Boolean = true,
    val hasCustomerRole: Boolean = true,
    val hasSupplierRole: Boolean = false,
    val customerSegment: String? = null,
    val supplierScope: String? = null,
)

fun String?.customerSegmentType(): ClientType =
    this?.let { runCatching { ClientType.valueOf(it.trim().uppercase()) }.getOrNull() } ?: ClientType.OTHER
fun String?.customerSegmentLabel(): String = customerSegmentType().label
fun String?.isCustomerSegment(type: ClientType): Boolean = customerSegmentType() == type
@Deprecated("Compatibility only; active pickers must use ClientItem.hasSupplierRole")

data class InventoryItemView(
    val id: String = UUID.randomUUID().toString(),
    val partNumber: String = "",
    val name: String,
    val barcode: String = "",
    val unitId: String? = null,
    val linkedUnitItemId: String? = null,
    val isUnitItem: Boolean = false,
    val quantityPerUnit: Double = 0.0,
    val isService: Boolean = false,
    val buyPrice: Double = 0.0,
    val sellPrice: Double = 0.0,
    val quantity: Int = 0,
    val minQuantity: Int = 5,
    val location: String = "",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDirty: Boolean = true,
)

enum class InvoiceStatus { CLOSED_CASH, CLOSED_CREDIT }
enum class InvoiceCategory(val label: String) { SALE("بيع"), PURCHASE("مشتريات") }
enum class InvoiceType(val label: String) { GOODS("فاتورة") }

data class InvoiceViewData(
    val id: String = UUID.randomUUID().toString(),
    val invoiceNumber: Int,
    val clientId: String,
    val type: InvoiceType = InvoiceType.GOODS,
    val category: InvoiceCategory = InvoiceCategory.SALE,
    val description: String,
    val totalAmount: Double,
    val transactionCurrencyCode: String = "",
    val functionalCurrencyCode: String = "",
    val invoiceExchangeRateSnapshot: String = "",
    val functionalAmountAtRecognitionMinor: Long = 0L,
    val legacyCurrencyStatus: String = "REVIEW_REQUIRED",
    val createdAt: Long = System.currentTimeMillis(),
    val dueDate: Long,
    val notifyDaysBefore: String = "1,3,7",
    val notifyRepeatDays: Int = 3,
    val notificationsEnabled: Boolean = true,
    val notes: String = "",
    val isOwedToMe: Boolean = true,
    val imageUri: String = "",
    val status: InvoiceStatus = InvoiceStatus.CLOSED_CASH,
    val discount: Double = 0.0,
    val commission: Double = 0.0,
    val commissionBeneficiaryClientId: String? = null,
    val commissionSource: String = "NONE",
    val shipmentId: String? = null,
    val purchaseScope: PaymentPurchaseScope = PaymentPurchaseScope.LOCAL,
    val createdBy: String = "",
    val voided: Boolean = false,
    val isDirty: Boolean = true,
)

data class InvoiceLineView(
    val id: String = UUID.randomUUID().toString(),
    val invoiceId: String,
    val itemName: String,
    val itemCategory: String = "",
    val quantity: Int = 1,
    val buyPrice: Double = 0.0,
    val sellPrice: Double = 0.0,
    val totalPrice: Double = 0.0,
    val description: String = "",
    val isOwedToMe: Boolean = true,
    val inventoryItemId: String = "",
    val adjustedPurchasePrice: Double = 0.0,
    val isDirty: Boolean = true,
)

data class PaymentInvoiceSummaryViewData(
    val invoice: InvoiceViewData,
    val totalPaid: Double,
    val remaining: Double,
)

data class InvoiceEditViewData(
    val invoice: InvoiceViewData,
    val items: List<InvoiceLineView>,
    val totalPaid: Double = 0.0,
    val dueInstallments: List<PaymentDueInstallmentDraft> = emptyList(),
    val deniedMessage: String? = null,
)

data class InvoiceItemData(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val quantity: String = "",
    val sellPrice: String = "",
    val buyPrice: String = "",
    val itemCategory: String = "",
    val inventoryItemId: String = "",
)

enum class PaymentMode { CASH, CREDIT }

data class InvoiceSaveResult(
    val invoiceId: String,
    val newInventoryItemsCreated: Int = 0,
    val inventoryItemsUpdated: Int = 0,
)

data class VehicleSuggestion(
    val organizationId: String,
    val clientId: String,
    val remoteVehicleId: String,
    val name: String,
    val vehicleType: String,
    val plateNumber: String,
    val updatedAt: Long,
)

data class CompanyMaintenanceImageData(
    val imageId: String,
    val localUri: String,
    val mimeType: String,
    val byteSize: Long,
    val sortOrder: Int,
)

data class CompanyMaintenanceData(
    val recordId: String,
    val officialVehicleOrganizationId: String? = null,
    val officialVehicleId: String? = null,
    val vehicleName: String = "",
    val vehicleType: String = "",
    val plateNumber: String = "",
    val driverOrDelegate: String = "",
    val notes: String = "",
    val images: List<CompanyMaintenanceImageData> = emptyList(),
    val createdAt: Long,
) {
    fun hasOfficialVehicle(): Boolean = !officialVehicleId.isNullOrBlank()
    fun hasContent(): Boolean = hasOfficialVehicle() || vehicleName.isNotBlank() || vehicleType.isNotBlank() ||
        plateNumber.isNotBlank() || driverOrDelegate.isNotBlank() || notes.isNotBlank() || images.isNotEmpty()
}

data class InvoiceVehicleSuggestionsQuery(
    val organizationId: String,
    val clientId: String,
    val searchTerm: String = "",
    val limit: Int = 20,
)

enum class PaymentPurchaseScope { LOCAL, INTERNATIONAL }

data class PaymentDueInstallmentDraft(
    val amount: Double,
    val dueDate: Long,
)

data class PaymentSaveInvoiceCommand(
    val existingInvoiceId: String?,
    val clientId: String,
    val items: List<InvoiceItemData>,
    val paymentMode: PaymentMode,
    val dueDate: Long,
    val dueInstallments: List<PaymentDueInstallmentDraft> = emptyList(),
    val notes: String,
    val isSale: Boolean,
    val originalCreatedAt: Long?,
    val originalInvoiceNumber: Int?,
    val initialPayment: Double,
    val shipmentId: String?,
    val purchaseScope: PaymentPurchaseScope = PaymentPurchaseScope.LOCAL,
    val discount: Double = 0.0,
    val commission: Double,
    val commissionBeneficiaryClientId: String? = null,
    val commissionSource: String = "NONE",
    val exchangeRate: Double,
    val transactionCurrencyCode: String = "",
    val organizationId: String,
    val companyClient: Boolean,
    val maintenance: CompanyMaintenanceData?,
    val writeId: String,
)
