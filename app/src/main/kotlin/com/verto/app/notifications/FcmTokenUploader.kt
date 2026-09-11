package com.verto.app.notifications

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.verto.app.core.concurrency.AppCoroutineScope
import com.verto.app.data.remote.PushTokenRepository
import kotlinx.coroutines.launch

/** Fire-and-forget رفع توكن FCM الحالي للسيرفر. يُستدعى بعد إكمال تسجيل الدخول/التسجيل. */
object FcmTokenUploader {

    fun trigger(context: Context, repository: PushTokenRepository) {
        val appScope = context.applicationContext as AppCoroutineScope
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FcmTokenUploader", "FCM token fetch failed")
                return@addOnCompleteListener
            }
            val token = task.result ?: return@addOnCompleteListener
            appScope.launch {
                repository.upsertCurrentUserToken(token)
                    .onSuccess {
                        FcmTokenStore.setLastUploaded(context, token)
                        FcmTokenStore.setPending(context, null)
                    }
                    .onFailure {
                        FcmTokenStore.setPending(context, token)
                        Log.w("FcmTokenUploader", "FCM token upload failed: ${it::class.java.simpleName}")
                    }
            }
        }
    }
}
