package com.verto.app.feature.reports.bridge

import com.verto.app.data.local.entity.RfmCacheEntity
import com.verto.app.feature.reports.application.model.ReportRfmMetrics
import com.verto.app.feature.reports.application.model.ReportRfmSegment

internal fun RfmCacheEntity.toReportModel() = ReportRfmMetrics(
    clientId = clientId, recencyScore = recencyScore, frequencyScore = frequencyScore, monetaryScore = monetaryScore,
    daysSinceLastPurchase = daysSinceLastPurchase, totalInvoiceCount = totalInvoiceCount, totalSpent = totalSpent,
    avgInvoiceValue = avgInvoiceValue, segment = ReportRfmSegment.valueOf(segment.name), totalProfit = totalProfit,
    firstPurchaseAt = firstPurchaseAt, lastPurchaseAt = lastPurchaseAt, customerLifespanDays = customerLifespanDays,
    calculatedAt = calculatedAt,
)
