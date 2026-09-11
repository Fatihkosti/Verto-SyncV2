package com.verto.app.notifications

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.verto.app.MainActivity
import com.verto.app.notifications.InternalNavigationMailbox
import com.verto.app.notifications.NotificationRoutePolicy

/** Non-exported entry point used only by app-created PendingIntents. */
class NotificationRouterActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationRoutePolicy.validate(intent?.getStringExtra(NotificationHelper.EXTRA_NAV_ROUTE))
            ?.let(InternalNavigationMailbox::offer)
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        })
        finish()
    }
}
