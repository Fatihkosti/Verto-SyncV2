#!/usr/bin/env python3
"""Execute the ACTUAL B10 migration and DAO SQL in native Python SQLite, starting at exported schema 98.
Not Room/KSP/Android, not a force-stop/device test, and not G-B10. No network or server writes.
The coordinator transaction scenarios below are explicit SQL harnesses, not calls into Kotlin/Room.
"""
from pathlib import Path
import json, re, sqlite3, tempfile, unittest

ROOT = Path(__file__).resolve().parents[1]
DB = ROOT / 'data/database/src/main/kotlin/com/verto/app/data/local'
SCHEMA = json.loads((ROOT/'app/schemas/com.verto.app.data.local.AppDatabase/98.json').read_text())['database']
MIGRATION_SOURCE = (DB/'AppDatabaseMigrations98To99.kt').read_text()
DAO_SOURCE = (DB/'dao/DurableSyncInboxDao.kt').read_text()
LITERAL = r'(?:"""([\s\S]*?)"""(?:\.trimIndent\(\))?|"((?:\\.|[^"\\])*)")'
MIGRATION = [(a or bytes(b, 'utf8').decode('unicode_escape')).strip() for a,b in re.findall(r'db\.execSQL\(\s*'+LITERAL+r'\s*\)', MIGRATION_SOURCE)]
QUERIES = {name:(a or b).strip() for a,b,name in re.findall(r'@Query\(\s*'+LITERAL+r'\s*\)\s*(?:abstract\s+)?suspend fun (\w+)', DAO_SOURCE)}
assert len(MIGRATION) == MIGRATION_SOURCE.count('db.execSQL('), 'unparsed migration statement'
assert len(QUERIES) == DAO_SOURCE.count('@Query('), 'unparsed DAO query'


def insert(db, table, **supplied):
    vals = {}
    for _, name, typ, required, default, _ in db.execute(f'PRAGMA table_info({table})'):
        if name in supplied: vals[name] = supplied[name]
        elif default is None: vals[name] = ('' if typ == 'TEXT' else 0) if required else None
    assert supplied.keys() <= vals.keys(), (table, supplied.keys() - vals.keys())
    db.execute(f'INSERT INTO {table} ({",".join(vals)}) VALUES ({",".join("?" for _ in vals)})', list(vals.values()))


def schema98(path=':memory:'):
    db = sqlite3.connect(path, isolation_level=None)
    for entity in SCHEMA['entities']:
        db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
        for index in entity.get('indices', []):
            db.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))
    return db


def migrate(db):
    db.execute('BEGIN')
    try:
        for sql in MIGRATION: db.execute(sql)
        db.execute('PRAGMA user_version=99')
        db.execute('COMMIT')
    except BaseException:
        db.execute('ROLLBACK'); raise


def cursor(db, scope='s', org='o', token='opaque-0', received=0, applied=None):
    insert(db, 'sync_cursor', scope_id=scope, organization_id=org, sync_principal_id='p',
           contract_family='verto-unified-sync', contract_version=1, scope_definition_version=1,
           cursor_token=token, received_cursor_token=token, received_high_watermark=received,
           applied_checkpoint=applied, last_applied_change_revision=applied, state='ACTIVE', updated_at=1)


def group(db, tx, revision, state='RECEIVED', scope='s', org='o', key=None, size=100):
    insert(db, 'sync_inbox_group', scope_id=scope, transaction_id=tx, organization_id=org,
           state=state, member_count=1, first_revision=revision, last_revision=revision,
           manifest_sha256='a'*64, touched_keys_json='[]', dependency_transaction_ids_json='[]',
           serialized_bytes=size, last_attempt_generation=-1)
    if key is not None: insert(db, 'sync_inbox_touched_key', scope_id=scope, transaction_id=tx, key_type='CATEGORY', key_id=key)


def event(db, revision=1, state='RECEIVED', tx='g', scope='s', org='o', payload='{"name":"صنف"}'):
    insert(db, 'sync_inbox', scope_id=scope, organization_id=org, server_revision=revision,
           aggregate_type='CATEGORY', aggregate_id='c-'+str(revision), operation_type='UPSERT',
           entity_version=1, payload_version=1, payload_json=payload, transaction_id=tx,
           transaction_order=0, transaction_size=1, changed_at=1, content_fingerprint='b'*64,
           apply_state=state, received_at=1, applied_at=2 if state=='APPLIED' else None)


class B10SqliteTests(unittest.TestCase):
    def setUp(self): self.db=schema98(); migrate(self.db); cursor(self.db)
    def tearDown(self): self.db.close()
    def query(self, name, **parameters): return self.db.execute(QUERIES[name], parameters)
    def count(self, table): return self.db.execute('SELECT COUNT(*) FROM '+table).fetchone()[0]
    def request(self, requested=1, drained=0, at=10):
        insert(self.db, 'sync_inbox_apply_request', scope_id='s', organization_id='o', requested_generation=requested,
               drained_generation=drained, next_wake_at=at, updated_at=1)
    def test_all_actual_dao_queries_prepare_against_migrated_schema(self):
        for name, sql in QUERIES.items():
            with self.subTest(query=name):
                parameters = {key: 1 for key in re.findall(r':(\w+)', sql)}
                self.db.execute('EXPLAIN '+sql, parameters).fetchall()
        self.assertEqual(25, len(QUERIES))

    def test_migration_preserves_every_immutable_event_column_and_indexes(self):
        before = schema98()
        try:
            for n,state in enumerate(['RECEIVED','APPLIED','REQUIRES_REVIEW'],1): event(before,n,state)
            rows = before.execute('SELECT * FROM sync_inbox ORDER BY server_revision').fetchall()
            old_indices = before.execute("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='sync_inbox' ORDER BY name").fetchall()
            migrate(before)
            self.assertEqual(rows, before.execute('SELECT * FROM sync_inbox ORDER BY server_revision').fetchall())
            self.assertEqual(old_indices, before.execute("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='sync_inbox' ORDER BY name").fetchall())
            self.assertEqual('INBOX_LEGACY_MANIFEST_UNPROVEN', before.execute('SELECT storage_wait_reason FROM sync_inbox_apply_request').fetchone()[0])
            self.assertEqual([], before.execute('PRAGMA foreign_key_check').fetchall())
        finally: before.close()
    def test_old_server_watermark_is_not_treated_as_received_coverage(self):
        before=schema98()
        try:
            cursor(before, received=900, applied=7)
            cursor(before, scope='unsafe', received=900, applied=7)
            event(before, 8, scope='unsafe')
            migrate(before)
            self.assertEqual((7,'opaque-0'),before.execute("SELECT received_high_watermark,received_cursor_token FROM sync_cursor WHERE scope_id='s'").fetchone())
            self.assertEqual(900,before.execute("SELECT received_high_watermark FROM sync_cursor WHERE scope_id='unsafe'").fetchone()[0])
        finally: before.close()
    def test_all_six_states_two_mib_event_guard_and_immutability(self):
        for i,state in enumerate(['RECEIVED','READY','APPLIED','WAITING_LOCAL','WAITING_DEPENDENCY','REQUIRES_REVIEW'],1): event(self.db,i,state)
        event(self.db,7,payload='x'*2097152)
        with self.assertRaises(sqlite3.IntegrityError): event(self.db,8,payload='x'*2097153)
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute("UPDATE sync_inbox SET payload_json='changed' WHERE server_revision=1")
        self.assertEqual(7,self.count('sync_inbox'))
    def test_exact_duplicate_revision_rejects_replace(self):
        event(self.db)
        with self.assertRaises(sqlite3.IntegrityError): event(self.db,payload='changed')
        self.assertEqual('{"name":"صنف"}',self.db.execute('SELECT payload_json FROM sync_inbox').fetchone()[0])
    def test_receive_cursor_cas_never_writes_applied_checkpoint(self):
        args=dict(scopeId='s',organizationId='o',principalId='p',family='verto-unified-sync',version=1,definition=1,
                  expectedCursor='opaque-0',nextCursor='opaque-1',receivedThrough=10,serverHigh=999,minimumRevision=None,now=2)
        self.assertEqual(1,self.query('advanceReceivedCursor',**args).rowcount)
        self.assertEqual(('opaque-1',10,None,999),self.db.execute('SELECT received_cursor_token,received_high_watermark,applied_checkpoint,page_high_watermark FROM sync_cursor').fetchone())
        self.assertEqual(0,self.query('advanceReceivedCursor',**args).rowcount)
        for key,value in [('organizationId','foreign'),('principalId','foreign'),('definition',2),('family','foreign')]:
            altered=args|{'expectedCursor':'opaque-1',key:value}
            self.assertEqual(0,self.query('advanceReceivedCursor',**altered).rowcount)
    def test_receive_transaction_failure_does_not_leave_rows_or_token(self):
        self.db.execute('BEGIN')
        try:
            event(self.db); group(self.db,'g',1,key='x'); self.request()
            self.db.execute("UPDATE sync_cursor SET cursor_token='opaque-1',received_cursor_token='opaque-1'")
            raise RuntimeError('simulated cut before SQL COMMIT')
        except RuntimeError: self.db.execute('ROLLBACK')
        for table in ['sync_inbox','sync_inbox_group','sync_inbox_apply_request']: self.assertEqual(0,self.count(table))
        self.assertEqual('opaque-0',self.db.execute('SELECT received_cursor_token FROM sync_cursor').fetchone()[0])
    def test_committed_receipt_survives_sqlite_close_reopen(self):
        with tempfile.TemporaryDirectory() as tmp:
            path=str(Path(tmp)/'process.db'); db=schema98(path); migrate(db); cursor(db)
            db.execute('BEGIN'); event(db); group(db,'g',1); db.execute("UPDATE sync_cursor SET received_cursor_token='opaque-1',cursor_token='opaque-1',received_high_watermark=1"); db.execute('COMMIT'); db.close()
            db=sqlite3.connect(path)
            self.assertEqual(1,db.execute('SELECT COUNT(*) FROM sync_inbox').fetchone()[0])
            self.assertEqual(('opaque-1',None),db.execute('SELECT received_cursor_token,applied_checkpoint FROM sync_cursor').fetchone())
            db.close()
    def test_touch_order_blocks_x_not_independent_y_or_proven_origin(self):
        group(self.db,'x',1,'WAITING_LOCAL',key='x'); group(self.db,'y',2,key='y'); group(self.db,'z',3,key='x')
        self.assertFalse(self.query('hasEarlierUnappliedTouch',scopeId='s',transactionId='y',firstRevision=2).fetchone()[0])
        self.assertTrue(self.query('hasEarlierUnappliedTouch',scopeId='s',transactionId='z',firstRevision=3).fetchone()[0])
        insert(self.db,'sync_inbox_dependency',scope_id='s',transaction_id='x',depends_on_transaction_id='z')
        self.assertTrue(self.query('hasEarlierUnappliedTouch',scopeId='s',transactionId='z',firstRevision=3).fetchone()[0])
        self.db.execute("UPDATE sync_inbox_group SET state='WAITING_DEPENDENCY' WHERE transaction_id='x'")
        self.assertFalse(self.query('hasEarlierUnappliedTouch',scopeId='s',transactionId='z',firstRevision=3).fetchone()[0])
    def test_dependency_is_scope_bound_and_transitive_cycles_terminate(self):
        group(self.db,'dependent',1,'WAITING_DEPENDENCY',key='x'); group(self.db,'origin',3,key='x')
        insert(self.db,'sync_inbox_dependency',scope_id='s',transaction_id='dependent',depends_on_transaction_id='middle')
        insert(self.db,'sync_inbox_dependency',scope_id='s',transaction_id='middle',depends_on_transaction_id='origin')
        group(self.db,'middle',4,'APPLIED',scope='foreign')
        self.assertEqual([('middle',)],self.query('missingInboxDependencies',scopeId='s',transactionId='dependent').fetchall())
        self.assertFalse(self.query('hasEarlierUnappliedTouch',scopeId='s',transactionId='origin',firstRevision=3).fetchone()[0])
        insert(self.db,'sync_inbox_dependency',scope_id='s',transaction_id='origin',depends_on_transaction_id='dependent')
        self.query('hasEarlierUnappliedTouch',scopeId='s',transactionId='origin',firstRevision=3).fetchall()
    def test_generation_prevents_wait_polling_and_stale_drain_cannot_lose_wake(self):
        self.request(); group(self.db,'x',1,'WAITING_LOCAL')
        self.assertEqual(1,len(self.query('inboxApplyCandidates',scopeId='s',generation=1,limit=1).fetchall()))
        self.query('markInboxGroupAttempt',scopeId='s',transactionId='x',generation=1)
        self.assertEqual([],self.query('inboxApplyCandidates',scopeId='s',generation=1,limit=1).fetchall())
        self.query('incrementInboxRequestRaw',scopeId='s',organizationId='o',wakeAt=2,now=2)
        self.assertEqual(0,self.query('drainInboxRequestIfUnchanged',scopeId='s',observedGeneration=1,now=3).rowcount)
        self.assertEqual(1,len(self.query('inboxApplyCandidates',scopeId='s',generation=2,limit=1).fetchall()))
        self.query('drainInboxRequestIfUnchanged',scopeId='s',observedGeneration=2,now=4)
        self.assertEqual((2,2,None),self.db.execute('SELECT requested_generation,drained_generation,next_wake_at FROM sync_inbox_apply_request').fetchone())
    def test_terminal_wake_update_is_transactional_and_tenant_scoped(self):
        self.request(); insert(self.db,'sync_inbox_apply_request',scope_id='other',organization_id='other',requested_generation=1,drained_generation=0,next_wake_at=9)
        self.db.execute('BEGIN'); self.query('requestInboxApplyForOrganization',organizationId='o',now=2); self.db.execute('ROLLBACK')
        self.assertEqual(1,self.db.execute("SELECT requested_generation FROM sync_inbox_apply_request WHERE scope_id='s'").fetchone()[0])
        self.query('requestInboxApplyForOrganization',organizationId='o',now=2)
        self.assertEqual([(1,),(2,)],self.db.execute('SELECT requested_generation FROM sync_inbox_apply_request ORDER BY scope_id').fetchall())
    def test_quota_counts_all_unapplied_states_not_applied_or_other_scope(self):
        states=['RECEIVED','READY','WAITING_LOCAL','WAITING_DEPENDENCY','REQUIRES_REVIEW']
        for i,state in enumerate(states): group(self.db,str(i),i+1,state,size=2097152)
        group(self.db,'applied',10,'APPLIED',size=2097152); group(self.db,'foreign',11,scope='foreign',size=2097152)
        self.assertEqual(5*2097152,self.query('unappliedInboxBytes',scopeId='s').fetchone()[0])
        self.assertEqual(5,self.query('unappliedInboxGroupCount',scopeId='s').fetchone()[0])
        self.assertEqual(1,self.query('reviewInboxGroupCount',scopeId='s').fetchone()[0])
        event(self.db,20,tx='orphan'); self.assertEqual(1,self.query('unprovenInboxRowCount',scopeId='s').fetchone()[0])
    def test_group_apply_status_and_cursor_are_atomic_sql_harness(self):
        event(self.db); group(self.db,'g',1); self.db.execute('UPDATE sync_cursor SET received_high_watermark=1')
        self.db.execute('CREATE TABLE fixture_domain(id TEXT PRIMARY KEY)')
        self.db.execute('BEGIN')
        self.db.execute("INSERT INTO fixture_domain VALUES ('x')")
        self.query('setInboxMembersStateRaw',scopeId='s',transactionId='g',state='APPLIED',reason=None,now=2)
        self.query('setInboxGroupStateRaw',scopeId='s',transactionId='g',state='APPLIED',reason=None,now=2)
        self.query('advanceAppliedCheckpoint',scopeId='s',organizationId='o',checkpoint=1,now=2)
        self.db.execute('ROLLBACK')
        self.assertEqual(0,self.count('fixture_domain')); self.assertEqual('RECEIVED',self.db.execute('SELECT apply_state FROM sync_inbox').fetchone()[0])
        self.assertIsNone(self.db.execute('SELECT applied_checkpoint FROM sync_cursor').fetchone()[0])
        self.assertEqual(0,self.query('advanceAppliedCheckpoint',scopeId='s',organizationId='o',checkpoint=2,now=2).rowcount)
    def test_real_sqlite_full_error_rolls_back_receipt_transaction(self):
        db=sqlite3.connect(':memory:',isolation_level=None)
        try:
            db.execute('PRAGMA page_size=1024'); db.execute('CREATE TABLE fixture_receipt(id INTEGER PRIMARY KEY,payload BLOB)')
            db.execute('CREATE TABLE fixture_cursor(token TEXT)'); db.execute("INSERT INTO fixture_cursor VALUES ('old')")
            pages=db.execute('PRAGMA page_count').fetchone()[0]; db.execute(f'PRAGMA max_page_count={pages+2}')
            with self.assertRaises(sqlite3.OperationalError) as caught:
                db.execute('BEGIN'); db.execute("UPDATE fixture_cursor SET token='new'")
                db.execute('INSERT INTO fixture_receipt(payload) VALUES (?)',(b'x'*1024*1024,)); db.execute('COMMIT')
            if db.in_transaction: db.execute('ROLLBACK')
            self.assertEqual(sqlite3.SQLITE_FULL,caught.exception.sqlite_errorcode)
            self.assertEqual('old',db.execute('SELECT token FROM fixture_cursor').fetchone()[0]); self.assertEqual(0,db.execute('SELECT COUNT(*) FROM fixture_receipt').fetchone()[0])
        finally: db.close()


if __name__=='__main__':
    print(f'Native SQLite {sqlite3.sqlite_version}; {len(MIGRATION)} actual migration statements; {len(QUERIES)} actual DAO queries parsed. NOT Room/Android/G-B10.',flush=True)
    unittest.main(verbosity=2)
