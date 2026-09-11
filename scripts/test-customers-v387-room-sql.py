#!/usr/bin/env python3
import re, sqlite3
from pathlib import Path

root=Path(__file__).resolve().parents[1]
src=(root/'data/database/src/main/kotlin/com/verto/app/data/local/dao/ClientDao.kt').read_text()

def query_for(method):
    marker = 'abstract fun ' + method + '('
    end = src.find(marker)
    assert end >= 0, method
    start = src.rfind('@Query(\"\"\"', 0, end)
    assert start >= 0, method + ' query start'
    qstart = start + len('@Query(\"\"\"')
    qend = src.find('\"\"\")', qstart)
    assert qend >= 0 and qend < end, method + ' query end'
    return src[qstart:qend].strip()

cx=sqlite3.connect(':memory:')
cx.row_factory=sqlite3.Row
cx.executescript('''
CREATE TABLE clients(id TEXT PRIMARY KEY,name TEXT NOT NULL,phone TEXT NOT NULL,createdAt INTEGER NOT NULL);
CREATE TABLE party_roles(id TEXT PRIMARY KEY,party_id TEXT,organization_id TEXT,role TEXT,status TEXT,deleted_at INTEGER);
CREATE TABLE customer_profiles(organization_id TEXT,party_id TEXT,segment TEXT,PRIMARY KEY(organization_id,party_id));
CREATE TABLE supplier_profiles(organization_id TEXT,party_id TEXT,scope TEXT,PRIMARY KEY(organization_id,party_id));
CREATE TABLE invoices(id TEXT PRIMARY KEY,clientId TEXT,organization_id TEXT,category TEXT,totalAmount REAL,dueDate INTEGER,voided INTEGER DEFAULT 0);
CREATE TABLE payments(id TEXT PRIMARY KEY,invoiceId TEXT,clientId TEXT,amount REAL);
INSERT INTO clients VALUES('shared','Shared','249000',2),('b-only','B Only','249111',1);
INSERT INTO party_roles VALUES
 ('ra','shared','org-a','CUSTOMER','ACTIVE',NULL),
 ('rb','shared','org-b','CUSTOMER','ACTIVE',NULL),
 ('rbo','b-only','org-b','CUSTOMER','ACTIVE',NULL);
INSERT INTO customer_profiles VALUES('org-a','shared','MARKETER'),('org-b','shared','COMPANY'),('org-b','b-only','INDIVIDUAL');
INSERT INTO invoices VALUES
 ('ia','shared','org-a','SALE',100,9999999999999,0),
 ('ib','shared','org-b','SALE',900,9999999999999,0),
 ('ibb','b-only','org-b','SALE',50,9999999999999,0);
INSERT INTO payments VALUES('pa','ia','shared',40),('pb','ib','shared',800),('pbb','ibb','b-only',10);
''')

q=query_for('getAllClientsWithBalance')
a=cx.execute(q, {'organizationId':'org-a'}).fetchall()
b=cx.execute(q, {'organizationId':'org-b'}).fetchall()
assert [r['id'] for r in a]==['shared'], [dict(r) for r in a]
assert a[0]['customerSegment']=='MARKETER'
assert a[0]['totalDebt']==100 and a[0]['totalPaid']==40
shared_b=[r for r in b if r['id']=='shared'][0]
assert shared_b['customerSegment']=='COMPANY'
assert shared_b['totalDebt']==900 and shared_b['totalPaid']==800
assert {r['id'] for r in b}=={'shared','b-only'}
print('PASS: exact getAllClientsWithBalance SQL isolates tenants, profiles, invoices, payments')

qs=query_for('searchClientsWithBalance')
sa=cx.execute(qs, {'organizationId':'org-a','query':'Shared'}).fetchall()
sb=cx.execute(qs, {'organizationId':'org-b','query':'B Only'}).fetchall()
assert [r['id'] for r in sa]==['shared']
assert [r['id'] for r in sb]==['b-only']
print('PASS: exact searchClientsWithBalance SQL isolates tenant search results')

cx.execute("UPDATE party_roles SET status='ARCHIVED' WHERE id='ra'")
a2=cx.execute(q, {'organizationId':'org-a'}).fetchall()
b2=cx.execute(q, {'organizationId':'org-b'}).fetchall()
assert a2==[]
assert {r['id'] for r in b2}=={'shared','b-only'}
print('PASS: archived customer role disappears only from its organization')
print('CUSTOMER_ROOM_SQL_387=PASS')
