#!/usr/bin/env python3
"""B09 offline evidence: source checks and SQLite using the REAL Room-98 exported schema.

The SQL projection/transaction scenarios below are a PYTHON MODEL, not execution of
FinancialMaterializerV2 or Room. They never close G-B09/Txx. Real production-path tests
are FinancialMaterializerV2InstrumentedTest, which require Android/Gradle.
"""
from pathlib import Path
from decimal import Decimal
import argparse, copy, hashlib, json, re, sqlite3

ROOT = Path(__file__).resolve().parents[1]
DB = ROOT / 'data/database/src/main/kotlin/com/verto/app/data/local'
NET = ROOT / 'data/network/src/main/kotlin/com/verto/app/data/sync'
PULL = ROOT / 'data/sync/src/main/kotlin/com/verto/app/data/sync/pull'
NAMES = ['Invoice','InvoiceItem','InvoiceDueInstallment','Payment','PaymentAllocation','RealizedFxEvent','InvoiceReturnDocument','InvoiceReturnLine','InvoiceReturnPaymentAllocation']
TABLES = ['invoices','invoice_items','invoice_due_installments','payments','payment_allocations','realized_fx_events','invoice_return_documents','invoice_return_lines','invoice_return_payment_allocations']
KEYS = ['header','items','dueInstallments','payments','paymentAllocations','realizedFxEvents','returnDocuments','returnLines','returnPaymentAllocations']
SCHEMA_PATH = next((ROOT / 'app/schemas').rglob('98.json'))
SCHEMA = json.loads(SCHEMA_PATH.read_text())['database']
ENTITIES = {e['tableName']:e for e in SCHEMA['entities']}
FIXTURE_PATH = ROOT/'data/sync/src/androidTest/assets/sync/b09/financial-full-v2.json'
FIXTURE = json.loads(FIXTURE_PATH.read_text())
RESULTS = []

def check(name, condition):
    if not condition: raise AssertionError(name)
    RESULTS.append(name)

def queries(path):
    text = path.read_text()
    pattern = r'@Query\(\s*("""[\s\S]*?"""|"[^"\n]*")\s*\)\s*(?:abstract\s+)?suspend\s+fun\s+(\w+)'
    return {name:token[3:-3] if token.startswith('"""') else token[1:-1] for token,name in re.findall(pattern,text)}

QUERIES = queries(DB/'dao/FinancialMaterializationDao.kt')
CURSOR_QUERY = queries(DB/'dao/UnifiedSyncCursorDao.kt')['compareAndSetAdvanceRaw']

def new_database():
    db=sqlite3.connect(':memory:',isolation_level=None); db.row_factory=sqlite3.Row
    db.execute('PRAGMA foreign_keys=ON')
    for entity in SCHEMA['entities']:
        db.execute(entity['createSql'].replace('${TABLE_NAME}',entity['tableName']))
        for index in entity['indices']:
            db.execute(index['createSql'].replace('${TABLE_NAME}',entity['tableName']))
    for view in SCHEMA.get('views',[]): db.execute(view['createSql'].replace('${VIEW_NAME}',view['viewName']))
    seed(db,'clients',id='party-1',name='Fixture party',phone='249',isDirty=0)
    seed(db,'party_roles',id='party:CUSTOMER',party_id='party-1',organization_id='org-1',role='CUSTOMER',dirty=0)
    seed(db,'sync_cursor',scope_id='scope-1',organization_id='org-1',sync_principal_id='principal-1',contract_family='verto_unified_sync',contract_version=1,scope_definition_version=1,cursor_token='cursor-0',received_cursor_token='cursor-0',state='ACTIVE')
    return db

def insert(db,table,row):
    names=list(row); db.execute('INSERT OR ABORT INTO "'+table+'" ('+','.join('"'+n+'"' for n in names)+') VALUES ('+','.join('?' for n in names)+')', [row[n] for n in names])

def seed(db,table,**values):
    """Test-only parent/authority seed, not a production defaulting policy."""
    row={}
    for column in db.execute('PRAGMA table_info("'+table+'")'):
        name,typ,required,default=column[1:5]
        if name in values: row[name]=values[name]
        elif default is not None: continue
        else: row[name]=('' if typ=='TEXT' else 0) if required else None
    insert(db,table,row)

def project(table,dto):
    row={}
    for field in ENTITIES[table]['fields']:
        prop=field['fieldPath']; name=field['columnName']
        if prop in dto: value=dto[prop]
        elif prop+'Minor' in dto: value=float(Decimal(dto[prop+'Minor'])/100)
        elif prop=='imageUri': value=''
        elif prop=='invoiceNumberSearch': value=str(dto['invoiceNumber'])
        elif prop=='voided': value=dto['lifecycleStatus']=='VOID'
        elif prop=='isDirty': value=False
        else: raise AssertionError(('unmapped business column',table,prop))
        row[name]=value
    return row

def rows(s,key):
    value=s[key]; return [value] if key=='header' else value

def projection(db,s=FIXTURE,fail_after=None):
    """SQL model, deliberately not named/materialized as the production Kotlin materializer."""
    for table,key in zip(TABLES,KEYS):
        members=rows(s,key)
        if key=='payments': members=sorted(members,key=lambda r:r['reversedPaymentId'] is not None)
        for dto in members:
            desired=project(table,dto)
            prior=db.execute('SELECT * FROM "'+table+'" WHERE id=?',(dto['id'],)).fetchone()
            if prior is None: insert(db,table,desired)
            else: assert dict(prior)==desired, 'model immutable/replay mismatch'
        if fail_after==table: raise RuntimeError('injected after '+table)
    if fail_after=='business_complete': raise RuntimeError('injected before versions')
    seed(db,'sync_entity_version',organization_id='org-1',scope_id='scope-1',version_family='FINANCIAL_INVOICE',aggregate_id='invoice-1',applied_server_version=1,observed_server_version=1,last_applied_revision=41,applied_content_hash=s['businessContentHash'],tombstone=0)
    if fail_after=='version': raise RuntimeError('injected after version')
    seed(db,'sync_inbox',scope_id='scope-1',organization_id='org-1',server_revision=41,aggregate_type='INVOICE',aggregate_id='invoice-1',operation_type='UPSERT',entity_version=1,payload_version=2,payload_json=json.dumps(s),transaction_id='transaction-1',transaction_order=0,transaction_size=1,content_fingerprint='a'*64,apply_state='APPLIED')
    if fail_after=='applied': raise RuntimeError('injected after applied')
    assert db.execute(CURSOR_QUERY, cursor_params()).rowcount==1
    if fail_after=='checkpoint': raise RuntimeError('injected after checkpoint')

def cursor_params(**change):
    result=dict(scopeId='scope-1',organizationId='org-1',syncPrincipalId='principal-1',contractFamily='verto_unified_sync',contractVersion=1,scopeDefinitionVersion=1,expectedCursor='cursor-0',nextCursor='cursor-1',lastAppliedChangeRevision=41,pageHighWatermark=41,minAvailableRevision=None,updatedAt=1)
    result.update(change); return result

def strip_hashes(value,root=True):
    if isinstance(value,dict): return {k:strip_hashes(v,False) for k,v in value.items() if not(k=='businessContentHash' or k.endswith('Hash') or (root and k in {'financialStreamVersion','expectedFinancialStreamVersion'}))}
    if isinstance(value,list): return [strip_hashes(v,False) for v in value]
    return value

def sha(text): return hashlib.sha256(text.encode()).hexdigest()

def run(output):
    dto=(NET/'FinancialSyncContractV2.kt').read_text(); mapper=(NET/'FinancialEntityMappingsV2.kt').read_text()
    check('JVM_and_Room_fixture_byte_parity',FIXTURE_PATH.read_bytes()==(ROOT/'data/sync/src/test/resources/sync/b09/financial-full-v2.json').read_bytes())
    coverage=[]
    for name,table,key in zip(NAMES,TABLES,KEYS):
        block=re.search(r'data class '+name+r'DtoV2\((.*?)\n\)',dto,re.S).group(1)
        props=re.findall(r'\bval\s+(\w+)\s*:',block)
        methods=[re.search(r'fun '+name+r'Entity.toDtoV2\(\).*?\(\n(.*?)\n\)',mapper,re.S).group(1),re.search(r'fun '+name+r'DtoV2.toRemoteEntityV2\(.*?\).*?\(\n(.*?)\n\)',mapper,re.S).group(1)]
        for direction,method in enumerate(methods):
            for prop in props: check(f'{name}:{direction}:{prop}',bool(re.search(r'\b'+prop+r'\s*=',method)))
        check(name+':fixture_has_all_fields',all(set(row)==set(props) for row in rows(FIXTURE,key)))
        coverage.append({'dto':name+'DtoV2','business_fields':len(props),'room_columns':len(ENTITIES[table]['fields'])})
    check('fixture_business_hash',sha(json.dumps(strip_hashes(FIXTURE),ensure_ascii=False,separators=(',',':')))==FIXTURE['businessContentHash'])
    for payment in FIXTURE['payments']:
        ref=next(r for r in FIXTURE['effectReferences'] if r['factType']=='PAYMENT' and r['factId']==payment['id'])
        check('payment_hash:'+payment['id'],ref['contentHash']==sha(json.dumps(payment,ensure_ascii=False,separators=(',',':'))))
    db=new_database()
    for name,query in dict(QUERIES,compareAndSetAdvanceRaw=CURSOR_QUERY).items():
        db.execute('EXPLAIN '+query,{p:None for p in re.findall(r':(\w+)',query)})
        check('schema_SQL_prepare:'+name,True)
    check('positive_party_reference',db.execute(QUERIES['hasRemotePartyReference'],dict(organizationId='org-1',partyId='party-1',role='CUSTOMER')).fetchone()[0]==1)
    check('cross_org_party_reference_rejected',db.execute(QUERIES['hasRemotePartyReference'],dict(organizationId='other',partyId='party-1',role='CUSTOMER')).fetchone()[0]==0)
    # Thirteen real-SQL rollback cuts. This proves this schema permits atomic transactions, not Room execution.
    rollback_stages=TABLES+['business_complete','version','applied','checkpoint']
    for stage in rollback_stages:
        before='\n'.join(db.iterdump()); db.execute('BEGIN IMMEDIATE')
        try: projection(db,fail_after=stage)
        except RuntimeError: db.execute('ROLLBACK')
        else: raise AssertionError('failure not injected')
        check('SQL_MODEL_rollback:'+stage,'\n'.join(db.iterdump())==before)
    db.execute('BEGIN IMMEDIATE'); projection(db); db.execute('COMMIT')
    for name,table,key in zip(NAMES,TABLES,KEYS):
        expected={row['id']:row for row in rows(FIXTURE,key)}
        field_columns={f['fieldPath']:f['columnName'] for f in ENTITIES[table]['fields']}
        for row in db.execute('SELECT * FROM "'+table+'"'):
            original=expected.pop(row['id'])
            for prop,value in original.items(): check('SQL_MODEL_roundtrip:'+table+':'+row['id']+':'+prop,row[field_columns[prop]]==value)
        check('SQL_MODEL_no_extra_or_missing:'+table,not expected)
    check('SQL_MODEL_fk_integrity',not db.execute('PRAGMA foreign_key_check').fetchall())
    check('SQL_MODEL_payment_minor_sum',db.execute('SELECT SUM(amount_minor) FROM payments').fetchone()[0]==8000)
    for table in ['cash_register_movements','inventory_movements','commission_payments','financial_outbox','sync_outbox','sync_mutation_packet']:
        check('SQL_MODEL_no_effect_producer:'+table,db.execute('SELECT COUNT(*) FROM '+table).fetchone()[0]==0)
    check('real_SQL_return_restricts_item',db.execute(QUERIES['remoteInvoiceItemHasProtectedReference'],{'id':'line-1'}).fetchone()[0]==1)
    check('real_SQL_return_delete_noop',db.execute(QUERIES['deleteRemoteInvoiceItem'],dict(organizationId='org-1',invoiceId='invoice-1',id='line-1')).rowcount==0)
    check('real_SQL_cross_org_delete_noop',db.execute(QUERIES['deleteRemoteInvoiceItem'],dict(organizationId='other',invoiceId='invoice-1',id='line-2')).rowcount==0)
    # A second RESTRICT linkage exists in the real schema: purchase invoice matching.
    seed(db,'purchase_orders',id='po-1',organization_id='org-1',supplier_id='party-1')
    seed(db,'purchase_order_lines',id='po-line-1',purchase_order_id='po-1')
    seed(db,'purchase_invoice_matches',id='match-1',organization_id='org-1',invoice_id='invoice-1',purchase_order_id='po-1')
    seed(db,'purchase_invoice_match_lines',id='match-line-1',match_id='match-1',invoice_item_id='line-2',purchase_order_line_id='po-line-1')
    check('real_SQL_purchase_match_reference',db.execute(QUERIES['remoteInvoiceItemHasProtectedReference'],{'id':'line-2'}).fetchone()[0]==1)
    check('real_SQL_purchase_match_delete_noop',db.execute(QUERIES['deleteRemoteInvoiceItem'],dict(organizationId='org-1',invoiceId='invoice-1',id='line-2')).rowcount==0)
    check('real_SQL_cursor_does_not_regress',db.execute(CURSOR_QUERY,cursor_params(expectedCursor='cursor-1',lastAppliedChangeRevision=40)).rowcount==0)
    check('real_SQL_empty_cursor_update',db.execute(CURSOR_QUERY,cursor_params(expectedCursor='cursor-1',nextCursor='cursor-2',lastAppliedChangeRevision=None,pageHighWatermark=None)).rowcount==1)
    check('real_SQL_empty_update_keeps_checkpoint',db.execute('SELECT applied_checkpoint FROM sync_cursor').fetchone()[0]==41)
    # Sequence swaps use transient disjoint slots in the same transaction.
    db.execute('BEGIN')
    for idx,row in enumerate(FIXTURE['dueInstallments']):
        assert db.execute(QUERIES['stageRemoteDueSequence'],dict(invoiceId='invoice-1',id=row['id'],temporarySequence=-2147483648+idx)).rowcount==1
    for row in FIXTURE['dueInstallments']: db.execute('UPDATE invoice_due_installments SET sequence=? WHERE id=?',(3-row['sequence'],row['id']))
    db.execute('COMMIT')
    check('real_SQL_swap_no_negative_slots',db.execute('SELECT COUNT(*) FROM invoice_due_installments WHERE sequence<1').fetchone()[0]==0)
    large=9_007_199_254_740_993
    db.execute('UPDATE invoices SET total_amount_minor=? WHERE id=?',(large,'invoice-1'))
    check('real_SQL_int64_precision',db.execute('SELECT total_amount_minor FROM invoices').fetchone()[0]==large)
    mat=(PULL/'FinancialMaterializerV2.kt').read_text(); effects=(PULL/'FinancialEffectVerifierV2.kt').read_text(); dao=(DB/'dao/FinancialMaterializationDao.kt').read_text()
    engine=(PULL/'UnifiedSyncPullEngine.kt').read_text(); stronger=(PULL/'UnifiedStrongerSyncChangeApplier.kt').read_text()
    check('production_no_replace', 'OnConflictStrategy.REPLACE' not in dao+mat and 'INSERT OR REPLACE' not in dao.upper())
    check('production_nine_abort_inserts',dao.count('@Insert(onConflict = OnConflictStrategy.ABORT)')==9)
    check('production_three_selective_updates',dao.count('@Update(onConflict = OnConflictStrategy.ABORT)')==3)
    check('production_no_effect_insert',not re.search(r'\.(insert|append|post|enqueue|capture|recalculate)\w*\(',effects))
    check('production_effects_before_authority',mat.index('effectVerifier.verify')<mat.index('versions.recordAppliedVersion'))
    inbox_coordinator=(PULL/'DurableInboxApplyCoordinator.kt').read_text()
    check('production_group_before_APPLIED',inbox_coordinator.index('completeFinancialBatch(batch)')<inbox_coordinator.index('setGroupState(group, "APPLIED"')<inbox_coordinator.index('advanceCoveredCheckpoint(scope)'))
    check('production_no_FinancialInbox_placeholder','insertFinancialInbox' not in stronger)
    check('production_hilt_injection',all('@Inject constructor' in text for text in [mat,effects,stronger]))
    check('embedded_source_contract_unchanged',sha((ROOT/'VERTO_SYNC_REPAIR_BACKLOG_AR.md').read_text().split('<!-- SOURCE_CONTRACT_BEGIN -->\n')[1].split('<!-- SOURCE_CONTRACT_END -->')[0])=='34963f943ecc8d359711aaf1f2c4fc3805085dd61c988107edd2c2875a7609e0')
    report={'status':'PASS_OFFLINE_ONLY','limitation':'Source checks + real schema SQLite / Python projection model. NOT Room/Gradle/production execution; G-B09 BLOCKED.','sqlite_version':sqlite3.sqlite_version,'schema_version':SCHEMA['version'],'schema_tables':len(ENTITIES),'schema_sha256':hashlib.sha256(SCHEMA_PATH.read_bytes()).hexdigest(),'fixture_business_hash':FIXTURE['businessContentHash'],'checked_queries':len(QUERIES)+1,'rollback_cuts':len(rollback_stages),'assertion_count':len(RESULTS),'coverage':coverage,'assertions':RESULTS,'G-B09':'BLOCKED','Room':'NOT_RUN','Txx':'NOT_RUN'}
    if output:
        output.mkdir(parents=True,exist_ok=True)
        (output/'sqlite-static-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
        (output/'sqlite-model-dump.sql').write_text('-- PYTHON MODEL on actual Room schema; NOT an Android Room dump.\n'+'\n'.join(db.iterdump())+'\n')
    print(json.dumps({k:v for k,v in report.items() if k not in ['assertions','coverage']},ensure_ascii=False,indent=2))
    db.close()

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__); parser.add_argument('--output',type=Path)
    run(parser.parse_args().output)
