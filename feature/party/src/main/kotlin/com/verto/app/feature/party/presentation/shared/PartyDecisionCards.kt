package com.verto.app.feature.party.presentation.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.verto.app.feature.party.application.PartyCurrencyAmount
import com.verto.app.feature.party.application.intelligence.CustomerCreditDecision
import com.verto.app.feature.party.application.intelligence.CustomerDecisionSnapshot
import com.verto.app.feature.party.application.intelligence.CustomerRecommendedAction
import com.verto.app.feature.party.application.intelligence.CustomerRepurchaseState
import com.verto.app.feature.party.application.intelligence.SupplierPerformanceSnapshot
import com.verto.app.feature.party.application.intelligence.SupplierRecommendedAction
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.ErrorColor
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.ui.theme.TextMuted
import com.verto.app.utils.DateUtils

@Composable
internal fun CustomerDecisionCard(
    snapshot: CustomerDecisionSnapshot,
    modifier: Modifier = Modifier,
) {
    val decisionColor = when (snapshot.creditDecision) {
        CustomerCreditDecision.ALLOW_CREDIT -> SuccessColor
        CustomerCreditDecision.CASH_ONLY -> ErrorColor
        CustomerCreditDecision.REQUIRES_APPROVAL -> AccentPrimary
    }
    val decisionTitle = when (snapshot.creditDecision) {
        CustomerCreditDecision.ALLOW_CREDIT -> "البيع الآجل مسموح"
        CustomerCreditDecision.CASH_ONLY -> "نقدي فقط"
        CustomerCreditDecision.REQUIRES_APPROVAL -> "البيع الآجل يحتاج موافقة"
    }
    DecisionSurface(modifier) {
        Text("قرار العميل", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = PartyTextScale.sp15)
        Text(decisionTitle, color = decisionColor, fontWeight = FontWeight.Bold, fontSize = PartyTextScale.sp14)
        Text(customerAction(snapshot.recommendedAction), color = TextSecondary, fontSize = PartyTextScale.sp12)

        if (snapshot.overdueByCurrencyMinor.isNotEmpty()) {
            DecisionMetric(
                "متأخر للتحصيل",
                formatMinorMap(snapshot.overdueByCurrencyMinor),
                ErrorColor,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PartyDimensions.dp8)) {
            DecisionMetric(
                "دورة الشراء",
                snapshot.typicalRepurchaseDays?.let { "$it يوم" } ?: "غير كافية",
                TextSecondary,
                Modifier.weight(1f),
            )
            DecisionMetric(
                "الشراء المتوقع",
                snapshot.predictedNextPurchaseAt?.let { DateUtils.formatDate(it) } ?: "—",
                repurchaseColor(snapshot.repurchaseState),
                Modifier.weight(1f),
            )
        }
        Text(
            repurchaseLabel(snapshot.repurchaseState),
            color = repurchaseColor(snapshot.repurchaseState),
            fontSize = PartyTextScale.sp12,
            fontWeight = FontWeight.Bold,
        )
        if (!snapshot.dataComplete) {
            Text("القرار متحفظ لأن بعض البيانات المالية غير مكتملة", color = ErrorColor, fontSize = PartyTextScale.sp11)
        }
        snapshot.creditReasons.firstOrNull()?.let {
            Text(customerReason(it), color = TextMuted, fontSize = PartyTextScale.sp11)
        }
    }
}

@Composable
internal fun SupplierDecisionCard(
    snapshot: SupplierPerformanceSnapshot,
    modifier: Modifier = Modifier,
) {
    val scoreText = snapshot.scoreBps?.let(::formatBps) ?: "غير كافٍ"
    val actionColor = when (snapshot.recommendedAction) {
        SupplierRecommendedAction.PREFER -> SuccessColor
        SupplierRecommendedAction.ACCEPTABLE -> AccentPrimary
        SupplierRecommendedAction.INSUFFICIENT_HISTORY -> TextMuted
        else -> ErrorColor
    }
    DecisionSurface(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("أداء المورد", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = PartyTextScale.sp15)
            Text(scoreText, color = actionColor, fontWeight = FontWeight.Black, fontSize = PartyTextScale.sp15)
        }
        Text(supplierAction(snapshot.recommendedAction), color = actionColor, fontWeight = FontWeight.Bold, fontSize = PartyTextScale.sp12)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PartyDimensions.dp8)) {
            DecisionMetric("الجودة", snapshot.qualityRateBps?.let(::formatBps) ?: "—", metricColor(snapshot.qualityRateBps, 9_000), Modifier.weight(1f))
            DecisionMetric("Fill Rate", snapshot.acceptedFillRateBps?.let(::formatBps) ?: "—", metricColor(snapshot.acceptedFillRateBps, 8_500), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PartyDimensions.dp8)) {
            DecisionMetric("التسليم في الموعد", snapshot.onTimeDeliveryRateBps?.let(::formatBps) ?: "غير متاح", metricColor(snapshot.onTimeDeliveryRateBps, 8_000), Modifier.weight(1f))
            DecisionMetric("المرتجعات", snapshot.purchaseReturnRateBps?.let(::formatBps) ?: "—", if ((snapshot.purchaseReturnRateBps ?: 0) > 500) ErrorColor else SuccessColor, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PartyDimensions.dp8)) {
            DecisionMetric("Lead Time", snapshot.averageLeadTimeDays?.let { "$it يوم" } ?: "—", TextSecondary, Modifier.weight(1f))
            DecisionMetric("تذبذب المهلة", snapshot.leadTimeVariabilityDays?.let { "$it يوم" } ?: "—", TextSecondary, Modifier.weight(1f))
        }
        snapshot.priceVarianceBps?.let {
            DecisionMetric("انحراف السعر", signedBps(it), if (it > 500) ErrorColor else SuccessColor)
        }
        if (snapshot.matchedInvoiceCostByCurrencyMinor.isNotEmpty()) {
            DecisionMetric("تكلفة الفواتير المطابقة", formatMinorMap(snapshot.matchedInvoiceCostByCurrencyMinor), TextSecondary)
        }
        if (snapshot.promisedDeliveryCoverageBps != null && snapshot.promisedDeliveryCoverageBps < 10_000) {
            Text("تغطية تاريخ التسليم الموعود: ${formatBps(snapshot.promisedDeliveryCoverageBps)}", color = TextMuted, fontSize = PartyTextScale.sp11)
        }
        if (!snapshot.dataComplete) {
            Text("بعض أدلة الشراء غير مكتملة؛ تم استبعاد غير الموثوق من التقييم", color = ErrorColor, fontSize = PartyTextScale.sp11)
        }
    }
}

@Composable
private fun DecisionSurface(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PartyDimensions.dp14),
        color = AccentPrimary.copy(alpha = 0.05f),
        border = BorderStroke(PartyDimensions.dp1, AccentPrimary.copy(alpha = 0.2f)),
    ) {
        Column(
            Modifier.padding(PartyDimensions.dp14),
            verticalArrangement = Arrangement.spacedBy(PartyDimensions.dp8),
            content = content,
        )
    }
}

@Composable
private fun DecisionMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PartyDimensions.dp2)) {
        Text(label, color = TextMuted, fontSize = PartyTextScale.sp11)
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = PartyTextScale.sp12)
    }
}

private fun formatMinorMap(values: Map<String, Long>): String = formatPartyCurrencyAmounts(
    values.entries.sortedBy { it.key }.map { PartyCurrencyAmount(it.key, it.value) }
)

private fun formatBps(value: Int): String = "${value / 100}.${(value % 100).toString().padStart(2, '0')}%"
private fun signedBps(value: Int): String = when {
    value > 0 -> "+${formatBps(value)}"
    value < 0 -> "-${formatBps(kotlin.math.abs(value))}"
    else -> formatBps(0)
}
@Composable
private fun metricColor(value: Int?, healthyAtLeast: Int): Color = when {
    value == null -> TextMuted
    value >= healthyAtLeast -> SuccessColor
    else -> ErrorColor
}

private fun customerAction(action: CustomerRecommendedAction): String = when (action) {
    CustomerRecommendedAction.NONE -> "لا يوجد إجراء عاجل"
    CustomerRecommendedAction.COLLECT_OVERDUE -> "الإجراء الآن: تحصيل الدين المتأخر"
    CustomerRecommendedAction.REQUIRE_CASH -> "الإجراء الآن: البيع نقداً فقط"
    CustomerRecommendedAction.REVIEW_CREDIT -> "الإجراء الآن: مراجعة الائتمان مع المدير"
    CustomerRecommendedAction.FOLLOW_UP_REPURCHASE -> "الإجراء الآن: متابعة العميل لاسترجاع الشراء"
}

private fun customerReason(reason: String): String = when (reason) {
    "GOOD_SETTLEMENT_HISTORY" -> "سجل السداد الحالي يطابق السياسة"
    "CURRENT_OVERDUE_BALANCE" -> "يوجد رصيد آجل متأخر"
    "SEVERELY_OVERDUE_NOW" -> "يوجد تأخير حالي شديد"
    "HIGH_LATE_PAYMENT_RATIO" -> "نسبة التأخر التاريخية مرتفعة"
    "INSUFFICIENT_SETTLED_CREDIT_HISTORY" -> "تاريخ الفواتير الآجلة المسددة غير كافٍ"
    "NO_CREDIT_HISTORY" -> "لا يوجد تاريخ ائتماني كافٍ"
    "MULTI_CURRENCY_REQUIRES_EXPLICIT_POLICY" -> "السجل متعدد العملات ويحتاج سياسة واضحة"
    "INCOMPLETE_FINANCIAL_HISTORY" -> "السجل المالي غير مكتمل"
    "LATE_RATIO_ABOVE_POLICY" -> "نسبة التأخر أعلى من السياسة"
    "AVERAGE_LATENESS_ABOVE_POLICY" -> "متوسط التأخر أعلى من السياسة"
    else -> reason
}

private fun repurchaseLabel(state: CustomerRepurchaseState): String = when (state) {
    CustomerRepurchaseState.INSUFFICIENT_HISTORY -> "لا توجد دورة شراء كافية للتوقع"
    CustomerRepurchaseState.ACTIVE -> "العميل ضمن دورة الشراء الطبيعية"
    CustomerRepurchaseState.DUE_SOON -> "موعد الشراء المتوقع يقترب"
    CustomerRepurchaseState.DUE -> "حان موعد متابعة العميل"
    CustomerRepurchaseState.OVERDUE -> "خطر فقد العميل مرتفع: تجاوز دورة الشراء"
}

@Composable
private fun repurchaseColor(state: CustomerRepurchaseState): Color = when (state) {
    CustomerRepurchaseState.ACTIVE -> SuccessColor
    CustomerRepurchaseState.DUE_SOON -> AccentPrimary
    CustomerRepurchaseState.DUE, CustomerRepurchaseState.OVERDUE -> ErrorColor
    CustomerRepurchaseState.INSUFFICIENT_HISTORY -> TextMuted
}

private fun supplierAction(action: SupplierRecommendedAction): String = when (action) {
    SupplierRecommendedAction.INSUFFICIENT_HISTORY -> "التاريخ غير كافٍ لتوصية موثوقة"
    SupplierRecommendedAction.PREFER -> "مورد مفضل وفق التاريخ الفعلي"
    SupplierRecommendedAction.ACCEPTABLE -> "الأداء مقبول"
    SupplierRecommendedAction.REVIEW_QUALITY -> "راجع الجودة والمرتجعات"
    SupplierRecommendedAction.REVIEW_FILL -> "راجع الالتزام بالكميات المطلوبة"
    SupplierRecommendedAction.REVIEW_DELIVERY -> "راجع الالتزام بمواعيد التسليم"
    SupplierRecommendedAction.REVIEW_PRICE -> "راجع فروقات السعر"
    SupplierRecommendedAction.REVIEW_PERFORMANCE -> "الأدلة الحالية تحتاج مراجعة"
}
