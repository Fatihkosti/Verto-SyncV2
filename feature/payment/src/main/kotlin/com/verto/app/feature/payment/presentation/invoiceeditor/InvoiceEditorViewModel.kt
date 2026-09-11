package com.verto.app.feature.payment.presentation.invoiceeditor

import com.verto.app.core.error.ErrorClassifier
import com.verto.app.core.error.AppFailure
import com.verto.app.feature.payment.application.model.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import com.verto.app.core.session.domain.SessionReader
import androidx.lifecycle.viewModelScope
import com.verto.app.data.model.EmployeePermissions
import com.verto.app.feature.payment.application.PaymentDebtWorkflowService
import com.verto.app.core.audit.domain.WriteAuditPort
import com.verto.app.utils.ErrorHumanizer
import com.verto.app.core.audit.domain.logPermissionDenied
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

private data class MaintenanceSuggestionRequest(
    val clientId: String = "",
    val searchTerm: String = "",
    val enabled: Boolean = false,
)

const val CASH_CLIENT_ID   = "cash_client_main"
const val CASH_SUPPLIER_ID = "cash_supplier_main"

enum class InvoiceSaveUiKind { IDLE, SAVING, SUCCESS, ERROR, CONFLICT, OFFLINE }

data class InvoiceSaveUiState(
    val kind: InvoiceSaveUiKind = InvoiceSaveUiKind.IDLE,
    val message: String = "",
)

@HiltViewModel
class InvoiceEditorViewModel @Inject constructor(
    private val debtWorkflow: PaymentDebtWorkflowService,
    private val sessionReader: SessionReader,
    private val auditLogger: WriteAuditPort,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val isAdmin: StateFlow<Boolean> = debtWorkflow.isAdmin
    val permissions: StateFlow<EmployeePermissions?> = debtWorkflow.permissions

    private val _savedInvoiceId = MutableStateFlow<String?>(null)
    val savedInvoiceId = _savedInvoiceId.asStateFlow()

    private val _saveResult  = MutableStateFlow<InvoiceSaveResult?>(null)
    val saveResult = _saveResult.asStateFlow()

    /** رسالة خطأ من مسار الحفظ (قفل التعديل المالي/التحقق) لعرضها للمستخدم. */
    private val _saveError = MutableStateFlow<String?>(null)
    val saveError = _saveError.asStateFlow()
    private val _saveUiState = MutableStateFlow(InvoiceSaveUiState())
    val saveUiState = _saveUiState.asStateFlow()
    val isSaving: StateFlow<Boolean> = _saveUiState
        .map { it.kind == InvoiceSaveUiKind.SAVING }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _restoredDraft = MutableStateFlow<InvoiceEditorDraftData?>(null)
    val restoredDraft = _restoredDraft.asStateFlow()
    private val _draftLoadComplete = MutableStateFlow(false)
    val draftLoadComplete = _draftLoadComplete.asStateFlow()
    private val _activeDraftKey = MutableStateFlow("")
    val activeDraftKey = _activeDraftKey.asStateFlow()
    private var suppressDraftWrites = false
    private val draftUpdates = MutableSharedFlow<InvoiceEditorDraftData>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    // Stable across retries and process recreation until the save is confirmed successful.
    private companion object {
        const val ACTIVE_INVOICE_WRITE_ID = "active_invoice_write_id"
        const val ACTIVE_DRAFT_KEY = "active_invoice_draft_key"
    }
    private var activeInvoiceWriteId: String?
        get() = savedStateHandle.get<String>(ACTIVE_INVOICE_WRITE_ID)
        set(value) {
            if (value == null) savedStateHandle.remove<String>(ACTIVE_INVOICE_WRITE_ID)
            else savedStateHandle[ACTIVE_INVOICE_WRITE_ID] = value
        }
    private var invoiceSaveInFlight: Boolean = false

    init {
        viewModelScope.launch {
            draftUpdates.debounce(250).collect { draft ->
                if (!suppressDraftWrites && draft.draftKey == _activeDraftKey.value) {
                    val stableWriteId = activeInvoiceWriteId
                        ?: UUID.randomUUID().toString().also { activeInvoiceWriteId = it }
                    debtWorkflow.saveInvoiceDraft(
                        draft.withPersistence(stableWriteId, System.currentTimeMillis())
                    )
                }
            }
        }
    }

    fun clearSaveError() { _saveError.value = null }

    fun initializeInvoiceDraft(
        existingInvoiceId: String?,
        routeClientId: String,
        isInternational: Boolean,
        initialIsSale: Boolean,
    ) = viewModelScope.launch {
        if (_draftLoadComplete.value) return@launch
        val organizationId = sessionReader.organizationId.first().trim()
        if (organizationId.isBlank()) return@launch
        val scope = if (isInternational) "international" else "local"
        val kind = if (initialIsSale && !isInternational) "sale" else "purchase"
        val logical = existingInvoiceId?.let { "edit:$it" }
            ?: "new:$kind:$scope:${routeClientId.ifBlank { "none" }}"
        val key = "$organizationId:$logical"
        savedStateHandle[ACTIVE_DRAFT_KEY] = key
        _activeDraftKey.value = key
        val restored = debtWorkflow.loadInvoiceDraft(key, organizationId)
        _restoredDraft.value = restored
        if (activeInvoiceWriteId.isNullOrBlank()) {
            activeInvoiceWriteId = restored?.writeId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        }
        _draftLoadComplete.value = true
    }

    fun onDraftChanged(draft: InvoiceEditorDraftData) {
        if (_draftLoadComplete.value && !suppressDraftWrites && draft.draftKey == _activeDraftKey.value) {
            val stableWriteId = activeInvoiceWriteId ?: UUID.randomUUID().toString().also { activeInvoiceWriteId = it }
            draftUpdates.tryEmit(draft.withPersistence(stableWriteId))
        }
    }

    fun persistDraftAndThen(draft: InvoiceEditorDraftData, onDone: () -> Unit) = viewModelScope.launch {
        if (!suppressDraftWrites && draft.draftKey == _activeDraftKey.value) {
            val stableWriteId = activeInvoiceWriteId
                ?: UUID.randomUUID().toString().also { activeInvoiceWriteId = it }
            debtWorkflow.saveInvoiceDraft(
                draft.withPersistence(stableWriteId, System.currentTimeMillis())
            )
        }
        onDone()
    }

    fun discardActiveDraft(onDone: (() -> Unit)? = null) = viewModelScope.launch {
        val key = _activeDraftKey.value
        val organizationId = sessionReader.organizationId.first().trim()
        suppressDraftWrites = true
        if (key.isNotBlank() && organizationId.isNotBlank()) debtWorkflow.deleteInvoiceDraft(key, organizationId)
        _restoredDraft.value = null
        onDone?.invoke()
    }

    fun clearSaveResult() {
        val id = _saveResult.value?.invoiceId
        _saveResult.value = null
        _savedInvoiceId.value = id
    }

    private val _editData   = MutableStateFlow<Pair<InvoiceViewData, List<InvoiceLineView>>?>(null)
    val editData = _editData.asStateFlow()
    private val _editDueInstallments = MutableStateFlow<List<PaymentDueInstallmentDraft>>(emptyList())
    val editDueInstallments = _editDueInstallments.asStateFlow()
    private val _editTotalPaid = MutableStateFlow(0.0)
    val editTotalPaid = _editTotalPaid.asStateFlow()

    private val _editPermissionDenied = MutableStateFlow<String?>(null)
    val editPermissionDenied = _editPermissionDenied.asStateFlow()
    fun clearEditPermissionDenied() { _editPermissionDenied.value = null }

    private val _editLoaded = MutableStateFlow(false)

    val inventoryItems = debtWorkflow.observeInventoryItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allClients = debtWorkflow.observeClients()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeOrganizationId = sessionReader.organizationId
        .map(String::trim)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val maintenanceSuggestionRequest = MutableStateFlow(MaintenanceSuggestionRequest())

    val maintenanceVehicleSuggestions = combine(
        activeOrganizationId,
        maintenanceSuggestionRequest,
    ) { organizationId, request -> organizationId to request }
        .distinctUntilChanged()
        .flatMapLatest { (organizationId, request) ->
            if (!request.enabled || organizationId.isBlank() || request.clientId.isBlank()) {
                flowOf(emptyList())
            } else {
                debtWorkflow.observeVehicleSuggestions(
                    InvoiceVehicleSuggestionsQuery(
                        organizationId = organizationId,
                        clientId = request.clientId,
                        searchTerm = request.searchTerm,
                        limit = 8,
                    )
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateMaintenanceVehicleSearch(
        clientId: String,
        searchTerm: String,
        enabled: Boolean,
    ) {
        maintenanceSuggestionRequest.value = MaintenanceSuggestionRequest(
            clientId = clientId.trim(),
            searchTerm = searchTerm.trim(),
            enabled = enabled,
        )
    }

    fun loadInvoiceForEdit(invoiceId: String?) = viewModelScope.launch {
        if (!invoiceId.isNullOrEmpty() && !_editLoaded.value) {
            val edit = debtWorkflow.loadInvoiceForEdit(invoiceId)
            if (edit != null) {
                if (edit.deniedMessage != null) {
                    auditLogger.logPermissionDenied(
                        action = "invoice_edit",
                        details = "invoiceId=$invoiceId category=${edit.invoice.category}",
                        sessionReader = sessionReader
                    )
                    _editPermissionDenied.value = edit.deniedMessage
                    _editLoaded.value = true
                    return@launch
                }
                _editData.value = Pair(edit.invoice, edit.items)
                _editDueInstallments.value = edit.dueInstallments
                _editTotalPaid.value = edit.totalPaid
                _editLoaded.value = true
            }
        }
    }

    fun quickAddClient(
        context: android.content.Context,
        name: String,
        isSupplier: Boolean = false,
        isGlobalSupplier: Boolean = false,
        onDone: (String) -> Unit
    ) = viewModelScope.launch {
        val client = ClientItem(
            name = name.trim(), phone = "",
            createdBy = sessionReader.userId.first(),
            hasCustomerRole = !isSupplier && !isGlobalSupplier,
            hasSupplierRole = isSupplier || isGlobalSupplier,
            customerSegment = if (!isSupplier && !isGlobalSupplier) ClientType.INDIVIDUAL.name else null,
            supplierScope = if (isGlobalSupplier) "INTERNATIONAL" else if (isSupplier) "LOCAL" else null,
        )
        debtWorkflow.insertClient(client)
        onDone(client.id)
        // إشعارات إكمال البيانات متوقفة مؤقتاً حتى تُعاد كأنواع المزامنة لاحقاً.
    }

    fun quickAddInventoryItem(
        context: android.content.Context,
        name: String,
        buyPrice: Double,
        sellPrice: Double,
        quantity: Int,
        onDone: (InventoryItemView) -> Unit
    ) = viewModelScope.launch {
        val item = InventoryItemView(
            name      = name.trim(),
            buyPrice  = buyPrice,
            sellPrice = sellPrice,
            quantity  = quantity
        )
        debtWorkflow.saveInventoryItem(item)
        onDone(item)
        // إشعارات إكمال بيانات المنتج متوقفة مؤقتاً حتى تُعاد كأنواع المزامنة لاحقاً.
    }

    private suspend fun ensureCashClient(isSupplier: Boolean): String {
        val id   = if (isSupplier) CASH_SUPPLIER_ID else CASH_CLIENT_ID
        val name = if (isSupplier) "المورد النقدي"   else "العميل النقدي"
        val existing = debtWorkflow.observeClients().first().find { it.id == id }
        if (existing == null) {
            debtWorkflow.insertClient(
                ClientItem(id = id, name = name, phone = "",
                    hasCustomerRole = !isSupplier, hasSupplierRole = isSupplier,
                    customerSegment = if (isSupplier) null else ClientType.INDIVIDUAL.name,
                    supplierScope = if (isSupplier) "LOCAL" else null)
            )
        }
        return id
    }

    fun saveOrUpdateInvoice(
        existingInvoiceId: String?,
        clientId: String,
        items: List<InvoiceItemData>,
        paymentMode: PaymentMode,
        dueDate: Long,
        dueInstallments: List<PaymentDueInstallmentDraft> = emptyList(),
        notes: String,
        isSale: Boolean = true,
        originalCreatedAt: Long? = null,
        originalInvoiceNumber: Int? = null,
        initialPayment: Double = 0.0,
        shipmentId: String? = null,
        purchaseScope: PaymentPurchaseScope = PaymentPurchaseScope.LOCAL,
        discount: Double = 0.0,
        commission: Double = 0.0,
        commissionBeneficiaryClientId: String? = null,
        commissionSource: String = "NONE",
        exchangeRate: Double = 1.0,
        transactionCurrencyCode: String = "",
        companyClient: Boolean = false,
        maintenance: CompanyMaintenanceData? = null,
    ) = viewModelScope.launch {
        if (invoiceSaveInFlight) return@launch
        invoiceSaveInFlight = true
        _saveUiState.value = InvoiceSaveUiState(InvoiceSaveUiKind.SAVING, "جاري حفظ الفاتورة…")
        _saveError.value = null
        val writeId = activeInvoiceWriteId ?: UUID.randomUUID().toString().also { activeInvoiceWriteId = it }
        try {
            val resolvedClientId = when {
                clientId.isNotBlank()             -> clientId
                paymentMode == PaymentMode.CASH   -> ensureCashClient(isSupplier = !isSale)
                else                              -> clientId
            }
            val organizationId = sessionReader.organizationId.first().trim()
            require(organizationId.isNotEmpty()) { "لا توجد مؤسسة نشطة لحفظ الفاتورة" }
            val result = debtWorkflow.saveInvoice(
                PaymentSaveInvoiceCommand(
                    existingInvoiceId = existingInvoiceId,
                    clientId = resolvedClientId,
                    items = items,
                    paymentMode = paymentMode,
                    dueDate = dueDate,
                    dueInstallments = dueInstallments,
                    notes = notes,
                    isSale = isSale,
                    originalCreatedAt = originalCreatedAt,
                    originalInvoiceNumber = originalInvoiceNumber,
                    initialPayment = initialPayment,
                    shipmentId = shipmentId,
                    purchaseScope = purchaseScope,
                    discount = discount,
                    commission = commission,
                    commissionBeneficiaryClientId = commissionBeneficiaryClientId,
                    commissionSource = commissionSource,
                    exchangeRate = exchangeRate,
                    transactionCurrencyCode = transactionCurrencyCode,
                    organizationId = organizationId,
                    companyClient = companyClient,
                    maintenance = maintenance,
                    writeId = writeId,
                )
            )
            if (activeInvoiceWriteId == writeId) activeInvoiceWriteId = null
            val draftKey = _activeDraftKey.value
            if (draftKey.isNotBlank()) {
                suppressDraftWrites = true
                debtWorkflow.deleteInvoiceDraft(draftKey, organizationId)
            }
            _saveUiState.value = InvoiceSaveUiState(InvoiceSaveUiKind.SUCCESS, "تم حفظ الفاتورة")
            val hasSummary = !isSale && (result.newInventoryItemsCreated > 0 || result.inventoryItemsUpdated > 0)
            if (hasSummary) _saveResult.value = result else _savedInvoiceId.value = result.invoiceId
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            val human = ErrorHumanizer.humanize(error, "حفظ الفاتورة")
            _saveError.value = human
            _saveUiState.value = classifySaveFailure(error, human)
        } finally {
            invoiceSaveInFlight = false
        }
    }

    private fun classifySaveFailure(error: Throwable, human: String): InvoiceSaveUiState =
        when (ErrorClassifier.classify(error)) {
            is AppFailure.Conflict ->
                InvoiceSaveUiState(InvoiceSaveUiKind.CONFLICT, "يوجد تعارض مع بيانات محفوظة. راجع الفاتورة ثم أعد المحاولة.")
            is AppFailure.NetworkUnavailable,
            is AppFailure.ConnectionFailed,
            is AppFailure.Timeout ->
                InvoiceSaveUiState(InvoiceSaveUiKind.OFFLINE, "لا يوجد اتصال متاح. بقيت المسودة محفوظة على الجهاز.")
            else -> InvoiceSaveUiState(InvoiceSaveUiKind.ERROR, human)
        }

    private data class DecisionContext(
        val clientId: String = "",
        val isSale: Boolean = true,
        val paymentMode: PaymentMode = PaymentMode.CASH,
        val inventoryItemId: String = "",
    )

    private val decisionContext = MutableStateFlow(DecisionContext())

    val customerCreditDecision: StateFlow<PaymentCustomerDecision?> = decisionContext
        .map { it.clientId.trim() to (it.isSale && it.paymentMode == PaymentMode.CREDIT) }
        .distinctUntilChanged()
        .flatMapLatest { (clientId, enabled) ->
            if (!enabled || clientId.isBlank()) flowOf(null)
            else debtWorkflow.observeCustomerDecision(clientId).map<PaymentCustomerDecision, PaymentCustomerDecision?> { it }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val supplierRecommendation: StateFlow<PaymentSupplierRecommendation?> = decisionContext
        .map { it.inventoryItemId.trim() to !it.isSale }
        .distinctUntilChanged()
        .flatMapLatest { (inventoryItemId, enabled) ->
            if (!enabled || inventoryItemId.isBlank()) flowOf(null)
            else debtWorkflow.observeSupplierRecommendation(inventoryItemId)
                .map<PaymentSupplierRecommendation, PaymentSupplierRecommendation?> { it }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun updateDecisionContext(
        clientId: String,
        isSale: Boolean,
        paymentMode: PaymentMode,
        inventoryItemId: String,
    ) {
        decisionContext.value = DecisionContext(clientId, isSale, paymentMode, inventoryItemId)
    }

}
