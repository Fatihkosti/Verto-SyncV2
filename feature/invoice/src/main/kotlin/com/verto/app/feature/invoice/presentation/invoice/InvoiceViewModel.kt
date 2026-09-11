package com.verto.app.feature.invoice.presentation.invoice

import com.verto.app.feature.invoice.application.*
import com.verto.app.feature.invoice.application.InvoicePresentationService
import android.content.Context
import androidx.lifecycle.ViewModel
import com.verto.app.core.error.UserErrorPresentation
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.core.export.domain.DocumentSharePort
import androidx.lifecycle.viewModelScope
import com.verto.app.data.model.EmployeePermissions
import com.verto.app.utils.ErrorHumanizer
import com.verto.app.core.audit.domain.WriteAuditPort
import com.verto.app.core.audit.domain.AuditAction
import com.verto.app.core.audit.domain.AuditTable
import com.verto.app.core.audit.domain.logPermissionDenied
import com.verto.app.feature.invoice.application.VoidInvoiceUseCase
import com.verto.app.feature.invoice.application.CreateInvoiceReturnUseCase
import com.verto.app.feature.invoice.domain.model.CreateInvoiceReturnCommand
import com.verto.app.feature.invoice.domain.model.InvoiceReturnLineRequest
import com.verto.app.feature.invoice.domain.model.InvoiceReturnSettlementMode
import java.util.UUID
import com.verto.app.feature.invoice.domain.repository.InvoiceMessageShareGateway
import com.verto.app.feature.invoice.domain.model.InvoiceVoidPaymentDisposition
import com.verto.app.feature.invoice.domain.model.InvoiceVoidRequest
import com.verto.app.feature.invoice.domain.repository.InvoiceSharePayload
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InvoiceViewModel @Inject constructor(
    private val presentationService: InvoicePresentationService,
    private val sessionReader: SessionReader,
    private val voidInvoice: VoidInvoiceUseCase,
    private val createInvoiceReturn: CreateInvoiceReturnUseCase,
    private val auditLogger: WriteAuditPort,
    private val documentSharePort: DocumentSharePort,
    private val invoiceMessageShareGateway: InvoiceMessageShareGateway,
) : ViewModel() {

    /** دور المستخدم للاستخدامات القديمة في الشاشة؛ إدارة العمولة تعتمد على commission_manage. */
    val isAdmin: StateFlow<Boolean> = presentationService.isAdmin
    val permissions: StateFlow<EmployeePermissions?> = presentationService.permissions

    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError: StateFlow<String?> = _deleteError
    fun clearDeleteError() { _deleteError.value = null }

    private val _invoiceId = MutableStateFlow("")

    val summary = _invoiceId.flatMapLatest { presentationService.observeSummary(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val client = summary.filterNotNull().flatMapLatest { s ->
        presentationService.observeClient(s.invoice.clientId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val commissionBeneficiary = combine(summary, client) { s, buyer ->
        val explicit = s?.invoice?.commissionBeneficiaryClientId?.takeIf { it.isNotBlank() }
        explicit ?: buyer?.takeIf { party ->
            party.customerSegment == ClientType.MARKETER.name || party.customerSegment == ClientType.WORKSHOP_OWNER.name
        }?.id
    }.distinctUntilChanged().flatMapLatest { id ->
        if (id.isNullOrBlank()) flowOf(null) else presentationService.observeClient(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val invoiceItems = _invoiceId.flatMapLatest { presentationService.observeInvoiceItems(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val communicationHistory = _invoiceId.flatMapLatest { presentationService.observeCommunicationHistory(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val orgSettings = presentationService.observeOrganizationSettings()
        .stateIn(viewModelScope, SharingStarted.Eagerly, InvoiceOrgSettings())

    val userPhone = sessionReader.userPhone
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val userName = sessionReader.userName
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val printSettings: StateFlow<InvoicePrintSettings> = presentationService.observePrintSettings()
        .stateIn(viewModelScope, SharingStarted.Eagerly, InvoicePrintSettings())

    fun init(id: String) { _invoiceId.value = id }

    fun saveCommission(newCommission: Double) = viewModelScope.launch {
        val s = summary.value ?: return@launch
        if (!presentationService.canManageCommission()) {
            auditLogger.logPermissionDenied(
                action = "commission_manage:invoice_commission",
                details = "invoiceId=${s.invoice.id}",
                sessionReader = sessionReader
            )
            _paymentError.value = "لا تملك صلاحية إدارة العمولات"
            return@launch
        }
        val buyer = client.value
        val buyerEligible = buyer?.customerSegment in setOf(ClientType.MARKETER.name, ClientType.WORKSHOP_OWNER.name)
        val beneficiaryId = s.invoice.commissionBeneficiaryClientId?.takeIf { it.isNotBlank() }
            ?: buyer?.id?.takeIf { buyerEligible }
        val source = when {
            s.invoice.commissionSource == "REFERRER" && !beneficiaryId.isNullOrBlank() -> "REFERRER"
            buyerEligible && beneficiaryId == buyer?.id -> "BUYER"
            else -> "NONE"
        }
        if (beneficiaryId.isNullOrBlank() || source == "NONE") {
            _paymentError.value = "حدد مسوقاً/ورشة مستفيدة من العمولة"
            return@launch
        }
        runCatching { presentationService.updateCommission(s.invoice.id, newCommission, beneficiaryId, source) }
            .onFailure { _paymentError.value = ErrorHumanizer.humanize(it, "تعديل العمولة") }
    }

    private val _paymentError = MutableStateFlow<String?>(null)
    val paymentError: StateFlow<String?> = _paymentError
    fun clearPaymentError() { _paymentError.value = null }

    private val _paymentStructuredError = MutableStateFlow<UserErrorPresentation?>(null)
    val paymentStructuredError: StateFlow<UserErrorPresentation?> = _paymentStructuredError
    fun clearPaymentStructuredError() { _paymentStructuredError.value = null }

    private val _exportError = MutableStateFlow<String?>(null)
    val exportError: StateFlow<String?> = _exportError
    fun clearExportError() { _exportError.value = null }

    private suspend fun ensureCanExportInvoice(action: String): Boolean {
        val s = summary.value ?: return false
        val allowed = presentationService.canExport(s.invoice.category)
        if (!allowed) {
            auditLogger.logPermissionDenied(
                action = action,
                details = "invoiceId=${s.invoice.id} category=${s.invoice.category}",
                sessionReader = sessionReader
            )
            _exportError.value = "لا تملك صلاحية طباعة/مشاركة الفواتير"
        }
        return allowed
    }

    /**
     * Session 7: عكس الدفعة بدل حذفها — يُنشئ حركة عكسية تعيد الصندوق وتحفظ التاريخ.
     */
    fun reversePaymentEntry(payment: PaymentItem) = viewModelScope.launch {
        when (val result = presentationService.reversePayment(payment.id)) {
            is InvoicePaymentReversalResult.Error -> {
                _paymentStructuredError.value = result.presentation
                return@launch
            }
            InvoicePaymentReversalResult.Success -> Unit
        }
        presentationService.fullSync().onFailure {
            _paymentError.value = "تم التراجع عن الدفعة على هذا الجهاز، وستكتمل المزامنة عند توفر الإنترنت"
        }
    }

    fun createSalesReturn(
        quantities: Map<String, Int>,
        reason: String,
        cashRefund: Boolean,
        onSuccess: () -> Unit = {},
    ) = viewModelScope.launch {
        val s = summary.value ?: return@launch
        val lines = quantities.filterValues { it > 0 }.map { (lineId, quantity) ->
            InvoiceReturnLineRequest(lineId, quantity)
        }
        if (lines.isEmpty()) { _paymentError.value = "حدد كمية مرتجع"; return@launch }
        val organizationId = sessionReader.organizationId.first().trim()
        val command = CreateInvoiceReturnCommand(
            originalInvoiceId = s.invoice.id,
            lines = lines,
            settlementMode = if (cashRefund) InvoiceReturnSettlementMode.CASH_REFUND else InvoiceReturnSettlementMode.CREDIT_BALANCE,
            reason = reason.trim(),
            organizationId = organizationId,
            writeId = UUID.randomUUID().toString(),
            occurredAt = System.currentTimeMillis(),
        )
        createInvoiceReturn(command)
            .onSuccess { presentationService.fullSync(); onSuccess() }
            .onFailure { _paymentError.value = ErrorHumanizer.humanize(it, "تسجيل المرتجع") }
    }

    fun deleteInvoiceById(
        invoiceId: String,
        reason: String,
        refundPayments: Boolean,
        onSuccess: () -> Unit,
    ) = viewModelScope.launch {
        val hasPayments = (summary.value?.totalPaid ?: 0.0) > 0.01
        val request = InvoiceVoidRequest(
            invoiceId = invoiceId,
            reason = reason,
            paymentDisposition = if (hasPayments && refundPayments) {
                InvoiceVoidPaymentDisposition.REFUND_TO_CASH
            } else {
                null
            },
            requestId = "void:$invoiceId",
        )
        runCatching { voidInvoice(request) }
            .onFailure {
                _deleteError.value = ErrorHumanizer.humanize(it, "إلغاء الفاتورة")
                return@launch
            }
        onSuccess()
        presentationService.fullSync().onFailure {
            _deleteError.value = "تم إلغاء الفاتورة على هذا الجهاز، وستكتمل المزامنة عند توفر الإنترنت"
        }
    }

    fun generateAndSharePdf(context: Context) = viewModelScope.launch {
        if (!ensureCanExportInvoice("invoice_export_pdf")) return@launch
        val s  = summary.value      ?: return@launch
        val c  = client.value       ?: return@launch
        val ps = printSettings.value
        val os = orgSettings.value
        val un = userName.value
        val up = userPhone.value
        val it = invoiceItems.value
        val file = presentationService.createInvoicePdf(
            InvoicePdfRequest(
                client = c,
                summary = s,
                orgSettings = os,
                employeeName = un,
                employeePhone = up,
                items = it,
                printSettings = ps,
            )
        )
        documentSharePort.sharePdf(file)
    }

    fun shareViaWhatsApp(context: Context, useWaBusiness: Boolean) = viewModelScope.launch {
        if (!ensureCanExportInvoice("invoice_export_whatsapp")) return@launch
        val s = summary.value ?: return@launch
        val c = client.value  ?: return@launch
        if (c.phone.isBlank()) return@launch
        invoiceMessageShareGateway.shareInvoice(c.toSharePayload(s), useWaBusiness)
        logCommunicationOpened(s.invoice.id, "INVOICE_COMMUNICATION_INVOICE_OPENED", "فتح الفاتورة في واتساب")
    }

    fun shareReminder(useWaBusiness: Boolean) = viewModelScope.launch {
        if (!ensureCanExportInvoice("invoice_export_reminder")) return@launch
        val s = summary.value ?: return@launch
        val c = client.value ?: return@launch
        if (c.phone.isBlank() || s.remaining <= 0.0) return@launch
        invoiceMessageShareGateway.shareReminder(c.toSharePayload(s), useWaBusiness)
        logCommunicationOpened(s.invoice.id, "INVOICE_COMMUNICATION_REMINDER_OPENED", "فتح تذكير الفاتورة في واتساب")
    }

    fun sendThankYou(context: Context, paidAmount: Double, useWaBusiness: Boolean) = viewModelScope.launch {
        if (!ensureCanExportInvoice("invoice_export_thank_you")) return@launch
        val s = summary.value ?: return@launch
        val c = client.value  ?: return@launch
        if (c.phone.isBlank()) return@launch
        val isFullyPaid = s.totalPaid >= s.invoice.totalAmount
        invoiceMessageShareGateway.shareThankYou(c.toSharePayload(s), paidAmount, isFullyPaid, useWaBusiness)
        logCommunicationOpened(s.invoice.id, "INVOICE_COMMUNICATION_THANK_YOU_OPENED", "فتح رسالة شكر في واتساب")
    }

    private suspend fun logCommunicationOpened(invoiceId: String, sourceType: String, summaryText: String) {
        val snapshot = sessionReader.snapshot()
        auditLogger.log(
            action = AuditAction.INSERT,
            table = AuditTable.INVOICE,
            recordId = invoiceId,
            summary = summaryText,
            employeeId = snapshot.user.id,
            employeeName = snapshot.user.name,
            canUndo = false,
            sourceType = sourceType,
            sourceId = invoiceId,
            writeId = "${sourceType.lowercase()}:$invoiceId:${System.currentTimeMillis()}",
        )
    }

    private fun ClientItem.toSharePayload(summary: InvoiceSummary) =
        InvoiceSharePayload(
            clientName = name,
            clientPhone = phone,
            invoiceNumber = summary.invoice.invoiceNumber,
            invoiceTypeLabel = summary.invoice.type.label,
            description = summary.invoice.description,
            totalAmount = summary.invoice.totalAmount,
            totalPaid = summary.totalPaid,
            remaining = summary.remaining,
            dueDate = summary.invoice.dueDate
        )
}
