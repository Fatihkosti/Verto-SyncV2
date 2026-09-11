package com.verto.app.feature.inventory.presentation.pricelist

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.inventory.application.model.InventoryItemView
import com.verto.app.feature.inventory.application.model.PriceListTemplateView
import com.verto.app.pdf.PriceItem
import com.verto.app.pdf.ShopInfo
import com.verto.app.pdf.generatePriceListPdf
import com.verto.app.ui.components.VertoTopBar
import com.verto.app.ui.theme.AccentDim
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BgDeep
import com.verto.app.ui.theme.BgSurface
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextOnAccent
import com.verto.app.ui.theme.TextPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PriceListScreen(
    onBack: () -> Unit,
    vm: PriceListViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val draftItems by vm.draftItems.collectAsStateWithLifecycle()
    val inventoryItems by vm.inventoryItems.collectAsStateWithLifecycle()
    val templates by vm.templates.collectAsStateWithLifecycle()
    val orgSettings by vm.orgSettings.collectAsStateWithLifecycle()
    val userName by vm.userName.collectAsStateWithLifecycle()
    val userPhone by vm.userPhone.collectAsStateWithLifecycle()
    val priceListFont by vm.priceListFont.collectAsStateWithLifecycle()
    val priceListFontSize by vm.priceListFontSize.collectAsStateWithLifecycle()
    val permissions by vm.permissions.collectAsStateWithLifecycle()
    val permissionError by vm.permissionError.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val canEdit = permissions?.inventoryPrice == true
    val canExport = permissions?.inventoryExport == true

    var showAddItem by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    var showTemplateEditor by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<PriceListTemplateView?>(null) }
    var editorInitialIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var deletingTemplate by remember { mutableStateOf<PriceListTemplateView?>(null) }
    var showDate by remember { mutableStateOf(true) }
    var showPrices by remember { mutableStateOf(true) }

    LaunchedEffect(permissionError) {
        permissionError?.let {
            snackbarHost.showSnackbar(it)
            vm.clearPermissionError()
        }
    }
    LaunchedEffect(message) {
        message?.let {
            snackbarHost.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = BgDeep,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                VertoTopBar(
                    title = stringResource(com.verto.feature.inventory.R.string.price_list_title),
                    onBack = onBack,
                )
            }

            if (canEdit) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { showAddItem = true },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary, contentColor = TextOnAccent),
                        ) {
                            Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(com.verto.feature.inventory.R.string.price_list_add_item))
                        }
                        OutlinedButton(onClick = { showTemplates = true }, modifier = Modifier.weight(1f).height(48.dp)) {
                            Text(stringResource(com.verto.feature.inventory.R.string.price_list_templates))
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            stringResource(com.verto.feature.inventory.R.string.price_list_selected_count, draftItems.size),
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(com.verto.feature.inventory.R.string.price_list_empty_subtitle),
                            color = TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (canEdit && draftItems.isNotEmpty()) {
                        TextButton(onClick = vm::clearDraft) {
                            Text(stringResource(com.verto.feature.inventory.R.string.price_list_clear))
                        }
                    }
                }
            }

            if (draftItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                stringResource(com.verto.feature.inventory.R.string.price_list_empty_title),
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                stringResource(com.verto.feature.inventory.R.string.price_list_empty_subtitle),
                                color = TextMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(draftItems, key = { _, item -> item.inventoryItemId }) { index, item ->
                    PriceDraftItemCard(
                        item = item,
                        index = index,
                        canEdit = canEdit,
                        onPriceChange = { value -> vm.updateDraftPrice(item.inventoryItemId, value) },
                        onDelete = { vm.removeDraftItem(item.inventoryItemId) },
                    )
                }
            }

            if (canEdit && draftItems.isNotEmpty()) {
                item {
                    OutlinedButton(
                        onClick = {
                            editingTemplate = null
                            editorInitialIds = draftItems.map { it.inventoryItemId }
                            showTemplateEditor = true
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(46.dp),
                    ) {
                        Text(stringResource(com.verto.feature.inventory.R.string.price_list_save_as_template))
                    }
                }
            }

            if (draftItems.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(BgCard)
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(stringResource(com.verto.feature.inventory.R.string.price_list_show_date), color = TextPrimary)
                            Switch(checked = showDate, onCheckedChange = { showDate = it })
                        }
                        HorizontalDivider(color = BorderColor)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(stringResource(com.verto.feature.inventory.R.string.price_list_show_prices), color = TextPrimary)
                            Switch(checked = showPrices, onCheckedChange = { showPrices = it })
                        }
                    }
                }
            }

            if (draftItems.isNotEmpty() && canExport) {
                item {
                    Button(
                        onClick = {
                            scope.launch {
                                if (!vm.canExportPdf()) return@launch
                                val date = if (showDate) SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH).format(Date()) else null
                                val shop = ShopInfo(
                                    name = orgSettings.shopName,
                                    address = orgSettings.address,
                                    phone = orgSettings.shopPhone,
                                    userName = userName,
                                    userPhone = userPhone,
                                )
                                val pdfItems = draftItems.map {
                                    PriceItem(name = it.name, price = it.price, partNumber = it.partNumber)
                                }
                                val file = withContext(Dispatchers.Default) {
                                    generatePriceListPdf(
                                        context = context,
                                        shop = shop,
                                        items = pdfItems,
                                        dateString = date,
                                        includePrices = showPrices,
                                        font = priceListFont,
                                        fontSize = priceListFontSize,
                                    )
                                }
                                vm.sharePdf(file)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary, contentColor = TextOnAccent),
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(com.verto.feature.inventory.R.string.price_list_pdf), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showAddItem) {
        AddInventoryItemDialog(
            vm = vm,
            selectedIds = draftItems.mapTo(mutableSetOf()) { it.inventoryItemId },
            onDismiss = { showAddItem = false; vm.setInventoryQuery("") },
        )
    }

    if (showTemplates) {
        PriceListTemplatesDialog(
            templates = templates,
            inventoryItems = inventoryItems,
            onAdd = { template, availableOnly ->
                vm.addTemplate(template, availableOnly)
                showTemplates = false
            },
            onFavorite = vm::toggleFavorite,
            onEdit = {
                editingTemplate = it
                editorInitialIds = it.inventoryItemIds
                showTemplates = false
                showTemplateEditor = true
            },
            onDelete = {
                deletingTemplate = it
                showTemplates = false
            },
            onNew = {
                editingTemplate = null
                editorInitialIds = emptyList()
                showTemplates = false
                showTemplateEditor = true
            },
            onDismiss = { showTemplates = false },
        )
    }

    if (showTemplateEditor) {
        TemplateEditorDialog(
            existing = editingTemplate,
            initialIds = editorInitialIds,
            inventoryItems = inventoryItems,
            onSave = { name, ids ->
                vm.saveTemplate(name, ids, editingTemplate)
                showTemplateEditor = false
                editingTemplate = null
                editorInitialIds = emptyList()
            },
            onDismiss = {
                showTemplateEditor = false
                editingTemplate = null
                editorInitialIds = emptyList()
            },
        )
    }

    deletingTemplate?.let { template ->
        AlertDialog(
            onDismissRequest = { deletingTemplate = null },
            title = {
                Text(stringResource(com.verto.feature.inventory.R.string.price_list_template_delete_confirm, template.name))
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteTemplate(template); deletingTemplate = null }) {
                    Text(stringResource(com.verto.feature.inventory.R.string.price_list_template_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTemplate = null }) {
                    Text(stringResource(com.verto.core.designsystem.R.string.verto_action_cancel))
                }
            },
        )
    }

}

@Composable
private fun AddInventoryItemDialog(
    vm: PriceListViewModel,
    selectedIds: Set<String>,
    onDismiss: () -> Unit,
) {
    val query by vm.inventoryQuery.collectAsStateWithLifecycle()
    val results by vm.inventoryResults.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(com.verto.feature.inventory.R.string.price_list_add_item)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = vm::setInventoryQuery,
                    label = { Text(stringResource(com.verto.feature.inventory.R.string.price_list_search_inventory)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    if (results.isEmpty()) {
                        item {
                            Text(
                                stringResource(com.verto.feature.inventory.R.string.price_list_no_search_results),
                                color = TextMuted,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                    items(results.take(50), key = { it.id }) { item ->
                        val selected = item.id in selectedIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = !selected) { vm.addDirectItem(item) }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, color = TextPrimary, fontWeight = FontWeight.Medium)
                                Text(
                                    if (item.quantity > 0) stringResource(com.verto.feature.inventory.R.string.price_list_available, item.quantity)
                                    else stringResource(com.verto.feature.inventory.R.string.price_list_out_of_stock),
                                    color = if (item.quantity > 0) SuccessColor else MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (selected) Icon(Icons.Filled.CheckCircle, null, tint = SuccessColor)
                            else Icon(Icons.Filled.Add, null, tint = AccentPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("تم") } },
    )
}

@Composable
private fun PriceListTemplatesDialog(
    templates: List<PriceListTemplateView>,
    inventoryItems: List<InventoryItemView>,
    onAdd: (PriceListTemplateView, Boolean) -> Unit,
    onFavorite: (PriceListTemplateView) -> Unit,
    onEdit: (PriceListTemplateView) -> Unit,
    onDelete: (PriceListTemplateView) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    val inventoryById = remember(inventoryItems) { inventoryItems.associateBy { it.id } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(com.verto.feature.inventory.R.string.price_list_templates_title)) },
        text = {
            if (templates.isEmpty()) {
                Text(stringResource(com.verto.feature.inventory.R.string.price_list_template_empty), color = TextMuted)
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(templates, key = { it.id }) { template ->
                        val available = template.inventoryItemIds.count { inventoryById[it]?.quantity?.let { q -> q > 0 } == true }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(BgSurface)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(template.name, color = TextPrimary, fontWeight = FontWeight.Bold)
                                    Text(
                                        stringResource(
                                            com.verto.feature.inventory.R.string.price_list_template_items_count,
                                            template.inventoryItemIds.size,
                                            available,
                                        ),
                                        color = TextMuted,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                IconButton(onClick = { onFavorite(template) }) {
                                    Icon(
                                        if (template.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                        contentDescription = null,
                                        tint = AccentPrimary,
                                    )
                                }
                                IconButton(onClick = { onEdit(template) }) {
                                    Icon(Icons.Filled.Edit, stringResource(com.verto.feature.inventory.R.string.price_list_template_edit), tint = AccentPrimary)
                                }
                                IconButton(onClick = { onDelete(template) }) {
                                    Icon(Icons.Filled.DeleteOutline, stringResource(com.verto.feature.inventory.R.string.price_list_template_delete), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onAdd(template, true) },
                                    enabled = available > 0,
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                ) {
                                    Text(stringResource(com.verto.feature.inventory.R.string.price_list_template_available, available))
                                }
                                OutlinedButton(
                                    onClick = { onAdd(template, false) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                ) {
                                    Text(stringResource(com.verto.feature.inventory.R.string.price_list_template_all, template.inventoryItemIds.size))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onNew) {
                Text(stringResource(com.verto.feature.inventory.R.string.price_list_template_new))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )
}

@Composable
private fun TemplateEditorDialog(
    existing: PriceListTemplateView?,
    initialIds: List<String>,
    inventoryItems: List<InventoryItemView>,
    onSave: (String, List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var selected by remember(existing?.id, initialIds) { mutableStateOf(initialIds.toSet()) }
    var query by remember(existing?.id) { mutableStateOf("") }
    var addingFromInventory by remember(existing?.id) { mutableStateOf(existing == null && initialIds.isEmpty()) }
    val normalized = query.trim()
    val inventoryById = remember(inventoryItems) { inventoryItems.associateBy { it.id } }
    val selectedItems = remember(inventoryItems, selected) {
        selected.mapNotNull(inventoryById::get)
    }
    val inventoryResults = remember(inventoryItems, normalized) {
        val source = inventoryItems.filterNot { it.id in selected }
        if (normalized.isBlank()) source
        else source.filter {
            it.name.contains(normalized, ignoreCase = true) || it.partNumber.contains(normalized, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existing == null) stringResource(com.verto.feature.inventory.R.string.price_list_template_new)
                else stringResource(com.verto.feature.inventory.R.string.price_list_template_edit)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(com.verto.feature.inventory.R.string.price_list_template_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(com.verto.feature.inventory.R.string.price_list_template_selected_count, selected.size),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!addingFromInventory) {
                    OutlinedButton(
                        onClick = { addingFromInventory = true; query = "" },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(com.verto.feature.inventory.R.string.price_list_template_add_from_inventory))
                    }
                    HorizontalDivider(color = BorderColor)
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp)) {
                        if (selectedItems.isEmpty()) {
                            item {
                                Text(
                                    stringResource(com.verto.feature.inventory.R.string.price_list_template_no_items),
                                    color = TextMuted,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        }
                        items(selectedItems, key = { it.id }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selected = selected - item.id }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = true,
                                    onCheckedChange = { checked -> if (!checked) selected = selected - item.id },
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, color = TextPrimary)
                                    if (item.partNumber.isNotBlank()) {
                                        Text(item.partNumber, color = TextMuted, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Text(
                                    if (item.quantity > 0) "${item.quantity}" else "0",
                                    color = if (item.quantity > 0) SuccessColor else TextMuted,
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(com.verto.feature.inventory.R.string.price_list_search_inventory)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    HorizontalDivider(color = BorderColor)
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                        if (inventoryResults.isEmpty()) {
                            item {
                                Text(
                                    stringResource(com.verto.feature.inventory.R.string.price_list_no_search_results),
                                    color = TextMuted,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        }
                        items(inventoryResults.take(80), key = { it.id }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selected = selected + item.id }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Add, null, tint = AccentPrimary, modifier = Modifier.size(40.dp).padding(9.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, color = TextPrimary)
                                    if (item.partNumber.isNotBlank()) {
                                        Text(item.partNumber, color = TextMuted, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Text(
                                    if (item.quantity > 0) "${item.quantity}" else "0",
                                    color = if (item.quantity > 0) SuccessColor else TextMuted,
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = { addingFromInventory = false; query = "" },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(stringResource(com.verto.feature.inventory.R.string.price_list_template_done_adding))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), selected.toList()) },
                enabled = name.isNotBlank() && selected.isNotEmpty(),
            ) {
                Text(
                    if (existing == null) stringResource(com.verto.feature.inventory.R.string.price_list_template_create)
                    else stringResource(com.verto.feature.inventory.R.string.price_list_template_save)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(com.verto.core.designsystem.R.string.verto_action_cancel)) }
        },
    )
}
