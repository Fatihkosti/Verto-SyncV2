package com.verto.app.feature.integration.optimal.data

import com.verto.app.feature.integration.optimal.domain.repository.OptimalClock
import javax.inject.Inject

class SystemOptimalClock @Inject constructor() : OptimalClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
