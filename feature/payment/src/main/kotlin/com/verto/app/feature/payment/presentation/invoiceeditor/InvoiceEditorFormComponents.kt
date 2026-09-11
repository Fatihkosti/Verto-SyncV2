package com.verto.app.feature.payment.presentation.invoiceeditor

import com.verto.app.money.Money

import com.verto.app.feature.payment.presentation.PaymentDimensions
import com.verto.app.feature.payment.presentation.PaymentTextScale

import com.verto.app.feature.payment.application.model.*

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import com.verto.app.ui.theme.*
import com.verto.app.utils.WhatsAppUtils

@Composable
internal fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onNext: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null
) {
    var tfv by remember { mutableStateOf(TextFieldValue(value)) }
    LaunchedEffect(value) {
        if (tfv.text != value) tfv = TextFieldValue(value, TextRange(value.length))
    }
    BasicTextField(
        value         = tfv,
        onValueChange = { new -> tfv = new; if (new.text != value) onValueChange(new.text) },
        textStyle     = androidx.compose.material3.LocalTextStyle.current.copy(
            color = TextPrimary, fontSize = PaymentTextScale.sp13, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onNext = { onNext?.invoke() },
            onDone = { onDone?.invoke() }
        ),
        modifier = modifier.height(PaymentDimensions.dp42)
            .clip(RoundedCornerShape(PaymentDimensions.dp8)).background(BgCard)
            .border(PaymentDimensions.dp1, BorderColor, RoundedCornerShape(PaymentDimensions.dp8))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { if (it.isFocused) tfv = tfv.copy(selection = TextRange(0, tfv.text.length)) }
            .padding(horizontal = PaymentDimensions.dp8),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.Center) {
                if (tfv.text.isEmpty())
                    Text(placeholder, color = TextMuted, fontSize = PaymentTextScale.sp11, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
                innerTextField()
            }
        }
    )
}

@Composable
internal fun DirectClientSearch(
    selectedClientId: String,
    isSale: Boolean,
    isCashMode: Boolean,
    allClients: List<ClientItem>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSelectClient: (String) -> Unit,
    onQuickAdd: (String) -> Unit,
    globalSupplierOnly: Boolean = false,
    showFieldLabel: Boolean = true,
    modifier: Modifier = Modifier
) {
    val visibleClients = allClients.filter {
        it.id != CASH_CLIENT_ID && it.id != CASH_SUPPLIER_ID &&
            (!globalSupplierOnly || it.supplierScope == "INTERNATIONAL")
    }

    val selectedClient = visibleClients.find { it.id == selectedClientId }
    val query   = searchQuery.trim()
    val label   = if (isSale) "العميل" else if (globalSupplierOnly) "المورد العالمي" else "المورد"
    val filtered = if (query.isNotBlank()) visibleClients.filter { c ->
        val isSupplier   = c.hasSupplierRole
        val isCompetitor = c.customerSegment == "COMPETITOR"
        val matchesType  = if (globalSupplierOnly)
            c.supplierScope == "INTERNATIONAL"
        else if (isSale) !isSupplier else (isSupplier || isCompetitor)
        matchesType && (c.name.contains(query, ignoreCase = true) || c.phone.contains(query))
    }.take(7) else emptyList()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (showFieldLabel) PaymentDimensions.dp6 else PaymentDimensions.dp0)) {
        if (showFieldLabel) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, color = TextSecondary, fontSize = PaymentTextScale.sp13, fontWeight = FontWeight.SemiBold)
                if (isCashMode) Text(androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_9305c41b0adf), color = TextMuted, fontSize = PaymentTextScale.sp11)
            }
        }

        if (selectedClient != null && query.isBlank()) {
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(PaymentDimensions.dp10)).background(BgCard)
                    .border(PaymentDimensions.dp1, AccentPrimary.copy(0.4f), RoundedCornerShape(PaymentDimensions.dp10))
                    .clickable { onSelectClient(""); onSearchChange("") }
                    .padding(horizontal = PaymentDimensions.dp14, vertical = PaymentDimensions.dp12),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(selectedClient.name, color = TextPrimary,
                        fontWeight = FontWeight.SemiBold, fontSize = PaymentTextScale.sp14)
                    if (selectedClient.phone.isNotBlank())
                        Text(selectedClient.phone, color = TextMuted, fontSize = PaymentTextScale.sp11)
                }
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PaymentDimensions.dp4)) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_c0d1656706f7), color = AccentPrimary, fontSize = PaymentTextScale.sp11)
                    Icon(Icons.Filled.Edit, null, tint = AccentPrimary, modifier = Modifier.size(PaymentDimensions.dp12))
                }
            }
        }

        if (selectedClient == null || query.isNotBlank()) {
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    color = TextPrimary,
                    fontSize = PaymentTextScale.sp13,
                    textAlign = TextAlign.End,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PaymentDimensions.dp40)
                    .clip(RoundedCornerShape(PaymentDimensions.dp10))
                    .background(BgCard)
                    .border(PaymentDimensions.dp1, BorderColor, RoundedCornerShape(PaymentDimensions.dp10))
                    .padding(horizontal = PaymentDimensions.dp14),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PaymentDimensions.dp10),
                    ) {
                        Icon(Icons.Filled.Search, null, tint = TextMuted, modifier = Modifier.size(PaymentDimensions.dp24))
                        Box(Modifier.weight(1f)) {
                            if (searchQuery.isBlank()) {
                                Text(
                                    androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_v298_928c4d12f5a2_2, label, if (isCashMode) androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_v298_928c4d12f5a2) else ""),
                                    color = TextMuted,
                                    fontSize = PaymentTextScale.sp12,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            innerTextField()
                        }
                    }
                },
            )
            if (query.isNotBlank()) {
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(PaymentDimensions.dp10)).background(BgCard)
                        .border(PaymentDimensions.dp1, BorderColor, RoundedCornerShape(PaymentDimensions.dp10))
                ) {
                    if (filtered.isEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().clickable { onQuickAdd(query) }.padding(PaymentDimensions.dp14),
                            horizontalArrangement = Arrangement.spacedBy(PaymentDimensions.dp10),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.PersonAdd, null,
                                tint = AccentPrimary, modifier = Modifier.size(PaymentDimensions.dp18))
                            Text(androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_a0e947ff51b1, query), color = AccentPrimary, fontSize = PaymentTextScale.sp13)
                        }
                    } else {
                        filtered.forEach { client ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { onSelectClient(client.id) }
                                    .padding(horizontal = PaymentDimensions.dp14, vertical = PaymentDimensions.dp10),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(client.name, color = TextPrimary,
                                        fontSize = PaymentTextScale.sp13, fontWeight = FontWeight.Medium)
                                    if (client.phone.isNotBlank())
                                        Text(client.phone, color = TextMuted, fontSize = PaymentTextScale.sp11)
                                }
                                Text(client.customerSegment.customerSegmentLabel(),
                                    color = AccentLight, fontSize = PaymentTextScale.sp10)
                            }
                            HorizontalDivider(color = BorderColor, thickness = PaymentDimensions.dp0_5)
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable { onQuickAdd(query) }
                                .padding(horizontal = PaymentDimensions.dp14, vertical = PaymentDimensions.dp10),
                            horizontalArrangement = Arrangement.spacedBy(PaymentDimensions.dp8),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.PersonAdd, null,
                                tint = TextMuted, modifier = Modifier.size(PaymentDimensions.dp14))
                            Text(androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_a0e947ff51b1, query), color = TextMuted, fontSize = PaymentTextScale.sp12)
                        }
                    }
                }
            }
        }
    }
}

/** Compatibility projection for presentation-only APIs. New validation uses Money.parseOrNull directly. */
internal fun String.toAmountDouble(): Double = Money.parseOrNull(this)?.toLegacyDouble() ?: 0.0
