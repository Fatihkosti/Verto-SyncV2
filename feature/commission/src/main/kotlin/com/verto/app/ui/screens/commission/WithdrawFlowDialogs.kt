package com.verto.app.ui.screens.commission

import com.verto.app.ui.components.VertoOutlinedTextField
import com.verto.app.ui.components.VertoOutlinedButton
import com.verto.app.ui.components.VertoButton

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BgSurface
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.ErrorColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.utils.MoneyMath
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import com.verto.app.ui.components.VertoCard
import androidx.compose.foundation.layout.PaddingValues
import com.verto.app.ui.theme.VertoSpacing

private val wfNumFmt = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US))
private fun Double.wfEng() = wfNumFmt.format(this)

private enum class WithdrawStep { OPTIONS, INVOICE_SELECT, PAYMENT, FREE_AMOUNT }

/**
 * حوارات تدفّق صرف العمولة لمسوّق واحد — مكوّن مشترك بين شاشة العمولات وشاشة تفاصيل المسوّق.
 * يدير حالته الداخلية (الخطوة/المبلغ/البنك) بنفسه، ويستدعي دوال الصرف عبر الـ callbacks.
 */
@Composable
fun WithdrawFlowDialogs(
    client: ClientCommissionBalance,
    isPayingOut: Boolean,
    onWithdrawAll: (bankName: String, txRef: String) -> Unit,
    onWithdrawSingle: (invoice: CommissionInvoiceModel, bankName: String, txRef: String) -> Unit,
    onWithdrawFree: (amount: Double, bankName: String, txRef: String) -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(WithdrawStep.OPTIONS) }
    var bankName by remember { mutableStateOf("") }
    var txRef by remember { mutableStateOf("") }
    var freeAmount by remember { mutableStateOf("") }
    var payForAll by remember { mutableStateOf(false) }
    var selectedInvoice by remember { mutableStateOf<CommissionInvoiceModel?>(null) }

    when (step) {
        WithdrawStep.OPTIONS -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = BgSurface,
            title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_47115a45a0c1, client.clientName), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(CommissionDimensions.dp6)) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_428c4252d88c, client.withdrawableCommission.wfEng()),
                        color = SuccessColor, fontWeight = FontWeight.SemiBold)
                    HorizontalDivider(color = BorderColor)
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_670109c55474), color = TextSecondary, fontSize = CommissionTextScale.sp13)
                }
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(CommissionDimensions.dp8)) {
                    VertoButton(
                        onClick = { payForAll = true; bankName = ""; txRef = ""; step = WithdrawStep.PAYMENT },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessColor)
                    ) { Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_fa57a93b4e1f)) }
                    VertoOutlinedButton(
                        onClick = { bankName = ""; txRef = ""; step = WithdrawStep.INVOICE_SELECT },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_076e4dad7c44), color = TextPrimary) }
                    VertoOutlinedButton(
                        onClick = { freeAmount = ""; bankName = ""; txRef = ""; step = WithdrawStep.FREE_AMOUNT },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_b980b2bb4f76), color = AccentPrimary) }
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextMuted)
                    }
                }
            },
            dismissButton = {}
        )

        WithdrawStep.INVOICE_SELECT -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = BgSurface,
            title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_e8b6321bf63d, client.clientName), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = CommissionDimensions.dp320)) {
                    items(client.withdrawableInvoices) { inv ->
                        VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
                            modifier = Modifier.fillMaxWidth().padding(vertical = CommissionDimensions.dp4).clickable {
                                selectedInvoice = inv; payForAll = false; step = WithdrawStep.PAYMENT
                            },
                            colors = CardDefaults.cardColors(containerColor = BgCard)
                        ) {
                            Row(
                                Modifier.padding(CommissionDimensions.dp12),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_a8d9bb06f339, inv.invoiceNumber), color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(inv.totalAmount.wfEng(), color = TextSecondary, fontSize = CommissionTextScale.sp12)
                                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_acd162add6df, inv.commission.wfEng()), color = SuccessColor, fontSize = CommissionTextScale.sp12)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextMuted) }
            }
        )

        WithdrawStep.FREE_AMOUNT -> {
            val freeAmountValue = freeAmount.toDoubleOrNull()
            val isFreeAmountValid = freeAmountValue?.let { MoneyMath.isGreaterThan(it, 0.0) } == true
            val isOverBalance = freeAmountValue?.let { MoneyMath.isGreaterThan(it, client.withdrawableCommission) } == true
            val isTransferInfoValid = bankName.isNotBlank() && txRef.isNotBlank()
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = BgSurface,
                title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_f3f0b54bdbd5, client.clientName), color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(CommissionDimensions.dp10)) {
                        Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_269fcb70f02f, client.withdrawableCommission.wfEng()),
                            color = SuccessColor, fontWeight = FontWeight.SemiBold)
                        HorizontalDivider(color = BorderColor)
                        VertoOutlinedTextField(
                            value = freeAmount,
                            onValueChange = { freeAmount = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_amount)) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = wfFieldColors()
                        )
                        if (isOverBalance) {
                            Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_f1b979b58ee9),
                                color = ErrorColor, fontSize = CommissionTextScale.sp11, fontWeight = FontWeight.SemiBold)
                        }
                        VertoOutlinedTextField(value = bankName, onValueChange = { bankName = it },
                            label = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_ce7dcf7e104e)) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                            colors = wfFieldColors())
                        VertoOutlinedTextField(value = txRef, onValueChange = { txRef = it },
                            label = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_6ee25ca44380)) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                            colors = wfFieldColors())
                    }
                },
                confirmButton = {
                    VertoButton(
                        onClick = {
                            val amt = freeAmountValue ?: 0.0
                            if (isFreeAmountValid && !isOverBalance && isTransferInfoValid) {
                                onWithdrawFree(amt, bankName.trim(), txRef.trim())
                            }
                        },
                        enabled = !isPayingOut && isFreeAmountValid && !isOverBalance && isTransferInfoValid,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                    ) {
                        if (isPayingOut) CircularProgressIndicator(Modifier.padding(CommissionDimensions.dp2), color = Color.White, strokeWidth = CommissionDimensions.dp2)
                        else Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_eb6208cfee7d))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextMuted) }
                }
            )
        }

        WithdrawStep.PAYMENT -> {
            val amount = if (payForAll) client.withdrawableCommission else selectedInvoice?.commission ?: 0.0
            val invoiceList = if (payForAll) client.withdrawableInvoices else listOfNotNull(selectedInvoice)
            val isTransferInfoValid = bankName.isNotBlank() && txRef.isNotBlank()
            val isAmountValid = MoneyMath.isGreaterThan(amount, 0.0)
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = BgSurface,
                title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_a69f0ec7ae57), color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(CommissionDimensions.dp10)) {
                        invoiceList.forEach { inv ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_a8d9bb06f339, inv.invoiceNumber), color = TextSecondary, fontSize = CommissionTextScale.sp13)
                                Text(inv.commission.wfEng(), color = SuccessColor, fontSize = CommissionTextScale.sp13)
                            }
                        }
                        HorizontalDivider(color = BorderColor)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_total), color = TextPrimary, fontWeight = FontWeight.Bold)
                            Text(amount.wfEng(), color = SuccessColor, fontWeight = FontWeight.Bold, fontSize = CommissionTextScale.sp15)
                        }
                        HorizontalDivider(color = BorderColor)
                        VertoOutlinedTextField(value = bankName, onValueChange = { bankName = it },
                            label = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_ce7dcf7e104e)) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                            colors = wfFieldColors())
                        VertoOutlinedTextField(value = txRef, onValueChange = { txRef = it },
                            label = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_6ee25ca44380)) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                            colors = wfFieldColors())
                    }
                },
                confirmButton = {
                    VertoButton(
                        onClick = {
                            if (isTransferInfoValid && isAmountValid) {
                                if (payForAll) onWithdrawAll(bankName.trim(), txRef.trim())
                                else selectedInvoice?.let { onWithdrawSingle(it, bankName.trim(), txRef.trim()) }
                            }
                        },
                        enabled = !isPayingOut && isTransferInfoValid && isAmountValid,
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessColor)
                    ) {
                        if (isPayingOut) CircularProgressIndicator(Modifier.padding(CommissionDimensions.dp2), color = Color.White, strokeWidth = CommissionDimensions.dp2)
                        else Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_6d57d7cb7cda))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextMuted) }
                }
            )
        }
    }
}

@Composable
private fun wfFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AccentPrimary, unfocusedBorderColor = BorderColor,
    focusedLabelColor = AccentPrimary, focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
