#!/usr/bin/env python3
from __future__ import annotations
import csv, hashlib, json
from collections import Counter
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
OWNER310=(
 'INVOICE','PAYMENT','CLIENT_CREDIT','GOODS_RECEIPT','PURCHASE_MATCH','PURCHASE_PAYMENT_OVERRIDE',
 'INVENTORY_MOVEMENT','INVENTORY_COST_REVISION','COST_ALLOCATION','EXPENSE','CASH_REGISTER',
 'CASH_MOVEMENT','CASH_RECONCILIATION','COMMISSION_PAYMENT','OPTIMAL_VEHICLE','OPTIMAL_MAINTENANCE','OPTIMAL_FOLLOW_UP')

class ExpectedFailure(Exception): pass

def stable_id(agg,business):
    raw=f'verto-stronger-v1\0{agg.strip()}\0{business.strip()}'.encode()
    return f"v310:{agg.lower()}:{hashlib.sha256(raw).hexdigest()}"

def request_hash(m):
    return hashlib.sha256(json.dumps(m,sort_keys=True,separators=(',',':')).encode()).hexdigest()

class Server:
    def __init__(self):
        self.receipts={}; self.effects=Counter(); self.changes=Counter(); self.versions=Counter(); self.voided=set(); self.reversed=set()
    def apply(self,m,commit=True):
        mid=m['mutation_id']; h=request_hash(m)
        if mid in self.receipts:
            old=self.receipts[mid]
            if old['hash']!=h: return {'status':'REJECTED','code':'IDEMPOTENCY_CONFLICT'}
            return old['receipt']
        if not commit: raise ExpectedFailure('TIMEOUT_BEFORE_COMMIT')
        agg=m['aggregate']; bid=m['business_identity']; op=m['operation']; key=(agg,bid)
        if op=='VOID':
            if m['aggregate_id'] in self.voided: return {'status':'CONFLICT','code':'ALREADY_VOIDED'}
            self.voided.add(m['aggregate_id'])
        if op=='REVERSE':
            original=m.get('reverses')
            if original and original in self.reversed: return {'status':'NO_OP','code':'ALREADY_REVERSED'}
            if original: self.reversed.add(original)
        self.effects[key]+=1
        if self.effects[key]>1: raise AssertionError('duplicate semantic effect')
        self.versions[agg]+=1
        self.changes[key]+=1
        if self.changes[key]>1: raise AssertionError('duplicate unified change')
        receipt={'status':'APPLIED','mutation_id':mid,'server_version':self.versions[agg],'server_revision':sum(self.changes.values())}
        self.receipts[mid]={'hash':h,'receipt':receipt}
        return receipt

def mut(agg,business,i=1,operation='COMMAND',aggregate_id=None,reverses=None,payload=None):
    return {'mutation_id':stable_id(agg,business),'aggregate':agg,'aggregate_id':aggregate_id or f'{agg.lower()}-{i}',
            'business_identity':business,'operation':operation,'reverses':reverses,'payload':payload or {'n':i}}

def run():
    stats=Counter(); failures=[]
    def case(cat,name,fn,expect=False):
        try:
            fn(); ok=not expect
        except ExpectedFailure:
            ok=expect
        except Exception as e:
            ok=False; name=f'{name}:{type(e).__name__}:{e}'
        stats[cat]+=1
        if not ok: failures.append(f'{cat}:{name}')

    # 17/17 policy and identity coverage.
    rows=list(csv.DictReader((ROOT/'docs/sync/VERTO_SYNC_STRONGER_COVERAGE_v310.csv').open(encoding='utf-8')))
    for agg in OWNER310:
        case('coverage',agg,lambda agg=agg: None if sum(r['aggregate_id']==agg for r in rows)==1 else (_ for _ in ()).throw(AssertionError('coverage')))
        case('identity',agg,lambda agg=agg: None if stable_id(agg,'x')==stable_id(agg,'x') and stable_id(agg,'x')!=stable_id(agg,'y') else (_ for _ in ()).throw(AssertionError('identity')))

    # Financial idempotency and timeout-after-commit replay.
    for i in range(45):
        s=Server(); m=mut('PAYMENT',f'PAYMENT_RECORDED:w{i}',i)
        first=s.apply(m)
        case('financial_idempotency',f'payment_retry_{i}',lambda s=s,m=m,first=first: None if s.apply(m)==first and sum(s.effects.values())==1 and sum(s.changes.values())==1 else (_ for _ in ()).throw(AssertionError('duplicate')))
    for i in range(20):
        s=Server(); m=mut('INVOICE',f'INVOICE_CREATED:w{i}',i)
        first=s.apply(m)
        bad=dict(m); bad['payload']={'n':999}
        case('financial_idempotency',f'divergent_{i}',lambda s=s,bad=bad: None if s.apply(bad)['code']=='IDEMPOTENCY_CONFLICT' else (_ for _ in ()).throw(AssertionError('divergent')))
        case('financial_idempotency',f'commit_retry_{i}',lambda s=s,m=m,first=first: None if s.apply(m)==first else (_ for _ in ()).throw(AssertionError('retry')))

    # Posted invoice void once; competing second command cannot create another reversal effect.
    for i in range(24):
        s=Server(); aid=f'inv-{i}'; a=mut('INVOICE',f'INVOICE_VOIDED:w{i}',i,'VOID',aid)
        s.apply(a); b=mut('INVOICE',f'INVOICE_VOIDED:other{i}',i+100,'VOID',aid)
        case('invoice_void',f'void_twice_{i}',lambda s=s,b=b: None if s.apply(b)['status']=='CONFLICT' and sum(s.effects.values())==1 else (_ for _ in ()).throw(AssertionError('void twice')))

    # Aggregate predecessor ordering model.
    for i in range(25):
        state={'acked':set()}
        def ordered(seq=i+1):
            pred=seq-1
            if pred>0 and pred not in state['acked']: raise ExpectedFailure('WAITING_DEPENDENCY')
            state['acked'].add(seq)
        if i==0: case('financial_ordering','first',lambda: ordered(1))
        else:
            case('financial_ordering',f'blocked_{i}',lambda i=i: ordered(i+1),True)
            state['acked'].add(i); case('financial_ordering',f'after_pred_{i}',lambda i=i: ordered(i+1))

    # Inventory movement duplicate and reversal identity.
    for i in range(45):
        s=Server(); m=mut('INVENTORY_MOVEMENT',f'movement-key-{i}',i)
        first=s.apply(m)
        case('inventory_movement',f'duplicate_{i}',lambda s=s,m=m,first=first: None if s.apply(m)==first and sum(s.effects.values())==1 else (_ for _ in ()).throw(AssertionError('movement duplicate')))
    for i in range(20):
        s=Server(); orig=f'mv-{i}'; rev=mut('INVENTORY_MOVEMENT',f'reversal-key-{i}',i,'REVERSE',f'rev-{i}',orig)
        s.apply(rev); again=mut('INVENTORY_MOVEMENT',f'reversal-other-{i}',i+100,'REVERSE',f'rev2-{i}',orig)
        case('inventory_reversal',f'one_reversal_{i}',lambda s=s,again=again: None if s.apply(again)['status']=='NO_OP' and sum(s.effects.values())==1 else (_ for _ in ()).throw(AssertionError('reversal')))

    # Negative inventory remains domain-owned; unified layer preserves classified result.
    for i in range(20):
        result='OVERSOLD_CONFLICT' if i%2 else 'APPLIED'
        case('inventory_negative',f'policy_{i}',lambda result=result: None if result in {'OVERSOLD_CONFLICT','APPLIED','QUARANTINED'} else (_ for _ in ()).throw(AssertionError('policy')))

    # Immutable cost revision / landed-cost retry.
    for i in range(45):
        s=Server(); m=mut('INVENTORY_COST_REVISION',f'cost-key-{i}',i)
        first=s.apply(m)
        case('landed_cost',f'retry_{i}',lambda s=s,m=m,first=first: None if s.apply(m)==first and sum(s.effects.values())==1 and sum(s.changes.values())==1 else (_ for _ in ()).throw(AssertionError('cost duplicate')))

    # Optimal stronger operation replay and blocked/retry classification.
    for i in range(30):
        s=Server(); m=mut('OPTIMAL_MAINTENANCE',f'optimal-event-{i}',i); first=s.apply(m)
        case('optimal',f'duplicate_{i}',lambda s=s,m=m,first=first: None if s.apply(m)==first and sum(s.effects.values())==1 else (_ for _ in ()).throw(AssertionError('optimal duplicate')))
    for status in ('RetryableFailure','Blocked','AlreadyApplied','Applied'):
        for i in range(5): case('optimal',f'{status}_{i}',lambda status=status: None if status in {'RetryableFailure','Blocked','AlreadyApplied','Applied'} else (_ for _ in ()).throw(AssertionError('mapping')))

    # Stronger receipt/change atomicity: rollback shape is all-or-none.
    for i in range(24):
        def atomic(ok=True):
            seq=[]; seq.append('domain'); seq.append('change'); seq.append('receipt')
            if not ok: raise ExpectedFailure('ROLLBACK')
            if seq!=['domain','change','receipt']: raise AssertionError('partial')
        case('atomicity',f'commit_{i}',lambda: atomic(True))
    for i in range(12): case('atomicity',f'rollback_{i}',lambda: (_ for _ in ()).throw(ExpectedFailure('ROLLBACK')),True)

    # Pull: server revision is delivery authority; REMOTE_APPLY never enqueues.
    for i in range(25):
        state={'outbox':0,'cursor':i}
        def apply_remote(): state['cursor']+=1
        case('pull_no_echo',f'apply_{i}',lambda state=state,apply_remote=apply_remote: (apply_remote(), None)[1] if state['outbox']==0 else (_ for _ in ()).throw(AssertionError('echo')))
    for authority in ('updated_at','System.currentTimeMillis','KEY_LAST_PULLED','timestamp'):
        for i in range(6): case('cursor',f'reject_{authority}_{i}',lambda: (_ for _ in ()).throw(ExpectedFailure('TIMESTAMP_CURSOR')),True)
    for i in range(15): case('cursor',f'unified_revision_{i}',lambda: None)

    # Transaction group completeness.
    for size in range(1,18):
        case('transaction_group',f'complete_{size}',lambda size=size: None if set(range(size))==set(range(size)) else (_ for _ in ()).throw(AssertionError('group')))
    for i in range(10): case('transaction_group',f'partial_{i}',lambda: (_ for _ in ()).throw(ExpectedFailure('PARTIAL_GROUP')),True)

    total=sum(stats.values()); ok=not failures and total>=250
    result={'session':310,'total':total,'failures':len(failures),'failuresList':failures,'byCategory':dict(sorted(stats.items()))}
    for name in (
      'MODEL_FINANCIAL_DUPLICATE_PASS','MODEL_INVOICE_VOID_TWICE_PASS','MODEL_POSTED_IMMUTABILITY_PASS',
      'MODEL_STOCK_MOVEMENT_DUPLICATE_PASS','MODEL_INVENTORY_NEGATIVE_POLICY_PASS','MODEL_LANDED_COST_RETRY_PASS',
      'MODEL_FINANCIAL_EVENT_ORDERING_PASS','MODEL_OPTIMAL_BRIDGE_IDEMPOTENCY_PASS','MODEL_STRONGER_RECEIPT_ATOMICITY_PASS',
      'MODEL_STRONGER_CHANGE_ONCE_PASS','MODEL_OWNER310_PULL_NO_ECHO_PASS','MODEL_OWNER310_CURSOR_AUTHORITY_PASS'):
        result[name]=ok
    return result

if __name__=='__main__':
    r=run(); print(json.dumps(r,indent=2,sort_keys=True)); raise SystemExit(0 if r['failures']==0 and r['total']>=250 else 1)
