package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.ui.components.VertoButton

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.verto.app.feature.shipment.domain.model.LogisticsCostStatus
import com.verto.app.feature.shipment.domain.model.LogisticsCostType
import com.verto.app.ui.theme.VertoSpacing

@Composable
fun ShipmentCostSettlementScreen(
    shipmentNumber: String,
    state: LogisticsCostSettlementUi,
    access: LogisticsV2Access,
    operationState: LogisticsOperationUiState = LogisticsOperationUiState.Idle,
    onBack: () -> Unit,
    onAddCost: (LogisticsCostDraft) -> Unit,
    onSettle: () -> Unit,
    onCloseWithoutAdditionalCosts: () -> Unit,
) {
    var type by remember { mutableStateOf(LogisticsCostType.FREIGHT) }
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("SDG") }
    var exchangeRate by remember { mutableStateOf("1") }
    var status by remember { mutableStateOf(LogisticsCostStatus.ACTUAL) }
    var reference by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf("shipment") }
    val draft = LogisticsCostDraft(
        type = type,
        amount = amount,
        currency = currency,
        exchangeRate = exchangeRate,
        status = status,
        sourceId = scope.takeIf { it.startsWith("source:") }?.substringAfter(':'),
        milestoneId = scope.takeIf { it.startsWith("milestone:") }?.substringAfter(':'),
        legId = scope.takeIf { it.startsWith("leg:") }?.substringAfter(':'),
        reference = reference,
        note = note,
    )

    LogisticsV2Screen(
        title = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_79a20305a242),
        subtitle = shipmentNumber,
        onBack = onBack,
    ) {
        LogisticsSection("التكاليف", trailing = state.costs.size.toString()) {
            if (state.costs.isEmpty()) LogisticsEmpty("لا توجد تكاليف مسجلة.")
            state.costs.groupBy { cost ->
                when {
                    cost.sourceId != null -> "source:${cost.sourceId}"
                    cost.milestoneId != null -> "milestone:${cost.milestoneId}"
                    cost.legId != null -> "leg:${cost.legId}"
                    else -> "shipment"
                }
            }.forEach { (scopeKey, scoped) ->
                val estimated = scoped.filter { it.status == LogisticsCostStatus.ESTIMATED }
                    .fold(java.math.BigDecimal.ZERO) { acc, cost -> acc.add(cost.baseCurrencyAmount) }
                val actual = scoped.filter { it.status == LogisticsCostStatus.ACTUAL }
                    .fold(java.math.BigDecimal.ZERO) { acc, cost -> acc.add(cost.baseCurrencyAmount) }
                LogisticsLabeledValue(
                    settlementScopeLabel(state, scopeKey),
                    "متوقع: $estimated • فعلي: $actual • الفرق: ${actual.subtract(estimated)}",
                )
            }
            LogisticsLabeledValue("إجمالي الفعلي بالعملة الأساسية", state.actualTotal.toPlainString())
        }

        if (state.allocations.isNotEmpty()) {
            LogisticsSection("التوزيع", trailing = state.allocations.size.toString()) {
                state.allocations.forEach { allocation ->
                    LogisticsLabeledValue(allocation.shipmentLineId, allocation.amount.toPlainString())
                }
            }
        }

        if (!access.canManage) {
            LogisticsPermissionDenied()
        } else {
            LogisticsSection("إضافة تكلفة") {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs),
                ) {
                    LogisticsCostType.entries.forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = { type = option },
                            label = { Text(option.arabicLabel()) },
                        )
                    }
                }
                Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_221cc76f1c8b))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs),
                ) {
                    FilterChip(selected = scope == "shipment", onClick = { scope = "shipment" }, label = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_068b74354fab)) })
                    state.sources.forEach { source ->
                        val key = "source:${source.id}"
                        FilterChip(selected = scope == key, onClick = { scope = key }, label = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_adebc50ade66, source.invoiceNumberSnapshot)) })
                    }
                    state.milestones.forEach { milestone ->
                        val key = "milestone:${milestone.id}"
                        FilterChip(selected = scope == key, onClick = { scope = key }, label = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_7c288ea25807, milestone.order + 1)) })
                    }
                    state.legs.forEach { leg ->
                        val key = "leg:${leg.id}"
                        FilterChip(selected = scope == key, onClick = { scope = key }, label = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_386141eaafe7, leg.sequence + 1)) })
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs),
                ) {
                    LogisticsCostStatus.entries.forEach { option ->
                        FilterChip(
                            selected = status == option,
                            onClick = { status = option },
                            label = { Text(if (option == LogisticsCostStatus.ACTUAL) androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_1e384352aa21_2) else androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_1e384352aa21)) },
                        )
                    }
                }
                LogisticsTextField(amount, { amount = it }, "المبلغ")
                LogisticsTextField(currency, { currency = it }, "العملة")
                LogisticsTextField(exchangeRate, { exchangeRate = it }, "سعر الصرف للعملة الأساسية")
                LogisticsTextField(reference, { reference = it }, "المرجع")
                LogisticsTextField(note, { note = it }, "ملاحظات", singleLine = false)
                VertoButton(
                    onClick = {
                        onAddCost(draft)
                        amount = ""
                        reference = ""
                        note = ""
                    },
                    enabled = draft.isValid && !operationState.isWorking,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_962e6507f23c)) }
            }

            LogisticsOperationFeedback(operationState)
            if (state.actualTotal.signum() > 0 && !state.settled) {
                VertoButton(
                    onClick = onSettle,
                    enabled = !operationState.isWorking,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_24c851c415d5)) }
            } else if (state.actualTotal.signum() > 0 && state.settled) {
                VertoButton(
                    onClick = onCloseWithoutAdditionalCosts,
                    enabled = !operationState.isWorking,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_a806c7bffac1)) }
            } else if (state.actualTotal.signum() == 0) {
                VertoButton(
                    onClick = onCloseWithoutAdditionalCosts,
                    enabled = !operationState.isWorking,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_250ed6ae1961)) }
            }
        }
    }
}

private fun settlementScopeLabel(state: LogisticsCostSettlementUi, key: String): String = when {
    key == "shipment" -> "الشحنة"
    key.startsWith("source:") -> state.sources.firstOrNull { it.id == key.substringAfter(':') }
        ?.let { "فاتورة #${it.invoiceNumberSnapshot}" } ?: "فاتورة"
    key.startsWith("milestone:") -> state.milestones.firstOrNull { it.id == key.substringAfter(':') }
        ?.let { "محطة ${it.location}" } ?: "محطة"
    key.startsWith("leg:") -> state.legs.firstOrNull { it.id == key.substringAfter(':') }
        ?.let { "مرحلة ${it.sequence + 1}" } ?: "مرحلة"
    else -> key
}

private fun LogisticsCostType.arabicLabel(): String = when (this) {
    LogisticsCostType.FREIGHT -> "شحن"
    LogisticsCostType.CUSTOMS_DUTY -> "جمارك"
    LogisticsCostType.CLEARANCE -> "تخليص"
    LogisticsCostType.STORAGE -> "تخزين"
    LogisticsCostType.INSURANCE -> "تأمين"
    LogisticsCostType.INSPECTION -> "فحص"
    LogisticsCostType.LOCAL_TRANSPORT -> "نقل محلي"
    LogisticsCostType.OTHER -> "أخرى"
}
