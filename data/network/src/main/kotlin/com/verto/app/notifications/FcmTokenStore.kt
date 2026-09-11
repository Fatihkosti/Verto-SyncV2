package com.verto.app.notifications

import android.content.Context
import java.util.UUID

object FcmTokenStore {
    private const val PREFS = "verto_fcm_prefs"
    private const val KEY_LAST_UPLOADED = "last_uploaded_token"
    private const val KEY_PENDING       = "pending_token"
    private const val KEY_DEVICE_ID     = "device_id"

    fun deviceId(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val value = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, value).commit()
        return prefs.getString(KEY_DEVICE_ID, value) ?: value
    }

    fun lastUploaded(context: Context): String? =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_UPLOADED, null)

    fun setLastUploaded(context: Context, token: String?) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST_UPLOADED, token).apply()
    }

    fun pending(context: Context): String? =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PENDING, null)

    fun setPending(context: Context, token: String?) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PENDING, token).apply()
    }

    fun clearSession(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_UPLOADED)
            .remove(KEY_PENDING)
            .apply()
    }
}
