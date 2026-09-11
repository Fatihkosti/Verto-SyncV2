#!/usr/bin/env python3
"""Host-side B12 SQLite contract probe.
Verifies the already-existing expense revision schema plus B12 append-only guards.
This is not a substitute for Room MigrationTestHelper/Android instrumentation.
"""
import re
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
migration = (ROOT / 'data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations96To97.kt').read_text(encoding='utf-8')
guards = (ROOT / 'data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseB12ExpenseGuards.kt').read_text(encoding='utf-8')

# Keep this probe explicit: mirror the DDL declared by migration 96->97, then execute the exact B12 triggers.
db = sqlite3.connect(':memory:')
db.executescript(
    """
    CREATE TABLE expense_revision_history (
        organization_id TEXT NOT NULL,
        expense_id TEXT NOT NULL,
        server_version INTEGER NOT NULL,
        previous_version INTEGER,
        write_id TEXT NOT NULL,
        before_content_hash TEXT,
        after_content_hash TEXT NOT NULL,
        actor_id TEXT NOT NULL,
        changed_at INTEGER NOT NULL,
        PRIMARY KEY (organization_id,expense_id,server_version),
        CHECK(server_version>=0),
        CHECK(previous_version IS NULL OR previous_version>=0),
        CHECK(before_content_hash IS NULL OR (length(before_content_hash)=64 AND lower(before_content_hash)=before_content_hash)),
        CHECK(length(after_content_hash)=64 AND lower(after_content_hash)=after_content_hash)
    );
    CREATE UNIQUE INDEX index_expense_revision_history_version
      ON expense_revision_history (organization_id,expense_id,server_version);
    CREATE UNIQUE INDEX index_expense_revision_history_write
      ON expense_revision_history (organization_id,write_id);
    """
)

# Assert this probe did not silently diverge from the product's declared schema names/constraints.
for token in (
    'CREATE TABLE IF NOT EXISTS expense_revision_history',
    'index_expense_revision_history_version',
    'index_expense_revision_history_write',
    'before_content_hash',
    'after_content_hash',
):
    assert token in migration, token

triggers = re.findall(r'db\.execSQL\(\s*"""(.*?)"""\.trimIndent\(\)\s*\)', guards, flags=re.S)
assert len(triggers) == 2, len(triggers)
for sql in triggers:
    db.execute(sql.strip())

sha_a = 'a' * 64
sha_b = 'b' * 64
row = ('org', 'expense', 2, 1, 'write-2', sha_a, sha_b, 'actor', 123456)
db.execute('INSERT INTO expense_revision_history VALUES(?,?,?,?,?,?,?,?,?)', row)
assert db.execute(
    'SELECT previous_version,write_id,before_content_hash,after_content_hash,actor_id,changed_at '
    'FROM expense_revision_history WHERE organization_id=? AND expense_id=? AND server_version=?',
    ('org', 'expense', 2),
).fetchone() == (1, 'write-2', sha_a, sha_b, 'actor', 123456)

# Version and write-id uniqueness are independent invariants.
for duplicate in (
    ('org', 'expense', 2, 1, 'write-other', sha_a, sha_b, 'actor', 123457),
    ('org', 'expense-other', 3, 2, 'write-2', sha_a, sha_b, 'actor', 123458),
):
    try:
        db.execute('INSERT INTO expense_revision_history VALUES(?,?,?,?,?,?,?,?,?)', duplicate)
        raise AssertionError('duplicate history unexpectedly allowed')
    except sqlite3.IntegrityError:
        pass

# Append-only means both mutation paths are rejected, not merely discouraged at DAO level.
for stmt, expected in (
    ("UPDATE expense_revision_history SET changed_at=999 WHERE write_id='write-2'", 'FAIL_EXPENSE_REVISION_HISTORY_IMMUTABLE'),
    ("DELETE FROM expense_revision_history WHERE write_id='write-2'", 'FAIL_EXPENSE_REVISION_HISTORY_IMMUTABLE'),
):
    try:
        db.execute(stmt)
        raise AssertionError(f'unexpectedly allowed: {stmt}')
    except sqlite3.IntegrityError as exc:
        assert expected in str(exc), str(exc)

print('B12_SQLITE_CONTRACT=PASS')
