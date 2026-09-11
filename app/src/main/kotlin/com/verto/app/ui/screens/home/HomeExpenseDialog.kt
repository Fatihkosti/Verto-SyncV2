package com.verto.app.ui.screens.home

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import com.verto.app.R
import com.verto.app.ui.components.VertoPrimaryButton
import com.verto.app.ui.components.VertoSecondaryButton
import com.verto.app.ui.components.VertoTextField
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.VertoSpacing
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
internal fun HomeExpenseDialog(
    isSaving: Boolean,
    onSave: (statement: String, amount: Double, date: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var statement by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var selectedDate by rememberSaveable { mutableStateOf(startOfToday()) }
    var statementError by rememberSaveable { mutableStateOf(false) }
    var amountError by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH) }

    fun submit() {
        val amount = amountText.toDoubleOrNull()
        statementError = statement.isBlank()
        amountError = amount == null || amount <= 0.0
        if (!statementError && !amountError) {
            onSave(statement.trim(), requireNotNull(amount), selectedDate)
        }
    }

    HomeDialogSurface(
        onDismissRequest = { if (!isSaving) onDismiss() },
    ) {
        Column(
            modifier = Modifier.padding(VertoSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(VertoSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.home_expense_title),
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                modifier = Modifier.semantics { heading() },
            )
            VertoTextField(
                value = statement,
                onValueChange = {
                    statement = it
                    statementError = false
                },
                label = stringResource(R.string.home_expense_statement),
                isRequired = true,
                errorText = stringResource(R.string.home_expense_statement_required)
                    .takeIf { statementError },
                enabled = !isSaving,
                imeAction = ImeAction.Next,
            )
            VertoTextField(
                value = amountText,
                onValueChange = {
                    amountText = it.filter { char -> char.isDigit() || char == '.' }
                    amountError = false
                },
                label = stringResource(R.string.home_expense_amount),
                isRequired = true,
                errorText = stringResource(R.string.home_expense_amount_invalid)
                    .takeIf { amountError },
                enabled = !isSaving,
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            VertoSecondaryButton(
                text = stringResource(
                    R.string.home_expense_date,
                    dateFormatter.format(Date(selectedDate)),
                ),
                enabled = !isSaving,
                onClick = {
                    val calendar = Calendar.getInstance().apply { timeInMillis = selectedDate }
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            selectedDate = Calendar.getInstance().apply {
                                set(Calendar.YEAR, year)
                                set(Calendar.MONTH, month)
                                set(Calendar.DAY_OF_MONTH, day)
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }.timeInMillis
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH),
                    ).show()
                },
            )
            Spacer(Modifier.height(VertoSpacing.xxs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VertoSpacing.sm),
            ) {
                VertoPrimaryButton(
                    text = stringResource(R.string.home_save),
                    onClick = ::submit,
                    enabled = !isSaving,
                    isLoading = isSaving,
                    modifier = Modifier.weight(1f),
                )
                VertoSecondaryButton(
                    text = stringResource(R.string.home_cancel),
                    onClick = onDismiss,
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
