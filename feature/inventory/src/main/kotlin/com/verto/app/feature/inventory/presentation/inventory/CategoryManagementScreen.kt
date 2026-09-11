package com.verto.app.feature.inventory.presentation.inventory

import com.verto.app.ui.components.VertoOutlinedTextField
import com.verto.app.ui.components.VertoButton

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.inventory.application.model.CategoryItem

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import com.verto.app.ui.components.VertoEmptyState
import com.verto.app.ui.components.VertoEmptyStateVariant
import com.verto.app.ui.components.VertoTopBar
import com.verto.app.ui.theme.*
import kotlinx.coroutines.launch
import com.verto.app.ui.components.VertoIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    onBack: () -> Unit,
    vm: InventoryViewModel = hiltViewModel()
) {
    val categories     by vm.allMasterCategories.collectAsStateWithLifecycle()
    val snackbarHost   = remember { SnackbarHostState() }
    val scope          = rememberCoroutineScope()

    var showDeleteDialog by remember { mutableStateOf<CategoryItem?>(null) }
    var editingId        by remember { mutableStateOf<String?>(null) }
    var editingName      by remember { mutableStateOf("") }
    var newCategoryName  by remember { mutableStateOf("") }

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHost) },
        containerColor = BgDeep
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            VertoTopBar(title = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_9f22ffde0566), onBack = onBack)

            // ── قائمة التصنيفات ──────────────────────────
            if (categories.isEmpty() && editingId == null) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    VertoEmptyState(
                        iconText = "🏷️",
                        variant = VertoEmptyStateVariant.Plain,
                        message = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_47f1a4429bb0)
                    )
                }
            } else {
                LazyColumn(
                    modifier            = Modifier.weight(1f),
                    contentPadding      = PaddingValues(horizontal = InventoryDimensions.dp16, vertical = InventoryDimensions.dp12),
                    verticalArrangement = Arrangement.spacedBy(InventoryDimensions.dp8)
                ) {
                    items(categories, key = { it.id }) { category ->
                        if (editingId == category.id) {
                            // ── وضع التعديل المباشر ──────────────
                            EditCategoryRow(
                                name         = editingName,
                                onNameChange = { editingName = it },
                                onSave       = {
                                    if (editingName.isNotBlank()) {
                                        vm.updateMasterCategory(category.id, editingName)
                                    }
                                    editingId   = null
                                    editingName = ""
                                },
                                onCancel     = { editingId = null; editingName = "" }
                            )
                        } else {
                            // ── وضع العرض مع السحب للحذف ─────────
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value != SwipeToDismissBoxValue.Settled) {
                                        showDeleteDialog = category
                                    }
                                    false
                                }
                            )
                            SwipeToDismissBox(
                                state                       = dismissState,
                                enableDismissFromStartToEnd = true,
                                enableDismissFromEndToStart = true,
                                backgroundContent           = {
                                    val align = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart)
                                        Alignment.CenterEnd else Alignment.CenterStart
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(InventoryDimensions.dp12))
                                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                                        contentAlignment = align
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint     = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(horizontal = InventoryDimensions.dp20)
                                        )
                                    }
                                }
                            ) {
                                CategoryItemRow(
                                    name        = category.name,
                                    onLongPress = {
                                        editingId   = category.id
                                        editingName = category.name
                                    }
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(InventoryDimensions.dp8)) }
                }
            }

            // ── صف الإضافة في الأسفل ─────────────────────
            Divider(color = BorderColor, modifier = Modifier.padding(horizontal = InventoryDimensions.dp16))
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(BgCard)
                    .padding(horizontal = InventoryDimensions.dp16, vertical = InventoryDimensions.dp12),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(InventoryDimensions.dp8)
            ) {
                VertoOutlinedTextField(
                    value         = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    placeholder   = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_ea1329523e64), color = TextMuted, fontSize = InventoryTextScale.sp13) },
                    singleLine    = true,
                    shape         = RoundedCornerShape(InventoryDimensions.dp12),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = AccentPrimary,
                        unfocusedBorderColor    = BorderColor,
                        focusedContainerColor   = BgDeep,
                        unfocusedContainerColor = BgDeep,
                        focusedTextColor        = TextPrimary,
                        unfocusedTextColor      = TextPrimary,
                        cursorColor             = AccentPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )
                VertoButton(
                    onClick = {
                        val trimmed = newCategoryName.trim()
                        if (trimmed.isNotBlank()) {
                            vm.addMasterCategory(trimmed)
                            newCategoryName = ""
                            scope.launch { snackbarHost.showSnackbar("تمت الإضافة بنجاح") }
                        }
                    },
                    enabled  = newCategoryName.isNotBlank(),
                    shape    = RoundedCornerShape(InventoryDimensions.dp12),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = AccentPrimary,
                        contentColor           = TextPrimary,
                        disabledContainerColor = AccentPrimary.copy(0.3f),
                        disabledContentColor   = TextMuted
                    )
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_add), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    // ── Dialog تأكيد الحذف ───────────────────────────────
    showDeleteDialog?.let { cat ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            containerColor   = BgCard,
            title  = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_835b649f6c2b), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text   = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_47ece41c3a21, cat.name), color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteMasterCategory(cat.id)
                    showDeleteDialog = null
                }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_delete), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextSecondary)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────
// صف عرض التصنيف (مع long press للتعديل)
// ─────────────────────────────────────────────────────
@Composable
private fun CategoryItemRow(
    name: String,
    onLongPress: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(InventoryDimensions.dp12),
        color    = BgCard,
        border   = BorderStroke(InventoryDimensions.dp1, BorderColor)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onLongPress() })
                }
                .padding(horizontal = InventoryDimensions.dp16, vertical = InventoryDimensions.dp14),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text       = name,
                color      = TextPrimary,
                fontSize   = InventoryTextScale.sp15,
                fontWeight = FontWeight.Medium
            )
            Text(
                text     = androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_9f72554d71a2),
                color    = TextMuted,
                fontSize = InventoryTextScale.sp11
            )
        }
    }
}

// ─────────────────────────────────────────────────────
// صف تعديل التصنيف (inline)
// ─────────────────────────────────────────────────────
@Composable
private fun EditCategoryRow(
    name: String,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(InventoryDimensions.dp12),
        color    = AccentDim,
        border   = BorderStroke(InventoryDimensions.dp1, AccentPrimary.copy(0.5f))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = InventoryDimensions.dp12, vertical = InventoryDimensions.dp8),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(InventoryDimensions.dp8)
        ) {
            VertoOutlinedTextField(
                value         = name,
                onValueChange = onNameChange,
                singleLine    = true,
                shape         = RoundedCornerShape(InventoryDimensions.dp10),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor      = AccentPrimary,
                    unfocusedBorderColor    = BorderColor,
                    focusedContainerColor   = BgDeep,
                    unfocusedContainerColor = BgDeep,
                    focusedTextColor        = TextPrimary,
                    unfocusedTextColor      = TextPrimary,
                    cursorColor             = AccentPrimary
                ),
                modifier = Modifier.weight(1f)
            )
            VertoIconButton(
                onClick = onSave,
                enabled = name.isNotBlank()
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_save),
                    tint = if (name.isNotBlank()) SuccessColor else TextMuted
                )
            }
            VertoIconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
