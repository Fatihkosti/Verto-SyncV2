#!/usr/bin/env python3
import re, sqlite3
from pathlib import Path

migration = Path('data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations92To93.kt').read_text()
pat = re.compile(r'db\.execSQL\(\s*(?:"""(.*?)"""\.trimIndent\(\)|"((?:\\.|[^"\\])*)")\s*\)', re.S)
sqls=[]
for m in pat.finditer(migration):
    raw=m.group(1) if m.group(1) is not None else bytes(m.group(2), 'utf-8').decode('unicode_escape')
    sqls.append(raw.strip())
assert len(sqls)==14, f'expected 14 migration statements, got {len(sqls)}'

cx=sqlite3.connect(':memory:')
cx.execute('PRAGMA foreign_keys=ON')
cx.executescript('''
CREATE TABLE clients(id TEXT PRIMARY KEY);
CREATE TABLE party_roles(party_id TEXT NOT NULL, organization_id TEXT NOT NULL, role TEXT NOT NULL, deleted_at INTEGER);
CREATE TABLE customer_profiles(
 party_id TEXT PRIMARY KEY, segment TEXT NOT NULL, age_years INTEGER,
 purchase_contact_name TEXT NOT NULL DEFAULT '', business_activity TEXT NOT NULL DEFAULT '',
 workplace_name TEXT NOT NULL DEFAULT '', shop_name TEXT NOT NULL DEFAULT '', workshop_name TEXT NOT NULL DEFAULT '',
 vehicle_models TEXT NOT NULL DEFAULT '', workshop_worker_count INTEGER,
 updated_at INTEGER NOT NULL, sync_revision INTEGER NOT NULL DEFAULT 0, dirty INTEGER NOT NULL DEFAULT 1,
 FOREIGN KEY(party_id) REFERENCES clients(id) ON DELETE RESTRICT);
CREATE TABLE supplier_profiles(
 party_id TEXT PRIMARY KEY, scope TEXT NOT NULL, country TEXT NOT NULL DEFAULT '', currency_code TEXT NOT NULL DEFAULT '',
 specialty TEXT NOT NULL DEFAULT '', updated_at INTEGER NOT NULL, sync_revision INTEGER NOT NULL DEFAULT 0,
 dirty INTEGER NOT NULL DEFAULT 1, FOREIGN KEY(party_id) REFERENCES clients(id) ON DELETE RESTRICT);
''')
cx.executemany('INSERT INTO clients(id) VALUES (?)', [('p1',),('p2',)])
cx.executemany('INSERT INTO party_roles VALUES (?,?,?,NULL)', [
 ('p1','orgA','CUSTOMER'),('p1','orgB','CUSTOMER'),('p1','orgA','SUPPLIER')])
cx.execute("INSERT INTO customer_profiles VALUES ('p1','COMPANY',NULL,'Buyer','Parts','','Shop','','Hilux',NULL,100,7,0)")
cx.execute("INSERT INTO supplier_profiles VALUES ('p1','LOCAL','SD','SDG','Parts',100,7,0)")
cx.execute("INSERT INTO customer_profiles VALUES ('p2','OTHER',30,'','','','','','',NULL,101,0,1)")
for sql in sqls:
    cx.execute(sql)

cust=cx.execute('SELECT organization_id,party_id,segment,purchase_contact_name FROM customer_profiles ORDER BY organization_id,party_id').fetchall()
supp=cx.execute('SELECT organization_id,party_id,scope FROM supplier_profiles ORDER BY organization_id,party_id').fetchall()
assert cust == [('', 'p2','OTHER',''),('orgA','p1','COMPANY','Buyer'),('orgB','p1','COMPANY','Buyer')], cust
assert supp == [('orgA','p1','LOCAL')], supp
pk=[r[1] for r in cx.execute("PRAGMA table_info('customer_profiles')") if r[5] > 0]
assert pk == ['organization_id','party_id'], pk
try:
    cx.execute("INSERT INTO customer_profiles(organization_id,party_id,segment,updated_at) VALUES('orgA','p1','OTHER',1)")
    raise AssertionError('composite primary key failed')
except sqlite3.IntegrityError:
    pass
print('PARTY_386_MIGRATION_92_93=PASS')
