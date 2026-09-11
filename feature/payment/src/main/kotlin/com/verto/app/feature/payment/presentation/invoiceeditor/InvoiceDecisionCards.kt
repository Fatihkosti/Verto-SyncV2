package com.verto.app.feature.payment.presentation.invoiceeditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.verto.app.feature.payment.application.model.PaymentCreditDecision
import com.verto.app.feature.payment.application.model.PaymentCustomerDecision
import com.verto.app.feature.payment.application.model.PaymentSupplierRecommendation
import com.verto.app.feature.payment.presentation.PaymentDimensions
import com.verto.app.feature.payment.presentation.PaymentTextScale
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.ErrorColor
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary

@Composable
internal fun CustomerCreditDecisionBanner(
    decision: PaymentCustomerDecision,
    isAdmin: Boolean,
    modifier: Modifier = Modifier,
) {
    val (title, color) = when (decision.decision) {
        PaymentCreditDecision.ALLOW_CREDIT -> "البيع الآجل مسموح" to SuccessColor
        PaymentCreditDecision.CASH_ONLY -> "نقدي فقط" to ErrorColor
        PaymentCreditDecision.REQUIRES_APPROVAL -> "يتطلب موافقة مدير" to AccentPrimary
    }
    val detail = when (decision.decision) {
        PaymentCreditDecision.ALLOW_CREDIT -> "سجل السداد يطابق سياسة الائتمان الحالية"
        PaymentCreditDecision.CASH_ONLY -> creditReasonText(decision.reasons.firstOrNull())
        PaymentCreditDecision.REQUIRES_APPROVAL -> if (isAdmin) {
            "أنت مدير؛ يمكن متابعة الحفظ مع بقاء القرار مسجلاً كتجاوز إداري"
        } else {
            creditReasonText(decision.reasons.firstOrNull())
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PaymentDimensions.dp12),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(PaymentDimensions.dp1, color.copy(alpha = 0.35f)),
    ) {
        Column(
            Modifier.padding(PaymentDimensions.dp10),
            verticalArrangement = Arrangement.spacedBy(PaymentDimensions.dp4),
        ) {
            Text(title, color = color, fontWeight = FontWeight.Bold, fontSize = PaymentTextScale.sp13)
            Text(detail, color = TextSecondary, fontSize = PaymentTextScale.sp12)
            if (decision.currentlyOverdueInvoiceCount > 0) {
                Text(
                    "متأخر الآن: ${decision.currentlyOverdueInvoiceCount} فاتورة • حتى ${decision.maxCurrentDaysOverdue} يوم",
                    color = TextPrimary,
                    fontSize = PaymentTextScale.sp12,
                )
            }
        }
    }
}

@Composable
internal fun SupplierRecommendationBanner(
    recommendation: PaymentSupplierRecommendation,
    bestSupplierName: String?,
    selectedSupplierId: String,
    modifier: Modifier = Modifier,
) {
    val bestId = recommendation.bestSupplierId ?: return
    val score = recommendation.bestSupplierScoreBps?.let { "${it / 100}.${(it % 100).toString().padStart(2, '0')}%" }
    val selectedMatches = selectedSupplierId.isNotBlank() && selectedSupplierId == bestId
    val color = if (selectedMatches) SuccessColor else AccentPrimary
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PaymentDimensions.dp12),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(PaymentDimensions.dp1, color.copy(alpha = 0.35f)),
    ) {
        Column(
            Modifier.padding(PaymentDimensions.dp10),
            verticalArrangement = Arrangement.spacedBy(PaymentDimensions.dp4),
        ) {
            Text(
                if (selectedMatches) "المورد المختار هو الأفضل تاريخياً لهذا الصنف" else "المورد الموصى لهذا الصنف",
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = PaymentTextScale.sp13,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(PaymentDimensions.dp8)) {
                Text(bestSupplierName ?: bestId, color = TextPrimary, fontSize = PaymentTextScale.sp12)
                if (score != null) Text("التقييم $score", color = TextSecondary, fontSize = PaymentTextScale.sp12)
                recommendation.bestSupplierOrderCount?.let {
                    Text("$it طلبات", color = TextSecondary, fontSize = PaymentTextScale.sp12)
                }
            }
        }
    }
}

private fun creditReasonText(reason: String?): String = when (reason) {
    "CURRENT_OVERDUE_BALANCE" -> "يوجد رصيد آجل متأخر يحتاج مراجعة"
    "SEVERELY_OVERDUE_NOW" -> "يوجد تأخير حالي شديد في السداد"
    "HIGH_LATE_PAYMENT_RATIO" -> "نسبة التأخر التاريخية مرتفعة"
    "INSUFFICIENT_SETTLED_CREDIT_HISTORY" -> "سجل الفواتير الآجلة المسددة غير كافٍ للقرار التلقائي"
    "NO_CREDIT_HISTORY" -> "لا يوجد تاريخ ائتماني كافٍ"
    "MULTI_CURRENCY_REQUIRES_EXPLICIT_POLICY" -> "السجل متعدد العملات؛ يلزم قرار إداري"
    "INCOMPLETE_FINANCIAL_HISTORY" -> "بيانات السجل المالي غير مكتملة؛ تم الإغلاق الآمن"
    "LATE_RATIO_ABOVE_POLICY" -> "نسبة التأخر أعلى من سياسة الائتمان"
    "AVERAGE_LATENESS_ABOVE_POLICY" -> "متوسط أيام التأخر أعلى من السياسة"
    else -> "القرار مبني على سجل السداد الفعلي للعميل"
}
