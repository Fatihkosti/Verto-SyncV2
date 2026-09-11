package com.verto.app.data.local.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.verto.app.data.local.entity.PartyIdentityEntity
import com.verto.app.data.local.entity.withSearchKeys
import com.verto.app.utils.MoneyMath
import kotlinx.coroutines.flow.Flow

/**
 * نتيجة SQL: عميل + رصيده المحسوب مباشرة بدون تحميل شيء في الـ memory.
 *
 *  totalDebt  = مجموع فواتير البيع (isOwedToMe = 1)
 *  totalPaid  = مجموع كل المدفوعات
 *  hasOverdue = هل يوجد دين متأخر غير مسدد
 *  remaining  = computed property (لا تُخزَّن في DB)
 */
data class InactiveCustomerRow(
    val customerId: String,
    val customerName: String,
    val phone: String,
    val lastSaleAt: Long,
    val saleCount: Int,
)

data class ClientWithBalance(
    @Embedded val client: PartyIdentityEntity,
    val totalDebt        : Double,
    val totalPaid        : Double,
    val totalPurchaseDebt: Double,
    val totalPurchasePaid: Double,
    val hasOverdue       : Boolean,
    val customerSegment  : String? = null,
    val supplierScope    : String? = null,
) {
    val remaining: Double get() = MoneyMath.subtract(totalDebt, totalPaid)
    val competitorBalance: Double get() =
        (totalDebt - (totalPaid - totalPurchasePaid)) - (totalPurchaseDebt - totalPurchasePaid)
}

data class PartyActivityRow(
    @Embedded val client: PartyIdentityEntity,
    val isSupplierRole: Boolean,
    val roleCreatedAt: Long,
)

data class PartySearchWithRoles(
    @Embedded val summary: ClientWithBalance,
    val isSupplierRole: Boolean,
    val isCompetitor: Boolean,
)

data class ClientRoleProjection(
    @Embedded val client: PartyIdentityEntity,
    val hasCustomerRole: Boolean,
    val hasSupplierRole: Boolean,
    val customerSegment: String?,
    val supplierScope: String?,
)

@Dao
abstract class ClientDao {

    // ── استعلامات بسيطة ──────────────────────────────────────────────

    @Query("""
        SELECT c.* FROM clients c
        WHERE EXISTS (
            SELECT 1 FROM party_roles pr
            WHERE pr.party_id=c.id AND pr.organization_id=:organizationId
              AND pr.status='ACTIVE' AND pr.deleted_at IS NULL
              AND pr.role IN ('CUSTOMER','SUPPLIER')
        )
        ORDER BY c.createdAt DESC
    """)
    abstract fun getAllClientsForOrganization(organizationId: String): Flow<List<PartyIdentityEntity>>

    @Query("""
        SELECT c.*,
          EXISTS(SELECT 1 FROM party_roles pr WHERE pr.party_id=c.id AND pr.organization_id=:organizationId AND pr.role='CUSTOMER' AND pr.status='ACTIVE' AND pr.deleted_at IS NULL) AS hasCustomerRole,
          EXISTS(SELECT 1 FROM party_roles pr WHERE pr.party_id=c.id AND pr.organization_id=:organizationId AND pr.role='SUPPLIER' AND pr.status='ACTIVE' AND pr.deleted_at IS NULL) AS hasSupplierRole,
          (SELECT segment FROM customer_profiles cp WHERE cp.party_id=c.id AND cp.organization_id=:organizationId AND EXISTS (SELECT 1 FROM party_roles prc WHERE prc.organization_id=:organizationId AND prc.party_id=c.id AND prc.role='CUSTOMER' AND prc.status='ACTIVE' AND prc.deleted_at IS NULL) LIMIT 1) AS customerSegment,
          (SELECT scope FROM supplier_profiles sp WHERE sp.party_id=c.id AND sp.organization_id=:organizationId AND EXISTS (SELECT 1 FROM party_roles prs WHERE prs.organization_id=:organizationId AND prs.party_id=c.id AND prs.role='SUPPLIER' AND prs.status='ACTIVE' AND prs.deleted_at IS NULL) LIMIT 1) AS supplierScope
        FROM clients c
        WHERE EXISTS (
            SELECT 1 FROM party_roles pr
            WHERE pr.party_id=c.id AND pr.organization_id=:organizationId
              AND pr.status='ACTIVE' AND pr.deleted_at IS NULL
              AND pr.role IN ('CUSTOMER','SUPPLIER')
        )
        ORDER BY c.createdAt DESC
    """)
    abstract fun observeClientRoleProjections(organizationId: String): Flow<List<ClientRoleProjection>>

    @Query("""
        SELECT c.*,
          EXISTS(SELECT 1 FROM party_roles pr WHERE pr.party_id=c.id AND pr.organization_id=:organizationId AND pr.role='CUSTOMER' AND pr.status='ACTIVE' AND pr.deleted_at IS NULL) AS hasCustomerRole,
          EXISTS(SELECT 1 FROM party_roles pr WHERE pr.party_id=c.id AND pr.organization_id=:organizationId AND pr.role='SUPPLIER' AND pr.status='ACTIVE' AND pr.deleted_at IS NULL) AS hasSupplierRole,
          (SELECT segment FROM customer_profiles cp WHERE cp.party_id=c.id AND cp.organization_id=:organizationId AND EXISTS (SELECT 1 FROM party_roles prc WHERE prc.organization_id=:organizationId AND prc.party_id=c.id AND prc.role='CUSTOMER' AND prc.status='ACTIVE' AND prc.deleted_at IS NULL) LIMIT 1) AS customerSegment,
          (SELECT scope FROM supplier_profiles sp WHERE sp.party_id=c.id AND sp.organization_id=:organizationId AND EXISTS (SELECT 1 FROM party_roles prs WHERE prs.organization_id=:organizationId AND prs.party_id=c.id AND prs.role='SUPPLIER' AND prs.status='ACTIVE' AND prs.deleted_at IS NULL) LIMIT 1) AS supplierScope
        FROM clients c
        WHERE c.id=:id
          AND EXISTS (SELECT 1 FROM party_roles pr WHERE pr.party_id=c.id AND pr.organization_id=:organizationId AND pr.status='ACTIVE' AND pr.deleted_at IS NULL AND pr.role IN ('CUSTOMER','SUPPLIER'))
        LIMIT 1
    """)
    abstract fun observeClientRoleProjection(organizationId: String, id: String): Flow<ClientRoleProjection?>

    @Query("""
        SELECT c.*,
          EXISTS(SELECT 1 FROM party_roles pr WHERE pr.party_id=c.id AND pr.organization_id=:organizationId AND pr.role='CUSTOMER' AND pr.status='ACTIVE' AND pr.deleted_at IS NULL) AS hasCustomerRole,
          EXISTS(SELECT 1 FROM party_roles pr WHERE pr.party_id=c.id AND pr.organization_id=:organizationId AND pr.role='SUPPLIER' AND pr.status='ACTIVE' AND pr.deleted_at IS NULL) AS hasSupplierRole,
          (SELECT segment FROM customer_profiles cp WHERE cp.party_id=c.id AND cp.organization_id=:organizationId AND EXISTS (SELECT 1 FROM party_roles prc WHERE prc.organization_id=:organizationId AND prc.party_id=c.id AND prc.role='CUSTOMER' AND prc.status='ACTIVE' AND prc.deleted_at IS NULL) LIMIT 1) AS customerSegment,
          (SELECT scope FROM supplier_profiles sp WHERE sp.party_id=c.id AND sp.organization_id=:organizationId AND EXISTS (SELECT 1 FROM party_roles prs WHERE prs.organization_id=:organizationId AND prs.party_id=c.id AND prs.role='SUPPLIER' AND prs.status='ACTIVE' AND prs.deleted_at IS NULL) LIMIT 1) AS supplierScope
        FROM clients c
        WHERE c.id=:id
          AND EXISTS (SELECT 1 FROM party_roles pr WHERE pr.party_id=c.id AND pr.organization_id=:organizationId AND pr.status='ACTIVE' AND pr.deleted_at IS NULL AND pr.role IN ('CUSTOMER','SUPPLIER'))
        LIMIT 1
    """)
    abstract suspend fun getClientRoleProjectionSync(organizationId: String, id: String): ClientRoleProjection?

    @Query("""
        SELECT c.*,
          EXISTS(SELECT 1 FROM party_roles pr WHERE pr.party_id=c.id AND pr.organization_id=:organizationId AND pr.role='CUSTOMER' AND pr.status='ACTIVE' AND pr.deleted_at IS NULL) AS hasCustomerRole,
          EXISTS(SELECT 1 FROM party_roles pr WHERE pr.party_id=c.id AND pr.organization_id=:organizationId AND pr.role='SUPPLIER' AND pr.status='ACTIVE' AND pr.deleted_at IS NULL) AS hasSupplierRole,
          (SELECT segment FROM customer_profiles cp WHERE cp.party_id=c.id AND cp.organization_id=:organizationId AND EXISTS (SELECT 1 FROM party_roles prc WHERE prc.organization_id=:organizationId AND prc.party_id=c.id AND prc.role='CUSTOMER' AND prc.status='ACTIVE' AND prc.deleted_at IS NULL) LIMIT 1) AS customerSegment,
          (SELECT scope FROM supplier_profiles sp WHERE sp.party_id=c.id AND sp.organization_id=:organizationId AND EXISTS (SELECT 1 FROM party_roles prs WHERE prs.organization_id=:organizationId AND prs.party_id=c.id AND prs.role='SUPPLIER' AND prs.status='ACTIVE' AND prs.deleted_at IS NULL) LIMIT 1) AS supplierScope
        FROM clients c
        WHERE EXISTS (SELECT 1 FROM party_roles pr WHERE pr.party_id=c.id AND pr.organization_id=:organizationId AND pr.status='ACTIVE' AND pr.deleted_at IS NULL AND pr.role IN ('CUSTOMER','SUPPLIER'))
        ORDER BY c.createdAt DESC
    """)
    abstract suspend fun getClientRoleProjectionsSync(organizationId: String): List<ClientRoleProjection>

    @Query("""
        SELECT c.*,
               CASE WHEN pr.role = 'SUPPLIER' THEN 1 ELSE 0 END AS isSupplierRole,
               pr.created_at AS roleCreatedAt
        FROM party_roles pr
        INNER JOIN clients c ON c.id = pr.party_id
        WHERE pr.organization_id = :organizationId
          AND pr.status = 'ACTIVE'
          AND pr.deleted_at IS NULL
          AND pr.role IN ('CUSTOMER', 'SUPPLIER')
          AND pr.created_at >= :sinceEpochMillis
        ORDER BY pr.created_at DESC, c.id ASC, pr.role ASC
        LIMIT :limit
    """)
    abstract fun observeActivityClients(
        organizationId: String,
        sinceEpochMillis: Long,
        limit: Int,
    ): Flow<List<PartyActivityRow>>

    @Query("""
        SELECT c.* FROM clients c
        WHERE c.id=:id
          AND EXISTS (
              SELECT 1 FROM party_roles pr
              WHERE pr.party_id=c.id AND pr.organization_id=:organizationId
                AND pr.status='ACTIVE' AND pr.deleted_at IS NULL
                AND pr.role IN ('CUSTOMER','SUPPLIER')
          )
        LIMIT 1
    """)
    abstract fun getClientById(organizationId: String, id: String): Flow<PartyIdentityEntity?>


    @Query("""
        SELECT c.id AS customerId,
               c.name AS customerName,
               c.phone AS phone,
               MAX(inv.createdAt) AS lastSaleAt,
               COUNT(inv.id) AS saleCount
        FROM clients c
        INNER JOIN invoices inv
                ON inv.clientId = c.id
               AND inv.organization_id = :organizationId
               AND inv.category = 'SALE'
               AND inv.voided = 0
        WHERE c.id NOT IN (
            'cash_client_main',
            '00000000-0000-0000-0000-000000000001'
        )
          AND EXISTS (
              SELECT 1
              FROM party_roles pr
              WHERE pr.party_id = c.id
                AND pr.organization_id = :organizationId
                AND pr.role = 'CUSTOMER'
                AND pr.status = 'ACTIVE'
          )
        GROUP BY c.id
        HAVING COUNT(inv.id) > 0
           AND MAX(inv.createdAt) <= :inactiveCutoffEpochMillis
        ORDER BY
            CASE WHEN MAX(inv.createdAt) <= :highPriorityCutoffEpochMillis THEN 0 ELSE 1 END,
            MAX(inv.createdAt) ASC,
            c.id ASC
    """)
    abstract fun observeInactiveCustomerCandidates(
        organizationId: String,
        inactiveCutoffEpochMillis: Long,
        highPriorityCutoffEpochMillis: Long,
    ): Flow<List<InactiveCustomerRow>>

    @Query("""
        SELECT c.* FROM clients c
        WHERE EXISTS (
            SELECT 1 FROM party_roles pr
            WHERE pr.party_id=c.id AND pr.organization_id=:organizationId
              AND pr.status='ACTIVE' AND pr.deleted_at IS NULL
              AND pr.role IN ('CUSTOMER','SUPPLIER')
        )
          AND (c.name LIKE '%' || :query || '%' OR c.phone LIKE '%' || :query || '%')
        ORDER BY c.createdAt DESC
    """)
    abstract fun searchClients(organizationId: String, query: String): Flow<List<PartyIdentityEntity>>

    @Query("""
        SELECT c.*,
            (SELECT COALESCE(SUM(totalAmount), 0)
             FROM invoices
             WHERE clientId = c.id AND organization_id = :organizationId
               AND category = 'SALE' AND voided = 0) AS totalDebt,
            (SELECT COALESCE(SUM(amount), 0)
             FROM payments p INNER JOIN invoices pi ON pi.id=p.invoiceId
             WHERE p.clientId = c.id AND pi.organization_id = :organizationId
               AND pi.category='SALE' AND pi.voided=0) AS totalPaid,
            (SELECT COALESCE(SUM(totalAmount), 0)
             FROM invoices
             WHERE clientId = c.id AND organization_id = :organizationId
               AND category = 'PURCHASE' AND voided = 0) AS totalPurchaseDebt,
            (SELECT COALESCE(SUM(p.amount), 0)
             FROM payments p
             INNER JOIN invoices i ON p.invoiceId = i.id
             WHERE i.clientId = c.id AND i.organization_id = :organizationId
               AND i.category='PURCHASE' AND i.voided = 0) AS totalPurchasePaid,
            (SELECT COUNT(*) FROM invoices
             WHERE clientId = c.id
               AND organization_id = :organizationId
               AND category='SALE'
               AND voided = 0
               AND dueDate < (strftime('%s','now') * 1000)
               AND totalAmount > (
                   SELECT COALESCE(SUM(amount), 0)
                   FROM payments WHERE invoiceId = invoices.id
               )
            ) > 0 AS hasOverdue,
            EXISTS(
                SELECT 1 FROM party_roles pr
                WHERE pr.party_id=c.id AND pr.organization_id=:organizationId
                  AND pr.role='SUPPLIER' AND pr.status='ACTIVE'
            ) AS isSupplierRole,
            (EXISTS(SELECT 1 FROM party_roles prc WHERE prc.party_id=c.id AND prc.organization_id=:organizationId AND prc.role='CUSTOMER' AND prc.status='ACTIVE' AND prc.deleted_at IS NULL) AND EXISTS(SELECT 1 FROM party_roles prs WHERE prs.party_id=c.id AND prs.organization_id=:organizationId AND prs.role='SUPPLIER' AND prs.status='ACTIVE' AND prs.deleted_at IS NULL)) AS isCompetitor,
            (SELECT segment FROM customer_profiles cp WHERE cp.party_id=c.id AND cp.organization_id=:organizationId AND EXISTS (SELECT 1 FROM party_roles prc WHERE prc.organization_id=:organizationId AND prc.party_id=c.id AND prc.role='CUSTOMER' AND prc.status='ACTIVE' AND prc.deleted_at IS NULL) LIMIT 1) AS customerSegment,
            (SELECT scope FROM supplier_profiles sp WHERE sp.party_id=c.id AND sp.organization_id=:organizationId AND EXISTS (SELECT 1 FROM party_roles prs WHERE prs.organization_id=:organizationId AND prs.party_id=c.id AND prs.role='SUPPLIER' AND prs.status='ACTIVE' AND prs.deleted_at IS NULL) LIMIT 1) AS supplierScope
        FROM clients c
        WHERE EXISTS (
            SELECT 1 FROM party_roles pr
            WHERE pr.party_id = c.id
              AND pr.organization_id = :organizationId
              AND pr.status = 'ACTIVE'
              AND pr.role IN ('CUSTOMER', 'SUPPLIER')
        )
          AND (
              (length(:textQuery) >= 2
               AND c.nameSearch >= :textQuery
               AND c.nameSearch < (:textQuery || char(1114111)))
              OR (length(:phoneQuery) >= 2
               AND c.phoneSearch >= :phoneQuery
               AND c.phoneSearch < (:phoneQuery || char(1114111)))
          )
        ORDER BY
            CASE WHEN c.nameSearch = :textQuery OR c.phoneSearch = :phoneQuery THEN 0 ELSE 1 END,
            c.nameSearch ASC,
            c.createdAt DESC,
            c.id ASC
        LIMIT :limit
    """)
    abstract suspend fun searchClientsWithBalanceByPrefix(
        organizationId: String,
        textQuery: String,
        phoneQuery: String,
        limit: Int
    ): List<PartySearchWithRoles>

    @Query("SELECT COUNT(*) FROM invoices WHERE organization_id=:organizationId AND clientId=:clientId")
    abstract suspend fun countInvoicesForClient(organizationId: String, clientId: String): Int

    @Query("SELECT id FROM invoices WHERE organization_id=:organizationId AND clientId=:clientId")
    abstract suspend fun getInvoiceIdsForClient(organizationId: String, clientId: String): List<String>

    @Query("""
        SELECT c.* FROM clients c
        WHERE EXISTS (
            SELECT 1 FROM party_roles pr
            WHERE pr.party_id=c.id AND pr.organization_id=:organizationId
              AND pr.status='ACTIVE' AND pr.deleted_at IS NULL
              AND pr.role IN ('CUSTOMER','SUPPLIER')
        )
    """)
    abstract suspend fun getAllClientsSyncForOrganization(organizationId: String): List<PartyIdentityEntity>

    /** Backup/tenant maintenance: includes archived roles so no Party V2 identity is orphaned. */
    @Query("""
        SELECT c.* FROM clients c
        WHERE EXISTS (
            SELECT 1 FROM party_roles pr
            WHERE pr.party_id=c.id AND pr.organization_id=:organizationId
        )
        ORDER BY c.createdAt DESC, c.id ASC
    """)
    abstract suspend fun getAllPartyIdentitiesForOrganizationSync(organizationId: String): List<PartyIdentityEntity>

    // ── Pagination ───────────────────────────────────────────────────

    @Query("""
        SELECT c.*,
            (SELECT COALESCE(SUM(totalAmount), 0) FROM invoices WHERE clientId = c.id AND organization_id=:organizationId AND category='SALE' AND voided = 0) AS totalDebt,
            (SELECT COALESCE(SUM(p.amount), 0) FROM payments p INNER JOIN invoices pi ON pi.id=p.invoiceId WHERE p.clientId=c.id AND pi.organization_id=:organizationId AND pi.category='SALE' AND pi.voided=0) AS totalPaid,
            (SELECT COALESCE(SUM(totalAmount), 0) FROM invoices WHERE clientId = c.id AND organization_id=:organizationId AND category='PURCHASE' AND voided = 0) AS totalPurchaseDebt,
            (SELECT COALESCE(SUM(p.amount), 0) FROM payments p INNER JOIN invoices i ON p.invoiceId = i.id
             WHERE i.clientId = c.id AND i.organization_id=:organizationId AND i.category='PURCHASE' AND i.voided = 0) AS totalPurchasePaid,
            (SELECT COUNT(*) FROM invoices
             WHERE clientId = c.id AND organization_id=:organizationId AND category='SALE' AND voided = 0
               AND dueDate < (strftime('%s','now') * 1000)
               AND totalAmount > (SELECT COALESCE(SUM(amount), 0) FROM payments WHERE invoiceId = invoices.id)
            ) > 0 AS hasOverdue,
            (SELECT segment FROM customer_profiles cp WHERE cp.party_id=c.id AND cp.organization_id=:organizationId AND EXISTS (SELECT 1 FROM party_roles prc WHERE prc.organization_id=:organizationId AND prc.party_id=c.id AND prc.role='CUSTOMER' AND prc.status='ACTIVE' AND prc.deleted_at IS NULL) LIMIT 1) AS customerSegment,
            (SELECT scope FROM supplier_profiles sp WHERE sp.party_id=c.id AND sp.organization_id=:organizationId AND EXISTS (SELECT 1 FROM party_roles prs WHERE prs.organization_id=:organizationId AND prs.party_id=c.id AND prs.role='SUPPLIER' AND prs.status='ACTIVE' AND prs.deleted_at IS NULL) LIMIT 1) AS supplierScope
        FROM clients c
        WHERE EXISTS (
            SELECT 1 FROM party_roles pr
            WHERE pr.party_id=c.id AND pr.organization_id=:organizationId AND pr.status='ACTIVE'
              AND pr.deleted_at IS NULL
              AND pr.role=CASE WHEN :showSuppliers=1 THEN 'SUPPLIER' ELSE 'CUSTOMER' END
        )
          AND (:supplierScope IS NULL OR (:showSuppliers=1 AND EXISTS (
              SELECT 1 FROM supplier_profiles sp WHERE sp.party_id=c.id AND sp.organization_id=:organizationId AND sp.scope=:supplierScope
          )))
        ORDER BY c.name ASC
    """)
    abstract fun getAllClientsWithBalancePaged(organizationId: String, showSuppliers: Int, supplierScope: String?): PagingSource<Int, ClientWithBalance>

    @Query("""
        SELECT c.*,
            (SELECT COALESCE(SUM(totalAmount), 0) FROM invoices WHERE clientId = c.id AND organization_id=:organizationId AND category='SALE' AND voided = 0) AS totalDebt,
            (SELECT COALESCE(SUM(p.amount), 0) FROM payments p INNER JOIN invoices pi ON pi.id=p.invoiceId WHERE p.clientId=c.id AND pi.organization_id=:organizationId AND pi.category='SALE' AND pi.voided=0) AS totalPaid,
            (SELECT COALESCE(SUM(totalAmount), 0) FROM invoices WHERE clientId = c.id AND organization_id=:organizationId AND category='PURCHASE' AND voided = 0) AS totalPurchaseDebt,
            (SELECT COALESCE(SUM(p.amount), 0) FROM payments p INNER JOIN invoices i ON p.invoiceId = i.id
             WHERE i.clientId = c.id AND i.organization_id=:organizationId AND i.category='PURCHASE' AND i.voided = 0) AS totalPurchasePaid,
            (SELECT COUNT(*) FROM invoices
             WHERE clientId = c.id AND organization_id=:organizationId AND category='SALE' AND voided = 0
               AND dueDate < (strftime('%s','now') * 1000)
               AND totalAmount > (SELECT COALESCE(SUM(amount), 0) FROM payments WHERE invoiceId = invoices.id)
            ) > 0 AS hasOverdue,
            (SELECT segment FROM customer_profiles cp WHERE cp.party_id=c.id AND cp.organization_id=:organizationId AND EXISTS (SELECT 1 FROM party_roles prc WHERE prc.organization_id=:organizationId AND prc.party_id=c.id AND prc.role='CUSTOMER' AND prc.status='ACTIVE' AND prc.deleted_at IS NULL) LIMIT 1) AS customerSegment,
            (SELECT scope FROM supplier_profiles sp WHERE sp.party_id=c.id AND sp.organization_id=:organizationId AND EXISTS (SELECT 1 FROM party_roles prs WHERE prs.organization_id=:organizationId AND prs.party_id=c.id AND prs.role='SUPPLIER' AND prs.status='ACTIVE' AND prs.deleted_at IS NULL) LIMIT 1) AS supplierScope
        FROM clients c
        WHERE EXISTS (
            SELECT 1 FROM party_roles pr
            WHERE pr.party_id=c.id AND pr.organization_id=:organizationId AND pr.status='ACTIVE'
              AND pr.deleted_at IS NULL
              AND pr.role=CASE WHEN :showSuppliers=1 THEN 'SUPPLIER' ELSE 'CUSTOMER' END
        )
          AND (:supplierScope IS NULL OR (:showSuppliers=1 AND EXISTS (
              SELECT 1 FROM supplier_profiles sp WHERE sp.party_id=c.id AND sp.organization_id=:organizationId AND sp.scope=:supplierScope
          )))
          AND (c.name LIKE '%' || :query || '%' OR c.phone LIKE '%' || :query || '%')
        ORDER BY c.name ASC
    """)
    abstract fun searchClientsWithBalancePaged(organizationId: String, query: String, showSuppliers: Int, supplierScope: String?): PagingSource<Int, ClientWithBalance>

    // ── استعلامات الرصيد — subqueries بدل JOIN مزدوج ────────────────
    //
    // السبب: LEFT JOIN على جدولين (invoices + payments) في نفس الوقت
    // ينتج Cartesian Product يضاعف الأرقام.
    // الـ subqueries تحسب كل قيمة باستقلالية تامة.

    @Query("""
        SELECT c.*,
            (SELECT COALESCE(SUM(totalAmount), 0)
             FROM invoices
             WHERE clientId = c.id AND organization_id=:organizationId AND category='SALE' AND voided = 0) AS totalDebt,
            (SELECT COALESCE(SUM(amount), 0)
             FROM payments p INNER JOIN invoices pi ON pi.id=p.invoiceId
             WHERE p.clientId = c.id AND pi.organization_id=:organizationId AND pi.category='SALE' AND pi.voided=0) AS totalPaid,
            (SELECT COALESCE(SUM(totalAmount), 0)
             FROM invoices
             WHERE clientId = c.id AND organization_id=:organizationId AND category='PURCHASE' AND voided = 0) AS totalPurchaseDebt,
            (SELECT COALESCE(SUM(p.amount), 0)
             FROM payments p INNER JOIN invoices i ON p.invoiceId = i.id
             WHERE i.clientId = c.id AND i.organization_id=:organizationId AND i.category='PURCHASE' AND i.voided = 0) AS totalPurchasePaid,
            (SELECT COUNT(*) FROM invoices
             WHERE clientId = c.id
               AND organization_id=:organizationId
               AND category='SALE'
               AND voided = 0
               AND dueDate < (strftime('%s','now') * 1000)
               AND totalAmount > (
                   SELECT COALESCE(SUM(amount), 0)
                   FROM payments WHERE invoiceId = invoices.id
               )
            ) > 0 AS hasOverdue,
            (SELECT segment FROM customer_profiles cp WHERE cp.party_id=c.id AND cp.organization_id=:organizationId AND EXISTS (SELECT 1 FROM party_roles prc WHERE prc.organization_id=:organizationId AND prc.party_id=c.id AND prc.role='CUSTOMER' AND prc.status='ACTIVE' AND prc.deleted_at IS NULL) LIMIT 1) AS customerSegment,
            (SELECT scope FROM supplier_profiles sp WHERE sp.party_id=c.id AND sp.organization_id=:organizationId AND EXISTS (SELECT 1 FROM party_roles prs WHERE prs.organization_id=:organizationId AND prs.party_id=c.id AND prs.role='SUPPLIER' AND prs.status='ACTIVE' AND prs.deleted_at IS NULL) LIMIT 1) AS supplierScope
        FROM clients c
        WHERE EXISTS (
            SELECT 1 FROM party_roles pr
            WHERE pr.party_id=c.id AND pr.organization_id=:organizationId
              AND pr.status='ACTIVE' AND pr.deleted_at IS NULL
              AND pr.role='CUSTOMER'
        )
        ORDER BY c.createdAt DESC
    """)
    abstract fun getAllClientsWithBalance(organizationId: String): Flow<List<ClientWithBalance>>

    @Query("""
        SELECT c.*,
            (SELECT COALESCE(SUM(totalAmount), 0)
             FROM invoices
             WHERE clientId = c.id AND organization_id=:organizationId AND category='SALE' AND voided = 0) AS totalDebt,
            (SELECT COALESCE(SUM(amount), 0)
             FROM payments p INNER JOIN invoices pi ON pi.id=p.invoiceId
             WHERE p.clientId = c.id AND pi.organization_id=:organizationId AND pi.category='SALE' AND pi.voided=0) AS totalPaid,
            (SELECT COALESCE(SUM(totalAmount), 0)
             FROM invoices
             WHERE clientId = c.id AND organization_id=:organizationId AND category='PURCHASE' AND voided = 0) AS totalPurchaseDebt,
            (SELECT COALESCE(SUM(p.amount), 0)
             FROM payments p INNER JOIN invoices i ON p.invoiceId = i.id
             WHERE i.clientId = c.id AND i.organization_id=:organizationId AND i.category='PURCHASE' AND i.voided = 0) AS totalPurchasePaid,
            (SELECT COUNT(*) FROM invoices
             WHERE clientId = c.id
               AND organization_id=:organizationId
               AND category='SALE'
               AND voided = 0
               AND dueDate < (strftime('%s','now') * 1000)
               AND totalAmount > (
                   SELECT COALESCE(SUM(amount), 0)
                   FROM payments WHERE invoiceId = invoices.id
               )
            ) > 0 AS hasOverdue,
            (SELECT segment FROM customer_profiles cp WHERE cp.party_id=c.id AND cp.organization_id=:organizationId AND EXISTS (SELECT 1 FROM party_roles prc WHERE prc.organization_id=:organizationId AND prc.party_id=c.id AND prc.role='CUSTOMER' AND prc.status='ACTIVE' AND prc.deleted_at IS NULL) LIMIT 1) AS customerSegment,
            (SELECT scope FROM supplier_profiles sp WHERE sp.party_id=c.id AND sp.organization_id=:organizationId AND EXISTS (SELECT 1 FROM party_roles prs WHERE prs.organization_id=:organizationId AND prs.party_id=c.id AND prs.role='SUPPLIER' AND prs.status='ACTIVE' AND prs.deleted_at IS NULL) LIMIT 1) AS supplierScope
        FROM clients c
        WHERE EXISTS (
            SELECT 1 FROM party_roles pr
            WHERE pr.party_id=c.id AND pr.organization_id=:organizationId
              AND pr.status='ACTIVE' AND pr.deleted_at IS NULL
              AND pr.role='CUSTOMER'
        )
          AND (c.name LIKE '%' || :query || '%' OR c.phone LIKE '%' || :query || '%')
        ORDER BY c.createdAt DESC
    """)
    abstract fun searchClientsWithBalance(organizationId: String, query: String): Flow<List<ClientWithBalance>>

    // ── كتابة ────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertClientRaw(client: PartyIdentityEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertClientsRaw(clients: List<PartyIdentityEntity>)

    @Update
    protected abstract suspend fun updateClientRaw(client: PartyIdentityEntity)

    @Transaction
    open suspend fun insertClient(client: PartyIdentityEntity): Long =
        insertClientRaw(client.withSearchKeys())

    @Transaction
    open suspend fun insertClients(clients: List<PartyIdentityEntity>) =
        insertClientsRaw(clients.map(PartyIdentityEntity::withSearchKeys))

    @Transaction
    open suspend fun updateClient(client: PartyIdentityEntity) =
        updateClientRaw(client.withSearchKeys())

    @Delete
    abstract suspend fun deleteClient(client: PartyIdentityEntity)

    /** مخصص للـ PULL فقط — لا يحذف العميل ولا يشغّل CASCADE */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertClientsFromRemoteRaw(clients: List<PartyIdentityEntity>)

    @Transaction
    open suspend fun insertClientsFromRemote(clients: List<PartyIdentityEntity>) =
        insertClientsFromRemoteRaw(clients.map(PartyIdentityEntity::withSearchKeys))

    /** Party V2 remote identity merge: updates stable identity only and never touches legacy profile columns. */
    @Query("""
        UPDATE clients SET
            name = :name,
            phone = :phone,
            nameSearch = :nameSearch,
            phoneSearch = :phoneSearch,
            address = CASE WHEN :address = '' AND address != '' THEN address ELSE :address END,
            generalNote = CASE WHEN :generalNote = '' AND generalNote != '' THEN generalNote ELSE :generalNote END,
            createdBy = CASE WHEN :createdBy != '' THEN :createdBy ELSE createdBy END,
            isDirty = 0
        WHERE id = :id
    """)
    abstract suspend fun updatePartyIdentityFromRemote(
        id: String,
        name: String,
        phone: String,
        nameSearch: String,
        phoneSearch: String,
        address: String,
        generalNote: String,
        createdBy: String,
    )

    /** نسخة متزامنة للقراءة المباشرة (للحفاظ على createdBy عند تعديل العميل). */
    @Query("""
        SELECT c.* FROM clients c
        WHERE c.id=:id
          AND EXISTS (
              SELECT 1 FROM party_roles pr
              WHERE pr.party_id=c.id AND pr.organization_id=:organizationId
                AND pr.status='ACTIVE' AND pr.deleted_at IS NULL
                AND pr.role IN ('CUSTOMER','SUPPLIER')
          )
        LIMIT 1
    """)
    abstract suspend fun getClientByIdSync(organizationId: String, id: String): PartyIdentityEntity?

    /** Internal identity lookup used only when a caller intentionally handles Party-role scope itself. */
    @Query("SELECT * FROM clients WHERE id = :id LIMIT 1")
    abstract suspend fun getClientIdentityByIdSync(id: String): PartyIdentityEntity?

    /** SYNC-012: only dirty identities belonging to the active organization may be pushed. */
    @Query("""
        SELECT c.* FROM clients c
        WHERE c.isDirty=1
          AND EXISTS (
              SELECT 1 FROM party_roles pr
              WHERE pr.party_id=c.id AND pr.organization_id=:organizationId
                AND pr.status='ACTIVE' AND pr.deleted_at IS NULL
                AND pr.role IN ('CUSTOMER','SUPPLIER')
          )
    """)
    abstract suspend fun getDirtyClientsSync(organizationId: String): List<PartyIdentityEntity>

    @Query("UPDATE clients SET isDirty = 1 WHERE id = :id")
    abstract suspend fun markClientDirty(id: String): Int

    /** SYNC-012: تصفير علم التغيّر بعد رفع ناجح. */
    @Query("UPDATE clients SET isDirty = 0 WHERE id IN (:ids)")
    abstract suspend fun markClientsClean(ids: List<String>)

    @Query("DELETE FROM clients WHERE id = :id")
    abstract suspend fun deleteClientById(id: String)

    // ── إعادة التعيين ─────────────────────────────────
    // يحذف كل العملاء ما عدا العميل النقدي والمورد النقدي
    @Query("DELETE FROM clients WHERE id NOT IN ('cash_client_main', 'cash_supplier_main')")
    abstract suspend fun deleteAllNonSystemClients()

    @Query("DELETE FROM clients")
    abstract suspend fun deleteAllClients()
}
