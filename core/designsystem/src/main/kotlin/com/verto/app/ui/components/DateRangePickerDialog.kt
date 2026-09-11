package com.verto.app.ui.components

import com.verto.app.ui.theme.VertoSpacing
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.verto.core.designsystem.R

/**
 * حوار اختيار فترة (من/إلى) مشترك. يُرجع البداية (00:00) والنهاية (نهاية اليوم) بالـ millis.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    initialFrom: Long?,
    initialTo: Long?,
    onConfirm: (Long, Long) -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(0) }
    val fromState = rememberDatePickerState(initialSelectedDateMillis = initialFrom)
    val toState   = rememberDatePickerState(initialSelectedDateMillis = initialTo)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                if (step == 0) { if (fromState.selectedDateMillis != null) step = 1 }
                else {
                    val f = fromState.selectedDateMillis ?: return@TextButton
                    val t = toState.selectedDateMillis   ?: return@TextButton
                    onConfirm(minOf(f, t), maxOf(f, t) + 86_399_999L)
                }
            }) { Text(if (step == 0) stringResource(R.string.verto_action_next) else stringResource(R.string.verto_action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = { if (step == 1) step = 0 else onDismiss() }) {
                Text(if (step == 1) stringResource(R.string.verto_navigate_back) else stringResource(R.string.verto_action_cancel))
            }
        }
    ) {
        if (step == 0) DatePicker(state = fromState,
            title = { Text(stringResource(R.string.verto_date_start), Modifier.padding(start = VertoSpacing.xl, top = VertoSpacing.md)) })
        else DatePicker(state = toState,
            title = { Text(stringResource(R.string.verto_date_end), Modifier.padding(start = VertoSpacing.xl, top = VertoSpacing.md)) })
    }
}
