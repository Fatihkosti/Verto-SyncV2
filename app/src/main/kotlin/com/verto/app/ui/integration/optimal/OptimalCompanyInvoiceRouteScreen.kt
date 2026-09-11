package com.verto.app.ui.integration.optimal

import com.verto.app.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.verto.app.feature.integration.optimal.application.ObserveCompanyInvoiceAccessUseCase
import com.verto.app.feature.integration.optimal.navigation.OptimalNavigation
import com.verto.app.feature.invoice.presentation.invoice.InvoiceScreen
import com.verto.app.ui.theme.VertoSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class OptimalCompanyInvoiceRouteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeAccess: ObserveCompanyInvoiceAccessUseCase,
) : ViewModel() {
    val invoiceId: String =
        savedStateHandle.get<String>(OptimalNavigation.COMPANY_INVOICE_ID_ARG).orEmpty().trim()
    private val expectedOrganizationId: String =
        savedStateHandle.get<String>(OptimalNavigation.COMPANY_INVOICE_ORGANIZATION_ID_ARG).orEmpty().trim()

    val accessState = observeAccess(
        expectedOrganizationId = expectedOrganizationId,
        invoiceId = invoiceId,
    )
        .map { allowed ->
            if (allowed) OptimalCompanyInvoiceAccessState.Allowed
            else OptimalCompanyInvoiceAccessState.Denied
        }
        .catch { emit(OptimalCompanyInvoiceAccessState.Denied) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = OptimalCompanyInvoiceAccessState.Checking,
        )
}

enum class OptimalCompanyInvoiceAccessState {
    Checking,
    Allowed,
    Denied,
}

/** Keeps the original invoice UI behind a continuously observed tenant ownership guard. */
@Composable
fun OptimalCompanyInvoiceRouteScreen(
    onBack: () -> Unit,
    onAddPayment: (String, String) -> Unit,
    onPayFull: (String, String) -> Unit = onAddPayment,
    onEditInvoice: (String, String) -> Unit,
    onDeleteSuccess: () -> Unit,
    viewModel: OptimalCompanyInvoiceRouteViewModel = hiltViewModel(),
) {
    val accessState by viewModel.accessState.collectAsStateWithLifecycle()
    when (accessState) {
        OptimalCompanyInvoiceAccessState.Checking -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        OptimalCompanyInvoiceAccessState.Denied -> Column(
            modifier = Modifier.fillMaxSize().padding(VertoSpacing.xl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.ds_f24b9e6ef7b8),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onBack) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_navigate_back)) }
        }

        OptimalCompanyInvoiceAccessState.Allowed -> InvoiceScreen(
            invoiceId = viewModel.invoiceId,
            onBack = onBack,
            onAddPayment = onAddPayment,
            onPayFull = onPayFull,
            onEditInvoice = onEditInvoice,
            onDeleteSuccess = onDeleteSuccess,
        )
    }
}
