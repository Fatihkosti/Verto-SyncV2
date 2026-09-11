package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.ui.components.VertoOutlinedButton
import com.verto.app.ui.components.VertoButton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.verto.app.feature.shipment.domain.model.LogisticsPartner
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.ui.theme.VertoSpacing

@Composable
internal fun V239MovementPreparationSection(
    leg: LogisticsShipmentLeg,
    carriers: List<LogisticsPartner>,
    busy: Boolean,
    onPrepare: (LogisticsMovementPreparationDraft) -> Unit,
) {
    var showDialog by remember(leg.id) { mutableStateOf(false) }
    LogisticsSection("الحركة التالية") {
        LogisticsLabeledValue("المرحلة", "${leg.sequence + 1}")
        LogisticsLabeledValue("نوع النقل", leg.mode.name)
        LogisticsLabeledValue(
            "المدة المخططة",
            leg.expectedTransitMinutes?.let { "$it دقيقة" } ?: leg.expectedTransitDays?.let { "$it يوم" } ?: "—",
        )
        VertoButton(
            onClick = { showDialog = true },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_62e4ae2a295b)) }
        Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_39b6242a7188), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
    if (showDialog) {
        LogisticsMovementPreparationDialog(
            leg = leg,
            carriers = carriers,
            busy = busy,
            onDismiss = { showDialog = false },
            onConfirm = { draft -> showDialog = false; onPrepare(draft) },
        )
    }
}

@Composable
internal fun V239PreparedDepartureSection(
    detail: LogisticsShipmentDetailUi,
    access: LogisticsV2Access,
    busy: Boolean,
    onSourceHandoff: (String) -> Unit,
    onStart: () -> Unit,
) {
    LogisticsSection("جاهزية الحركة", trailing = "${detail.sourceCustody.count { it.atFirstCarrier }}/${detail.sourceCustody.size}") {
        if (detail.sourceCustody.isEmpty()) LogisticsEmpty("لا توجد فواتير مصدرية.")
        detail.sourceCustody.forEach { source ->
            Column(verticalArrangement = Arrangement.spacedBy(VertoSpacing.xxs)) {
                LogisticsLabeledValue("#${source.invoiceNumber} — ${source.supplierName}", source.holderName)
                source.phone?.let { Text(it, color = TextSecondary, style = MaterialTheme.typography.bodySmall) }
                if (!source.atFirstCarrier && access.canManage) {
                    VertoOutlinedButton(
                        onClick = { onSourceHandoff(source.sourceId) },
                        enabled = !busy && detail.firstCarrierId != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_0270cbee0fa9, detail.firstCarrierName.orEmpty())) }
                }
            }
        }
        VertoButton(
            onClick = onStart,
            enabled = access.canManage && detail.startReady && !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (detail.startReady) androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_448b33771932_2) else androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_448b33771932)) }
    }
}
