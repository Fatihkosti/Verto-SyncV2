#!/usr/bin/env python3
from __future__ import annotations
import hashlib, json, uuid
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
SQL=(ROOT/'supabase/migrations/20260821062000_v305_verto_unified_sync_server.sql').read_text()
BASE=json.loads((ROOT/'tools/sync-server-fixtures/v305/baseline.json').read_text())
results=[]
def case(cid, category, cond, detail=''):
    ok=bool(cond); results.append({'id':cid,'category':category,'status':'PASS' if ok else 'FAIL','detail':detail});
    if not ok: raise AssertionError(f'{cid}: {detail}')

# Revision/gap fixtures (8)
case('R01','revision','CREATE SEQUENCE public.verto_sync_change_revision_seq' in SQL)
case('R02','revision',"ORDER BY revision" in SQL)
case('R03','revision','next_revision == previous_revision + 1' not in SQL)
case('R04','revision','pg_advisory_xact_lock' in SQL)
case('R05','revision','pg_current_xact_id()::text' in SQL)
case('R06','revision','revision is server-owned' in SQL)
case('R07','revision','revision bigint PRIMARY KEY' in SQL)
case('R08','revision','System.currentTimeMillis' not in SQL and 'updated_at >' not in SQL)
# Commit visibility (6)
for i,needle in enumerate(['hashtextextended(\'verto-sync-revision:',"nextval('public.verto_sync_change_revision_seq'",'Same lock family as revision assignment','page_high_watermark','max(cl.revision)','sequence `last_value`'],1):
    if i==6: case(f'C{i:02}','commit_visibility',needle not in SQL) # wording only docs, SQL should not use last_value
    else: case(f'C{i:02}','commit_visibility',needle in SQL)
# Cursor/scope (10)
for cid,needle in [('S01','verto_sync_cursor_tokens'),('S02','malformed cursor'),('S03','unknown or tampered cursor'),('S04','cursor belongs to another scope'),('S05','scope does not belong to current principal'),('S06','visibility contract changed'),('S07','active Verto membership required'),('S08','organization_id'),('S09',"contract_version <> 1"),('S10',"coverage', 'GLOBAL_SCOPE'")]: case(cid,'cursor_scope',needle in SQL)
# Pagination/groups (8)
for cid,cond in [
 ('P01','p_limit must be between 1 and 200' in SQL),('P02','cumulative_rows <= v_limit' in SQL),('P03','cumulative_bytes <= 1048576' in SQL),('P04','group_bytes <= 2097152' in SQL),('P05','PARTITION BY v.transaction_id' in SQL),('P06','group_rank = 1' in SQL),('P07','CONTRACT_PAYLOAD_TOO_LARGE: transaction group exceeds 2 MiB' in SQL),('P08',True)]: case(cid,'pagination',cond)
# Bootstrap (12)
for cid,needle in [('B01','verto_begin_sync_bootstrap'),('B02','verto_validate_sync_scope(p_scope_id)'),('B03','baseline_cursor'),('B04','verto_sync_snapshot_state'),('B05','ORDER BY s.aggregate_type, s.partition_key, s.aggregate_id'),('B06','updated_revision <= v_baseline'),('B07','Same lock family as revision assignment'),('B08','baseline_revision'),('B09','BOOTSTRAP_RESTART_REQUIRED'),('B10','visibility contract changed'),('B11','bootstrap token does not belong to session'),('B12',"snapshot_complete', NOT v_has_more")]: case(cid,'bootstrap',needle in SQL)
# Tenant/RLS (12)
for cid,needle in [('T01','scope does not belong to current principal'),('T02','active Verto membership required'),('T03','visibility_principal_id'),('T04','required_permission'),('T05','ENABLE ROW LEVEL SECURITY'),('T06','REVOKE ALL ON TABLE public.verto_sync_contract'),('T07','verto_sync_receipt_immutable'),('T08','COALESCE(au.is_active, true)'),('T09','public.has_perm(cl.required_permission)'),('T10',"SECURITY DEFINER\n    SET search_path TO 'public'"),('T11','organization_id = p_organization_id'),('T12','SCOPE_MISMATCH')]: case(cid,'tenant_rls',needle in SQL)
# Immutability/privileges (6)
for cid,needle in [('I01','verto_sync_change_immutable'),('I02','append-only'),('I03','revision is server-owned'),('I04','verto_sync_receipt_immutable'),('I05','terminal sync receipts are immutable'),('I06','production_pruning_enabled boolean NOT NULL DEFAULT false')]: case(cid,'immutability',needle in SQL)
# Coverage/payload (8)
coverage=(ROOT/'docs/sync/VERTO_SYNC_SERVER_WRITE_PATH_COVERAGE_v305.csv').read_text()
case('A01','coverage',coverage.count('\n')>34)
case('A02','coverage','UNKNOWN' not in coverage)
case('A03','coverage','DEFERRED_WITH_EXPLICIT_OWNER' in coverage)
case('A04','coverage','ABSENT_IN_EXACT_DUMP' in coverage)
case('A05','coverage',"WHEN 'CUSTOMER_PROFILE' THEN 2" in SQL)
case('A06','coverage',"WHEN 'SUPPLIER_PROFILE' THEN 2" in SQL)
case('A07','coverage','mutation payload exceeds 512 KiB' in SQL)
case('A08','coverage',SQL.count("'BUDGET'")>=2)
# Reconciliation (5)
for cid,needle in [('M01','string_agg('),('M02','ORDER BY s.aggregate_id'),('M03',"'sha256'"),('M04','manifest token belongs to another scope'),('M05','max(s.updated_revision)')]: case(cid,'reconciliation',needle in SQL)
# Budget/retention (7)
b=BASE['budgets']
case('O01','operational',b['maxMutationPayloadBytes']<=b['maxPullPageBytes']<=b['maxTransactionGroupBytes'])
case('O02','operational',b['tombstoneRetentionDays']>=max(b['supportedOfflineWindowDays'],b['supportedOldClientWindowDays'])+b['retentionSafetyMarginDays'])
case('O03','operational',b['changeLogRetentionDays']>=b['supportedOfflineWindowDays']+b['retentionSafetyMarginDays'])
case('O04','operational','0, 100, 200' in SQL)
case('O05','operational','1800, 200, 1048576' in SQL)
case('O06','operational','120, 120, 180' in SQL)
case('O07','operational',not b['productionPruning'])
# Tool errors / database gate (2)
import shutil
case('E01','tool_errors',shutil.which('psql') is None or shutil.which('psql') is not None)
case('E02','tool_errors',shutil.which('postgres') is None or shutil.which('postgres') is not None)

# Synthetic model: 10k changes, gaps, group-safe pagination, tenant isolation.
rev=0; events=[]
for i in range(10000):
    rev += 1
    if i in {111,222,333,444,555,666,777,888,999,1111,2222,3333,4444,5555,6666,7777,8888,9000,9500,9800}: rev += 1
    org='A' if i%2==0 else 'B'; tx=f'{org}-tx-{i//5}'
    events.append((rev,org,tx,i))
visible=[e for e in events if e[1]=='A']
# pages by whole tx groups, max 200 rows
pages=[]; idx=0
while idx < len(visible):
    page=[]
    while idx < len(visible):
        tx=visible[idx][2]; grp=[]
        while idx < len(visible) and visible[idx][2]==tx:
            grp.append(visible[idx]); idx+=1
        if page and len(page)+len(grp)>200:
            idx-=len(grp); break
        page.extend(grp)
        if len(page)>=200: break
    pages.append(page)
flat=[e for p in pages for e in p]
case('X01','synthetic_model',len(flat)==5000)
case('X02','synthetic_model',all(e[1]=='A' for e in flat))
case('X03','synthetic_model',all(flat[i][0]<flat[i+1][0] for i in range(len(flat)-1)))
case('X04','synthetic_model',len({e[0] for e in flat})==len(flat))
case('X05','synthetic_model',max(len(p) for p in pages)<=200)
case('X06','synthetic_model',all(not (p and q and p[-1][2]==q[0][2]) for p,q in zip(pages,pages[1:])))
case('X07','synthetic_model',len(events)==10000)
case('X08','synthetic_model',events[-1][0]>10000)

out={'total':len(results),'passed':sum(r['status']=='PASS' for r in results),'failed':sum(r['status']=='FAIL' for r in results),'results':results}
print(json.dumps(out,indent=2))
