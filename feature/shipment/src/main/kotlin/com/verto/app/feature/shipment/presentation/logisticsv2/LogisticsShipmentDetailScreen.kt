package com.verto.app.feature.shipment.presentation.logisticsv2

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.verto.app.feature.shipment.domain.model.LogisticsCostStatus
import com.verto.app.feature.shipment.domain.model.LogisticsCostType
import com.verto.app.feature.shipment.domain.model.LogisticsDocument
import com.verto.app.feature.shipment.domain.model.LogisticsDocumentType
import com.verto.app.feature.shipment.domain.model.LogisticsLegStatus
import com.verto.app.feature.shipment.domain.model.LogisticsLegTransportMode
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsOperationalAction
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.ui.theme.VertoSpacing
import java.util.concurrent.TimeUnit

@Composable
fun LogisticsShipmentDetailScreen(
    state: LogisticsDetailUiState,
    access: LogisticsV2Access,
    operationState: LogisticsOperationUiState = LogisticsOperationUiState.Idle,
    onBack: () -> Unit,
    onAction: (LogisticsNextAction) -> Unit,
    onOpenPartners: () -> Unit,
    onSaveDocument: (sourceUri: String, displayName: String, mimeType: String) -> Unit,
    onDeleteDocument: (documentId: String) -> Unit,
) {
    val subtitle = (state as? LogisticsDetailUiState.Content)?.detail?.aggregate?.shipment?.shipmentNumber
    val context = LocalContext.current
    val viewModel: LogisticsV2ViewModel = hiltViewModel()
    var customsDocumentMilestoneId by remember { mutableStateOf<String?>(null) }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            onSaveDocument(
                it.toString(),
                context.displayName(it),
                context.contentResolver.getType(it) ?: "application/octet-stream",
            )
        }
    }
    val customsDocumentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val milestoneId = customsDocumentMilestoneId
        val content = state as? LogisticsDetailUiState.Content
        if (uri != null && milestoneId != null && content != null) {
            viewModel.saveDocument(
                shipmentId = content.detail.aggregate.shipment.id,
                sourceUri = uri.toString(),
                displayName = context.displayName(uri),
                mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream",
                type = LogisticsDocumentType.CUSTOMS_DOCUMENT,
                milestoneId = milestoneId,
            )
        }
        customsDocumentMilestoneId = null
    }
    LogisticsV2Screen(
        title = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_b876c476ab60),
        subtitle = subtitle,
        onBack = onBack,
    ) {
        when (state) {
            LogisticsDetailUiState.Idle,
            LogisticsDetailUiState.Loading -> LogisticsLoading()
            LogisticsDetailUiState.PermissionDenied -> LogisticsPermissionDenied()
            is LogisticsDetailUiState.Error -> LogisticsError(state.message)
            is LogisticsDetailUiState.Content -> ShipmentDetailContent(
                detail = state.detail,
                access = access,
                operationState = operationState,
                onAction = onAction,
                onOpenPartners = onOpenPartners,
                onAddDocument = { documentPicker.launch(arrayOf("*/*")) },
                onAddCustomsDocument = { milestoneId ->
                    customsDocumentMilestoneId = milestoneId
                    customsDocumentPicker.launch(arrayOf("*/*"))
                },
                onDeleteDocument = onDeleteDocument,
                onOpenDocument = { viewModel.openDocument(state.detail.aggregate.shipment.id, it) },
                onSourceHandoff = { viewModel.handoffSourceToFirstCarrier(state.detail.aggregate.shipment.id, it) },
                onPrepareMovement = { viewModel.prepareMovement(state.detail.aggregate.shipment.id, it) }, onStartPreparedMovement = { viewModel.startPreparedMovement(state.detail.aggregate.shipment.id) },
                onOperationalArrival = { viewModel.recordOperationalArrival(state.detail.aggregate.shipment.id) }, onConfirmUnload = { viewModel.confirmUnload(state.detail.aggregate.shipment.id) },
                onStartCustoms = { viewModel.requestCustomsPickup(state.detail.aggregate.shipment.id) }, onCompleteCustoms = { viewModel.completeCustoms(state.detail.aggregate.shipment.id) },
                onOperationalHandoff = { viewModel.recordOperationalHandoff(state.detail.aggregate.shipment.id) },
                onConfirmLoad = { viewModel.confirmLoad(state.detail.aggregate.shipment.id) },
                onOperationalDeparture = { viewModel.recordOperationalDeparture(state.detail.aggregate.shipment.id) },
                onUpdateEta = { legId, eta -> viewModel.updateEta(state.detail.aggregate.shipment.id, legId, eta) },
                onUpdateFutureLeg = { leg, reason -> viewModel.updateFutureLeg(state.detail.aggregate.shipment.id, leg, reason) },
                onInsertUnplannedStation = { draft -> viewModel.insertUnplannedStation(state.detail.aggregate.shipment.id, draft) },
                onCorrectActualTime = { draft -> viewModel.correctActualTime(state.detail.aggregate.shipment.id, draft) },
                onFollowUp = { note -> viewModel.recordFollowUp(state.detail.aggregate.shipment.id, note) },
                onCancel = { reason -> viewModel.cancelShipment(state.detail.aggregate.shipment.id, reason) },
                onAddActualCost = { draft -> viewModel.addCost(state.detail.aggregate.shipment.id, draft) },
                onPermanentDelete = { confirmation, reason -> viewModel.permanentlyDeleteShipment(state.detail.aggregate.shipment.id, confirmation, reason, onBack) },
            )
        }
    }
}
