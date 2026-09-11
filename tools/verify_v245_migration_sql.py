#!/usr/bin/env python3
from pathlib import Path
import sqlite3, sys
ROOT=Path(__file__).resolve().parents[1]
src=(ROOT/'data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations62To63.kt').read_text(encoding='utf-8')
required=[
 'ALTER TABLE invoices ADD COLUMN organization_id',
 'supplier_invoice_ref_normalized',
 'index_invoices_org_supplier_external_ref',
 'ALTER TABLE payments ADD COLUMN source_type',
 'ALTER TABLE inventory_movements ADD COLUMN source_type',
 'ALTER TABLE cash_register_movements ADD COLUMN source_type',
 'ALTER TABLE audit_log ADD COLUMN sourceType',
 'CREATE TABLE IF NOT EXISTS invoice_write_guard',
 'index_invoice_write_guard_identity',
]
missing=[x for x in required if x not in src]
if missing:
 print('V245_MIGRATION_SQL_FAIL missing',missing); sys.exit(1)

c=sqlite3.connect(':memory:')
c.executescript('''
CREATE TABLE invoices(id TEXT PRIMARY KEY, clientId TEXT NOT NULL);
CREATE TABLE payments(id TEXT PRIMARY KEY);
CREATE TABLE inventory_movements(id TEXT PRIMARY KEY);
CREATE TABLE cash_register_movements(id TEXT PRIMARY KEY);
CREATE TABLE audit_log(id TEXT PRIMARY KEY);
''')
# Execute F245's additive SQL against the minimal v62-shaped tables.
sqls=[
 "ALTER TABLE invoices ADD COLUMN organization_id TEXT NOT NULL DEFAULT ''",
 "ALTER TABLE invoices ADD COLUMN supplier_invoice_ref TEXT",
 "ALTER TABLE invoices ADD COLUMN supplier_invoice_ref_normalized TEXT",
 "CREATE UNIQUE INDEX IF NOT EXISTS index_invoices_org_supplier_external_ref ON invoices(organization_id, clientId, supplier_invoice_ref_normalized)",
 "ALTER TABLE payments ADD COLUMN source_type TEXT NOT NULL DEFAULT ''",
 "ALTER TABLE payments ADD COLUMN source_id TEXT NOT NULL DEFAULT ''",
 "ALTER TABLE payments ADD COLUMN source_version INTEGER NOT NULL DEFAULT 1",
 "ALTER TABLE payments ADD COLUMN write_id TEXT NOT NULL DEFAULT ''",
 "ALTER TABLE inventory_movements ADD COLUMN source_type TEXT NOT NULL DEFAULT ''",
 "ALTER TABLE inventory_movements ADD COLUMN source_id TEXT NOT NULL DEFAULT ''",
 "ALTER TABLE inventory_movements ADD COLUMN source_version INTEGER NOT NULL DEFAULT 1",
 "ALTER TABLE inventory_movements ADD COLUMN write_id TEXT NOT NULL DEFAULT ''",
 "CREATE INDEX IF NOT EXISTS index_inventory_movements_source_id ON inventory_movements(source_id)",
 "ALTER TABLE cash_register_movements ADD COLUMN source_type TEXT NOT NULL DEFAULT ''",
 "ALTER TABLE cash_register_movements ADD COLUMN source_id TEXT NOT NULL DEFAULT ''",
 "ALTER TABLE cash_register_movements ADD COLUMN source_version INTEGER NOT NULL DEFAULT 1",
 "ALTER TABLE cash_register_movements ADD COLUMN write_id TEXT NOT NULL DEFAULT ''",
 "ALTER TABLE audit_log ADD COLUMN sourceType TEXT NOT NULL DEFAULT ''",
 "ALTER TABLE audit_log ADD COLUMN sourceId TEXT NOT NULL DEFAULT ''",
 "ALTER TABLE audit_log ADD COLUMN sourceVersion INTEGER NOT NULL DEFAULT 1",
 "ALTER TABLE audit_log ADD COLUMN writeId TEXT NOT NULL DEFAULT ''",
 '''CREATE TABLE IF NOT EXISTS invoice_write_guard (
 id TEXT NOT NULL PRIMARY KEY, organization_id TEXT NOT NULL, operation_type TEXT NOT NULL,
 write_id TEXT NOT NULL, target_invoice_id TEXT NOT NULL, source_type TEXT NOT NULL,
 source_id TEXT NOT NULL, source_version INTEGER NOT NULL, createdAt INTEGER NOT NULL)''',
 "CREATE UNIQUE INDEX IF NOT EXISTS index_invoice_write_guard_identity ON invoice_write_guard(organization_id, operation_type, write_id)",
 "CREATE INDEX IF NOT EXISTS index_invoice_write_guard_target ON invoice_write_guard(target_invoice_id)",
]
try:
 for sql in sqls: c.execute(sql)
except Exception as e:
 print('V245_MIGRATION_SQL_FAIL execute',e); sys.exit(1)

# Supplier external reference is unique per org+supplier when present; multiple NULLs remain legal.
c.execute("INSERT INTO invoices(id,clientId,organization_id,supplier_invoice_ref_normalized) VALUES('1','s','o','abc')")
try:
 c.execute("INSERT INTO invoices(id,clientId,organization_id,supplier_invoice_ref_normalized) VALUES('2','s','o','abc')")
 print('V245_MIGRATION_SQL_FAIL supplier-duplicate-accepted'); sys.exit(1)
except sqlite3.IntegrityError: pass
c.execute("INSERT INTO invoices(id,clientId,organization_id,supplier_invoice_ref_normalized) VALUES('3','s','o',NULL)")
c.execute("INSERT INTO invoices(id,clientId,organization_id,supplier_invoice_ref_normalized) VALUES('4','s','o',NULL)")

# Durable write identity accepts one owner only.
c.execute("INSERT INTO invoice_write_guard VALUES('g1','o','CREATE','w','i1','INVOICE','i1',1,1)")
try:
 c.execute("INSERT INTO invoice_write_guard VALUES('g2','o','CREATE','w','i2','INVOICE','i2',1,2)")
 print('V245_MIGRATION_SQL_FAIL write-guard-duplicate-accepted'); sys.exit(1)
except sqlite3.IntegrityError: pass

print('V245_MIGRATION_SQL_PASS')
