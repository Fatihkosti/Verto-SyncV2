#!/usr/bin/env python3
from __future__ import annotations
import csv, hashlib, json, re, shutil, subprocess, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE=json.loads((ROOT/'tools/sync-server-fixtures/v305/baseline.json').read_text())
MIG=ROOT/'supabase/migrations/20260821062000_v305_verto_unified_sync_server.sql'
COVER=ROOT/'docs/sync/VERTO_SYNC_SERVER_WRITE_PATH_COVERAGE_v305.csv'
checks=[]
def sha(p): return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def check(name, cond, detail=''):
    checks.append({'name':name,'status':'PASS' if cond else 'FAIL','detail':detail})
    return cond
def tree(paths):
    lines=[]
    for p in sorted(paths, key=lambda x:str(x)):
        rel='./'+p.relative_to(ROOT).as_posix()
        lines.append(f'{sha(p)}  {rel}')
    return hashlib.sha256(('\n'.join(sorted(lines))+'\n').encode()).hexdigest(),len(lines)

# Source immutability.
prod=list(ROOT.glob('**/src/main/**/*.kt'))
gradle=[]
for p in ROOT.rglob('*'):
    if p.is_file() and (p.name.endswith('.gradle') or p.name.endswith('.gradle.kts') or p.name in {'gradle.properties','libs.versions.toml'}): gradle.append(p)
histsql=[p for p in ROOT.rglob('*.sql') if p!=MIG and p!=(ROOT/'docs/sql/v305_verto_unified_sync_server.sql')]
ph,pc=tree(prod); gh,gc=tree(gradle); sh,sc=tree(histsql)
check('production_kotlin_unchanged',pc==BASE['productionKotlinCount'] and ph==BASE['productionKotlinTreeHash'],f'{pc}:{ph}')
check('gradle_unchanged',gc==BASE['gradleCount'] and gh==BASE['gradleTreeHash'],f'{gc}:{gh}')
check('historical_sql_unchanged',sc==BASE['historicalSqlCount'] and sh==BASE['historicalSqlTreeHash'],f'{sc}:{sh}')
for p,h in BASE['protected'].items(): check('protected:'+p, (ROOT/p).exists() and sha(ROOT/p)==h)
room=(ROOT/'data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt').read_text()
check('room_77',bool(re.search(r'ROOM_SCHEMA_VERSION:\s*Int\s*=\s*77',room)))

# v304 authority fingerprints.
authority={
'data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncContract.kt':'9553e1801dcf756f619ea2c28fd1bf8534f18fa32e4f74c2743cedc5460a7e6c',
'data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncAggregateRegistry.kt':'9e35f020993e3caa21171ab6a311605f2bcfa58a910bf7814f22e157b5af7fe9',
'docs/sync/VERTO_SYNC_BASELINE_v303.json':'587133b69a753e279b54c742e0fa1d5b6b427152a8aa1edc47a1c06bf77269cf',
'docs/sync/VERTO_UNIFIED_SYNC_CONTRACT_v304.json':'58c22cb9fffd5114d925d063ba1d0d1491b06c9cf4f698dd7f3922d932486ca2',
'docs/sync/VERTO_UNIFIED_SYNC_CONTRACT_v304.md':'6dd4c60307fa9677e3e8a233063b869e78c34b4316bba58a1bfa0a0a045707fb',
'docs/sync/VERTO_SYNC_AGGREGATE_COVERAGE_v304.csv':'93aa250f359e90d1b0417eb5e938ff3ec9495586297e13a199f880cc8da92faf',
'docs/sync/VERTO_SYNC_SERVER_BASELINE_v304.md':'a06f6a63a8404ea79a6c14c0aabda77cfa79257367411d9ec5331557f6818928',
'VERTO_SYNC_CONTRACT_VERIFICATION_v304.json':'fd4b38b35f46dd1f449bcd648542e2b222eb4da4d724da76b8ebdd65bb7fcdf3',
'VERTO_SYNC_CONTRACT_VERIFICATION_v304.md':'f564a905100b6530ece03a78c831d58115c929723a33b0ff6dc254ba2b40345a',
'tools/verify_sync_contract_v304.py':'c3cdff33828105ef568ceeb4a830f9e77b8e39d0a93364f8b868d1e843c9eddf',
'tools/sync-contract-fixtures/v304/fixtures.json':'3af7aee782bbaf1485e3cf44ddab09c438179a385ca05d701750181001521b42'}
for p,h in authority.items(): check('v304_authority:'+p,sha(ROOT/p)==h)

# Migration shape.
migs=list((ROOT/'supabase/migrations').glob('*_v305_verto_unified_sync_server.sql'))
check('exactly_one_v305_migration',len(migs)==1)
sql=MIG.read_text()
required=['verto_sync_change_revision_seq','verto_sync_change_log','verto_sync_receipts','verto_sync_contract','verto_sync_scopes','verto_sync_bootstrap_sessions','verto_sync_bootstrap_rows','verto_assign_sync_change_revision','verto_reject_sync_change_log_mutation','verto_reject_sync_receipt_mutation','verto_resolve_sync_scope','verto_encode_sync_cursor','verto_decode_sync_cursor','verto_pull_sync_changes','verto_begin_sync_bootstrap','verto_pull_bootstrap_page','verto_get_reconciliation_manifest']
for x in required: check('required_object:'+x,x in sql)
check('no_drop',not re.search(r'\bDROP\s+(TABLE|COLUMN|FUNCTION|SEQUENCE)\b',sql,re.I))
check('no_truncate',not re.search(r'\bTRUNCATE\b',sql,re.I))
for tbl in re.findall(r'ALTER TABLE public\.([a-zA-Z0-9_]+)',sql): check('alter_new_only:'+tbl,tbl.startswith('verto_sync_'))
check('business_trigger_none',not re.search(r'CREATE TRIGGER[^;]+ ON public\.(?!verto_sync_)',sql,re.I|re.S))
check('append_only_guard','BEFORE UPDATE OR DELETE ON public.verto_sync_change_log' in sql)
check('receipt_immutable_guard','BEFORE UPDATE OR DELETE ON public.verto_sync_receipts' in sql)
check('server_revision_lock',sql.index('pg_advisory_xact_lock') < sql.index("nextval('public.verto_sync_change_revision_seq'"))
check('server_transaction_id','pg_current_xact_id()::text' in sql)
check('no_gapless_assumption','previous_revision + 1' not in sql and 'revision + 1' not in sql)
check('no_timestamp_cursor','updated_at >' not in sql and 'changed_at >' not in sql)
check('opaque_cursor_table','verto_sync_cursor_tokens' in sql and 'unknown or tampered cursor' in sql)
check('tenant_from_auth','auth.uid()' in sql and 'active Verto membership required' in sql)
check('visibility_fingerprint','employee_permissions' in sql and "extensions.digest" in sql)
check('pull_limits','v_limit < 1 OR v_limit > 200' in sql and 'cumulative_bytes <= 1048576' in sql)
check('transaction_hard_ceiling','group_bytes <= 2097152' in sql and 'transaction group exceeds 2 MiB' in sql)
check('transaction_group_boundary','PARTITION BY v.transaction_id' in sql and 'ends_at_transaction_boundary' in sql)
check('min_available','min_available_revision' in sql and 'CURSOR_EXPIRED' in sql)
check('bootstrap_same_lock',sql.count("hashtextextended('verto-sync-revision:'")>=2)
check('bootstrap_materialized','INSERT INTO public.verto_sync_bootstrap_rows' in sql and 'verto_sync_snapshot_state' in sql)
check('bootstrap_ttl_1800','1800, 200, 1048576' in sql)
check('reconciliation_sha256','verto_get_reconciliation_manifest' in sql and "'sha256'" in sql and 'ORDER BY s.aggregate_id' in sql)
check('rls_all',sql.count(' ENABLE ROW LEVEL SECURITY;')>=10)
check('least_privilege','REVOKE ALL ON TABLE public.verto_sync_contract' in sql and 'GRANT EXECUTE ON FUNCTION public.verto_pull_sync_changes(uuid,text,integer) TO authenticated;' in sql)
check('no_anon_rpc_grant','GRANT EXECUTE' not in '\n'.join([ln for ln in sql.splitlines() if ' TO anon' in ln]))
check('production_pruning_disabled','120, 120, 180' in sql and '120, 14, 14, false' in sql)

# Coverage REVISION 2.
rows=list(csv.DictReader(COVER.open()))
check('coverage_34_aggregates',len({r['aggregate_id'] for r in rows})==34)
check('coverage_no_unknown',all('UNKNOWN' not in '|'.join(r.values()) for r in rows))
check('coverage_present_classified',all(r['status'] in {'CAPTURED_ATOMIC','EXISTING_STRONGER_STREAM','SERVER_ONLY_DOCUMENTED','INERT_UNTIL_OWNER_SESSION','DEFERRED_WITH_EXPLICIT_OWNER','LOCAL_ONLY','BLOCKED_PAYLOAD_ADAPTER','BLOCKED_TENANCY_MAPPING','BLOCKED_WRITE_PATH'} for r in rows))
for agg,objs in BASE['revision2Absent'].items():
    for obj in objs:
        rr=[r for r in rows if r['aggregate_id']==agg and r['server_object']==obj]
        check('revision2_absent:'+agg+':'+obj,len(rr)==1 and rr[0]['current_server_presence']=='ABSENT_IN_EXACT_DUMP' and rr[0]['status']=='DEFERRED_WITH_EXPLICIT_OWNER')

# Fixture/model gate.
proc=subprocess.run([sys.executable,str(ROOT/'tools/test_sync_server_verification_v305.py')],capture_output=True,text=True)
model=json.loads(proc.stdout) if proc.returncode==0 else {'total':0,'passed':0,'failed':1,'stderr':proc.stderr}
check('fixture_model_70_plus',model.get('total',0)>=70 and model.get('failed',1)==0,f"{model.get('passed')}/{model.get('total')}")

static_ok=all(c['status']=='PASS' for c in checks)
db_available=bool(shutil.which('psql') and shutil.which('postgres') and shutil.which('initdb'))
final='PASS_STATIC_GATES' if static_ok else 'FAIL_STATIC_GATES'
# Full session verdict is intentionally not PASS without DB execution.
session_verdict='SERVER_STATIC_COMPLETE / DATABASE_EXECUTION_BLOCKED' if static_ok and not db_available else ('DATABASE_EXECUTION_REQUIRED' if static_ok else 'FAIL_STATIC_GATES')
report={'session':305,'staticGateStatus':final,'databaseExecutionAvailable':db_available,'databaseTestStatus':'BLOCKED_PSQL_POSTGRES_INITDB_UNAVAILABLE' if not db_available else 'NOT_RUN_BY_STATIC_VERIFIER','fixtureStats':{'total':model.get('total',0),'passed':model.get('passed',0),'failed':model.get('failed',0)},'checks':checks,'finalVerdict':session_verdict}
print(json.dumps(report,indent=2))
sys.exit(0 if static_ok else 1)
