package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.ui.components.VertoButton

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun CreateLogisticsShipmentScreen(
    access: LogisticsV2Access,
    onBack: () -> Unit,
    onResolved: (String) -> Unit,
    viewModel: LogisticsV2ViewModel = hiltViewModel(),
) {
    val draftState by viewModel.draftState.collectAsStateWithLifecycle()

    LaunchedEffect(access) {
        viewModel.updateAccess(access)
        if (access.canManage) viewModel.openShipmentDraft()
    }
    BackHandler(enabled = access.canManage) { viewModel.backFromDraft(onBack) }

    when (val state = draftState) {
        LogisticsDraftUiState.Idle,
        LogisticsDraftUiState.Loading -> LogisticsV2Screen(
            title = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_356405ee8120),
            subtitle = "جارٍ تجهيز مسودة التخطيط…",
            onBack = { viewModel.backFromDraft(onBack) },
        ) { LogisticsLoading() }

        LogisticsDraftUiState.PermissionDenied -> LogisticsV2Screen(
            title = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_356405ee8120),
            onBack = onBack,
        ) { LogisticsPermissionDenied() }

        is LogisticsDraftUiState.Error -> LogisticsV2Screen(
            title = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_356405ee8120),
            onBack = { viewModel.backFromDraft(onBack) },
        ) {
            LogisticsSection("تعذر تجهيز المسودة") {
                Text(state.message)
                VertoButton(onClick = viewModel::openShipmentDraft, modifier = Modifier.fillMaxWidth()) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_retry))
                }
            }
        }

        is LogisticsDraftUiState.ResumeAvailable -> {
            LogisticsV2Screen(
                title = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_356405ee8120),
                subtitle = "لديك تخطيط غير مكتمل",
                onBack = onBack,
            ) { LogisticsLoading() }
            AlertDialog(
                onDismissRequest = onBack,
                title = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_7cf320709614)) },
                text = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_ba37faa90e95, state.shipmentNumber)) },
                confirmButton = {
                    VertoButton(onClick = viewModel::resumeShipmentDraft) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_follow_up)) }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::startNewShipmentDraft) { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_785a2e3466dc)) }
                },
            )
        }

        is LogisticsDraftUiState.Content -> LaunchedEffect(state.draft.shipmentId) {
            onResolved(state.draft.shipmentId)
        }
    }
}
