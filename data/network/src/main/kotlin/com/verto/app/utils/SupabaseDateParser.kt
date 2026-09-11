package com.verto.app.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object SupabaseDateParser {
    private fun normalize(iso: String): String = iso.trim()
        .replace(" ", "T")
        .replace(Regex("\\.\\d+"), "")
        .replace(Regex("\\+00:?00$"), "Z")
        .replace(Regex("\\+00$"), "Z")
        .replace(Regex("[+-]\\d{2}:?\\d{2}$"), "Z")

    private fun utcFormat() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        .also { it.timeZone = TimeZone.getTimeZone("UTC") }

    fun parse(iso: String?): Long {
        if (iso.isNullOrBlank()) return 0L
        return runCatching {
            utcFormat().parse(normalize(iso))?.time ?: 0L
        }.getOrElse { e ->
            android.util.Log.e("SupabaseDateParser", "Date parse failed: ${e::class.java.simpleName}")
            0L
        }
    }

    fun parseOrNow(iso: String?): Long {
        if (iso.isNullOrBlank()) return System.currentTimeMillis()
        return runCatching {
            utcFormat().parse(normalize(iso))?.time ?: System.currentTimeMillis()
        }.getOrDefault(System.currentTimeMillis())
    }

    fun format(epochMs: Long): String = utcFormat().format(Date(epochMs))
}
