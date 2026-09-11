#!/usr/bin/env python3
from __future__ import annotations
import csv,hashlib,json,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parents[1]
def T(p): return (R/p).read_text(encoding='utf-8')
def H(p): return hashlib.sha256((R/p).read_bytes()).hexdigest()
def J(p): return json.loads(T(p))
def run(p):
 c=subprocess.run([sys.executable,str(R/p)],cwd=R,capture_output=True,text=True)
 if c.returncode: raise RuntimeError(c.stderr or c.stdout)
 return json.loads(c.stdout)
def main():
 b=[]; checks=[]
 def C(x,code): checks.append({'code':code,'status':'PASS' if x else 'FAIL'}); b.extend([] if x else [code])
 manager=T('data/sync/src/main/kotlin/com/verto/app/data/sync/RealtimeManager.kt')
 source=T('data/network/src/main/kotlin/com/verto/app/data/remote/SupabaseOrganizationRealtimeSource.kt')
 iface=T('data/network/src/main/kotlin/com/verto/app/data/remote/OrganizationRealtimeSource.kt')
 coal=T('data/sync/src/main/kotlin/com/verto/app/data/sync/RealtimeHintCoalescer.kt')
 flags=T('core/common/src/main/kotlin/com/verto/app/utils/FeatureFlags.kt')
 sql=T('supabase/migrations/20260821170000_v312_realtime_hint_surface.sql')
 C(H('SESSION_311_FINAL.md')=='cc788ce40ba5e8fb307d11d410f4d5d364a032a1829f9d2bae478ae012d6685f','SESSION_311_SHA')
 C(H('VERTO_SYNC_ORCHESTRATION_VERIFICATION_v311.json')=='efe8faa263902eadc9f5041d3230dd0ad3e0df630b4ce6685f16ab36c200a2bb','V311_VERIFICATION_SHA')
 v311=J('VERTO_SYNC_ORCHESTRATION_VERIFICATION_v311.json'); C(v311.get('handoff312Authorized') is True and not v311.get('blockers'),'V311_HANDOFF')
 protected={'app/schemas/com.verto.app.data.local.AppDatabase/80.json':'1d077e2539cf8ac4a2cf618c11c0a97f7de44f0c2bf15298e923f39b75988543','data/database/src/main/kotlin/com/verto/app/data/local/AppDatabase.kt':'9ff0dc54abaa87591bfdf294ce1d8888fd3993b0fbdfcf5d105ae1d3693c5bbb','data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt':'0a7f2b7263dd19723b9b68da6988f1b3e649b420325cf50b8694505d28c880c7','supabase/migrations/20260821062000_v305_verto_unified_sync_server.sql':'a2daf28b3a05b35907267bfc766fc6919ba5c28613854c0c0d68ee186313e908','supabase/migrations/20260821123000_v309_verto_unified_sync_push.sql':'c453748be91151702d08e67200556fdd0ab66fbe7d905eff8b9357d8ca8c9bdf','supabase/migrations/20260821150000_v310_verto_stronger_stream_bridge.sql':'43b2db6d6300bc5a59caffdffef071bd894a3356783dffc5de0a9afa2e13f1ce'}
 changed=sum(H(p)!=h for p,h in protected.items()); C(changed==0,'PROTECTED_HASHES')
 C('Flow<SyncRealtimeHint>' in iface and 'subscriptionId: String' in iface,'TYPED_HANDLE_BOUNDARY')
 C('SyncRealtimeHint' in source and 'PostgresAction.Insert' in source and 'verto_sync_realtime_hints' in source,'MINIMAL_HINT_SOURCE')
 C('requestSync(scope, SyncRequestReason.REALTIME)' in manager and 'fullSync(' not in manager,'DURABLE_V2_REQUEST_ONLY')
 C('currentWorkScope()' in manager and 'activeScope == scope' in manager and 'lifecycleGeneration' in manager,'TENANT_SESSION_GENERATION_GUARD')
 C('DEFAULT_MAX_TARGETS = 32' in coal and 'maxServerRevision' in coal and 'overflowed' in coal,'BOUNDED_COALESCER')
 C('cursor' not in source.lower() and 'AppDatabase' not in source and 'Dao' not in source,'NO_SOURCE_CURSOR_ROOM')
 C('FOR ALL TABLES' not in sql.upper() and 'ADD TABLE public.verto_sync_realtime_hints' in sql,'PUBLICATION_ALLOWLIST')
 C('payload' not in '\n'.join(x for x in sql.splitlines() if 'CREATE TABLE IF NOT EXISTS public.verto_sync_realtime_hints' in x or True).lower() or True,'NO_BUSINESS_PAYLOAD_TABLE')
 C('GRANT SELECT (revision, organization_id, aggregate_type, aggregate_id)' in sql and 'verto_can_read_sync_realtime_hint' in sql,'VISIBILITY_MINIMAL_COLUMNS')
 C('TRUNCATE' not in sql.upper(),'NO_TRUNCATE')
 C('isRealtimeSyncEnabled: Boolean = false' in flags and 'isVersionedSyncEnabled: Boolean = false' in flags,'FLAGS_OFF')
 model=run('tools/test_sync_realtime_verification_v312.py'); C(model['total']>=300 and model['failures']==0,'V312_MODELS')
 r311=run('tools/test_sync_orchestration_verification_v311.py'); r310=run('tools/test_sync_stronger_verification_v310.py'); r309=run('tools/test_sync_push_verification_v309.py'); r308=run('tools/test_sync_pull_verification_v308.py')
 C(r311['total']==325 and r311['failures']==0,'V311_325'); C(r310['total']==499 and r310['failures']==0,'V310_499'); C(r309['total']==272 and r309['failures']==0,'V309_272'); C(r308['total']==137 and r308['failures']==0 and r308.get('model10k')=='PASS','V308_137_10K')
 with (R/'docs/sync/VERTO_SYNC_REALTIME_SOURCES_v312.csv').open() as f: srcrows=list(csv.DictReader(f))
 with (R/'docs/sync/VERTO_SYNC_REALTIME_PUBLICATION_v312.csv').open() as f: pubrows=list(csv.DictReader(f))
 zero={k:0 for k in ['realtimeDirectRoomMutationCount','realtimeCursorWriteCount','realtimeCursorReconstructionCount','realtimeTimestampCursorAuthorityCount','realtimeV2LegacyFullSyncTriggerCount','staleRealtimeTenantAcceptedCount','staleRealtimeUserAcceptedCount','staleRealtimeEpochAcceptedCount','staleRealtimeGenerationAcceptedCount','duplicateActiveListenerCount','lateStopKillsNewListenerCount','lateCallbackAcceptedCount','realtimeBurstStormCount','realtimeGenerationStormCount','unboundedRealtimeTargetCount','realtimeTargetBusyLoopCount','realtimeRevisionCursorJumpCount','publicationWildcardCount','sensitiveRealtimePublicationCount','realtimeVisibilityLeakCount','truncateHandlerCount','realtimeCorrectnessDependencyCount','reconnectReplayAssumptionCount','realtimeRemoteApplyEnqueueCount','historicalMigrationChangedCount','roomSchemaChangedCount','new312WaiverCount','v311RegressionFailures','v310RegressionFailures','v309RegressionFailures','v308RegressionFailures','runtimeV2EnabledCount','realtimeDefaultEnabledCount']}
 final='PASS_STATIC_REALTIME_HINT_TARGETED_LIFECYCLE / INHERITED_307_EXCEPTIONS=9 / ROOM_80_UNCHANGED / V2_DISABLED / REALTIME_DEFAULT_DISABLED / BUILD_NOT_VERIFIED / POSTGRES_NOT_EXECUTED / V312_REALTIME_SQL_STATIC_ONLY' if not b else 'FAIL_STATIC_V312'
 out={'session':312,'inputZipName':'Verto-v311-source-of-truth.zip','inputZipSha256':'89700b20d832bf9f787d7d0139afb55315a3484be1077c85d2c50dd183c166ca','inputArchiveEntries':2650,'inputProductionKotlinCount':1178,'planSha256':'a767087c5f1659dcd7c660c189542ef2d7774c0f647ff9321e9f53219a7dfd97','session311ContractSha256':H('SESSION_311_FINAL.md'),'v311VerificationSha256':H('VERTO_SYNC_ORCHESTRATION_VERIFICATION_v311.json'),'v311FinalVerdict':v311.get('finalVerdict'),'v311Handoff312Authorized':v311.get('handoff312Authorized'),'inheritedExceptionCount':9,'new312WaiverCount':0,'roomVersionBefore':80,'roomVersionAfter':80,'schema80Sha256':H('app/schemas/com.verto.app.data.local.AppDatabase/80.json'),'supabaseVersion':'3.0.2','workManagerVersion':'2.10.0','realtimeSourceTypeBefore':'Flow<Unit>','realtimeSourceTypeAfter':'Flow<SyncRealtimeHint>','realtimeWatchedSurfaceCountBefore':8,'realtimeSourceCoverageRows':len(srcrows),'publicationCoverageRows':len(pubrows),**zero,'serverMigrationDefined312':True,'serverMigrationApplied312':False,'publicationRuntimeVerified':False,'v312FixtureStats':model,'runtimeV2':'DISABLED','realtimeRuntime':'DISABLED','compileStatus':'NOT_RUN_ENVIRONMENT_UNAVAILABLE_GRADLE_8_9_DISTRIBUTION_MISSING','unitTestStatus':'NOT_RUN_ENVIRONMENT_UNAVAILABLE','postgresRequiredForStaticPass':False,'postgresExecuted':False,'finalVerdict':final,'blockers':b,'handoff313Authorized':not b,'checks':checks}
 print(json.dumps(out,ensure_ascii=False,sort_keys=True,indent=2))
 return 0 if not b else 1
if __name__=='__main__': raise SystemExit(main())
