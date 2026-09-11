package com.verto.app.feature.dashboard.application.pendingaction

import java.util.Calendar
import java.util.TimeZone

/** Explicit local snooze targets; persistence stores the resulting absolute epoch time. */
data class PendingActionSnoozeOption(
    val label: String,
    val untilEpochMillis: Long,
)

fun pendingActionSnoozeOptions(
    nowEpochMillis: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
): List<PendingActionSnoozeOption> {
    require(nowEpochMillis >= 0L) { "nowEpochMillis must not be negative" }
    val tomorrowMorning = Calendar.getInstance(timeZone).apply {
        timeInMillis = nowEpochMillis
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return listOf(
        PendingActionSnoozeOption("بعد ساعة", nowEpochMillis + HOUR_MS),
        PendingActionSnoozeOption("غدًا صباحًا", tomorrowMorning),
        PendingActionSnoozeOption("بعد أسبوع", nowEpochMillis + WEEK_MS),
    )
}

private const val HOUR_MS = 60L * 60L * 1_000L
private const val WEEK_MS = 7L * 24L * HOUR_MS
