package com.verto.app.feature.reports.application.model

enum class ReportRfmSegment(val label: String, val emoji: String) {
    CHAMPIONS("أبطال", "🏆"),
    LOYAL("عملاء مخلصون", "💎"),
    POTENTIAL_LOYALIST("مخلصون محتملون", "🌱"),
    NEW_CUSTOMERS("عملاء جدد", "🆕"),
    PROMISING("واعدون", "✨"),
    NEEDS_ATTENTION("يحتاجون انتباه", "⚠️"),
    AT_RISK("في خطر", "🚨"),
    CANT_LOSE("لا يمكن خسارتهم", "💔"),
    HIBERNATING("نائمون", "💤"),
    LOST("مفقودون", "❌")
}

data class ReportRfmMetrics(
    val clientId: String,
    val recencyScore: Int = 0,
    val frequencyScore: Int = 0,
    val monetaryScore: Int = 0,
    val daysSinceLastPurchase: Int = 0,
    val totalInvoiceCount: Int = 0,
    val totalSpent: Double = 0.0,
    val avgInvoiceValue: Double = 0.0,
    val segment: ReportRfmSegment = ReportRfmSegment.NEW_CUSTOMERS,
    val totalProfit: Double = 0.0,
    val firstPurchaseAt: Long = 0L,
    val lastPurchaseAt: Long = 0L,
    val customerLifespanDays: Int = 0,
    val calculatedAt: Long = 0L
)

data class RfmSegmentSummary(
    val segment: ReportRfmSegment,
    val count: Int,
    val totalSpent: Double
)

data class ClientClvData(
    val rfm: ReportRfmMetrics,
    val clientName: String,
    val projectedClv: Double
)
