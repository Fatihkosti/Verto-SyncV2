package com.verto.app.feature.integration.optimal.data

import com.verto.app.feature.integration.optimal.domain.port.OptimalMessagingIdGenerator
import java.util.UUID
import javax.inject.Inject

class SystemOptimalMessagingIdGenerator @Inject constructor() : OptimalMessagingIdGenerator {
    override fun newConversationId(): String = UUID.randomUUID().toString()
    override fun newMessageId(): String = UUID.randomUUID().toString()
    override fun newMediaId(): String = UUID.randomUUID().toString()
}
