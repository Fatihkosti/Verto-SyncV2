package com.verto.app.feature.payment.presentation.invoiceeditor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.verto.app.feature.payment.presentation.PaymentDimensions
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary

@Composable
internal fun InvoiceDraftExitGuard(
    enabled: Boolean,
    visible: Boolean,
    onDismiss: () -> Unit,
    onSaveAndExit: () -> Unit,
    onDiscard: () -> Unit,
) {
    BackHandler(enabled = enabled) { if (!visible) onDismiss() }
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        shape = RoundedCornerShape(PaymentDimensions.dp20),
        title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_7d917d330a23), color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_672ec1fb1101), color = TextSecondary) },
        confirmButton = {
            TextButton(onClick = onSaveAndExit) {
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_a7bb28042d01), color = AccentPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_28527bb31491), color = TextSecondary) }
                TextButton(onClick = onDiscard) { Text(androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_c8c41d14ffb2), color = MaterialTheme.colorScheme.error) }
            }
        },
    )
}

