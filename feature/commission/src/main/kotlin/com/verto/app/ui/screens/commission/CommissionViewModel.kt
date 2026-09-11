package com.verto.app.ui.screens.commission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.feature.commission.application.CommissionAction
import com.verto.app.feature.commission.application.CommissionControllerFactory
import com.verto.app.feature.commission.application.MarketerCommissionReport
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject

@HiltViewModel
class CommissionViewModel @Inject constructor(
    controllerFactory: CommissionControllerFactory
) : ViewModel() {
    private val controller = controllerFactory.create(viewModelScope)

    val role = controller.role
    val permissions = controller.permissions
    val uiState = controller.uiState

    fun setFilter(from: Long, to: Long) = controller.dispatch(CommissionAction.SetFilter(from, to))
    fun clearFilter() = controller.dispatch(CommissionAction.ClearFilter)
    fun showAllPeriods() = controller.dispatch(CommissionAction.ShowAllPeriods)
    fun selectCard(card: CommissionCard) = controller.dispatch(CommissionAction.SelectCard(card))
    fun clearPaymentSuccess() = controller.dispatch(CommissionAction.ClearPaymentSuccess)
    fun clearRequestActionError() = controller.dispatch(CommissionAction.ClearRequestActionError)
    fun loadServerData() = controller.dispatch(CommissionAction.LoadServerData)
    fun loadWithdrawalRequests() = controller.dispatch(CommissionAction.LoadWithdrawalRequests)

    fun withdrawAll(clientId: String, clientName: String, bankName: String, txRef: String) =
        controller.dispatch(CommissionAction.WithdrawAll(clientId, clientName, bankName, txRef))

    fun withdrawSingle(invoice: CommissionInvoiceModel, clientName: String, bankName: String, txRef: String) =
        controller.dispatch(CommissionAction.WithdrawSingle(invoice, clientName, bankName, txRef))

    fun withdrawFreeAmount(
        clientId: String,
        clientName: String,
        amount: Double,
        bankName: String,
        txRef: String
    ) = controller.dispatch(
        CommissionAction.WithdrawFreeAmount(clientId, clientName, amount, bankName, txRef)
    )

    fun approveRequest(id: String, transactionRef: String) =
        controller.dispatch(CommissionAction.ApproveRequest(id, transactionRef))

    fun rejectRequest(id: String, adminNote: String) =
        controller.dispatch(CommissionAction.RejectRequest(id, adminNote))

    fun completeRequest(id: String, adminNote: String) =
        controller.dispatch(CommissionAction.CompleteRequest(id, adminNote))

    fun generateJoinCode(client: MarketingClient) =
        controller.dispatch(CommissionAction.GenerateJoinCode(client))

    fun clearCodeError() = controller.dispatch(CommissionAction.ClearCodeError)
    fun clearGeneratedCode() = controller.dispatch(CommissionAction.ClearGeneratedCode)
    fun deleteCommissionPayment(id: String) =
        controller.dispatch(CommissionAction.DeleteCommissionPayment(id))

    fun sharePdf(file: File) = controller.sharePdf(file)

    fun buildMarketerReport(clientId: String, clientName: String): MarketerCommissionReport =
        controller.buildMarketerReport(clientId, clientName)
}
