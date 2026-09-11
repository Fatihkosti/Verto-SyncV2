package com.verto.app.ui.screens.expenses

import com.verto.app.utils.ErrorHumanizer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.feature.expenses.application.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

internal sealed interface FinancialOperationState {
    data object Idle:FinancialOperationState; data object Submitting:FinancialOperationState; data object Success:FinancialOperationState
    data class Error(val message:String):FinancialOperationState
}

@HiltViewModel
class ExpensesViewModel @Inject constructor(
    private val gateway: ExpensesGateway
) : ViewModel() {
    val isAdmin=gateway.isAdmin
    val permissions=gateway.access.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)
    private val _pendingWithdrawalRequestsCount=MutableStateFlow(0)
    val pendingWithdrawalRequestsCount:StateFlow<Int> = _pendingWithdrawalRequestsCount
    val cashBalance=gateway.observeCashBalance().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),0.0)
    val movements=gateway.observeRecentMovements(50).stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    private val monthRange=monthRangeFor(System.currentTimeMillis())
    val expenses=gateway.observeExpenses(monthRange.first,monthRange.second).stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val totalExpenses=gateway.observeTotalExpenses(monthRange.first,monthRange.second).stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),0.0)
    private val _activeTab=MutableStateFlow(0); val activeTab:StateFlow<Int> = _activeTab.asStateFlow()
    private val _operationState=MutableStateFlow<FinancialOperationState>(FinancialOperationState.Idle)
    internal val operationState:StateFlow<FinancialOperationState> = _operationState.asStateFlow()
    val recentPurchaseInvoices=gateway.observeRecentPurchaseInvoices(System.currentTimeMillis()-30L*86_400_000L,System.currentTimeMillis()+86_400_000L)
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    init {
        gateway.refreshRole(); refreshPendingWithdrawalRequestsCount()
        viewModelScope.launch { gateway.withdrawalRequestsChanged.collect { refreshPendingWithdrawalRequestsCount() } }
    }
    fun selectTab(index:Int){_activeTab.value=index}
    fun addExpense(category:String,item:String,amount:Double,note:String,linkedInvoiceId:String?=null)=financial {
        gateway.addExpense(AddExpenseCommand(category,item,amount,note,linkedInvoiceId))
    }
    fun deleteExpense(expense:ExpenseItem)=financial { gateway.deleteExpense(expense.id) }
    fun manualAdjust(amount:Double,isAdd:Boolean,note:String)=financial { gateway.adjustCash(CashAdjustmentCommand(amount,isAdd,note)) }
    internal fun clearOperationState(){_operationState.value=FinancialOperationState.Idle}
    private fun financial(block:suspend()->Unit)=viewModelScope.launch {
        _operationState.value=FinancialOperationState.Submitting
        runCatching { block() }.onSuccess { _operationState.value=FinancialOperationState.Success }
            .onFailure { _operationState.value=FinancialOperationState.Error(ErrorHumanizer.humanize(it, "إكمال العملية المالية")) }
    }
    fun refreshPendingWithdrawalRequestsCount()=viewModelScope.launch {
        gateway.pendingWithdrawalRequestsCount().onSuccess { _pendingWithdrawalRequestsCount.value=it }
    }
}

internal fun monthRangeFor(nowMillis:Long,timeZone:java.util.TimeZone=java.util.TimeZone.getDefault()):Pair<Long,Long> {
    val calendar=Calendar.getInstance(timeZone).apply {
        timeInMillis=nowMillis; set(Calendar.DAY_OF_MONTH,1); set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0)
    }
    val start=calendar.timeInMillis; calendar.add(Calendar.MONTH,1); return start to calendar.timeInMillis
}
