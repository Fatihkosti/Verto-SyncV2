package com.verto.app.startup

import java.util.concurrent.atomic.AtomicBoolean

/**
 * بوابة مملوكة لمسار بدء التطبيق تمنع تكرار الأعمال المؤجلة عند إعادة إنشاء Activity.
 */
internal class StartupOnceGate {
    private val started = AtomicBoolean(false)

    fun tryStart(): Boolean = started.compareAndSet(false, true)
}
