package com.verto.app.ui.screens.commissionreport

import dagger.hilt.android.qualifiers.ApplicationContext

import android.content.Context

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.feature.commission.application.MarketerCommissionReportItem
import com.verto.app.feature.commission.application.CommissionReportGateway
import com.verto.app.utils.ErrorHumanizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MarketerReportUiState(
    val isLoading: Boolean = true,
    val report: MarketerCommissionReportItem? = null,
    val error: String? = null,
    val isSending: Boolean = false,
    val sendResult: String? = null
)

/**
 * شاشة تقرير عمولات مسوّق داخل Verto (معاينة الأدمن). تبني التقرير من بيانات الأهلية السيرفرية
 * (مصدر الحقيقة) عبر الباني المشترك، وتتيح إرسال إشعار للمسوّق يفتح شاشة تقريره في AutoDrive.
 */
@HiltViewModel
class MarketerCommissionReportViewModel @Inject constructor(
    private val commissionReportGateway: CommissionReportGateway,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val role: StateFlow<String?> = commissionReportGateway.role

    private val _state = MutableStateFlow(MarketerReportUiState())
    val state: StateFlow<MarketerReportUiState> = _state.asStateFlow()

    private var loadedKey: String? = null
    private var clientName: String = ""

    fun load(clientId: String, from: Long? = null, to: Long? = null) {
        val key = "$clientId|$from|$to"
        if (loadedKey == key && _state.value.report != null) return
        loadedKey = key
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            commissionReportGateway.buildMarketerCommissionReport(clientId, from, to)
                .onSuccess { report ->
                    clientName = report.marketerName
                    _state.update { it.copy(isLoading = false, report = report, error = null) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = ErrorHumanizer.humanize(e, "تحميل التقرير")) }
                }
        }
    }

    fun sendReport(clientId: String) {
        if (_state.value.isSending) return
        _state.update { it.copy(isSending = true) }
        viewModelScope.launch {
            commissionReportGateway.sendAdminReminder(
                clientId = clientId,
                message = context.getString(com.verto.feature.commission.R.string.commission_v298_report_ready_reminder),
                navRoute = "commission_report"
            )
                .onSuccess { _state.update { it.copy(isSending = false, sendResult = "تم إرسال التقرير إلى $clientName") } }
                .onFailure { _ -> _state.update { it.copy(isSending = false, sendResult = "تعذّر إرسال التقرير، تحقق من الاتصال وحاول مرة أخرى") } }
        }
    }

    fun consumeSendResult() = _state.update { it.copy(sendResult = null) }
}
