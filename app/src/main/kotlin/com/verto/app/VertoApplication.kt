package com.verto.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.verto.app.core.concurrency.AppCoroutineScope
import com.verto.app.data.sync.SyncManager
import com.verto.app.data.sync.SyncRequestReason
import com.verto.app.notifications.NotificationHelper
import com.verto.app.utils.CrashReporter
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class VertoApplication : Application(), AppCoroutineScope {
    override val coroutineContext = SupervisorJob() + Dispatchers.IO

    @Inject lateinit var syncManager: SyncManager

    private var startedActivities = 0
    private var changingConfigurations = false

    override fun onCreate() {
        super.onCreate()
        // مطلوبان قبل أي Activity أو إشعار؛ بقية العمل يؤجل لما بعد الإطار الأول.
        CrashReporter.init(this)
        NotificationHelper.createChannels(this)
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (startedActivities == 0 && !changingConfigurations) {
                    launch {
                        // M07: returning to foreground registers the same durable V2 request.
                        syncManager.request(SyncRequestReason.FOREGROUND)
                    }
                }
                startedActivities += 1
            }

            override fun onActivityStopped(activity: Activity) {
                changingConfigurations = activity.isChangingConfigurations
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
