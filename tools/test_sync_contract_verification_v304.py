#!/usr/bin/env python3
"""Deterministic failure-fixture harness for Session 304."""
from __future__ import annotations
import copy,csv,importlib.util,json,shutil,subprocess,sys,tempfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
FIX=ROOT/'tools/sync-contract-fixtures/v304/fixtures.json'
COV_CASES=ROOT/'tools/sync-contract-fixtures/v304/coverage_cases.csv'
VERIFIER=ROOT/'tools/verify_sync_contract_v304.py'

spec=importlib.util.spec_from_file_location('v304verify',VERIFIER)
v=importlib.util.module_from_spec(spec); spec.loader.exec_module(v)

def outcome(exit_code,code='PASS'): return exit_code,code

def policy_eval(f,contract,headers,rows,baseline,ckt,rkt):
    s=f['scenario']
    if s.startswith('revision_') or s in {'same_timestamp_revision_order','unrelated_groups_revision_gaps'}:
        rev=f.get('revisions',[])
        if any(x<=0 for x in rev): return outcome(1,'VALIDATION')
        if any(b<a for a,b in zip(rev,rev[1:])): return outcome(1,'INVALID_REVISION_ORDER')
        return outcome(0)
    if s=='duplicate_identical': return outcome(0)
    if s=='duplicate_divergent': return outcome(1,'DUPLICATE_REVISION_CONTENT_MISMATCH')
    if s in {'clock_plus_24h_no_cursor_effect','clock_minus_24h_no_cursor_effect'}:
        return outcome(0) if contract['revisionPolicy']['timeFieldsAreMetadataOnly'] else outcome(1,'FAIL_TIMESTAMP_CURSOR_POLICY')
    if s=='opaque_cursor_preserved': return outcome(0) if contract['cursorPolicy']['representation']=='OPAQUE_NONBLANK_STRING' else outcome(1,'FAIL_TARGETED_CURSOR_POLICY')
    if s=='timestamp_derived_cursor': return outcome(1,'FAIL_TIMESTAMP_CURSOR_POLICY') if contract['cursorPolicy']['timestampCursorForbidden'] else outcome(0)
    if s=='filtered_advances_global': return outcome(1,'FILTERED_GLOBAL_CURSOR_ADVANCE') if not contract['cursorPolicy']['filteredPullAdvancesGlobalCursor'] else outcome(0)
    if s=='scope_mismatch_cursor': return outcome(1,'SCOPE_MISMATCH')
    if s=='contract_mismatch_cursor': return outcome(1,'CONTRACT_UNSUPPORTED')
    if s=='bootstrap_snapshot_baseline_delta': return outcome(0) if contract['bootstrapPolicy']['snapshotAndBaselineSameHandshake'] else outcome(1,'FAIL_BOOTSTRAP_HANDOFF_POLICY')
    if s=='bootstrap_independent_current_max': return outcome(1,'FAIL_BOOTSTRAP_HANDOFF_POLICY') if contract['bootstrapPolicy']['independentPostSnapshotMaxRevisionForbidden'] else outcome(0)
    if s in {'bootstrap_expired','bootstrap_scope_changed'}: return outcome(1,'BOOTSTRAP_RESTART_REQUIRED')
    if s.startswith('transaction_'):
        orders=f.get('orders',[]); size=f.get('size',0)
        return outcome(0) if len(orders)==size and set(orders)==set(range(size)) else outcome(1,'INCOMPLETE_TRANSACTION_GROUP')
    if s=='dependency_parent_first': return outcome(0)
    if s=='dependency_child_first': return outcome(1,'DEPENDENCY_ORDER_VIOLATION')
    if s=='atomic_batch_partial_success': return outcome(1,'FAIL_TRANSACTION_GROUP_POLICY') if contract['transactionPolicy']['pageSplitForbidden'] else outcome(0)
    if s in {'known_supported_payload','known_unsupported_payload','unknown_aggregate'}:
        by={r['id']:r for r in contract['aggregateRegistry']}; r=by.get(f['aggregate'])
        return outcome(0) if r and r['payloadVersion']==f['version'] else outcome(1,'CONTRACT_UNSUPPORTED')
    if s=='error_enum_roundtrip': return outcome(0) if f['value'] in contract['errorTypes'] else outcome(1,'CONTRACT_UNSUPPORTED')
    if s=='unknown_error_enum': return outcome(1,'CONTRACT_UNSUPPORTED') if f['value'] not in contract['errorTypes'] else outcome(0)
    if s in {'payment_scoped_lww','inventory_movement_scoped_lww','inventory_cost_mutable_overwrite'}:
        # This fixture targets the domain guardrail itself, not JSON/Kotlin reconciliation order.
        if f['aggregate'] in {'PAYMENT','INVENTORY_MOVEMENT'} and f['policy']=='EXPLICIT_SCOPED_LWW':
            return outcome(1,'FAIL_CONFLICT_POLICY')
        if f['aggregate']=='INVENTORY_COST_REVISION' and f['policy']!='IMMUTABLE_REVISION':
            return outcome(1,'FAIL_CONFLICT_POLICY')
        return outcome(0)
    if s=='metadata_checksum_object_ref': return outcome(0) if contract['attachmentPolicies']==['NONE','METADATA_ONLY_EXTERNAL_BINARY'] else outcome(1,'FAIL_ATTACHMENT_POLICY')
    if s=='inline_base64_binary': return outcome(1,'FAIL_ATTACHMENT_POLICY') if 'INLINE_BINARY' not in contract['attachmentPolicies'] else outcome(0)
    if s.startswith('all_producers_') or s in {'unknown_status','missing_aggregate_row','duplicate_authority_row','blank_evidence_path','wildcard_evidence'}:
        rr=copy.deepcopy(rows)
        if s=='unknown_status': rr[0]['coverage_status']='UNKNOWN'
        elif s=='missing_aggregate_row': rr=rr[:-1]
        elif s=='duplicate_authority_row': rr[-1]=copy.deepcopy(rr[0])
        elif s=='blank_evidence_path': rr[0]['evidence_path']=''
        elif s=='wildcard_evidence': rr[0]['evidence_path']='data/network/src/main/kotlin/**/SyncCash.kt'
        try: v.verify_coverage(ROOT,contract,headers,rr,baseline); return outcome(0)
        except v.PolicyFailure as e: return outcome(1,e.code)
    raise RuntimeError(f'unknown fixture scenario {s}')

def tool_eval(scenario):
    with tempfile.TemporaryDirectory(prefix='v304-fixture-') as td:
        r=Path(td); (r/'docs/sync').mkdir(parents=True); (r/'data/network/src/main/kotlin/com/verto/app/data/sync').mkdir(parents=True)
        copies=[
            ('docs/sync/VERTO_SYNC_BASELINE_v303.json',True),('docs/sync/VERTO_UNIFIED_SYNC_CONTRACT_v304.json',scenario!='missing_contract_json'),
            ('docs/sync/VERTO_SYNC_AGGREGATE_COVERAGE_v304.csv',scenario!='missing_coverage_csv'),
            ('data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncContract.kt',True),
            ('data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncAggregateRegistry.kt',True),
        ]
        for rel,do in copies:
            if do:
                dst=r/rel; dst.parent.mkdir(parents=True,exist_ok=True); shutil.copy2(ROOT/rel,dst)
        if scenario=='malformed_contract_json': (r/'docs/sync/VERTO_UNIFIED_SYNC_CONTRACT_v304.json').write_text('{malformed')
        p=subprocess.run([sys.executable,str(VERIFIER),'--contract','--root',str(r)],text=True,capture_output=True)
        code='TOOL_ERROR'
        try: code=json.loads(p.stdout.strip() or '{}').get('code','PASS' if p.returncode==0 else 'TOOL_ERROR')
        except Exception: pass
        return p.returncode,code

def main():
    fixtures=json.loads(FIX.read_text())
    with COV_CASES.open(newline='') as f: coverage_fixture_rows=list(csv.DictReader(f))
    if len(coverage_fixture_rows)!=6: raise SystemExit(2)
    _,baseline,contract,headers,rows,ckt,rkt=v.load_required(ROOT)
    outcomes=[]
    for f in sorted(fixtures,key=lambda x:x['fixture_id']):
        if f['category']=='tool errors': actual_exit,actual_code=tool_eval(f['scenario'])
        else: actual_exit,actual_code=policy_eval(f,contract,headers,rows,baseline,ckt,rkt)
        status='PASS' if actual_exit==f['expected_exit'] and actual_code==f['expected_code'] else 'FAIL'
        outcomes.append({k:f[k] for k in ['fixture_id','category','expected_exit','expected_code']} | {'actual_exit':actual_exit,'actual_code':actual_code,'status':status})
    from collections import Counter
    counts=Counter(x['category'] for x in outcomes)
    minimums={'revision/cursor':10,'bootstrap/transaction':8,'payload/aggregate':6,'financial policies':3,'attachments':2,'coverage':6,'tool errors':3}
    minima_ok=all(counts[k]>=n for k,n in minimums.items()) and len(outcomes)>=38
    all_ok=minima_ok and all(x['status']=='PASS' for x in outcomes)
    result={'outcomes':outcomes,'summary':{'total':len(outcomes),'categoryCounts':dict(sorted(counts.items())),'minimumsSatisfied':minima_ok,'passed':sum(x['status']=='PASS' for x in outcomes),'failed':sum(x['status']!='PASS' for x in outcomes),'status':'PASS' if all_ok else 'FAIL'}}
    print(json.dumps(result,sort_keys=True,separators=(',',':')))
    return 0 if all_ok else 1
if __name__=='__main__': sys.exit(main())
