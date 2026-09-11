package com.verto.app.feature.shipment.domain.model

import java.math.BigDecimal

enum class LogisticsPlanKind { PLANNED, UNPLANNED }

enum class LogisticsDelayLevel { NORMAL, LATE, VERY_LATE }

enum class LogisticsPackageChangeReason {
    REPACKAGED,
    CONSOLIDATED,
    SPLIT,
    DAMAGE,
    CORRECTION,
    OTHER,
}

enum class LogisticsCostPaymentState {
    UNPAID,
    POSTING,
    PAID,
    ADJUSTING,
    REVERSED,
}

/** Offline canonical logistics location. countryCode is the internal normalized country key; legacy rows may contain ISO codes. */
data class LogisticsLocation(
    val countryCode: String,
    val countryNameSnapshot: String,
    val city: String,
    val placeName: String,
)

data class LogisticsCargoSnapshot(
    val packageCount: Int,
    val weightKg: BigDecimal? = null,
)
