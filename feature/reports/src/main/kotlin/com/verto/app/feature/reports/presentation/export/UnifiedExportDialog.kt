package com.verto.app.feature.reports.presentation.export

import com.verto.app.feature.reports.presentation.ReportsDimensions
import com.verto.app.feature.reports.presentation.ReportsTextScale

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.ui.theme.*
import com.verto.app.ui.components.VertoBottomSheet

// ─────────────────────────────────────────────────────
// خيارات التصدير
// ─────────────────────────────────────────────────────

enum class ExportOption(val icon: String, val title: String, val subtitle: String) {
    PDF_ITEMS("📄", "PDF الأصناف", "قائمة أعلى الأصناف مبيعاً مع الأرباح"),
    PDF_CATEGORIES("📊", "PDF التصنيفات", "تحليل الفئات والهوامش"),
    WHATSAPP("💬", "مشاركة WhatsApp", "ملخص الفترة جاهز للإرسال"),
    SHARE_TEXT("📋", "نسخ / مشاركة نص", "ملخص نصي قابل للمشاركة")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedExportDialog(
    onOptionSelected: (ExportOption) -> Unit,
    onDismiss: () -> Unit
) {
    VertoBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        shape = RoundedCornerShape(topStart = ReportsDimensions.dp20, topEnd = ReportsDimensions.dp20)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = ReportsDimensions.dp20)
                .padding(bottom = ReportsDimensions.dp40),
            verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp12)
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_62d85520f9af),
                color = TextPrimary,
                fontSize = ReportsTextScale.sp18,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = ReportsDimensions.dp4)
            )

            ExportOption.entries.forEach { option ->
                ExportOptionRow(
                    option = option,
                    onClick = {
                        onOptionSelected(option)
                        onDismiss()
                    }
                )
            }

            Spacer(Modifier.height(ReportsDimensions.dp8))
        }
    }
}

@Composable
private fun ExportOptionRow(option: ExportOption, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ReportsDimensions.dp14))
            .background(BgCardAlt)
            .clickable(onClick = onClick)
            .padding(horizontal = ReportsDimensions.dp16, vertical = ReportsDimensions.dp14),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReportsDimensions.dp14)
    ) {
        Box(
            modifier = Modifier
                .size(ReportsDimensions.dp42)
                .clip(RoundedCornerShape(ReportsDimensions.dp12))
                .background(AccentDim),
            contentAlignment = Alignment.Center
        ) {
            Text(option.icon, fontSize = ReportsTextScale.sp20)
        }
        Column(verticalArrangement = Arrangement.spacedBy(ReportsDimensions.dp2)) {
            Text(option.title, color = TextPrimary, fontSize = ReportsTextScale.sp14, fontWeight = FontWeight.SemiBold)
            Text(option.subtitle, color = TextMuted, fontSize = ReportsTextScale.sp12)
        }
    }
}
