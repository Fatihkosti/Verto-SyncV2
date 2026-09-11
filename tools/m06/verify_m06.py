#!/usr/bin/env python3
from pathlib import Path
import json, re, sqlite3, sys

ROOT = Path(__file__).resolve().parents[2]
checks = []

def txt(rel):
    return (ROOT / rel).read_text(encoding='utf-8')

def check(name, condition, detail):
    checks.append({'name': name, 'pass': bool(condition), 'detail': detail})

def string_set(block):
    return set(re.findall(r'"([A-Z][A-Z0-9_]+)"', block))

agg_path='data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncAggregateRegistry.kt'
pull_reg_path='data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedSyncPullRegistry.kt'
pull_engine_path='data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedSyncPullEngine.kt'
contract_path='data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncContract.kt'
applier_path='data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedSyncChangeApplier.kt'
strong_applier_path='data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedStrongerSyncChangeApplier.kt'
recovery_reg_path='data/sync/src/main/kotlin/com/verto/app/data/sync/recovery/UnifiedSyncRecoveryRegistry.kt'
recovery_engine_path='data/sync/src/main/kotlin/com/verto/app/data/sync/recovery/UnifiedSyncRecoveryEngine.kt'
snapshot_path='data/sync/src/main/kotlin/com/verto/app/data/sync/recovery/UnifiedSyncSnapshotApplier.kt'
dao_path='data/database/src/main/kotlin/com/verto/app/data/local/dao/UnifiedSyncOutboxDao.kt'
migration_path='supabase/migrations/20260908154626_m02_team_observation_visibility_alignment.sql'

agg=txt(agg_path)
pull_reg=txt(pull_reg_path)
pull_engine=txt(pull_engine_path)
contract=txt(contract_path)
applier=txt(applier_path)
strong_applier=txt(strong_applier_path)
recovery_reg=txt(recovery_reg_path)
recovery_engine=txt(recovery_engine_path)
snapshot=txt(snapshot_path)
dao=txt(dao_path)
server=txt(migration_path)

aggregates=set(re.findall(r'\brecord\("([A-Z][A-Z0-9_]+)"', agg))
check('aggregate_registry_35', len(aggregates)==35, f'live registry has {len(aggregates)} aggregate types')
check('team_observation_in_live_registry', 'TEAM_OBSERVATION' in aggregates, 'M02 live contract extension is included')

m=re.search(r'private val directTargets = setOf\((.*?)\n\s*\)', pull_reg, re.S)
direct=string_set(m.group(1)) if m else set()
m=re.search(r'private val owner310 = setOf\((.*?)\n\s*\)', pull_reg, re.S)
owner=string_set(m.group(1)) if m else set()
check('pull_partition_18_17', len(direct)==18 and len(owner)==17 and not direct & owner, f'direct={len(direct)}, owner310={len(owner)}')
check('pull_registry_exact_coverage', direct | owner == aggregates, f'pull covers {len(direct|owner)}/{len(aggregates)}')

recovery=set(re.findall(r'\br\("([A-Z][A-Z0-9_]+)"', recovery_reg))
check('recovery_registry_exact_coverage', recovery==aggregates and len(recovery)==35, f'recovery covers {len(recovery)}/{len(aggregates)}')

# Applier routing is explicitly total: direct routes + the stronger group delegated to owner310 applier.
route_head=applier[applier.find('when (change.aggregateType)'):applier.find('private suspend fun applyPartyIdentity')]
for a in sorted(direct):
    check('direct_applier_'+a.lower(), f'"{a}"' in route_head, f'{a} has an explicit direct pull route')
for a in sorted(owner):
    check('stronger_route_'+a.lower(), f'"{a}"' in route_head and f'"{a}"' in strong_applier, f'{a} is delegated and materialized by stronger applier')

check('whole_page_contract', 'requireValidPullPage(page, scope)' in pull_engine, 'client validates the complete server page contract before commit')
check('transaction_boundary_contract', 'requireCompleteTransactionGroups(page.changes)' in contract and 'endsAtTransactionBoundary' in contract, 'contract requires complete transaction groups and page boundary')
check('nonempty_cursor_must_advance', 'page.changes.isNotEmpty() && page.nextCursor == currentCursor' in pull_engine, 'every non-empty global page must advance the opaque server cursor')
check('empty_page_cannot_advance', 'empty page cannot advance or advertise more data' in pull_engine, 'empty page cannot move cursor')
check('cursor_expiry_forces_recovery', 'CURSOR_EXPIRED' in pull_engine and 'RECOVERY_REQUIRED' in pull_engine, 'expired cursor becomes durable recovery obligation')
check('inbox_anchor_required', 'hasAppliedInboxAnchor' in pull_engine and 'LOCAL_ANCHOR_MISSING' in pull_engine, 'cursor anchor is checked against APPLIED inbox state')
check('page_commit_atomic', 'private suspend fun commitPageAtomically' in pull_engine and 'database.withTransaction {' in pull_engine[pull_engine.find('private suspend fun commitPageAtomically'):], 'domain apply + inbox + cursor commit share one Room transaction')
commit=pull_engine[pull_engine.find('private suspend fun commitPageAtomically'):pull_engine.find('/** Session 309', pull_engine.find('private suspend fun commitPageAtomically'))]
check('apply_before_cursor_advance', commit.find('applier.apply(change)') < commit.find('dao.advanceCursorOrThrow'), 'domain materialization completes before cursor CAS')
check('pending_local_blocks_page', 'guardPendingLocalMutation(change)' in commit and 'PENDING_LOCAL_MUTATION' in pull_engine, 'remote page cannot overwrite unresolved local intent')
check('review_state_counts_as_active', "'REQUIRES_REVIEW'" in dao or '"REQUIRES_REVIEW"' in dao, 'REQUIRES_REVIEW is represented in active outbox semantics')
check('financial_hard_delete_guard', 'UnifiedSyncDeletePolicy.VOID_OR_REVERSE' in pull_reg and 'requires VOID/REVERSE, not hard DELETE' in pull_reg, 'pull has a central defense-in-depth guard against hard financial delete')

check('bootstrap_scope_guard', recovery_engine.count('scopeGuard()') >= 4 and 'guard()' in recovery_engine, 'scope is revalidated around bootstrap network/commit boundaries')
check('bootstrap_pages_durable', 'insertStage(entity)' in recovery_engine and 'database.withTransaction {' in recovery_engine, 'snapshot pages are staged durably and atomically')
check('bootstrap_cutover_atomic', 'snapshotApplier.materializeAndPrune' in recovery_engine and 'dao.installBootstrapCursor' in recovery_engine and 'database.withTransaction {' in recovery_engine[recovery_engine.find('private suspend fun cutover'):], 'snapshot materialization and baseline cursor install share one Room transaction')
check('bootstrap_restart_supported', 'BOOTSTRAP_RESTART_REQUIRED' in recovery_engine and 'startFresh' in recovery_engine, 'expired/invalid bootstrap can restart safely')
check('outbox_digest_guard', 'OutboxIdentityDigest.compute' in snapshot and 'FAIL_RECOVERY_OUTBOX_LOSS' in snapshot, 'recovery asserts pending outbox identity is unchanged by cutover')
check('pending_snapshot_rows_preserved', 'shouldPreservePending(scope, row)' in snapshot, 'snapshot does not overwrite locally pending aggregate state')
check('price_list_authoritative_absence_prune', "aggregate_type='PRICE_LIST'" in recovery_reg and 'DELETE FROM price_list_templates' in recovery_reg, 'bootstrap absence removes stale deleted price-list templates')
check('price_list_prune_preserves_pending', "o.aggregate_type='PRICE_LIST'" in recovery_reg and "'REQUIRES_REVIEW'" in recovery_reg, 'price-list prune excludes all active/review local intents')

# Execute the new PRICE_LIST prune SQL against sqlite to verify actual semantics and placeholder order.
price_match=re.search(r'r\("PRICE_LIST".*?\n\s*"(DELETE FROM price_list_templates .*?)"\),', recovery_reg, re.S)
price_sql=price_match.group(1) if price_match else None
sqlite_ok=False
sqlite_detail='PRICE_LIST prune SQL not found'
if price_sql:
    try:
        c=sqlite3.connect(':memory:')
        c.executescript('''
        CREATE TABLE price_list_templates(id TEXT PRIMARY KEY, organization_id TEXT NOT NULL);
        CREATE TABLE sync_bootstrap_stage(scope_id TEXT, bootstrap_session_id TEXT, aggregate_type TEXT, aggregate_id TEXT);
        CREATE TABLE sync_outbox(organization_id TEXT, aggregate_type TEXT, aggregate_id TEXT, state TEXT);
        ''')
        # Absent authoritative row: stale local row is deleted.
        c.execute("INSERT INTO price_list_templates VALUES('p1','org')")
        c.execute(price_sql, ('scope','session','org'))
        deleted = c.execute("SELECT count(*) FROM price_list_templates WHERE id='p1'").fetchone()[0] == 0
        # Present authoritative row: local row survives prune.
        c.execute("INSERT INTO price_list_templates VALUES('p2','org')")
        c.execute("INSERT INTO sync_bootstrap_stage VALUES('scope','session','PRICE_LIST','p2')")
        c.execute(price_sql, ('scope','session','org'))
        present = c.execute("SELECT count(*) FROM price_list_templates WHERE id='p2'").fetchone()[0] == 1
        # Pending local row: survives even if absent from snapshot.
        c.execute("INSERT INTO price_list_templates VALUES('p3','org')")
        c.execute("INSERT INTO sync_outbox VALUES('org','PRICE_LIST','p3','REQUIRES_REVIEW')")
        c.execute(price_sql, ('scope','session','org'))
        pending = c.execute("SELECT count(*) FROM price_list_templates WHERE id='p3'").fetchone()[0] == 1
        sqlite_ok=deleted and present and pending
        sqlite_detail=f'absent_deleted={deleted}, authoritative_present_preserved={present}, pending_preserved={pending}'
    except Exception as e:
        sqlite_detail=f'{type(e).__name__}: {e}'
check('price_list_prune_sqlite_semantics', sqlite_ok, sqlite_detail)

check('server_pull_transaction_order', 'partition by v.transaction_id' in server and 'transaction_order' in server and 'transaction_size' in server, 'server page exports complete transaction membership metadata')
check('server_page_boundary_true', "'ends_at_transaction_boundary', true" in server, 'server explicitly certifies page transaction boundary')
check('server_bootstrap_revision_lock', 'pg_advisory_xact_lock' in server and 'v_baseline' in server, 'bootstrap baseline is serialized with unified revision authority')
check('server_snapshot_fixed_baseline', 's.updated_revision <= v_baseline' in server, 'bootstrap snapshot is fixed at baseline revision')

cursor_sources = '\n'.join([pull_engine, recovery_engine, contract])
check('no_updated_at_cursor_authority', 'updated_at cursor' not in cursor_sources.lower() and 'cursorToken = updated' not in cursor_sources, 'pull/recovery cursor authority is opaque server cursor, not device updated_at')

failed=[c for c in checks if not c['pass']]
result={'result':'PASS' if not failed else 'FAIL','checks':checks,'passed':len(checks)-len(failed),'failed':len(failed),'aggregateCount':len(aggregates)}
outdir=ROOT/'evidence/m06/verification'
outdir.mkdir(parents=True, exist_ok=True)
(outdir/'M06_STATIC_GATE.json').write_text(json.dumps(result, indent=2, ensure_ascii=False)+'\n', encoding='utf-8')
lines=[]
for c in checks:
    line=f"{'PASS' if c['pass'] else 'FAIL'} {c['name']} - {c['detail']}"
    print(line); lines.append(line)
summary=f"RESULT {result['result']} ({result['passed']}/{len(checks)})"
print(summary); lines.append(summary)
(outdir/'M06_STATIC_GATE.txt').write_text('\n'.join(lines)+'\n', encoding='utf-8')
sys.exit(0 if not failed else 1)
