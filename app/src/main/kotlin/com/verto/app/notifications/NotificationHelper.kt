package com.verto.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.verto.app.R

/** Android presentation only. Business notification creation belongs to the server. */
object NotificationHelper {
    const val CHANNEL_DEBTS = "verto_debts"
    const val CHANNEL_PURCHASES = "verto_purchases"
    const val CHANNEL_PAYMENTS = "verto_payments"
    const val CHANNEL_INCOMPLETE = "verto_incomplete"
    const val CHANNEL_CHAT = "verto_chat"
    const val CHANNEL_COMMISSION = "verto_commission"
    const val CHANNEL_MANAGEMENT = "verto_management"
    const val CHANNEL_GENERAL = "verto_general_notifications_v2"
    const val EXTRA_NAV_ROUTE = "NAV_ROUTE"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        listOf(
            channel(CHANNEL_DEBTS, "تنبيهات الديون", NotificationManager.IMPORTANCE_HIGH, "تنبيهات مواعيد الاستحقاق والتأخير"),
            channel(CHANNEL_PURCHASES, "فواتير المشتريات", NotificationManager.IMPORTANCE_DEFAULT, "أحداث المشتريات والاستلام"),
            channel(CHANNEL_PAYMENTS, "تسجيل المدفوعات", NotificationManager.IMPORTANCE_DEFAULT, "إشعارات الدفعات والسداد"),
            channel(CHANNEL_INCOMPLETE, "بيانات ناقصة", NotificationManager.IMPORTANCE_DEFAULT, "تنبيهات استكمال البيانات"),
            channel(CHANNEL_CHAT, "رسائل الدردشة", NotificationManager.IMPORTANCE_HIGH, "رسائل جديدة"),
            channel(CHANNEL_COMMISSION, "العمولات", NotificationManager.IMPORTANCE_HIGH, "عمولات وطلبات سحب تحتاج إجراء"),
            channel(CHANNEL_MANAGEMENT, "إجراءات الإدارة", NotificationManager.IMPORTANCE_HIGH, "طلبات وأحداث تحتاج إجراء إداري"),
            channel(CHANNEL_GENERAL, "إشعارات Verto", NotificationManager.IMPORTANCE_DEFAULT, "إشعارات تشغيلية عامة"),
        ).forEach(manager::createNotificationChannel)
    }

    private fun channel(id: String, name: String, importance: Int, description: String) =
        NotificationChannel(id, name, importance).apply {
            this.description = description
            enableVibration(importance >= NotificationManager.IMPORTANCE_DEFAULT)
            enableLights(importance >= NotificationManager.IMPORTANCE_HIGH)
        }

    fun showRemoteNotification(
        context: Context,
        notificationId: String?,
        type: String,
        title: String,
        body: String,
        navRoute: String,
    ) {
        val channelId = channelFor(type)
        val stableId = notificationId?.takeIf { it.isNotBlank() }?.hashCode()
            ?: "$type|$title|$body".hashCode()
        val intent = Intent(context, NotificationRouterActivity::class.java).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_NAV_ROUTE, navRoute)
        }
        val pending = PendingIntent.getActivity(
            context,
            stableId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val largeIcon = BitmapFactory.decodeResource(context.resources, R.drawable.ic_notification_large)
        val compatPriority = if (channelId in HIGH_PRIORITY_CHANNELS) {
            NotificationCompat.PRIORITY_HIGH
        } else {
            NotificationCompat.PRIORITY_DEFAULT
        }

        val publicVersion = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_verto)
            .setLargeIcon(largeIcon)
            .setContentTitle("إشعار Verto")
            .setContentText("افتح التطبيق لعرض التفاصيل")
            .build()

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_verto)
            .setLargeIcon(largeIcon)
            .setColor(ContextCompat.getColor(context, R.color.verto_notification_color))
            .setContentTitle(title.ifBlank { "إشعار Verto" })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(compatPriority)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(stableId, notification)
    }

    private fun channelFor(type: String): String = when (type.trim().uppercase()) {
        "PAYMENT_DUE_REMINDER", "PAYMENT_OVERDUE" -> CHANNEL_DEBTS
        "PAYMENT_RECORDED" -> CHANNEL_PAYMENTS
        "PURCHASE_INVOICE_CREATED", "GOODS_RECEIVED" -> CHANNEL_PURCHASES
        "NEW_CHAT_MESSAGE", "CHAT" -> CHANNEL_CHAT
        "ADMIN_COMMISSION_NEEDED", "WITHDRAWAL_REQUESTED" -> CHANNEL_COMMISSION
        "AUTODRIVE_JOIN_REQUEST", "MARKETER_REGISTERED" -> CHANNEL_MANAGEMENT
        "PROFILE_INCOMPLETE" -> CHANNEL_INCOMPLETE
        else -> CHANNEL_GENERAL
    }

    private val HIGH_PRIORITY_CHANNELS = setOf(
        CHANNEL_DEBTS,
        CHANNEL_CHAT,
        CHANNEL_COMMISSION,
        CHANNEL_MANAGEMENT,
    )
}
