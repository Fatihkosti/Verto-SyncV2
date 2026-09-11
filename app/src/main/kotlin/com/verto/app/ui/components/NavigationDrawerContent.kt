package com.verto.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.PriceChange
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.verto.app.R
import com.verto.app.feature.sync.presentation.DrawerSyncDomainUi
import com.verto.app.feature.sync.presentation.DrawerSyncUiState
import com.verto.app.feature.sync.presentation.SyncConflictReviewUiState
import com.verto.app.feature.sync.presentation.formatDateTime
import com.verto.app.ui.navigation.DrawerDestination
import com.verto.app.ui.navigation.DrawerDestinationRegistry
import com.verto.app.ui.navigation.DrawerIconKey
import com.verto.app.ui.navigation.DrawerNavigationUiState
import com.verto.app.ui.navigation.DrawerSection
import com.verto.app.ui.theme.VertoSpacing

@Composable
fun VertoNavigationDrawerContent(
    uiState: DrawerNavigationUiState,
    syncState: DrawerSyncUiState,
    conflictReviewState: SyncConflictReviewUiState,
    selectedDestinationId: String?,
    homeSelected: Boolean,
    onHomeClick: () -> Unit,
    onDestinationClick: (String) -> Unit,
    onSectionToggle: (DrawerSection) -> Unit,
    onSyncNow: () -> Unit,
    onOpenConflictReview: () -> Unit,
    onAcceptServerConflict: (String, Long) -> Unit,
    onResendLocalConflict: (String, Long) -> Unit,
    onClose: () -> Unit,
) {
    val visibleBySection = remember(uiState.permissions, uiState.role) {
        DrawerDestinationRegistry.visibleBySection(uiState.permissions, uiState.role)
    }
    var showSyncDetails by rememberSaveable { mutableStateOf(false) }
    var showConflictReview by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppChromeDimensions.dp12),
    ) {
        OrganizationDrawerHeader(uiState = uiState, onClose = onClose)

        NavigationDrawerItem(
            label = { Text(androidx.compose.ui.res.stringResource(R.string.app_drawer_home)) },
            selected = homeSelected,
            onClick = onHomeClick,
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = AppChromeDimensions.dp8))

        if (!uiState.isReady) {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(AppChromeDimensions.dp12))
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.ds_e3b57a7f22ba),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppChromeDimensions.dp2),
            ) {
                visibleBySection.forEach { (section, destinations) ->
                    item(key = "section_${section.storageKey}") {
                        DrawerSectionHeader(
                            section = section,
                            expanded = uiState.expandedSection == section,
                            onClick = { onSectionToggle(section) },
                        )
                    }
                    item(key = "section_items_${section.storageKey}") {
                        AnimatedVisibility(visible = uiState.expandedSection == section) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize()
                                    .padding(start = AppChromeDimensions.dp18),
                                verticalArrangement = Arrangement.spacedBy(AppChromeDimensions.dp2),
                            ) {
                                destinations.forEach { destination ->
                                    DrawerDestinationRow(
                                        destination = destination,
                                        selected = selectedDestinationId == destination.id,
                                        onClick = { onDestinationClick(destination.id) },
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(AppChromeDimensions.dp16)) }
            }
        }

        SyncStatusCard(state = syncState, onClick = { showSyncDetails = true })
        Spacer(Modifier.height(AppChromeDimensions.dp12))
    }

    if (showSyncDetails) {
        SyncStatusDialog(
            state = syncState,
            onSyncNow = onSyncNow,
            onReviewConflicts = {
                showSyncDetails = false
                showConflictReview = true
                onOpenConflictReview()
            },
            onDismiss = { showSyncDetails = false },
        )
    }

    if (showConflictReview) {
        SyncConflictReviewDialog(
            state = conflictReviewState,
            onRefresh = onOpenConflictReview,
            onAcceptServer = onAcceptServerConflict,
            onResendLocal = onResendLocalConflict,
            onDismiss = { showConflictReview = false },
        )
    }
}

@Composable
private fun OrganizationDrawerHeader(uiState: DrawerNavigationUiState, onClose: () -> Unit) {
    val header = uiState.organizationHeader
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppChromeDimensions.dp12, bottom = AppChromeDimensions.dp8),
        horizontalArrangement = Arrangement.spacedBy(AppChromeDimensions.dp10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val logoUrl = header.logoUrl
            if (logoUrl.isNullOrBlank()) {
                Icon(Icons.Filled.Business, contentDescription = null, modifier = Modifier.size(32.dp))
            } else {
                val context = LocalContext.current
                val imageRequest = remember(logoUrl, context) {
                    ImageRequest.Builder(context)
                        .data(logoUrl)
                        .networkCachePolicy(CachePolicy.DISABLED)
                        .build()
                }
                SubcomposeAsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    loading = { Icon(Icons.Filled.Business, contentDescription = null, modifier = Modifier.size(32.dp)) },
                    error = { Icon(Icons.Filled.Business, contentDescription = null, modifier = Modifier.size(32.dp)) },
                    success = { SubcomposeAsyncImageContent() },
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = header.organizationName.ifBlank { androidx.compose.ui.res.stringResource(R.string.app_drawer_org_fallback) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = header.branchLabel ?: androidx.compose.ui.res.stringResource(R.string.app_drawer_branch_unknown),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        VertoIconButton(
            onClick = onClose,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Filled.Close, contentDescription = androidx.compose.ui.res.stringResource(R.string.app_drawer_close))
        }
    }
}

@Composable
private fun DrawerDestinationRow(destination: DrawerDestination, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = {
            Text(
                androidx.compose.ui.res.stringResource(destination.labelResId),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        selected = selected,
        onClick = onClick,
        icon = { Icon(destination.icon(), contentDescription = null, modifier = Modifier.size(AppChromeDimensions.dp18)) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        colors = NavigationDrawerItemDefaults.colors(
            unselectedContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun SyncStatusCard(state: DrawerSyncUiState, onClick: () -> Unit) {
    VertoCard(
        contentPadding = PaddingValues(VertoSpacing.none),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(AppChromeDimensions.dp14),
            horizontalArrangement = Arrangement.spacedBy(AppChromeDimensions.dp12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.inProgress) {
                CircularProgressIndicator(modifier = Modifier.size(AppChromeDimensions.dp24), strokeWidth = AppChromeDimensions.dp2)
            } else {
                Icon(Icons.Filled.Sync, contentDescription = null)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(androidx.compose.ui.res.stringResource(R.string.ds_1935dea4e7c6), fontWeight = FontWeight.SemiBold)
                Text(text = state.summary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SyncStatusDialog(
    state: DrawerSyncUiState,
    onSyncNow: () -> Unit,
    onReviewConflicts: () -> Unit,
    onDismiss: () -> Unit,
) {
    val defaultExpanded = state.domains.firstOrNull { it.failed.isNotEmpty() }?.key ?: "general"
    var expandedKey by rememberSaveable(state.attemptedAtMillis) { mutableStateOf<String?>(defaultExpanded) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_sync_status)) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppChromeDimensions.dp6),
            ) {
                item(key = "sync_general") {
                    SyncAccordionHeader(
                        label = androidx.compose.ui.res.stringResource(R.string.app_drawer_sync_general),
                        failedCount = if (state.failureMessage != null) 1 else 0,
                        expanded = expandedKey == "general",
                        onClick = { expandedKey = if (expandedKey == "general") null else "general" },
                    )
                    AnimatedVisibility(visible = expandedKey == "general") {
                        Column(verticalArrangement = Arrangement.spacedBy(AppChromeDimensions.dp4)) {
                            SyncInfoLine(androidx.compose.ui.res.stringResource(R.string.legacy_ui_5eb7d604fe0f), state.summary)
                            SyncInfoLine(androidx.compose.ui.res.stringResource(R.string.legacy_ui_83899a6d1169), formatDateTime(state.attemptedAtMillis))
                            SyncInfoLine(androidx.compose.ui.res.stringResource(R.string.legacy_ui_bf2d2d4e7eb1), formatDateTime(state.lastSuccessfulAtMillis))
                            SyncInfoLine("معلّق", state.pendingCount.toString())
                            SyncInfoLine("يحتاج مراجعة", state.requiresReviewCount.toString())
                            SyncInfoLine("مرفوض", state.rejectedCount.toString())
                            SyncInfoLine(androidx.compose.ui.res.stringResource(R.string.legacy_ui_4deed6312305), state.organizationLabel.ifBlank { "—" })
                            SyncInfoLine(
                                androidx.compose.ui.res.stringResource(R.string.legacy_ui_5400d85be59a),
                                if (state.resumed) androidx.compose.ui.res.stringResource(R.string.legacy_ui_c4830a048dff)
                                else androidx.compose.ui.res.stringResource(R.string.legacy_ui_eff7e7459d29),
                            )
                            state.failureMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }

                items(state.domains, key = DrawerSyncDomainUi::key) { domain ->
                    val expanded = expandedKey == domain.key
                    SyncAccordionHeader(
                        label = domain.label,
                        failedCount = domain.failed.size,
                        expanded = expanded,
                        onClick = { expandedKey = if (expanded) null else domain.key },
                    )
                    AnimatedVisibility(visible = expanded) { SyncDomainDetails(domain) }
                }

                if (state.domains.isEmpty() && !state.inProgress) {
                    item { Text(androidx.compose.ui.res.stringResource(R.string.ds_8d8f3ac3ea42)) }
                }

                if (state.requiresReviewCount > 0L) {
                    item(key = "sync_conflict_review") {
                        TextButton(onClick = onReviewConflicts) { Text("مراجعة التعارضات") }
                    }
                }
            }
        },
        confirmButton = {
            VertoButton(onClick = onSyncNow, enabled = !state.inProgress) {
                Text(if (state.inProgress) androidx.compose.ui.res.stringResource(R.string.legacy_ui_556af01f0bc0) else androidx.compose.ui.res.stringResource(R.string.legacy_ui_d681695c97c6))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_close)) }
        },
    )
}

@Composable
private fun SyncConflictReviewDialog(
    state: SyncConflictReviewUiState,
    onRefresh: () -> Unit,
    onAcceptServer: (String, Long) -> Unit,
    onResendLocal: (String, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مراجعة تعارضات المزامنة") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppChromeDimensions.dp12),
            ) {
                if (state.loading) {
                    item(key = "conflict_loading") { CircularProgressIndicator() }
                }
                state.errorMessage?.let { message ->
                    item(key = "conflict_error") { Text(message, color = MaterialTheme.colorScheme.error) }
                }
                items(state.items, key = { it.conflictId }) { item ->
                    val resolving = state.resolvingConflictId == item.conflictId
                    val canResolve = item.state == "OPEN" && item.mutable && item.outcomeProven && !resolving
                    Column(verticalArrangement = Arrangement.spacedBy(AppChromeDimensions.dp6)) {
                        Text("${item.aggregateType} · ${item.aggregateId}", fontWeight = FontWeight.Bold)
                        Text("سبب التعارض: ${item.reason}", style = MaterialTheme.typography.bodySmall)
                        val localVersionLabel = item.localBaseVersion?.let { "v$it" } ?: "إنشاء جديد"
                        Text("أساس المحلي $localVersionLabel — الخادم v${item.remoteVersion} — صيغة p${item.localPayloadVersion}", style = MaterialTheme.typography.bodySmall)
                        Text("بصمة المحلي: ${item.localFingerprint}", style = MaterialTheme.typography.labelSmall)
                        Text("بصمة الخادم: ${item.remoteFingerprint}", style = MaterialTheme.typography.labelSmall)
                        if (!item.outcomeProven) {
                            Text("مصير الطلب السابق غير مثبت؛ الحسم معطّل.", color = MaterialTheme.colorScheme.error)
                        } else if (!item.mutable || item.state == "DOMAIN_CORRECTION_REQUIRED") {
                            Text("حقيقة غير قابلة للاستبدال؛ يلزم مسار تصحيح/عكس معتمد.", color = MaterialTheme.colorScheme.error)
                        } else if (item.state == "WAITING_REPLACEMENT_RECEIPT") {
                            Text("تم إنشاء تعديل بديل؛ ينتظر إثبات الاستلام.")
                        }
                        if (item.differences.isNotEmpty()) {
                            Text("الحقول المختلفة:", fontWeight = FontWeight.SemiBold)
                            item.differences.take(12).forEach { diff ->
                                Text("${diff.path}: ${diff.local} → ${diff.remote}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text("النسخة المحلية (منقحة):\n${item.localPreviewJson}", style = MaterialTheme.typography.bodySmall)
                        Text("نسخة الخادم (منقحة):\n${item.remotePreviewJson}", style = MaterialTheme.typography.bodySmall)
                        if (canResolve) {
                            Row(horizontalArrangement = Arrangement.spacedBy(AppChromeDimensions.dp8)) {
                                TextButton(onClick = { onAcceptServer(item.conflictId, item.remoteVersion) }) {
                                    Text("اعتماد نسخة الخادم")
                                }
                                TextButton(onClick = { onResendLocal(item.conflictId, item.remoteVersion) }) {
                                    Text("إعادة إرسال تعديلي")
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
                if (!state.loading && state.items.isEmpty() && state.errorMessage == null) {
                    item(key = "conflict_empty") { Text("لا توجد تعارضات تحتاج قرارًا.") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onRefresh) { Text("تحديث") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )
}

@Composable
private fun SyncAccordionHeader(label: String, failedCount: Int, expanded: Boolean, onClick: () -> Unit) {
    val expansionState = androidx.compose.ui.res.stringResource(
        if (expanded) R.string.app_drawer_state_expanded else R.string.app_drawer_state_collapsed,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics {
                role = Role.Button
                stateDescription = expansionState
            }
            .clickable(onClick = onClick)
            .padding(horizontal = AppChromeDimensions.dp8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppChromeDimensions.dp8),
    ) {
        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        if (failedCount > 0) {
            Text(
                androidx.compose.ui.res.stringResource(R.string.app_drawer_sync_failed_count, failedCount),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
    }
}

@Composable
private fun SyncInfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Text(value, modifier = Modifier.padding(start = AppChromeDimensions.dp12))
    }
}

@Composable
private fun SyncDomainDetails(domain: DrawerSyncDomainUi) {
    Column(verticalArrangement = Arrangement.spacedBy(AppChromeDimensions.dp4)) {
        domain.failed.forEach {
            Text(androidx.compose.ui.res.stringResource(R.string.ds_2b51b9ab8c68, it.label), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        domain.succeeded.forEach {
            Text(androidx.compose.ui.res.stringResource(R.string.ds_98d3f752d47f, it.label), style = MaterialTheme.typography.bodySmall)
        }
        domain.skipped.forEach {
            Text(androidx.compose.ui.res.stringResource(R.string.ds_38c5ec772f99, it.label), style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()
    }
}

@Composable
private fun DrawerSectionHeader(section: DrawerSection, expanded: Boolean, onClick: () -> Unit) {
    val expansionState = androidx.compose.ui.res.stringResource(
        if (expanded) R.string.app_drawer_state_expanded else R.string.app_drawer_state_collapsed,
    )
    NavigationDrawerItem(
        label = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(androidx.compose.ui.res.stringResource(section.labelResId), fontWeight = FontWeight.SemiBold)
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            }
        },
        selected = false,
        onClick = onClick,
        icon = { Icon(section.icon(), contentDescription = null) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { stateDescription = expansionState },
        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = MaterialTheme.colorScheme.surface),
    )
}

private fun DrawerSection.icon(): ImageVector = when (this) {
    DrawerSection.SALES_CUSTOMERS -> Icons.Filled.PointOfSale
    DrawerSection.PURCHASES_SUPPLIERS -> Icons.Filled.ShoppingCart
    DrawerSection.INVENTORY_PRICING -> Icons.Filled.Inventory2
    DrawerSection.FINANCE_REPORTS -> Icons.Filled.AccountBalance
    DrawerSection.MANAGEMENT -> Icons.Filled.AdminPanelSettings
    DrawerSection.SETTINGS -> Icons.Filled.Settings
}

private fun DrawerDestination.icon(): ImageVector = when (iconKey) {
    DrawerIconKey.CLIENTS -> Icons.Filled.Person
    DrawerIconKey.SALES_INVOICES -> Icons.Filled.ReceiptLong
    DrawerIconKey.LOCAL_SUPPLIERS -> Icons.Filled.Storefront
    DrawerIconKey.INTERNATIONAL_SUPPLIERS -> Icons.Filled.Public
    DrawerIconKey.LOCAL_PURCHASE_INVOICES -> Icons.Filled.ShoppingCart
    DrawerIconKey.INTERNATIONAL_PURCHASE_INVOICES -> Icons.Filled.Public
    DrawerIconKey.SHIPMENTS -> Icons.Filled.LocalShipping
    DrawerIconKey.INVENTORY -> Icons.Filled.Inventory2
    DrawerIconKey.CATEGORIES -> Icons.Filled.Category
    DrawerIconKey.PRICE_LIST -> Icons.Filled.ListAlt
    DrawerIconKey.BULK_PRICE_EDIT -> Icons.Filled.PriceChange
    DrawerIconKey.EXPENSES -> Icons.Filled.AccountBalanceWallet
    DrawerIconKey.REPORTS -> Icons.Filled.Analytics
    DrawerIconKey.AUDIT_LOG -> Icons.Filled.FactCheck
    DrawerIconKey.OPTIMAL -> Icons.Filled.Hub
    DrawerIconKey.BENZINE -> Icons.Filled.LocalGasStation
    DrawerIconKey.EMPLOYEES -> Icons.Filled.AdminPanelSettings
    DrawerIconKey.EDUCATION -> Icons.Filled.School
    DrawerIconKey.TEAM_OBSERVATIONS -> Icons.Filled.FactCheck
    DrawerIconKey.PROFILE -> Icons.Filled.Person
    DrawerIconKey.ORGANIZATION -> Icons.Filled.Business
    DrawerIconKey.APPEARANCE -> Icons.Filled.Visibility
    DrawerIconKey.NOTIFICATIONS -> Icons.Filled.Notifications
    DrawerIconKey.PRINT -> Icons.Filled.Print
    DrawerIconKey.THEMES -> Icons.Filled.ColorLens
}
