package com.verto.app.feature.management.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.management.domain.model.BenzineClientError
import com.verto.app.feature.management.domain.model.BenzineJoinCodeCandidate
import com.verto.app.feature.management.domain.model.BenzineUserHealth
import com.verto.app.ui.components.VertoCard
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoTopAppBar
import com.verto.app.ui.theme.VertoSpacing
import com.verto.feature.management.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenzineManagementScreen(
    onBack: () -> Unit,
    onOpenCommissions: () -> Unit,
    onOpenMarketers: () -> Unit,
    viewModel: BenzineControlPlaneViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showJoinCodePicker by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            VertoTopAppBar(
                title = {
                    Column {
                        Text(
                            text = androidx.compose.ui.res.stringResource(R.string.ds_bdda0c5b4a61),
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "مركز تشغيل AutoDrive",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    VertoIconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_navigate_back),
                        )
                    }
                },
                actions = {
                    VertoIconButton(
                        onClick = viewModel::refresh,
                        enabled = !state.isRefreshing,
                    ) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(ManagementDimensions.dp18))
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "تحديث")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        BenzineControlPlaneContent(
            state = state,
            onNewJoinCode = { showJoinCodePicker = true },
            onOpenCommissions = onOpenCommissions,
            onOpenMarketers = onOpenMarketers,
            modifier = Modifier.padding(padding),
        )
    }

    if (showJoinCodePicker) {
        JoinCodeCandidateDialog(
            candidates = state.joinCodeCandidates,
            issuingClientId = state.issueCodeClientId,
            onSelect = { candidate ->
                showJoinCodePicker = false
                viewModel.issueJoinCode(candidate)
            },
            onDismiss = { showJoinCodePicker = false },
        )
    }

    state.generatedJoinCode?.let { code ->
        GeneratedJoinCodeDialog(
            code = code,
            clientName = state.generatedJoinCodeClientName,
            onDismiss = viewModel::clearGeneratedJoinCode,
        )
    }
}

@Composable
private fun BenzineControlPlaneContent(
    state: BenzineControlPlaneState,
    onNewJoinCode: () -> Unit,
    onOpenCommissions: () -> Unit,
    onOpenMarketers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.size(ManagementDimensions.dp12))
            Text("جارٍ تحميل حالة AutoDrive")
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = ManagementDimensions.dp16,
            vertical = ManagementDimensions.dp14,
        ),
        verticalArrangement = Arrangement.spacedBy(ManagementDimensions.dp12),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(ManagementDimensions.dp6)) {
                Text(
                    "الحالة التشغيلية",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "إصدار أكواد الانضمام، المستخدمون، المزامنة والأخطاء من مكان واحد.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        state.errorMessage?.let { message ->
            item { InlineMessage(message = message, isError = true) }
        }
        state.actionMessage?.let { message ->
            item { InlineMessage(message = message, isError = false) }
        }

        item {
            NewJoinCodeCard(
                availableCount = state.joinCodeCandidates.size,
                isBusy = state.isIssuingCode,
                onClick = onNewJoinCode,
            )
        }

        item { SummaryGrid(state) }

        item {
            SectionHeader(
                title = "حالة المستخدمين",
                subtitle = "المستخدمون المرتبطون فعليًا بـ Verto",
                icon = Icons.Filled.HealthAndSafety,
            )
        }

        if (state.userHealth.isEmpty()) {
            item { EmptyStateCard("لا توجد عضويات AutoDrive مرتبطة حتى الآن") }
        } else {
            items(state.userHealth, key = { it.clientId }) { health ->
                UserHealthCard(health)
            }
        }

        item {
            SectionHeader(
                title = "الأخطاء الأخيرة",
                subtitle = "الأخطاء المرسلة من أجهزة AutoDrive محفوظة في السيرفر",
                icon = Icons.Filled.BugReport,
            )
        }

        if (state.recentErrors.isEmpty()) {
            item { EmptyStateCard("لا توجد أخطاء مسجلة") }
        } else {
            items(state.recentErrors.take(25), key = { it.errorId }) { error ->
                ClientErrorCard(error)
            }
        }

        item {
            SectionHeader(
                title = "الإدارة التجارية",
                subtitle = "المسوقون والورش والعمولات",
                icon = Icons.Filled.Groups,
            )
        }

        item {
            BenzineDestinationCard(
                title = androidx.compose.ui.res.stringResource(R.string.ds_4b20c54bf31d),
                description = "الاستحقاق، طلبات السحب، الصرف وسجل العمولات",
                icon = Icons.Filled.Payments,
                onClick = onOpenCommissions,
            )
        }
        item {
            BenzineDestinationCard(
                title = androidx.compose.ui.res.stringResource(R.string.ds_7f080fc4f5c1),
                description = "الأداء الأسبوعي، النشاط، التراجع والمتابعة",
                icon = Icons.Filled.Groups,
                onClick = onOpenMarketers,
            )
        }
    }
}

@Composable
private fun NewJoinCodeCard(
    availableCount: Int,
    isBusy: Boolean,
    onClick: () -> Unit,
) {
    VertoCard(
        contentPadding = PaddingValues(VertoSpacing.none),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isBusy) { onClick() },
        shape = RoundedCornerShape(ManagementDimensions.dp18),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(ManagementDimensions.dp18),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.HowToReg,
                contentDescription = null,
                modifier = Modifier.size(ManagementDimensions.dp30),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(ManagementDimensions.dp14))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ManagementDimensions.dp4)) {
                Text("كود انضمام جديد", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (availableCount == 0) "لا توجد ورش أو مسوقون غير مربوطين"
                    else "$availableCount متاح للربط — تظهر غير المربوطين فقط",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isBusy) {
                CircularProgressIndicator(modifier = Modifier.size(ManagementDimensions.dp22))
            } else {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
private fun SummaryGrid(state: BenzineControlPlaneState) {
    Column(verticalArrangement = Arrangement.spacedBy(ManagementDimensions.dp8)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ManagementDimensions.dp8),
        ) {
            SummaryCard("مرتبط", state.userHealth.size.toString(), Modifier.weight(1f))
            SummaryCard("غير مربوط", state.joinCodeCandidates.size.toString(), Modifier.weight(1f))
            SummaryCard("سليم", state.healthyCount.toString(), Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ManagementDimensions.dp8),
        ) {
            SummaryCard("تحذير", state.warningCount.toString(), Modifier.weight(1f))
            SummaryCard("غير متصل", state.offlineCount.toString(), Modifier.weight(1f))
            SummaryCard("أخطاء", state.recentErrors.size.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    VertoCard(
        modifier = modifier,
        contentPadding = PaddingValues(ManagementDimensions.dp12),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun JoinCodeCandidateDialog(
    candidates: List<BenzineJoinCodeCandidate>,
    issuingClientId: String?,
    onSelect: (BenzineJoinCodeCandidate) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(candidates, query) {
        val q = query.trim()
        if (q.isBlank()) candidates else candidates.filter {
            it.name.contains(q, ignoreCase = true) || it.phone.contains(q)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("كود انضمام جديد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ManagementDimensions.dp10)) {
                Text(
                    "تظهر فقط سجلات المسوقين وأصحاب الورش غير المرتبطة بـ AutoDrive.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("بحث بالاسم أو الهاتف") },
                    singleLine = true,
                )
                if (filtered.isEmpty()) {
                    Text(
                        if (candidates.isEmpty()) "كل الورش والمسوقين الحاليين مربوطون"
                        else "لا توجد نتيجة مطابقة",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = ManagementDimensions.dp320),
                        verticalArrangement = Arrangement.spacedBy(ManagementDimensions.dp6),
                    ) {
                        items(filtered, key = { it.clientId }) { candidate ->
                            val busy = issuingClientId == candidate.clientId
                            VertoCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = issuingClientId == null) { onSelect(candidate) },
                                contentPadding = PaddingValues(ManagementDimensions.dp12),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(candidate.name, fontWeight = FontWeight.Bold)
                                        Text(
                                            "${accountTypeLabel(candidate.accountType)}${candidate.phone.takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty()}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (busy) {
                                        CircularProgressIndicator(modifier = Modifier.size(ManagementDimensions.dp20))
                                    } else {
                                        Text("إصدار", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )
}

@Composable
private fun GeneratedJoinCodeDialog(
    code: String,
    clientName: String,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("كود $clientName") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ManagementDimensions.dp12),
            ) {
                Text(
                    code,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "يدخل المستخدم هذا الكود في AutoDrive بعد نجاح OTP. الكود صالح لمدة 24 ساعة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(ManagementDimensions.dp8))
                    Text("نسخ الكود")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("تم") } },
    )
}

@Composable
private fun SectionHeader(title: String, subtitle: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(ManagementDimensions.dp8))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UserHealthCard(item: BenzineUserHealth) {
    val (statusLabel, statusColor) = healthLabelAndColor(item.healthStatus)
    VertoCard(contentPadding = PaddingValues(ManagementDimensions.dp14)) {
        Column(verticalArrangement = Arrangement.spacedBy(ManagementDimensions.dp6)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.clientName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(accountTypeLabel(item.accountType), style = MaterialTheme.typography.bodySmall)
                }
                Text(statusLabel, color = statusColor, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Text(
                "آخر ظهور: ${formatTimestamp(item.lastSeenAt)} • آخر مزامنة: ${formatTimestamp(item.lastSuccessfulSyncAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "الإصدار ${item.appVersion.ifBlank { "غير معروف" }} • أوامر معلقة ${item.pendingCommands} • فاشلة ${item.failedCommands} • Push ${item.pushStatus}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ClientErrorCard(error: BenzineClientError) {
    val critical = error.severity == "CRITICAL" || error.severity == "ERROR"
    VertoCard(contentPadding = PaddingValues(ManagementDimensions.dp14)) {
        Column(verticalArrangement = Arrangement.spacedBy(ManagementDimensions.dp6)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    error.clientName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    error.severity,
                    color = if (critical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(error.errorCode, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (error.safeMessage.isNotBlank()) {
                Text(
                    error.safeMessage,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "${error.category} / ${error.operation.ifBlank { "غير محدد" }} • ${formatTimestamp(error.receivedAt)} • v${error.appVersion.ifBlank { "?" }}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InlineMessage(message: String, isError: Boolean) {
    VertoCard(contentPadding = PaddingValues(ManagementDimensions.dp12)) {
        Text(
            message,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun EmptyStateCard(message: String) {
    VertoCard(contentPadding = PaddingValues(ManagementDimensions.dp16)) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BenzineDestinationCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    VertoCard(
        contentPadding = PaddingValues(VertoSpacing.none),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(ManagementDimensions.dp18),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = ManagementDimensions.dp2),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = ManagementDimensions.dp18,
                vertical = ManagementDimensions.dp20,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(ManagementDimensions.dp30),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(ManagementDimensions.dp14))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ManagementDimensions.dp6),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun healthLabelAndColor(status: String): Pair<String, Color> = when (status) {
    "HEALTHY" -> "سليم" to MaterialTheme.colorScheme.primary
    "WARNING" -> "تحذير" to MaterialTheme.colorScheme.tertiary
    "CRITICAL" -> "حرج" to MaterialTheme.colorScheme.error
    "OFFLINE" -> "غير متصل" to MaterialTheme.colorScheme.onSurfaceVariant
    "NO_TELEMETRY" -> "لا توجد بيانات" to MaterialTheme.colorScheme.tertiary
    "NOT_ACTIVATED" -> "غير مفعّل" to MaterialTheme.colorScheme.onSurfaceVariant
    else -> status to MaterialTheme.colorScheme.onSurfaceVariant
}

private fun accountTypeLabel(type: String): String = when (type) {
    "MARKETER" -> "مسوّق"
    "WORKSHOP_OWNER" -> "صاحب ورشة"
    else -> type
}

private fun formatTimestamp(value: String?): String {
    if (value.isNullOrBlank()) return "غير متاح"
    return value.replace('T', ' ').substringBefore('.').take(16)
}
