package com.verto.app.utils

import java.text.Normalizer
import java.util.Locale

/** The existing invoice-editor normalization, shared with strict remote validation (B09). */
fun normalizeSupplierInvoiceReference(value: String?): String? = value
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?.let { Normalizer.normalize(it, Normalizer.Form.NFKC) }
    ?.lowercase(Locale.ROOT)
    ?.filter(Char::isLetterOrDigit)
    ?.takeIf { it.isNotEmpty() }
