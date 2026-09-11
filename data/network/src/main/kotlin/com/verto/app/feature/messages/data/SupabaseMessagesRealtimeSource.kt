package com.verto.app.feature.messages.data

import com.verto.app.data.remote.VertoSupabase
import com.verto.app.data.remote.dto.InternalMessageDto
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SupabaseMessagesRealtimeSource @Inject constructor() : MessagesRealtimeSource {
    private val realtimeJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    override fun observeConversationListChanges(orgId: String): Flow<Unit> = channelFlow {
        val supabase = VertoSupabase.client
        val channel = supabase.channel("verto_conversations_list_$orgId")
        try {
            channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "conversations"
                filter("org_id", FilterOperator.EQ, orgId)
            }.onEach { trySend(Unit) }.launchIn(this)

            channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                table = "conversations"
                filter("org_id", FilterOperator.EQ, orgId)
            }.onEach { trySend(Unit) }.launchIn(this)

            channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "internal_messages"
                filter("org_id", FilterOperator.EQ, orgId)
            }.onEach { trySend(Unit) }.launchIn(this)

            supabase.realtime.connect()
            channel.subscribe()
            awaitCancellation()
        } finally {
            runCatching { supabase.realtime.removeChannel(channel) }
        }
    }

    override fun observeMessages(orgId: String, conversationId: String): Flow<InternalMessageDto> = channelFlow {
        val supabase = VertoSupabase.client
        val channel = supabase.channel("chat_detail_${orgId}_$conversationId")
        try {
            channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "internal_messages"
                // Realtime accepts one server filter here; org is enforced again on the decoded row.
                filter("conversation_id", FilterOperator.EQ, conversationId)
            }.onEach { action ->
                runCatching {
                    realtimeJson.decodeFromJsonElement<InternalMessageDto>(action.record)
                }.getOrNull()?.let { message ->
                    if (message.orgId == orgId && message.conversationId == conversationId) trySend(message)
                }
            }.launchIn(this)

            supabase.realtime.connect()
            channel.subscribe()
            awaitCancellation()
        } finally {
            runCatching { supabase.realtime.removeChannel(channel) }
        }
    }
}
