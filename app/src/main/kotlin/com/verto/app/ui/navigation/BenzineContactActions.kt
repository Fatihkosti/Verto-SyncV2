package com.verto.app.ui.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri

internal object BenzineContactActions {
    fun dial(context: Context, phone: String) {
        if (phone.isBlank()) return
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:${phone.trim()}")
        }
        runCatching { context.startActivity(intent) }
    }

    fun openWhatsApp(context: Context, phone: String) {
        val normalized = phone
            .replace(" ", "")
            .replace("+", "")
            .replace("-", "")
        if (normalized.isBlank()) return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://wa.me/$normalized")
        }
        runCatching { context.startActivity(intent) }
    }
}
