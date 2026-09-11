#!/usr/bin/env python3
import argparse, json, sys
from collections import Counter

# Pure static/model fixture harness for Session 307. It intentionally does not claim Android/runtime coverage.

def rule_atomic(s):
    tx = s.find('withTransaction')
    if tx < 0: tx = s.find('inTransaction')
    if tx < 0 or 'domain_write' not in s or 'outbox_enqueue' not in s: return False
    op = s.find('{', tx); close = s.find('}', op + 1)
    domain = s.find('domain_write'); outbox = s.find('outbox_enqueue')
    inside = op >= 0 and domain > op and outbox > domain and (close < 0 or outbox < close)
    return inside and 'remote.' not in s and 'swallow_enqueue' not in s

def rule_identity(s):
    return 'stable_id' in s and not any(x in s for x in ('currentTimeMillis_as_id','random_retry_id','MAX(sequence)','AtomicLong_sequence')) and 'divergent_same_id' not in s

def rule_delete(s):
    if 'policy=ARCHIVE' in s: return 'ARCHIVE' in s and 'hard_delete' not in s
    if 'policy=VOID_OR_REVERSE' in s: return ('VOID' in s or 'REVERSE' in s) and 'hard_delete' not in s
    if 'policy=CANCEL_STATE_TRANSITION' in s: return 'CANCEL' in s and 'hard_delete' not in s
    if 'policy=NO_CLIENT_DELETE' in s or 'policy=SERVER_OWNED' in s: return 'local_delete' not in s
    if 'policy=VERSIONED_DELETE' in s or 'policy=TOMBSTONE' in s: return 'DELETE' in s and 'durable' in s
    return False

def rule_origin(s):
    if 'REMOTE_APPLY' in s or 'CACHE_HYDRATION' in s: return 'outbox_enqueue' not in s
    if 'LOCAL_MUTATION' in s: return 'outbox_enqueue' in s or 'stronger_outbox' in s
    return False

def rule_stronger(s):
    if 'stronger_outbox' not in s: return False
    return ('same_tx' in s and 'generic_duplicate' not in s and 'domain_write' in s)

def rule_coverage(s):
    return 'matrix_row' in s and 'exact_exclusion' not in s and 'wildcard_exclusion' not in s and 'UNKNOWN' not in s and 'TODO' not in s

def rule_attachment(s):
    return all(x in s for x in ('metadata','transfer_intent','checksum','object_key')) and not any(x in s for x in ('base64','binary_payload','file_bytes'))

def rule_org_settings(s):
    return all(x in s for x in ('room_state','outbox_enqueue','same_tx')) and s.index('room_state') < s.index('outbox_enqueue') and ('cache_after_commit' in s or 'remote_hydration_no_enqueue' in s) and 'datastore_only' not in s

def rule_tenant(s):
    return 'trusted_org' in s and 'blank_org' not in s and 'payload_org_authority' not in s

def rule_dependency(s):
    return 'self_dependency' not in s and 'two_node_cycle' not in s and 'cross_org_dependency' not in s and 'negative_order' not in s

rules = {
    'atomicity': rule_atomic,
    'identity': rule_identity,
    'delete': rule_delete,
    'origins': rule_origin,
    'specialized_outbox': rule_stronger,
    'coverage': rule_coverage,
    'attachment': rule_attachment,
    'organization_settings': rule_org_settings,
    'tenant': rule_tenant,
    'dependency': rule_dependency,
}

fixtures = []
def add(cat, name, sample, expected): fixtures.append((cat,name,sample,expected))

# 8 meaningful fixtures per category = 80 total.
for i,s,e in [
(1,'withTransaction { domain_write; outbox_enqueue }',True),(2,'inTransaction { domain_write; outbox_enqueue }',True),
(3,'domain_write; outbox_enqueue',False),(4,'withTransaction { domain_write }; outbox_enqueue',False),
(5,'withTransaction { domain_write; swallow_enqueue }',False),(6,'withTransaction { domain_write; remote.upsert; outbox_enqueue }',False),
(7,'withTransaction { outbox_enqueue; domain_write }',False),(8,'withTransaction { domain_write; outbox_enqueue; compatibility_after }',True)]: add('atomicity',f'A{i}',s,e)
for i,s,e in [
(1,'stable_id payload',True),(2,'stable_id retry stable_id',True),(3,'currentTimeMillis_as_id',False),(4,'stable_id random_retry_id',False),
(5,'stable_id divergent_same_id',False),(6,'stable_id MAX(sequence)',False),(7,'stable_id AtomicLong_sequence',False),(8,'stable_id command_batch',True)]: add('identity',f'I{i}',s,e)
for i,s,e in [
(1,'policy=ARCHIVE ARCHIVE durable',True),(2,'policy=ARCHIVE hard_delete ARCHIVE',False),(3,'policy=VOID_OR_REVERSE VOID durable',True),(4,'policy=VOID_OR_REVERSE hard_delete',False),
(5,'policy=CANCEL_STATE_TRANSITION CANCEL durable',True),(6,'policy=CANCEL_STATE_TRANSITION hard_delete',False),(7,'policy=NO_CLIENT_DELETE local_delete',False),(8,'policy=VERSIONED_DELETE DELETE durable',True)]: add('delete',f'D{i}',s,e)
for i,s,e in [
(1,'REMOTE_APPLY dao_write',True),(2,'REMOTE_APPLY outbox_enqueue',False),(3,'CACHE_HYDRATION dao_write',True),(4,'CACHE_HYDRATION outbox_enqueue',False),
(5,'LOCAL_MUTATION outbox_enqueue',True),(6,'LOCAL_MUTATION stronger_outbox',True),(7,'LOCAL_MUTATION dirty_only',False),(8,'REMOTE_APPLY mark_clean',True)]: add('origins',f'O{i}',s,e)
for i,s,e in [
(1,'stronger_outbox same_tx domain_write',True),(2,'stronger_outbox same_tx domain_write generic_duplicate',False),(3,'stronger_outbox domain_write',False),(4,'same_tx domain_write',False),
(5,'stronger_outbox same_tx domain_write audit',True),(6,'stronger_outbox same_tx domain_write operation_id',True),(7,'stronger_outbox generic_duplicate domain_write',False),(8,'stronger_outbox same_tx domain_write lease',True)]: add('specialized_outbox',f'S{i}',s,e)
for i,s,e in [
(1,'matrix_row exact_symbol',True),(2,'matrix_row evidence',True),(3,'missing_row',False),(4,'matrix_row wildcard_exclusion',False),
(5,'matrix_row UNKNOWN',False),(6,'matrix_row TODO',False),(7,'matrix_row exact_exclusion',False),(8,'matrix_row final_status',True)]: add('coverage',f'C{i}',s,e)
for i,s,e in [
(1,'metadata transfer_intent checksum object_key',True),(2,'metadata transfer_intent checksum object_key mime',True),(3,'metadata transfer_intent checksum object_key base64',False),(4,'metadata transfer_intent object_key',False),
(5,'metadata transfer_intent checksum object_key binary_payload',False),(6,'metadata transfer_intent checksum object_key file_bytes',False),(7,'metadata checksum object_key',False),(8,'metadata transfer_intent checksum object_key local_uri',True)]: add('attachment',f'T{i}',s,e)
for i,s,e in [
(1,'room_state outbox_enqueue same_tx cache_after_commit',True),(2,'room_state outbox_enqueue same_tx remote_hydration_no_enqueue',True),(3,'datastore_only',False),(4,'room_state same_tx cache_after_commit',False),
(5,'outbox_enqueue room_state same_tx cache_after_commit',False),(6,'room_state outbox_enqueue same_tx datastore_only',False),(7,'room_state outbox_enqueue same_tx cache_after_commit legacy_remote_after',True),(8,'room_state outbox_enqueue same_tx remote_hydration_no_enqueue cache_after_commit',True)]: add('organization_settings',f'G{i}',s,e)
for i,s,e in [
(1,'trusted_org',True),(2,'trusted_org session',True),(3,'blank_org',False),(4,'trusted_org blank_org',False),(5,'payload_org_authority',False),(6,'trusted_org payload_org_authority',False),(7,'trusted_org fail_closed',True),(8,'trusted_org scoped',True)]: add('tenant',f'N{i}',s,e)
for i,s,e in [
(1,'command_order dependency',True),(2,'command_batch',True),(3,'self_dependency',False),(4,'two_node_cycle',False),(5,'cross_org_dependency',False),(6,'negative_order',False),(7,'command_order depends_previous',True),(8,'batch stable_order',True)]: add('dependency',f'P{i}',s,e)

def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--json-out'); args=ap.parse_args()
    failures=[]; bycat=Counter()
    for cat,name,sample,expected in fixtures:
        got=rules[cat](sample); bycat[cat]+=1
        if got != expected: failures.append({'category':cat,'name':name,'expected':expected,'got':got})
    result={'session':307,'kind':'STATIC_MODEL_FIXTURES','total':len(fixtures),'passed':len(fixtures)-len(failures),'failed':len(failures),'categories':dict(sorted(bycat.items())),'failures':failures}
    text=json.dumps(result,ensure_ascii=False,sort_keys=True,indent=2)+'\n'
    if args.json_out: open(args.json_out,'w',encoding='utf-8').write(text)
    print(text,end='')
    return 0 if not failures and len(fixtures)>=70 else 1
if __name__=='__main__': sys.exit(main())
