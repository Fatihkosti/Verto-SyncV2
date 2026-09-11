package com.verto.app.feature.party.presentation.client

import com.verto.app.ui.components.VertoOutlinedTextField

import com.verto.app.feature.party.presentation.shared.PartyContactActionButton

import com.verto.app.feature.party.presentation.shared.PartyDimensions
import com.verto.app.feature.party.presentation.shared.PartyTextScale

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.party.application.PartyApplicationService
import com.verto.app.feature.party.domain.model.*
import com.verto.app.feature.party.application.model.*

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.ui.components.VertoTopBar
import com.verto.app.ui.theme.*
import com.verto.app.utils.ErrorHumanizer
import com.verto.app.utils.WhatsAppUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

// ─────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────
@HiltViewModel
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ClientsListViewModel @Inject constructor(
    private val partyService: PartyApplicationService
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private val _showSuppliersFilter = MutableStateFlow(false)
    private val _supplierScope = MutableStateFlow<com.verto.app.feature.party.domain.model.SupplierScope?>(null)
    val showSuppliersFilter: StateFlow<Boolean> = _showSuppliersFilter.asStateFlow()
    val permissions = partyService.permissions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setShowSuppliers(v: Boolean) { _showSuppliersFilter.value = v }
    fun setSupplierScope(scope: com.verto.app.feature.party.domain.model.SupplierScope?) { _supplierScope.value = scope }

    val clients: Flow<PagingData<PartyClientSummary>> =
        combine(_searchQuery.debounce(200), _showSuppliersFilter, _supplierScope) { q, sup, scope -> Triple(q, sup, scope) }
            .flatMapLatest { (q, sup, scope) ->
                partyService.pagedClientSummaries(q, sup, scope)
            }
            .cachedIn(viewModelScope)

    fun archiveRole(
        clientId: String,
        role: PartyRole,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) = viewModelScope.launch {
        val result = partyService.archiveRole(clientId, role)
        result.onSuccess {
            onSuccess()
            partyService.fullSync().onFailure {
                onError("تمت الأرشفة على هذا الجهاز، وستكتمل المزامنة تلقائياً عند توفر الإنترنت")
            }
        }
        result.onFailure { e -> onError(ErrorHumanizer.humanize(e, "الأرشفة")) }
    }
}

// ─────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientsListScreen(
    showSuppliers: Boolean = false,
    supplierScope: com.verto.app.feature.party.domain.model.SupplierScope? = null,
    onBack: () -> Unit,
    onClientClick: (String) -> Unit,
    onCompetitorClick: (String) -> Unit = {},
    onAddClient: () -> Unit,
    vm: ClientsListViewModel = hiltViewModel()
) {
    val context           = LocalContext.current
    val search            by vm.searchQuery.collectAsStateWithLifecycle()
    val clients           = vm.clients.collectAsLazyPagingItems()
    val permissions       by vm.permissions.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope    = rememberCoroutineScope()

    LaunchedEffect(showSuppliers, supplierScope) {
        vm.setShowSuppliers(showSuppliers)
        vm.setSupplierScope(if (showSuppliers) supplierScope else null)
    }

    val title       = if (showSuppliers) "الموردين" else "العملاء"
    val accentColor = if (showSuppliers) AccentBlue else AccentPrimary
    val canDeleteClients = permissions?.clientsDelete == true
    val canEditClients = permissions?.clientsEdit == true

    // ── حالة حوار تأكيد الأرشفة ─────────────────────────
    var deleteTarget by remember { mutableStateOf<PartyClientSummary?>(null) }

    Scaffold(
        containerColor    = BgDeep,
        snackbarHost      = { SnackbarHost(snackbarHostState) },
        topBar            = { VertoTopBar(title = title, onBack = onBack) },
        floatingActionButton = {
            if (!canEditClients) return@Scaffold
            FloatingActionButton(
                onClick        = onAddClient,
                containerColor = accentColor,
                shape          = RoundedCornerShape(PartyDimensions.dp16)
            ) {
                Icon(Icons.Filled.PersonAdd, null, tint = TextPrimary)
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // ── حقل البحث ─────────────────────────────
            VertoOutlinedTextField(
                value         = search,
                onValueChange = { vm.setSearchQuery(it) },
                placeholder   = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_7a2b8b01a05a), fontSize = PartyTextScale.sp13) },
                leadingIcon   = { Icon(Icons.Filled.Search, null, tint = TextMuted) },
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PartyDimensions.dp16, vertical = PartyDimensions.dp8),
                shape  = RoundedCornerShape(PartyDimensions.dp12),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor      = accentColor,
                    unfocusedBorderColor    = BorderColor,
                    focusedContainerColor   = BgCard,
                    unfocusedContainerColor = BgCard,
                    focusedTextColor        = TextPrimary,
                    unfocusedTextColor      = TextPrimary
                ),
                singleLine = true
            )

            // ── عدد النتائج ───────────────────────────
            Text(
                androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_2bb1a30cfc1d_2, clients.itemCount, if (showSuppliers) androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_2bb1a30cfc1d) else androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_8898da70bb4c)),
                color    = TextMuted,
                fontSize = PartyTextScale.sp12,
                modifier = Modifier.padding(horizontal = PartyDimensions.dp16, vertical = PartyDimensions.dp2)
            )

            // ── القائمة ───────────────────────────────
            LazyColumn(
                contentPadding      = PaddingValues(horizontal = PartyDimensions.dp16, vertical = PartyDimensions.dp8),
                verticalArrangement = Arrangement.spacedBy(PartyDimensions.dp8)
            ) {
                items(
                    count = clients.itemCount,
                    key   = clients.itemKey { it.client.id }
                ) { index ->
                    val summary = clients[index] ?: return@items
                    SwipeableClientRow(
                        summary     = summary,
                        accentColor = accentColor,
                        onClick     = { onClientClick(summary.client.id) },
                        onDelete    = { deleteTarget = summary },
                        onCall      = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${summary.client.phone}"))
                            context.startActivity(intent)
                        },
                        onWhatsApp  = {
                            WhatsAppUtils.openWhatsApp(context, summary.client.phone, "")
                        },
                        canDelete = canDeleteClients
                    )
                }
                if (clients.itemCount == 0) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(top = PartyDimensions.dp60),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (search.isNotBlank()) androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_1fb02a1016e7)
                                else androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_c06a610db2b5_3, if (showSuppliers) androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_c06a610db2b5_2) else androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_c06a610db2b5)),
                                color = TextMuted
                            )
                        }
                    }
                }

                // مؤشر تحميل الصفحة التالية
                if (clients.loadState.append is androidx.paging.LoadState.Loading) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(PartyDimensions.dp16),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator(modifier = Modifier.size(PartyDimensions.dp24)) }
                    }
                }
            }
        }
    }

    // ── حوار تأكيد الأرشفة ────────────────────────────────
    deleteTarget?.let { summary ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor   = BgCard,
            shape            = RoundedCornerShape(PartyDimensions.dp20),
            title = {
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_archive_role_title, if (showSuppliers) "المورد" else "العميل"),
                    color = ErrorColor, fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_416adf43052a, summary.client.name),
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = summary
                    deleteTarget = null
                    vm.archiveRole(
                        clientId  = target.client.id,
                        role = if (showSuppliers) PartyRole.SUPPLIER else PartyRole.CUSTOMER,
                        onSuccess = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("تمت أرشفة ${target.client.name}")
                            }
                        },
                        onError = { msg ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(msg)
                            }
                        }
                    )
                }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_archive_action), color = ErrorColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextSecondary)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────
// صف قابل للسحب لكشف زر الحذف
// ─────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableClientRow(
    summary: PartyClientSummary,
    accentColor: Color,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit,
    canDelete: Boolean
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (canDelete && value == SwipeToDismissBoxValue.StartToEnd) {
                onDelete()
            }
            false   // لا تُغلق تلقائياً — يبقى الصف في مكانه
        }
    )

    SwipeToDismissBox(
        state                    = dismissState,
        backgroundContent        = { if (canDelete) DeleteBackground() },
        enableDismissFromStartToEnd = canDelete,
        enableDismissFromEndToStart = false
    ) {
        ClientRow(
            summary     = summary,
            accentColor = accentColor,
            onClick     = onClick,
            onCall      = onCall,
            onWhatsApp  = onWhatsApp
        )
    }
}

// خلفية السحب الحمراء
@Composable
private fun DeleteBackground() {
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(PartyDimensions.dp14))
            .background(ErrorColor.copy(alpha = 0.15f))
            .padding(start = PartyDimensions.dp20),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PartyDimensions.dp6)
        ) {
            Icon(Icons.Filled.Archive, null, tint = ErrorColor, modifier = Modifier.size(PartyDimensions.dp22))
            Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_archive_action), color = ErrorColor, fontWeight = FontWeight.Bold, fontSize = PartyTextScale.sp14)
        }
    }
}

// ─────────────────────────────────────────────────────
// صف العميل
// ─────────────────────────────────────────────────────
@Composable
private fun ClientRow(
    summary: PartyClientSummary,
    accentColor: Color,
    onClick: () -> Unit,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit
) {
    val client       = summary.client
    val displayBalance = summary.remaining
    val isCompetitor = client.customerSegment != null && client.supplierScope != null
    val hasDebt = displayBalance > 0.01
    val debtColor = when {
        hasDebt -> ErrorColor
        else -> SuccessColor
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PartyDimensions.dp14))
            .background(BgCard)
            .border(PartyDimensions.dp1, if (hasDebt) ErrorColor.copy(0.2f) else BorderColor, RoundedCornerShape(PartyDimensions.dp14))
            .clickable { onClick() }
            .padding(PartyDimensions.dp12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(PartyDimensions.dp44)
                .clip(CircleShape)
                .background(accentColor.copy(0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                client.name.take(1),
                color      = accentColor,
                fontSize   = PartyTextScale.sp18,
                fontWeight = FontWeight.Black
            )
        }

        Spacer(Modifier.width(PartyDimensions.dp10))

        Column(Modifier.weight(1f)) {
            Text(
                client.name,
                color      = TextPrimary,
                fontSize   = PartyTextScale.sp14,
                fontWeight = FontWeight.Bold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            if (isCompetitor && (displayBalance > 0.01 || displayBalance < -0.01)) {
                val label = if (displayBalance > 0.01)
                    "مدين: ${WhatsAppUtils.formatAmount(displayBalance)} ج"
                else
                    "دائن: ${WhatsAppUtils.formatAmount(-displayBalance)} ج"
                Text(label, color = debtColor, fontSize = PartyTextScale.sp11)
            } else if (!isCompetitor && hasDebt) {
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_f8fca53d963f, WhatsAppUtils.formatAmount(displayBalance)),
                    color    = debtColor,
                    fontSize = PartyTextScale.sp11
                )
            } else {
                Text(
                    client.customerSegment.customerSegmentLabel(),
                    color    = TextMuted,
                    fontSize = PartyTextScale.sp11
                )
            }
        }

        // الهاتف الأساسي للاتصال/واتساب
        val primaryPhone = client.phone.ifBlank { client.secondaryPhones.toSecondaryPhoneList().firstOrNull() ?: "" }
        if (primaryPhone.isNotBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(PartyDimensions.dp6)) {
                PartyContactActionButton(
                    icon = Icons.Filled.Chat,
                    color = WhatsAppBrand,
                    size = PartyDimensions.dp36,
                    cornerRadius = PartyDimensions.dp9,
                    iconSize = PartyDimensions.dp18,
                    backgroundAlpha = 0.12f,
                    onClick = onWhatsApp,
                )
                PartyContactActionButton(
                    icon = Icons.Filled.Phone,
                    color = SuccessColor,
                    size = PartyDimensions.dp36,
                    cornerRadius = PartyDimensions.dp9,
                    iconSize = PartyDimensions.dp18,
                    backgroundAlpha = 0.12f,
                    onClick = onCall,
                )
            }
        }
    }
}
