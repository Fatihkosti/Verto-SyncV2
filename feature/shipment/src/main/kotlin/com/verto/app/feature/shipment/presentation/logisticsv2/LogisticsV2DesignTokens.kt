package com.verto.app.feature.shipment.presentation.logisticsv2

import androidx.compose.ui.unit.dp
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing

/** Logistics-owned geometry. Shared app-wide dimensions reuse Foundation tokens. */
internal object LogisticsV2Tokens {
    val contentMaxWidth = 920.dp
    val metricMinWidth = 132.dp
    val timelineRailWidth = VertoSpacing.xl
    val timelineDot = 10.dp
    val progressHeight = VertoSpacing.xs
    val compactChipHeight = VertoSpacing.xxl
    val planningDialogMaxHeight = 520.dp
    val pickerListMaxHeight = 360.dp
    val documentPreviewSize = 72.dp
    val documentIconSize = VertoSize.iconContainer
    val compactValueGap = 2.dp
    val severeDelayCardElevation = 2.dp

    // Session 300: Logistics-owned geometry migrated 1:1 from production consumers.
    val stationProofUploadMinHeight = 88.dp
    val invoicePickerMaxHeight = 430.dp
    val planningProgressNodeSize = 36.dp
    val transportChoiceMinHeight = 92.dp
    val transportChoiceIconSize = 26.dp
    val routeNodeSize = 38.dp
    val routeConnectorStartInset = routeNodeSize / 2
    val routeConnectorHeight = VertoSpacing.xl
    val movementDialogMaxHeight = 560.dp
    val customsHeaderIconSize = 36.dp
    val documentUploadMinHeight = 72.dp
    val reviewCustomsMarkerSize = VertoSpacing.xs
    val reviewTerminalDotSize = 14.dp
    val reviewStationDotSize = VertoSpacing.sm
}
