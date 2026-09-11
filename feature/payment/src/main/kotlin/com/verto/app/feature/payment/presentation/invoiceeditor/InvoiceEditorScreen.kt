package com.verto.app.feature.payment.presentation.invoiceeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.payment.application.model.ClientType
import com.verto.app.feature.payment.application.model.InvoiceDraftIdentity
import com.verto.app.feature.payment.application.model.InvoiceEditorDraftData
import com.verto.app.feature.payment.application.model.PaymentPurchaseScope
import com.verto.app.feature.payment.presentation.PaymentDimensions
import com.verto.app.ui.components.VertoLoadingState
import com.verto.app.ui.theme.BgDeep
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceEditorScreen(
    clientId: String,
    invoiceId: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit = { onBack() },
    isInternational: Boolean = false,
    foreignCurrencyName: String = "",
    exchangeRate: Double = 1.0,
    initialIsSale: Boolean = !isInternational,
    vm: InvoiceEditorViewModel = hiltViewModel(),
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        LaunchedEffect(invoiceId) { vm.loadInvoiceForEdit(invoiceId) }
        val editData by vm.editData.collectAsStateWithLifecycle()
        val editDueInstallments by vm.editDueInstallments.collectAsStateWithLifecycle()
        val editTotalPaid by vm.editTotalPaid.collectAsStateWithLifecycle()
        val inventoryItems by vm.inventoryItems.collectAsStateWithLifecycle()
        val allClients by vm.allClients.collectAsStateWithLifecycle()
        val savedInvoiceId by vm.savedInvoiceId.collectAsStateWithLifecycle()
        val saveResult by vm.saveResult.collectAsStateWithLifecycle()
        val vmSaveError by vm.saveError.collectAsStateWithLifecycle()
        val isSaving by vm.isSaving.collectAsStateWithLifecycle()
        val editPermissionDenied by vm.editPermissionDenied.collectAsStateWithLifecycle()
        val permissions by vm.permissions.collectAsStateWithLifecycle()
        val activeOrganizationId by vm.activeOrganizationId.collectAsStateWithLifecycle()
        val restoredDraft by vm.restoredDraft.collectAsStateWithLifecycle()
        val draftLoadComplete by vm.draftLoadComplete.collectAsStateWithLifecycle()
        val activeDraftKey by vm.activeDraftKey.collectAsStateWithLifecycle()
        val form = remember(clientId, isInternational, initialIsSale) {
            InvoiceEditorFormState(clientId, isInternational, initialIsSale)
        }
        val isEditMode = !invoiceId.isNullOrEmpty()
        val persistedInternational = isEditMode && editData?.first?.purchaseScope == PaymentPurchaseScope.INTERNATIONAL
        val editorIsInternational = isInternational || persistedInternational
        val editorCurrencyName = if (persistedInternational) {
            editData?.first?.transactionCurrencyCode.orEmpty().ifBlank { foreignCurrencyName }
        } else foreignCurrencyName
        val editorExchangeRate = if (persistedInternational) {
            editData?.first?.invoiceExchangeRateSnapshot?.toDoubleOrNull() ?: exchangeRate
        } else exchangeRate
        var initialFormApplied by remember(invoiceId, clientId, isInternational, initialIsSale) { mutableStateOf(false) }
        var initialContentSnapshot by remember(invoiceId, clientId, isInternational, initialIsSale) {
            mutableStateOf<InvoiceEditorContentSnapshot?>(null)
        }
        var showExitWarning by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(invoiceId, clientId, isInternational, initialIsSale) {
            vm.initializeInvoiceDraft(invoiceId, clientId, isInternational, initialIsSale)
        }
        val canCreateSales = permissions?.salesCreate == true
        val canCreatePurchases = permissions?.purchasesCreate == true
        LaunchedEffect(savedInvoiceId) { savedInvoiceId?.let(onSaved) }
        LaunchedEffect(draftLoadComplete, restoredDraft, editData, isEditMode) {
            if (!draftLoadComplete || initialFormApplied) return@LaunchedEffect
            when {
                restoredDraft != null -> {
                    form.restoreDraft(checkNotNull(restoredDraft))
                    initialContentSnapshot = form.contentSnapshot()
                    initialFormApplied = true
                }
                !isEditMode -> {
                    initialContentSnapshot = form.contentSnapshot()
                    initialFormApplied = true
                }
                editData != null -> {
                    form.load(checkNotNull(editData).first, checkNotNull(editData).second, editDueInstallments, editTotalPaid)
                    initialContentSnapshot = form.contentSnapshot()
                    initialFormApplied = true
                }
            }
        }
        LaunchedEffect(vmSaveError) {
            vmSaveError?.let {
                form.saveError = it
                vm.clearSaveError()
            }
        }
        LaunchedEffect(permissions, isEditMode, isInternational) {
            if (!isEditMode && !isInternational && permissions != null) {
                val before = form.contentSnapshot()
                resolveAllowedInvoiceType(
                    currentIsSale = form.isSale,
                    canCreateSales = canCreateSales,
                    canCreatePurchases = canCreatePurchases,
                )?.let { allowed ->
                    if (form.isSale != allowed) {
                        val baselineWasUntouched = initialContentSnapshot == before
                        form.isSale = allowed
                        if (baselineWasUntouched) initialContentSnapshot = form.contentSnapshot()
                    }
                }
            }
        }
        LaunchedEffect(initialFormApplied, activeDraftKey, activeOrganizationId) {
            if (!initialFormApplied || activeDraftKey.isBlank() || activeOrganizationId.isBlank()) return@LaunchedEffect
            snapshotFlow {
                form.toDraftData(
                    InvoiceDraftSnapshotTarget(
                        identity = InvoiceDraftIdentity(activeDraftKey, activeOrganizationId),
                        existingInvoiceId = invoiceId,
                        routeClientId = clientId,
                        isInternational = editorIsInternational,
                        transactionCurrencyCode = if (editorIsInternational) editorCurrencyName else "",
                        exchangeRate = editorExchangeRate,
                    )
                )
            }
                .distinctUntilChanged()
                .debounce(250)
                .collect { draft ->
                    val baseline = initialContentSnapshot
                    if (baseline != null && form.contentSnapshot() != baseline) vm.onDraftChanged(draft)
                }
        }
        if (!draftLoadComplete || (isEditMode && restoredDraft == null && editData == null && editPermissionDenied == null)) {
            Box(
                modifier = Modifier.fillMaxSize().background(BgDeep),
                contentAlignment = Alignment.Center,
            ) {
                VertoLoadingState(androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_v298_57434c957bf8), modifier = Modifier.padding(PaymentDimensions.dp32))
            }
            return@CompositionLocalProvider
        }

        with(form) {
            val resolvedDate = selectedDateMillis ?: System.currentTimeMillis()
            val selectedCommissionClient = allClients.firstOrNull { it.id == selectedClientId }
            val buyerEarnsCommission = selectedCommissionClient?.customerSegment in setOf(ClientType.MARKETER.name, ClientType.WORKSHOP_OWNER.name)
            val selectedReferrer = allClients.firstOrNull { client ->
                client.id == referrerClientId && client.id != selectedClientId &&
                    client.customerSegment in setOf(ClientType.MARKETER.name, ClientType.WORKSHOP_OWNER.name)
            }
            val commissionBeneficiaryClientId = when {
                buyerEarnsCommission -> selectedClientId
                selectedReferrer != null -> selectedReferrer.id
                else -> null
            }
            val commissionSource = when {
                buyerEarnsCommission -> "BUYER"
                selectedReferrer != null -> "REFERRER"
                else -> "NONE"
            }
            val saveInvoice: (Boolean, String) -> Unit = { sale, saveNotes ->
                vm.saveOrUpdateInvoice(
                    existingInvoiceId = if (isEditMode) invoiceId else null,
                    clientId = selectedClientId,
                    items = invoiceItems.filter { it.name.isNotBlank() },
                    paymentMode = paymentMode,
                    dueDate = pendingDue,
                    dueInstallments = dueInstallments,
                    notes = saveNotes,
                    isSale = sale,
                    originalCreatedAt = selectedDateMillis,
                    originalInvoiceNumber = editData?.first?.invoiceNumber,
                    initialPayment = paidAmount.toAmountDouble(),
                    shipmentId = if (isEditMode) editData?.first?.shipmentId else null,
                    purchaseScope = if (!sale && editorIsInternational) PaymentPurchaseScope.INTERNATIONAL else PaymentPurchaseScope.LOCAL,
                    discount = if (sale) invoiceDiscount.toAmountDouble() else 0.0,
                    exchangeRate = editorExchangeRate,
                    transactionCurrencyCode = if (!sale && editorIsInternational) editorCurrencyName else "",
                    commission = editData?.first?.commission ?: 0.0,
                    commissionBeneficiaryClientId = if (sale) commissionBeneficiaryClientId else null,
                    commissionSource = if (sale) commissionSource else "NONE",
                    companyClient = maintenance.isEnabled,
                    maintenance = maintenance.toDomainOrNull(),
                )
            }

            InvoiceEditorScreenEffects(
                form = form,
                resolvedDate = resolvedDate,
                editPermissionDenied = editPermissionDenied,
                onEditPermissionDismiss = { vm.clearEditPermissionDenied(); onBack() },
            )

            val hasUnsavedChanges = initialFormApplied && (
                restoredDraft != null || initialContentSnapshot?.let { contentSnapshot() != it } == true
            )
            fun currentDraft(): InvoiceEditorDraftData = toDraftData(
                InvoiceDraftSnapshotTarget(
                    identity = InvoiceDraftIdentity(activeDraftKey, activeOrganizationId),
                    existingInvoiceId = invoiceId,
                    routeClientId = clientId,
                    isInternational = editorIsInternational,
                    transactionCurrencyCode = if (editorIsInternational) editorCurrencyName else "",
                    exchangeRate = editorExchangeRate,
                )
            )

            InvoiceDraftExitGuard(
                enabled = hasUnsavedChanges && !isSaving,
                visible = showExitWarning,
                onDismiss = { showExitWarning = !showExitWarning },
                onSaveAndExit = {
                    showExitWarning = false
                    if (activeDraftKey.isBlank() || activeOrganizationId.isBlank()) onBack()
                    else vm.persistDraftAndThen(currentDraft(), onBack)
                },
                onDiscard = {
                    showExitWarning = false
                    vm.discardActiveDraft(onBack)
                },
            )

            saveResult?.let {
                PurchaseSaveResultDialog(it) { vm.clearSaveResult() }
            }

            if (isSale) {
                NewSaleInvoiceEditor349(
                    form = form,
                    vm = vm,
                    inventoryItems = inventoryItems,
                    allClients = allClients,
                    isEditMode = isEditMode,
                    canManageCommission = permissions?.commissionManage == true,
                    isSaving = isSaving,
                    onBack = {
                        if (hasUnsavedChanges) showExitWarning = true else onBack()
                    },
                    onSave = { mode, paid ->
                        paymentMode = mode
                        paidAmount = paid.toPlainString()
                        saveInvoice(true, notes)
                    },
                )
            } else {
                NewPurchaseInvoiceEditor369(
                    form = form,
                    vm = vm,
                    inventoryItems = inventoryItems,
                    allClients = allClients,
                    isEditMode = isEditMode,
                    isInternational = editorIsInternational,
                    currencyLabel = editorCurrencyName,
                    isSaving = isSaving,
                    onBack = {
                        if (hasUnsavedChanges) showExitWarning = true else onBack()
                    },
                    onSave = { mode, paid ->
                        paymentMode = mode
                        paidAmount = paid.toPlainString()
                        saveInvoice(false, notes)
                    },
                )
            }
        }
    }
}
