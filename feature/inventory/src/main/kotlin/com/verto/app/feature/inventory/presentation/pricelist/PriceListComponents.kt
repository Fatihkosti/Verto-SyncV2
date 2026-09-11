package com.verto.app.feature.inventory.presentation.pricelist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.verto.app.feature.inventory.application.model.PriceListDraftItemView
import com.verto.app.ui.theme.AccentDim
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary

@Composable
internal fun PriceDraftItemCard(
    item: PriceListDraftItemView,
    index: Int,
    canEdit: Boolean,
    onPriceChange: (Double) -> Unit,
    onDelete: () -> Unit,
) {
    var priceText by remember(item.inventoryItemId) { mutableStateOf(editablePrice(item.price)) }
    var priceFocused by remember(item.inventoryItemId) { mutableStateOf(false) }

    LaunchedEffect(item.price, priceFocused) {
        if (!priceFocused) priceText = editablePrice(item.price)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
            .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AccentDim),
            contentAlignment = Alignment.Center,
        ) {
            Text("${index + 1}", color = AccentPrimary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.name, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            if (item.partNumber.isNotBlank()) {
                Text(
                    stringResource(com.verto.feature.inventory.R.string.price_list_part_number, item.partNumber),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                if (item.quantity > 0) stringResource(com.verto.feature.inventory.R.string.price_list_available, item.quantity)
                else stringResource(com.verto.feature.inventory.R.string.price_list_out_of_stock),
                color = if (item.quantity > 0) SuccessColor else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
            )
            if (item.priceOverridden) {
                Text(
                    stringResource(com.verto.feature.inventory.R.string.price_list_price_override),
                    color = AccentPrimary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            if (canEdit) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { raw ->
                            if (raw.count { it == '.' || it == ',' } > 1 || raw.any { !it.isDigit() && it != '.' && it != ',' }) {
                                return@OutlinedTextField
                            }
                            priceText = raw
                            raw.replace(',', '.').toDoubleOrNull()
                                ?.takeIf { it > 0.0 && it.isFinite() }
                                ?.let(onPriceChange)
                        },
                        modifier = Modifier
                            .widthIn(min = 108.dp, max = 132.dp)
                            .onFocusChanged { state ->
                                priceFocused = state.isFocused
                                if (!state.isFocused) {
                                    val parsed = priceText.replace(',', '.').toDoubleOrNull()
                                    if (parsed == null || parsed <= 0.0 || !parsed.isFinite()) {
                                        priceText = editablePrice(item.price)
                                    }
                                }
                            },
                        label = { Text(stringResource(com.verto.feature.inventory.R.string.price_list_price)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                    IconButton(onClick = onDelete, modifier = Modifier.size(42.dp)) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            } else {
                Text(
                    editablePrice(item.price),
                    color = SuccessColor,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun editablePrice(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
