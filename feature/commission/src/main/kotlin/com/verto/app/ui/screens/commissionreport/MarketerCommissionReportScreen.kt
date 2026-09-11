package com.verto.app.ui.screens.commissionreport

import com.verto.app.ui.components.VertoButton
import com.verto.app.ui.screens.commission.CommissionDimensions
import com.verto.app.ui.screens.commission.CommissionTextScale

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.commission.application.MarketerCommissionReportItem
import com.verto.app.feature.commission.application.MarketerCommissionRowItem
import com.verto.app.ui.theme.AccentBlue
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BgDeep
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.WarningColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import com.verto.app.ui.components.VertoCard
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoTopAppBar
import com.verto.app.ui.theme.VertoSpacing

private val numFmt = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US))
private fun Double.fmt() = numFmt.format(this)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MarketerCommissionReportScreen(
    clientId: String,
    from: Long? = null,
    to: Long? = null,
    onBack: () -> Unit = {},
    viewModel: MarketerCommissionReportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val role by viewModel.role.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    // حارس الصلاحية: التقرير للمدير حصراً.
    LaunchedEffect(role) { if (role != null && role != "admin") onBack() }
    LaunchedEffect(clientId, from, to) { viewModel.load(clientId, from, to) }
    LaunchedEffect(state.sendResult) {
        state.sendResult?.let {
            snackbarHost.showSnackbar(it)
            viewModel.consumeSendResult()
        }
    }

    Scaffold(
        containerColor = BgDeep,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            VertoTopAppBar(
                title = {
                    Text(
                        state.report?.let { androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_v298_d78bf81031a8_2, it.marketerName) } ?: androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_v298_d78bf81031a8),
                        color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = CommissionTextScale.sp16
                    )
                },
                navigationIcon = {
                    VertoIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_navigate_back), tint = TextPrimary)
                    }
                },
                actions = {
                    VertoButton(
                        onClick = { viewModel.sendReport(clientId) },
                        enabled = !state.isSending && state.report != null,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        modifier = Modifier.padding(end = CommissionDimensions.dp8)
                    ) {
                        if (state.isSending) {
                            CircularProgressIndicator(Modifier.height(CommissionDimensions.dp16), color = TextPrimary, strokeWidth = CommissionDimensions.dp2)
                        } else {
                            Icon(Icons.Filled.Send, null, modifier = Modifier.height(CommissionDimensions.dp16))
                            Spacer(Modifier.height(CommissionDimensions.dp0))
                            Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_93ee737a2a1c), fontSize = CommissionTextScale.sp12)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep)
            )
        }
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentPrimary)
            }
            state.error != null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(state.error ?: "", color = WarningColor, fontSize = CommissionTextScale.sp14)
            }
            else -> {
                val report = state.report ?: return@Scaffold
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(CommissionDimensions.dp16),
                    verticalArrangement = Arrangement.spacedBy(CommissionDimensions.dp12)
                ) {
                    item { SummaryCard(report) }
                    item {
                        Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_aabd26a57090, report.rows.size), color = TextPrimary,
                            fontWeight = FontWeight.Bold, fontSize = CommissionTextScale.sp15)
                    }
                    if (report.rows.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(CommissionDimensions.dp32), contentAlignment = Alignment.Center) {
                                Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_796bc50ef880), color = TextMuted, fontSize = CommissionTextScale.sp13)
                            }
                        }
                    } else {
                        items(report.rows) { row -> ReportRow(row) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(report: MarketerCommissionReportItem) {
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), colors = CardDefaults.cardColors(containerColor = BgCard), shape = RoundedCornerShape(CommissionDimensions.dp16)) {
        Column(Modifier.fillMaxWidth().padding(CommissionDimensions.dp16)) {
            Text(report.periodLabel, color = TextSecondary, fontSize = CommissionTextScale.sp12)
            Spacer(Modifier.height(CommissionDimensions.dp10))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CommissionDimensions.dp10)) {
                MetricCell("إجمالي العمولة", report.totalCommission.fmt(), AccentPrimary, Modifier.weight(1f))
                MetricCell("قابل للسحب", report.withdrawableTotal.fmt(), SuccessColor, Modifier.weight(1f))
            }
            Spacer(Modifier.height(CommissionDimensions.dp10))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CommissionDimensions.dp10)) {
                MetricCell("مدفوعة", report.paidTotal.fmt(), AccentBlue, Modifier.weight(1f))
                MetricCell("قيد الانتظار", report.pendingTotal.fmt(), WarningColor, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricCell(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = TextSecondary, fontSize = CommissionTextScale.sp11)
        Spacer(Modifier.height(CommissionDimensions.dp2))
        Text(value, color = color, fontSize = CommissionTextScale.sp16, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun ReportRow(row: MarketerCommissionRowItem) {
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), colors = CardDefaults.cardColors(containerColor = BgCard), shape = RoundedCornerShape(CommissionDimensions.dp12)) {
        Column(Modifier.fillMaxWidth().padding(CommissionDimensions.dp12)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_a8d9bb06f339, row.invoiceNumber), color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(row.commission.fmt(), color = SuccessColor, fontWeight = FontWeight.Bold, fontSize = CommissionTextScale.sp14)
            }
            Spacer(Modifier.height(CommissionDimensions.dp6))
            HorizontalDivider(color = BorderColor)
            Spacer(Modifier.height(CommissionDimensions.dp6))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(row.dateLabel, color = TextSecondary, fontSize = CommissionTextScale.sp12)
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.commission.R.string.commission_ds_33f370dcf73f, row.invoiceTotal.fmt()), color = TextSecondary, fontSize = CommissionTextScale.sp12)
                Text(row.statusLabel, color = TextMuted, fontSize = CommissionTextScale.sp12)
            }
        }
    }
}
