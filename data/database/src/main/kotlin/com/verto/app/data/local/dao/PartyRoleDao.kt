package com.verto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.verto.app.data.local.entity.CustomerProfileEntity
import com.verto.app.data.local.entity.PartyMigrationIssueEntity
import com.verto.app.data.local.entity.PartyRoleAuditEntity
import com.verto.app.data.local.entity.PartyRoleEntity
import com.verto.app.data.local.entity.SupplierProfileEntity
import com.verto.app.data.local.entity.PartySyncOutboxEntity
import com.verto.app.data.local.entity.PartySyncConflictEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PartyRoleDao {
    @Query("SELECT COUNT(*) FROM party_roles WHERE party_id=:partyId")
    suspend fun countRoles(partyId: String): Int
    @Query("SELECT * FROM party_roles WHERE organization_id=:organizationId AND role=:role AND status='ACTIVE' ORDER BY updated_at DESC")
    fun observeActiveRoles(organizationId: String, role: String): Flow<List<PartyRoleEntity>>

    @Query("""
        SELECT CASE WHEN
          EXISTS(SELECT 1 FROM party_roles WHERE organization_id=:organizationId AND party_id=:partyId AND role='CUSTOMER' AND status='ACTIVE' AND deleted_at IS NULL)
          AND EXISTS(SELECT 1 FROM party_roles WHERE organization_id=:organizationId AND party_id=:partyId AND role='SUPPLIER' AND status='ACTIVE' AND deleted_at IS NULL)
        THEN 1 ELSE 0 END
    """)
    fun observeIsCompetitor(organizationId: String, partyId: String): Flow<Boolean>

    @Query("SELECT * FROM party_roles WHERE organization_id=:organizationId ORDER BY party_id, role")
    suspend fun getRolesForOrganizationSync(organizationId: String): List<PartyRoleEntity>

    @Query("SELECT * FROM customer_profiles WHERE organization_id=:organizationId ORDER BY party_id")
    suspend fun getCustomerProfilesForOrganizationSync(organizationId: String): List<CustomerProfileEntity>

    @Query("SELECT * FROM supplier_profiles WHERE organization_id=:organizationId ORDER BY party_id")
    suspend fun getSupplierProfilesForOrganizationSync(organizationId: String): List<SupplierProfileEntity>

    @Query("SELECT * FROM party_roles WHERE organization_id=:organizationId AND party_id=:partyId AND role=:role LIMIT 1")
    suspend fun getRole(partyId: String, organizationId: String, role: String): PartyRoleEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRole(role: PartyRoleEntity): Long

    @Query("UPDATE party_roles SET status=:status, updated_at=:updatedAt, archived_at=:archivedAt, archived_by=:actorId, archive_reason=:reason, dirty=1, sync_revision=sync_revision+1 WHERE organization_id=:organizationId AND party_id=:partyId AND role=:role")
    suspend fun setRoleStatus(partyId: String, organizationId: String, role: String, status: String, updatedAt: Long, archivedAt: Long?, actorId: String?, reason: String?): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCustomerProfile(profile: CustomerProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCustomerProfile(profile: CustomerProfileEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSupplierProfile(profile: SupplierProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSupplierProfile(profile: SupplierProfileEntity)

    @Query("SELECT * FROM customer_profiles WHERE organization_id=:organizationId AND party_id=:partyId LIMIT 1")
    fun observeCustomerProfile(organizationId: String, partyId: String): Flow<CustomerProfileEntity?>

    @Query("SELECT * FROM customer_profiles WHERE organization_id=:organizationId AND party_id=:partyId LIMIT 1")
    suspend fun getCustomerProfileSync(organizationId: String, partyId: String): CustomerProfileEntity?

    @Query("SELECT * FROM supplier_profiles WHERE organization_id=:organizationId AND party_id=:partyId LIMIT 1")
    fun observeSupplierProfile(organizationId: String, partyId: String): Flow<SupplierProfileEntity?>

    @Query("SELECT * FROM supplier_profiles WHERE organization_id=:organizationId AND party_id=:partyId LIMIT 1")
    suspend fun getSupplierProfileSync(organizationId: String, partyId: String): SupplierProfileEntity?

    @Query("UPDATE party_roles SET dirty=0 WHERE organization_id=:organizationId AND party_id=:partyId")
    suspend fun markRolesClean(organizationId: String, partyId: String): Int

    @Query("UPDATE customer_profiles SET dirty=0 WHERE organization_id=:organizationId AND party_id=:partyId")
    suspend fun markCustomerProfileClean(organizationId: String, partyId: String): Int

    @Query("UPDATE supplier_profiles SET dirty=0 WHERE organization_id=:organizationId AND party_id=:partyId")
    suspend fun markSupplierProfileClean(organizationId: String, partyId: String): Int

    @Query("DELETE FROM party_sync_outbox WHERE aggregate_type='ROLE' AND aggregate_id=:partyId")
    suspend fun clearLegacyRoleOutbox(partyId: String): Int

    /** Session 308 REMOTE_APPLY methods never mark rows dirty or enqueue Party operations. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoleFromRemote(role: PartyRoleEntity): Long

    @Query("DELETE FROM party_roles WHERE organization_id=:organizationId AND party_id=:partyId AND role=:role")
    suspend fun deleteRoleFromRemote(organizationId: String, partyId: String, role: String): Int

    @Query("DELETE FROM customer_profiles WHERE organization_id=:organizationId AND party_id=:partyId")
    suspend fun deleteCustomerProfileFromRemote(organizationId: String, partyId: String): Int

    @Query("DELETE FROM supplier_profiles WHERE organization_id=:organizationId AND party_id=:partyId")
    suspend fun deleteSupplierProfileFromRemote(organizationId: String, partyId: String): Int

    @Query("DELETE FROM customer_profiles WHERE organization_id=:organizationId")
    suspend fun deleteCustomerProfilesForOrganization(organizationId: String): Int

    @Query("DELETE FROM supplier_profiles WHERE organization_id=:organizationId")
    suspend fun deleteSupplierProfilesForOrganization(organizationId: String): Int

    @Query("DELETE FROM party_roles WHERE organization_id=:organizationId")
    suspend fun deleteRolesForOrganization(organizationId: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM party_sync_outbox WHERE aggregate_type=:aggregateType AND aggregate_id=:aggregateId AND state IN ('PENDING','LEASED','RETRY','REQUIRES_REVIEW'))")
    suspend fun hasActivePartyMutation(aggregateType: String, aggregateId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAudit(row: PartyRoleAuditEntity)

    @Query("SELECT * FROM party_migration_issues WHERE resolved=0 ORDER BY created_at, id")
    suspend fun getUnresolvedMigrationIssues(): List<PartyMigrationIssueEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueueSyncOperation(row: PartySyncOutboxEntity): Long

    @Query("SELECT * FROM party_sync_outbox WHERE state='PENDING' AND next_attempt_at<=:now ORDER BY CASE aggregate_type WHEN 'IDENTITY' THEN 0 WHEN 'ROLE' THEN 1 WHEN 'CUSTOMER_PROFILE' THEN 2 WHEN 'SUPPLIER_PROFILE' THEN 3 ELSE 4 END, created_at, id")
    suspend fun getPendingSyncOperations(now: Long): List<PartySyncOutboxEntity>


    @Query("SELECT * FROM party_sync_outbox WHERE operation_id=:operationId LIMIT 1")
    suspend fun getSyncOperationByOperationId(operationId: String): PartySyncOutboxEntity?


    /** Session 309 bounded bridge scan; PARTY_ROLE remains on its stronger outbox. */
    @Query("SELECT * FROM party_sync_outbox WHERE aggregate_type='ROLE' AND state IN ('PENDING','RETRY') AND next_attempt_at<=:now ORDER BY created_at, id LIMIT :limit")
    suspend fun getPendingPartyRoleOperations(now: Long, limit: Int): List<PartySyncOutboxEntity>

    @Query("UPDATE party_sync_outbox SET state=:terminalState, last_error='' WHERE operation_id=:operationId AND state IN ('PENDING','RETRY')")
    suspend fun markPartyRoleTerminal(operationId: String, terminalState: String): Int

    @Query("UPDATE party_sync_outbox SET state='RETRY', retry_count=retry_count+1, last_error=:error, next_attempt_at=:nextAttemptAt WHERE operation_id=:operationId AND state IN ('PENDING','RETRY')")
    suspend fun markPartyRoleRetry(operationId: String, error: String, nextAttemptAt: Long): Int


    @Query("SELECT COUNT(*) FROM party_sync_outbox WHERE aggregate_type='ROLE' AND state IN ('PENDING','RETRY')")
    suspend fun countPartyRoleBacklog(): Long

    @Query("SELECT COUNT(*) FROM party_sync_outbox WHERE aggregate_type='ROLE' AND state='REQUIRES_REVIEW'")
    suspend fun countPartyRoleReview(): Long

    @Query("SELECT COUNT(*) FROM party_sync_outbox WHERE aggregate_type='ROLE' AND state='REJECTED'")
    suspend fun countPartyRoleRejected(): Long

    @Query("SELECT MIN(next_attempt_at) FROM party_sync_outbox WHERE aggregate_type='ROLE' AND state='RETRY' AND next_attempt_at>:now")
    suspend fun nextPartyRoleRetryAt(now: Long): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSyncConflict(row: PartySyncConflictEntity): Long
}
