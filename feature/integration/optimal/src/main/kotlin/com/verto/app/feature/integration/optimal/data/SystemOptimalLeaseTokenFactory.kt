package com.verto.app.feature.integration.optimal.data

import com.verto.app.feature.integration.optimal.domain.port.OptimalLeaseTokenFactory
import java.util.UUID
import javax.inject.Inject

class SystemOptimalLeaseTokenFactory @Inject constructor() : OptimalLeaseTokenFactory {
    override fun create(): String = UUID.randomUUID().toString()
}
