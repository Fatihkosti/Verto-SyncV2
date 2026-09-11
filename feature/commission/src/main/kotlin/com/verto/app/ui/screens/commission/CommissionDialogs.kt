package com.verto.app.ui.screens.commission

import com.verto.app.ui.components.VertoOutlinedTextField
import com.verto.app.ui.components.VertoOutlinedButton
import com.verto.app.ui.components.VertoButton

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.verto.app.ui.theme.*
import com.verto.app.ui.components.VertoCard
import com.verto.app.ui.components.VertoBottomSheet
import androidx.compose.foundation.layout.PaddingValues
import com.verto.app.ui.theme.VertoSpacing

// ─────────────────────────────────────────────────────────────────────────────
// Client Picker Dialog — عملاء المسوقين وأصحاب الورش
// ─────────────────────────────────────────────────────────────────────────────
@Composable
internal fun ClientPickerDialog(
    clients: List<MarketingClient>,
    onSelect: (MarketingClient) -> Unit,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = clients.filter { client ->
        client.linkStatus != MarketerLinkStatus.REGISTERED &&
            (query.isBlank() || client.name.contains(query, ignoreCase = true))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = BgSurface,
        title = {
            Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_05c742a3eb24), color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CommissionDimensions.dp8)) {
                VertoOutlinedTextField(
                    value         = query,
                    onValueChange = { query = it },
                    placeholder   = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_1d3863672e3e), color = TextMuted) },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = AccentPrimary,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor     = TextPrimary,
                        unfocusedTextColor   = TextPrimary
                    )
                )
                if (filtered.isEmpty()) {
                    Text(
                        androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_9fe12146d9cc),
                        color = TextMuted, fontSize = CommissionTextScale.sp13,
                        modifier = Modifier.fillMaxWidth().padding(vertical = CommissionDimensions.dp12),
                        textAlign = TextAlign.Center
                    )
                } else {
                    LazyColumn(
                        modifier       = Modifier.heightIn(max = CommissionDimensions.dp320),
                        verticalArrangement = Arrangement.spacedBy(CommissionDimensions.dp4)
                    ) {
                        items(filtered) { client ->
                            val canGenerateCode = client.linkStatus != MarketerLinkStatus.REGISTERED
                            VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
                                modifier = Modifier.fillMaxWidth().then(
                                    if (canGenerateCode) Modifier.clickable { onSelect(client) } else Modifier
                                ),
                                colors   = CardDefaults.cardColors(containerColor = BgCard)
                            ) {
                                Row(
                                    Modifier.padding(CommissionDimensions.dp12),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(client.name, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(CommissionDimensions.dp6)
                                        ) {
                                            Text(client.typeLabel, color = AccentLight, fontSize = CommissionTextScale.sp12)
                                            LinkStatusBadge(client.linkStatus)
                                        }
                                    }
                                    if (canGenerateCode) Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_60ecbc7f40b3), color = TextMuted, fontSize = CommissionTextScale.sp18)
                                }
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
}

@Composable
internal fun LinkStatusBadge(status: MarketerLinkStatus) {
    val color = when (status) {
        MarketerLinkStatus.REGISTERED   -> SuccessColor
        MarketerLinkStatus.PENDING_CODE -> WarningColor
        MarketerLinkStatus.NOT_LINKED   -> TextMuted
    }
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(CommissionDimensions.dp8)
    ) {
        Text(
            status.label,
            color = color,
            fontSize = CommissionTextScale.sp10,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = CommissionDimensions.dp7, vertical = CommissionDimensions.dp3)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Generated Code Dialog — عرض الكود مع نسخ
// ─────────────────────────────────────────────────────────────────────────────
@Composable
internal fun GeneratedCodeDialog(
    code: String,
    clientName: String,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = BgSurface,
        title = {
            Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_d25234661453, clientName), color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CommissionDimensions.dp12),
                modifier = Modifier.fillMaxWidth()
            ) {
                VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
                    colors = CardDefaults.cardColors(containerColor = AccentDim),
                    shape  = RoundedCornerShape(CommissionDimensions.dp12)
                ) {
                    Text(
                        text       = code,
                        fontSize   = CommissionTextScale.sp36,
                        fontWeight = FontWeight.ExtraBold,
                        color      = AccentPrimary,
                        letterSpacing = CommissionTextScale.sp8,
                        modifier   = Modifier.padding(horizontal = CommissionDimensions.dp24, vertical = CommissionDimensions.dp16)
                    )
                }
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_716bfc4b58fb),
                    color = TextMuted, fontSize = CommissionTextScale.sp12, textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            VertoButton(
                onClick = {
                    clipboard.setText(AnnotatedString(code))
                    copied = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (copied) SuccessColor else AccentPrimary
                )
            ) {
                Text(if (copied) androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_v298_995cd49dfd45_2) else androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_v298_995cd49dfd45))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_close), color = TextMuted) }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Withdrawal Requests Sheet
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WithdrawalRequestsSheet(
    items: List<WithdrawalRequestUiModel>,
    isActionInProgress: Boolean,
    onApprove: (String, String) -> Unit,
    onReject: (String, String) -> Unit,
    onComplete: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var approveTargetId  by remember { mutableStateOf<String?>(null) }
    var rejectTargetId   by remember { mutableStateOf<String?>(null) }
    var completeTargetId by remember { mutableStateOf<String?>(null) }
    var adminNoteInput   by remember { mutableStateOf("") }
    var txRefInput       by remember { mutableStateOf("") }

    VertoBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = BgSurface,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = CommissionDimensions.dp32)) {
            Text(
                androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_0589dd253fa2),
                color      = TextPrimary, fontWeight = FontWeight.Bold, fontSize = CommissionTextScale.sp17,
                modifier   = Modifier.padding(horizontal = CommissionDimensions.dp16, vertical = CommissionDimensions.dp12)
            )
            if (items.isEmpty()) {
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_f8fd41b8403e),
                    color    = TextMuted,
                    modifier = Modifier.fillMaxWidth().padding(CommissionDimensions.dp32),
                    textAlign = TextAlign.Center
                )
            } else {
                LazyColumn(
                    modifier       = Modifier.heightIn(max = CommissionDimensions.dp520),
                    contentPadding = PaddingValues(horizontal = CommissionDimensions.dp16, vertical = CommissionDimensions.dp4),
                    verticalArrangement = Arrangement.spacedBy(CommissionDimensions.dp10)
                ) {
                    items(items, key = { it.request.id }) { item ->
                        WithdrawalRequestCard(
                            item               = item,
                            isActionInProgress = isActionInProgress,
                            onApprove          = { approveTargetId = item.request.id; txRefInput = "" },
                            onReject           = { rejectTargetId  = item.request.id; adminNoteInput = "" },
                            onComplete         = { completeTargetId = item.request.id; adminNoteInput = "" }
                        )
                    }
                }
            }
        }
    }

    // ── حوار الموافقة (رقم العملية) ─────────────────────────────────────────
    val approveId = approveTargetId
    if (approveId != null) {
        AlertDialog(
            onDismissRequest = { approveTargetId = null },
            containerColor   = BgSurface,
            title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_8e89195c0ef6), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(CommissionDimensions.dp8)) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_59a1d189fbc9), color = TextSecondary, fontSize = CommissionTextScale.sp13)
                    VertoOutlinedTextField(
                        value         = txRefInput,
                        onValueChange = { txRefInput = it },
                        label         = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_6ee25ca44380)) },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = SuccessColor, unfocusedBorderColor = BorderColor,
                            focusedLabelColor    = SuccessColor, focusedTextColor     = TextPrimary,
                            unfocusedTextColor   = TextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                VertoButton(
                    onClick  = {
                        onApprove(approveId, txRefInput.trim())
                        approveTargetId = null
                    },
                    enabled  = !isActionInProgress && txRefInput.isNotBlank(),
                    colors   = ButtonDefaults.buttonColors(containerColor = SuccessColor)
                ) { Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_c05fd18b76b9)) }
            },
            dismissButton = {
                TextButton(onClick = { approveTargetId = null }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextMuted) }
            }
        )
    }

    // ── حوار الرفض ──────────────────────────────────────────────────────────
    val rejectId = rejectTargetId
    if (rejectId != null) {
        AlertDialog(
            onDismissRequest = { rejectTargetId = null },
            containerColor   = BgSurface,
            title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_6cc5c27df15f), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                VertoOutlinedTextField(
                    value = adminNoteInput, onValueChange = { adminNoteInput = it },
                    label = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_6dc59976d09b)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = ErrorColor, unfocusedBorderColor = BorderColor,
                        focusedLabelColor    = ErrorColor, focusedTextColor     = TextPrimary,
                        unfocusedTextColor   = TextPrimary
                    )
                )
            },
            confirmButton = {
                VertoButton(
                    onClick  = { onReject(rejectId, adminNoteInput); rejectTargetId = null },
                    enabled  = !isActionInProgress,
                    colors   = ButtonDefaults.buttonColors(containerColor = ErrorColor, contentColor = OnDanger)
                ) { Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_5574893f2c9e)) }
            },
            dismissButton = {
                TextButton(onClick = { rejectTargetId = null }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextMuted) }
            }
        )
    }

    // ── حوار الإتمام ────────────────────────────────────────────────────────
    val completeId = completeTargetId
    if (completeId != null) {
        AlertDialog(
            onDismissRequest = { completeTargetId = null },
            containerColor   = BgSurface,
            title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_042f5d26d592), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(CommissionDimensions.dp8)) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_fc12d76c94dc), color = TextSecondary, fontSize = CommissionTextScale.sp13)
                    VertoOutlinedTextField(
                        value = adminNoteInput, onValueChange = { adminNoteInput = it },
                        label = { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_note_optional)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = SuccessColor, unfocusedBorderColor = BorderColor,
                            focusedLabelColor    = SuccessColor, focusedTextColor     = TextPrimary,
                            unfocusedTextColor   = TextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                VertoButton(
                    onClick  = { onComplete(completeId, adminNoteInput); completeTargetId = null },
                    enabled  = !isActionInProgress,
                    colors   = ButtonDefaults.buttonColors(containerColor = SuccessColor)
                ) { Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_d0c2dfa7496a)) }
            },
            dismissButton = {
                TextButton(onClick = { completeTargetId = null }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextMuted) }
            }
        )
    }
}

@Composable
internal fun WithdrawalRequestCard(
    item: WithdrawalRequestUiModel,
    isActionInProgress: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onComplete: () -> Unit
) {
    val request = item.request
    val statusColor = item.status.color()
    val statusLabel = item.status.label.ifBlank { request.status }
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = BgCard),
        shape    = RoundedCornerShape(CommissionDimensions.dp12)
    ) {
        Column(Modifier.padding(CommissionDimensions.dp12), verticalArrangement = Arrangement.spacedBy(CommissionDimensions.dp6)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_c79f71243f41, request.amount.eng()),
                    color = SuccessColor, fontWeight = FontWeight.ExtraBold, fontSize = CommissionTextScale.sp18
                )
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(CommissionDimensions.dp8)
                ) {
                    Text(
                        statusLabel, color = statusColor,
                        fontSize = CommissionTextScale.sp11, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = CommissionDimensions.dp8, vertical = CommissionDimensions.dp4)
                    )
                }
            }
            Text(item.clientName, color = TextPrimary, fontSize = CommissionTextScale.sp13, fontWeight = FontWeight.SemiBold)
            Text(
                androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_82a323b101e6, item.marketerBalance.eng()),
                color = if (item.isAmountOverBalance) ErrorColor else TextSecondary,
                fontSize = CommissionTextScale.sp12
            )
            if (item.isAmountOverBalance) {
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_1056082abe24),
                    color = ErrorColor,
                    fontSize = CommissionTextScale.sp11,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (item.isPendingTotalOverBalance) {
                Text(
                    androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_f70eef0ff091, item.pendingTotalForClient.eng()),
                    color = ErrorColor,
                    fontSize = CommissionTextScale.sp11,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (request.bankName.isNotBlank())
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_c1aacd9629e6, request.bankName, request.bankAccount), color = TextSecondary, fontSize = CommissionTextScale.sp12)
            Text(
                request.requestedAt.take(10),
                color = TextMuted, fontSize = CommissionTextScale.sp11
            )
            if (!request.note.isNullOrBlank())
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_97e50ebf9be9, request.note), color = TextMuted, fontSize = CommissionTextScale.sp11)
            if (!request.transactionRef.isNullOrBlank())
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_8c911735d5e5, request.transactionRef), color = SuccessColor, fontSize = CommissionTextScale.sp12, fontWeight = FontWeight.SemiBold)
            if (!request.adminNote.isNullOrBlank())
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_b12fe8ef5464, request.adminNote), color = AccentLight, fontSize = CommissionTextScale.sp11)

            // أزرار الإجراءات
            when (item.status) {
                CommissionWithdrawalStatus.PENDING -> Row(horizontalArrangement = Arrangement.spacedBy(CommissionDimensions.dp8)) {
                    VertoButton(
                        onClick  = onApprove, modifier = Modifier.weight(1f),
                        enabled  = !isActionInProgress && !item.isAmountOverBalance,
                        colors   = ButtonDefaults.buttonColors(containerColor = SuccessColor),
                        contentPadding = PaddingValues(vertical = CommissionDimensions.dp6)
                    ) { Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_c05fd18b76b9), fontSize = CommissionTextScale.sp13) }
                    VertoOutlinedButton(
                        onClick  = onReject, modifier = Modifier.weight(1f),
                        enabled  = !isActionInProgress,
                        contentPadding = PaddingValues(vertical = CommissionDimensions.dp6)
                    ) { Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_5574893f2c9e), fontSize = CommissionTextScale.sp13, color = ErrorColor) }
                }
                CommissionWithdrawalStatus.APPROVED -> VertoButton(
                    onClick  = onComplete, modifier = Modifier.fillMaxWidth(),
                    enabled  = !isActionInProgress,
                    colors   = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = OnSecondary),
                    contentPadding = PaddingValues(vertical = CommissionDimensions.dp6)
                ) { Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_12c6c4cde743), fontSize = CommissionTextScale.sp13) }
                else -> Unit
            }
        }
    }
}

@Composable
internal fun CommissionWithdrawalStatus.color(): Color = when (this) {
    CommissionWithdrawalStatus.PENDING   -> WarningColor
    CommissionWithdrawalStatus.APPROVED  -> AccentBlue
    CommissionWithdrawalStatus.COMPLETED -> SuccessColor
    CommissionWithdrawalStatus.REJECTED  -> ErrorColor
    CommissionWithdrawalStatus.UNKNOWN   -> TextMuted
}
