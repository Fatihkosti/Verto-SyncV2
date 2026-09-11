package com.verto.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Shared WhatsApp launcher and money/phone formatting. Contains no feature or data models. */
object WhatsAppUtils {
    private const val WHATSAPP_PKG = "com.whatsapp"
    private const val WHATSAPP_BUSINESS_PKG = "com.whatsapp.w4b"
    private const val COUNTRY_CODE = "249"

    fun formatPhone(raw: String): String {
        val digits = raw.replace(Regex("[^0-9]"), "")
        return when {
            digits.startsWith("0") -> COUNTRY_CODE + digits.substring(1)
            digits.startsWith("249") -> digits
            else -> COUNTRY_CODE + digits
        }
    }

    fun isWhatsAppInstalled(ctx: Context): Boolean = isPackageInstalled(ctx, WHATSAPP_PKG)
    fun isWhatsAppBusinessInstalled(ctx: Context): Boolean = isPackageInstalled(ctx, WHATSAPP_BUSINESS_PKG)

    private fun isPackageInstalled(ctx: Context, pkg: String): Boolean =
        runCatching { ctx.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    fun openWhatsApp(
        context: Context,
        phone: String,
        message: String,
        useWhatsAppBusiness: Boolean = false
    ) {
        val pkg = if (useWhatsAppBusiness) WHATSAPP_BUSINESS_PKG else WHATSAPP_PKG
        val normalizedPhone = formatPhone(phone)
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$normalizedPhone&text=${Uri.encode(message)}")
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(pkg)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }.onFailure {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
            }.onFailure {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(Intent.createChooser(share, "مشاركة عبر").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }
        }
    }

    fun formatAmount(amount: Double): String =
        if (amount == amount.toLong().toDouble()) amount.toLong().toString() else "%.2f".format(amount)
}
