#!/usr/bin/env python3
import sqlite3, hashlib, sys

con = sqlite3.connect(':memory:')
con.executescript('''
CREATE TABLE sync_recovery_state (
 scope_id TEXT PRIMARY KEY, organization_id TEXT NOT NULL, sync_principal_id TEXT NOT NULL,
 contract_family TEXT NOT NULL, contract_version INTEGER NOT NULL, scope_definition_version INTEGER NOT NULL,
 state TEXT NOT NULL, reason TEXT NOT NULL, bootstrap_session_id TEXT, baseline_cursor TEXT,
 baseline_revision INTEGER, next_page_token TEXT, expected_snapshot_rows INTEGER,
 staged_snapshot_rows INTEGER NOT NULL DEFAULT 0, recovery_generation INTEGER NOT NULL DEFAULT 0,
 attempt_count INTEGER NOT NULL DEFAULT 0, last_error_code TEXT, started_at INTEGER, updated_at INTEGER NOT NULL, completed_at INTEGER
);
CREATE TABLE sync_bootstrap_stage (
 scope_id TEXT NOT NULL, bootstrap_session_id TEXT NOT NULL, ordinal INTEGER NOT NULL,
 aggregate_type TEXT NOT NULL, aggregate_id TEXT NOT NULL, entity_version INTEGER,
 payload_version INTEGER NOT NULL, payload_json TEXT NOT NULL, partition_key TEXT NOT NULL,
 content_fingerprint TEXT NOT NULL, PRIMARY KEY(scope_id,bootstrap_session_id,ordinal)
);
''')

migration_sql = [
"ALTER TABLE sync_recovery_state ADD COLUMN expected_snapshot_digest TEXT",
"ALTER TABLE sync_recovery_state ADD COLUMN expected_coverage_json TEXT",
"ALTER TABLE sync_recovery_state ADD COLUMN bootstrap_high_watermark INTEGER",
"ALTER TABLE sync_recovery_state ADD COLUMN bootstrap_delta_token TEXT",
"ALTER TABLE sync_recovery_state ADD COLUMN stage_verified_at INTEGER",
"ALTER TABLE sync_bootstrap_stage ADD COLUMN is_tombstone INTEGER NOT NULL DEFAULT 0",
"ALTER TABLE sync_bootstrap_stage ADD COLUMN promotion_state TEXT NOT NULL DEFAULT 'STAGED'",
"ALTER TABLE sync_bootstrap_stage ADD COLUMN wait_reason TEXT",
"ALTER TABLE sync_bootstrap_stage ADD COLUMN applied_at INTEGER",
]
for sql in migration_sql: con.execute(sql)
con.executescript('''
CREATE TABLE sync_recovery_protection_manifest (
 scope_id TEXT NOT NULL, bootstrap_session_id TEXT NOT NULL, organization_id TEXT NOT NULL,
 combined_sha256 TEXT NOT NULL, unified_sha256 TEXT NOT NULL, party_sha256 TEXT NOT NULL,
 financial_sha256 TEXT NOT NULL, inventory_stock_sha256 TEXT NOT NULL, inventory_cost_sha256 TEXT NOT NULL,
 optimal_sha256 TEXT NOT NULL, attachment_sha256 TEXT NOT NULL, pending_reference_sha256 TEXT NOT NULL,
 mutation_packet_sha256 TEXT NOT NULL, local_generation_sha256 TEXT NOT NULL, captured_at INTEGER NOT NULL,
 PRIMARY KEY(scope_id, bootstrap_session_id),
 CHECK(length(combined_sha256)=64), CHECK(length(unified_sha256)=64), CHECK(length(party_sha256)=64),
 CHECK(length(financial_sha256)=64), CHECK(length(inventory_stock_sha256)=64), CHECK(length(inventory_cost_sha256)=64),
 CHECK(length(optimal_sha256)=64), CHECK(length(attachment_sha256)=64), CHECK(length(pending_reference_sha256)=64),
 CHECK(length(mutation_packet_sha256)=64), CHECK(length(local_generation_sha256)=64)
);
CREATE INDEX index_sync_recovery_protection_manifest_org ON sync_recovery_protection_manifest(organization_id);
''')

stage_cols = {r[1]: r for r in con.execute('PRAGMA table_info(sync_bootstrap_stage)')}
assert stage_cols['is_tombstone'][4] == '0'
assert stage_cols['promotion_state'][4] == "'STAGED'"
state_cols = {r[1] for r in con.execute('PRAGMA table_info(sync_recovery_state)')}
for col in ['expected_snapshot_digest','expected_coverage_json','bootstrap_high_watermark','bootstrap_delta_token','stage_verified_at']:
    assert col in state_cols
h = hashlib.sha256(b'x').hexdigest()
vals = ['s','b','o'] + [h]*11 + [1]
con.execute('INSERT INTO sync_recovery_protection_manifest VALUES (' + ','.join('?'*len(vals)) + ')', vals)
try:
    bad = ['s2','b2','o'] + ['bad'] + [h]*10 + [1]
    con.execute('INSERT INTO sync_recovery_protection_manifest VALUES (' + ','.join('?'*len(bad)) + ')', bad)
    raise AssertionError('sha check did not reject')
except sqlite3.IntegrityError:
    pass

# Process-boundary precursor: staged state survives a rolled-back promotion transaction.
con.execute("INSERT INTO sync_recovery_state(scope_id,organization_id,sync_principal_id,contract_family,contract_version,scope_definition_version,state,reason,bootstrap_session_id,baseline_cursor,expected_snapshot_rows,updated_at) VALUES('scope','org','p','f',1,1,'STAGED_VERIFIED','INITIAL_BOOTSTRAP','sess','delta',1,1)")
con.execute("INSERT INTO sync_bootstrap_stage(scope_id,bootstrap_session_id,ordinal,aggregate_type,aggregate_id,payload_version,payload_json,partition_key,content_fingerprint) VALUES('scope','sess',1,'NOTE','n1',1,'{}','p','abc')")
con.commit()
try:
    con.execute('BEGIN')
    con.execute("UPDATE sync_bootstrap_stage SET promotion_state='APPLIED' WHERE scope_id='scope'")
    raise RuntimeError('simulated process failure')
except RuntimeError:
    con.rollback()
assert con.execute("SELECT promotion_state FROM sync_bootstrap_stage WHERE scope_id='scope'").fetchone()[0] == 'STAGED'
print('B13_SQLITE_CONTRACT=PASS')
