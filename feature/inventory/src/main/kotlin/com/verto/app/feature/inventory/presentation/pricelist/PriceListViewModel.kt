package com.verto.app.feature.inventory.presentation.pricelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.core.audit.domain.WriteAuditPort
import com.verto.app.core.audit.domain.logPermissionDenied
import com.verto.app.core.export.domain.DocumentSharePort
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.feature.inventory.application.InventoryApplicationService
import com.verto.app.feature.inventory.application.InventoryPermissionService
import com.verto.app.feature.inventory.application.InventoryPreferencesService
import com.verto.app.feature.inventory.application.PriceListApplicationService
import com.verto.app.feature.inventory.application.model.InventoryItemView
import com.verto.app.feature.inventory.application.model.InventoryPermission
import com.verto.app.feature.inventory.application.model.InventoryPermissionsViewData
import com.verto.app.feature.inventory.application.model.PriceListDraftItemView
import com.verto.app.feature.inventory.application.model.PriceListDraftItemViewData
import com.verto.app.feature.inventory.application.model.PriceListTemplateView
import com.verto.app.feature.inventory.application.model.PriceListTemplateViewData
import com.verto.app.feature.inventory.domain.port.InventoryShopSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class PriceListViewModel @Inject constructor(
    private val priceListAccess: PriceListApplicationService,
    private val inventoryAccess: InventoryApplicationService,
    private val sessionReader: SessionReader,
    private val prefs: InventoryPreferencesService,
    private val permissionProvider: InventoryPermissionService,
    private val auditLogger: WriteAuditPort,
    private val documentSharePort: DocumentSharePort,
) : ViewModel() {

    val orgSettings: StateFlow<InventoryShopSettings> = priceListAccess.organizationSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InventoryShopSettings())
    val userName = sessionReader.userName.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val userPhone = sessionReader.userPhone.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val priceListFont = prefs.priceListFont.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.verto.app.utils.InvoiceFont.CAIRO)
    val priceListFontSize = prefs.priceListFontSize.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 14)
    val permissions: StateFlow<InventoryPermissionsViewData?> = permissionProvider.permissions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val inventoryItems: StateFlow<List<InventoryItemView>> = inventoryAccess.getAllItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val templates: StateFlow<List<PriceListTemplateView>> = sessionReader.organizationId
        .distinctUntilChanged()
        .flatMapLatest { organizationId ->
            if (organizationId.isBlank()) flowOf(emptyList())
            else priceListAccess.observeTemplates(organizationId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val selectedIds = MutableStateFlow<List<String>>(emptyList())
    private val priceOverrides = MutableStateFlow<Map<String, Double>>(emptyMap())

    val draftItems: StateFlow<List<PriceListDraftItemView>> = combine(
        inventoryItems, selectedIds, priceOverrides,
    ) { inventory, ids, overrides ->
        val byId = inventory.associateBy { it.id }
        ids.distinct().mapNotNull { itemId ->
            byId[itemId]?.let { item ->
                PriceListDraftItemViewData(
                    inventoryItemId = item.id,
                    name = item.name,
                    price = overrides[item.id] ?: item.sellPrice,
                    quantity = item.quantity,
                    partNumber = item.partNumber,
                    priceOverridden = overrides.containsKey(item.id),
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val inventoryQueryState = MutableStateFlow("")
    val inventoryQuery = inventoryQueryState.asStateFlow()
    val inventoryResults: StateFlow<List<InventoryItemView>> = inventoryQueryState
        .debounce(120)
        .flatMapLatest { query ->
            if (query.isBlank()) inventoryAccess.getAllItems() else inventoryAccess.searchItems(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    fun clearMessage() { _message.value = null }

    private val _permissionError = MutableStateFlow<String?>(null)
    val permissionError = _permissionError.asStateFlow()
    fun clearPermissionError() { _permissionError.value = null }

    fun setInventoryQuery(value: String) { inventoryQueryState.value = value }

    fun addDirectItem(item: InventoryItemView) {
        if (item.id !in selectedIds.value) selectedIds.value = selectedIds.value + item.id
    }

    fun addTemplate(template: PriceListTemplateView, availableOnly: Boolean) {
        val inventoryById = inventoryItems.value.associateBy { it.id }
        val ids = template.inventoryItemIds.filter { id ->
            val item = inventoryById[id] ?: return@filter false
            !availableOnly || item.quantity > 0
        }
        selectedIds.value = (selectedIds.value + ids).distinct()
        _message.value = if (availableOnly) "تمت إضافة ${ids.size} صنف متوفر" else "تمت إضافة ${ids.size} صنف"
    }

    fun removeDraftItem(itemId: String) {
        selectedIds.value = selectedIds.value.filterNot { it == itemId }
        priceOverrides.value = priceOverrides.value - itemId
    }

    fun updateDraftPrice(itemId: String, price: Double) {
        if (price > 0.0 && price.isFinite()) priceOverrides.value = priceOverrides.value + (itemId to price)
    }

    fun clearDraft() {
        selectedIds.value = emptyList()
        priceOverrides.value = emptyMap()
    }

    fun saveTemplate(
        name: String,
        itemIds: List<String>,
        existing: PriceListTemplateView? = null,
    ) = viewModelScope.launch {
        if (!canEdit("template_save", "templateId=${existing?.id.orEmpty()}")) return@launch
        runCatching {
            priceListAccess.saveTemplate(
                PriceListTemplateViewData(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    name = name.trim(),
                    inventoryItemIds = itemIds.distinct(),
                    isFavorite = existing?.isFavorite ?: false,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                )
            )
        }.onSuccess {
            _message.value = if (existing == null) "تم إنشاء القالب" else "تم تحديث القالب"
        }.onFailure {
            _message.value = when (it.message) {
                "EMPTY_TEMPLATE_NAME" -> "اكتب اسم القالب"
                "EMPTY_TEMPLATE_ITEMS" -> "اختر صنفًا واحدًا على الأقل"
                "DUPLICATE_TEMPLATE_NAME" -> "يوجد قالب بهذا الاسم"
                else -> "تعذر حفظ القالب"
            }
        }
    }

    fun deleteTemplate(template: PriceListTemplateView) = viewModelScope.launch {
        if (!canEdit("template_delete", "templateId=${template.id}")) return@launch
        runCatching { priceListAccess.deleteTemplate(template.id) }
            .onSuccess { _message.value = "تم حذف القالب" }
            .onFailure { _message.value = "تعذر حذف القالب" }
    }

    fun toggleFavorite(template: PriceListTemplateView) = viewModelScope.launch {
        if (!canEdit("template_favorite", "templateId=${template.id}")) return@launch
        runCatching { priceListAccess.saveTemplate(template.copy(isFavorite = !template.isFavorite)) }
            .onFailure { _message.value = "تعذر تحديث القالب" }
    }

    fun sharePdf(file: File) = documentSharePort.sharePdf(file)

    suspend fun canExportPdf(): Boolean {
        if (permissionProvider.canNow(InventoryPermission.EXPORT)) return true
        runCatching { auditLogger.logPermissionDenied("inventory_export:price_list_pdf", "count=${draftItems.value.size}", sessionReader) }
        _permissionError.value = "لا تملك صلاحية تصدير بيانات المخزون"
        return false
    }

    private suspend fun canEdit(action: String, details: String = ""): Boolean {
        if (permissionProvider.canNow(InventoryPermission.PRICE)) return true
        runCatching { auditLogger.logPermissionDenied("inventory_price:price_list_$action", details, sessionReader) }
        _permissionError.value = "لا تملك صلاحية تعديل كشف الأسعار"
        return false
    }
}
