package com.verto.app.feature.invoice.presentation.activeinvoices

import com.verto.app.ui.components.VertoOutlinedTextField

import com.verto.app.feature.invoice.presentation.InvoiceDimensions
import com.verto.app.feature.invoice.presentation.InvoiceTextScale

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.invoice.application.*
import com.verto.app.feature.invoice.application.InvoicePresentationService

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.verto.app.ui.components.VertoTopBar
import com.verto.app.ui.theme.*
import com.verto.app.utils.DateUtils
import com.verto.app.utils.WhatsAppUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import com.verto.app.ui.components.VertoIconButton

enum class InvoiceSort(val label: String) {
    NEWEST("الأحدث"),
    HIGHEST("الأعلى مبلغاً"),
    OVERDUE("المتأخر")
}

data class InvoiceListItem(
    val invoice: InvoiceViewData,
    val clientName: String,
    val remaining: Double,
    val totalPaid: Double,
    val overdueDays: Int = 0
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class InvoicesByCategoryViewModel @Inject constructor(
    private val presentationService: InvoicePresentationService,
) : ViewModel() {

    private val _category      = MutableStateFlow("sales")
    private val _purchaseScope = MutableStateFlow<String?>(null)
    private val _tab           = MutableStateFlow(0)   // 0=الكل 1=كاش 2=آجل
    private val _sort          = MutableStateFlow(InvoiceSort.NEWEST)
    // ── الجديد ──
    private val _search        = MutableStateFlow("")

    val tab   : StateFlow<Int>          = _tab
    val sort  : StateFlow<InvoiceSort>  = _sort
    val search: StateFlow<String>       = _search

    fun setCategory(cat: String) { _category.value = cat }
    fun setPurchaseScope(scope: String?) { _purchaseScope.value = scope }
    fun setTab(t: Int)           { _tab.value = t }
    fun setSort(s: InvoiceSort)  { _sort.value = s }
    fun setSearch(q: String)     { _search.value = q }

    private val _startDate = MutableStateFlow(todayStartMs())
    private val _endDate   = MutableStateFlow(todayEndMs())

    val startDate: StateFlow<Long> = _startDate
    val endDate:   StateFlow<Long> = _endDate

    fun setDateRange(start: Long, end: Long) {
        _startDate.value = start
        _endDate.value   = end
    }

    // خريطة clientId → اسم العميل (محمَّلة مرة واحدة)
    val clientMap: StateFlow<Map<String, String>> =
        presentationService.observeClients()
            .map { list -> list.associate { it.id to it.name } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // قائمة مصفَّحة — تستبدل StateFlow<List<InvoiceListItem>>
    val items: Flow<PagingData<InvoicePaymentSummary>> =
        combine(
            combine(_category, _tab, _sort, _search) { a, b, c, d -> Quadruple(a, b, c, d) },
            combine(_startDate, _endDate, _purchaseScope) { s, e, scope -> Triple(s, e, scope) }
        ) { q, date ->
            FilterState(q.a, q.b, q.c, q.d, date.first, date.second, date.third)
        }.flatMapLatest { fs ->
            val category = if (fs.category == "sales") "SALE" else "PURCHASE"
            presentationService.observePagedInvoices(
                category = category,
                tab = fs.tab,
                from = fs.startDate,
                to = fs.endDate,
                search = fs.search,
                sort = fs.sort.name,
                purchaseScope = if (category == "PURCHASE") fs.purchaseScope else null,
            )
        }.cachedIn(viewModelScope)
}

private fun todayStartMs(): Long {
    val cal = java.util.Calendar.getInstance()
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun todayEndMs(): Long {
    val cal = java.util.Calendar.getInstance()
    cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
    cal.set(java.util.Calendar.MINUTE, 59)
    cal.set(java.util.Calendar.SECOND, 59)
    cal.set(java.util.Calendar.MILLISECOND, 999)
    return cal.timeInMillis
}

// ── الجديد ──
private data class Quadruple<A,B,C,D>(val a:A, val b:B, val c:C, val d:D)

private data class FilterState(
    val category: String, val tab: Int, val sort: InvoiceSort,
    val search: String,
    val startDate: Long, val endDate: Long, val purchaseScope: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoicesByCategoryScreen(
    category: String,
    purchaseScope: String? = null,
    onBack: () -> Unit,
    onInvoice: (String) -> Unit,
    vm: InvoicesByCategoryViewModel = hiltViewModel()
) {
    LaunchedEffect(category, purchaseScope) {
        vm.setCategory(category)
        vm.setPurchaseScope(purchaseScope)
    }

    val isSales = category == "sales"
    val title   = if (isSales) "فواتير المبيعات" else "فواتير المشتريات"

    val tab          by vm.tab.collectAsStateWithLifecycle()
    val sort         by vm.sort.collectAsStateWithLifecycle()
    val search       by vm.search.collectAsStateWithLifecycle()
    val items        = vm.items.collectAsLazyPagingItems()
    val clientMap    by vm.clientMap.collectAsStateWithLifecycle()
    val startDate    by vm.startDate.collectAsStateWithLifecycle()
    val endDate      by vm.endDate.collectAsStateWithLifecycle()

    var showSortMenu    by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker   by remember { mutableStateOf(false) }
    var tempStart       by remember { mutableStateOf(0L) }

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            VertoTopBar(
                title   = title,
                onBack  = onBack,
                actions = {
                    VertoIconButton(onClick = { showStartPicker = true }) {
                        Icon(Icons.Filled.DateRange, null, tint = AccentPrimary)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── حقل البحث ─────────────────────────────
            VertoOutlinedTextField(
                value = search,
                onValueChange = { vm.setSearch(it) },
                placeholder = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_bdbc530349b0), fontSize = InvoiceTextScale.sp13) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextMuted) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = InvoiceDimensions.dp16, vertical = InvoiceDimensions.dp8),
                shape = RoundedCornerShape(InvoiceDimensions.dp12),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor      = AccentPrimary,
                    unfocusedBorderColor    = BorderColor,
                    focusedContainerColor   = BgCard,
                    unfocusedContainerColor = BgCard,
                    focusedTextColor        = TextPrimary,
                    unfocusedTextColor      = TextPrimary
                ),
                singleLine = true
            )

            // ── نطاق التاريخ المُختار ─────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = InvoiceDimensions.dp16)
                    .padding(bottom = InvoiceDimensions.dp4),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.DateRange, null, tint = AccentPrimary, modifier = Modifier.size(InvoiceDimensions.dp14))
                Spacer(Modifier.width(InvoiceDimensions.dp4))
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_95e472e1f495, DateUtils.formatDate(startDate), DateUtils.formatDate(endDate)),
                    color    = TextMuted,
                    fontSize = InvoiceTextScale.sp11
                )
            }

            // ── شريط التبويب + الفلتر ──────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = InvoiceDimensions.dp16),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(InvoiceDimensions.dp6)) {
                    listOf("الكل", "كاش", "آجل").forEachIndexed { i, label ->
                        FilterChip(
                            selected = tab == i,
                            onClick  = { vm.setTab(i) },
                            label    = { Text(label, fontSize = InvoiceTextScale.sp12) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentPrimary,
                                selectedLabelColor     = TextPrimary
                            )
                        )
                    }
                }

                // ── الجديد (UI) ──
                Row(
                    horizontalArrangement = Arrangement.spacedBy(InvoiceDimensions.dp4),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        VertoIconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Filled.Sort, null, tint = AccentPrimary)
                        }
                        DropdownMenu(
                            expanded         = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            containerColor   = BgCard
                        ) {
                            InvoiceSort.entries.forEach { s ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            s.label,
                                            color = if (sort == s) AccentPrimary else TextPrimary,
                                            fontWeight = if (sort == s) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = { vm.setSort(s); showSortMenu = false }
                                )
                            }
                        }
                    }
                }
            }

            // ── عدد النتائج ───────────────────────────
            Text(
                androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_0fd53029cf2d, items.itemCount),
                color    = TextMuted,
                fontSize = InvoiceTextScale.sp12,
                modifier = Modifier.padding(horizontal = InvoiceDimensions.dp16, vertical = InvoiceDimensions.dp4)
            )

            LazyColumn(
                contentPadding = PaddingValues(horizontal = InvoiceDimensions.dp16, vertical = InvoiceDimensions.dp8),
                verticalArrangement = Arrangement.spacedBy(InvoiceDimensions.dp8)
            ) {
                items(
                    count = items.itemCount,
                    key   = items.itemKey { it.invoice.id }
                ) { index ->
                    val item = items[index] ?: return@items
                    // المصدر الموحَّد لحالة الفاتورة — لا يُحسب التأخير من dueDate وحده.
                    val financial = item.financial

                    InvoiceCard(
                        item    = InvoiceListItem(
                            invoice     = item.invoice,
                            clientName  = clientMap[item.invoice.clientId] ?: "غير معروف",
                            remaining   = financial.remaining,
                            totalPaid   = item.totalPaid,
                            overdueDays = financial.overdueDays
                        ),
                        onClick = { onInvoice(item.invoice.id) }
                    )
                }
                if (items.itemCount == 0) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = InvoiceDimensions.dp60),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_e5ce6bd59016), color = TextMuted, fontSize = InvoiceTextScale.sp14)
                        }
                    }
                }
                if (items.loadState.append is androidx.paging.LoadState.Loading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(InvoiceDimensions.dp16), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(InvoiceDimensions.dp24))
                        }
                    }
                }
            }
        }
    }

    // ── منتقي تاريخ البداية ───────────────────────────
    if (showStartPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = startDate)
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val selected = pickerState.selectedDateMillis
                    if (selected != null) {
                        tempStart       = selected
                        showStartPicker = false
                        showEndPicker   = true
                    }
                }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_next)) }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel)) }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    // ── منتقي تاريخ النهاية ───────────────────────────
    if (showEndPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = endDate)
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val selected = pickerState.selectedDateMillis
                    if (selected != null) {
                        // نهاية اليوم المُختار (23:59:59.999)
                        val endOfDay = selected + 86_400_000L - 1
                        vm.setDateRange(tempStart, endOfDay)
                        showEndPicker = false
                    }
                }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel)) }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun InvoiceCard(
    item: InvoiceListItem,
    onClick: () -> Unit
) {
    val isCredit  = item.invoice.status == InvoiceStatus.CLOSED_CREDIT
    val isPaid    = isCredit && item.remaining <= 0.01
    val isOverdue = item.overdueDays > 0
    // أولوية «مسدَّد» قبل «متأخر»: فاتورة مسددة بالكامل لا تظهر متأخرة أبدًا.
    val statusColor = when {
        isPaid    -> SuccessColor
        isOverdue -> MaterialTheme.colorScheme.error
        isCredit  -> InfoColor
        else      -> SuccessColor
    }
    val statusLabel = when {
        isPaid    -> "مسدَّد ✓"
        isOverdue -> "متأخر ${item.overdueDays} يوم"
        isCredit  -> "آجل"
        else      -> "كاش"
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(InvoiceDimensions.dp14))
            .background(BgCard)
            .border(InvoiceDimensions.dp1, statusColor.copy(0.2f), RoundedCornerShape(InvoiceDimensions.dp14))
            .clickable { onClick() }
            .padding(InvoiceDimensions.dp14),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(InvoiceDimensions.dp44)
                .clip(RoundedCornerShape(InvoiceDimensions.dp10))
                .background(statusColor.copy(0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_adebc50ade66, item.invoice.invoiceNumber),
                color      = statusColor,
                fontSize   = InvoiceTextScale.sp12,
                fontWeight = FontWeight.Black
            )
        }

        Spacer(Modifier.width(InvoiceDimensions.dp12))

        Column(Modifier.weight(1f)) {
            Text(
                item.clientName,
                color      = TextPrimary,
                fontSize   = InvoiceTextScale.sp14,
                fontWeight = FontWeight.Bold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            Text(
                DateUtils.formatDate(item.invoice.createdAt),
                color    = TextMuted,
                fontSize = InvoiceTextScale.sp11
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                WhatsAppUtils.formatAmount(item.invoice.totalAmount) + androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_v298_bdab82802377),
                color      = TextPrimary,
                fontSize   = InvoiceTextScale.sp14,
                fontWeight = FontWeight.Bold
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(InvoiceDimensions.dp6))
                    .background(statusColor.copy(0.12f))
                    .padding(horizontal = InvoiceDimensions.dp6, vertical = InvoiceDimensions.dp2)
            ) {
                Text(statusLabel, color = statusColor, fontSize = InvoiceTextScale.sp10, fontWeight = FontWeight.Bold)
            }
        }
    }
}
