package com.verto.app.feature.inventory.presentation.inventory

import com.verto.app.feature.inventory.application.model.InventoryItemView
import com.verto.app.feature.inventory.application.model.InventoryUnitView

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import com.verto.app.ui.components.*
import com.verto.app.ui.theme.*
import java.util.UUID

@Composable
internal fun UnitButton(
    hasUnit:  Boolean,
    unitName: String,
    unitQty:  String,
    onEdit:   () -> Unit,
    onRemove: () -> Unit
) {
    if (hasUnit) {
        Surface(
            shape  = RoundedCornerShape(InventoryDimensions.dp12),
            color  = AccentDim,
            border = BorderStroke(InventoryDimensions.dp1, AccentPrimary.copy(0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = InventoryDimensions.dp14, vertical = InventoryDimensions.dp10),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_629ab4482661, unitName), color = AccentLight, fontSize = InventoryTextScale.sp14, fontWeight = FontWeight.SemiBold)
                    if (unitQty.isNotBlank()) {
                        Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_01832579160f, unitQty), color = TextSecondary, fontSize = InventoryTextScale.sp12)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(InventoryDimensions.dp4)) {
                    TextButton(
                        onClick        = onEdit,
                        contentPadding = PaddingValues(horizontal = InventoryDimensions.dp8, vertical = InventoryDimensions.dp0)
                    ) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_edit), color = AccentPrimary, fontSize = InventoryTextScale.sp12) }
                    TextButton(
                        onClick        = onRemove,
                        contentPadding = PaddingValues(horizontal = InventoryDimensions.dp8, vertical = InventoryDimensions.dp0)
                    ) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_remove), color = MaterialTheme.colorScheme.error, fontSize = InventoryTextScale.sp12) }
                }
            }
        }
    } else {
        VertoOutlinedButton(
            onClick  = onEdit,
            modifier = Modifier.fillMaxWidth().height(InventoryDimensions.dp48),
            shape    = RoundedCornerShape(InventoryDimensions.dp12),
            border   = BorderStroke(InventoryDimensions.dp1, BorderColor),
            colors   = ButtonDefaults.outlinedButtonColors(containerColor = BgCard)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_09dffe980782), color = TextMuted, fontSize = InventoryTextScale.sp14)
                Icon(Icons.Filled.Add, contentDescription = null, tint = TextMuted, modifier = Modifier.size(InventoryDimensions.dp18))
            }
        }
    }
}

// ─────────────────────────────────────────────────────
// Dialog — إعداد الوحدة
// ─────────────────────────────────────────────────────
@Composable
internal fun UnitDialog(
    initialName:     String,
    initialQty:      String,
    initialLinkedId: String?,
    allItems:        List<InventoryItemView>,
    onDismiss:       () -> Unit,
    onSave:          (name: String, qty: String, linkedId: String) -> Unit
) {
    var unitName        by remember { mutableStateOf(initialName) }
    var unitQty         by remember { mutableStateOf(initialQty) }
    var linkedItem      by remember { mutableStateOf(allItems.find { it.id == initialLinkedId }) }
    var itemSearchQuery by remember { mutableStateOf(allItems.find { it.id == initialLinkedId }?.name ?: "") }
    val filteredItems   = remember(itemSearchQuery, allItems) {
        if (itemSearchQuery.isBlank()) emptyList()
        else allItems.filter { it.name.contains(itemSearchQuery, ignoreCase = true) }.take(6)
    }

    val isValid = unitName.isNotBlank() &&
            unitQty.toDoubleOrNull() != null &&
            (unitQty.toDoubleOrNull() ?: 0.0) > 0 &&
            linkedItem != null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = BgCard,
        title = {
            Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_6a7ef7aff897), color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(InventoryDimensions.dp12)) {

                // اسم الوحدة
                VertoOutlinedTextField(
                    value         = unitName,
                    onValueChange = { unitName = it },
                    label         = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_5c2ae54e45d5), color = TextMuted, fontSize = InventoryTextScale.sp13) },
                    placeholder   = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_98fb38194248), color = TextMuted, fontSize = InventoryTextScale.sp13) },
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
                    modifier = Modifier.fillMaxWidth()
                )

                // عدد القطع في الوحدة
                VertoOutlinedTextField(
                    value         = unitQty,
                    onValueChange = { unitQty = it },
                    label         = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_b7065380b4cc), color = TextMuted, fontSize = InventoryTextScale.sp13) },
                    placeholder   = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_158f6654aebb), color = TextMuted, fontSize = InventoryTextScale.sp13) },
                    singleLine    = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    shape  = RoundedCornerShape(InventoryDimensions.dp12),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = AccentPrimary,
                        unfocusedBorderColor    = BorderColor,
                        focusedContainerColor   = BgDeep,
                        unfocusedContainerColor = BgDeep,
                        focusedTextColor        = TextPrimary,
                        unfocusedTextColor      = TextPrimary,
                        cursorColor             = AccentPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // البند المرتبط (القطعة) — بحث حر
                Column(verticalArrangement = Arrangement.spacedBy(InventoryDimensions.dp4)) {
                    VertoOutlinedTextField(
                        value         = itemSearchQuery,
                        onValueChange = { itemSearchQuery = it; linkedItem = null },
                        label         = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_a110ee7d3866), color = TextMuted, fontSize = InventoryTextScale.sp13) },
                        placeholder   = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_defe5f59699b), color = TextMuted, fontSize = InventoryTextScale.sp13) },
                        singleLine    = true,
                        trailingIcon  = {
                            if (linkedItem != null)
                                Icon(Icons.Filled.CheckCircle, null, tint = SuccessColor, modifier = Modifier.size(InventoryDimensions.dp18))
                        },
                        shape  = RoundedCornerShape(InventoryDimensions.dp12),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor      = if (linkedItem != null) SuccessColor else AccentPrimary,
                            unfocusedBorderColor    = if (linkedItem != null) SuccessColor.copy(0.5f) else BorderColor,
                            focusedContainerColor   = BgDeep,
                            unfocusedContainerColor = BgDeep,
                            focusedTextColor        = TextPrimary,
                            unfocusedTextColor      = TextPrimary,
                            cursorColor             = AccentPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    // نتائج البحث
                    if (filteredItems.isNotEmpty() && linkedItem == null) {
                        Surface(
                            shape  = RoundedCornerShape(InventoryDimensions.dp10),
                            color  = BgDeep,
                            border = BorderStroke(InventoryDimensions.dp1, BorderColor)
                        ) {
                            Column {
                                filteredItems.forEach { item ->
                                    TextButton(
                                        onClick  = { linkedItem = item; itemSearchQuery = item.name },
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(horizontal = InventoryDimensions.dp12, vertical = InventoryDimensions.dp6)
                                    ) {
                                        Text(
                                            item.name,
                                            color    = TextPrimary,
                                            fontSize = InventoryTextScale.sp14,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    if (item != filteredItems.last())
                                        Divider(color = BorderColor.copy(0.4f))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick  = { onSave(unitName.trim(), unitQty.trim(), checkNotNull(linkedItem).id) },
                enabled  = isValid
            ) {
                Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_save), color = AccentPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextSecondary)
            }
        }
    )
}

// ─────────────────────────────────────────────────────
// زر اختيار التصنيف (single-select)
// ─────────────────────────────────────────────────────
@Composable
internal fun CategoryPickerButton(
    selectedCategory: String,
    onClick: () -> Unit
) {
    val hasCategory = selectedCategory.isNotBlank()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape  = RoundedCornerShape(InventoryDimensions.dp12),
        color  = if (hasCategory) AccentDim else BgCard,
        border = BorderStroke(
            InventoryDimensions.dp1,
            if (hasCategory) AccentPrimary.copy(0.4f) else BorderColor
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = InventoryDimensions.dp14, vertical = InventoryDimensions.dp12),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(InventoryDimensions.dp2)) {
                Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_category), color = TextMuted, fontSize = InventoryTextScale.sp12)
                Text(
                    text     = if (hasCategory) selectedCategory else androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_v298_bf85a6454f86),
                    color    = if (hasCategory) AccentLight else TextMuted,
                    fontSize = InventoryTextScale.sp14,
                    fontWeight = if (hasCategory) FontWeight.Medium else FontWeight.Normal
                )
            }
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = null,
                tint = if (hasCategory) AccentPrimary else TextMuted,
                modifier = Modifier.size(InventoryDimensions.dp20)
            )
        }
    }
}

// ─────────────────────────────────────────────────────
// Dialog اختيار التصنيف من القائمة
// ─────────────────────────────────────────────────────
@Composable
internal fun CategoryPickerDialog(
    categories: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = BgCard,
        title = {
            Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_a9d487241b73), color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            if (categories.isEmpty()) {
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_7b5ad10f0f58),
                    color    = TextMuted,
                    fontSize = InventoryTextScale.sp13
                )
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    TextButton(
                        onClick  = onClear,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = InventoryDimensions.dp10, horizontal = InventoryDimensions.dp8)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_2aa3693faed8), color = TextMuted, fontSize = InventoryTextScale.sp14)
                            if (selected.isBlank()) {
                                Icon(Icons.Default.Check, null, tint = AccentPrimary, modifier = Modifier.size(InventoryDimensions.dp18))
                            }
                        }
                    }
                    Divider(color = BorderColor.copy(0.5f))
                    categories.forEach { cat ->
                        TextButton(
                            onClick  = { onSelect(cat) },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = InventoryDimensions.dp10, horizontal = InventoryDimensions.dp8)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Text(cat, color = TextPrimary, fontSize = InventoryTextScale.sp14)
                                if (selected == cat) {
                                    Icon(Icons.Default.Check, null, tint = AccentPrimary, modifier = Modifier.size(InventoryDimensions.dp18))
                                }
                            }
                        }
                        Divider(color = BorderColor.copy(0.5f))
                    }       
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = MaterialTheme.colorScheme.error)
            }
        }
    )
}
