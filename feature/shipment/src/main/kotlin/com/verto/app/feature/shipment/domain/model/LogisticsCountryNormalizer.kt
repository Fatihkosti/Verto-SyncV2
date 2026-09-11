package com.verto.app.feature.shipment.domain.model

import java.text.Normalizer
import java.util.Locale

/**
 * Canonicalizes user-entered country names without exposing a technical country code to the UI.
 * The returned key is stable for spacing/case/diacritic variants and is suitable for reporting joins.
 */
object LogisticsCountryNormalizer {
    private val whitespace = Regex("\\s+")
    private val combiningMarks = Regex("\\p{M}+")

    fun displayName(value: String): String = value
        .replace('\u0640'.toString(), "")
        .trim()
        .replace(whitespace, " ")

    fun key(value: String): String {
        val display = displayName(value)
        if (display.isBlank()) return ""
        val decomposed = Normalizer.normalize(display, Normalizer.Form.NFKD)
            .replace(combiningMarks, "")
            .lowercase(Locale.ROOT)
            .replace(whitespace, " ")
            .trim()
        return "name:$decomposed"
    }

    fun searchKey(value: String): String = key(value).removePrefix("name:")

    fun isInternalKey(value: String): Boolean {
        val candidate = value.trim()
        if (candidate.matches(Regex("[A-Z]{2}"))) return true // legacy ISO rows
        if (!candidate.startsWith("name:")) return false
        return key(candidate.removePrefix("name:")) == candidate
    }
}
