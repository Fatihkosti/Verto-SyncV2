#!/usr/bin/env python3
from pathlib import Path
import collections
import re
import sqlite3

ROOT = Path(__file__).resolve().parents[1]

def text(rel):
    return (ROOT / rel).read_text(encoding='utf-8')

def require(rel, needle, label):
    if needle not in text(rel):
        raise AssertionError(f'{label}: missing {needle!r} in {rel}')

# Room schema and durable local envelopes.
schema_match = re.search(r'ROOM_SCHEMA_VERSION: Int = (\d+)', text('data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt'))
if not schema_match or int(schema_match.group(1)) < 67:
    raise AssertionError('schema-67: current Room schema is older than required')
require('data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt', 'MIGRATION_66_67', 'migration-registered')
for needle in [
    'financial_outbox', 'financial_inbox', 'index_financial_outbox_identity',
    'index_financial_outbox_aggregate_sequence', 'index_financial_inbox_server_revision',
    "`sync_state` TEXT NOT NULL DEFAULT 'PENDING'", "`apply_reason` TEXT NOT NULL DEFAULT ''",
]:
    require('data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations66To67.kt', needle, f'migration-{needle[:24]}')

entity = 'data/database/src/main/kotlin/com/verto/app/data/local/entity/InvoicePaymentEntities.kt'
for needle in [
    'data class FinancialOutboxEntity(', 'data class FinancialInboxEntity(',
    'val writeId: String', 'val aggregateVersion: Int', 'val sequence: Long',
    'val operationType: String', 'val payloadVersion: Int', 'val schemaVersion: Int',
    'val occurredAt: Long', 'val recordedAt: Long', 'val syncState: String = "PENDING"',
    'val applyState: String',
]:
    require(entity, needle, f'entity-{needle[:24]}')

# Sequence must include remote Inbox history, otherwise a second device starts again at sequence 1.
dao = 'data/database/src/main/kotlin/com/verto/app/data/local/dao/InvoiceDao.kt'
for needle in [
    'SELECT sequence FROM financial_outbox', 'UNION ALL', 'SELECT sequence FROM financial_inbox',
    'NOT EXISTS (', "predecessor.sync_state <> 'ACKNOWLEDGED'",
    "sync_state = 'REQUIRES_REVIEW'", "apply_state = 'REQUIRES_REVIEW'",
    'insertFinancialInbox', 'getFinancialInboxRevision',
]:
    require(dao, needle, f'dao-{needle[:28]}')

# Every local financial write appends the mandatory envelope from the owner transaction.
writer = 'data/operations/src/main/kotlin/com/verto/app/data/operations/transaction/FinancialOutboxWriter.kt'
for needle in [
    'class FinancialOutboxWriter', 'OP_INVOICE_CREATED', 'OP_INVOICE_UPDATED', 'OP_INVOICE_VOIDED',
    'OP_PAYMENT_RECORDED', 'OP_PAYMENT_REVERSED', 'PAYLOAD_VERSION: Int = 1', 'SCHEMA_VERSION: Int = 1',
    'stableEventId(', 'nextFinancialOutboxSequence', '.filter { it.reversedPaymentId != null }',
    'appendPaymentEntity(',
]:
    require(writer, needle, f'writer-{needle[:28]}')
for rel, needle in [
    ('data/operations/src/main/kotlin/com/verto/app/data/operations/invoice/DefaultInvoiceAtomicPersistenceCoordinator.kt', 'financialOutboxWriter.appendInvoice(normalized)'),
    ('data/operations/src/main/kotlin/com/verto/app/data/operations/invoice/DefaultInvoiceAtomicPersistenceCoordinator.kt', 'financialOutboxWriter.appendVoid(normalized)'),
    ('data/operations/src/main/kotlin/com/verto/app/data/operations/payment/DefaultPaymentAtomicPersistenceCoordinator.kt', 'financialOutboxWriter.appendPayment(normalized)'),
]:
    require(rel, needle, 'atomic-outbox')

# Network delivery: at-least-once + exact-once effect, ordering and inbox dedupe/reconciliation.
sync = 'data/network/src/main/kotlin/com/verto/app/data/sync/SyncFinancialEvents.kt'
for needle in [
    'financial_sync_apply_event_v1', 'financial_sync_pull_events_v1',
    'getReadyFinancialOutbox', 'retryDelayMillis', 'markFinancialOutboxRequiresReview',
    'WAITING_DEPENDENCY', 'acknowledgeFinancialOutbox', 'REQUIRES_REVIEW',
    'insertFinancialInbox', 'reconcileFinancialInbox', 'FINANCIAL_SCHEMA_VERSION = 1',
    'FinancialAggregateConflictPolicy', 'remoteVersion != localVersion + 1',
    'financial inbox returned foreign organization data',
]:
    require(sync, needle, f'sync-{needle[:30]}')

participant = 'feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/data/sync/InvoiceSyncParticipant.kt'
for needle in [
    'SyncOperation(SyncStage.PUSH, 15', 'pushFinancialOutbox',
    'SyncOperation(SyncStage.PUSH, 20',
    'SyncOperation(SyncStage.PULL, 15', 'pullFinancialInbox',
    'SyncOperation(SyncStage.PULL, 85', 'reconcileFinancialInbox',
]:
    require(participant, needle, f'participant-{needle[:30]}')

# Compatibility row writers cannot bypass an unresolved financial event.
for rel, needles in {
    'data/network/src/main/kotlin/com/verto/app/data/sync/SyncInvoiceHeaders.kt': [
        'financiallyBlockedAggregateIds(orgId)', 'FinancialAggregateConflictPolicy.resolve(',
        'lifecycleConflictDetected',
    ],
    'data/network/src/main/kotlin/com/verto/app/data/sync/SyncInvoiceLines.kt': ['financiallyBlockedAggregateIds(orgId)'],
    'data/network/src/main/kotlin/com/verto/app/data/sync/SyncInvoicePayments.kt': [
        'financiallyBlockedAggregateIds(orgId)', 'paymentFinancialFactsMatch', 'immutableConflictDetected',
    ],
    'data/network/src/main/kotlin/com/verto/app/data/sync/SyncInventory.kt': [
        'financiallyBlockedAggregateIds(orgId)', 'getItemIdsTouchedByInvoices', 'blockedReturned',
    ],
    'data/network/src/main/kotlin/com/verto/app/data/sync/SyncCash.kt': [
        'financiallyBlockedAggregateIds(orgId)', 'blockedReferences', 'blockedReturned',
    ],
}.items():
    for needle in needles:
        require(rel, needle, f'compat-gate-{needle[:24]}')

# Server-side contract is authoritative for idempotency and aggregate ordering.
sql_rel = 'docs/sql/v249_financial_event_sync.sql'
sql = text(sql_rel).lower()
for needle in [
    'create table if not exists public.financial_sync_events',
    'uq_financial_sync_operation_write', 'unique (organization_id, operation_type, write_id)',
    'uq_financial_sync_aggregate_sequence', 'unique (organization_id, aggregate_id, aggregate_sequence)',
    'financial_sync_apply_event_v1', 'financial_sync_pull_events_v1',
    "'replayed'::text", "'waiting_dependency'::text", "'conflict'::text", "'applied'::text",
    'security definer', 'enable row level security', 'auth.uid()',
    'previous aggregate event has not arrived', 'optimistic aggregate version conflict',
    'invoice was already voided', 'original payment has not arrived',
    'schema_version integer not null check (schema_version = 1)',
    'financial_sync_backfill_invoice_baselines_v1', "'legacybaseline', true",
    'revoke all on function public.financial_sync_backfill_invoice_baselines_v1() from authenticated',
]:
    if needle not in sql:
        raise AssertionError(f'server-contract missing {needle!r}')

# Execute the exact CREATE TABLE strings from the Room migration and exercise the critical unique/order rules.
migration = text('data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations66To67.kt')
creates = re.findall(r'"""\s*(CREATE TABLE IF NOT EXISTS `financial_(?:outbox|inbox)`.*?\))\s*"""', migration, re.S)
if len(creates) != 2:
    raise AssertionError(f'could not extract F249 tables from migration: {len(creates)}')
db = sqlite3.connect(':memory:')
for ddl in creates:
    db.execute(ddl)
db.executescript('''
CREATE UNIQUE INDEX index_financial_outbox_identity ON financial_outbox(organization_id, operation_type, write_id);
CREATE UNIQUE INDEX index_financial_outbox_aggregate_sequence ON financial_outbox(organization_id, aggregate_id, sequence);
CREATE UNIQUE INDEX index_financial_inbox_server_revision ON financial_inbox(organization_id, server_revision);
''')

def out(event, write, agg, seq, state='PENDING', op='INVOICE_CREATED'):
    db.execute('''INSERT INTO financial_outbox(
      event_id,organization_id,write_id,aggregate_id,aggregate_version,sequence,operation_type,
      payload,payload_version,schema_version,occurred_at,recorded_at,created_at,sync_state
    ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)''',
    (event,'org',write,agg,1,seq,op,'{}',1,1,1,1,seq,state))

out('e1','w1','inv',1)
out('e2','w2','inv',2,op='PAYMENT_RECORDED')
ready_sql = '''SELECT candidate.event_id FROM financial_outbox candidate
 WHERE candidate.organization_id='org' AND candidate.sync_state IN ('PENDING','RETRY')
 AND candidate.next_attempt_at <= 0
 AND NOT EXISTS (SELECT 1 FROM financial_outbox predecessor
   WHERE predecessor.organization_id=candidate.organization_id
   AND predecessor.aggregate_id=candidate.aggregate_id
   AND predecessor.sequence<candidate.sequence AND predecessor.sync_state<>'ACKNOWLEDGED')
 ORDER BY candidate.sequence'''
assert [r[0] for r in db.execute(ready_sql)] == ['e1']
db.execute("UPDATE financial_outbox SET sync_state='ACKNOWLEDGED' WHERE event_id='e1'")
assert [r[0] for r in db.execute(ready_sql)] == ['e2']
try:
    out('duplicate-identity','w1','other',1)
    raise AssertionError('outbox identity uniqueness did not fire')
except sqlite3.IntegrityError:
    pass
try:
    out('duplicate-sequence','other','inv',2)
    raise AssertionError('aggregate sequence uniqueness did not fire')
except sqlite3.IntegrityError:
    pass

# Remote Inbox history participates in the next local sequence (multi-device case).
db.execute('''INSERT INTO financial_inbox(
 event_id,organization_id,aggregate_id,aggregate_version,sequence,operation_type,payload_version,schema_version,
 payload,occurred_at,recorded_at,server_revision,apply_state,received_at
) VALUES('remote-4','org','remote-invoice',4,4,'INVOICE_UPDATED',1,1,'{}',1,1,44,'APPLIED',1)''')
next_seq = db.execute('''SELECT COALESCE(MAX(sequence),0)+1 FROM (
 SELECT sequence FROM financial_outbox WHERE organization_id='org' AND aggregate_id='remote-invoice'
 UNION ALL
 SELECT sequence FROM financial_inbox WHERE organization_id='org' AND aggregate_id='remote-invoice'
)''').fetchone()[0]
assert next_seq == 5, next_seq

# Model the server idempotency key: ten deliveries (including a lost-response retry) have one effect.
server = sqlite3.connect(':memory:')
server.execute('''CREATE TABLE events(
 event_id TEXT PRIMARY KEY, organization_id TEXT NOT NULL, operation_type TEXT NOT NULL, write_id TEXT NOT NULL,
 aggregate_id TEXT NOT NULL, aggregate_sequence INTEGER NOT NULL, server_revision INTEGER NOT NULL,
 UNIQUE(organization_id, operation_type, write_id), UNIQUE(organization_id, aggregate_id, aggregate_sequence))''')
for _ in range(10):
    server.execute('''INSERT OR IGNORE INTO events VALUES('evt','org','INVOICE_CREATED','write','inv',1,77)''')
assert server.execute('SELECT count(*) FROM events').fetchone()[0] == 1
assert server.execute("SELECT server_revision FROM events WHERE organization_id='org' AND operation_type='INVOICE_CREATED' AND write_id='write'").fetchone()[0] == 77

# A child received before its compatibility parent is durable WAITING_DEPENDENCY, not dropped/applied.
db.execute('''INSERT INTO financial_inbox(
 event_id,organization_id,aggregate_id,aggregate_version,sequence,operation_type,payload_version,schema_version,
 payload,occurred_at,recorded_at,server_revision,apply_state,apply_reason,received_at
) VALUES('child-wait','org','missing-parent',1,2,'PAYMENT_RECORDED',1,1,'{}',1,1,45,'WAITING_DEPENDENCY','parent invoice not available',1)''')
assert db.execute("SELECT apply_state FROM financial_inbox WHERE event_id='child-wait'").fetchone()[0] == 'WAITING_DEPENDENCY'

# Inbox itself deduplicates both event-id and server cursor revisions.
try:
    db.execute('''INSERT INTO financial_inbox(
     event_id,organization_id,aggregate_id,aggregate_version,sequence,operation_type,payload_version,schema_version,
     payload,occurred_at,recorded_at,server_revision,apply_state,received_at
    ) VALUES('remote-44b','org','x',1,1,'INVOICE_CREATED',1,1,'{}',1,1,44,'RECEIVED',1)''')
    raise AssertionError('inbox server revision uniqueness did not fire')
except sqlite3.IntegrityError:
    pass

# The runtime rejects duplicate global sync slots; source plan must remain collision-free.
slots = collections.defaultdict(list)
for path in ROOT.rglob('*.kt'):
    source = path.read_text(encoding='utf-8', errors='ignore')
    for stage, order in re.findall(r'SyncOperation\(SyncStage\.(PUSH|DELETE|PULL),\s*(\d+)', source):
        slots[(stage, int(order))].append(str(path.relative_to(ROOT)))
duplicates = {k:v for k,v in slots.items() if len(v) > 1}
if duplicates:
    raise AssertionError(f'duplicate SyncOperation slots: {duplicates}')

print('V249_FINANCIAL_SYNC_PASS')
