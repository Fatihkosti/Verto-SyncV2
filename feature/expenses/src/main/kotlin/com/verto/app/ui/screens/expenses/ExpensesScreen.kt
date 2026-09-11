package com.verto.app.ui.screens.expenses

import com.verto.app.ui.components.VertoOutlinedButton
import com.verto.app.ui.components.VertoButton

import com.verto.app.ui.components.VertoEmptyState
import com.verto.app.ui.components.VertoEmptyStateVariant
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.verto.app.feature.expenses.application.CashMovementItem
import com.verto.app.feature.expenses.application.CashMovementKind
import com.verto.app.feature.expenses.application.ExpenseItem
import com.verto.app.ui.theme.*
import com.verto.app.utils.DateUtils
import com.verto.app.utils.WhatsAppUtils
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoTabRow
import com.verto.app.ui.components.VertoTopAppBar

// ─────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    onBack: () -> Unit = {},
    onCommission: () -> Unit = {},
    vm: ExpensesViewModel = hiltViewModel()
) {
    val cashBalance   by vm.cashBalance.collectAsStateWithLifecycle()
    val movements     by vm.movements.collectAsStateWithLifecycle()
    val expenses      by vm.expenses.collectAsStateWithLifecycle()
    val totalExpenses by vm.totalExpenses.collectAsStateWithLifecycle()
    val activeTab     by vm.activeTab.collectAsStateWithLifecycle()
    val isAdmin       by vm.isAdmin.collectAsStateWithLifecycle()
    val permissions   by vm.permissions.collectAsStateWithLifecycle()
    val pendingWithdrawalRequestsCount by vm.pendingWithdrawalRequestsCount.collectAsStateWithLifecycle()
    val operationState by vm.operationState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val canCashAdjust = permissions?.cashAdjust == true
    val canCreateExpense = permissions?.expensesCreate == true
    val canDeleteExpense = permissions?.expensesDelete == true
    val canManageCommission = permissions?.commissionManage == true
    var showAddExpense   by remember { mutableStateOf(false) }
    var showAdjustDialog by remember { mutableStateOf(false) }
    var adjustIsAdd      by remember { mutableStateOf(true) }

    FinancialOperationErrorEffect(operationState, snackbarHostState, vm::clearOperationState)

    Scaffold(
        containerColor = BgDeep,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            VertoTopAppBar(
                title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_ds_6df056fbc97a), color = TextPrimary, fontWeight = FontWeight.Bold) },
                actions = {
                    if (canManageCommission) {
                        BadgedBox(
                            badge = {
                                if (pendingWithdrawalRequestsCount > 0) {
                                    Badge(containerColor = WarningColor) {
                                        Text(pendingWithdrawalRequestsCount.toString(), color = Color.White)
                                    }
                                }
                            }
                        ) {
                            VertoIconButton(onClick = onCommission) {
                                Icon(Icons.Filled.MonetizationOn, contentDescription = androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_ds_4b20c54bf31d), tint = TextPrimary)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep)
            )
        },
        floatingActionButton = {
            if (canCreateExpense) {
                FloatingActionButton(
                    onClick        = { showAddExpense = true },
                    containerColor = MaterialTheme.colorScheme.error,
                    shape          = RoundedCornerShape(ExpensesDimensions.dp16)
                ) {
                    Icon(Icons.Filled.Remove, androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_v298_552e04d88526), tint = TextPrimary)
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            CashBalanceCard(
                balance   = cashBalance,
                canAdjust = canCashAdjust,
                onAdd     = { adjustIsAdd = true; showAdjustDialog = true },
                onDeduct  = { adjustIsAdd = false; showAdjustDialog = true }
            )

            VertoTabRow(
                selectedTabIndex = activeTab,
                containerColor   = BgCard,
                contentColor     = AccentPrimary,
                modifier         = Modifier.padding(horizontal = ExpensesDimensions.dp16, vertical = ExpensesDimensions.dp8).clip(RoundedCornerShape(ExpensesDimensions.dp10))
            ) {
                Tab(selected = activeTab == 0, onClick = { vm.selectTab(0) }, text = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_ds_97bded51e8dc), fontSize = ExpensesTextScale.sp13) })
                Tab(selected = activeTab == 1, onClick = { vm.selectTab(1) }, text = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_ds_faffd946d457), fontSize = ExpensesTextScale.sp13) })
            }

            LazyColumn(
                contentPadding      = PaddingValues(horizontal = ExpensesDimensions.dp16, vertical = ExpensesDimensions.dp8),
                verticalArrangement = Arrangement.spacedBy(ExpensesDimensions.dp8),
                modifier            = Modifier.fillMaxSize()
            ) {
                if (activeTab == 0) {
                    if (movements.isEmpty()) {
                        item { VertoEmptyState(message = androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_ds_07c4c3ae426d), icon = null, variant = VertoEmptyStateVariant.Plain) }
                    } else {
                        items(movements, key = { it.id }) { mov -> MovementRow(movement = mov) }
                    }
                } else {
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text(androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_ds_4e6d12e2bec8), color = TextSecondary, fontSize = ExpensesTextScale.sp13)
                            Text(
                                WhatsAppUtils.formatAmount(totalExpenses) + androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_v298_00cb6d6bbcd6),
                                color      = MaterialTheme.colorScheme.error,
                                fontSize   = ExpensesTextScale.sp16,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (expenses.isEmpty()) {
                        item { VertoEmptyState(message = androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_ds_833616b578ba), icon = null, variant = VertoEmptyStateVariant.Plain) }
                    } else {
                        items(expenses, key = { it.id }) { exp ->
                            ExpenseRow(
                                expense = exp,
                                canDelete = canDeleteExpense,
                                onDelete = { vm.deleteExpense(exp) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddExpense) {
        AddExpenseDialog(
            vm = vm,
            onDismiss = { showAddExpense = false },
            onConfirm = { category, item, amount, note, linkedInvoiceId ->
                vm.addExpense(category, item, amount, note, linkedInvoiceId)
                showAddExpense = false
            }
        )
    }

    if (showAdjustDialog) {
        AdjustCashDialog(
            isAdd     = adjustIsAdd,
            onDismiss = { showAdjustDialog = false },
            onConfirm = { amount, note ->
                vm.manualAdjust(amount, adjustIsAdd, note)
                showAdjustDialog = false
            }
        )
    }
}

// بطاقة رصيد الصندوق
@Composable
private fun CashBalanceCard(balance: Double, canAdjust: Boolean, onAdd: () -> Unit, onDeduct: () -> Unit) {
    val isPositive = balance >= 0
    val balanceColor = if (isPositive) SuccessColor else MaterialTheme.colorScheme.error

    Column(
        Modifier
            .fillMaxWidth().padding(ExpensesDimensions.dp16).clip(RoundedCornerShape(ExpensesDimensions.dp20))
            .background(BgCard).border(ExpensesDimensions.dp1, balanceColor.copy(0.3f), RoundedCornerShape(ExpensesDimensions.dp20))
            .padding(ExpensesDimensions.dp20)
    ) {
        Text(androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_ds_6d9c3cb56058), color = TextMuted, fontSize = ExpensesTextScale.sp13)
        Spacer(Modifier.height(ExpensesDimensions.dp8))
        Text(WhatsAppUtils.formatAmount(balance) + androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_v298_63148af8793f), color = balanceColor, fontSize = ExpensesTextScale.sp32, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(ExpensesDimensions.dp16))
        if (canAdjust) {
            Row(horizontalArrangement = Arrangement.spacedBy(ExpensesDimensions.dp10)) {
                VertoButton(
                    onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = SuccessColor),
                    shape = RoundedCornerShape(ExpensesDimensions.dp10), modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(ExpensesDimensions.dp16))
                    Spacer(Modifier.width(ExpensesDimensions.dp4))
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_add), fontSize = ExpensesTextScale.sp13)
                }
                VertoOutlinedButton(
                    onClick = onDeduct, border = BorderStroke(ExpensesDimensions.dp1, MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(ExpensesDimensions.dp10), modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Remove, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(ExpensesDimensions.dp16))
                    Spacer(Modifier.width(ExpensesDimensions.dp4))
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_ds_c78b4cc71d0d), color = MaterialTheme.colorScheme.error, fontSize = ExpensesTextScale.sp13)
                }
            }
        }
    }
}

// صف حركة الصندوق
@Composable
private fun MovementRow(movement: CashMovementItem) {
    val isPositive = movement.amount >= 0
    val amountColor = if (isPositive) SuccessColor else MaterialTheme.colorScheme.error
    val icon: ImageVector = when (movement.kind) {
        CashMovementKind.SALE_CASH -> Icons.Filled.TrendingUp
        CashMovementKind.PURCHASE_CASH -> Icons.Filled.TrendingDown
        CashMovementKind.PAYMENT_RECEIVED -> Icons.Filled.AccountBalance
        CashMovementKind.PAYMENT_MADE -> Icons.Filled.AccountBalance
        CashMovementKind.EXPENSE -> Icons.Filled.MoneyOff
        CashMovementKind.MANUAL_ADD -> Icons.Filled.AddCircle
        CashMovementKind.MANUAL_DEDUCT -> Icons.Filled.RemoveCircle
    }

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(ExpensesDimensions.dp12)).background(BgCard)
            .border(ExpensesDimensions.dp1, amountColor.copy(0.15f), RoundedCornerShape(ExpensesDimensions.dp12)).padding(ExpensesDimensions.dp12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(ExpensesDimensions.dp40).clip(RoundedCornerShape(ExpensesDimensions.dp10)).background(amountColor.copy(0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = amountColor, modifier = Modifier.size(ExpensesDimensions.dp20))
        }
        Spacer(Modifier.width(ExpensesDimensions.dp10))
        Column(Modifier.weight(1f)) {
            Text(
                when (movement.kind) {
                    CashMovementKind.SALE_CASH -> androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_v298_896003127258)
                    CashMovementKind.PURCHASE_CASH -> androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_v298_ef188f311424)
                    CashMovementKind.PAYMENT_RECEIVED -> androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_v298_f1acf4828a3a)
                    CashMovementKind.PAYMENT_MADE -> androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_v298_aab19987ca8f)
                    CashMovementKind.EXPENSE -> androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_v298_c4658f502cad)
                    CashMovementKind.MANUAL_ADD -> androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_v298_4b868a584d59)
                    CashMovementKind.MANUAL_DEDUCT -> androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_v298_2264ee37b709)
                },
                color = TextPrimary,
                fontSize = ExpensesTextScale.sp13,
                fontWeight = FontWeight.Bold
            )
            if (movement.note.isNotBlank()) Text(movement.note, color = TextMuted, fontSize = ExpensesTextScale.sp11)
            Text(DateUtils.formatDate(movement.createdAt), color = TextMuted, fontSize = ExpensesTextScale.sp10)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text((if (isPositive) androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_v298_44f3911755d2_2) else "") + WhatsAppUtils.formatAmount(movement.amount) + androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_v298_44f3911755d2), color = amountColor, fontSize = ExpensesTextScale.sp14, fontWeight = FontWeight.Bold)
            Text(androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_ds_9ade70a03d81) + WhatsAppUtils.formatAmount(movement.balanceAfter) + androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_v298_6f2777431c78), color = TextMuted, fontSize = ExpensesTextScale.sp10)
        }
    }
}

// صف مصروف
@Composable
private fun ExpenseRow(expense: ExpenseItem, canDelete: Boolean, onDelete: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(ExpensesDimensions.dp12)).background(BgCard)
            .border(ExpensesDimensions.dp1, MaterialTheme.colorScheme.error.copy(0.12f), RoundedCornerShape(ExpensesDimensions.dp12)).padding(ExpensesDimensions.dp12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(expense.item, color = TextPrimary, fontSize = ExpensesTextScale.sp13, fontWeight = FontWeight.Bold)
            Text(expense.category, color = TextMuted, fontSize = ExpensesTextScale.sp11)
            Text(DateUtils.formatDate(expense.date), color = TextMuted, fontSize = ExpensesTextScale.sp10)
        }
        Text(WhatsAppUtils.formatAmount(expense.amount) + androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_v298_734376fb84ea), color = MaterialTheme.colorScheme.error, fontSize = ExpensesTextScale.sp14, fontWeight = FontWeight.Bold)
        if (canDelete) {
            Spacer(Modifier.width(ExpensesDimensions.dp8))
            VertoIconButton(onClick = { showConfirm = true }) {
                Icon(Icons.Filled.DeleteOutline, null, tint = TextMuted, modifier = Modifier.size(ExpensesDimensions.dp18))
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false }, containerColor = BgCard,
            title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_ds_edaaeb3f2841), color = TextPrimary) },
            text = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.expenses.R.string.expenses_ds_45feb3f2c9c3), color = TextSecondary) },
            confirmButton = { TextButton(onClick = { onDelete(); showConfirm = false }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextMuted) } }
        )
    }
}


@Composable
private fun FinancialOperationErrorEffect(state:FinancialOperationState,host:SnackbarHostState,clear:()->Unit){
    LaunchedEffect(state){ if(state is FinancialOperationState.Error){ host.showSnackbar(state.message); clear() } }
}
