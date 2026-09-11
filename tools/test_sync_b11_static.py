#!/usr/bin/env python3
from pathlib import Path
engine=Path('data/sync/src/main/kotlin/com/verto/app/data/sync/push/UnifiedSyncConflictEngine.kt').read_text()
service=Path('data/sync/src/main/kotlin/com/verto/app/data/sync/conflict/SyncConflictReviewService.kt').read_text()
frozen=Path('data/sync/src/main/kotlin/com/verto/app/data/sync/FrozenMutationStore.kt').read_text()
ui=Path('app/src/main/kotlin/com/verto/app/ui/components/NavigationDrawerContent.kt').read_text()
auth=Path('app/src/main/kotlin/com/verto/app/feature/sync/di/SyncConflictReviewModule.kt').read_text()
push=Path('data/sync/src/main/kotlin/com/verto/app/data/sync/push/UnifiedSyncPushEngine.kt').read_text()
migration=Path('data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations99To100.kt').read_text()
appdb=Path('data/database/src/main/kotlin/com/verto/app/data/local/AppDatabase.kt').read_text()
recovery=Path('data/sync/src/main/kotlin/com/verto/app/data/sync/recovery/UnifiedSyncRecoveryRegistry.kt').read_text()
digest=Path('data/sync/src/main/kotlin/com/verto/app/data/sync/recovery/OutboxIdentityDigest.kt').read_text()
assert 'SERVER_WINS_SAFE ->' not in engine
assert 'LOCAL_RETRY_WITH_NEW_BASE ->' not in engine
assert 'applier.apply' not in engine
assert 'terminalState = "REQUIRES_REVIEW"' in engine
assert 'DOMAIN_CORRECTION_REQUIRED' in engine
assert 'PROVEN_CONFLICT' in engine and 'OUTCOME_UNKNOWN' in engine
assert 'markReviewSupersededWithProof' in service and 'RESOLVED_SERVER_ACCEPTED' in service
assert 'baseVersion = expectedServerVersion' in service
assert 'payloadJson = old.payloadJson' in service
assert 'markReviewSupersededPendingProof' in service and 'WAITING_REPLACEMENT_RECEIPT' in service
assert 'captureUnified(replacement, supersedesMutationId = old.mutationId, useUnresolvedPredecessor = false)' in service
assert 'UnifiedSyncConflictPolicy.OPTIMISTIC_VERSION' in service
assert 'DOMAIN_CORRECTION_REQUIRED' in service
assert 'finalizeReplacementProofChain' in frozen and 'RESOLVED_REPLACEMENT_PROVED' in frozen
assert 'resolvedPredecessorServerVersion' in frozen and 'RESOLVED_REPLACEMENT_PROVED' in frozen
assert 'replacementParentSequence' in push and 'SUPERSEDED_PARENT_AWAITING_REPLACEMENT_PROOF' in push
assert 'AUTHORIZED_USER_DECISION_PRESERVE_NEWER_LOCAL' in service
assert 'resolveMatchingEchoConflict' in frozen and 'RESOLVED_MATCHING_ECHO' in frozen
assert 'settingsOrgData' in auth and 'CONFLICT_RESOLUTION_PERMISSION_DENIED' in auth
for text in ['سبب التعارض','بصمة المحلي','بصمة الخادم','النسخة المحلية','نسخة الخادم','اعتماد نسخة الخادم','إعادة إرسال تعديلي']:
    assert text in ui, text
assert '[REDACTED]' in service

assert 'SUPERSEDED_PENDING_PROOF' in migration and 'SUPERSEDED_WITH_PROOF' in migration
assert 'installB11ConflictIntegrityGuards(db)' in migration
assert 'installB11ConflictIntegrityGuards(db)' in appdb
assert 'local_base_version' in migration and 'localBaseVersion = outbox.baseVersion' in engine
assert "receipt.status != SyncReceiptStatus.CONFLICT" in push
assert 'SUPERSEDED_PENDING_PROOF' in recovery and 'SUPERSEDED_PENDING_PROOF' in digest
print('B11_STATIC_CONTRACT: PASS')
