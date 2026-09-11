package com.verto.app.feature.expenses.application

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class CashMovementKind { SALE_CASH, PURCHASE_CASH, PAYMENT_RECEIVED, PAYMENT_MADE, EXPENSE, MANUAL_ADD, MANUAL_DEDUCT }
data class CashMovementItem(val id:String,val kind:CashMovementKind,val amount:Double,val balanceAfter:Double,val note:String,val createdAt:Long)
data class ExpenseItem(val id:String,val category:String,val item:String,val amount:Double,val note:String,val date:Long)
data class PurchaseInvoiceOption(val id:String,val invoiceNumber:Int,val totalAmount:Double)
data class ExpenseAccess(val cashAdjust:Boolean,val expensesCreate:Boolean,val expensesDelete:Boolean,val commissionManage:Boolean)
data class AddExpenseCommand(val category:String,val item:String,val amount:Double,val note:String,val linkedInvoiceId:String?=null,val date:Long=System.currentTimeMillis())
data class CashAdjustmentCommand(val amount:Double,val isAdd:Boolean,val note:String)
interface ExpensesGateway {
 val isAdmin: StateFlow<Boolean>
 val access: Flow<ExpenseAccess?>
 val withdrawalRequestsChanged: Flow<Unit>
 fun observeCashBalance(): Flow<Double>
 fun observeRecentMovements(limit:Int): Flow<List<CashMovementItem>>
 fun observeExpenses(from:Long,to:Long): Flow<List<ExpenseItem>>
 fun observeTotalExpenses(from:Long,to:Long): Flow<Double>
 fun observeRecentPurchaseInvoices(from:Long,to:Long): Flow<List<PurchaseInvoiceOption>>
 fun refreshRole()
 suspend fun pendingWithdrawalRequestsCount(): Result<Int>
 suspend fun addExpense(command:AddExpenseCommand)
 suspend fun deleteExpense(id:String)
 suspend fun adjustCash(command:CashAdjustmentCommand)
}
