package com.verto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.verto.app.data.local.entity.PriceListTemplateEntity
import com.verto.app.data.local.entity.PriceListTemplateItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceListDao {
    @Query("""
        SELECT * FROM price_list_templates
        WHERE organization_id = :organizationId
        ORDER BY is_favorite DESC, updated_at DESC, name COLLATE NOCASE ASC
    """)
    fun observeTemplates(organizationId: String): Flow<List<PriceListTemplateEntity>>

    @Query("""
        SELECT i.* FROM price_list_template_items i
        INNER JOIN price_list_templates t ON t.id = i.template_id
        WHERE t.organization_id = :organizationId
        ORDER BY i.template_id, i.sort_order, i.inventory_item_id
    """)
    fun observeTemplateItems(organizationId: String): Flow<List<PriceListTemplateItemEntity>>

    @Upsert
    suspend fun upsertTemplate(template: PriceListTemplateEntity)

    @Upsert
    suspend fun upsertTemplateItems(items: List<PriceListTemplateItemEntity>)

    @Query("DELETE FROM price_list_template_items WHERE template_id = :templateId")
    suspend fun deleteTemplateItems(templateId: String)

    @Query("DELETE FROM price_list_templates WHERE id = :templateId AND organization_id = :organizationId")
    suspend fun deleteTemplate(templateId: String, organizationId: String): Int

    @Query("SELECT * FROM price_list_templates WHERE id = :templateId LIMIT 1")
    suspend fun getTemplate(templateId: String): PriceListTemplateEntity?

    @Query("""
        SELECT id FROM price_list_templates
        WHERE organization_id = :organizationId
          AND name = :name COLLATE NOCASE
          AND id != :templateId
        LIMIT 1
    """)
    suspend fun findConflictingTemplateId(organizationId: String, name: String, templateId: String): String?

    @Query("SELECT * FROM price_list_template_items WHERE template_id = :templateId ORDER BY sort_order")
    suspend fun getTemplateItems(templateId: String): List<PriceListTemplateItemEntity>

    @Query("SELECT * FROM price_list_templates WHERE organization_id = :organizationId")
    suspend fun getTemplatesSync(organizationId: String): List<PriceListTemplateEntity>

    @Query("""
        SELECT i.* FROM price_list_template_items i
        INNER JOIN price_list_templates t ON t.id = i.template_id
        WHERE t.organization_id = :organizationId
        ORDER BY i.template_id, i.sort_order
    """)
    suspend fun getTemplateItemsSync(organizationId: String): List<PriceListTemplateItemEntity>
}
