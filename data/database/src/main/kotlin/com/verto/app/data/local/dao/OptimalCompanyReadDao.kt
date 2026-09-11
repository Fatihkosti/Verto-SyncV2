package com.verto.app.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Read-only projection over Party, Invoice, and Optimal link ownership. */
data class OptimalCompanyReadRow(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "client_id") val clientId: String,
    @ColumnInfo(name = "client_name") val clientName: String,
    @ColumnInfo(name = "customer_segment") val customerSegment: String,
    @ColumnInfo(name = "is_linked") val isLinked: Boolean,
    @ColumnInfo(name = "optimal_company_id") val optimalCompanyId: String?,
    @ColumnInfo(name = "linked_at") val linkedAt: Long?,
    @ColumnInfo(name = "invoice_count") val invoiceCount: Int,
)

@Dao
interface OptimalCompanyReadDao {
    /**
     * Party V2 roles/profiles and invoice counts are all constrained to the requested organization.
     */
    @Query(
        """
        SELECT
            :organizationId AS organization_id,
            c.id AS client_id,
            c.name AS client_name,
            cp.segment AS customer_segment,
            CASE WHEN link.client_id IS NULL THEN 0 ELSE 1 END AS is_linked,
            link.optimal_company_id AS optimal_company_id,
            link.linked_at AS linked_at,
            (SELECT COUNT(*) FROM invoices invoice WHERE invoice.clientId = c.id AND invoice.organization_id = :organizationId) AS invoice_count
        FROM clients c
        INNER JOIN party_roles role
            ON role.party_id = c.id
            AND role.organization_id = :organizationId
            AND role.role = 'CUSTOMER'
            AND role.status = 'ACTIVE'
            AND role.deleted_at IS NULL
        INNER JOIN customer_profiles cp
            ON cp.party_id = c.id
            AND cp.organization_id = :organizationId
            AND cp.segment = 'COMPANY'
        LEFT JOIN optimal_company_links link
            ON link.organization_id = :organizationId
            AND link.client_id = c.id
        WHERE length(trim(c.name)) > 0
        AND (:searchTerm = '' OR lower(c.name) LIKE '%' || lower(:searchTerm) || '%')
        AND (
            :linkFilter = 0
            OR (:linkFilter = 1 AND link.client_id IS NOT NULL)
            OR (:linkFilter = 2 AND link.client_id IS NULL)
        )
        ORDER BY c.name COLLATE NOCASE ASC, c.id ASC
        """,
    )
    fun observeCompanies(
        organizationId: String,
        searchTerm: String,
        linkFilter: Int,
    ): Flow<List<OptimalCompanyReadRow>>
}
