package com.verto.app.utils

import java.text.Normalizer
import java.util.Locale

/**
 * Produces stable local-search keys without changing the original displayed value.
 * Arabic/Persian digits are converted to ASCII and Arabic orthographic variants are folded.
 */
object SearchTextNormalizer {
    data class QueryKeys(
        val text: String,
        val identifier: String,
        val phone: String
    )

    fun text(value: String): String {
        if (value.isBlank()) return ""
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFKD)
            .lowercase(Locale.ROOT)
        val out = StringBuilder(decomposed.length)
        var pendingSpace = false

        decomposed.forEach { source ->
            if (source.isCombiningMark() || source == '\u0640') return@forEach
            val char = source.foldArabicVariant().toAsciiDigit()
            when {
                char.isLetterOrDigit() -> {
                    if (pendingSpace && out.isNotEmpty()) out.append(' ')
                    out.append(char)
                    pendingSpace = false
                }
                out.isNotEmpty() -> pendingSpace = true
            }
        }
        return out.toString()
    }

    fun identifier(value: String): String =
        text(value).filter(Char::isLetterOrDigit)

    fun phone(value: String): String = buildString(value.length) {
        value.forEach { char ->
            val digit = char.toAsciiDigit()
            if (digit in '0'..'9') append(digit)
        }
    }

    /** Prefix search is intentionally delayed until at least two normalized characters. */
    fun query(raw: String): QueryKeys? {
        val keys = QueryKeys(
            text = text(raw),
            identifier = identifier(raw),
            phone = phone(raw)
        )
        return keys.takeIf {
            it.text.length >= MIN_PREFIX_LENGTH ||
                it.identifier.length >= MIN_PREFIX_LENGTH ||
                it.phone.length >= MIN_PREFIX_LENGTH
        }
    }

    private fun Char.foldArabicVariant(): Char = when (this) {
        '\u0623', '\u0625', '\u0622', '\u0671' -> '\u0627'
        '\u0624' -> '\u0648'
        '\u0626', '\u0649' -> '\u064a'
        '\u0629' -> '\u0647'
        else -> this
    }

    private fun Char.toAsciiDigit(): Char = when (this) {
        '\u0660', '\u06f0' -> '0'
        '\u0661', '\u06f1' -> '1'
        '\u0662', '\u06f2' -> '2'
        '\u0663', '\u06f3' -> '3'
        '\u0664', '\u06f4' -> '4'
        '\u0665', '\u06f5' -> '5'
        '\u0666', '\u06f6' -> '6'
        '\u0667', '\u06f7' -> '7'
        '\u0668', '\u06f8' -> '8'
        '\u0669', '\u06f9' -> '9'
        else -> this
    }

    private fun Char.isCombiningMark(): Boolean = when (Character.getType(this)) {
        Character.NON_SPACING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt() -> true
        else -> false
    }

    const val MIN_PREFIX_LENGTH: Int = 2
}
