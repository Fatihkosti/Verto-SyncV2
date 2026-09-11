#!/usr/bin/env python3
"""Host-side contract probe for B11 migration 99→100.
Not a substitute for Room MigrationTestHelper; it verifies SQLite DDL/state invariants without Android/Gradle.
"""
import re
import sqlite3
from pathlib import Path

src = Path('data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations99To100.kt').read_text()
pattern = re.compile(r'db\.execSQL\(\s*(?:"""(.*?)"""\.trimIndent\(\)|"((?:[^"\\]|\\.)*)")\s*\)', re.S)
statements = []
for match in pattern.finditer(src):
    triple, single = match.groups()
    if triple is not None:
        lines = triple.splitlines()
        nonblank = [len(line) - len(line.lstrip()) for line in lines if line.strip()]
        indent = min(nonblank) if nonblank else 0
        statements.append('\n'.join(line[indent:] for line in lines).strip())
    else:
        statements.append(bytes(single, 'utf8').decode('unicode_escape'))
assert len(statements) >= 20, len(statements)

db = sqlite3.connect(':memory:')
db.executescript(
    """
    CREATE TABLE sync_outbox (
      mutation_id TEXT NOT NULL PRIMARY KEY, organization_id TEXT NOT NULL,
      aggregate_type TEXT NOT NULL, aggregate_id TEXT NOT NULL, operation_type TEXT NOT NULL,
      base_version INTEGER, local_sequence INTEGER NOT NULL, aggregate_sequence INTEGER NOT NULL,
      payload_version INTEGER NOT NULL, payload_json TEXT NOT NULL, semantic_fingerprint TEXT NOT NULL,
      command_batch_id TEXT, command_order INTEGER, depends_on_mutation_id TEXT,
      state TEXT NOT NULL DEFAULT 'PENDING', attempt_count INTEGER NOT NULL DEFAULT 0,
      last_error_type TEXT, last_error_code TEXT, next_attempt_at INTEGER NOT NULL DEFAULT 0,
      lease_owner TEXT, lease_token TEXT, lease_scope_epoch INTEGER, lease_expires_at INTEGER,
      acked_server_revision INTEGER, acked_server_version INTEGER, receipt_status TEXT,
      created_at INTEGER NOT NULL, acked_at INTEGER,
      CHECK(state IN ('PENDING','LEASED','RETRY','ACKNOWLEDGED','REQUIRES_REVIEW','REJECTED'))
    );
    CREATE TABLE sync_mutation_packet (
      organization_id TEXT NOT NULL, mutation_id TEXT NOT NULL,
      PRIMARY KEY(organization_id,mutation_id)
    );
    """
)
db.execute(
    """INSERT INTO sync_outbox(
      mutation_id,organization_id,aggregate_type,aggregate_id,operation_type,base_version,
      local_sequence,aggregate_sequence,payload_version,payload_json,semantic_fingerprint,
      state,created_at
    ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)""",
    ('m','o','PARTY_IDENTITY','a','UPSERT',1,1,1,1,'{"name":"local"}','fp','REQUIRES_REVIEW',1),
)

for sql in statements:
    db.execute(sql)

# Migration preserves rows and enables the two proof-bearing lifecycle states.
row = db.execute("SELECT payload_json,state FROM sync_outbox WHERE mutation_id='m'").fetchone()
assert row == ('{"name":"local"}', 'REQUIRES_REVIEW'), row
db.execute("UPDATE sync_outbox SET state='SUPERSEDED_PENDING_PROOF' WHERE mutation_id='m'")
db.execute("UPDATE sync_outbox SET state='SUPERSEDED_WITH_PROOF' WHERE mutation_id='m'")
assert db.execute("SELECT state FROM sync_outbox WHERE mutation_id='m'").fetchone()[0] == 'SUPERSEDED_WITH_PROOF'

# Semantic bytes remain immutable and rows cannot be pruned.
for stmt, expected in [
    ("UPDATE sync_outbox SET payload_json='{}' WHERE mutation_id='m'", 'FAIL_LOCAL_SYNC_IMMUTABILITY'),
    ("DELETE FROM sync_outbox WHERE mutation_id='m'", 'SYNC_OUTBOX_PRUNING_DISABLED_V306'),
]:
    try:
        db.execute(stmt)
        raise AssertionError(f'unexpectedly allowed: {stmt}')
    except sqlite3.IntegrityError as exc:
        assert expected in str(exc), (expected, str(exc))

packet_cols = {r[1] for r in db.execute('PRAGMA table_info(sync_mutation_packet)')}
assert 'supersedes_mutation_id' in packet_cols

evidence_cols = {r[1] for r in db.execute('PRAGMA table_info(sync_conflict_review_evidence)')}
assert {'local_payload_json','remote_payload_json','local_payload_sha256','remote_payload_sha256','local_base_version','outcome_proof'} <= evidence_cols
sha = 'a' * 64
db.execute(
    "INSERT INTO sync_conflict_review_evidence VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
    ('c','o','m','{}',sha,'sf',1,'{}',sha,1,2,'PROVEN_CONFLICT',3),
)
for stmt in [
    "UPDATE sync_conflict_review_evidence SET local_payload_json='x' WHERE conflict_id='c'",
    "DELETE FROM sync_conflict_review_evidence WHERE conflict_id='c'",
]:
    try:
        db.execute(stmt)
        raise AssertionError(f'evidence mutation unexpectedly allowed: {stmt}')
    except sqlite3.IntegrityError as exc:
        assert 'FAIL_CONFLICT_EVIDENCE_IMMUTABLE' in str(exc)

db.execute(
    "INSERT INTO sync_conflict_resolution_audit VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
    ('d','c','o','m','ACCEPT_SERVER','u','admin',sha,sha,2,None,'AUTHORIZED_USER_DECISION','u','OPEN','RESOLVED_SERVER_ACCEPTED',3),
)
for stmt in [
    "UPDATE sync_conflict_resolution_audit SET after_state='x' WHERE decision_id='d'",
    "DELETE FROM sync_conflict_resolution_audit WHERE decision_id='d'",
]:
    try:
        db.execute(stmt)
        raise AssertionError(f'audit mutation unexpectedly allowed: {stmt}')
    except sqlite3.IntegrityError as exc:
        assert 'FAIL_CONFLICT_AUDIT_IMMUTABLE' in str(exc)

print('B11_SQLITE_CONTRACT: PASS')
