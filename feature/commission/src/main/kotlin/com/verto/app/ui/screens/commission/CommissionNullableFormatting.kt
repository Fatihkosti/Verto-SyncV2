package com.verto.app.ui.screens.commission

/** Nullable balance formatting: absence is unknown data, never a synthetic zero. */
internal fun Number?.eng(): String =
    this?.toDouble()?.let(numFmt::format) ?: "غير متاح"
