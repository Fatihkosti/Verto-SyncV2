package com.verto.app.feature.expenses.bridge

import androidx.room.withTransaction
import com.verto.app.core.audit.domain.WriteAuditPort
import com.verto.app.core.audit.domain.logPermissionDenied
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.dao.ExpenseDao
import com.verto.app.data.local.entity.CashMovementType
import com.verto.app.data.local.entity.CashRegisterMovementEntity
import com.verto.app.data.local.entity.ExpenseEntity
import com.verto.app.data.remote.PermissionProvider
import com.verto.app.data.remote.RoleProvider
import com.verto.app.data.repository.ExpenseRepository
import com.verto.app.data.repository.InvoiceRepository
import com.verto.app.data.repository.WithdrawalRepository
import com.verto.app.data.sync.RealtimeManager
import com.verto.app.data.sync.UnifiedOutboxWriter
import com.verto.app.feature.expenses.application.*
import com.verto.app.feature.inventory.domain.port.InventoryLandedCostAdjustment
import com.verto.app.feature.inventory.domain.port.InventoryLandedCostAdjustmentPort
import com.verto.app.money.Money
import com.verto.app.utils.CashRegisterManager
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

@Singleton

class ExpensesOperationsAdapter @Inject constructor(
    private val expenseRepository: ExpenseRepository, private val expenseDao: ExpenseDao,
    private val cashRegisterManager: CashRegisterManager, private val invoiceRepository: InvoiceRepository,
    private val landedCostAdjustment: InventoryLandedCostAdjustmentPort, private val database: AppDatabase,
    private val outbox: UnifiedOutboxWriter, private val withdrawalRepository: WithdrawalRepository,
    private val realtimeManager: RealtimeManager, private val roleProvider: RoleProvider,
    private val permissionProvider: PermissionProvider, private val auditLogger: WriteAuditPort,
    private val sessionReader: SessionReader
) : ExpensesGateway {
    override val isAdmin: StateFlow<Boolean> = roleProvider.isAdmin
    override val withdrawalRequestsChanged: Flow<Unit> = realtimeManager.withdrawalRequestsChanged
    override val access = permissionProvider.permissions.map { p -> p?.let { ExpenseAccess(it.cashAdjust,it.expensesCreate,it.expensesDelete,it.commissionManage) } }
    override fun observeCashBalance() = cashRegisterManager.getBalance().map { Money.ofMinor(it?.balanceMinor ?: 0L).toLegacyDouble() }
    override fun observeRecentMovements(limit:Int) = cashRegisterManager.getRecentMovements(limit).map { rows -> rows.map(CashRegisterMovementEntity::toItem) }
    override fun observeExpenses(from:Long,to:Long) = expenseRepository.getExpensesByRange(from,to).map { rows -> rows.map(ExpenseEntity::toItem) }
    override fun observeTotalExpenses(from:Long,to:Long) = expenseRepository.getTotalMinorInRange(from,to).map { Money.ofMinor(it).toLegacyDouble() }
    override fun observeRecentPurchaseInvoices(from:Long,to:Long) = invoiceRepository.getPurchaseInvoicesByRange(from,to).map { rows -> rows.map { PurchaseInvoiceOption(it.id,it.invoiceNumber,it.totalAmount) } }
    override fun refreshRole()=roleProvider.refreshAsync()
    override suspend fun pendingWithdrawalRequestsCount()=withdrawalRepository.getPendingWithdrawalRequestsCount()
    override suspend fun addExpense(command: AddExpenseCommand) {
        requireExpenseCreatePermission()
        val money=Money.fromLegacyDouble(command.amount); require(money.isPositive()) { "Expense amount must be greater than zero" }
        val expense=ExpenseEntity(category=command.category,item=command.item,amount=money.toLegacyDouble(),amountMinor=money.amountMinor,note=command.note,date=command.date)
        runLinkedExpenseAtomic(
            transaction={ block -> database.withTransaction { block() } },
            writeExpense={ expenseRepository.insertExpense(expense) },
            linkedEffects={ command.linkedInvoiceId?.trim()?.takeIf(String::isNotEmpty)?.let { distributeLandedCost(it,money.amountMinor,expense.id) } },
        )
    }
    private suspend fun requireExpenseCreatePermission() = enforceExpenseCreatePermission(
        isAllowed = { permissionProvider.canNow { it.expensesCreate } },
        auditDenied = {
            auditLogger.logPermissionDenied(
                "expense_create",
                "source=expenses_gateway reason=permission_denied",
                sessionReader,
            )
        },
    )
    override suspend fun deleteExpense(id:String){expenseDao.getExpenseByIdSync(id)?.let{expenseRepository.deleteExpense(it)}}
    override suspend fun adjustCash(command: CashAdjustmentCommand) {
        val money=Money.fromLegacyDouble(command.amount); require(money.isPositive()) { "Cash adjustment amount must be greater than zero" }
        // Session 335: cash movement writer resolves trusted organization scope.
        val id=UUID.randomUUID().toString()
        runAuthorizedCashAdjustment(
            authorize={ requireCashAdjustPermission(money.amountMinor,command.isAdd) },
            transaction={ block -> database.withTransaction { block() } },
        ) {
            if(command.isAdd) cashRegisterManager.manualAddMinor(money.amountMinor,command.note,id,id)
            else cashRegisterManager.manualDeductMinor(money.amountMinor,command.note,false,id,id)
            // Session 335: CashRegisterManager persists the durable CASH_MOVEMENT command atomically.
            // CASH_REGISTER is server-authoritative and is never client-pushed.
        }
    }
    private suspend fun requireCashAdjustPermission(amountMinor:Long,isAdd:Boolean) {
        if (permissionProvider.canNow { it.cashAdjust }) return
        runCatching { auditLogger.logPermissionDenied("cash_adjust", "amountMinor=$amountMinor isAdd=$isAdd", sessionReader) }
        throw com.verto.app.data.model.PermissionDeniedException("لا تملك صلاحية تعديل الصندوق")
    }
    private suspend fun distributeLandedCost(invoiceId:String, amountMinor:Long, expenseId:String) {
        // Session 335: cash movement writer resolves trusted organization scope.
        val org = sessionReader.snapshot().organization.id.trim().also {
            require(it.isNotEmpty()) { "FAIL_TRUSTED_ORGANIZATION_REQUIRED" }
        }
        val rows=invoiceRepository.getInvoiceItemsSync(invoiceId); require(rows.isNotEmpty()) { "FAIL_LINKED_EXPENSE_INVOICE_MISSING_OR_EMPTY" }
        val targets=rows.filter { it.inventoryItemId.isNotBlank()&&it.quantity>0&&it.buyPriceMinor>0 }.groupBy { it.inventoryItemId }.toSortedMap().map { (id,group) ->
            val qty=group.sumOf { it.quantity.toLong() }; val total=group.sumOf { Math.multiplyExact(it.buyPriceMinor,it.quantity.toLong()) }
            require(qty in 1..Int.MAX_VALUE.toLong() && total>0 && total%qty==0L) { "FAIL_EXACT_ALLOCATION_UNREPRESENTABLE" }
            CostTarget(id,qty.toInt(),total/qty)
        }
        require(targets.isNotEmpty()) { "FAIL_LINKED_EXPENSE_NO_ELIGIBLE_INVENTORY_LINES" }
        val allocations=allocateMinor(amountMinor,targets); check(allocations.sum()==amountMinor) { "FAIL_LANDED_COST_CONSERVATION" }
        val batch="landed-cost:$org:$expenseId:$invoiceId"
        targets.forEachIndexed { index,t ->
            val allocated=allocations[index]; val newMinor=Math.addExact(t.baseMinor,allocated/t.quantity)
            val movement=stableId("$batch:movement:${t.itemId}"); val itemWrite=stableId("$batch:item:${t.itemId}"); val movementWrite=stableId("$batch:movement-intent:${t.itemId}")
            val command=InventoryLandedCostAdjustment(InventoryLandedCostAdjustment.Target(org,t.itemId,Money.ofMinor(newMinor).toLegacyDouble(),movement,"تحديث تكلفة (مصروف مرتبط بفاتورة #$invoiceId)",expenseId),InventoryLandedCostAdjustment.Mutations(itemWrite,movementWrite),InventoryLandedCostAdjustment.Ordering(batch,index*2))
            requireLandedCostApply(command,t.itemId,landedCostAdjustment::apply)
        }
    }
}
internal data class CostTarget(val itemId:String,val quantity:Int,val baseMinor:Long){val weight get()=java.math.BigInteger.valueOf(baseMinor).multiply(java.math.BigInteger.valueOf(quantity.toLong()))}
internal fun allocateMinor(amount:Long,targets:List<CostTarget>):LongArray{
    require(amount>0); val total=targets.fold(java.math.BigInteger.ZERO){a,t->a+t.weight}; require(total.signum()>0)
    val out=LongArray(targets.size); var used=0L; val money=java.math.BigInteger.valueOf(amount)
    targets.forEachIndexed{i,t-> val floor=money.multiply(t.weight).divide(total).longValueExact(); out[i]=(floor/t.quantity)*t.quantity; used=Math.addExact(used,out[i])}
    val remainder=Math.subtractExact(amount,used); if(remainder==0L)return out
    val extra=exactCounts(remainder,targets.map{it.quantity.toLong()}) ?: exactCounts(amount,targets.map{it.quantity.toLong()})?.also{java.util.Arrays.fill(out,0)} ?: error("FAIL_EXACT_ALLOCATION_UNREPRESENTABLE")
    extra.forEachIndexed{i,count->out[i]=Math.addExact(out[i],Math.multiplyExact(count,targets[i].quantity.toLong()))}; check(out.sum()==amount); return out
}
private fun exactCounts(amount:Long,quanta:List<Long>):LongArray?{
    quanta.forEachIndexed{i,q->if(amount%q==0L)return LongArray(quanta.size).also{it[i]=amount/q}}
    if(amount<=1_000_000L)return dpCounts(amount.toInt(),quanta)
    quanta.indices.forEach{a->for(b in a+1 until quanta.size) solvePair(amount,quanta[a],quanta[b])?.let{pair->return LongArray(quanta.size).also{counts->counts[a]=pair.first;counts[b]=pair.second}}}; return null
}
private fun dpCounts(amount:Int,q:List<Long>):LongArray?{
    val prev=IntArray(amount+1){-1};val coin=IntArray(amount+1){-1};prev[0]=0
    for(v in 0..amount)if(prev[v]>=0)q.forEachIndexed{i,x->if(x<=Int.MAX_VALUE&&v+x<=amount&&prev[v+x.toInt()]<0){prev[v+x.toInt()]=v;coin[v+x.toInt()]=i}}
    if(prev[amount]<0)return null;val out=LongArray(q.size);var v=amount;while(v>0){val i=coin[v];out[i]++;v=prev[v]};return out
}
private fun solvePair(amount:Long,a:Long,b:Long):Pair<Long,Long>?{for(x in 0..minOf(amount/a,b)){val rest=amount-x*a;if(rest%b==0L)return x to rest/b};return null}
private fun stableId(value:String)=UUID.nameUUIDFromBytes(value.toByteArray()).toString()
private fun CashRegisterMovementEntity.toItem()=CashMovementItem(id,when(movementType){CashMovementType.SALE_CASH->CashMovementKind.SALE_CASH;CashMovementType.PURCHASE_CASH->CashMovementKind.PURCHASE_CASH;CashMovementType.PAYMENT_RECEIVED->CashMovementKind.PAYMENT_RECEIVED;CashMovementType.PAYMENT_MADE->CashMovementKind.PAYMENT_MADE;CashMovementType.EXPENSE->CashMovementKind.EXPENSE;CashMovementType.MANUAL_ADD->CashMovementKind.MANUAL_ADD;CashMovementType.MANUAL_DEDUCT->CashMovementKind.MANUAL_DEDUCT},Money.ofMinor(amountMinor).toLegacyDouble(),Money.ofMinor(balanceAfterMinor).toLegacyDouble(),note,createdAt)
private fun ExpenseEntity.toItem()=ExpenseItem(id,category,item,Money.ofMinor(amountMinor).toLegacyDouble(),note,date)

internal suspend fun <T> runLinkedExpenseAtomic(
    transaction:suspend(suspend()->T)->T, writeExpense:suspend()->T, linkedEffects:suspend()->Unit,
):T = transaction { val result=writeExpense(); linkedEffects(); result }

internal suspend fun <T> runAuthorizedCashAdjustment(
    authorize:suspend()->Unit, transaction:suspend(suspend()->T)->T, block:suspend()->T,
):T { authorize(); return transaction(block) }

internal suspend fun <T> requireLandedCostApply(command:T,targetId:String,apply:suspend(T)->Boolean) {
    check(apply(command)) { "FAIL_LINKED_EXPENSE_INVENTORY_TARGET_MISSING:$targetId" }
}


internal suspend fun enforceExpenseCreatePermission(
    isAllowed: suspend () -> Boolean,
    auditDenied: suspend () -> Unit,
) {
    if (isAllowed()) return
    try {
        auditDenied()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        // Authorization remains fail-closed even if best-effort audit persistence is unavailable.
    }
    throw com.verto.app.data.model.PermissionDeniedException("لا تملك صلاحية إضافة مصروف")
}
