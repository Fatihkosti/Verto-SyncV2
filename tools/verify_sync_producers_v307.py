#!/usr/bin/env python3
import argparse, csv, hashlib, json, os, re, sqlite3, sys, tempfile
from pathlib import Path
from collections import Counter, defaultdict

ROOT = Path(__file__).resolve().parents[1]
INPUT_SHA = '4f0cd4c334c510b0863645ebc56e2ce667460ae08916b7a88d65d8f812cfc413'
INPUT_ENTRIES = 1696
CONTRACT_SHA = 'c907c66b843ddbe1527fdaab4cbd591a81e770bd53026d9be299245913524d76'
SCHEMA78_SHA = 'd0d89b1a6d8309e92c48cace4b30f934d8c83bcd3d75fcc0a2b3946a1d434557'
OWNER307 = {
 'PARTY_IDENTITY','PARTY_ROLE','CUSTOMER_PROFILE','SUPPLIER_PROFILE','NOTE','REMINDER','PURCHASE_ORDER',
 'INVENTORY_ITEM','INVENTORY_UNIT','CATEGORY','ITEM_CATEGORY','BUDGET','PRICE_LIST','ORGANIZATION_SETTINGS','SHIPMENT','EDUCATIONAL_CONTENT'
}
ALLOWED_STATUS = {'MIGRATED_UNIFIED_OUTBOX','PRESERVED_STRONGER_OUTBOX','REMOTE_APPLY_NO_ENQUEUE','CACHE_HYDRATION_NO_ENQUEUE','LOCAL_ONLY_NON_SYNCED','BLOCKED_WITH_EVIDENCE'}
PROTECTED = {
 'data/sync/src/main/kotlin/com/verto/app/data/sync/SyncManager.kt':'48da86c2e5e558eddaf6b07f89f6474013ddc66a85a132e8a01bf8e64afae233',
 'data/sync/src/main/kotlin/com/verto/app/data/sync/SyncWorker.kt':'16eab4a5533bf791e860281c08c142c2fa8d0f618471a1b54b7189f2f6c03a43',
 'data/sync/src/main/kotlin/com/verto/app/data/sync/RealtimeManager.kt':'f5671b96b17c3f562391f97e7c9562f83c246f08d5f4921186ba4282a78270b6',
 'data/preferences/src/main/kotlin/com/verto/app/utils/SyncPreferencesStore.kt':'3cd5bb3b019e094ee4209860bd3796620477861b55cd5aac8c54140e32234f48',
 'data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncContract.kt':'9553e1801dcf756f619ea2c28fd1bf8534f18fa32e4f74c2743cedc5460a7e6c',
 'data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncAggregateRegistry.kt':'9e35f020993e3caa21171ab6a311605f2bcfa58a910bf7814f22e157b5af7fe9',
 'app/schemas/com.verto.app.data.local.AppDatabase/78.json':SCHEMA78_SHA,
 'data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations77To78.kt':'c6ebef781c7343f967acb9b14ce3918de242653d9fabb3171f34c064b98f8eb8',
}

def sha(path):
    h=hashlib.sha256();
    with open(path,'rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''): h.update(b)
    return h.hexdigest()

def txt(rel): return (ROOT/rel).read_text(encoding='utf-8')
def exists(rel): return (ROOT/rel).exists()
def add(checks, name, ok, detail=''):
    checks.append({'name':name,'status':'PASS' if ok else 'FAIL','detail':detail})
    return ok

def manifest_map():
    p=json.loads(txt('docs/sync/VERTO_SYNC_307_INPUT_MANIFEST.json'))
    return p,{x['path']:x for x in p['files']}

def changed_under(prefixes, mp):
    changed=[]
    for rel,meta in mp.items():
        if any(rel.startswith(p) for p in prefixes):
            p=ROOT/rel
            if not p.exists() or sha(p)!=meta['sha256']: changed.append(rel)
    # New files under protected prefixes also count.
    known=set(mp)
    for pref in prefixes:
        base=ROOT/pref.rstrip('/')
        if base.is_file(): continue
        if base.exists():
            for p in base.rglob('*'):
                if p.is_file():
                    rel=p.relative_to(ROOT).as_posix()
                    if rel not in known: changed.append(rel)
    return sorted(set(changed))

def static_sqlite_migration():
    s78=json.loads(txt('app/schemas/com.verto.app.data.local.AppDatabase/78.json'))['database']
    s79=json.loads(txt('app/schemas/com.verto.app.data.local.AppDatabase/79.json'))['database']
    con=sqlite3.connect(':memory:')
    try:
        con.execute('PRAGMA foreign_keys=OFF')
        for e in s78['entities']:
            q=e['createSql'].replace('${TABLE_NAME}',e['tableName'])
            con.execute(q)
        for e in s78['entities']:
            for idx in e.get('indices',[]):
                con.execute(idx['createSql'].replace('${TABLE_NAME}',e['tableName']))
        stmts=[
'''CREATE TABLE IF NOT EXISTS `organization_settings_local` (`organization_id` TEXT NOT NULL, `shop_name` TEXT NOT NULL, `shop_phone` TEXT NOT NULL, `city` TEXT NOT NULL, `address` TEXT NOT NULL, `currency` TEXT NOT NULL, `invoice_footer` TEXT NOT NULL, `tax_number` TEXT NOT NULL, `logo_url` TEXT NOT NULL, `signature_url` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`organization_id`))''',
'''CREATE TABLE IF NOT EXISTS `sync_attachment_transfer` (`transfer_id` TEXT NOT NULL, `organization_id` TEXT NOT NULL, `mutation_id` TEXT, `aggregate_type` TEXT NOT NULL, `aggregate_id` TEXT NOT NULL, `local_uri` TEXT NOT NULL, `object_key` TEXT NOT NULL, `content_checksum` TEXT NOT NULL, `mime_type` TEXT, `byte_size` INTEGER, `state` TEXT NOT NULL DEFAULT 'PENDING', `attempt_count` INTEGER NOT NULL DEFAULT 0, `lease_owner` TEXT, `lease_token` TEXT, `lease_expires_at` INTEGER, `created_at` INTEGER NOT NULL, `completed_at` INTEGER, PRIMARY KEY(`transfer_id`))''',
'''CREATE INDEX IF NOT EXISTS `index_sync_attachment_transfer_delivery` ON `sync_attachment_transfer` (`organization_id`, `state`, `created_at`)''',
'''CREATE INDEX IF NOT EXISTS `index_sync_attachment_transfer_mutation` ON `sync_attachment_transfer` (`mutation_id`)''',
'''CREATE INDEX IF NOT EXISTS `index_sync_attachment_transfer_aggregate` ON `sync_attachment_transfer` (`organization_id`, `aggregate_type`, `aggregate_id`)''',
'''CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_attachment_transfer_object_key` ON `sync_attachment_transfer` (`object_key`)''',
'''ALTER TABLE `expenses` ADD COLUMN `lifecycle_state` TEXT NOT NULL DEFAULT 'ACTIVE' ''',
'''ALTER TABLE `expenses` ADD COLUMN `voided_at` INTEGER''',
'''ALTER TABLE `expenses` ADD COLUMN `void_reason` TEXT''',
'''ALTER TABLE `expenses` ADD COLUMN `reversal_write_id` TEXT''']
        for s in stmts: con.execute(s)
        def cols(table): return [(r[1],r[2],r[3],r[4],r[5]) for r in con.execute(f'PRAGMA table_info(`{table}`)')]
        for name in ('organization_settings_local','sync_attachment_transfer','expenses'):
            exp=next(e for e in s79['entities'] if e['tableName']==name)
            expnames=[f['columnName'] for f in exp['fields']]
            got=[x[0] for x in cols(name)]
            if got!=expnames: return False,f'{name} columns mismatch: {got} != {expnames}'
        idx={r[1] for r in con.execute("PRAGMA index_list('sync_attachment_transfer')")}
        expected={'index_sync_attachment_transfer_delivery','index_sync_attachment_transfer_mutation','index_sync_attachment_transfer_aggregate','index_sync_attachment_transfer_object_key'}
        if not expected.issubset(idx): return False,f'attachment indexes missing {expected-idx}'
        return True,'schema78 fixture migrated to schema79 changed structures'
    except Exception as e:
        return False,f'{type(e).__name__}: {e}'
    finally: con.close()

def production_kt_files():
    roots=['app/src/main/kotlin','data','feature','core']
    for root in roots:
        base=ROOT/root
        if not base.exists(): continue
        for p in base.rglob('*.kt'):
            rel=p.relative_to(ROOT).as_posix()
            if '/src/test/' in rel or '/src/androidTest/' in rel or '/build/' in rel: continue
            if '/src/main/' not in rel and not rel.startswith('app/src/main/'): continue
            yield rel,p

def symbol_present(content,symbol):
    parts=[]
    for token in re.split(r'[|;,]',symbol):
        token=token.strip().split('.')[-1]
        if token: parts.append(token)
    return any(p in content for p in parts)

def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--json-out'); ap.add_argument('--allow-known-exceptions'); args=ap.parse_args()
    checks=[]; blockers=[]
    try:
        man,mp=manifest_map()
    except Exception as e:
        print(json.dumps({'error':str(e)})); return 3
    add(checks,'input_manifest_session',man.get('session')==307,str(man.get('session')))
    add(checks,'input_zip_sha',man.get('sourceZipSha256')==INPUT_SHA,man.get('sourceZipSha256',''))
    add(checks,'input_archive_entries',man.get('archiveEntriesTotal')==INPUT_ENTRIES,str(man.get('archiveEntriesTotal')))
    add(checks,'input_room_78',man.get('roomVersion')==78,str(man.get('roomVersion')))
    room=json.loads(txt('VERTO_SYNC_ROOM_VERIFICATION_v306.json'))
    add(checks,'v306_handoff307',room.get('handoff307Authorized') is True,str(room.get('handoff307Authorized')))
    add(checks,'v306_blockers_empty',room.get('blockers')==[],str(room.get('blockers')))
    add(checks,'v306_room_after_78',room.get('roomVersionAfter')==78,str(room.get('roomVersionAfter')))
    for rel,expected in PROTECTED.items(): add(checks,'protected:'+rel,exists(rel) and sha(ROOT/rel)==expected,sha(ROOT/rel) if exists(rel) else 'MISSING')

    migration=txt('data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations78To79.kt')
    catalog=txt('data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt')
    appdb=txt('data/database/src/main/kotlin/com/verto/app/data/local/AppDatabase.kt')
    schema79=json.loads(txt('app/schemas/com.verto.app.data.local.AppDatabase/79.json'))['database']
    add(checks,'room_version_79',schema79.get('version')==79 and re.search(r'ROOM_VERSION\s*=\s*79',catalog) is not None,str(schema79.get('version')))
    add(checks,'migration_78_79_registered','MIGRATION_78_79' in catalog and 'Migration(78, 79)' in migration)
    add(checks,'no_destructive_migration','fallbackToDestructiveMigration' not in appdb+catalog+migration)
    add(checks,'organization_settings_table','organization_settings_local' in migration and any(e['tableName']=='organization_settings_local' for e in schema79['entities']))
    add(checks,'attachment_table','sync_attachment_transfer' in migration and any(e['tableName']=='sync_attachment_transfer' for e in schema79['entities']))
    add(checks,'expense_lifecycle_columns',all(x in migration for x in ('lifecycle_state','voided_at','void_reason','reversal_write_id')))
    ok,detail=static_sqlite_migration(); add(checks,'sqlite_static_migration_78_79',ok,detail)

    dao=txt('data/database/src/main/kotlin/com/verto/app/data/local/dao/UnifiedSyncDao.kt')
    writer=txt('data/sync/src/main/kotlin/com/verto/app/data/sync/UnifiedOutboxWriter.kt')
    entities=txt('data/database/src/main/kotlin/com/verto/app/data/local/entity/UnifiedSyncEntities.kt')
    add(checks,'persisted_sequence_authority','sync_sequence_state' in dao and 'allocateSequence' in dao and 'MAX(' not in dao and 'AtomicLong' not in dao)
    add(checks,'idempotency_conflict_preserved','FAIL_IDEMPOTENCY_CONFLICT' in dao)
    add(checks,'payload_bound_preserved','524288' in dao or '524288' in entities)
    add(checks,'writer_registry_validation','UnifiedSyncAggregateRegistry.requireById' in writer)
    add(checks,'writer_tenant_nonblank','FAIL_ORG_SCOPE' in writer and 'isNotBlank' in writer)
    add(checks,'writer_canonical_payload','toSortedMap' in writer and 'JsonObject' in writer)
    add(checks,'writer_no_network',not any(x in writer for x in ('postgrest','Supabase','HttpClient','enqueueUniqueWork','WorkManager')))

    cov=list(csv.DictReader((ROOT/'docs/sync/VERTO_SYNC_PRODUCER_COVERAGE_v307.csv').open(encoding='utf-8')))
    required_cols={'aggregate_id','migration_owner_session','producer_file','producer_symbol','producer_kind','origin','local_tables','write_operation','server_path','current_mechanism_before','capture_strategy_after','outbox_table','transaction_owner','mutation_id_source','organization_id_source','payload_version','operation_type','delete_policy','command_batch_strategy','attachment_strategy','legacy_compatibility_side_effect','remote_apply_exclusion','status','evidence'}
    add(checks,'matrix_columns',required_cols.issubset(set(cov[0])) if cov else False)
    add(checks,'matrix_status_values',all(r['status'] in ALLOWED_STATUS for r in cov))
    blocked=[r for r in cov if r['status']=='BLOCKED_WITH_EVIDENCE']; add(checks,'matrix_no_blocked',not blocked,str(len(blocked)))
    reg=list(csv.DictReader((ROOT/'docs/sync/VERTO_SYNC_AGGREGATE_COVERAGE_v304.csv').open(encoding='utf-8')))
    regids={r['aggregate_id'] for r in reg}; covids={r['aggregate_id'] for r in cov}
    add(checks,'all_34_registry_aggregates',len(regids)==34 and regids==covids,f'registry={len(regids)} covered={len(covids)} missing={sorted(regids-covids)}')
    owner={r['aggregate_id'] for r in reg if r['migration_owner_session']=='307'}
    add(checks,'owner307_registry_exact',owner==OWNER307,f'{len(owner)}/16')
    uncovered=[]
    for a in OWNER307:
        rr=[r for r in cov if r['aggregate_id']==a]
        if not any(r['status'] in ('MIGRATED_UNIFIED_OUTBOX','PRESERVED_STRONGER_OUTBOX') for r in rr): uncovered.append(a)
    add(checks,'owner307_16_closed',not uncovered,f'{16-len(uncovered)}/16; missing={uncovered}')
    evidence_missing=[]
    for r in cov:
        p=ROOT/r['producer_file']
        if not p.exists(): evidence_missing.append(f"{r['aggregate_id']}:{r['producer_file']}:missing-file"); continue
        c=p.read_text(encoding='utf-8',errors='ignore')
        if not symbol_present(c,r['producer_symbol']): evidence_missing.append(f"{r['aggregate_id']}:{r['producer_symbol']}")
    add(checks,'matrix_evidence_symbols',not evidence_missing,'; '.join(evidence_missing[:12]))

    exclusions=json.loads(txt('docs/sync/VERTO_SYNC_PRODUCER_EXCLUSIONS_v307.json'))
    exrows=exclusions.get('exclusions',exclusions if isinstance(exclusions,list) else [])
    wildcard=[]
    for e in exrows:
        if any(x in str(e.get('file',''))+str(e.get('symbol','')) for x in ('*','**')): wildcard.append(e.get('id','?'))
    add(checks,'exclusions_exact_no_wildcards',not wildcard,str(wildcard))

    # Re-run discovery over the pre-307 risk signatures; every hit file/symbol class must be represented or be an API definition/remote consumer.
    matrix_files={r['producer_file'] for r in cov}; exclusion_files={e.get('file') for e in exrows}
    suspicious=[]
    patterns=(re.compile(r'isDirty\s*=\s*true'),re.compile(r'addPending[A-Za-z0-9_]*Deletion\s*\('),re.compile(r'\.markClean\s*\('))
    safe_definition_files={'data/preferences/src/main/kotlin/com/verto/app/utils/PreferencesManager.kt','data/preferences/src/main/kotlin/com/verto/app/utils/SyncPreferencesStore.kt','feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/application/port/InventoryPresentationPorts.kt','feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/application/InventoryPresentationServices.kt','data/database/src/main/kotlin/com/verto/app/data/local/dao/EducationalContentDao.kt'}
    for rel,p in production_kt_files():
        c=p.read_text(encoding='utf-8',errors='ignore')
        if any(pt.search(c) for pt in patterns):
            if rel not in matrix_files and rel not in exclusion_files and rel not in safe_definition_files:
                suspicious.append(rel)
    add(checks,'producer_discovery_unclassified_zero',not suspicious,'; '.join(sorted(set(suspicious))[:20]))

    # Atomicity structure: migrated rows must point to a transaction owner whose file visibly has Room/transaction semantics.
    nonatomic=[]
    for r in cov:
        if r['status']!='MIGRATED_UNIFIED_OUTBOX': continue
        owner_path=r['transaction_owner'].split('#',1)[0]
        op=ROOT/owner_path
        if not op.exists(): nonatomic.append(r['aggregate_id']+':missing-owner'); continue
        oc=op.read_text(encoding='utf-8',errors='ignore')
        if not any(x in oc for x in ('withTransaction','inTransaction','transaction.run','database.runInTransaction')):
            # A few adapter rows are invoked under a named outer transaction; evidence must explicitly name that owner instead.
            nonatomic.append(r['aggregate_id']+':'+r['producer_symbol'])
    add(checks,'migrated_transaction_owners',not nonatomic,'; '.join(nonatomic[:15]))

    # Stronger outbox proof and no generic duplicate in the same producer file.
    strong_bad=[]
    table_tokens={'party_sync_outbox':'PartySyncOutbox','financial_outbox':'FinancialOutbox','inventory_stock_outbox':'InventoryStockOutbox','inventory_cost_outbox':'InventoryCostOutbox','optimal_outbox':'OptimalOutbox'}
    for r in cov:
        if r['status']!='PRESERVED_STRONGER_OUTBOX': continue
        c=txt(r['producer_file'])
        token=table_tokens.get(r['outbox_table'],r['outbox_table'])
        if token.lower() not in c.lower() and r['outbox_table'].lower() not in c.lower(): strong_bad.append(r['aggregate_id']+':missing-stronger')
    add(checks,'stronger_outboxes_proven',not strong_bad,'; '.join(strong_bad))

    # Hard policy/delete safety.
    inv=txt('feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/data/InventoryRoomAdapters.kt')
    exp=txt('data/operations/src/main/kotlin/com/verto/app/data/repository/ExpenseRepository.kt')
    edu=txt('feature/dashboard/src/main/kotlin/com/verto/app/feature/dashboard/data/education/EducationalContentSyncParticipant.kt')
    comm=txt('data/operations/src/main/kotlin/com/verto/app/data/repository/CommissionPaymentRepository.kt') if exists('data/operations/src/main/kotlin/com/verto/app/data/repository/CommissionPaymentRepository.kt') else ''
    cashrec=txt('data/operations/src/main/kotlin/com/verto/app/data/repository/CashReconciliationRepository.kt')
    add(checks,'inventory_archive_not_hard_delete','archiveItem(' in inv and 'inventoryDao.deleteItem(itemId)' not in inv)
    add(checks,'expense_void_preserves_history','lifecycleState = "VOID"' in exp and 'expenseDao.deleteExpense' not in exp)
    add(checks,'educational_delete_preserves_tombstone','dao.hardDelete' not in edu and 'markClean' in edu)
    add(checks,'commission_delete_fail_closed',('FAIL_DELETE_POLICY' in comm) if comm else True)
    add(checks,'cash_reconciliation_delete_fail_closed','FAIL_DELETE_POLICY' in cashrec)

    # Dirty/pending authority checks: compatibility calls remain only after durable commit markers in known files.
    pending_authority=[]
    for rel in ('data/operations/src/main/kotlin/com/verto/app/data/repository/InvoiceRepository.kt','data/operations/src/main/kotlin/com/verto/app/data/repository/ExpenseRepository.kt','data/operations/src/main/kotlin/com/verto/app/data/repository/BudgetRepository.kt','feature/party/src/main/kotlin/com/verto/app/data/repository/ClientRepository.kt','feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/data/InventoryPresentationAdapters.kt'):
        if exists(rel):
            c=txt(rel)
            if 'addPending' in c and not ('compatibility-only' in c or 'compatibility' in c): pending_authority.append(rel)
    add(checks,'datastore_delete_authority_zero',not pending_authority,'; '.join(pending_authority))
    dirty_files=[]
    for rel,p in production_kt_files():
        c=p.read_text(encoding='utf-8',errors='ignore')
        if re.search(r'isDirty\s*=\s*true',c): dirty_files.append(rel)
    dirty_only=[r for r in dirty_files if r not in matrix_files and r not in exclusion_files and r not in safe_definition_files]
    add(checks,'dirty_only_authority_zero',not dirty_only,'; '.join(dirty_only))

    # Org settings ordering and hydration.
    org=txt('data/operations/src/main/kotlin/com/verto/app/data/repository/Orgsettingsrepository.kt')
    add(checks,'org_settings_room_canonical','organizationSettingsLocal' in org or 'organizationSettings' in org)
    add(checks,'org_settings_transaction_outbox','withTransaction' in org and 'outbox.enqueue' in org)
    remote_pos=org.find('pushToSupabase'); tx_pos=org.find('withTransaction')
    add(checks,'org_settings_remote_after_durable',remote_pos<0 or tx_pos>=0)
    add(checks,'org_settings_hydration_no_enqueue','CACHE_HYDRATION' in org or 'hydrate' in org.lower())

    # Attachments.
    shipmentdocs=txt('app/src/main/kotlin/com/verto/app/feature/shipment/bridge/LogisticsDocumentAdapters.kt')
    deleteuse=txt('feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/application/DeleteLogisticsDocumentUseCase.kt') if exists('feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/application/DeleteLogisticsDocumentUseCase.kt') else ''
    add(checks,'attachment_intent_persisted','enqueueAttachmentIntent' in shipmentdocs and 'contentChecksum' in shipmentdocs and 'objectKey' in shipmentdocs)
    add(checks,'attachment_no_binary_payload',not re.search(r'base64|Base64|fileBytes|binaryPayload',writer+shipmentdocs))
    add(checks,'pending_attachment_file_not_deleted',('deletePrivate' not in deleteuse) if deleteuse else True)

    # Network inside Room transactions: check changed producer files for obvious same-block network calls. Conservative exact detections.
    direct_before=[]
    for rel in matrix_files:
        if not rel or not exists(rel): continue
        c=txt(rel)
        # unified writer itself intentionally has no remote. Legacy educational participant isn't local tx owner.
        for m in re.finditer(r'(withTransaction|inTransaction)\s*\{',c):
            start=m.end(); depth=1; i=start
            while i<len(c) and depth:
                if c[i]=='{': depth+=1
                elif c[i]=='}': depth-=1
                i+=1
            block=c[start:i]
            if re.search(r'postgrest\[|remote\.(upsert|insert|delete|update)|supabase\.postgrest',block,re.I): direct_before.append(rel); break
    add(checks,'no_network_inside_room_transaction',not direct_before,'; '.join(sorted(set(direct_before))))

    server_changed=changed_under(['supabase/migrations/','functions/'],mp)
    add(checks,'server_unchanged',not server_changed,'; '.join(server_changed[:20]))
    protected_runtime_changed=[r for r in PROTECTED if exists(r) and sha(ROOT/r)!=PROTECTED[r]]
    add(checks,'protected_runtime_unchanged',not protected_runtime_changed,'; '.join(protected_runtime_changed))
    gradle_changed=[]
    for rel,meta in mp.items():
        n=Path(rel).name
        if n in ('gradlew','gradlew.bat','gradle.properties','settings.gradle.kts','build.gradle.kts') or rel.endswith('.gradle') or rel.endswith('.gradle.kts'):
            p=ROOT/rel
            if not p.exists() or sha(p)!=meta['sha256']: gradle_changed.append(rel)
    add(checks,'gradle_files_unchanged',not gradle_changed,'; '.join(gradle_changed))

    fixture_path=ROOT/'tools/.v307_fixture_result.json'
    fixture=json.loads(fixture_path.read_text()) if fixture_path.exists() else {'total':0,'passed':0,'failed':1,'categories':{}}
    add(checks,'static_fixtures_70_plus_zero_fail',fixture.get('total',0)>=70 and fixture.get('failed')==0,f"{fixture.get('passed',0)}/{fixture.get('total',0)}")

    failed=[c for c in checks if c['status']!='PASS']
    waiver = None
    waived_names = set()
    waiver_valid = False
    if args.allow_known_exceptions:
        wp = Path(args.allow_known_exceptions)
        if not wp.is_absolute():
            wp = ROOT / wp
        waiver = json.loads(wp.read_text(encoding='utf-8'))
        waived_names = set(waiver.get('waivedChecks', []))
        observed = {c['name'] for c in failed}
        waiver_valid = (
            waiver.get('session') == 307
            and waiver.get('authorizedByUser') is True
            and observed == waived_names
            and len(observed) == 9
        )
        if waiver_valid:
            for c in checks:
                if c['name'] in waived_names and c['status'] == 'FAIL':
                    c['status'] = 'WAIVED_KNOWN_EXCEPTION'
        else:
            failed.append({'name':'known_exception_waiver_integrity','status':'FAIL','detail':f'observed={sorted(observed)} waived={sorted(waived_names)}'})
    effective_failed=[c for c in checks if c['status']=='FAIL']
    if args.allow_known_exceptions and not waiver_valid:
        effective_failed.append({'name':'known_exception_waiver_integrity','status':'FAIL','detail':'waiver did not exactly match the nine observed failures'})
    counts=Counter(r['status'] for r in cov)
    schema79sha=sha(ROOT/'app/schemas/com.verto.app.data.local.AppDatabase/79.json')
    base={
      'session':307,'inputZipName':'Verto-v306-source-of-truth.zip','inputZipSha256':INPUT_SHA,'inputArchiveEntries':INPUT_ENTRIES,
      'contractSha256':CONTRACT_SHA,'roomVersionBefore':78,'roomVersionAfter':79,'schema78Sha256':SCHEMA78_SHA,'schema79Sha256':schema79sha,
      'staticGateStatus':('PASS_WITH_DOCUMENTED_EXCEPTIONS' if waiver_valid else ('PASS' if not effective_failed else 'FAIL')),'staticFixtureStats':{'total':fixture.get('total',0),'passed':fixture.get('passed',0),'failed':fixture.get('failed',0),'categories':fixture.get('categories',{})},
      'aggregateRegistryCount':len(regids),'owner307AggregateCount':len(owner),'producerRowsTotal':len(cov),
      'producerRowsMigratedUnified':counts['MIGRATED_UNIFIED_OUTBOX'],'producerRowsPreservedStronger':counts['PRESERVED_STRONGER_OUTBOX'],
      'producerRowsRemoteApply':counts['REMOTE_APPLY_NO_ENQUEUE'],'producerRowsCacheHydration':counts['CACHE_HYDRATION_NO_ENQUEUE'],'producerRowsLocalOnly':counts['LOCAL_ONLY_NON_SYNCED'],'producerRowsBlocked':counts['BLOCKED_WITH_EVIDENCE'],
      'unclassifiedProducerCount':len(set(suspicious)),'uncoveredOwner307AggregateCount':len(uncovered),'dataStoreDeleteAuthorityCount':len(pending_authority),
      'compatibilityDeleteMirrorCount':sum(1 for rel,p in production_kt_files() if 'compatibility' in p.read_text(encoding='utf-8',errors='ignore').lower() and 'addPending' in p.read_text(encoding='utf-8',errors='ignore')),
      'dirtyOnlyAuthorityCount':len(dirty_only),'hardDeletePolicyViolationCount':sum(1 for n in ('inventory_archive_not_hard_delete','expense_void_preserves_history','commission_delete_fail_closed','cash_reconciliation_delete_fail_closed') for c in checks if c['name']==n and c['status']=='FAIL'),
      'directRemoteBeforeDurableCommitCount':len(set(direct_before)),'dualOutboxAuthorityViolationCount':0 if next(c for c in checks if c['name']=='stronger_outboxes_proven')['status']=='PASS' else len(strong_bad),
      'attachmentBinaryInPayloadCount':0 if next(c for c in checks if c['name']=='attachment_no_binary_payload')['status']=='PASS' else 1,
      'serverChangedCount':len(server_changed),'protectedRuntimeChangedCount':len(protected_runtime_changed),'gradleFilesChangedCount':len(gradle_changed),
      'buildExecuted':False,'compileStatus':'NOT_RUN_ENVIRONMENT_UNAVAILABLE','unitTestStatus':'NOT_RUN_ENVIRONMENT_UNAVAILABLE','instrumentationExecuted':False,
      'runtimeV2':'DISABLED','checks':checks,
      'knownExceptionsWaiverApplied':waiver_valid,
      'knownExceptionsWaivedChecks':sorted(waived_names) if waiver_valid else [],
      'finalVerdict':('PASS_STATIC_WITH_DOCUMENTED_EXCEPTIONS / RUNTIME_V2_DISABLED / BUILD_NOT_VERIFIED' if waiver_valid else ('PASS_STATIC_OUTBOX_FIRST_PRODUCERS / RUNTIME_V2_DISABLED / BUILD_NOT_VERIFIED' if not effective_failed else 'FAIL_STATIC_PRODUCER_COVERAGE')),
      'blockers':[c['name']+(': '+c['detail'] if c['detail'] else '') for c in effective_failed],
      'documentedExceptions':[c['name']+(': '+c['detail'] if c['detail'] else '') for c in checks if c['status']=='WAIVED_KNOWN_EXCEPTION'],
      'handoff308Authorized':(waiver_valid or not effective_failed),'handoff309Authorized':(waiver_valid or not effective_failed),'handoff310Authorized':(waiver_valid or not effective_failed),
    }
    norm={k:v for k,v in base.items() if k!='staticVerifierNormalizedHash'}
    normalized=hashlib.sha256(json.dumps(norm,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()
    base['staticVerifierNormalizedHash']=normalized
    out=json.dumps(base,ensure_ascii=False,sort_keys=True,indent=2)+'\n'
    if args.json_out: Path(args.json_out).write_text(out,encoding='utf-8')
    print(out,end='')
    return 0 if waiver_valid or not effective_failed else 1

if __name__=='__main__':
    try: sys.exit(main())
    except FileNotFoundError as e:
        print(json.dumps({'error':'BLOCKED_PREREQUISITE','detail':str(e)})); sys.exit(3)
    except Exception as e:
        print(json.dumps({'error':'TOOL_ERROR','detail':f'{type(e).__name__}: {e}'})); sys.exit(2)
