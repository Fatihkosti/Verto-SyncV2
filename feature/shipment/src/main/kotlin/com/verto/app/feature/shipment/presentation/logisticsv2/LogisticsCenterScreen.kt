package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.ui.components.VertoButton

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.verto.app.ui.theme.VertoSpacing

@Composable
fun LogisticsCenterScreen(
    state: LogisticsCenterUiState,
    access: LogisticsV2Access,
    onNewShipment: () -> Unit,
    onOpenShipment: (LogisticsShipmentCardUi) -> Unit,
    onAction: (String, LogisticsNextAction) -> Unit,
    onRetry: () -> Unit = {},
) {
    LogisticsV2Screen(title = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_e6a6454fa04e), subtitle = "الشحنات الواردة من المصدر حتى الاستلام والإغلاق") {
        when (state) {
            LogisticsCenterUiState.Loading -> LogisticsLoading()
            LogisticsCenterUiState.PermissionDenied -> LogisticsPermissionDenied()
            is LogisticsCenterUiState.Error -> LogisticsError(state.message, onRetry)
            is LogisticsCenterUiState.Content -> {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(VertoSpacing.sm),
                ) {
                    LogisticsMetricCard("نشطة", state.metrics.active)
                    LogisticsMetricCard("متأخرة", state.metrics.delayed)
                    LogisticsMetricCard("عند الجمارك", state.metrics.atCustoms)
                    LogisticsMetricCard("تنتظر استلام", state.metrics.waitingReceiving)
                }

                if (access.canManage) {
                    VertoButton(onClick = onNewShipment, modifier = Modifier.fillMaxWidth()) {
                        Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_356405ee8120))
                    }
                }

                if (state.isEmpty) {
                    LogisticsEmpty(
                        message = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_5face4fd9479),
                        actionLabel = if (access.canManage) androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_6653953efb36) else null,
                        onAction = if (access.canManage) onNewShipment else null,
                    )
                } else {
                    LogisticsCenterGroup.entries.forEach { group ->
                        val items = state.groups[group].orEmpty()
                        if (items.isNotEmpty()) {
                            LogisticsSection(title = group.label, trailing = items.size.toString()) {
                                items.forEach { shipment ->
                                    LogisticsShipmentCard(
                                        shipment = shipment,
                                        access = access,
                                        onOpen = { onOpenShipment(shipment) },
                                        onAction = { action -> onAction(shipment.id, action) },
                                    )
                                }
                            }
                        }
                    }

                    LogisticsOperationalAnalyticsSection(state.analytics)
                }
            }
        }
    }
}
