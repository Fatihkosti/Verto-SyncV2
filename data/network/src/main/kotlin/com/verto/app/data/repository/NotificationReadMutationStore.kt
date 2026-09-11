package com.verto.app.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Durable, tenant/user-scoped pending read receipts.
 *
 * A read action is persisted before Room is updated. The server RPC is idempotent, so replay after
 * process death or connectivity loss is safe. This intentionally stores only notification UUIDs;
 * notification content never leaves Room/server.
 */
@Singleton
class NotificationReadMutationStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    fun enqueue(organizationId: String, userId: String, notificationId: String) {
        enqueueAll(organizationId, userId, listOf(notificationId))
    }

    fun enqueueAll(organizationId: String, userId: String, notificationIds: Collection<String>) {
        val validIds = notificationIds.asSequence().map(String::trim).filter(String::isNotEmpty).toSet()
        if (validIds.isEmpty()) return
        synchronized(lock) {
            val key = key(organizationId, userId)
            val next = prefs.getStringSet(key, emptySet()).orEmpty().toMutableSet().apply { addAll(validIds) }
            check(prefs.edit().putStringSet(key, next).commit()) {
                "Unable to persist notification read mutation"
            }
        }
    }

    fun pending(organizationId: String, userId: String): Set<String> = synchronized(lock) {
        prefs.getStringSet(key(organizationId, userId), emptySet()).orEmpty().toSet()
    }

    fun acknowledge(organizationId: String, userId: String, notificationId: String) {
        synchronized(lock) {
            val key = key(organizationId, userId)
            val next = prefs.getStringSet(key, emptySet()).orEmpty().toMutableSet()
            if (!next.remove(notificationId)) return
            if (next.isEmpty()) {
                prefs.edit().remove(key).commit()
            } else {
                prefs.edit().putStringSet(key, next).commit()
            }
        }
    }

    private fun key(organizationId: String, userId: String): String {
        require(organizationId.isNotBlank() && userId.isNotBlank()) { "Notification read scope is required" }
        return "pending:$organizationId:$userId"
    }

    private companion object {
        const val PREFS_NAME = "verto_notification_read_mutations_v1"
    }
}
