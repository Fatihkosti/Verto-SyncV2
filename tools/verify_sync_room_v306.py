#!/usr/bin/env python3
from __future__ import annotations
import argparse, hashlib, json, re, sqlite3, sys, zipfile
from pathlib import Path

EXPECTED_INPUT_SHA = '6144c6273dd8c5e4d6ecd211ea753f9aff29231c1fabce12009852a4586769a5'
EXPECTED_INPUT_ENTRIES = 2553
EXPECTED_SCHEMA77_SHA = '63ec65fd1eedb9c29a585cb43d9a8afed42a429fb0ad749da85b305e5ccf8b1a'
EXPECTED = {
 'data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncContract.kt': '9553e1801dcf756f619ea2c28fd1bf8534f18fa32e4f74c2743cedc5460a7e6c',
 'data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncAggregateRegistry.kt': '9e35f020993e3caa21171ab6a311605f2bcfa58a910bf7814f22e157b5af7fe9',
}
PROTECTED_HASHES = {
 'data/sync/src/main/kotlin/com/verto/app/data/sync/SyncManager.kt': '48da86c2e5e558eddaf6b07f89f6474013ddc66a85a132e8a01bf8e64afae233',
 'data/sync/src/main/kotlin/com/verto/app/data/sync/SyncWorker.kt': '16eab4a5533bf791e860281c08c142c2fa8d0f618471a1b54b7189f2f6c03a43',
 'data/preferences/src/main/kotlin/com/verto/app/utils/SyncPreferencesStore.kt': '3cd5bb3b019e094ee4209860bd3796620477861b55cd5aac8c54140e32234f48',
}
PROD_ALLOW = {
 'data/database/src/main/kotlin/com/verto/app/data/local/AppDatabase.kt',
 'data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt',
 'data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations77To78.kt',
 'data/database/src/main/kotlin/com/verto/app/data/local/entity/UnifiedSyncEntities.kt',
 'data/database/src/main/kotlin/com/verto/app/data/local/dao/UnifiedSyncDao.kt',
}
ALLOW = PROD_ALLOW | {
 'app/schemas/com.verto.app.data.local.AppDatabase/78.json',
 'data/database/src/test/kotlin/com/verto/app/data/local/MigrationCatalogV278Test.kt',
 'data/database/src/test/kotlin/com/verto/app/data/local/MigrationCatalogV306Test.kt',
 'data/database/src/androidTest/kotlin/com/verto/app/data/local/UnifiedSyncRoomMigrationV306Test.kt',
 'data/database/src/androidTest/kotlin/com/verto/app/data/local/UnifiedSyncRoomAtomicityV306Test.kt',
 'data/database/src/androidTest/kotlin/com/verto/app/data/local/UnifiedSyncRoomProcessDeathV306Test.kt',
 'tools/verify_sync_room_v306.py','tools/test_sync_room_verification_v306.py','scripts/verify-v306-sync-room.sh',
 'VERTO_SYNC_ROOM_VERIFICATION_v306.json','VERTO_SYNC_ROOM_VERIFICATION_v306.md','Verto-v306-report.md',
}
NEW_TABLES = {'sync_outbox','sync_inbox','sync_cursor','sync_sequence_state'}
OPERATIONS = {'UPSERT','DELETE','COMMAND','ARCHIVE','VOID','REVERSE','CANCEL'}

def sha_bytes(b: bytes) -> str: return hashlib.sha256(b).hexdigest()
def sha_file(p: Path) -> str: return sha_bytes(p.read_bytes())
def txt(p: Path) -> str: return p.read_text(encoding='utf-8')
def zip_file_map(z: zipfile.ZipFile) -> dict[str,bytes]:
    return {i.filename: z.read(i) for i in z.infolist() if not i.is_dir()}

def extract_exec_sql(source: str) -> list[str]:
    sql=[]
    for m in re.finditer(r'db\.execSQL\(\s*"""(.*?)"""\.trimIndent\(\)\s*\)',source,re.S): sql.append(m.group(1).strip())
    for m in re.finditer(r'db\.execSQL\("((?:[^"\\]|\\.)*)"\)',source):
        sql.append(bytes(m.group(1),'utf-8').decode('unicode_escape'))
    return sql

def sqlite_validate(schema77: dict, migration_source: str) -> tuple[bool,str]:
    try:
        db=sqlite3.connect(':memory:')
        db.execute('PRAGMA foreign_keys=OFF')
        for e in schema77['database']['entities']:
            db.execute(e['createSql'].replace('${TABLE_NAME}',e['tableName']))
            for ix in e.get('indices',[]): db.execute(ix['createSql'].replace('${TABLE_NAME}',e['tableName']))
        for sql in extract_exec_sql(migration_source): db.execute(sql)
        names={r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        if not NEW_TABLES <= names: return False, f'missing tables {sorted(NEW_TABLES-names)}'
        # adversarial guards on migration path
        db.execute("INSERT INTO sync_outbox(mutation_id,organization_id,aggregate_type,aggregate_id,operation_type,local_sequence,aggregate_sequence,payload_version,payload_json,semantic_fingerprint,state,attempt_count,next_attempt_at,created_at) VALUES('m','o','X','a','UPSERT',1,1,1,'{}','fp','PENDING',0,0,1)")
        try:
            db.execute("UPDATE sync_outbox SET payload_json='x' WHERE mutation_id='m'")
            return False,'outbox semantic update unexpectedly allowed'
        except sqlite3.DatabaseError: pass
        db.execute("INSERT INTO sync_inbox(scope_id,organization_id,server_revision,aggregate_type,aggregate_id,operation_type,payload_version,payload_json,transaction_id,transaction_order,transaction_size,changed_at,content_fingerprint,apply_state,received_at) VALUES('s','o',1,'X','a','DELETE',1,'{}','tx',0,1,1,'fp','RECEIVED',1)")
        try:
            db.execute("UPDATE sync_inbox SET server_revision=2 WHERE scope_id='s' AND server_revision=1")
            return False,'inbox semantic update unexpectedly allowed'
        except sqlite3.DatabaseError: pass
        db.close(); return True,'ok'
    except Exception as e: return False,f'{type(e).__name__}: {e}'

def main() -> int:
    ap=argparse.ArgumentParser()
    ap.add_argument('--root',required=True)
    ap.add_argument('--input-zip',required=True)
    args=ap.parse_args()
    root=Path(args.root).resolve(); zp=Path(args.input_zip).resolve()
    if not root.is_dir() or not zp.is_file(): return 3
    try:
        zsha=sha_file(zp)
        with zipfile.ZipFile(zp) as z:
            entry_count=len(z.infolist()); base=zip_file_map(z)
    except Exception: return 2
    current={p.relative_to(root).as_posix():p.read_bytes() for p in root.rglob('*') if p.is_file()}
    changed=sorted(k for k in set(base)|set(current) if base.get(k)!=current.get(k))
    deleted=sorted(k for k in base if k not in current)
    unexpected=sorted(set(changed)-ALLOW)
    cases=[]
    def case(cid,cat,desc,ok): cases.append({'id':cid,'category':cat,'description':desc,'pass':bool(ok)})
    def has(s,*parts): return all(x in s for x in parts)

    # authorities
    case('S01','source/authority','input zip SHA exact',zsha==EXPECTED_INPUT_SHA)
    case('S02','source/authority','archive entries exact',entry_count==EXPECTED_INPUT_ENTRIES)
    case('S03','source/authority','no unexpected changed paths',not unexpected and not deleted)
    case('S04','source/authority','production changes confined to five-file allowlist',all((k not in current or not '/src/main/' in k) or k in PROD_ALLOW for k in changed))
    p77=root/'app/schemas/com.verto.app.data.local.AppDatabase/77.json'
    case('S05','source/authority','schema 77 hash unchanged',p77.exists() and sha_file(p77)==EXPECTED_SCHEMA77_SHA)
    case('S06','source/authority','protected runtime hashes unchanged',all((root/p).exists() and sha_file(root/p)==h for p,h in PROTECTED_HASHES.items()))
    case('S07','source/authority','v304 authority hashes unchanged',all((root/p).exists() and sha_file(root/p)==h for p,h in EXPECTED.items()))
    v305=json.loads(txt(root/'VERTO_SYNC_SERVER_VERIFICATION_v305.json'))
    case('S08','source/authority','raw v305 static state preserved',v305.get('fixtureStats',{}).get('passed')==92 and v305.get('finalVerdict')=='SERVER_STATIC_COMPLETE / DATABASE_EXECUTION_BLOCKED')
    v305_migration = root/'supabase/migrations/20260821062000_v305_verto_unified_sync_server.sql'
    v305_manifest = json.loads(txt(root/'docs/sync/VERTO_SYNC_SERVER_OBJECT_MANIFEST_v305.json'))
    case('S09','source/authority','v305 migration SHA and object authority exact',v305_migration.exists() and sha_file(v305_migration)=='a2daf28b3a05b35907267bfc766fc6919ba5c28613854c0c0d68ee186313e908' and v305_manifest.get('migrationSha256')=='a2daf28b3a05b35907267bfc766fc6919ba5c28613854c0c0d68ee186313e908' and len(v305_manifest.get('objects',[]))==21)
    v304_contract = root/'docs/sync/VERTO_UNIFIED_SYNC_CONTRACT_v304.json'
    case('S10','source/authority','v304 contract artifact exact',v304_contract.exists() and sha_file(v304_contract)=='58c22cb9fffd5114d925d063ba1d0d1491b06c9cf4f698dd7f3922d932486ca2')

    mig=txt(root/'data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations77To78.kt')
    cat=txt(root/'data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt')
    app=txt(root/'data/database/src/main/kotlin/com/verto/app/data/local/AppDatabase.kt')
    ent=txt(root/'data/database/src/main/kotlin/com/verto/app/data/local/entity/UnifiedSyncEntities.kt')
    dao=txt(root/'data/database/src/main/kotlin/com/verto/app/data/local/dao/UnifiedSyncDao.kt')
    d77=json.loads(txt(p77)); d78=json.loads(txt(root/'app/schemas/com.verto.app.data.local.AppDatabase/78.json'))
    old={e['tableName']:e for e in d77['database']['entities']}; new={e['tableName']:e for e in d78['database']['entities']}
    create_tables=set(re.findall(r'CREATE TABLE IF NOT EXISTS `([^`]+)`',mig))
    sqlite_ok,sqlite_msg=sqlite_validate(d77,mig)

    # migration/schema M01-M09
    case('M01','migration/schema','exactly one 77 to 78 declaration',len(re.findall(r'Migration\(77,\s*78\)',mig))==1)
    case('M02','migration/schema','ROOM_SCHEMA_VERSION 78',has(cat,'ROOM_SCHEMA_VERSION: Int = 78'))
    case('M03','migration/schema','77 schema immutable',sha_file(p77)==EXPECTED_SCHEMA77_SHA)
    case('M04','migration/schema','all 77 entities structurally preserved',all(k in new and new[k]==v for k,v in old.items()))
    case('M05','migration/schema','exactly four unified tables added',set(new)-set(old)==NEW_TABLES and create_tables==NEW_TABLES)
    case('M06','migration/schema','no legacy backfill SQL',not re.search(r'INSERT\s+(?:OR\s+\w+\s+)?INTO\s+`?(financial_|inventory_|party_|optimal_)',mig,re.I))
    req_indexes=['index_sync_outbox_org_local_sequence','index_sync_outbox_aggregate_sequence','index_sync_outbox_delivery','index_sync_outbox_aggregate_delivery','index_sync_outbox_dependency','index_sync_inbox_org_scope_revision','index_sync_inbox_apply','index_sync_inbox_transaction','index_sync_inbox_aggregate_revision']
    case('M07','migration/schema','required indexes triggers constraints present',all(x in mig for x in req_indexes) and has(mig,'sync_outbox_semantic_immutable','sync_inbox_semantic_immutable','CHECK'))
    starts=[int(x) for x in re.findall(r'MIGRATION_(\d+)_\d+',cat.split('arrayOf(',1)[1].split(')',1)[0])]
    case('M08','migration/schema','migration catalog continuous to 78',starts==list(range(1,78)) and 'MIGRATION_77_78' in cat)
    case('M09','migration/schema','no destructive migration or unrelated DDL',sqlite_ok and 'DROP TABLE' not in mig.upper() and 'ALTER TABLE' not in mig.upper() and 'fallbackToDestructiveMigration' not in (app+cat))

    # outbox O01-O12
    case('O01','outbox identity/sequence','mutation primary identity',has(ent,'@PrimaryKey @ColumnInfo(name = "mutation_id")','val mutationId'))
    case('O02','outbox identity/sequence','semantic SHA-256 fingerprint represented',has(ent,'semantic_fingerprint') and has(dao,'MessageDigest.getInstance("SHA-256")','semanticFingerprint'))
    case('O03','outbox identity/sequence','divergent duplicate fails closed',has(dao,'FAIL_IDEMPOTENCY_CONFLICT','existing.matches(draft)'))
    case('O04','outbox identity/sequence','semantic UPDATE guard present',has(mig,'sync_outbox_semantic_immutable','BEFORE UPDATE OF','FAIL_LOCAL_SYNC_IMMUTABILITY'))
    case('O05','outbox identity/sequence','positive payload version guarded',has(mig,'CHECK (`payload_version` > 0)') and has(dao,'draft.payloadVersion > 0'))
    case('O06','outbox identity/sequence','512 KiB UTF-8 payload ceiling',has(mig,'524288') and has(dao,'toByteArray(Charsets.UTF_8).size','MAX_MUTATION_PAYLOAD_BYTES'))
    case('O07','outbox identity/sequence','global durable sequence authority',has(mig,"'GLOBAL'",'sync_sequence_state') and has(dao,'COUNTER_GLOBAL','allocateSequence'))
    case('O08','outbox identity/sequence','aggregate durable sequence authority',has(mig,"'AGGREGATE'") and has(dao,'COUNTER_AGGREGATE'))
    case('O09','outbox identity/sequence','sequence avoids wall-clock MAX(outbox) memory counter',not any(x in dao for x in ['MAX(sync_outbox','AtomicLong','System.currentTimeMillis']))
    case('O10','outbox identity/sequence','sequence and outbox share Transaction method',has(dao,'@Transaction\n    open suspend fun enqueueMutation') if False else ('@Transaction\n    open suspend fun enqueueMutation' in dao and 'insertOutboxRaw(row)' in dao))
    case('O11','outbox identity/sequence','terminal states excluded from lease query',has(dao,"state IN ('PENDING','RETRY')") and 'expectedState' in dao)
    case('O12','outbox identity/sequence','terminal ACK guarded by lease token',has(dao,"state = 'LEASED'",'lease_token = :leaseToken','markTerminalRaw'))

    # inbox I01-I08
    case('I01','inbox dedup/immutability','scope revision composite identity',has(ent,'primaryKeys = ["scope_id", "server_revision"]'))
    case('I02','inbox dedup/immutability','divergent fingerprint fails closed',has(dao,'FAIL_DUPLICATE_INBOX_CONTENT','contentFingerprint'))
    case('I03','inbox dedup/immutability','same revision different scopes distinct',new['sync_inbox']['primaryKey']['columnNames']==['scope_id','server_revision'])
    case('I04','inbox dedup/immutability','transaction group metadata present',has(ent,'transaction_id','transaction_order','transaction_size'))
    case('I05','inbox dedup/immutability','immutable inbox trigger present',has(mig,'sync_inbox_semantic_immutable','server_revision','payload_json'))
    case('I06','inbox dedup/immutability','apply metadata independently mutable',has(dao,'markInboxApplied','markInboxRequiresReview','apply_error_code','applied_at'))
    ordering_queries=' '.join(re.findall(r'SELECT \* FROM sync_inbox.*?LIMIT :limit',dao,re.S))
    case('I07','inbox dedup/immutability','changed_at absent from ordering authority','changed_at' not in ordering_queries and 'ORDER BY server_revision' in dao)
    case('I08','inbox dedup/immutability','DELETE operation persistable',"'DELETE'" in mig and 'operationType' in ent)

    # cursor C01-C10
    case('C01','cursor/scope/CAS','scope_id primary authority',has(ent,'@PrimaryKey @ColumnInfo(name = "scope_id")'))
    case('C02','cursor/scope/CAS','opaque token persisted as text',has(ent,'@ColumnInfo(name = "cursor_token") val cursorToken: String'))
    case('C03','cursor/scope/CAS','no numeric cursor parsing arithmetic',not any(x in dao for x in ['cursorToken.toLong','expectedCursor.toLong','parseLong','lastRevision + 1']))
    case('C04','cursor/scope/CAS','CAS includes expected token and ACTIVE state',has(dao,'cursor_token = :expectedCursor',"state = 'ACTIVE'",'compareAndSetAdvanceRaw'))
    case('C05','cursor/scope/CAS','stale cursor explicitly fails',has(dao,'== 1','FAIL_STALE_CURSOR_WRITE'))
    case('C06','cursor/scope/CAS','INVALIDATED prevents normal advance',"state = 'ACTIVE'" in dao and 'INVALIDATED' in mig)
    case('C07','cursor/scope/CAS','BOOTSTRAP_REQUIRED prevents normal advance',"state = 'ACTIVE'" in dao and 'BOOTSTRAP_REQUIRED' in mig)
    case('C08','cursor/scope/CAS','principal org scope metadata in CAS',all(x in dao for x in ['organization_id = :organizationId','sync_principal_id = :syncPrincipalId','scope_id = :scopeId']))
    case('C09','cursor/scope/CAS','contract family version retained and checked',all(x in dao for x in ['contract_family = :contractFamily','contract_version = :contractVersion','scope_definition_version = :scopeDefinitionVersion']))
    case('C10','cursor/scope/CAS','no legacy cursor conversion',not any(x in (dao+mig) for x in ['lastPulledAt','last_server_sequence','financial_inbox.max','inventory_sync_cursors']))

    # atomicity A01-A08
    case('A01','atomicity structure','domain sequence outbox single transaction surface documented',has(dao,'AppDatabase.withTransaction { domain write; enqueueMutation'))
    case('A02','atomicity structure','no producer runtime wiring in session 306',not any('UnifiedSyncDao' in (current.get(p,b'').decode('utf-8','ignore')) for p in current if ('SyncManager.kt' in p or 'SyncWorker.kt' in p)))
    case('A03','atomicity structure','sequence allocation inside enqueue transaction','@Transaction\n    open suspend fun enqueueMutation' in dao and dao.index('allocateSequence') < dao.index('insertOutboxRaw(row)'))
    case('A04','atomicity structure','inbox domain cursor transaction surface representable',has(dao,'insertInboxChecked(...); domain apply; advanceCursorOrThrow'))
    case('A05','atomicity structure','cursor CAS callable inside outer transaction',has(dao,'Must be called inside the same AppDatabase.withTransaction','advanceCursorOrThrow'))
    case('A06','atomicity structure','no network DataStore dependencies in DAO',not re.search(r'^import .*?(supabase|datastore|ktor|realtime)', dao, re.I|re.M))
    case('A07','atomicity structure','no half-commit canonical helper',has(dao,'Canonical future producer boundary','Canonical future pull boundary'))
    pdeath=txt(root/'data/database/src/androidTest/kotlin/com/verto/app/data/local/UnifiedSyncRoomProcessDeathV306Test.kt')
    case('A08','atomicity structure','runtime process death explicitly non-gating',has(pdeath,'@Ignore','NOT_RUN_ENVIRONMENT_UNAVAILABLE'))

    # isolation/runtime
    case('R01','scope/runtime isolation','SyncManager unchanged and V2 unwired',all('UnifiedSyncDao' not in current.get(p,b'').decode('utf-8','ignore') for p in current if p.endswith('SyncManager.kt')))
    case('R02','scope/runtime isolation','SyncWorker unchanged and V2 unwired',all('UnifiedSyncDao' not in current.get(p,b'').decode('utf-8','ignore') for p in current if p.endswith('SyncWorker.kt')))
    case('R03','scope/runtime isolation','server migration tree byte-identical',all(base.get(k)==current.get(k) for k in base if k.startswith('supabase/migrations/')))
    case('R04','scope/runtime isolation','Gradle files byte-identical',all(base.get(k)==current.get(k) for k in base if k.endswith(('.gradle','.gradle.kts')) or k in {'gradle.properties','settings.gradle.kts'}))
    case('R05','scope/runtime isolation','no legacy backfill or preferences protocol migration',not any(x in mig for x in ['financial_outbox','inventory_stock_outbox','party_sync_outbox','optimal_outbox','Preferences','DataStore']))

    passed=sum(c['pass'] for c in cases); failed=len(cases)-passed
    bycat={}
    for c in cases:
        x=bycat.setdefault(c['category'],{'total':0,'passed':0,'failed':0}); x['total']+=1; x['passed']+=int(c['pass']); x['failed']+=int(not c['pass'])
    normalized={'cases':cases,'changedPaths':changed,'unexpectedPaths':unexpected,'sqliteValidation':sqlite_msg}
    nh=sha_bytes(json.dumps(normalized,sort_keys=True,separators=(',',':')).encode())
    out={
      'session':306,'verifier':'verify_sync_room_v306/1','inputZipSha256':zsha,'inputArchiveEntries':entry_count,
      'roomBefore':77,'roomAfter':78,'changedPaths':changed,'unexpectedPaths':unexpected,
      'productionChangedCount':sum(1 for p in changed if p in PROD_ALLOW),
      'serverChangedCount':sum(1 for p in changed if p.startswith('supabase/migrations/')),
      'gradleFilesChangedCount':sum(1 for p in changed if p.endswith(('.gradle','.gradle.kts')) or p in {'gradle.properties','settings.gradle.kts'}),
      'newRoomTables':sorted(NEW_TABLES),'legacyBackfillCount':0 if cases[-1]['pass'] else 1,
      'fixtureStats':{'total':len(cases),'passed':passed,'failed':failed,'byCategory':bycat},
      'staticVerifierNormalizedHash':nh,'sqliteStaticMigrationValidation':sqlite_msg,
      'status':'PASS' if failed==0 else 'FAIL','failedCaseIds':[c['id'] for c in cases if not c['pass']],
    }
    print(json.dumps(out,indent=2,sort_keys=True))
    return 0 if failed==0 else 1

if __name__=='__main__':
    try: raise SystemExit(main())
    except SystemExit: raise
    except Exception as e:
        print(json.dumps({'status':'TOOL_ERROR','error':f'{type(e).__name__}: {e}'},sort_keys=True))
        raise SystemExit(2)
