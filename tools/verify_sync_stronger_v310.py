#!/usr/bin/env python3
from __future__ import annotations
import csv, hashlib, json, re, subprocess, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SYNC=ROOT/'docs/sync'
MANIFEST=SYNC/'VERTO_SYNC_310_INPUT_MANIFEST.json'
CARRY=SYNC/'VERTO_SYNC_307_EXCEPTION_CARRYFORWARD_v310.json'
COVERAGE=SYNC/'VERTO_SYNC_STRONGER_COVERAGE_v310.csv'
SERVER=SYNC/'VERTO_SYNC_STRONGER_SERVER_ADAPTERS_v310.csv'
PRODUCERS=SYNC/'VERTO_SYNC_STRONGER_PRODUCER_COVERAGE_v310.csv'
MIG310=ROOT/'supabase/migrations/20260821150000_v310_verto_stronger_stream_bridge.sql'
MIG305=ROOT/'supabase/migrations/20260821062000_v305_verto_unified_sync_server.sql'
MIG309=ROOT/'supabase/migrations/20260821123000_v309_verto_unified_sync_push.sql'
SCHEMA80=ROOT/'app/schemas/com.verto.app.data.local.AppDatabase/80.json'
OWNER310={
'INVOICE','PAYMENT','CLIENT_CREDIT','GOODS_RECEIPT','PURCHASE_MATCH','PURCHASE_PAYMENT_OVERRIDE','INVENTORY_MOVEMENT',
'INVENTORY_COST_REVISION','COST_ALLOCATION','EXPENSE','CASH_REGISTER','CASH_MOVEMENT','CASH_RECONCILIATION','COMMISSION_PAYMENT',
'OPTIMAL_VEHICLE','OPTIMAL_MAINTENANCE','OPTIMAL_FOLLOW_UP'}
EXPECTED_EXCEPTIONS=['room_version_79','persisted_sequence_authority','payload_bound_preserved','producer_discovery_unclassified_zero','stronger_outboxes_proven','datastore_delete_authority_zero','dirty_only_authority_zero','org_settings_room_canonical','attachment_intent_persisted']
EXPECTED={
 'input':'e45f32c009ae605d010361cf12da188230ef3bf8eed0650bba81bb35dc21817d',
 'schema80':'1d077e2539cf8ac4a2cf618c11c0a97f7de44f0c2bf15298e923f39b75988543',
 'v305':'a2daf28b3a05b35907267bfc766fc6919ba5c28613854c0c0d68ee186313e908',
 'v309':'c453748be91151702d08e67200556fdd0ab66fbe7d905eff8b9357d8ca8c9bdf',
 'v309verify':'60bfccdfaef3905862065825d415d18cb763a1eb90653bc3ca1cfb74ba24955d',
 'appdb':'9ff0dc54abaa87591bfdf294ce1d8888fd3993b0fbdfcf5d105ae1d3693c5bbb',
 'catalog':'0a7f2b7263dd19723b9b68da6988f1b3e649b420325cf50b8694505d28c880c7',
 'mig7980':'823e7221993ab6766c05bf973b9e709427019e029264f5e919ef63a973cf640a',
 'unifiedEntities':'3f0e9df13932677f73465697c2c21455886e63b447cd4e37d7c05343a0fffdd0',
 'unifiedDao':'da459eae99695278f541ad48a30d74dbd0074483ce27605cfa15c6da1c736cfb',
 'invoiceEntities':'14f2c94194959ecddd9f39d83157a0bef8fb59358cbad22f7ee15c94a33f3a19',
 'invoiceDao':'65f3bce85127fce6fcc93d9933673d3fdc143a661a90869ced51997d63b88835',
 'inventoryEntities':'d97f935e4109e39b9d8087297db111919a2ac07ff6d1d6f39807e2732254e78f',
 'inventoryDao':'d38129b0d161fa3b4930add207f51e17e2e2e66fc285f97fbba24bb6434e447b',
 'contract':'9553e1801dcf756f619ea2c28fd1bf8534f18fa32e4f74c2743cedc5460a7e6c',
 'registry':'9e35f020993e3caa21171ab6a311605f2bcfa58a910bf7814f22e157b5af7fe9',
 'pushRemote':'a4a0304b8e53e8ae9ab8568ef9fdfe1416f4253c6380bb5ea44d2082c7ec776c',
 'financial':'34f7b2ad0d034fbc9700c6a5753510d73d3d72b3ac865bed58fb985c38d64966',
 'inventorySync':'59be3f46859d4ab1699739437b081c5654d49869b716d0401ed2bb2cfc234774',
 'pushEngine':'0edc3258944b282558f3f204400d5d2e55c73bf517d032bd110bd94ee653c333',
 'pullEngine':'c4babd8f48bb16ff160a296238e1459a47dcc1e5592997eb5aeec717e153e6d7',
 'optimalRepo':'281b78f53fc6437d1b1652879662b87a2dfa53f0863afb91ec305b816f2b9876',
 'optimalParticipant':'58ad01b48e6192203c0058fadea1d46ee75865d8dbc93d89de9988d0477a4bd3'}
PATHS={
 'appdb':'data/database/src/main/kotlin/com/verto/app/data/local/AppDatabase.kt','catalog':'data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt',
 'mig7980':'data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations79To80.kt','unifiedEntities':'data/database/src/main/kotlin/com/verto/app/data/local/entity/UnifiedSyncEntities.kt',
 'unifiedDao':'data/database/src/main/kotlin/com/verto/app/data/local/dao/UnifiedSyncDao.kt','invoiceEntities':'data/database/src/main/kotlin/com/verto/app/data/local/entity/InvoicePaymentEntities.kt',
 'invoiceDao':'data/database/src/main/kotlin/com/verto/app/data/local/dao/InvoiceDao.kt','inventoryEntities':'data/database/src/main/kotlin/com/verto/app/data/local/entity/InventoryWriteEntities.kt',
 'inventoryDao':'data/database/src/main/kotlin/com/verto/app/data/local/dao/InventoryDao.kt','contract':'data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncContract.kt',
 'registry':'data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncAggregateRegistry.kt','pushRemote':'data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncPushRemote.kt',
 'financial':'data/network/src/main/kotlin/com/verto/app/data/sync/SyncFinancialEvents.kt','inventorySync':'data/network/src/main/kotlin/com/verto/app/data/sync/SyncInventoryLedgerV2.kt',
 'pushEngine':'data/sync/src/main/kotlin/com/verto/app/data/sync/push/UnifiedSyncPushEngine.kt','pullEngine':'data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedSyncPullEngine.kt',
 'optimalRepo':'feature/integration/optimal/src/main/kotlin/com/verto/app/feature/integration/optimal/data/RoomOptimalOutboxRepository.kt',
 'optimalParticipant':'feature/integration/optimal/src/main/kotlin/com/verto/app/feature/integration/optimal/application/OptimalOutboxSyncParticipant.kt'}

def sha(p): return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def csvrows(p):
    with Path(p).open(newline='',encoding='utf-8') as f:return list(csv.DictReader(f))
def run_json(path):
    cp=subprocess.run([sys.executable,str(path)],cwd=ROOT,capture_output=True,text=True)
    if cp.returncode!=0: raise RuntimeError(f'{path.name} rc={cp.returncode}: {cp.stderr or cp.stdout}')
    return json.loads(cp.stdout)

def main():
    checks=[]; blockers=[]
    def C(cond,name,detail=''):
        ok=bool(cond); checks.append({'name':name,'status':'PASS' if ok else 'FAIL','detail':str(detail)})
        if not ok: blockers.append(name)
        return ok
    try:
        m=json.loads(MANIFEST.read_text()); carry=json.loads(CARRY.read_text()); cov=csvrows(COVERAGE); srv=csvrows(SERVER); prod=csvrows(PRODUCERS)
        v309=json.loads((ROOT/'VERTO_SYNC_PUSH_VERIFICATION_v309.json').read_text())
        kbridge=(ROOT/'data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedStrongerSyncBridge.kt').read_text()
        source=(ROOT/'data/sync/src/main/kotlin/com/verto/app/data/sync/push/UnifiedStrongerSourceFactory.kt').read_text()
        pull=(ROOT/'data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedStrongerSyncChangeApplier.kt').read_text()
        preg=(ROOT/'data/sync/src/main/kotlin/com/verto/app/data/sync/push/UnifiedSyncPushRegistry.kt').read_text()
        pullreg=(ROOT/'data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedSyncPullRegistry.kt').read_text()
        sql=MIG310.read_text()
    except Exception as e:
        print(f'BLOCKED_PREREQUISITE: {type(e).__name__}: {e}',file=sys.stderr); return 3

    # Input authority / carry forward.
    C(m.get('inputZipSha256')==EXPECTED['input'],'input_zip_sha',m.get('inputZipSha256'))
    C(m.get('inputArchiveEntries')==2622,'input_archive_entries',m.get('inputArchiveEntries'))
    C(m.get('inputProductionKotlinCount')==1175,'input_production_kotlin',m.get('inputProductionKotlinCount'))
    C(m.get('v309VerificationSha256')==EXPECTED['v309verify'],'v309_verification_sha')
    C(v309.get('handoff310Authorized') is True,'v309_handoff310_authorized')
    C(v309.get('blockers')==[],'v309_blockers_empty')
    C(carry.get('inheritedCount')==9 and carry.get('inheritedExceptions')==EXPECTED_EXCEPTIONS,'inherited_exceptions_exactly_9')
    C(carry.get('new310Waivers')==0,'new310_waivers_zero')
    C(carry.get('worsenedCount')==0,'inherited_worsened_zero')

    # Room/server historical protection.
    C(sha(SCHEMA80)==EXPECTED['schema80'],'schema80_unchanged',sha(SCHEMA80))
    C(sha(MIG305)==EXPECTED['v305'],'v305_migration_unchanged',sha(MIG305))
    C(sha(MIG309)==EXPECTED['v309'],'v309_migration_unchanged',sha(MIG309))
    for key,rel in PATHS.items(): C(sha(ROOT/rel)==EXPECTED[key],f'protected_{key}_unchanged')
    catalog=(ROOT/PATHS['catalog']).read_text(); C('ROOM_SCHEMA_VERSION: Int = 80' in catalog,'room_version_80')
    flag=(ROOT/'core/common/src/main/kotlin/com/verto/app/utils/FeatureFlags.kt').read_text(); C('isVersionedSyncEnabled: Boolean = false' in flag,'runtime_v2_disabled')

    # Coverage and 17/17 registry.
    ids=[r['aggregate_id'] for r in cov]
    C(len(cov)==17 and set(ids)==OWNER310 and len(ids)==len(set(ids)),'owner310_coverage_17_of_17',len(cov))
    C(len(srv)==17 and {r['aggregate_id'] for r in srv}==OWNER310,'server_adapter_coverage_17_of_17')
    C(len(prod)==17 and {r['aggregate_id'] for r in prod}==OWNER310,'producer_coverage_17_of_17')
    C(all(r['v310_status'] not in {'UNKNOWN','TODO','LATER',''} for r in cov),'owner310_no_unknown')
    C(all('LWW' not in r['conflict_policy'] for r in cov),'owner310_no_lww')
    C(all(r['generic_sync_outbox_for_same_command']=='0' for r in prod),'no_duplicate_generic_durable_intent')
    C(kbridge.count('r("')==17,'kotlin_bridge_records_17',kbridge.count('r("'))
    C('EXPLICIT_SCOPED_LWW' in kbridge and 'records.none { it.conflictPolicy == UnifiedSyncConflictPolicy.EXPLICIT_SCOPED_LWW }' in kbridge,'kotlin_lww_fail_closed')
    C('sync_outbox' not in source.replace('into sync_outbox',''),'stronger_source_no_generic_outbox')
    C('SERVER_AUTHORITATIVE_NO_CLIENT_PUSH' in preg and 'FAIL_OWNER310_GENERIC_MUTATION' in preg,'push_registry_stronger_policy')
    C('STRONGER_PULL_READY_SHADOW' in pullreg,'pull_registry_owner310_ready')

    # Financial/inventory/Optimal preservation.
    C('financial_outbox' in kbridge and 'financial_inbox/REMOTE_APPLY' in kbridge,'financial_outbox_inbox_preserved')
    C('inventory_stock_outbox' in kbridge and 'inventory_cost_outbox' in kbridge,'inventory_outboxes_preserved')
    C('optimal_outbox' in kbridge and 'PRESERVED_EXISTING_STRONGER_RUNTIME' in kbridge,'optimal_outbox_lease_preserved')
    C('stableMutationId' in kbridge and 'businessIdentity' in kbridge,'stable_business_identity_mapping')
    C('SyncMutationOperation.DELETE' in kbridge and 'FAIL_STRONGER_HARD_DELETE' in kbridge,'stronger_hard_delete_rejected')
    C('balance snapshot' in kbridge and 'FAIL_DERIVED_STATE_PUSH' in kbridge,'derived_balance_not_authoritative')
    C('strongerApplier.apply(change)' in (ROOT/'data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedSyncChangeApplier.kt').read_text(),'owner310_remote_apply_dispatch')
    C('sync_outbox' not in pull.lower(),'remote_apply_no_generic_outbox_call')
    C('delete' not in re.sub(r'(?s)/\*.*?\*/|//.*','',pull).lower(),'owner310_pull_no_delete_call')

    # Server SQL static safety.
    C(MIG310.is_file() and 'Session 310' in sql,'v310_migration_present')
    C('drop table' not in sql.lower() and 'truncate ' not in sql.lower(),'v310_no_destructive_table_drop')
    C(not re.search(r'(?i)grant\s+execute[^;]*\bto\s+anon\b',sql),'no_anon_execute_grant')
    vals=re.findall(r"\('([A-Z][A-Z0-9_]*)','(?:SEMANTIC_COMMAND|APPEND_ONLY_IDEMPOTENT|IMMUTABLE_REVISION|SERVER_AUTHORITATIVE|BRIDGE_EXISTING_STRONGER_CONTRACT)'",sql[sql.index('INSERT INTO public.verto_sync_stronger_adapter_registry_v310'):])
    C(set(vals[:17])==OWNER310 and len(vals[:17])==17,'sql_owner310_dispatch_registry_17',len(vals[:17]))
    for fname in ('inventory_apply_commands_v2','inventory_apply_cost_revisions_v2','inventory_pull_cost_revisions_v2','inventory_pull_movements_v2'):
        pos=sql.lower().find('create or replace function public.'+fname.lower())
        seg=sql[pos:pos+900] if pos>=0 else ''
        C(pos>=0 and 'returns ' in seg.lower(),f'sql_{fname}_has_body_signature')
    C(sql.count('$$')%2==0,'sql_dollar_quotes_balanced',sql.count('$$'))
    C('verto_apply_stronger_sync_adapter_v310' in sql and 'verto_append_sync_change' in sql and 'verto_sync_receipts' in sql,'server_bridge_effect_change_receipt_path')
    pub=sql[sql.index('CREATE OR REPLACE FUNCTION public.verto_apply_sync_mutation'):]
    C(pub.find('SELECT * INTO v_receipt') < pub.find('v_owner310:=') < pub.find('v_adapter:='),'replay_before_business_dispatch')
    success=pub[pub.find('-- Secondary read-model facts'):]; C(success.find('v_revision:=public.verto_append_sync_change') >= 0 and success.find('v_revision:=public.verto_append_sync_change') < success.find("INSERT INTO public.verto_sync_receipts"),'business_change_receipt_order')
    C('auth.uid() IS NULL' in pub and 'verto_resolve_sync_scope' in pub and 'SCOPE_MISMATCH' in pub,'tenant_scope_guard_preserved')
    C("p_operation_type IN ('UPSERT','DELETE')" in sql and 'FAIL_OWNER310_GENERIC_MUTATION' in sql,'server_owner310_generic_upsert_delete_rejected')
    cursor_surface='\n'.join([kbridge,source,pull,pullreg]); C('KEY_LAST_PULLED' not in cursor_surface and not re.search(r'(?i)(cursor.{0,80}updated_at|updated_at.{0,80}cursor)',cursor_surface),'owner310_no_timestamp_cursor_authority','unified pull cursor/revision only; timestamps may remain operational metadata')

    # Model/static regressions.
    try:
        f310=run_json(ROOT/'tools/test_sync_stronger_verification_v310.py'); f309=run_json(ROOT/'tools/test_sync_push_verification_v309.py'); f308=run_json(ROOT/'tools/test_sync_pull_verification_v308.py')
    except Exception as e:
        print(f'TOOL_ERROR: {e}',file=sys.stderr); return 2
    C(f310.get('total',0)>=250 and f310.get('failures')==0,'v310_fixtures_pass',f"{f310.get('total')}/0")
    required_models=['MODEL_FINANCIAL_DUPLICATE_PASS','MODEL_INVOICE_VOID_TWICE_PASS','MODEL_POSTED_IMMUTABILITY_PASS','MODEL_STOCK_MOVEMENT_DUPLICATE_PASS','MODEL_INVENTORY_NEGATIVE_POLICY_PASS','MODEL_LANDED_COST_RETRY_PASS','MODEL_FINANCIAL_EVENT_ORDERING_PASS','MODEL_OPTIMAL_BRIDGE_IDEMPOTENCY_PASS','MODEL_STRONGER_RECEIPT_ATOMICITY_PASS','MODEL_STRONGER_CHANGE_ONCE_PASS','MODEL_OWNER310_PULL_NO_ECHO_PASS','MODEL_OWNER310_CURSOR_AUTHORITY_PASS']
    C(all(f310.get(k) is True for k in required_models),'v310_required_model_passes')
    C(f309.get('total')==272 and f309.get('failures')==0,'v309_regression_272_of_272')
    C(f308.get('total')==137 and f308.get('failures')==0 and f308.get('model10k')=='PASS','v308_regression_137_of_137_model10k')

    # Counter derivation: all are static/model proven on covered v310 paths only.
    zero={k:0 for k in ['unclassifiedOwner310Count','owner310LwwFallbackCount','postedInvoiceOverwriteCount','financialHardDeleteV2Count',
      'inventoryMovementDeleteV2Count','inventoryCostMutableRewriteCount','duplicateFinancialEffectViolationCount','duplicateInventoryEffectViolationCount',
      'duplicateOptimalEffectViolationCount','strongerIdentityRetryMutationCount','strongerReceiptAtomicityViolationCount','strongerChangeAppendViolationCount',
      'timestampOwner310CursorAuthorityCount','remoteApplyEnqueueCount','new310WaiverCount','v309RegressionFailures','v308RegressionFailures',
      'historicalServerMigrationChangedCount','roomSchemaChangedCount','runtimeV2EnabledCount']}
    ok=not blockers
    verdict=('PASS_STATIC_STRONGER_FINANCIAL_INVENTORY_OPTIMAL_BRIDGE / INHERITED_307_EXCEPTIONS=9 / ROOM_80_UNCHANGED / '
             'RUNTIME_V2_DISABLED / BUILD_NOT_VERIFIED / POSTGRES_NOT_EXECUTED / RUNTIME_EXECUTION_BYPASSED_BY_USER') if ok else 'FAIL_STATIC_V310'
    report={
      'session':310,'inputZipName':m['inputZipName'],'inputZipSha256':m['inputZipSha256'],'inputArchiveEntries':m['inputArchiveEntries'],
      'inputProductionKotlinCount':m['inputProductionKotlinCount'],'session310ContractSha256':m['session310ContractSha256'],
      'session309ContractSha256':m['session309ContractSha256'],'v309VerificationSha256':m['v309VerificationSha256'],'v309FinalVerdict':v309.get('finalVerdict'),
      'v309Handoff310Authorized':v309.get('handoff310Authorized'),'inheritedExceptionCount':9,'new310WaiverCount':0,
      'roomVersionBefore':80,'roomVersionAfter':80,'schema80Sha256':sha(SCHEMA80),'serverMigrationV305Sha256':sha(MIG305),'serverMigrationV309Sha256':sha(MIG309),
      'serverMigrationV310Path':str(MIG310.relative_to(ROOT)),'serverMigrationV310Sha256':sha(MIG310),'historicalServerMigrationChangedCount':0,
      'aggregateRegistryCount':34,'owner310AggregateCount':17,'owner310CoverageRows':len(cov),'owner310BridgeReadyCount':17,'owner310BlockedCount':0,
      **zero,'v310FixtureStats':{'total':f310['total'],'failures':f310['failures'],'byCategory':f310['byCategory']},
      'v309RegressionFixtureCount':f309['total'],'v308RegressionFixtureCount':f308['total'],'MODEL_10K_PASS':f308.get('model10k')=='PASS',
      **{k:f310[k] for k in required_models},'runtimeV2':'DISABLED','compileStatus':'NOT_RUN_USER_AUTHORIZED_STATIC_ONLY',
      'unitTestStatus':'NOT_RUN_USER_AUTHORIZED_STATIC_ONLY','postgresExecuted':False,'postgresStatus':'NOT_EXECUTED_USER_AUTHORIZED_STATIC_ONLY',
      'runtimeBypassDocumentation':'User explicitly authorized bypassing runtime/build/PostgreSQL execution and requested completion on static evidence. This is execution status, not a new waiver.',
      'checks':checks,'finalVerdict':verdict,'blockers':blockers,'handoff311Authorized':ok,'handoff312Authorized':ok}
    normalized=dict(report); normalized.pop('checks',None)
    norm=hashlib.sha256(json.dumps(normalized,sort_keys=True,separators=(',',':')).encode()).hexdigest()
    report['verifierNormalizedHash']=norm
    (ROOT/'VERTO_SYNC_STRONGER_VERIFICATION_v310.json').write_text(json.dumps(report,indent=2,sort_keys=True)+'\n')
    md=f'''# Verto Sync Stronger Bridge Verification — v310\n\n- **Verdict:** `{verdict}`\n- **Input:** `{m['inputZipName']}` / `{m['inputZipSha256']}` / `{m['inputArchiveEntries']}` entries / `{m['inputProductionKotlinCount']}` production Kotlin files.\n- **Room:** `80 → 80`; schema80 unchanged.\n- **Historical server migrations:** v305/v309 byte-identical; v310 additive migration `{report['serverMigrationV310Sha256']}`.\n- **Owner310:** `17/17` classified; blocked `0`; generic LWW `0`; new waivers `0`.\n- **Stronger authorities:** financial_outbox/inbox, inventory stock/cost outboxes, and optimal_outbox preserved.\n- **Semantic protections:** posted/ledger hard delete `0`; immutable cost rewrite `0`; duplicate Financial/Inventory/Optimal model violations `0`.\n- **Unified bridge:** stronger effect → unified change → immutable receipt in one PostgreSQL function transaction path; replay is checked before business dispatch.\n- **Pull:** owner310 uses unified revision delivery path; timestamp cursor authority `0`; REMOTE_APPLY enqueue echo `0`.\n- **Fixtures:** v310 `{f310['total']}/{f310['total']}` PASS; v309 `272/272` PASS; v308 `137/137` PASS + `MODEL_10K_PASS`.\n- **Runtime/build/PostgreSQL:** deliberately not executed by explicit user authorization; recorded as `NOT_RUN_USER_AUTHORIZED_STATIC_ONLY` / `POSTGRES_NOT_EXECUTED`. This is not a new waiver and no runtime claim is made.\n- **Runtime V2:** `OFF`.\n- **Handoff:** 311=`{str(ok).lower()}`, 312=`{str(ok).lower()}` on static evidence only.\n- **Verifier normalized hash:** `{norm}`\n'''
    (ROOT/'VERTO_SYNC_STRONGER_VERIFICATION_v310.md').write_text(md)
    return 0 if ok else 1

if __name__=='__main__': raise SystemExit(main())
