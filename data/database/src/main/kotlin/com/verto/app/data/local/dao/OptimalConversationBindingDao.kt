package com.verto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.verto.app.data.local.entity.OptimalConversationBindingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OptimalConversationBindingDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(binding: OptimalConversationBindingEntity)

    @Query(
        """
        SELECT * FROM optimal_conversation_bindings
        WHERE organization_id = :organizationId AND client_id = :clientId
        LIMIT 1
        """,
    )
    suspend fun get(
        organizationId: String,
        clientId: String,
    ): OptimalConversationBindingEntity?

    @Query(
        """
        SELECT * FROM optimal_conversation_bindings
        WHERE organization_id = :organizationId AND conversation_id = :conversationId
        LIMIT 1
        """,
    )
    suspend fun getByConversation(
        organizationId: String,
        conversationId: String,
    ): OptimalConversationBindingEntity?

    @Query(
        """
        SELECT * FROM optimal_conversation_bindings
        WHERE organization_id = :organizationId
        ORDER BY bound_at DESC, client_id ASC
        """,
    )
    fun observeForOrganization(
        organizationId: String,
    ): Flow<List<OptimalConversationBindingEntity>>
}
