package com.verto.app.feature.party.presentation.client

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
fun ClientStatementScreen(
    clientId              : String,
    onBack                : () -> Unit,
    fromCompetitor        : Boolean = false,
    onNavigateToCompetitor: (String) -> Unit = {},
    vm                    : ClientStatementViewModel = hiltViewModel()
) {
    LaunchedEffect(clientId) { vm.init(clientId) }

    val context        = LocalContext.current
    val client         by vm.client.collectAsStateWithLifecycle()

    // COMPETITOR uses the same CUSTOMER ledger here; it is not a third or netted ledger.
    if (client == null) return
    val rows           by vm.statementRows.collectAsStateWithLifecycle()
    val openingBalance by vm.openingBalance.collectAsStateWithLifecycle()
    val filterFrom     by vm.filterFrom.collectAsStateWithLifecycle()
    val filterTo       by vm.filterTo.collectAsStateWithLifecycle()

    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker   by remember { mutableStateOf(false) }

    val closingBalance = rows.lastOrNull()?.balance ?: openingBalance
    val closingColor   = when {
        closingBalance >  0.01 -> ErrorColor
        closingBalance < -0.01 -> SuccessColor
        else                   -> TextSecondary
    }

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            VertoTopBar(
                title  = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_client_statement_title, client?.name.orEmpty()),
                onBack = onBack,
                actions = {
                    VertoIconButton(onClick = {
                        client?.let { c ->
                            printClientStatement(context, c.name, filterFrom, filterTo, openingBalance, rows)
                        }
                    }) {
                        Icon(Icons.Filled.Print, null, tint = AccentPrimary)
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
                    horizontalArrangement = Arrangement.spacedBy(PartyDimensions.dp10)
                ) {
                    VertoOutlinedButton(
                        onClick  = { showFromPicker = true },
                        modifier = Modifier.weight(1f).height(PartyDimensions.dp44),
                        shape    = RoundedCornerShape(PartyDimensions.dp10),
                        border   = androidx.compose.foundation.BorderStroke(PartyDimensions.dp1, AccentPrimary.copy(0.5f)),
                        colors   = ButtonDefaults.outlinedButtonColors(containerColor = BgCard)
                    ) {
                        Icon(Icons.Filled.CalendarToday, null, tint = AccentPrimary, modifier = Modifier.size(PartyDimensions.dp14))
                        Spacer(Modifier.width(PartyDimensions.dp6))
                        Text(DateUtils.formatDateEn(filterFrom), color = AccentPrimary, fontSize = PartyTextScale.sp12)
                    }
                    VertoOutlinedButton(
                        onClick  = { showToPicker = true },
                        modifier = Modifier.weight(1f).height(PartyDimensions.dp44),
                        shape    = RoundedCornerShape(PartyDimensions.dp10),
                        border   = androidx.compose.foundation.BorderStroke(PartyDimensions.dp1, AccentPrimary.copy(0.5f)),
                        colors   = ButtonDefaults.outlinedButtonColors(containerColor = BgCard)
                    ) {
                        Icon(Icons.Filled.CalendarToday, null, tint = AccentPrimary, modifier = Modifier.size(PartyDimensions.dp14))
                        Spacer(Modifier.width(PartyDimensions.dp6))
                        Text(DateUtils.formatDateEn(filterTo), color = AccentPrimary, fontSize = PartyTextScale.sp12)
                    }
                }
            }

            // ── الرصيد المُرحَّل ──────────────────────
            if (openingBalance != 0.0) {
                item {
                    val color = if (openingBalance > 0) ErrorColor else SuccessColor
                    val label = if (openingBalance > 0) "رصيد مُرحَّل" else "رصيد لصالح العميل"
                    Box(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(PartyDimensions.dp10))
                            .background(color.copy(0.08f))
                            .border(PartyDimensions.dp1, color.copy(0.3f), RoundedCornerShape(PartyDimensions.dp10))
                            .padding(PartyDimensions.dp12)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(label, color = color, fontSize = PartyTextScale.sp12, fontWeight = FontWeight.SemiBold)
                                Text(
                                    androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_769deaaffce8, DateUtils.formatDateEn(filterFrom)),
                                    color = TextMuted, fontSize = PartyTextScale.sp11
                                )
                            }
                            Text(
                                WhatsAppUtils.formatAmount(kotlin.math.abs(openingBalance)),
                                color = color,
                                fontSize = PartyTextScale.sp16,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ── رأس الجدول ────────────────────────────
            item {
                PartyStatementHeader(accentColor = AccentPrimary)
            }

            // ── السطور ────────────────────────────────
            if (rows.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = PartyDimensions.dp32),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_aafc3e4edd56), color = TextMuted, fontSize = PartyTextScale.sp14)
                    }
                }
            } else {
                items(rows) { row ->
                    PartyStatementRowItem(DateUtils.formatDateEn(row.date), row.description, row.debit, row.credit, row.balance)
                }
            }

            // ── الرصيد الختامي ────────────────────────
            item {
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(PartyDimensions.dp10))
                        .background(closingColor.copy(0.08f))
                        .border(PartyDimensions.dp1, closingColor.copy(0.3f), RoundedCornerShape(PartyDimensions.dp10))
                        .padding(PartyDimensions.dp14)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_625354da1b9a), color = TextPrimary, fontSize = PartyTextScale.sp14, fontWeight = FontWeight.Bold)
                        Text(
                            WhatsAppUtils.formatAmount(kotlin.math.abs(closingBalance)),
                            color = closingColor,
                            fontSize = PartyTextScale.sp18,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                Spacer(Modifier.height(PartyDimensions.dp32))
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
                }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_confirm), color = AccentPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { showFromPicker = false }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextSecondary) }
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
                }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_confirm), color = AccentPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { showToPicker = false }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextSecondary) }
            }
        ) { DatePicker(state = state) }
    }
}

// ─────────────────────────────────────────────────────
// سطر واحد في كشف الحساب
// ─────────────────────────────────────────────────────
