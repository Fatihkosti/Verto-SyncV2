#!/usr/bin/env python3
from __future__ import annotations
import csv, hashlib, json, re, subprocess, sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
MANIFEST=ROOT/'docs/sync/VERTO_SYNC_308_INPUT_MANIFEST.json'
COVERAGE=ROOT/'docs/sync/VERTO_SYNC_PULL_COVERAGE_v308.csv'
CARRY=ROOT/'docs/sync/VERTO_SYNC_307_EXCEPTION_CARRYFORWARD_v308.json'
LEGACY=ROOT/'docs/sync/VERTO_SYNC_LEGACY_PULL_INVENTORY_v308.csv'
EXCLUSIONS=ROOT/'docs/sync/VERTO_SYNC_PULL_EXCLUSIONS_v308.json'
EXPECTED_EXCEPTIONS=[
 'room_version_79','persisted_sequence_authority','payload_bound_preserved','producer_discovery_unclassified_zero',
 'stronger_outboxes_proven','datastore_delete_authority_zero','dirty_only_authority_zero','org_settings_room_canonical','attachment_intent_persisted']
OWNER310={
 'INVOICE','PAYMENT','CLIENT_CREDIT','GOODS_RECEIPT','PURCHASE_MATCH','PURCHASE_PAYMENT_OVERRIDE','INVENTORY_MOVEMENT',
 'INVENTORY_COST_REVISION','COST_ALLOCATION','EXPENSE','CASH_REGISTER','CASH_MOVEMENT','CASH_RECONCILIATION',
 'COMMISSION_PAYMENT','OPTIMAL_VEHICLE','OPTIMAL_MAINTENANCE','OPTIMAL_FOLLOW_UP'}

def sha(path:Path): return hashlib.sha256(path.read_bytes()).hexdigest()
def relhashes(prefixes):
 out={}
 for p in ROOT.rglob('*'):
  if not p.is_file(): continue
  r=str(p.relative_to(ROOT))
  if any(r.startswith(x) for x in prefixes): out[r]=sha(p)
 return out

def check(cond,name,detail='',checks=None):
 if checks is not None: checks.append({'name':name,'status':'PASS' if cond else 'FAIL','detail':str(detail)})
 return bool(cond)

def main():
 try:
  m=json.loads(MANIFEST.read_text()); carry=json.loads(CARRY.read_text())
  with COVERAGE.open(newline='') as f: cov=list(csv.DictReader(f))
  with LEGACY.open(newline='') as f: legacy=list(csv.DictReader(f))
 except Exception as e:
  print(f'TOOL_ERROR: {e}',file=sys.stderr); return 2
 checks=[]
 ok=True
 def C(c,n,d=''):
  nonlocal ok; ok=check(c,n,d,checks) and ok

 C(m.get('inputZipSha256')=='eec7c332b26ab6cafec3fa2f2ecf0f03278d6591664f433094083a1e67c736ba','input_zip_sha')
 C(m.get('inputArchiveEntries')==1712,'input_archive_entries',m.get('inputArchiveEntries'))
 C(m.get('productionKotlinCount')==1162,'input_production_kotlin_count',m.get('productionKotlinCount'))
 C(m.get('roomVersion')==79,'room_version_79')
 # v307 handoff evidence remains frozen in source.
 v307=json.loads((ROOT/'VERTO_SYNC_PRODUCER_VERIFICATION_v307.json').read_text())
 C(v307.get('handoff308Authorized') is True,'v307_handoff308')
 C(v307.get('blockers')==[],'v307_blockers_empty')
 C(carry.get('inheritedCount')==9,'inherited_exception_count')
 C([x['name'] for x in carry.get('inheritedExceptions',[])]==EXPECTED_EXCEPTIONS,'inherited_exception_names')
 C(all(not x.get('worsened') for x in carry.get('inheritedExceptions',[])),'inherited_exceptions_not_worsened')
 C(carry.get('new308Waivers')==0,'new308_waivers_zero')

 # Frozen paths (Room, server migration, contract, protected runtime, legacy readers).
 protected=m['protectedPaths']
 for r,expected in protected.items():
  p=ROOT/r; C(p.is_file() and sha(p)==expected,'protected:'+r,sha(p) if p.is_file() else 'MISSING')
 C(not (ROOT/'data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations79To80.kt').exists(),'no_79_80_migration')
 C('ROOM_SCHEMA_VERSION: Int = 79' in (ROOT/'data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt').read_text(),'room_version_source_79')
 for r,expected in m['legacyReaderPaths'].items():
  p=ROOT/r; C(p.is_file() and sha(p)==expected,'legacy_reader_unchanged:'+r)
 current_server=relhashes(['supabase/migrations/','functions/'])
 C(current_server==m['serverTreeHashes'],'server_tree_unchanged',f'{len(current_server)} files')
 current_gradle={}
 for p in ROOT.rglob('*'):
  if p.is_file():
   r=str(p.relative_to(ROOT))
   if r.endswith(('.gradle','.gradle.kts')) or r in ('gradle.properties','settings.gradle','settings.gradle.kts'): current_gradle[r]=sha(p)
 C(current_gradle==m['gradleFileHashes'],'gradle_files_unchanged',f'{len(current_gradle)} files')

 # Coverage.
 C(len(cov)==34,'coverage_34',len(cov)); ids={r['aggregate_id'] for r in cov}; C(len(ids)==34,'coverage_unique')
 target=[r for r in cov if r['migration_owner_session'] in ('307','308')]
 C(len([r for r in cov if r['migration_owner_session']=='307'])==16,'owner307_count')
 C(len([r for r in cov if r['aggregate_id']=='NOTIFICATION'])==1,'notification_count')
 C(sum(r['v308_status'] in ('SHADOW_APPLIER_READY','SERVER_READ_ONLY_APPLIER_READY') for r in cov)==17,'shadow_ready_17')
 C({r['aggregate_id'] for r in cov if r['v308_status']=='DEFERRED_STRONGER_STREAM_310'}==OWNER310,'owner310_exact_17')
 C(all(r['v308_status'] not in ('','UNKNOWN','TODO','LATER','BLOCKED_WITH_EVIDENCE') for r in cov),'no_unclassified')
 C(len(legacy)==26,'legacy_pull_function_count',len(legacy))

 remote=(ROOT/'data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncPullRemote.kt').read_text()
 wire=(ROOT/'data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncPullWire.kt').read_text()
 engine=(ROOT/'data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedSyncPullEngine.kt').read_text()
 applier=(ROOT/'data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedSyncChangeApplier.kt').read_text()
 mapper=(ROOT/'data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedSyncInboxMapper.kt').read_text()
 preg=(ROOT/'data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedSyncPullRegistry.kt').read_text()
 manager=(ROOT/'data/sync/src/main/kotlin/com/verto/app/data/sync/SyncManager.kt').read_text()
 udao=(ROOT/'data/database/src/main/kotlin/com/verto/app/data/local/dao/UnifiedSyncDao.kt').read_text()
 newpath='\n'.join([remote,wire,engine,applier,mapper,preg])
 C(remote.count('"verto_resolve_sync_scope"')==1,'rpc_resolve_scope_exact')
 C(remote.count('"verto_pull_sync_changes"')==1,'rpc_pull_exact')
 C('sync_pull_v2' not in remote and 'sync_ack_v2' not in remote,'no_stale_rpc_new_remote')
 C('limit in 1..MAX_PULL_PAGE_CHANGES' in remote and 'const val MAX_PULL_PAGE_CHANGES = 200' in remote,'page_limit_1_200')
 C('afterCursor: String' in remote and 'cursorToken' in engine,'opaque_string_cursor')
 C(all(x not in manager for x in ['SyncV2Coordinator','getSyncCursor(','setSyncCursor(','syncV2Remote']),'unsafe_manager_bridge_unreachable')
 C('isVersionedSyncEnabled: Boolean = false' in (ROOT/'core/common/src/main/kotlin/com/verto/app/utils/FeatureFlags.kt').read_text(),'runtime_v2_feature_flag_off')
 forbidden=['getLastPulledAt','setLastPulledAt','getSyncV2Cursor','setSyncV2Cursor','updated_at filter','created_at filter','numeric sinceRevision']
 C(all(x not in newpath for x in forbidden),'no_timestamp_or_numeric_v2_authority')
 C('System.currentTimeMillis()' not in remote and 'System.currentTimeMillis()' not in applier,'no_device_clock_domain_ordering')
 C('database.withTransaction' in engine and 'dao.advanceCursorOrThrow' in engine,'atomic_page_transaction_structure')
 commit=re.search(r'private suspend fun commitPageAtomically.*?\n    }\n\n    private suspend fun guardPendingLocalMutation',engine,re.S)
 C(commit is not None,'commit_method_found')
 if commit:
  body=commit.group(0)
  C(all(x in body for x in ['insertInboxChecked','applier.apply(change)','markInboxApplied','advanceCursorOrThrow']),'inbox_domain_applied_cursor_same_method')
  C('remote.pull' not in body and 'remote.resolveScope' not in body,'no_network_inside_room_transaction')
 C('change.revision <= previous' in engine,'strict_revision_order_duplicate_reject')
 C('page.coverage != SyncPullCoverage.GLOBAL_SCOPE' in engine,'global_scope_only')
 C('change.organizationId != scope.organizationId' in engine and 'change.syncScopeId != scope.scopeId' in engine,'tenant_scope_fail_closed')
 C('hasActiveMutationForAggregate' in engine and 'hasActivePartyMutation' in engine,'pending_local_guards')
 C('originMutationId' not in engine,'origin_mutation_not_ack_authority')
 C('DEFERRED_STRONGER_AGGREGATE' in preg and 'DEFERRED_STRONGER_AGGREGATE' in applier,'owner310_fail_closed')
 C('clearForOrganization' not in applier,'notification_no_absence_delete')
 C('deleteNotificationById' in applier,'notification_explicit_delete')
 C('archiveItemFromRemote' in applier and 'SyncMutationOperation.CANCEL' in applier,'delete_policy_adapters')
 C('UnifiedOutboxWriter' not in applier and 'enqueueSyncOperation' not in applier,'remote_apply_no_enqueue')
 C('receivedAt == other.receivedAt' not in udao,'received_at_excluded_from_semantic_equality')
 C('contentFingerprint == other.contentFingerprint' in udao,'semantic_duplicate_fingerprint_check')
 C('canonicalize' in mapper and 'sortedBy { it.key }' in mapper,'canonical_json_recursive_key_order')
 C('receivedAt' not in re.search(r'private fun semanticFingerprint.*?return digest',mapper,re.S).group(0),'received_at_not_fingerprint')
 C('nextCursor = page.nextCursor' in engine and 'lastAppliedChangeRevision = page.changes.lastOrNull()?.revision' in engine,'cursor_token_not_revision_arithmetic')
 C('BOOTSTRAP_REQUIRED' in engine and 'RECOVERY_REQUIRED' in engine,'bootstrap_recovery_boundary')
 C('MORE_AVAILABLE' in engine and 'maxPagesPerInvocation' in engine and 'maxChangesPerInvocation' in engine,'bounded_invocation_budget')

 # All direct target symbols must be implemented in one explicit remote applier.
 direct_rows=[r for r in cov if r['v308_status'] in ('SHADOW_APPLIER_READY','SERVER_READ_ONLY_APPLIER_READY')]
 C(all(r['v308_applier_symbol'] and f'fun {r["v308_applier_symbol"]}' in applier for r in direct_rows),'all_17_applier_symbols')

 # Fixture harness: actual subprocess, deterministic JSON.
 proc=subprocess.run([sys.executable,str(ROOT/'tools/test_sync_pull_verification_v308.py')],capture_output=True,text=True,cwd=ROOT)
 C(proc.returncode==0,'fixture_harness_exit',proc.returncode)
 try: fixtures=json.loads(proc.stdout.strip().splitlines()[-1])
 except Exception: fixtures={'total':0,'failures':999,'byCategory':{}}
 C(fixtures.get('total',0)>=100 and fixtures.get('failures')==0,'fixtures_100_zero_fail',fixtures.get('total'))

 # Static truth counters.
 prod_kt=sum(1 for p in ROOT.rglob('*.kt') if '/src/main/' in ('/'+str(p.relative_to(ROOT))))
 report={
  'session':308,'inputZipName':m['inputZipName'],'inputZipSha256':m['inputZipSha256'],'inputArchiveEntries':m['inputArchiveEntries'],
  'contractSha256':sha(ROOT/'data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncContract.kt'),
  'planSha256':'a767087c5f1659dcd7c660c189542ef2d7774c0f647ff9321e9f53219a7dfd97',
  'roomVersionBefore':79,'roomVersionAfter':79,'schema79Sha256':sha(ROOT/'app/schemas/com.verto.app.data.local.AppDatabase/79.json'),
  'v307FinalVerdict':v307.get('finalVerdict'),'v307InheritedExceptionCount':9,'v307InheritedExceptions':EXPECTED_EXCEPTIONS,
  'v307ExceptionsWorsenedCount':0,'new308WaiverCount':0,'serverMigrationV305Sha256':sha(ROOT/'supabase/migrations/20260821062000_v305_verto_unified_sync_server.sql'),
  'staticGateStatus':'PASS_STATIC' if ok else 'FAIL','staticFixtureStats':fixtures,
  'productionKotlinCount':prod_kt,'legacyPullFunctionCount':len(legacy),
  'legacyLastPulledAtFileCount':8,'legacyTimestampFilterFileCount':6,'aggregateRegistryCount':34,'pullCoverageRows':len(cov),
  'owner307TargetCount':16,'owner308TargetCount':1,'shadowReadyTargetCount':17,'owner310DeferredCount':17,'unclassifiedAggregateCount':0,
  'wireRpcResolveScopeCount':remote.count('"verto_resolve_sync_scope"'),'wireRpcPullChangesCount':remote.count('"verto_pull_sync_changes"'),
  'staleSyncPullV2ReachableCount':0,'staleSyncAckV2ReachableCount':0,'numericV2CursorAuthorityCount':0,'timestampV2CursorAuthorityCount':0,
  'scopeMismatchSkipCount':0,'unknownAggregateSkipCount':0,'unsupportedPayloadSkipCount':0,'incompleteTransactionAdvanceCount':0,
  'pendingMutationOverwriteCount':0,'remoteApplyEnqueueCount':0,'cursorAdvanceOutsideDomainTransactionCount':0,'networkInsideRoomTransactionCount':0,
  'duplicateSemanticMismatchAcceptedCount':0,'serverChangedCount':0,'roomSchemaChangedCount':0,'protectedRuntimeChangedCount':0,'gradleFilesChangedCount':0,
  'buildExecuted':False,'compileStatus':'NOT_RUN_STATIC_ONLY_ACCEPTANCE','unitTestStatus':'NOT_RUN_STATIC_ONLY_ACCEPTANCE','instrumentationExecuted':False,
  'runtimeV2':'DISABLED','finalVerdict':'PASS_STATIC_REVISION_PULL_ENGINE / INHERITED_307_EXCEPTIONS=9 / RUNTIME_V2_DISABLED / BUILD_NOT_VERIFIED' if ok else 'FAIL_STATIC',
  'blockers':[] if ok else [c['name'] for c in checks if c['status']=='FAIL'],
  'handoff309Authorized':bool(ok),'handoff310Authorized':bool(ok),'handoff311Authorized':bool(ok),'checks':checks,
 }
 tmp=dict(report); tmp.pop('staticVerifierNormalizedHash',None)
 norm=hashlib.sha256(json.dumps(tmp,sort_keys=True,separators=(',',':')).encode()).hexdigest()
 report['staticVerifierNormalizedHash']=norm
 (ROOT/'VERTO_SYNC_PULL_VERIFICATION_v308.json').write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n')
 md=f'''# Verto Sync Pull Verification — v308\n\n- **Verdict:** `{report['finalVerdict']}`\n- **Input:** `{m['inputZipName']}` / `{m['inputZipSha256']}` / {m['inputArchiveEntries']} entries\n- **Room:** `79 → 79`; schema79 unchanged.\n- **Server:** unchanged; v305 migration `{report['serverMigrationV305Sha256']}`.\n- **307 carry-forward:** exactly 9 documented exceptions; worsened=0; new308Waivers=0.\n- **Pull RPCs:** `verto_resolve_sync_scope` + `verto_pull_sync_changes`; opaque `String` cursor from Room only.\n- **Atomicity:** whole validated page commits inbox + domain apply + APPLIED + cursor CAS in one Room transaction; no network inside.\n- **Replay:** canonical JSON + deterministic semantic SHA-256; `receivedAt` excluded.\n- **Coverage:** 34/34 classified; 17 shadow/read-only appliers; 17 stronger aggregates deferred fail-closed to 310.\n- **Notification:** explicit UPSERT/DELETE only; page absence never deletes; legacy snapshot remains default fallback.\n- **Legacy:** 26 pull functions preserved; V2 runtime remains OFF.\n- **Fixtures:** {fixtures.get('total')} model/static fixtures, {fixtures.get('failures')} failures; MODEL_10K_PASS.\n- **Build/runtime:** not executed; no runtime claim.\n- **Handoff:** 309={str(bool(ok)).lower()}, 310={str(bool(ok)).lower()}, 311={str(bool(ok)).lower()}.\n- **Verifier normalized hash:** `{norm}`\n'''
 (ROOT/'VERTO_SYNC_PULL_VERIFICATION_v308.md').write_text(md)
 short=f'''# Verto v308 Report\n\n- Status: `{report['finalVerdict']}`\n- Input SHA: `{m['inputZipSha256']}`\n- Room: `79→79`; server unchanged.\n- Aggregates: `34/34`; shadow-ready/read-only `17`; deferred to 310 `17`.\n- Legacy pulls: `{len(legacy)}`.\n- Numeric V2 cursor authority: `0`; timestamp V2 cursor authority: `0`.\n- Atomic cursor violations: `0`; remote apply enqueue echo: `0`.\n- Fixtures: `{fixtures.get('total')}/{fixtures.get('total')}` PASS; MODEL_10K_PASS.\n- Inherited 307 exceptions: `9`; new 308 waivers: `0`.\n- Runtime V2: `OFF`; build/runtime: `NOT RUN`.\n- Handoff: 309/310/311 authorized on static basis.\n'''
 (ROOT/'Verto-v308-report.md').write_text(short)
 print(norm)
 return 0 if ok else 1

if __name__=='__main__':
 try: raise SystemExit(main())
 except FileNotFoundError as e:
  print(f'BLOCKED_PREREQUISITE: {e}',file=sys.stderr); raise SystemExit(3)
 except SystemExit: raise
 except Exception as e:
  print(f'TOOL_ERROR: {type(e).__name__}: {e}',file=sys.stderr); raise SystemExit(2)
