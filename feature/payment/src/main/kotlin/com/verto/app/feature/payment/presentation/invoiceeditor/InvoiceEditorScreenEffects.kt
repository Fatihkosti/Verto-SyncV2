package com.verto.app.feature.payment.presentation.invoiceeditor

import com.verto.app.feature.payment.application.model.*
import com.verto.app.feature.payment.presentation.PaymentDimensions
import com.verto.app.feature.payment.presentation.PaymentTextScale

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.payment.application.model.ClientItem
import com.verto.app.feature.payment.application.model.InventoryItemView
import com.verto.app.ui.components.*
import com.verto.app.ui.theme.*
import com.verto.app.utils.WhatsAppUtils
import androidx.compose.runtime.CompositionLocalProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InvoiceEditorScreenEffects(
    form: InvoiceEditorFormState,
    resolvedDate: Long,
    editPermissionDenied: String?,
    onEditPermissionDismiss: () -> Unit,
) {
    with(form) {
            if (showPreview) {
                InvoicePreviewDialog(
                    invoiceItems = invoiceItems,
                    isSale = isSale,
                    onDismiss = { showPreview = false },
                )
            }

            if (showDatePicker) {
                val datePickerState = rememberDatePickerState(initialSelectedDateMillis = resolvedDate)
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { selectedDateMillis = it }
                            showDatePicker = false
                        }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_confirm), color = AccentPrimary, fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextSecondary) }
                    },
                ) { DatePicker(state = datePickerState) }
            }

            editPermissionDenied?.let { message ->
                AlertDialog(
                    onDismissRequest = { onEditPermissionDismiss() },
                    containerColor = BgCard,
                    shape = RoundedCornerShape(PaymentDimensions.dp22),
                    title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_e1ed7cb6a121), color = TextPrimary, fontWeight = FontWeight.Bold) },
                    text = { Text(message, color = TextSecondary) },
                    confirmButton = {
                        TextButton(onClick = { onEditPermissionDismiss() }) {
                            Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_navigate_back), color = AccentPrimary, fontWeight = FontWeight.Bold)
                        }
                    },
                )
            }

    }
}
