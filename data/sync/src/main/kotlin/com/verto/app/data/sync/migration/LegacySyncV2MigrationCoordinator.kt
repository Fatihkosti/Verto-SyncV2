package com.verto.app.data.sync.migration

import android.database.Cursor
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.SyncLegacyMigrationEntryEntity
import com.verto.app.data.local.entity.SyncLegacyMigrationStateEntity
import com.verto.app.data.sync.SyncDeletionSnapshot
import com.verto.app.data.sync.SyncWorkScope
import com.verto.app.data.sync.UnifiedStrongerBridgeRegistry
import com.verto.app.data.sync.ownership.ProtectedSyncKey
import com.verto.app.data.sync.ownership.SyncPendingProtection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M03 tenant-bound, resumable census of all known pre-cutover pending authorities.
 *
 * It intentionally never clears dirty flags/deletion preferences and never copies a stronger durable
 * outbox row into sync_outbox. M04/M05 may fence legacy writers only after every producer/transport
 * authority has a verified V2 replacement.
 */
@Singleton
class LegacySyncV2MigrationCoordinator @Inject constructor(
    private val database: AppDatabase,
    private val pendingProtection: SyncPendingProtection = SyncPendingProtection(database),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun prepare(
        scope: SyncWorkScope,
        deletions: SyncDeletionSnapshot,
        now: Long,
    ): LegacySyncMigrationSummary {
        val reconciled = reconcileActiveStrongerProof(scope.organizationId, buildCandidates(scope, deletions))
            .distinctBy { it.sourceKind to it.sourceId }
            .sortedWith(compareBy(LegacySyncMigrationCandidate::sourceKind, LegacySyncMigrationCandidate::sourceId))
        val candidates = reconciled.map { candidate ->
            if (candidate.disposition != LegacySyncMigrationDisposition.MIGRATED) return@map candidate
            val protected = pendingProtection.isProtected(
                scope.organizationId,
                ProtectedSyncKey(candidate.aggregateType, candidate.aggregateId),
            )
            if (protected) candidate else candidate.copy(
                targetKind = LegacySyncMigrationTarget.NONE,
                targetMutationId = null,
                disposition = LegacySyncMigrationDisposition.REQUIRES_REVIEW,
                reasonCode = "M03_PENDING_PROTECTION_UNPROVEN",
            )
        }
        val digest = LegacySyncMigrationPlanner.sourceDigest(candidates)
        val dao = database.syncLegacyMigrationDao()

        val reviewBreakdown = candidates
            .asSequence()
            .filter { it.disposition == LegacySyncMigrationDisposition.REQUIRES_REVIEW }
            .groupingBy { candidate ->
                "sourceKind=${candidate.sourceKind},aggregateType=${candidate.aggregateType}," +
                    "sourceState=${candidate.sourceState},reasonCode=${candidate.reasonCode ?: "<none>"}"
            }
            .eachCount()
        if (reviewBreakdown.isNotEmpty()) {
            android.util.Log.w(
                "M03Diagnostics",
                "M03 review breakdown count=${reviewBreakdown.values.sum()} groups=$reviewBreakdown",
            )
        }

        database.withTransaction {
            val previousState = dao.getState(scope.organizationId, scope.userId)
            candidates.forEach { candidate ->
                val entity = candidate.toEntity(scope, now)
                val inserted = dao.insertEntry(entity)
                if (inserted == -1L) {
                    val existing = requireNotNull(
                        dao.getEntry(scope.organizationId, candidate.sourceKind, candidate.sourceId)
                    ) { "M03 journal identity disappeared after conflict" }
                    check(existing.sourceFingerprint == entity.sourceFingerprint &&
                        existing.syncPrincipalId == scope.userId &&
                        existing.aggregateType == entity.aggregateType &&
                        existing.aggregateId == entity.aggregateId
                    ) {
                        "M03_SOURCE_IDENTITY_CONFLICT:${candidate.sourceKind}:${candidate.sourceId}"
                    }
                    check(dao.updateOutcome(
                        organizationId = scope.organizationId,
                        sourceKind = candidate.sourceKind,
                        sourceId = candidate.sourceId,
                        sourceState = candidate.sourceState,
                        sourceSequence = candidate.sourceSequence,
                        commandBatchId = candidate.commandBatchId,
                        commandOrder = candidate.commandOrder,
                        dependsOnSourceId = candidate.dependsOnSourceId,
                        targetKind = candidate.targetKind,
                        targetMutationId = candidate.targetMutationId,
                        disposition = candidate.disposition,
                        reasonCode = candidate.reasonCode,
                        updatedAt = now,
                    ) == 1) { "M03 journal outcome update failed" }
                }
            }

            val migrated = candidates.count { it.disposition == LegacySyncMigrationDisposition.MIGRATED }
            val receipts = candidates.count { it.disposition == LegacySyncMigrationDisposition.RECEIPT_CONFIRMED }
            val reviews = candidates.count { it.disposition == LegacySyncMigrationDisposition.REQUIRES_REVIEW }
            dao.upsertState(
                SyncLegacyMigrationStateEntity(
                    organizationId = scope.organizationId,
                    syncPrincipalId = scope.userId,
                    phase = if (reviews == 0) "PREPARED" else "REQUIRES_REVIEW",
                    sourceCount = candidates.size,
                    migratedCount = migrated,
                    receiptConfirmedCount = receipts,
                    reviewCount = reviews,
                    sourceDigest = digest,
                    // M03 does not claim M04/M05 producer/transport cutover.
                    legacyWritesFenced = false,
                    startedAt = previousState?.startedAt ?: now,
                    updatedAt = now,
                    completedAt = now,
                )
            )
        }

        return LegacySyncMigrationSummary(
            sourceCount = candidates.size,
            migratedCount = candidates.count { it.disposition == LegacySyncMigrationDisposition.MIGRATED },
            receiptConfirmedCount = candidates.count { it.disposition == LegacySyncMigrationDisposition.RECEIPT_CONFIRMED },
            reviewCount = candidates.count { it.disposition == LegacySyncMigrationDisposition.REQUIRES_REVIEW },
            sourceDigest = digest,
            legacyWritesFenced = false,
        )
    }

    private fun buildCandidates(
        scope: SyncWorkScope,
        deletions: SyncDeletionSnapshot,
    ): List<LegacySyncMigrationCandidate> = buildList {
        addAll(financialCandidates(scope.organizationId))
        addAll(inventoryMovementCandidates(scope.organizationId))
        addAll(inventoryCostCandidates(scope.organizationId))
        addAll(partyRoleOutboxCandidates(scope.organizationId))
        addAll(optimalCandidates(scope.organizationId))
        addAll(dirtyRowCandidates(scope.organizationId))
        addAll(deletionCandidates(scope.organizationId, deletions))
    }

    private fun financialCandidates(org: String): List<LegacySyncMigrationCandidate> = query(
        """
        SELECT event_id, write_id, aggregate_id, sequence, operation_type, sync_state, created_at
        FROM financial_outbox WHERE organization_id = ?
        """.trimIndent(), arrayOf(org)
    ) { c ->
        val operation = c.string("operation_type")
        val aggregate = if (operation.startsWith("PAYMENT_")) "PAYMENT" else "INVOICE"
        val identity = "$operation:${c.string("write_id")}" 
        strongerCandidate(
            sourceKind = "FINANCIAL_OUTBOX",
            sourceId = c.string("event_id"),
            aggregateType = aggregate,
            aggregateId = c.string("aggregate_id"),
            sourceState = c.string("sync_state"),
            businessIdentity = identity,
            sourceSequence = c.longOrNull("sequence"),
            createdAt = c.long("created_at"),
        )
    }

    private fun inventoryMovementCandidates(org: String): List<LegacySyncMigrationCandidate> = query(
        """
        SELECT o.id, o.command_id, o.idempotency_key, o.movement_id, o.sync_state, o.created_at,
               m.idempotency_key AS movement_idempotency_key, m.posting_group_id
        FROM inventory_stock_outbox o
        LEFT JOIN inventory_movements m ON m.id = o.movement_id
        WHERE o.organization_id = ?
        """.trimIndent(), arrayOf(org)
    ) { c ->
        val identity = c.stringOrNull("movement_idempotency_key")?.takeIf(String::isNotBlank)
            ?: c.stringOrNull("idempotency_key")?.takeIf(String::isNotBlank)
        if (identity == null) {
            reviewCandidate(
                "INVENTORY_STOCK_OUTBOX", c.string("id"), "INVENTORY_MOVEMENT", c.string("movement_id"),
                c.string("sync_state"), c.string("command_id"), c.long("created_at"),
                reason = "M03_STRONGER_IDENTITY_MISSING",
            )
        } else {
            strongerCandidate(
                sourceKind = "INVENTORY_STOCK_OUTBOX",
                sourceId = c.string("id"),
                aggregateType = "INVENTORY_MOVEMENT",
                aggregateId = c.string("movement_id"),
                sourceState = c.string("sync_state"),
                businessIdentity = identity,
                sourceSequence = c.long("created_at").coerceAtLeast(1L),
                commandBatchId = c.stringOrNull("posting_group_id"),
                createdAt = c.long("created_at"),
            )
        }
    }

    private fun inventoryCostCandidates(org: String): List<LegacySyncMigrationCandidate> = query(
        """
        SELECT o.id, o.command_id, o.cost_revision_id, o.sync_state, o.created_at,
               r.idempotency_key, r.cost_sequence
        FROM inventory_cost_outbox o
        LEFT JOIN inventory_cost_revisions r
          ON r.cost_revision_id = o.cost_revision_id AND r.organization_id = o.organization_id
        WHERE o.organization_id = ?
        """.trimIndent(), arrayOf(org)
    ) { c ->
        val identity = c.stringOrNull("idempotency_key")?.takeIf(String::isNotBlank)
        if (identity == null) {
            reviewCandidate(
                "INVENTORY_COST_OUTBOX", c.string("id"), "INVENTORY_COST_REVISION", c.string("cost_revision_id"),
                c.string("sync_state"), c.string("command_id"), c.long("created_at"),
                reason = "M03_STRONGER_IDENTITY_MISSING",
            )
        } else {
            strongerCandidate(
                sourceKind = "INVENTORY_COST_OUTBOX",
                sourceId = c.string("id"),
                aggregateType = "INVENTORY_COST_REVISION",
                aggregateId = c.string("cost_revision_id"),
                sourceState = c.string("sync_state"),
                businessIdentity = identity,
                sourceSequence = c.longOrNull("cost_sequence") ?: c.long("created_at").coerceAtLeast(1L),
                createdAt = c.long("created_at"),
            )
        }
    }

    private fun partyRoleOutboxCandidates(org: String): List<LegacySyncMigrationCandidate> = query(
        """
        SELECT id, operation_id, aggregate_type, aggregate_id, payload_json, state, created_at
        FROM party_sync_outbox WHERE aggregate_type = 'ROLE'
        """.trimIndent(), emptyArray()
    ) { c ->
        val partyId = c.string("aggregate_id")
        val role = runCatching {
            json.parseToJsonElement(c.string("payload_json")).jsonObject["role"]?.jsonPrimitive?.content
        }.getOrNull()?.takeIf(String::isNotBlank)
        val belongsToOrg = role != null && scalarLong(
            "SELECT COUNT(*) FROM party_roles WHERE organization_id = ? AND party_id = ? AND role = ?",
            arrayOf(org, partyId, role),
        ) > 0L
        if (!belongsToOrg || role == null) return@query null
        val state = c.string("state")
        LegacySyncMigrationCandidate(
            sourceKind = "PARTY_SYNC_OUTBOX",
            sourceId = c.string("id"),
            aggregateType = "PARTY_ROLE",
            aggregateId = "$partyId:$role",
            sourceState = state,
            businessIdentity = c.string("operation_id"),
            sourceSequence = c.long("created_at").coerceAtLeast(1L),
            targetKind = LegacySyncMigrationTarget.STRONGER_SOURCE,
            targetMutationId = c.string("operation_id"),
            disposition = LegacySyncMigrationPlanner.dispositionForState(state),
            reasonCode = stateReason(state),
            createdAt = c.long("created_at"),
        )
    }.filterNotNull()

    private fun optimalCandidates(org: String): List<LegacySyncMigrationCandidate> = query(
        """
        SELECT event_id, aggregate_type, aggregate_id, idempotency_key, sequence, status, created_at
        FROM optimal_outbox WHERE organization_id = ?
        """.trimIndent(), arrayOf(org)
    ) { c ->
        val aggregate = when (c.string("aggregate_type").uppercase()) {
            "VEHICLE" -> "OPTIMAL_VEHICLE"
            "MAINTENANCE" -> "OPTIMAL_MAINTENANCE"
            "FOLLOW_UP" -> "OPTIMAL_FOLLOW_UP"
            else -> null
        }
        val identity = c.stringOrNull("idempotency_key")?.takeIf(String::isNotBlank)
        if (aggregate == null || identity == null) {
            reviewCandidate(
                "OPTIMAL_OUTBOX", c.string("event_id"), aggregate ?: "OPTIMAL_UNKNOWN",
                c.string("aggregate_id"), c.string("status"), identity.orEmpty(), c.long("created_at"),
                reason = if (aggregate == null) "M03_OPTIMAL_AGGREGATE_UNKNOWN" else "M03_STRONGER_IDENTITY_MISSING",
                sourceSequence = c.longOrNull("sequence"),
            )
        } else {
            strongerCandidate(
                "OPTIMAL_OUTBOX", c.string("event_id"), aggregate, c.string("aggregate_id"),
                c.string("status"), identity, c.longOrNull("sequence"), createdAt = c.long("created_at"),
            )
        }
    }

    private fun dirtyRowCandidates(org: String): List<LegacySyncMigrationCandidate> = buildList {
        addAll(dirtyWithOrg("DIRTY_PARTY_IDENTITY", "PARTY_IDENTITY",
            "SELECT DISTINCT c.id AS source_id, c.id AS aggregate_id, c.createdAt AS created_at FROM clients c JOIN party_roles r ON r.party_id=c.id WHERE c.isDirty=1 AND r.organization_id=?", org))
        addAll(dirtyWithOrg("DIRTY_PARTY_ROLE", "PARTY_ROLE",
            "SELECT id AS source_id, party_id || ':' || role AS aggregate_id, created_at FROM party_roles WHERE dirty=1 AND organization_id=?", org))
        addAll(dirtyWithOrg("DIRTY_CUSTOMER_PROFILE", "CUSTOMER_PROFILE",
            "SELECT party_id AS source_id, party_id AS aggregate_id, updated_at AS created_at FROM customer_profiles WHERE dirty=1 AND organization_id=?", org))
        addAll(dirtyWithOrg("DIRTY_SUPPLIER_PROFILE", "SUPPLIER_PROFILE",
            "SELECT party_id AS source_id, party_id AS aggregate_id, updated_at AS created_at FROM supplier_profiles WHERE dirty=1 AND organization_id=?", org))
        addAll(dirtyWithOrg("DIRTY_INVOICE", "INVOICE",
            "SELECT id AS source_id, id AS aggregate_id, createdAt AS created_at FROM invoices WHERE isDirty=1 AND organization_id=?", org))
        addAll(dirtyWithOrg("DIRTY_INVOICE_ITEM", "INVOICE",
            "SELECT li.id AS source_id, li.invoiceId AS aggregate_id, i.createdAt AS created_at FROM invoice_items li JOIN invoices i ON i.id=li.invoiceId WHERE li.isDirty=1 AND i.organization_id=?", org))
        addAll(dirtyWithOrg("DIRTY_PAYMENT", "PAYMENT",
            "SELECT p.id AS source_id, p.id AS aggregate_id, p.paidAt AS created_at FROM payments p JOIN invoices i ON i.id=p.invoiceId WHERE p.isDirty=1 AND i.organization_id=?", org))
        addAll(dirtyWithOrg("DIRTY_CLIENT_CREDIT", "CLIENT_CREDIT",
            "SELECT DISTINCT cc.id AS source_id, cc.id AS aggregate_id, cc.createdAt AS created_at FROM client_credits cc JOIN party_roles r ON r.party_id=cc.clientId WHERE cc.isDirty=1 AND r.organization_id=?", org))
        addAll(dirtyWithOrg("DIRTY_EDUCATIONAL_CONTENT", "EDUCATIONAL_CONTENT",
            "SELECT topic_id AS source_id, topic_id AS aggregate_id, updated_at AS created_at FROM educational_topics WHERE is_dirty=1 AND organization_id=?", org))
        addAll(dirtyWithOrg("DIRTY_TEAM_OBSERVATION", "TEAM_OBSERVATION",
            "SELECT observation_id AS source_id, observation_id AS aggregate_id, updated_at AS created_at FROM team_observations WHERE is_dirty=1 AND organization_id=?", org))
        addAll(dirtyWithOrg("DIRTY_ORGANIZATION_SETTINGS", "ORGANIZATION_SETTINGS",
            "SELECT organization_id AS source_id, organization_id AS aggregate_id, updated_at AS created_at FROM organization_settings_local WHERE is_dirty=1 AND organization_id=?", org))

        // Historical tables have no tenant column. A matching durable V2 outbox row for the active
        // organization is accepted as tenant proof; otherwise the row remains preserved for review.
        addAll(unscopedDirty(org, "DIRTY_INVENTORY_ITEM_UNSCOPED", "INVENTORY_ITEM",
            "SELECT id AS source_id, id AS aggregate_id, createdAt AS created_at FROM inventory_items WHERE isDirty=1"))
        addAll(unscopedDirty(org, "DIRTY_EXPENSE_UNSCOPED", "EXPENSE",
            "SELECT id AS source_id, id AS aggregate_id, date AS created_at FROM expenses WHERE isDirty=1"))
    }

    private fun dirtyWithOrg(sourceKind: String, aggregateType: String, sql: String, org: String) =
        query(sql, arrayOf(org)) { c ->
            resolveGenericCandidate(
                org = org,
                sourceKind = sourceKind,
                sourceId = c.string("source_id"),
                aggregateType = aggregateType,
                aggregateId = c.string("aggregate_id"),
                sourceState = "DIRTY",
                createdAt = c.long("created_at"),
                missingReason = "M03_DIRTY_WITHOUT_DURABLE_INTENT",
            )
        }

    private fun unscopedDirty(org: String, sourceKind: String, aggregateType: String, sql: String) =
        query(sql, emptyArray()) { c ->
            resolveGenericCandidate(
                org = org,
                sourceKind = sourceKind,
                sourceId = c.string("source_id"),
                aggregateType = aggregateType,
                aggregateId = c.string("aggregate_id"),
                sourceState = "DIRTY",
                createdAt = c.long("created_at"),
                missingReason = "M03_ORG_SCOPE_UNPROVEN",
            )
        }

    private fun deletionCandidates(org: String, deletions: SyncDeletionSnapshot): List<LegacySyncMigrationCandidate> {
        val specs = listOf(
            Triple("DELETION_PREF_CLIENT", "PARTY_IDENTITY", deletions.clientIds),
            Triple("DELETION_PREF_INVOICE", "INVOICE", deletions.invoiceIds),
            Triple("DELETION_PREF_INVENTORY", "INVENTORY_ITEM", deletions.inventoryIds),
            Triple("DELETION_PREF_EXPENSE", "EXPENSE", deletions.expenseIds),
            Triple("DELETION_PREF_CATEGORY", "CATEGORY", deletions.categoryIds),
            Triple("DELETION_PREF_COMMISSION", "COMMISSION_PAYMENT", deletions.commissionIds),
            Triple("DELETION_PREF_UNIT", "INVENTORY_UNIT", deletions.unitIds),
            Triple("DELETION_PREF_BUDGET", "BUDGET", deletions.budgetIds),
            Triple("DELETION_PREF_RECONCILIATION", "CASH_RECONCILIATION", deletions.reconciliationIds),
        )
        return specs.flatMap { (kind, aggregate, ids) ->
            ids.map { id ->
                resolveGenericCandidate(
                    org, kind, id, aggregate, id, "PENDING_DELETE", 0L,
                    missingReason = if (aggregate in setOf("INVOICE", "EXPENSE", "COMMISSION_PAYMENT", "CASH_RECONCILIATION")) {
                        "M03_DELETE_REQUIRES_DOMAIN_COMMAND"
                    } else {
                        "M03_DELETE_WITHOUT_DURABLE_INTENT"
                    },
                )
            }
        }
    }

    private fun resolveGenericCandidate(
        org: String,
        sourceKind: String,
        sourceId: String,
        aggregateType: String,
        aggregateId: String,
        sourceState: String,
        createdAt: Long,
        missingReason: String,
    ): LegacySyncMigrationCandidate {
        val target = queryOne(
            """
            SELECT mutation_id, state, local_sequence, command_batch_id, command_order, depends_on_mutation_id
            FROM sync_outbox
            WHERE organization_id=? AND aggregate_type=? AND aggregate_id=?
            ORDER BY local_sequence DESC LIMIT 1
            """.trimIndent(), arrayOf(org, aggregateType, aggregateId)
        ) { c ->
            UnifiedTarget(
                mutationId = c.string("mutation_id"),
                state = c.string("state"),
                localSequence = c.longOrNull("local_sequence"),
                commandBatchId = c.stringOrNull("command_batch_id"),
                commandOrder = c.intOrNull("command_order"),
                dependsOn = c.stringOrNull("depends_on_mutation_id"),
            )
        }
        if (target == null) {
            return reviewCandidate(
                sourceKind, sourceId, aggregateType, aggregateId, sourceState, sourceId, createdAt,
                reason = missingReason,
            )
        }
        val disposition = LegacySyncMigrationPlanner.dispositionForState(target.state)
        return LegacySyncMigrationCandidate(
            sourceKind = sourceKind,
            sourceId = sourceId,
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            sourceState = sourceState,
            businessIdentity = sourceId,
            sourceSequence = target.localSequence,
            commandBatchId = target.commandBatchId,
            commandOrder = target.commandOrder,
            dependsOnSourceId = target.dependsOn,
            targetKind = LegacySyncMigrationTarget.UNIFIED_OUTBOX,
            targetMutationId = target.mutationId,
            disposition = disposition,
            reasonCode = if (disposition == LegacySyncMigrationDisposition.REQUIRES_REVIEW) "M03_TARGET_REQUIRES_REVIEW" else null,
            createdAt = createdAt,
        )
    }

    /**
     * A dirty mirror may predate M03 while the authoritative V2 producer already owns its durable
     * intent in a specialized outbox. Reuse only ACTIVE/PENDING evidence; old ACK/SYNCED rows are
     * deliberately not used because they could belong to a pre-V2 transport and must never authorize
     * an empty-bootstrap prune. Candidate identity/fingerprint is preserved; only delivery authority
     * metadata is upgraded.
     */
    private fun reconcileActiveStrongerProof(
        org: String,
        source: List<LegacySyncMigrationCandidate>,
    ): List<LegacySyncMigrationCandidate> {
        val activeEvidence = source
            .asSequence()
            .filter { it.targetKind != LegacySyncMigrationTarget.NONE }
            .filter { it.disposition == LegacySyncMigrationDisposition.MIGRATED }
            .groupBy { it.aggregateType to it.aggregateId }
            .mapValues { (_, rows) -> rows.maxByOrNull { it.sourceSequence ?: Long.MIN_VALUE }!! }

        return source.map { candidate ->
            if (candidate.disposition != LegacySyncMigrationDisposition.REQUIRES_REVIEW ||
                candidate.reasonCode !in SAFE_MISSING_INTENT_REASONS
            ) return@map candidate

            val proof = activeEvidence[candidate.aggregateType to candidate.aggregateId]
                ?: activePaymentFinancialProof(org, candidate)
                ?: return@map candidate

            candidate.copy(
                sourceSequence = proof.sourceSequence,
                commandBatchId = proof.commandBatchId,
                commandOrder = proof.commandOrder,
                dependsOnSourceId = proof.dependsOnSourceId,
                targetKind = proof.targetKind,
                targetMutationId = proof.targetMutationId,
                disposition = LegacySyncMigrationDisposition.MIGRATED,
                reasonCode = null,
            )
        }
    }

    private fun activePaymentFinancialProof(
        org: String,
        candidate: LegacySyncMigrationCandidate,
    ): LegacySyncMigrationCandidate? {
        if (candidate.sourceKind != "DIRTY_PAYMENT") return null
        val invoiceId = queryOne(
            "SELECT invoiceId FROM payments WHERE id=?",
            arrayOf(candidate.sourceId),
        ) { c -> c.string("invoiceId") } ?: return null

        return query(
            """
            SELECT event_id, write_id, aggregate_id, sequence, operation_type, sync_state, created_at, payload
            FROM financial_outbox
            WHERE organization_id=? AND aggregate_id=? AND operation_type LIKE 'PAYMENT_%'
            ORDER BY sequence DESC
            """.trimIndent(), arrayOf(org, invoiceId)
        ) { c ->
            val state = c.string("sync_state")
            if (LegacySyncMigrationPlanner.dispositionForState(state) != LegacySyncMigrationDisposition.MIGRATED) {
                return@query null
            }
            val paymentId = runCatching {
                json.parseToJsonElement(c.string("payload")).jsonObject["paymentId"]?.jsonPrimitive?.content
            }.getOrNull()
            if (paymentId != candidate.sourceId) return@query null
            val operation = c.string("operation_type")
            strongerCandidate(
                sourceKind = "FINANCIAL_OUTBOX",
                sourceId = c.string("event_id"),
                aggregateType = "PAYMENT",
                aggregateId = c.string("aggregate_id"),
                sourceState = state,
                businessIdentity = "$operation:${c.string("write_id")}",
                sourceSequence = c.longOrNull("sequence"),
                createdAt = c.long("created_at"),
            )
        }.filterNotNull().firstOrNull()
    }

    private fun strongerCandidate(
        sourceKind: String,
        sourceId: String,
        aggregateType: String,
        aggregateId: String,
        sourceState: String,
        businessIdentity: String,
        sourceSequence: Long? = null,
        commandBatchId: String? = null,
        commandOrder: Int? = null,
        dependsOnSourceId: String? = null,
        createdAt: Long,
    ): LegacySyncMigrationCandidate {
        val disposition = LegacySyncMigrationPlanner.dispositionForState(sourceState)
        return LegacySyncMigrationCandidate(
            sourceKind, sourceId, aggregateType, aggregateId, sourceState, businessIdentity,
            sourceSequence, commandBatchId, commandOrder, dependsOnSourceId,
            LegacySyncMigrationTarget.STRONGER_SOURCE,
            UnifiedStrongerBridgeRegistry.stableMutationId(aggregateType, businessIdentity),
            disposition,
            stateReason(sourceState),
            createdAt,
        )
    }

    private fun reviewCandidate(
        sourceKind: String,
        sourceId: String,
        aggregateType: String,
        aggregateId: String,
        sourceState: String,
        businessIdentity: String,
        createdAt: Long,
        reason: String,
        sourceSequence: Long? = null,
    ) = LegacySyncMigrationCandidate(
        sourceKind = sourceKind,
        sourceId = sourceId,
        aggregateType = aggregateType,
        aggregateId = aggregateId,
        sourceState = sourceState,
        businessIdentity = businessIdentity,
        sourceSequence = sourceSequence,
        targetKind = LegacySyncMigrationTarget.NONE,
        disposition = LegacySyncMigrationDisposition.REQUIRES_REVIEW,
        reasonCode = reason,
        createdAt = createdAt,
    )

    private fun stateReason(state: String): String? = when (state.trim().uppercase()) {
        "REQUIRES_REVIEW", "REJECTED", "BLOCKED" -> "M03_SOURCE_${state.trim().uppercase()}"
        else -> null
    }

    private fun <T> query(sql: String, args: Array<Any?>, mapper: (Cursor) -> T): List<T> {
        val db = database.openHelper.readableDatabase
        return db.query(SimpleSQLiteQuery(sql, args)).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(mapper(cursor))
            }
        }
    }

    private fun <T> queryOne(sql: String, args: Array<Any?>, mapper: (Cursor) -> T): T? =
        query(sql, args, mapper).firstOrNull()

    private fun scalarLong(sql: String, args: Array<Any?>): Long =
        queryOne(sql, args) { it.getLong(0) } ?: 0L

    private fun Cursor.index(name: String): Int = getColumnIndexOrThrow(name)
    private fun Cursor.string(name: String): String = getString(index(name)) ?: ""
    private fun Cursor.stringOrNull(name: String): String? = index(name).let { i -> if (isNull(i)) null else getString(i) }
    private fun Cursor.long(name: String): Long = getLong(index(name))
    private fun Cursor.longOrNull(name: String): Long? = index(name).let { i -> if (isNull(i)) null else getLong(i) }
    private fun Cursor.intOrNull(name: String): Int? = index(name).let { i -> if (isNull(i)) null else getInt(i) }

    private fun LegacySyncMigrationCandidate.toEntity(scope: SyncWorkScope, now: Long) =
        SyncLegacyMigrationEntryEntity(
            organizationId = scope.organizationId,
            sourceKind = sourceKind,
            sourceId = sourceId,
            syncPrincipalId = scope.userId,
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            sourceState = sourceState,
            businessIdentity = businessIdentity,
            sourceSequence = sourceSequence,
            commandBatchId = commandBatchId,
            commandOrder = commandOrder,
            dependsOnSourceId = dependsOnSourceId,
            targetKind = targetKind,
            targetMutationId = targetMutationId,
            disposition = disposition,
            reasonCode = reasonCode,
            sourceFingerprint = fingerprint,
            createdAt = createdAt.takeIf { it > 0L } ?: now,
            updatedAt = now,
        )

    private data class UnifiedTarget(
        val mutationId: String,
        val state: String,
        val localSequence: Long?,
        val commandBatchId: String?,
        val commandOrder: Int?,
        val dependsOn: String?,
    )

    private companion object {
        val SAFE_MISSING_INTENT_REASONS = setOf(
            "M03_DIRTY_WITHOUT_DURABLE_INTENT",
            "M03_ORG_SCOPE_UNPROVEN",
        )
    }
}

data class LegacySyncMigrationSummary(
    val sourceCount: Int,
    val migratedCount: Int,
    val receiptConfirmedCount: Int,
    val reviewCount: Int,
    val sourceDigest: String,
    val legacyWritesFenced: Boolean,
)
