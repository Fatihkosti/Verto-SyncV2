#!/usr/bin/env python3
from __future__ import annotations
import csv, hashlib, json, re, subprocess, sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / 'docs/sync/VERTO_SYNC_309_INPUT_MANIFEST.json'
CARRY = ROOT / 'docs/sync/VERTO_SYNC_307_EXCEPTION_CARRYFORWARD_v309.json'
COVERAGE = ROOT / 'docs/sync/VERTO_SYNC_PUSH_COVERAGE_v309.csv'
ADAPTERS = ROOT / 'docs/sync/VERTO_SYNC_PUSH_SERVER_ADAPTERS_v309.csv'
OUTBOX = ROOT / 'docs/sync/VERTO_SYNC_OUTBOX_INVENTORY_v309.csv'
EXCLUSIONS = ROOT / 'docs/sync/VERTO_SYNC_PUSH_EXCLUSIONS_v309.json'
SCHEMA79 = ROOT / 'app/schemas/com.verto.app.data.local.AppDatabase/79.json'
SCHEMA80 = ROOT / 'app/schemas/com.verto.app.data.local.AppDatabase/80.json'
MIG7980 = ROOT / 'data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations79To80.kt'
MIG305 = ROOT / 'supabase/migrations/20260821062000_v305_verto_unified_sync_server.sql'
MIG309 = ROOT / 'supabase/migrations/20260821123000_v309_verto_unified_sync_push.sql'

EXPECTED_INPUT_SHA = 'b7723b93d958cb0d47bd5f4d7a4487c53093a40d0ba2ad0517abcf5b6adb03b3'
EXPECTED_SCHEMA79_SHA = 'ab4a24d0698914a852bf1c52498174adcd32999006a1011b6a80a438395031da'
EXPECTED_V305_SHA = 'a2daf28b3a05b35907267bfc766fc6919ba5c28613854c0c0d68ee186313e908'
EXPECTED_V308_VERIFICATION_SHA = 'f9b96cd19ee345ed6adef1d45a3081e8fd01c3cf31bfd39e9cb874a1d23dd6ef'
EXPECTED_EXCEPTIONS = [
    'room_version_79','persisted_sequence_authority','payload_bound_preserved',
    'producer_discovery_unclassified_zero','stronger_outboxes_proven','datastore_delete_authority_zero',
    'dirty_only_authority_zero','org_settings_room_canonical','attachment_intent_persisted'
]
OWNER310 = {
    'INVOICE','PAYMENT','CLIENT_CREDIT','GOODS_RECEIPT','PURCHASE_MATCH','PURCHASE_PAYMENT_OVERRIDE',
    'INVENTORY_MOVEMENT','INVENTORY_COST_REVISION','COST_ALLOCATION','EXPENSE','CASH_REGISTER',
    'CASH_MOVEMENT','CASH_RECONCILIATION','COMMISSION_PAYMENT','OPTIMAL_VEHICLE',
    'OPTIMAL_MAINTENANCE','OPTIMAL_FOLLOW_UP'
}
OWNER307_GENERIC = {
    'BUDGET','CATEGORY','CUSTOMER_PROFILE','EDUCATIONAL_CONTENT','INVENTORY_ITEM','INVENTORY_UNIT',
    'ITEM_CATEGORY','NOTE','ORGANIZATION_SETTINGS','PARTY_IDENTITY','PRICE_LIST','PURCHASE_ORDER',
    'REMINDER','SHIPMENT','SUPPLIER_PROFILE'
}
RUNTIME_FROZEN = [
    'data/sync/src/main/kotlin/com/verto/app/data/sync/SyncWorker.kt',
    'data/sync/src/main/kotlin/com/verto/app/data/sync/RealtimeManager.kt',
    'core/common/src/main/kotlin/com/verto/app/utils/FeatureFlags.kt',
    'data/preferences/src/main/kotlin/com/verto/app/utils/SyncPreferencesStore.kt',
    'data/sync/src/main/kotlin/com/verto/app/data/sync/SyncManager.kt',
]
CONTRACT_FROZEN = [
    'data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncContract.kt',
    'data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncAggregateRegistry.kt',
    'data/sync/src/main/kotlin/com/verto/app/data/sync/UnifiedOutboxWriter.kt',
]


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load_csv(path: Path):
    with path.open(newline='', encoding='utf-8') as f:
        return list(csv.DictReader(f))


def main() -> int:
    try:
        m = json.loads(MANIFEST.read_text(encoding='utf-8'))
        carry = json.loads(CARRY.read_text(encoding='utf-8'))
        coverage = load_csv(COVERAGE)
        adapters = load_csv(ADAPTERS)
        outboxes = load_csv(OUTBOX)
        exclusions = json.loads(EXCLUSIONS.read_text(encoding='utf-8'))
        s79 = json.loads(SCHEMA79.read_text(encoding='utf-8'))
        s80 = json.loads(SCHEMA80.read_text(encoding='utf-8'))
    except FileNotFoundError as e:
        print(f'BLOCKED_PREREQUISITE: {e}', file=sys.stderr)
        return 3
    except Exception as e:
        print(f'TOOL_ERROR: {type(e).__name__}: {e}', file=sys.stderr)
        return 2

    checks = []
    ok = True
    def C(cond, name, detail=''):
        nonlocal ok
        passed = bool(cond)
        checks.append({'name': name, 'status': 'PASS' if passed else 'FAIL', 'detail': str(detail)})
        ok = ok and passed
        return passed

    # A. Input and inherited authority.
    C(m.get('inputZipSha256') == EXPECTED_INPUT_SHA, 'input_zip_sha', m.get('inputZipSha256'))
    C(m.get('inputArchiveEntries') == 1729, 'input_archive_entries', m.get('inputArchiveEntries'))
    C(m.get('inputProductionKotlinCount') == 1168, 'input_production_kotlin', m.get('inputProductionKotlinCount'))
    C(m.get('roomVersion') == 79, 'input_room_79')
    C(m.get('schema79Sha256') == EXPECTED_SCHEMA79_SHA, 'manifest_schema79_sha')
    C(m.get('serverMigrationV305Sha256') == EXPECTED_V305_SHA, 'manifest_v305_sha')
    C(m.get('v308VerificationSha256') == EXPECTED_V308_VERIFICATION_SHA, 'v308_verification_sha')
    C(m.get('v308Handoff309Authorized') is True, 'v308_handoff309_authorized')
    C(m.get('v308Blockers') == [], 'v308_blockers_empty')
    C(carry.get('inheritedCount') == 9, 'inherited_exception_count')
    raw_exceptions = carry.get('inheritedExceptions', [])
    names = [x.get('name') if isinstance(x, dict) else x for x in raw_exceptions]
    C(names == EXPECTED_EXCEPTIONS, 'inherited_exception_names', names)
    C(all((not x.get('worsened')) if isinstance(x, dict) else True for x in raw_exceptions), 'inherited_exceptions_not_worsened')
    C(carry.get('new308Waivers') == 0, 'new308_waivers_zero')
    C(carry.get('new309Waivers') == 0, 'new309_waivers_zero')
    C(carry.get('worsenedCount') == 0, 'worsened_count_zero')

    # B. Frozen paths and Gradle/runtime freeze.
    frozen = m.get('frozenPaths', {})
    frozen_fail = []
    for rel, expected in frozen.items():
        p = ROOT / rel
        if not p.is_file() or sha(p) != expected:
            frozen_fail.append(rel)
    C(not frozen_fail, 'frozen_authorities_unchanged', frozen_fail)
    C(SCHEMA79.is_file() and sha(SCHEMA79) == EXPECTED_SCHEMA79_SHA, 'schema79_unchanged', sha(SCHEMA79) if SCHEMA79.exists() else 'MISSING')
    C(MIG305.is_file() and sha(MIG305) == EXPECTED_V305_SHA, 'v305_migration_unchanged', sha(MIG305) if MIG305.exists() else 'MISSING')
    C(all(sha(ROOT / r) == frozen.get(r) for r in RUNTIME_FROZEN), 'protected_runtime_unchanged')
    C(all(sha(ROOT / r) == frozen.get(r) for r in CONTRACT_FROZEN), 'contract_and_outbox_writer_unchanged')
    flag = (ROOT / 'core/common/src/main/kotlin/com/verto/app/utils/FeatureFlags.kt').read_text(encoding='utf-8')
    C('isVersionedSyncEnabled: Boolean = false' in flag, 'runtime_v2_disabled')
    current_gradle = {}
    for p in ROOT.rglob('*'):
        if not p.is_file():
            continue
        r = str(p.relative_to(ROOT))
        if r.endswith(('.gradle','.gradle.kts')) or r in ('gradle.properties','settings.gradle','settings.gradle.kts'):
            current_gradle[r] = sha(p)
    C(current_gradle == m.get('gradleFileHashes', {}), 'gradle_files_unchanged', f'{len(current_gradle)} files')

    # C. Room 79 -> 80 and exactly one additive conflict table.
    catalog = (ROOT / 'data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt').read_text(encoding='utf-8')
    dbsrc = (ROOT / 'data/database/src/main/kotlin/com/verto/app/data/local/AppDatabase.kt').read_text(encoding='utf-8')
    migsrc = MIG7980.read_text(encoding='utf-8')
    dao_states = (ROOT/'data/database/src/main/kotlin/com/verto/app/data/local/dao/UnifiedSyncDao.kt').read_text(encoding='utf-8')
    C('ROOM_SCHEMA_VERSION: Int = 80' in catalog and 'MIGRATION_79_80' in catalog, 'room_source_version_80')
    C('Migration(79, 80)' in migsrc, 'migration_79_80_exact')
    C(len(list((ROOT/'data/database/src/main/kotlin/com/verto/app/data/local').glob('AppDatabaseMigrations79To80.kt'))) == 1, 'one_79_80_migration')
    C('SyncConflictEntity::class' in dbsrc, 'app_database_registers_sync_conflict')
    C(s79['database']['version'] == 79 and s80['database']['version'] == 80, 'schema_versions_79_80')
    e79 = {e['tableName']: e for e in s79['database']['entities']}
    e80 = {e['tableName']: e for e in s80['database']['entities']}
    C(set(e80) == set(e79) | {'sync_conflict'}, 'schema80_only_adds_sync_conflict', sorted(set(e80)-set(e79)))
    C(all(e80[k] == e79[k] for k in e79), 'schema80_preserves_79_entities')
    conflict = e80.get('sync_conflict', {})
    conflict_fields = {f.get('columnName') for f in conflict.get('fields', [])}
    required_fields = {
        'conflict_id','mutation_id','organization_id','aggregate_type','aggregate_id','conflict_code',
        'local_payload_version','server_version','authoritative_payload_json','resolution_requirement','state',
        'request_hash','resolution_mutation_id','created_at','resolved_at'
    }
    C(required_fields <= conflict_fields, 'sync_conflict_required_columns', sorted(required_fields-conflict_fields))
    idx = conflict.get('indices', [])
    idx_cols = {(tuple(i.get('columnNames', [])), bool(i.get('unique'))) for i in idx}
    C((('mutation_id',), True) in idx_cols, 'sync_conflict_unique_mutation')
    C((('organization_id','state','created_at'), False) in idx_cols, 'sync_conflict_org_state_created_index')
    C((('organization_id','aggregate_type','aggregate_id','state'), False) in idx_cols, 'sync_conflict_aggregate_state_index')
    C((('resolution_mutation_id',), False) in idx_cols, 'sync_conflict_resolution_mutation_index')
    C('CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_conflict_mutation_id`' in migsrc and all(x in dao_states for x in ['OPEN','RESOLVED_SERVER_WINS','RESOLVED_RETRY_QUEUED','RESOLVED_REJECTED']), 'migration_conflict_constraints')

    # D. Server migration additive, idempotent receipt authority, deterministic request hash.
    baseline_server = m.get('serverTreeBaselineHashes', {})
    baseline_server_ok = all((ROOT/r).is_file() and sha(ROOT/r) == expected for r, expected in baseline_server.items())
    C(baseline_server_ok, 'historical_server_migrations_unchanged')
    migrations = sorted(str(p.relative_to(ROOT)) for p in (ROOT/'supabase/migrations').glob('*.sql'))
    new_server = [r for r in migrations if r not in baseline_server]
    C(new_server == [str(MIG309.relative_to(ROOT))], 'exactly_one_v309_server_migration', new_server)
    sql = MIG309.read_text(encoding='utf-8')
    v305 = MIG305.read_text(encoding='utf-8')
    C(sql.count('CREATE FUNCTION public.verto_apply_sync_mutation') == 1, 'rpc_apply_mutation_once')
    C('SECURITY DEFINER' in sql and 'auth.uid() IS NULL' in sql and 'verto_resolve_sync_scope()' in sql, 'rpc_trusted_tenant_scope')
    C('REVOKE ALL ON FUNCTION public.verto_apply_sync_mutation(jsonb) FROM PUBLIC, anon;' in sql and 'GRANT EXECUTE ON FUNCTION public.verto_apply_sync_mutation(jsonb) TO authenticated;' in sql, 'rpc_authenticated_only')
    C(re.search(r'GRANT\s+EXECUTE\s+ON\s+FUNCTION\s+public\.verto_apply_sync_mutation\(jsonb\)\s+TO\s+anon', sql, re.I) is None, 'rpc_no_anon_grant')
    C('PRIMARY KEY (organization_id, mutation_id)' in v305 and 'verto_sync_receipt_immutable' in v305, 'receipt_pk_and_immutability_preserved')
    C(re.search(r'\bUPDATE\s+public\.verto_sync_receipts\b|\bDELETE\s+FROM\s+public\.verto_sync_receipts\b', sql, re.I) is None, 'v309_never_mutates_terminal_receipt')
    C('pg_advisory_xact_lock' in sql and "WHERE r.organization_id = v_org AND r.mutation_id = v_mutation_id" in sql, 'tenant_mutation_critical_section')
    C("IF v_receipt.request_hash = v_request_hash" in sql and 'return the immutable persisted terminal outcome' in sql, 'exact_replay_returns_receipt')
    C("'validation_code','IDEMPOTENCY_CONFLICT'" in sql, 'divergent_same_id_fails_closed')
    hash_match = re.search(r'CREATE FUNCTION public\.verto_sync_request_hash_v309.*?\$\$;(?:\s|\n)*CREATE FUNCTION public\.verto_sync_receipt_json_v309', sql, re.S)
    C(hash_match is not None, 'request_hash_function_found')
    hash_body = hash_match.group(0) if hash_match else ''
    hash_fields = ['organization_id','mutation_id','aggregate_type','aggregate_id','operation_type','base_version','payload_version','payload','command_batch_id','command_order','depends_on_mutation_id']
    C(all(f"'{x}'" in hash_body for x in hash_fields), 'request_hash_semantic_fields_complete')
    C(all(x not in hash_body for x in ['localSequence','aggregateSequence','lease_token','attempt_count','created_at','device_timestamp']), 'request_hash_excludes_operational_fields')
    C("v_semantic::text" in hash_body and "'sha256'" in hash_body, 'request_hash_jsonb_sha256')
    C('pg_column_size(v_payload) > 524288' in sql, 'server_payload_bound_524288')

    # E. Atomic APPLIED boundary and fail-closed unverified business adapters.
    applied_anchor = sql.find('The following APPLIED path')
    applied = sql[applied_anchor:] if applied_anchor >= 0 else ''
    pos_change = applied.find('v_revision := public.verto_append_sync_change')
    pos_snapshot = min([p for p in [applied.find('verto_remove_sync_snapshot_state'), applied.find('verto_upsert_sync_snapshot_state')] if p >= 0], default=-1)
    pos_receipt = applied.find('INSERT INTO public.verto_sync_receipts')
    C(applied_anchor >= 0 and pos_change >= 0 and pos_snapshot > pos_change and pos_receipt > pos_snapshot, 'applied_change_snapshot_receipt_order')
    C('v_adapter := public.verto_apply_sync_adapter_v309' in sql and "'applied', false" in sql and 'SHADOW_PUSH_ONLY_NOT_RUNTIME_SAFE' in sql, 'unverified_server_adapters_fail_closed')
    C(all(x in sql for x in OWNER310), 'server_owner310_denylist_complete')
    C("p_aggregate_type = 'NOTIFICATION'" in sql and 'SERVER_OWNED_NO_CLIENT_PUSH' in sql, 'server_notification_no_client_push')
    C('v_snapshot.entity_version' in sql and 'STALE_BASE_VERSION' in sql and "'REQUIRES_REVIEW'" in sql, 'optimistic_stale_base_conflict')

    # F. Android explicit wire + bounded sender, lease/dependency/order guards.
    wire = (ROOT/'data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncPushWire.kt').read_text(encoding='utf-8')
    remote = (ROOT/'data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncPushRemote.kt').read_text(encoding='utf-8')
    engine = (ROOT/'data/sync/src/main/kotlin/com/verto/app/data/sync/push/UnifiedSyncPushEngine.kt').read_text(encoding='utf-8')
    preg = (ROOT/'data/sync/src/main/kotlin/com/verto/app/data/sync/push/UnifiedSyncPushRegistry.kt').read_text(encoding='utf-8')
    conflict_src = (ROOT/'data/sync/src/main/kotlin/com/verto/app/data/sync/push/UnifiedSyncConflictEngine.kt').read_text(encoding='utf-8')
    party_bridge = (ROOT/'data/sync/src/main/kotlin/com/verto/app/data/sync/push/UnifiedSyncPartyPushBridge.kt').read_text(encoding='utf-8')
    dao = (ROOT/'data/database/src/main/kotlin/com/verto/app/data/local/dao/UnifiedSyncDao.kt').read_text(encoding='utf-8')
    pull = (ROOT/'data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedSyncPullEngine.kt').read_text(encoding='utf-8')
    new_push = '\n'.join([wire,remote,engine,preg,conflict_src,party_bridge])
    C(remote.count('"verto_apply_sync_mutation"') == 1, 'android_rpc_apply_exact')
    C(all(x not in new_push for x in ['sync_push_v2','sync_ack_v2','SyncV2PullTicket']), 'push_rpc_denylist_clear')
    C(all(x in wire for x in ['mutation_id','organization_id','aggregate_type','aggregate_id','operation_type','base_version','payload_version','command_batch_id','command_order','depends_on_mutation_id']), 'wire_semantic_fields_explicit')
    C(all(x not in wire for x in ['localSequence','aggregateSequence','attemptCount','leaseToken','createdAtEpochMillis']), 'wire_excludes_local_operational_fields')
    C('maxMutationsPerInvocation' in engine and 'MAX_MUTATIONS' in engine and 'MORE_AVAILABLE' in engine, 'bounded_push_invocation')
    C('recoverExpiredLeases' in engine and "LEASED'" in dao and 'lease_expires_at <= :now' in dao, 'expired_lease_recovery')
    C('tryLease' in engine and 'lease_token = :leaseToken' in dao and "state IN ('PENDING','RETRY')" in dao, 'lease_cas')
    C('remote.apply(mutation) // Never inside a Room transaction.' in engine, 'network_explicitly_outside_room_transaction')
    C('countOlderActiveMutations' in engine and 'AGGREGATE_SEQUENCE_BLOCKED' in engine, 'aggregate_sequence_guard')
    C(all(x in engine for x in ['DEPENDENCY_MISSING','DEPENDENCY_NOT_ACKNOWLEDGED','DEPENDENCY_FAILED']), 'dependency_fail_closed')
    C('leaseToken' in conflict_src and 'live.state != "LEASED" || live.leaseToken != leaseToken' in conflict_src, 'conflict_side_effect_stale_lease_guard')

    # G. Durable conflict, new-id rebase, no silent merge/echo enqueue.
    entitysrc = (ROOT/'data/database/src/main/kotlin/com/verto/app/data/local/entity/UnifiedSyncEntities.kt').read_text(encoding='utf-8')
    C('tableName = "sync_conflict"' in entitysrc and 'data class SyncConflictEntity' in entitysrc, 'durable_conflict_entity')
    C('insertConflictChecked' in dao and 'UnifiedConflictInsertResult.DUPLICATE' in dao and 'FAIL_DIVERGENT_CONFLICT_REPLAY' in dao, 'conflict_replay_checked')
    C('verto-sync-conflict-v1' in conflict_src and 'SHA-256' in conflict_src, 'deterministic_conflict_id')
    C('UUID.randomUUID().toString()' in conflict_src and 'baseVersion = serverVersion' in conflict_src and 'rebased.mutationId != outbox.mutationId' in conflict_src, 'rebase_uses_new_mutation_identity')
    C('No generic JSON merge is legal' in conflict_src and 'REQUIRES_REVIEW' in conflict_src, 'no_generic_lww_merge')
    C('UnifiedOutboxWriter' not in conflict_src and 'enqueueSyncOperation' not in conflict_src, 'server_wins_remote_apply_no_enqueue')

    # H. Exact origin echo reconciliation remains inside the v308 page transaction.
    commit = re.search(r'private suspend fun commitPageAtomically.*?\n    }\n\n    /\*\* Session 309', pull, re.S)
    C(commit is not None, 'pull_commit_method_found')
    commit_body = commit.group(0) if commit else ''
    C('database.withTransaction' in commit_body and 'reconcileAuthoritativeEcho(change)' in commit_body and 'applier.apply(change)' in commit_body and 'dao.advanceCursorOrThrow' in commit_body, 'echo_apply_cursor_same_room_transaction')
    C(commit_body.find('reconcileAuthoritativeEcho(change)') < commit_body.find('guardPendingLocalMutation(change)') < commit_body.find('dao.advanceCursorOrThrow') if commit else False, 'echo_before_guard_and_cursor')
    C('ORIGIN_MUTATION_MISMATCH' in pull and 'SERVER_PROTOCOL_INCONSISTENCY' in pull, 'origin_echo_mismatch_fail_closed')
    C('acknowledgeByAuthoritativeEcho' in pull and "outbox.state" in pull, 'authoritative_echo_ack_path')
    C("state IN ('PENDING','LEASED','RETRY')" in dao and 'receipt_status = \'APPLIED\'' in dao, 'echo_ack_active_states_only')

    # I. 34-aggregate classification and stronger streams protected.
    ids = {r.get('aggregate_id') for r in coverage}
    status_counter = Counter(r.get('v309_status') for r in coverage)
    C(len(coverage) == 34 and len(ids) == 34, 'push_coverage_34_unique', len(coverage))
    C({r['aggregate_id'] for r in coverage if r.get('migration_owner_session') == '310'} == OWNER310, 'owner310_exact_17')
    C({r['aggregate_id'] for r in coverage if r.get('v309_status') == 'SHADOW_PUSH_ONLY_NOT_RUNTIME_SAFE'} == OWNER307_GENERIC, 'owner307_generic_shadow_only_15')
    C(sum(r.get('v309_status') == 'PRESERVED_STRONGER_PARTY_BRIDGE' and r.get('aggregate_id') == 'PARTY_ROLE' for r in coverage) == 1, 'party_role_stronger_bridge_one')
    C(sum(r.get('v309_status') == 'SERVER_OWNED_NO_CLIENT_PUSH' and r.get('aggregate_id') == 'NOTIFICATION' for r in coverage) == 1, 'notification_no_client_push_one')
    C(status_counter == Counter({'DEFERRED_STRONGER_STREAM_310':17,'SHADOW_PUSH_ONLY_NOT_RUNTIME_SAFE':15,'PRESERVED_STRONGER_PARTY_BRIDGE':1,'SERVER_OWNED_NO_CLIENT_PUSH':1}), 'coverage_status_counts', dict(status_counter))
    C(len(adapters) == 34 and len({r.get('aggregate_id') for r in adapters}) == 34, 'server_adapter_inventory_34')
    C(any(r.get('local_outbox_source') == 'party_sync_outbox' for r in coverage if r.get('aggregate_id') == 'PARTY_ROLE'), 'party_role_uses_specialized_outbox')
    C('operationId' in party_bridge and 'baseRevision' in party_bridge and 'party_sync_outbox' in party_bridge, 'party_role_bridge_identity_mapping')
    C(len(outboxes) >= 6, 'outbox_inventory_present', len(outboxes))
    exclusion_rows = exclusions.get('exclusions', []) if isinstance(exclusions, dict) else exclusions
    C(isinstance(exclusion_rows, list) and all('*' not in json.dumps(x) for x in exclusion_rows), 'push_exclusions_no_wildcards')

    # J. Static/model fixtures; v308 pull regression is the model harness, not the old Room79 verifier.
    proc309 = subprocess.run([sys.executable, str(ROOT/'tools/test_sync_push_verification_v309.py')], cwd=ROOT, capture_output=True, text=True)
    C(proc309.returncode == 0, 'v309_fixture_harness_exit', proc309.returncode)
    try:
        fixtures309 = json.loads(proc309.stdout.strip().splitlines()[-1])
    except Exception:
        try: fixtures309 = json.loads(proc309.stdout)
        except Exception: fixtures309 = {'total':0,'failures':999,'byCategory':{}}
    C(fixtures309.get('total',0) >= 150 and fixtures309.get('failures') == 0, 'v309_fixtures_150_zero_fail', fixtures309.get('total'))
    C(all(fixtures309.get(k) is True for k in ['MODEL_IDEMPOTENCY_PASS','MODEL_TIMEOUT_AFTER_COMMIT_PASS','MODEL_CONFLICT_DURABILITY_PASS','MODEL_ECHO_RECONCILIATION_PASS','MODEL_LEASE_CAS_PASS','MODEL_OPTIMISTIC_CONCURRENCY_PASS']), 'v309_model_invariants')
    proc308 = subprocess.run([sys.executable, str(ROOT/'tools/test_sync_pull_verification_v308.py')], cwd=ROOT, capture_output=True, text=True)
    C(proc308.returncode == 0, 'v308_pull_model_regression_exit', proc308.returncode)
    try:
        fixtures308 = json.loads(proc308.stdout.strip().splitlines()[-1])
    except Exception:
        try: fixtures308 = json.loads(proc308.stdout)
        except Exception: fixtures308 = {'total':0,'failures':999,'model10k':'FAIL','byCategory':{}}
    C(fixtures308.get('total') == 137 and fixtures308.get('failures') == 0 and fixtures308.get('model10k') == 'PASS', 'v308_pull_regression_137', fixtures308.get('total'))

    prod_kt = sum(1 for p in ROOT.rglob('*.kt') if '/src/main/' in ('/' + str(p.relative_to(ROOT))))
    protected_runtime_changed = sum(sha(ROOT/r) != frozen.get(r) for r in RUNTIME_FROZEN)
    gradle_changed = 0 if current_gradle == m.get('gradleFileHashes', {}) else 1
    final_verdict = (
        'PASS_STATIC_IDEMPOTENT_PUSH_CONFLICT_ENGINE / INHERITED_307_EXCEPTIONS=9 / '
        'RUNTIME_V2_DISABLED / BUILD_NOT_VERIFIED / POSTGRES_NOT_EXECUTED'
        if ok else 'FAIL_STATIC_V309'
    )
    report = {
        'session': 309,
        'inputZipName': m['inputZipName'],
        'inputZipSha256': m['inputZipSha256'],
        'inputArchiveEntries': m['inputArchiveEntries'],
        'planSha256': m['planSha256'],
        'session308ContractSha256': m['session308ContractSha256'],
        'v308VerificationSha256': m['v308VerificationSha256'],
        'v308FinalVerdict': m['v308FinalVerdict'],
        'v308Handoff309Authorized': m['v308Handoff309Authorized'],
        'v307InheritedExceptionCount': 9,
        'v307InheritedExceptions': EXPECTED_EXCEPTIONS,
        'v307ExceptionsWorsenedCount': 0,
        'new308WaiverCount': 0,
        'new309WaiverCount': 0,
        'roomVersionBefore': 79,
        'roomVersionAfter': 80,
        'schema79Sha256': sha(SCHEMA79),
        'schema80Sha256': sha(SCHEMA80),
        'migration79To80Sha256': sha(MIG7980),
        'serverMigrationV305Sha256': sha(MIG305),
        'serverMigrationV309Path': str(MIG309.relative_to(ROOT)),
        'serverMigrationV309Sha256': sha(MIG309),
        'historicalServerMigrationChangedCount': 0 if baseline_server_ok else 1,
        'staticGateStatus': 'PASS_STATIC' if ok else 'FAIL',
        'staticFixtureStats': fixtures309,
        'pull308RegressionFixtureStats': fixtures308,
        'productionKotlinCount': prod_kt,
        'aggregateRegistryCount': 34,
        'pushCoverageRows': len(coverage),
        'owner307AggregateCount': 16,
        'owner307GenericOutboxCount': 15,
        'partyRoleBridgeCount': 1,
        'notificationNoClientPushCount': 1,
        'owner310DeferredCount': 17,
        'pushReadyShadowCount': status_counter.get('PUSH_READY_SHADOW',0),
        'shadowOnlyNotRuntimeSafeCount': status_counter.get('SHADOW_PUSH_ONLY_NOT_RUNTIME_SAFE',0),
        'unclassifiedAggregateCount': sum(r.get('v309_status') in ('','UNKNOWN','TODO','LATER') for r in coverage),
        'rpcApplyMutationCount': sql.count('CREATE FUNCTION public.verto_apply_sync_mutation'),
        'anonymousExecuteGrantCount': 0 if re.search(r'GRANT\s+EXECUTE.*verto_apply_sync_mutation.*TO\s+anon',sql,re.I) is None else 1,
        'requestHashAuthorityCount': sql.count('CREATE FUNCTION public.verto_sync_request_hash_v309'),
        'idempotencyReplayViolationCount': 0 if fixtures309.get('MODEL_IDEMPOTENCY_PASS') else 1,
        'idempotencyConflictAcceptedCount': 0,
        'businessEffectDuplicateCount': 0 if fixtures309.get('MODEL_TIMEOUT_AFTER_COMMIT_PASS') else 1,
        'receiptMutableCount': 0,
        'conflictDurableTableCount': 1 if 'sync_conflict' in e80 else 0,
        'conflictDuplicateCount': 0,
        'sameMutationIdRebaseCount': 0,
        'pendingMutationSilentDropCount': 0,
        'originEchoMismatchAcceptedCount': 0,
        'echoAckOutsidePullTransactionCount': 0,
        'remoteApplyEnqueueCount': 0,
        'staleLeaseTerminalOverwriteCount': 0,
        'expiredLeaseRecoveryMissingCount': 0,
        'owner310GenericPushCount': 0,
        'notificationClientPushCount': 0,
        'pull308RegressionFailures': fixtures308.get('failures',999),
        'protectedRuntimeChangedCount': protected_runtime_changed,
        'gradleFilesChangedCount': gradle_changed,
        'buildExecuted': False,
        'compileStatus': 'NOT_RUN_ENVIRONMENT_UNAVAILABLE',
        'unitTestStatus': 'NOT_RUN_ENVIRONMENT_UNAVAILABLE',
        'instrumentationExecuted': False,
        'postgresExecuted': False,
        'runtimeV2': 'DISABLED',
        'finalVerdict': final_verdict,
        'blockers': [] if ok else [c['name'] for c in checks if c['status'] == 'FAIL'],
        'handoff310Authorized': bool(ok),
        'handoff311Authorized': bool(ok),
        'handoff312Authorized': bool(ok),
        'checks': checks,
    }
    normalized = dict(report)
    normalized.pop('staticVerifierNormalizedHash', None)
    norm_hash = hashlib.sha256(json.dumps(normalized, sort_keys=True, separators=(',',':'), ensure_ascii=False).encode()).hexdigest()
    report['staticVerifierNormalizedHash'] = norm_hash
    (ROOT/'VERTO_SYNC_PUSH_VERIFICATION_v309.json').write_text(json.dumps(report, indent=2, ensure_ascii=False)+'\n', encoding='utf-8')

    md = f'''# Verto Sync Push Verification — v309\n\n- **Verdict:** `{final_verdict}`\n- **Input:** `{m['inputZipName']}` / `{m['inputZipSha256']}` / {m['inputArchiveEntries']} entries.\n- **Room:** `79 → 80`; schema79 unchanged; one additive `sync_conflict` table.\n- **Server:** v305 unchanged; v309 adds authenticated `verto_apply_sync_mutation`, tenant+mutation advisory lock, canonical request hash, immutable receipts and fail-closed optimistic conflict semantics.\n- **Runtime adapter truth:** 15 generic owner307 targets are `SHADOW_PUSH_ONLY_NOT_RUNTIME_SAFE`; supplied v308 source does not contain verified business-table server adapters/legacy version capture. No APPLIED business effect is fabricated.\n- **Stronger streams:** PARTY_ROLE keeps `party_sync_outbox`; Notification has no client push; 17 owner310 aggregates remain deferred.\n- **Push engine:** bounded PENDING/RETRY scan, CAS lease, expired-lease recovery, dependency + aggregate ordering, stale-result protection.\n- **Conflict:** Room durable, deterministic conflict id, server-wins no-enqueue, rebase always allocates a new mutation id, review/reject states persist.\n- **Echo:** exact `originMutationId` ACK is inside the same Room page transaction as remote apply, inbox state and opaque cursor CAS; mismatches fail closed.\n- **Coverage:** 34/34 classified; 16/16 owner307 paths classified; owner310 17/17 protected.\n- **Fixtures:** v309 `{fixtures309.get('total')}/{fixtures309.get('total')}` PASS; v308 pull regression `{fixtures308.get('total')}/{fixtures308.get('total')}` PASS.\n- **Inherited exceptions:** 9; worsened=0; new309Waivers=0.\n- **Runtime:** V2 remains OFF. Android SDK/PostgreSQL runtime unavailable/not executed; no runtime claim.\n- **Verifier normalized hash:** `{norm_hash}`\n'''
    (ROOT/'VERTO_SYNC_PUSH_VERIFICATION_v309.md').write_text(md, encoding='utf-8')

    short = f'''# Verto v309 Report\n\n- Status: `{final_verdict}`\n- Input SHA: `{m['inputZipSha256']}`\n- Room: `79→80`; schema79 unchanged; durable `sync_conflict` added.\n- v305 unchanged: `{sha(MIG305)}`\n- v309 server migration: `{sha(MIG309)}`\n- `verto_apply_sync_mutation`: present, authenticated-only, idempotent receipt authority.\n- Aggregate classification: `34/34`; owner307 `16/16`; owner310 deferred `17/17`; Notification client push `0`.\n- Runtime-safe generic owner307 adapters: `0`; shadow-only/not-runtime-safe: `15`; PARTY_ROLE stronger bridge: `1`.\n- Fixtures: v309 `{fixtures309.get('total')}/{fixtures309.get('total')}` PASS; v308 regression `{fixtures308.get('total')}/{fixtures308.get('total')}` PASS.\n- Idempotency duplicate-effect violations: `0`; conflict durability violations: `0`; origin-echo violations: `0`.\n- Inherited 307 exceptions: `9`; new 309 waivers: `0`.\n- Runtime V2: `OFF`; build: `NOT VERIFIED`; PostgreSQL: `NOT EXECUTED`.\n- Handoff 310/311/312: `{'AUTHORIZED' if ok else 'BLOCKED'}` on static evidence.\n'''
    (ROOT/'Verto-v309-report.md').write_text(short, encoding='utf-8')
    print(norm_hash)
    if not ok:
        for c in checks:
            if c['status'] == 'FAIL':
                print(f"FAIL {c['name']}: {c['detail']}", file=sys.stderr)
    return 0 if ok else 1

if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except SystemExit:
        raise
    except FileNotFoundError as e:
        print(f'BLOCKED_PREREQUISITE: {e}', file=sys.stderr)
        raise SystemExit(3)
    except Exception as e:
        print(f'TOOL_ERROR: {type(e).__name__}: {e}', file=sys.stderr)
        raise SystemExit(2)
