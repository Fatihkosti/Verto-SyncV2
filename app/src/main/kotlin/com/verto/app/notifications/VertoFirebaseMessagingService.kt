package com.verto.app.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.verto.app.data.remote.PushTokenRepository
import com.verto.app.data.repository.NotificationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class VertoFirebaseMessagingService : FirebaseMessagingService() {
    @Inject lateinit var pushTokenRepository: PushTokenRepository
    @Inject lateinit var notificationRepository: NotificationRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FcmTokenStore.setPending(applicationContext, token)
        scope.launch {
            pushTokenRepository.upsertCurrentUserToken(token)
                .onSuccess {
                    FcmTokenStore.setLastUploaded(applicationContext, token)
                    FcmTokenStore.setPending(applicationContext, null)
                }
                .onFailure { Log.w(TAG, "تعذّر رفع توكن FCM وسيُعاد لاحقاً") }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val type = (data["type"] ?: "GENERIC_NOTIFICATION").trim()
        if (type in AUTODRIVE_USER_ONLY_TYPES) return

        val title = message.notification?.title ?: data["title"] ?: "إشعار Verto"
        val body = message.notification?.body ?: data["body"] ?: ""
        val relatedId = data["related_entity_id"]
        val relatedType = data["related_entity_type"]
        val route = NotificationRoutePolicy.resolve(
            type = type,
            requestedRoute = data["navigation_route"] ?: data["nav_route"] ?: data["route"] ?: data["navigationRoute"],
            relatedEntityId = relatedId,
            relatedEntityType = relatedType,
            data = data,
        )
        val id = data["notification_id"] ?: data["message_id"]

        NotificationHelper.showRemoteNotification(
            context = applicationContext,
            notificationId = id,
            type = type,
            title = title,
            body = body,
            navRoute = route,
        )

        // FCM is a wake-up/display signal. Room is refreshed from the canonical server record.
        if (!type.equals("chat", ignoreCase = true) && type != "NEW_CHAT_MESSAGE") {
            scope.launch {
                notificationRepository.syncCurrentUserNotifications()
                    .onFailure { Log.w(TAG, "تعذّر تحديث مركز الإشعارات بعد Push") }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.coroutineContext[Job]?.cancel()
    }

    private companion object {
        const val TAG = "VertoFcmService"
        val AUTODRIVE_USER_ONLY_TYPES = setOf(
            "NEW_COMMISSION", "COMMISSION_WITHDRAWABLE", "COMMISSION_PAID", "BALANCE_CREDITED",
            "NEW_INVOICE", "WEEK_ENDING_SOON", "INACTIVITY", "WEEKLY_GOAL_ACHIEVED",
            "WITHDRAWAL_APPROVED", "WITHDRAWAL_REJECTED", "WITHDRAWAL_COMPLETED",
            "WELCOME", "ADMIN_REMINDER",
        )
    }
}
