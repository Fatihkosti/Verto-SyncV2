package com.verto.app.core.concurrency

import kotlinx.coroutines.CoroutineScope

/** Process-lifetime coroutine owner supplied only by the application composition root. */
interface AppCoroutineScope : CoroutineScope
