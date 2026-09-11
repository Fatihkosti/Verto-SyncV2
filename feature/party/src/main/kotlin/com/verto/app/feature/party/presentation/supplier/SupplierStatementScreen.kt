package com.verto.app.feature.party.presentation.supplier

import com.verto.app.ui.components.VertoOutlinedButton

import com.verto.app.feature.party.presentation.shared.PartyStatementRowItem

import com.verto.app.feature.party.presentation.shared.PartyStatementHeader

import com.verto.app.feature.party.presentation.shared.PartyDimensions
import com.verto.app.feature.party.presentation.shared.PartyTextScale

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.party.domain.model.*
import com.verto.app.feature.party.application.model.*

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.ui.components.VertoTopBar
import com.verto.app.ui.theme.*
import com.verto.app.utils.DateUtils
import com.verto.app.utils.WhatsAppUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.util.Calendar
import javax.inject.Inject
import com.verto.app.ui.components.VertoIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierStatementScreen(
    supplierId            : String,
    onBack                : () -> Unit,
    onNavigateToCompetitor: (String) -> Unit = {},
    vm                    : SupplierStatementViewModel = hiltViewModel()
) {
    LaunchedEffect(supplierId) { vm.init(supplierId) }

    val supplier       by vm.supplier.collectAsStateWithLifecycle()

    // COMPETITOR uses the same SUPPLIER ledger here; it is not a third or netted ledger.
    if (supplier == null) return
    val rows           by vm.statementRows.collectAsStateWithLifecycle()
    val openingBalance by vm.openingBalance.collectAsStateWithLifecycle()
    val filterFrom     by vm.filterFrom.collectAsStateWithLifecycle()
    val filterTo       by vm.filterTo.collectAsStateWithLifecycle()
    val context        = LocalContext.current

    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker   by remember { mutableStateOf(false) }

    val closingBalance = rows.lastOrNull()?.balance ?: openingBalance

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            VertoTopBar(
                title  = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_supplier_statement_title, supplier?.name ?: androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_loading_name)),
                onBack = onBack,
                actions = {
                    VertoIconButton(onClick = {
                        printStatement(context, supplier?.name ?: "", filterFrom, filterTo,
                            openingBalance, rows)
                    }) {
                        Icon(Icons.Filled.Print, contentDescription = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_print_content_description), tint = AccentBlue)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(PartyDimensions.dp16),
            verticalArrangement = Arrangement.spacedBy(PartyDimensions.dp10)
        ) {
            // ── فلتر التاريخ ──────────────────────────
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PartyDimensions.dp8)
                ) {
                    VertoOutlinedButton(
                        onClick = { showFromPicker = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue)
                    ) {
                        Icon(Icons.Filled.CalendarToday, null, modifier = Modifier.size(PartyDimensions.dp16))
                        Spacer(Modifier.width(PartyDimensions.dp4))
                        Text(DateUtils.formatDate(filterFrom), fontSize = PartyTextScale.sp12)
                    }
                    VertoOutlinedButton(
                        onClick = { showToPicker = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue)
                    ) {
                        Icon(Icons.Filled.CalendarToday, null, modifier = Modifier.size(PartyDimensions.dp16))
                        Spacer(Modifier.width(PartyDimensions.dp4))
                        Text(DateUtils.formatDate(filterTo), fontSize = PartyTextScale.sp12)
                    }
                }
            }

            // ── رصيد مرحّل من قبل الفترة ─────────────
            if (openingBalance != 0.0) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(PartyDimensions.dp10))
                            .background(
                                if (openingBalance > 0) ErrorColor.copy(0.08f)
                                else SuccessColor.copy(0.08f)
                            )
                            .border(
                                PartyDimensions.dp1,
                                if (openingBalance > 0) ErrorColor.copy(0.3f)
                                else SuccessColor.copy(0.3f),
                                RoundedCornerShape(PartyDimensions.dp10)
                            )
                            .padding(PartyDimensions.dp12),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_6a2f4c006f80),
                                color = TextMuted, fontSize = PartyTextScale.sp11)
                            Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_1b09c6628c92, DateUtils.formatDate(filterFrom)),
                                color = TextMuted, fontSize = PartyTextScale.sp10)
                        }
                        Text(
                            WhatsAppUtils.formatAmount(kotlin.math.abs(openingBalance)),
                            color = if (openingBalance > 0) ErrorColor else SuccessColor,
                            fontSize = PartyTextScale.sp16, fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            // ── رأس الجدول ────────────────────────────
            item {
                PartyStatementHeader(accentColor = AccentBlue)
            }

            // ── سطور الكشف ────────────────────────────
            if (rows.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(top = PartyDimensions.dp32),
                        contentAlignment = Alignment.Center
                    ) { Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_614e009d4779), color = TextMuted) }
                }
            } else {
                items(rows) { row ->
                    PartyStatementRowItem(DateUtils.formatDate(row.date), row.description, row.debit, row.credit, row.balance)
                }
            }

            // ── الرصيد الختامي ────────────────────────
            item {
                HorizontalDivider(color = BorderColor)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(PartyDimensions.dp10))
                        .background(BgCard)
                        .padding(PartyDimensions.dp12),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_5d59a0eaccc1),
                        color = TextPrimary, fontSize = PartyTextScale.sp14, fontWeight = FontWeight.Bold)
                    Text(
                        (if (closingBalance >= 0) "" else androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_510019481a3e)) +
                                WhatsAppUtils.formatAmount(kotlin.math.abs(closingBalance)),
                        color = if (closingBalance > 0) ErrorColor else SuccessColor,
                        fontSize = PartyTextScale.sp16, fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }

    // ── Date Pickers ─────────────────────────────────
    if (showFromPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = filterFrom)
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val c = Calendar.getInstance().apply {
                            timeInMillis = ms
                            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                        }
                        vm.setDateRange(c.timeInMillis, filterTo)
                        showFromPicker = false
                    }
                }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_confirm), color = AccentBlue) }
            },
            dismissButton = {
                TextButton(onClick = { showFromPicker = false }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextSecondary)
                }
            }
        ) { DatePicker(state = state) }
    }

    if (showToPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = filterTo)
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val c = Calendar.getInstance().apply {
                            timeInMillis = ms
                            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
                        }
                        vm.setDateRange(filterFrom, c.timeInMillis)
                        showToPicker = false
                    }
                }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_confirm), color = AccentBlue) }
            },
            dismissButton = {
                TextButton(onClick = { showToPicker = false }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextSecondary)
                }
            }
        ) { DatePicker(state = state) }
    }
}

// ─────────────────────────────────────────────────────
// رأس جدول كشف الحساب
// ─────────────────────────────────────────────────────
