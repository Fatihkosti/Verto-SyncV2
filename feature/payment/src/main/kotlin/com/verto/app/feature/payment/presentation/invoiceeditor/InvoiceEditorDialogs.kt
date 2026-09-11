package com.verto.app.feature.payment.presentation.invoiceeditor

import com.verto.app.money.Money
import com.verto.app.money.Quantity
import com.verto.app.feature.payment.application.model.InvoiceItemData
import com.verto.app.feature.payment.application.model.InvoiceSaveResult
import com.verto.app.feature.payment.presentation.PaymentDimensions
import com.verto.app.feature.payment.presentation.PaymentTextScale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import com.verto.app.ui.theme.*
import com.verto.app.utils.WhatsAppUtils

@Composable
internal fun PurchaseSaveResultDialog(result: InvoiceSaveResult, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        containerColor   = BgCard,
        title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_52c23ba1af88), color = TextPrimary, fontWeight = FontWeight.Bold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(PaymentDimensions.dp6)) {
                if (result.newInventoryItemsCreated > 0)
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_69fb8c578ce0, result.newInventoryItemsCreated), color = AccentPrimary, fontSize = PaymentTextScale.sp13)
                if (result.inventoryItemsUpdated > 0)
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_24186e0873cf, result.inventoryItemsUpdated), color = InfoColor, fontSize = PaymentTextScale.sp13)
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss() }) {
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_90725495747d), color = AccentPrimary, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
internal fun InvoicePreviewDialog(
    invoiceItems: List<InvoiceItemData>,
    isSale: Boolean,
    onDismiss: () -> Unit,
) {
    val previewItems = invoiceItems.filter { it.name.isNotBlank() }
    val previewTotalMoney = previewItems.fold(Money.zero()) { total, item ->
        val quantity = runCatching { Quantity.parse(item.quantity) }.getOrNull()
        val unitPrice = Money.parseOrNull(if (isSale) item.sellPrice else item.buyPrice)
        if (quantity == null || unitPrice == null) total else total + (unitPrice * quantity)
    }
    val previewTotal = previewTotalMoney.toLegacyDouble()

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(.88f)
                .clip(RoundedCornerShape(PaymentDimensions.dp24))
                .background(BgCard)
                .border(PaymentDimensions.dp1, BorderColor, RoundedCornerShape(PaymentDimensions.dp24))
                .padding(PaymentDimensions.dp20),
            verticalArrangement = Arrangement.spacedBy(PaymentDimensions.dp14),
        ) {
            Text(
                if (isSale) androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_v298_78abc319de33_2) else androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_v298_78abc319de33),
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = PaymentTextScale.sp20,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
            HorizontalDivider(color = BorderColor)

            Column(
                modifier = Modifier.heightIn(max = PaymentDimensions.dp360).verticalScroll(rememberScrollState()),
            ) {
                previewItems.forEachIndexed { index, item ->
                    val quantity = runCatching { Quantity.parse(item.quantity) }.getOrNull()
                    val unitPriceText = if (isSale) item.sellPrice else item.buyPrice
                    val unitPrice = Money.parseOrNull(unitPriceText)
                    val lineTotal = if (quantity != null && unitPrice != null) (unitPrice * quantity).toLegacyDouble() else 0.0
                    val quantityText = quantity?.units?.toString() ?: "—"
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = PaymentDimensions.dp10),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            item.name,
                            color = TextPrimary,
                            fontSize = PaymentTextScale.sp14,
                            modifier = Modifier.weight(1.25f),
                            maxLines = 2,
                        )
                        Text(
                            androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_d603b6287052, quantityText, unitPriceText),
                            color = TextMuted,
                            fontSize = PaymentTextScale.sp12,
                            modifier = Modifier.weight(.9f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Text(
                            WhatsAppUtils.formatAmount(lineTotal),
                            color = AccentPrimary,
                            fontSize = PaymentTextScale.sp14,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(.75f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        )
                    }
                    if (index < previewItems.lastIndex) {
                        HorizontalDivider(color = BorderColor.copy(alpha = .45f))
                    }
                }
            }

            HorizontalDivider(color = BorderColor)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_total), color = TextMuted, fontSize = PaymentTextScale.sp15, fontWeight = FontWeight.Bold)
                Text(
                    WhatsAppUtils.formatAmount(previewTotal),
                    color = GoldPrimary,
                    fontSize = PaymentTextScale.sp22,
                    fontWeight = FontWeight.Black,
                )
            }
            TextButton(onClick = onDismiss) {
                Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_close), color = AccentPrimary, fontSize = PaymentTextScale.sp16, fontWeight = FontWeight.Bold)
            }
        }
    }
}

