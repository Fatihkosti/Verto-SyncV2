package com.verto.app.feature.party.presentation.competitor

import com.verto.app.feature.party.presentation.shared.PartyDimensions
import com.verto.app.feature.party.presentation.shared.PartyTextScale

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.party.application.PartyApplicationService
import com.verto.app.feature.party.domain.model.*
import com.verto.app.feature.party.application.model.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import com.verto.app.feature.party.presentation.shared.DashboardInvoiceRow
import com.verto.app.ui.components.InfoChip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.verto.app.ui.components.VertoTabRow

// ─────────────────────────────────────────────────────
// Data
// ─────────────────────────────────────────────────────
data class CompetitorStats(
    val totalSales    : Double,
    val totalPurchases: Double,
    val profits       : Double,
    val invoiceCount  : Int
)

// ─────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────
@HiltViewModel
class CompetitorViewModel @Inject constructor(
    private val partyService: PartyApplicationService
) : ViewModel() {

    private val _clientId   = MutableStateFlow("")
    private val _filterFrom = MutableStateFlow(run {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        cal.timeInMillis
    })
    private val _filterTo = MutableStateFlow(run {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
        cal.timeInMillis
    })

    val filterFrom: StateFlow<Long> = _filterFrom
    val filterTo  : StateFlow<Long> = _filterTo

    fun setDateRange(from: Long, to: Long) { _filterFrom.value = from; _filterTo.value = to }
    fun init(id: String) { _clientId.value = id }

    val name: StateFlow<String> = _clientId
        .flatMapLatest { partyService.getClientById(it) }
        .map { it?.name ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _allSummaries: StateFlow<List<PartyInvoiceSummary>> = _clientId
        .flatMapLatest { id -> partyService.getInvoiceSummariesForClient(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // الرصيد الإجمالي — لا يتأثر بالتاريخ
    // موجب = المنافس مدين لي (أحمر) | سالب = أنا مدين له (أخضر)
    val balance: StateFlow<Double> = _allSummaries.map { summaries ->
        var b = 0.0
        for (s in summaries) {
            if (s.invoice.category == PartyInvoiceCategory.SALE) {
                b += s.invoice.totalAmount
                b -= s.totalPaid
            } else {
                b -= s.invoice.totalAmount
                b += s.totalPaid
            }
        }
        b
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val invoiceSummaries: StateFlow<List<PartyInvoiceSummary>> = _allSummaries

    val lastActivity: StateFlow<Long?> = _allSummaries
        .map { it.maxOfOrNull { s -> s.invoice.createdAt } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _items = _clientId
        .flatMapLatest { id -> partyService.getItemsForClient(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // الإحصائيات المفلترة بالتاريخ
    val stats: StateFlow<CompetitorStats> = combine(_allSummaries, _items, _filterFrom, _filterTo) { summaries, items, from, to ->
        val sales     = summaries.filter { it.invoice.category == PartyInvoiceCategory.SALE     && it.invoice.createdAt in from..to }
        val purchases = summaries.filter { it.invoice.category == PartyInvoiceCategory.PURCHASE && it.invoice.createdAt in from..to }
        val totalSales     = sales.sumOf { it.invoice.totalAmount }
        val totalPurchases = purchases.sumOf { it.invoice.totalAmount }
        val saleInvoiceIds = sales.map { it.invoice.id }.toSet()
        val profits = items
            .filter { it.invoiceId in saleInvoiceIds }
            .sumOf { (it.sellPrice - it.buyPrice) * it.quantity }
        CompetitorStats(
            totalSales     = totalSales,
            totalPurchases = totalPurchases,
            profits        = profits,
            invoiceCount   = sales.size + purchases.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CompetitorStats(0.0, 0.0, 0.0, 0))
}

// ─────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompetitorScreen(
    clientId    : String,
    onBack      : () -> Unit,
    onEditClient: () -> Unit = {},
    onAddPayment: (String) -> Unit = {},
    onStatement : (String) -> Unit = {},
    onInvoice   : (String) -> Unit = {},
    vm          : CompetitorViewModel = hiltViewModel()
) {
    LaunchedEffect(clientId) { vm.init(clientId) }

    val name             by vm.name.collectAsStateWithLifecycle()
    val balance          by vm.balance.collectAsStateWithLifecycle()
    val stats            by vm.stats.collectAsStateWithLifecycle()
    val filterFrom       by vm.filterFrom.collectAsStateWithLifecycle()
    val filterTo         by vm.filterTo.collectAsStateWithLifecycle()
    val invoiceSummaries by vm.invoiceSummaries.collectAsStateWithLifecycle()
    val lastActivity     by vm.lastActivity.collectAsStateWithLifecycle()

    var activeTab      by rememberSaveable { mutableStateOf(0) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker   by remember { mutableStateOf(false) }

    val balanceColor = when {
        balance >  0.01 -> ErrorColor
        balance < -0.01 -> SuccessColor
        else            -> TextSecondary
    }
    val balanceLabel = when {
        balance >  0.01 -> "المنافس مدين لك"
        balance < -0.01 -> "أنت مدين للمنافس"
        else            -> "لا يوجد رصيد"
    }

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            VertoTopBar(
                title   = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_da39a3ee5e6b),
                onBack  = onBack,
                actions = {
                    VertoIconButton(onClick = onEditClient) {
                        Icon(Icons.Filled.Edit, contentDescription = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_edit), tint = AccentPrimary)
                    }
                    VertoIconButton(onClick = { onAddPayment(clientId) }) {
                        Icon(Icons.Filled.Payments, contentDescription = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_a8436ce4b250), tint = AccentPrimary)
                    }
                    VertoIconButton(onClick = { onStatement(clientId) }) {
                        Icon(Icons.Filled.Description, contentDescription = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_ebfeae4e577a), tint = AccentPrimary)
                    }
                    VertoIconButton(onClick = { showFromPicker = true }) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_0a249d4772ec), tint = TextMuted)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(PartyDimensions.dp16),
            verticalArrangement = Arrangement.spacedBy(PartyDimensions.dp12)
        ) {

            // ── اسم المنافس ───────────────────────────
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(PartyDimensions.dp12))
                        .background(BgCard)
                        .padding(PartyDimensions.dp18),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        name,
                        color      = TextPrimary,
                        fontSize   = PartyTextScale.sp20,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ── بطاقة الرصيد الإجمالي (لا تتأثر بالفلتر) ──
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(PartyDimensions.dp12))
                        .background(balanceColor.copy(0.08f))
                        .border(PartyDimensions.dp1, balanceColor.copy(0.35f), RoundedCornerShape(PartyDimensions.dp12))
                        .padding(PartyDimensions.dp18)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_16fd9c43a308), color = TextPrimary, fontSize = PartyTextScale.sp14, fontWeight = FontWeight.SemiBold)
                            Text(balanceLabel, color = balanceColor, fontSize = PartyTextScale.sp12)
                        }
                        Text(
                            WhatsAppUtils.formatAmount(kotlin.math.abs(balance)),
                            color      = balanceColor,
                            fontSize   = PartyTextScale.sp22,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            // ── الأربع بطاقات ─────────────────────────
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PartyDimensions.dp10)
                ) {
                    CompetitorStatCard(
                        modifier = Modifier.weight(1f),
                        label    = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_7db74e774f7c),
                        value    = WhatsAppUtils.formatAmount(stats.totalSales),
                        color    = SuccessColor
                    )
                    CompetitorStatCard(
                        modifier = Modifier.weight(1f),
                        label    = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_62e5021947c6),
                        value    = WhatsAppUtils.formatAmount(stats.totalPurchases),
                        color    = ErrorColor
                    )
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PartyDimensions.dp10)
                ) {
                    val profitColor = if (stats.profits >= 0) SuccessColor else ErrorColor
                    CompetitorStatCard(
                        modifier = Modifier.weight(1f),
                        label    = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_ea1e6de14b99),
                        value    = WhatsAppUtils.formatAmount(kotlin.math.abs(stats.profits)),
                        color    = profitColor,
                        subtitle = if (stats.profits < 0) "خسارة" else null
                    )
                    CompetitorStatCard(
                        modifier = Modifier.weight(1f),
                        label    = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_aff299f08da2),
                        value    = stats.invoiceCount.toString(),
                        color    = AccentPrimary
                    )
                }
            }
            // ── آخر تعامل ─────────────────────────────
            item {
                InfoChip(
                    icon     = Icons.Filled.Schedule,
                    label    = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_a33fa614b279),
                    value    = lastActivity?.let { DateUtils.formatDate(it) } ?: "—",
                    color    = TextSecondary,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── تبويبات الفواتير ──────────────────────
            item {
                VertoTabRow(
                    selectedTabIndex = activeTab,
                    containerColor   = BgCard,
                    contentColor     = AccentPrimary
                ) {
                    listOf("كل الفواتير", "آجل", "متأخر").forEachIndexed { i, label ->
                        Tab(
                            selected = activeTab == i,
                            onClick  = { activeTab = i },
                            text = { Text(label, fontSize = PartyTextScale.sp13) }
                        )
                    }
                }
            }

            // ── قائمة الفواتير ────────────────────────
            val filtered = when (activeTab) {
                1    -> invoiceSummaries.filter { it.invoice.status == PartyInvoiceStatus.CLOSED_CREDIT }
                2    -> invoiceSummaries.filter { it.financial.isOverdue }
                else -> invoiceSummaries
            }.sortedByDescending { it.invoice.createdAt }

            if (filtered.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(top = PartyDimensions.dp32),
                        contentAlignment = Alignment.Center
                    ) { Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_e5ce6bd59016), color = TextMuted) }
                }
            } else {
                items(filtered, key = { it.invoice.id }) { s ->
                    DashboardInvoiceRow(summary = s, onClick = { onInvoice(s.invoice.id) })
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
                        showToPicker   = true
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
// بطاقة إحصائية
// ─────────────────────────────────────────────────────
@Composable
private fun CompetitorStatCard(
    modifier: Modifier,
    label   : String,
    value   : String,
    color   : androidx.compose.ui.graphics.Color,
    subtitle: String? = null
) {
    Box(
        modifier
            .clip(RoundedCornerShape(PartyDimensions.dp12))
            .background(BgCard)
            .border(PartyDimensions.dp1, color.copy(0.25f), RoundedCornerShape(PartyDimensions.dp12))
            .padding(PartyDimensions.dp14)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(label, color = TextMuted, fontSize = PartyTextScale.sp11, textAlign = TextAlign.Center)
            Spacer(Modifier.height(PartyDimensions.dp6))
            Text(value, color = color, fontSize = PartyTextScale.sp16, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            if (subtitle != null) {
                Text(subtitle, color = color, fontSize = PartyTextScale.sp10, textAlign = TextAlign.Center)
            }
        }
    }
}
