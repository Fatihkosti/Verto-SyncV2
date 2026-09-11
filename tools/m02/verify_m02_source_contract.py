#!/usr/bin/env python3
from pathlib import Path
import hashlib, json, re, sys
ROOT=Path(__file__).resolve().parents[2]
checks=[]
def check(name, ok, detail=''):
    checks.append({'name':name,'status':'PASS' if ok else 'FAIL','detail':detail})

def txt(p): return (ROOT/p).read_text()
reg=txt('data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncAggregateRegistry.kt')
ids=re.findall(r'\brecord\("([A-Z][A-Z0-9_]*)"', reg)
check('aggregate_registry_exact_35', len(ids)==35 and len(set(ids))==35, f'count={len(ids)} unique={len(set(ids))}')
check('team_registry_contract', 'record("TEAM_OBSERVATION", 1, UnifiedSyncConflictPolicy.SERVER_STATE_MACHINE, UnifiedSyncDeletePolicy.NO_CLIENT_DELETE' in reg)
pullreg=txt('data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedSyncPullRegistry.kt')
check('team_pull_registered', '"TEAM_OBSERVATION"' in pullreg and 'directTargets.size == 18' in pullreg and 'all.size == 35' in pullreg)
applier=txt('data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedSyncChangeApplier.kt')
check('team_pull_materializer', '"TEAM_OBSERVATION" -> applyTeamObservation(change)' in applier and 'database.teamObservationDao().upsertRemote' in applier)
recovery=txt('data/sync/src/main/kotlin/com/verto/app/data/sync/recovery/UnifiedSyncRecoveryRegistry.kt')
check('team_recovery_registered', 'r("TEAM_OBSERVATION", 66' in recovery and 'all.size == 35' in recovery and "aggregate_type='TEAM_OBSERVATION'" in recovery)
check('party_role_recovery_canonical_id', "party_roles.party_id || ':' || party_roles.role" in recovery)
snapshot=txt('data/sync/src/main/kotlin/com/verto/app/data/sync/recovery/UnifiedSyncSnapshotApplier.kt')
check('team_recovery_prune_binding', '"TEAM_OBSERVATION" ->' in snapshot or '"OPTIMAL_VEHICLE", "TEAM_OBSERVATION"' in snapshot)
repo=txt('feature/dashboard/src/main/kotlin/com/verto/app/feature/dashboard/data/observation/RoomTeamObservationRepository.kt')
check('team_domain_outbox_atomic', 'database.withTransaction {' in repo and 'outboxWriter.enqueue(' in repo and 'aggregateType = "TEAM_OBSERVATION"' in repo and 'operationType = "UPSERT"' in repo)
check('team_rollout_owner_fence', 'v2OwnsTransport' in repo and 'entity.copy(isDirty = false)' in repo and 'dao.upsert(entity.copy(isDirty = true))' in repo)
participant=txt('feature/dashboard/src/main/kotlin/com/verto/app/feature/dashboard/data/observation/TeamObservationSyncParticipant.kt')
check('team_legacy_transport_fenced_when_v2_owns', 'if (v2OwnsTransport) return emptyList()' in participant)
pushreg=txt('data/sync/src/main/kotlin/com/verto/app/data/sync/push/UnifiedSyncPushRegistry.kt')
check('team_push_ready', 'aggregateType == "TEAM_OBSERVATION" -> UnifiedSyncPushCoverageStatus.PUSH_READY_SHADOW' in pushreg)
party=txt('data/sync/src/main/kotlin/com/verto/app/data/sync/push/UnifiedSyncPartyPushBridge.kt')
check('party_role_push_canonical', 'aggregateId = "${row.aggregateId}:$role"' in party and 'operationType = SyncMutationOperation.UPSERT' in party)
engine=txt('data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedSyncPullEngine.kt')
check('party_role_echo_canonical', 'canonicalAggregateId' in engine and 'strongerAggregateId' in engine)
m1=ROOT/'supabase/migrations/20260908153803_m02_sync_contract_closeout.sql'
m2=ROOT/'supabase/migrations/20260908154626_m02_team_observation_visibility_alignment.sql'
for p in (m1,m2): check('migration_present_'+p.stem,p.exists(), hashlib.sha256(p.read_bytes()).hexdigest() if p.exists() else '')
check('no_global_v2_activation_in_m02_migrations', all(x not in (m1.read_text()+m2.read_text()).lower() for x in ['production_pruning_enabled=true','drop table','drop function']))
result={'verdict':'PASS' if all(c['status']=='PASS' for c in checks) else 'FAIL','checks':checks}
out=ROOT/'evidence/m02/source_contract_verification.json'; out.parent.mkdir(parents=True,exist_ok=True); out.write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n')
print(json.dumps(result,ensure_ascii=False,indent=2))
sys.exit(0 if result['verdict']=='PASS' else 1)
