package com.verto.app.feature.integration.optimal.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceAuditEntry
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceDetailImage
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceDetails
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceInvoiceItem
import com.verto.app.feature.integration.optimal.domain.model.MaintenancePayment
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import com.verto.app.ui.components.VertoCard
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoTopAppBar
import com.verto.app.ui.theme.VertoSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceDetailsScreen(
    onBack: () -> Unit,
    viewModel: MaintenanceDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val errorMessage = state.errorMessage

    Scaffold(
        topBar = {
            VertoTopAppBar(
                title = { Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_1c6509e5ac6c), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    VertoIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_navigate_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingDetails(Modifier.padding(padding))
            errorMessage != null -> DetailsMessage(errorMessage, Modifier.padding(padding))
            state.isNotFound || state.details == null -> DetailsMessage(
                "السجل غير موجود أو لا ينتمي إلى المؤسسة الحالية",
                Modifier.padding(padding),
            )
            else -> MaintenanceDetailsContent(
                details = requireNotNull(state.details),
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun MaintenanceDetailsContent(
    details: MaintenanceDetails,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(OptimalDimensions.dp16),
        verticalArrangement = Arrangement.spacedBy(OptimalDimensions.dp12),
    ) {
        item { MaintenanceIdentityCard(details) }
        item { InvoiceSummaryCard(details) }
        if (details.invoice.items.isNotEmpty()) {
            item { SectionTitle("بنود الفاتورة", Icons.Default.ReceiptLong) }
            items(details.invoice.items, key = { it.itemId }) { item -> InvoiceItemRow(item) }
        }
        if (details.invoice.payments.isNotEmpty()) {
            item { SectionTitle("الدفعات", Icons.Default.ReceiptLong) }
            items(details.invoice.payments, key = { it.paymentId }) { payment -> PaymentRow(payment) }
        }
        item { NotesCard(details.notes) }
        if (details.images.isNotEmpty()) {
            item { SectionTitle("الصور", Icons.Default.Image) }
            item { MaintenanceImages(details.images) }
        }
        if (details.audit.isNotEmpty()) {
            item { SectionTitle("سجل التعديلات", Icons.Default.History) }
            items(details.audit, key = { it.auditId }) { entry -> AuditRow(entry) }
        }
    }
}

@Composable
private fun MaintenanceIdentityCard(details: MaintenanceDetails) {
    val vehicleTitle = details.vehicleSnapshot.name
        .ifBlank { details.vehicleSnapshot.vehicleType }
        .ifBlank { "سيارة غير مسماة" }
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(OptimalDimensions.dp16),
            verticalArrangement = Arrangement.spacedBy(OptimalDimensions.dp8),
        ) {
            Text(details.companyName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(OptimalDimensions.dp8), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DirectionsCar, contentDescription = null)
                Text(vehicleTitle, style = MaterialTheme.typography.titleMedium)
                if (details.vehicleReference == null) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_faf440447b46), color = MaterialTheme.colorScheme.tertiary)
                }
            }
            if (details.vehicleSnapshot.plateNumber.isNotBlank()) {
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_64d753fae6de, details.vehicleSnapshot.plateNumber))
            }
            if (details.driverOrDelegate.isNotBlank()) {
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_3db2703543fe, details.driverOrDelegate))
            }
            Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_3d860f5eb1dc, formatDate(details.createdAt)))
            Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_ad51e7c08e52, details.syncStatus.arabicLabel()))
        }
    }
}

@Composable
private fun InvoiceSummaryCard(details: MaintenanceDetails) {
    val invoice = details.invoice
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(OptimalDimensions.dp16),
            verticalArrangement = Arrangement.spacedBy(OptimalDimensions.dp8),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_a8d9bb06f339, invoice.invoiceNumber), fontWeight = FontWeight.Bold)
                if (invoice.isVoided) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(OptimalDimensions.dp8),
                    ) {
                        Text(
                            androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_d96db3cf6533),
                            modifier = Modifier.padding(horizontal = OptimalDimensions.dp10, vertical = OptimalDimensions.dp4),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            if (invoice.description.isNotBlank()) Text(invoice.description)
            HorizontalDivider()
            FinancialLine("الإجمالي", invoice.totalAmount)
            FinancialLine("المدفوع", invoice.paidAmount)
            FinancialLine("المتبقي", invoice.remainingAmount, bold = true)
        }
    }
}

@Composable
private fun FinancialLine(label: String, amount: Double, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(formatMoney(amount), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun InvoiceItemRow(item: MaintenanceInvoiceItem) {
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(OptimalDimensions.dp14), verticalArrangement = Arrangement.spacedBy(OptimalDimensions.dp4)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(formatMoney(item.totalPrice))
            }
            Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_d603b6287052, item.quantity, formatMoney(item.unitPrice)))
            if (item.description.isNotBlank()) {
                Text(item.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PaymentRow(payment: MaintenancePayment) {
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(OptimalDimensions.dp14), verticalArrangement = Arrangement.spacedBy(OptimalDimensions.dp4)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(payment.method.paymentLabel(), fontWeight = FontWeight.Bold)
                Text(formatMoney(payment.amount), fontWeight = FontWeight.Bold)
            }
            Text(formatDate(payment.paidAt), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (payment.employeeName.isNotBlank()) Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_5ed20b433039, payment.employeeName))
            if (payment.note.isNotBlank()) Text(payment.note)
            if (payment.reversedPaymentId != null) {
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_a69aa5a4ce4c), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun NotesCard(notes: String) {
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(OptimalDimensions.dp16), verticalArrangement = Arrangement.spacedBy(OptimalDimensions.dp6)) {
            Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_4d2088ca692d), fontWeight = FontWeight.Bold)
            Text(notes.ifBlank { androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_913faab4fb17) })
        }
    }
}

@Composable
private fun MaintenanceImages(images: List<MaintenanceDetailImage>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(OptimalDimensions.dp10)) {
        items(images, key = { it.imageId }) { image -> PrivateMaintenanceImage(image) }
    }
}

@Composable
private fun PrivateMaintenanceImage(image: MaintenanceDetailImage) {
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), modifier = Modifier.size(OptimalDimensions.dp150)) {
        val uri = image.localUri
        if (uri == null) {
            MissingImage()
        } else {
            SubcomposeAsyncImage(
                model = uri,
                contentDescription = androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_a64057ab2a3a),
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(OptimalDimensions.dp12)),
                contentScale = ContentScale.Crop,
            ) {
                when (painter.state) {
                    is coil.compose.AsyncImagePainter.State.Loading -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(Modifier.size(OptimalDimensions.dp28)) }
                    is coil.compose.AsyncImagePainter.State.Error -> MissingImage()
                    else -> SubcomposeAsyncImageContent()
                }
            }
        }
    }
}

@Composable
private fun MissingImage() {
    Column(
        modifier = Modifier.fillMaxSize().padding(OptimalDimensions.dp12),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.BrokenImage, contentDescription = null)
        Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_e7af1b200781), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun AuditRow(entry: MaintenanceAuditEntry) {
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(OptimalDimensions.dp14), verticalArrangement = Arrangement.spacedBy(OptimalDimensions.dp4)) {
            Text(androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_ds_53861d6315a4, entry.action.auditLabel(), entry.table.auditTableLabel()), fontWeight = FontWeight.Bold)
            if (entry.summary.isNotBlank()) {
                Text(entry.summary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(entry.employeeName.ifBlank { androidx.compose.ui.res.stringResource(com.verto.feature.integration.optimal.R.string.optimal_v298_8ee17e0bc8b9) })
                Text(formatDate(entry.createdAt))
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(horizontalArrangement = Arrangement.spacedBy(OptimalDimensions.dp8), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LoadingDetails(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun DetailsMessage(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(OptimalDimensions.dp24), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun formatDate(value: Long): String = DateFormat.getDateTimeInstance().format(Date(value))
private fun formatMoney(value: Double): String = String.format(Locale.getDefault(), "%.2f", value)
private fun String.paymentLabel(): String = when (this) {
    "CASH" -> "كاش"
    "TRANSFER" -> "تحويل"
    "CHECK" -> "شيك"
    else -> this.ifBlank { "غير محدد" }
}
private fun String.auditLabel(): String = when (this) {
    "INSERT" -> "إضافة"
    "UPDATE" -> "تعديل"
    "DELETE" -> "حذف"
    else -> this
}
private fun String.auditTableLabel(): String = when (this) {
    "INVOICE" -> "فاتورة"
    "PAYMENT" -> "دفعة"
    "OPTIMAL" -> "Optimal"
    else -> this
}
