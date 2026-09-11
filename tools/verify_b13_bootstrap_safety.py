#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
def read(rel): return (ROOT / rel).read_text(encoding='utf-8')

def check(name, ok, detail=''):
    global failures
    if ok:
        print(f'PASS {name}')
    else:
        failures += 1
        print(f'FAIL {name} {detail}'.rstrip())

failures = 0
entity = read('data/database/src/main/kotlin/com/verto/app/data/local/entity/SyncRecoveryEntities.kt')
migration = read('data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations100To101.kt')
catalog = read('data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt')
dao = read('data/database/src/main/kotlin/com/verto/app/data/local/dao/SyncRecoveryDao.kt')
wire = read('data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncBootstrapWire.kt')
remote = read('data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncBootstrapRemote.kt')
seal = read('data/sync/src/main/kotlin/com/verto/app/data/sync/recovery/BootstrapSealPolicy.kt')
manifest = read('data/sync/src/main/kotlin/com/verto/app/data/sync/recovery/RecoveryProtectionManifest.kt')
engine = read('data/sync/src/main/kotlin/com/verto/app/data/sync/recovery/UnifiedSyncRecoveryEngine.kt')
applier = read('data/sync/src/main/kotlin/com/verto/app/data/sync/recovery/UnifiedSyncSnapshotApplier.kt')
manager = read('data/sync/src/main/kotlin/com/verto/app/data/sync/SyncManager.kt')
ports = read('data/sync/src/main/kotlin/com/verto/app/data/sync/SyncManagerPorts.kt')
material = read('data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedRemoteMaterialization.kt')

check('schema-101', 'ROOM_SCHEMA_VERSION: Int = 101' in catalog and 'MIGRATION_100_101' in catalog)
check('recovery-seal-columns', all(x in entity for x in ['expected_snapshot_digest','expected_coverage_json','bootstrap_high_watermark','bootstrap_delta_token','stage_verified_at']))
check('stage-promotion-columns', all(x in entity for x in ['is_tombstone','promotion_state','wait_reason','applied_at']))
check('protection-manifest-table', 'sync_recovery_protection_manifest' in entity and 'combined_sha256' in entity)
check('migration-state-columns', all(x in migration for x in ['expected_snapshot_digest','expected_coverage_json','bootstrap_high_watermark','bootstrap_delta_token','stage_verified_at']))
check('migration-stage-columns', all(x in migration for x in ['is_tombstone','promotion_state','wait_reason','applied_at']))
check('migration-manifest-sha-checks', migration.count('CHECK(length(') >= 11)
check('dao-stage-promotion', 'markStagePromotion' in dao)
check('dao-manifest-persistence', all(x in dao for x in ['upsertProtectionManifest','getProtectionManifest','clearProtectionManifest']))
check('wire-seal', all(x in wire for x in ['snapshot_digest_sha256','coverage_aggregate_types','high_watermark','delta_token']))
check('wire-tombstone', 'is_tombstone' in wire and 'isTombstone' in remote)
check('seal-count', 'rows.size.toLong() != expectedRows' in seal)
check('seal-digest', 'actualDigest != expectedDigest' in seal and 'contentFingerprint' in seal)
check('seal-coverage', 'coverage != requiredCoverage' in seal and 'UnifiedSyncAggregateRegistry.byId.keys.sorted()' in seal)
check('seal-high-watermark', 'highWatermark <= 0L' in seal and 'baselineRevision != highWatermark' in seal)
check('seal-delta-token', 'deltaToken != state.baselineCursor' in seal)
check('durable-staged-state', 'STATE_STAGED = "STAGED"' in engine and 'STATE_STAGED_VERIFIED = "STAGED_VERIFIED"' in engine)
check('seal-atomic-verify', 'database.withTransaction' in engine[engine.index('private suspend fun verifyStaging'):engine.index('private suspend fun captureProtectionManifest')])
check('transient-auth-preserve', 'preserveStaging = true' in engine and 'RecoveryRunOutcome.AUTH_BLOCKED' in engine)
check('cancellation-rethrow', 'if (t is CancellationException) throw t' in engine)
check('manifest-unified-owner', 'FROM sync_outbox' in manifest)
check('manifest-party-owner', 'FROM party_sync_outbox' in manifest)
check('manifest-financial-owner', 'FROM financial_outbox' in manifest)
check('manifest-inventory-stock-owner', 'FROM inventory_stock_outbox' in manifest)
check('manifest-inventory-cost-owner', 'FROM inventory_cost_outbox' in manifest)
check('manifest-optimal-owner', 'FROM optimal_outbox' in manifest)
check('manifest-attachment-owner', 'FROM sync_attachment_transfer' in manifest and 'content' in manifest.lower())
check('manifest-pending-reference', 'FROM sync_pending_reference' in manifest)
check('manifest-frozen-packets', all(x in manifest for x in ['sync_mutation_packet','sync_write_batch','sync_write_batch_member']))
check('manifest-local-content-generation', 'sync_local_generation' in manifest and 'content_hash' in manifest)
check('manifest-storage-class-exact', all(x in manifest for x in ['FIELD_TYPE_INTEGER','FIELD_TYPE_FLOAT','FIELD_TYPE_STRING','FIELD_TYPE_BLOB']))
check('manifest-blob-exact-bytes', 'cursor.getBlob(index)' in manifest)
check('manifest-before-after', all(x in engine for x in ['FAIL_RECOVERY_PROTECTION_MANIFEST_STALE','FAIL_RECOVERY_PROTECTION_HASH_CHANGED','RecoveryProtectionManifest.sameContent']))
check('one-cutover-room-tx', 'snapshotApplier.materializeAndPrune' in engine and 'database.withTransaction' in engine)
check('per-row-protection-recheck', 'for (row in ordered)' in applier and 'shouldPreservePending(scope, row)' in applier)
check('waiting-local', 'STATE_WAITING_LOCAL' in applier and 'PENDING_LOCAL_MUTATION' in applier)
check('applied-stage-state', 'STATE_APPLIED' in applier and 'markStagePromotion' in applier)
check('applied-version-after-domain-apply', applier.index('domainApplier.applySnapshot') < applier.index('recordAppliedVersion'))
check('no-synthetic-bootstrap-revision', 'coerceAtLeast(1L)' not in applier and 'BOOTSTRAP_BASELINE_REVISION_MISSING' in applier)
check('explicit-tombstone-policy', 'FAIL_BOOTSTRAP_TOMBSTONE_POLICY' in applier and 'SyncMutationOperation.DELETE' in applier)
check('no-absence-prune', 'No absence-pruning in B13' in applier and 'pruneSql' not in applier)
check('no-clear-all-tables', 'clearAllTables' not in engine + applier)
check('snapshot-operation-propagated', 'override val operationType: SyncMutationOperation = SyncMutationOperation.UPSERT' in material)
check('m03-promotion-gate-port', 'promotionAllowed: Boolean' in ports and 'promotionAllowed = promotionAllowed' in ports)
check('m03-promotion-blocked', 'RecoveryRunOutcome.PROMOTION_BLOCKED' in manager and 'promotionAllowed = migrationReviewCount == 0' in manager)
check('m03-independent-push-before-block-return', manager.index('val push = v2Engine.pushAvailable(scope)') < manager.index('recoveryPromotionBlockedByMigrationEvidence || recoveryContinuationDeferredByMigrationEvidence'))
check('m03-continuation-does-not-starve-push', 'recoveryContinuationDeferredByMigrationEvidence = true' in manager)
check('m03-no-pull-while-blocked', manager.index('recoveryPromotionBlockedByMigrationEvidence || recoveryContinuationDeferredByMigrationEvidence') < manager.index('val pull = v2Engine.pull(scope.organizationId)'))

if failures:
    print(f'B13_STATIC_GATE=FAIL failures={failures}')
    sys.exit(1)
print('B13_STATIC_GATE=PASS checks=48')
