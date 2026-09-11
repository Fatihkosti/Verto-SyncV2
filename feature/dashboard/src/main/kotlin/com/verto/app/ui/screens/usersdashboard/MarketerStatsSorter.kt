package com.verto.app.ui.screens.usersdashboard

import com.verto.app.feature.dashboard.application.MarketerStatsItem
import java.time.OffsetDateTime

/** معايير ترتيب لوحة المستخدمين. */
enum class MarketerSort(val label: String) {
    PURCHASES("الأكثر شراءً"),
    ACTIVITY("الأكثر نشاطاً"),
    STREAK("الأطول استمراراً"),
    RECENT("الأحدث ظهوراً")
}

/**
 * منطق الترتيب والفلترة للوحة المستخدمين — مفصول كـ object نقي لاختباره بسهولة.
 * كل العمليات بلا حالة (stateless) وتعتمد على `nowMillis` المحقون لتفادي الاعتماد
 * على الساعة الحقيقية في الاختبارات.
 */
object MarketerStatsSorter {

    const val ONLINE_WINDOW_MS = 5 * 60 * 1000L            // "متصل الآن" = آخر 5 دقائق
    const val INACTIVE_THRESHOLD_MS = 7L * 24 * 60 * 60 * 1000L // تنبيه الانقطاع = 7 أيام فأكثر

    /** يحوّل طابعاً زمنياً ISO من السيرفر إلى ميلي ثانية، أو null عند الفشل/الغياب. */
    fun parseMillis(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }
            .getOrElse {
                runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull()
            }
    }

    fun isOnline(stat: MarketerStatsItem, nowMillis: Long): Boolean {
        val seen = parseMillis(stat.lastSeenAt) ?: return false
        return nowMillis - seen <= ONLINE_WINDOW_MS
    }

    /** المنقطعون: لم يُروا منذ [INACTIVE_THRESHOLD_MS] أو لم يظهروا قط (مع وجود حساب فعّال). */
    fun isInactive(stat: MarketerStatsItem, nowMillis: Long): Boolean {
        val seen = parseMillis(stat.lastSeenAt) ?: return true
        return nowMillis - seen >= INACTIVE_THRESHOLD_MS
    }

    fun sort(list: List<MarketerStatsItem>, sort: MarketerSort): List<MarketerStatsItem> = when (sort) {
        MarketerSort.PURCHASES -> list.sortedByDescending { it.purchasesTotal }
        MarketerSort.ACTIVITY  -> list.sortedByDescending { it.activeWeeks }
        MarketerSort.STREAK    -> list.sortedByDescending { it.streakWeeks }
        MarketerSort.RECENT    -> list.sortedByDescending { parseMillis(it.lastSeenAt) ?: Long.MIN_VALUE }
    }

    fun inactiveList(list: List<MarketerStatsItem>, nowMillis: Long): List<MarketerStatsItem> =
        list.filter { isInactive(it, nowMillis) }
            .sortedBy { parseMillis(it.lastSeenAt) ?: Long.MIN_VALUE }
}

