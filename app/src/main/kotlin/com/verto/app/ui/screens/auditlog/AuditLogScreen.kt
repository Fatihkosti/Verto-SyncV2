package com.verto.app.ui.screens.auditlog

import androidx.compose.ui.res.stringResource

import com.verto.app.R

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.application.presentationboundary.*

import com.verto.app.core.audit.domain.AuditAction
import com.verto.app.core.audit.domain.AuditTable

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.verto.app.data.repository.AuditLogRepository
import com.verto.app.ui.components.VertoTopBar
import com.verto.app.ui.theme.*
import com.verto.app.utils.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.verto.app.ui.components.VertoIconButton

// ─────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class AuditLogViewModel @Inject constructor(
    private val repo: AuditLogRepository
) : ViewModel() {

    private val _filterTable = MutableStateFlow<AuditTable?>(null)
    val filterTable: StateFlow<AuditTable?> = _filterTable.asStateFlow()

    private val _showUndoOnly = MutableStateFlow(false)
    val showUndoOnly: StateFlow<Boolean> = _showUndoOnly.asStateFlow()

    // للـ PDF export — يحتاج القائمة الكاملة
    val allLogs: StateFlow<List<AuditLogItem>> =
        combine(repo.getAll(), _filterTable, _showUndoOnly) { all, table, undoOnly ->
            var list = all
            if (table != null) list = list.filter { it.auditTable == table }
            if (undoOnly)      list = list.filter { it.canUndo }
            list
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // للعرض — مصفَّح
    val logsPaged: Flow<PagingData<AuditLogItem>> =
        combine(_filterTable, _showUndoOnly) { table, undoOnly -> table to undoOnly }
            .flatMapLatest { (table, undoOnly) ->
                Pager(PagingConfig(pageSize = 30, enablePlaceholders = false)) {
                    repo.getFilteredPaged(table, undoOnly)
                }.flow
            }
            .cachedIn(viewModelScope)

    init {
        viewModelScope.launch { repo.expireOldEntries() }
    }

    fun setFilterTable(table: AuditTable?) { _filterTable.value = table }
    fun toggleUndoOnly() { _showUndoOnly.value = !_showUndoOnly.value }

    fun undoEntry(log: AuditLogItem) = viewModelScope.launch {
        repo.markAsUndone(log.id)
    }

    fun exportToPdf(context: Context, onDone: (java.io.File) -> Unit) =
        viewModelScope.launch(Dispatchers.IO) {
            val file = repo.exportToPdf(context, allLogs.value)
            kotlinx.coroutines.withContext(Dispatchers.Main) { onDone(file) }
        }
}

// ─────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreen(
    onBack: () -> Unit,
    vm: AuditLogViewModel = hiltViewModel()
) {
    val logs     = vm.logsPaged.collectAsLazyPagingItems()
    val filter   by vm.filterTable.collectAsStateWithLifecycle()
    val undoOnly by vm.showUndoOnly.collectAsStateWithLifecycle()

    var expandedEntry by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = BgDeep,
        topBar = { VertoTopBar(title = androidx.compose.ui.res.stringResource(R.string.ds_e94c905bb082), onBack = onBack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // ── فلاتر ─────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = AuditLogDimensions.dp16, vertical = AuditLogDimensions.dp8),
                horizontalArrangement = Arrangement.spacedBy(AuditLogDimensions.dp8),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                FilterChipSmall(selected = undoOnly, label = androidx.compose.ui.res.stringResource(R.string.ds_2a941f8b8570), color = SuccessColor) {
                    vm.toggleUndoOnly()
                }
                (listOf(null) + AuditTable.entries).forEach { table ->
                    FilterChipSmall(
                        selected = filter == table,
                        label    = table?.label ?: stringResource(R.string.legacy_ui_e367dd91fac0),
                        color    = AccentPrimary
                    ) { vm.setFilterTable(table) }
                }
            }

            Text(
                androidx.compose.ui.res.stringResource(R.string.ds_83a90505bd37, logs.itemCount),
                color    = TextMuted,
                fontSize = AuditLogTextScale.sp12,
                modifier = Modifier.padding(horizontal = AuditLogDimensions.dp16, vertical = AuditLogDimensions.dp2)
            )

            LazyColumn(
                contentPadding      = PaddingValues(horizontal = AuditLogDimensions.dp16, vertical = AuditLogDimensions.dp8),
                verticalArrangement = Arrangement.spacedBy(AuditLogDimensions.dp8)
            ) {
                if (logs.itemCount == 0) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(top = AuditLogDimensions.dp60),
                            contentAlignment = Alignment.Center
                        ) { Text(androidx.compose.ui.res.stringResource(R.string.ds_093abe15c06d), color = TextMuted) }
                    }
                } else {
                    items(
                        count = logs.itemCount,
                        key   = logs.itemKey { it.id }
                    ) { index ->
                        val log = logs[index] ?: return@items
                        AuditLogRow(
                            log      = log,
                            expanded = expandedEntry == log.id,
                            onExpand = { expandedEntry = if (expandedEntry == log.id) null else log.id },
                            onUndo   = { vm.undoEntry(log) }
                        )
                    }

                    if (logs.loadState.append is androidx.paging.LoadState.Loading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(AuditLogDimensions.dp16), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(AuditLogDimensions.dp24))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────
// صف سجل
// ─────────────────────────────────────────────────────
@Composable
private fun AuditLogRow(
    log: AuditLogItem,
    expanded: Boolean,
    onExpand: () -> Unit,
    onUndo: () -> Unit
) {
    val actionColor = when (log.action) {
        AuditAction.INSERT -> SuccessColor
        AuditAction.UPDATE -> AccentBlue
        AuditAction.DELETE -> ErrorColor
    }
    val actionIcon = when (log.action) {
        AuditAction.INSERT -> Icons.Filled.AddCircle
        AuditAction.UPDATE -> Icons.Filled.Edit
        AuditAction.DELETE -> Icons.Filled.DeleteForever
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AuditLogDimensions.dp12))
            .background(BgCard)
            .border(AuditLogDimensions.dp1, actionColor.copy(0.2f), RoundedCornerShape(AuditLogDimensions.dp12))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onExpand() }
                .padding(AuditLogDimensions.dp12),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(AuditLogDimensions.dp38)
                    .clip(RoundedCornerShape(AuditLogDimensions.dp9))
                    .background(actionColor.copy(0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(actionIcon, null, tint = actionColor, modifier = Modifier.size(AuditLogDimensions.dp18))
            }

            Spacer(Modifier.width(AuditLogDimensions.dp10))

            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(AuditLogDimensions.dp6)) {
                    Text(log.auditTable.label, color = AccentPrimary, fontSize = AuditLogTextScale.sp11, fontWeight = FontWeight.Bold)
                    Text(androidx.compose.ui.res.stringResource(R.string.ds_ecf727ea048d), color = TextMuted, fontSize = AuditLogTextScale.sp11)
                    Text(log.action.label, color = actionColor, fontSize = AuditLogTextScale.sp11)
                }
                Text(
                    log.recordSummary.ifBlank { stringResource(R.string.legacy_ui_ddf1ce35f4e4) },
                    color      = TextPrimary,
                    fontSize   = AuditLogTextScale.sp13,
                    fontWeight = FontWeight.Bold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AuditLogDimensions.dp8)) {
                    if (log.employeeName.isNotBlank())
                        Text(log.employeeName, color = TextMuted, fontSize = AuditLogTextScale.sp10)
                    Text(DateUtils.formatDateTime(log.createdAt), color = TextMuted, fontSize = AuditLogTextScale.sp10)
                }
            }

            if (log.canUndo) {
                var showUndo by remember { mutableStateOf(false) }
                VertoIconButton(onClick = { showUndo = true }, modifier = Modifier.size(AuditLogDimensions.dp48)) {
                    Icon(Icons.Filled.Undo, null, tint = WarningColor, modifier = Modifier.size(AuditLogDimensions.dp18))
                }
                if (showUndo) {
                    AlertDialog(
                        onDismissRequest = { showUndo = false },
                        containerColor   = BgCard,
                        title   = { Text(androidx.compose.ui.res.stringResource(R.string.ds_b3060271b97d), color = TextPrimary) },
                        text    = { Text(log.recordSummary, color = TextSecondary) },
                        confirmButton = {
                            TextButton(onClick = { onUndo(); showUndo = false }) {
                                Text(androidx.compose.ui.res.stringResource(R.string.ds_98df46fbd83b), color = WarningColor, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showUndo = false }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextMuted) }
                        }
                    )
                }
            } else {
                Icon(Icons.Filled.Lock, null, tint = TextMuted.copy(0.4f), modifier = Modifier.size(AuditLogDimensions.dp16))
            }
        }

        if (expanded && (log.oldValue.isNotBlank() || log.newValue.isNotBlank())) {
            HorizontalDivider(color = BorderColor)
            Column(Modifier.padding(AuditLogDimensions.dp12), verticalArrangement = Arrangement.spacedBy(AuditLogDimensions.dp6)) {
                if (log.oldValue.isNotBlank()) {
                    Text(androidx.compose.ui.res.stringResource(R.string.ds_f8621d012957), color = ErrorColor, fontSize = AuditLogTextScale.sp11, fontWeight = FontWeight.Bold)
                    Text(log.oldValue, color = TextSecondary, fontSize = AuditLogTextScale.sp11)
                }
                if (log.newValue.isNotBlank()) {
                    Text(androidx.compose.ui.res.stringResource(R.string.ds_b4774807554c), color = SuccessColor, fontSize = AuditLogTextScale.sp11, fontWeight = FontWeight.Bold)
                    Text(log.newValue, color = TextSecondary, fontSize = AuditLogTextScale.sp11)
                }
            }
        }
    }
}

@Composable
private fun FilterChipSmall(selected: Boolean, label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(AuditLogDimensions.dp8))
            .background(if (selected) color.copy(0.15f) else BgCard)
            .border(AuditLogDimensions.dp1, if (selected) color else BorderColor, RoundedCornerShape(AuditLogDimensions.dp8))
            .clickable { onClick() }
            .padding(horizontal = AuditLogDimensions.dp10, vertical = AuditLogDimensions.dp5)
    ) {
        Text(label, color = if (selected) color else TextMuted, fontSize = AuditLogTextScale.sp12, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
