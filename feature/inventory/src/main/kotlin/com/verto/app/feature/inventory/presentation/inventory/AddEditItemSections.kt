package com.verto.app.feature.inventory.presentation.inventory

import com.verto.app.ui.components.VertoOutlinedTextField

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.inventory.application.model.InventoryItemView
import com.verto.app.ui.theme.*
import java.util.UUID
import com.verto.app.ui.components.VertoIconButton

@Composable
internal fun CompositionLocalProviderRtl(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        content = content,
    )
}

@Composable
internal fun AddItemHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(InventoryDimensions.dp74)
            .padding(horizontal = InventoryDimensions.dp18),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = InventoryTextScale.sp26,
            fontWeight = FontWeight.Black,
        )
        Surface(
            modifier = Modifier
                .size(InventoryDimensions.dp44)
                .offset(y = InventoryDimensions.dpNegative7)
                .shadow(InventoryDimensions.dp7, RoundedCornerShape(InventoryDimensions.dp16)),
            shape = RoundedCornerShape(InventoryDimensions.dp16),
            color = BgCard,
        ) {
            VertoIconButton(onClick = onBack, modifier = Modifier.fillMaxSize()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_navigate_back), tint = GradientStart, modifier = Modifier.size(InventoryDimensions.dp28))
            }
        }
    }
}

@Composable
internal fun BgCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .then(modifier)
            .fillMaxWidth()
            .shadow(InventoryDimensions.dp7, RoundedCornerShape(InventoryDimensions.dp22), clip = false)
            .clip(RoundedCornerShape(InventoryDimensions.dp22))
            .background(BgCard)
            .padding(horizontal = InventoryDimensions.dp18, vertical = InventoryDimensions.dp10),
        content = content,
    )
}

@Composable
internal fun TextPrimaryCard(
    label: String,
    icon: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BgCard(modifier = Modifier.height(InventoryDimensions.dp108)) {
        AddItemLabel(label = label, icon = icon)
        Spacer(Modifier.height(InventoryDimensions.dp7))
        content()
    }
}

@Composable
internal fun AddItemPriceCard(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier
            .height(InventoryDimensions.dp108)
            .shadow(InventoryDimensions.dp7, RoundedCornerShape(InventoryDimensions.dp22), clip = false)
            .clip(RoundedCornerShape(InventoryDimensions.dp22))
            .background(if (enabled) BgCard else BgCard.copy(alpha = 0.65f))
            .padding(horizontal = InventoryDimensions.dp18, vertical = InventoryDimensions.dp10),
    ) {
        AddItemLabel(label = label, icon = icon, enabled = enabled)
        Spacer(Modifier.height(InventoryDimensions.dp7))
        AddItemInput(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            keyboardType = KeyboardType.Decimal,
            enabled = enabled,
            trailingText = "جنيه",
        )
    }
}

@Composable
internal fun AddItemLabel(
    label: String,
    icon: @Composable () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(InventoryDimensions.dp10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(modifier = Modifier.size(InventoryDimensions.dp26), contentAlignment = Alignment.Center) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides if (enabled) GradientStart else TextMuted,
                ) {
                    icon()
                }
            }
        }
        Text(
            text = label,
            color = if (enabled) TextMuted else TextMuted.copy(alpha = 0.55f),
            fontSize = InventoryTextScale.sp17,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun AddItemInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
    isError: Boolean = false,
) {
    VertoOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = singleLine,
        placeholder = { Text(placeholder, color = TextMuted.copy(alpha = 0.68f), fontSize = InventoryTextScale.sp18) },
        trailingIcon = trailingText?.let {
            { Text(it, color = TextMuted.copy(alpha = 0.75f), fontSize = InventoryTextScale.sp16, fontWeight = FontWeight.Bold) }
        },
        isError = isError,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = GradientStart,
            unfocusedBorderColor = BorderColor,
            disabledBorderColor = BorderColor.copy(alpha = 0.6f),
            focusedContainerColor = BgCard,
            unfocusedContainerColor = BgCard,
            disabledContainerColor = BgCard,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            disabledTextColor = TextMuted,
            cursorColor = GradientStart,
            errorBorderColor = MaterialTheme.colorScheme.error,
        ),
        shape = RoundedCornerShape(InventoryDimensions.dp14),
        modifier = modifier
            .fillMaxWidth()
            .height(if (singleLine) InventoryDimensions.dp50 else InventoryDimensions.dp86),
    )
}

@Composable
internal fun AddItemInputCard(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(label, color = TextMuted, fontSize = InventoryTextScale.sp12, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(InventoryDimensions.dp5))
        AddItemInput(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            keyboardType = keyboardType,
            singleLine = singleLine,
        )
    }
}

@Composable
internal fun AddItemOptionsRow(expanded: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(InventoryDimensions.dp60)
            .shadow(InventoryDimensions.dp7, RoundedCornerShape(InventoryDimensions.dp22), clip = false)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(InventoryDimensions.dp22),
        color = BgCard,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier.padding(horizontal = InventoryDimensions.dp20),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(InventoryDimensions.dp25),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(InventoryDimensions.dp12), verticalAlignment = Alignment.CenterVertically) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.inventory.R.string.inventory_ds_f2d19c084052), color = TextMuted, fontSize = InventoryTextScale.sp17, fontWeight = FontWeight.Bold)
                    Icon(Icons.Outlined.Tune, contentDescription = null, tint = GradientStart, modifier = Modifier.size(InventoryDimensions.dp26))
                }
            }
        }
    }
}

@Composable
internal fun AddItemSaveButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(InventoryDimensions.dp19)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(InventoryDimensions.dp52)
            .clip(shape)
            .background(
                if (enabled) Brush.horizontalGradient(listOf(GradientEnd, GradientStart))
                else Brush.horizontalGradient(listOf(BorderColor, BorderColor)),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(InventoryDimensions.dp10), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Save, contentDescription = null, tint = if (enabled) TextOnAccent else TextMuted, modifier = Modifier.size(InventoryDimensions.dp25))
            Text(text, color = if (enabled) TextOnAccent else TextMuted, fontSize = InventoryTextScale.sp20, fontWeight = FontWeight.Bold)
        }
    }
}

internal fun resetAddItemFields(
    onName: (String) -> Unit,
    onBuyPrice: (String) -> Unit,
    onSellPrice: (String) -> Unit,
    onQuantity: (String) -> Unit,
    onPartNum: (String) -> Unit,
    onBarcode: (String) -> Unit,
    onMinQty: (String) -> Unit,
    onLocation: (String) -> Unit,
    onNote: (String) -> Unit,
    onCategory: (String) -> Unit,
    onHasUnit: (Boolean) -> Unit,
    onUnitName: (String) -> Unit,
    onUnitQty: (String) -> Unit,
    onLinkedId: (String?) -> Unit,
    onExtras: (Boolean) -> Unit,
) {
    onName("")
    onBuyPrice("")
    onSellPrice("")
    onQuantity("0")
    onPartNum("")
    onBarcode("")
    onMinQty("")
    onLocation("")
    onNote("")
    onCategory("")
    onHasUnit(false)
    onUnitName("")
    onUnitQty("")
    onLinkedId(null)
    onExtras(false)
}
