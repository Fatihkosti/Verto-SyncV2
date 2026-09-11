package com.verto.app.utils

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {
    private val arabicMonths = arrayOf("يناير","فبراير","مارس","أبريل","مايو","يونيو","يوليو","أغسطس","سبتمبر","أكتوبر","نوفمبر","ديسمبر")

    fun formatDate(timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return "${cal.get(Calendar.DAY_OF_MONTH)} ${arabicMonths[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)}"
    }

    private val enDateFormat = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH)
    fun formatDateEn(timestamp: Long): String = enDateFormat.format(Date(timestamp))

    fun formatDateTime(timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val hour = cal.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
        val min  = cal.get(Calendar.MINUTE).toString().padStart(2, '0')
        return "${cal.get(Calendar.DAY_OF_MONTH)} ${arabicMonths[cal.get(Calendar.MONTH)]} — $hour:$min"
    }

    fun formatTimeAgo(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val minutes = diff / 60_000
        val hours   = diff / 3_600_000
        val days    = diff / 86_400_000
        return when {
            minutes < 1    -> "الآن"
            minutes < 60   -> "منذ $minutes دقيقة"
            hours   < 24   -> "منذ $hours ساعة"
            days    < 7    -> "منذ $days أيام"
            else           -> formatDate(timestamp)
        }
    }

    // وصف الوقت المتبقي أو التأخر
    fun dueDateLabel(dueDate: Long): Triple<String, Boolean, Boolean> {
        val now  = System.currentTimeMillis()
        val diff = dueDate - now
        val absDays  = Math.abs(diff) / 86_400_000
        val absHours = Math.abs(diff) / 3_600_000
        val isOverdue = diff < 0
        val isUrgent  = diff in 0..(86_400_000 * 3)

        val label = when {
            isOverdue && absDays > 0  -> "تأخر $absDays يوم"
            isOverdue                 -> "تأخر $absHours ساعة"
            absDays == 0L             -> "اليوم"
            absDays == 1L             -> "غداً"
            absDays < 7              -> "باقي $absDays أيام"
            else                      -> "باقي $absDays يوم"
        }
        return Triple(label, isOverdue, isUrgent)
    }

    fun rangeForPeriod(period: ReportPeriod, now: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val to  = now
        return when (period) {
            ReportPeriod.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, to)
            }
            ReportPeriod.WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, to)
            }
            ReportPeriod.MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, to)
            }
            ReportPeriod.THREE_MONTHS -> {
                cal.add(Calendar.MONTH, -3); Pair(cal.timeInMillis, to)
            }
            ReportPeriod.SIX_MONTHS -> {
                cal.add(Calendar.MONTH, -6); Pair(cal.timeInMillis, to)
            }
            ReportPeriod.YEAR -> {
                cal.add(Calendar.YEAR, -1); Pair(cal.timeInMillis, to)
            }
            ReportPeriod.CUSTOM -> Pair(0L, to)
        }
    }
}

enum class ReportPeriod(val label: String) {
    TODAY("اليوم"),
    WEEK("هذا الأسبوع"),
    MONTH("هذا الشهر"),
    THREE_MONTHS("٣ أشهر"),
    SIX_MONTHS("٦ أشهر"),
    YEAR("سنة"),
    CUSTOM("مخصص")
}