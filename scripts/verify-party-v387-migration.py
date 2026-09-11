#!/usr/bin/env python3
import sqlite3

con = sqlite3.connect(':memory:')
con.execute('PRAGMA foreign_keys=ON')
con.executescript('''
CREATE TABLE clients(
 id TEXT PRIMARY KEY NOT NULL,
 name TEXT NOT NULL,
 phone TEXT NOT NULL,
 clientType TEXT NOT NULL DEFAULT 'INDIVIDUAL'
);
CREATE TABLE party_roles(
 id TEXT PRIMARY KEY NOT NULL,
 party_id TEXT NOT NULL,
 organization_id TEXT NOT NULL,
 role TEXT NOT NULL,
 status TEXT NOT NULL,
 FOREIGN KEY(party_id) REFERENCES clients(id) ON DELETE RESTRICT
);
CREATE TABLE customer_profiles(
 organization_id TEXT NOT NULL,
 party_id TEXT NOT NULL,
 segment TEXT NOT NULL,
 PRIMARY KEY(organization_id, party_id),
 FOREIGN KEY(party_id) REFERENCES clients(id) ON DELETE RESTRICT
);
CREATE TABLE party_sync_outbox(
 id TEXT PRIMARY KEY NOT NULL,
 aggregate_type TEXT NOT NULL,
 aggregate_id TEXT NOT NULL
);
INSERT INTO clients(id,name,phone,clientType) VALUES('c1','Client','249','MARKETER');
INSERT INTO party_roles VALUES('r1','c1','org1','CUSTOMER','ACTIVE');
INSERT INTO customer_profiles VALUES('org1','c1','MARKETER');
INSERT INTO party_sync_outbox VALUES('o1','ROLE','c1');
INSERT INTO party_sync_outbox VALUES('o2','IDENTITY','c1');
''')
con.execute("UPDATE clients SET clientType='' WHERE clientType<>''")
con.execute("DELETE FROM party_sync_outbox WHERE aggregate_type='ROLE'")
assert con.execute("SELECT clientType FROM clients WHERE id='c1'").fetchone()[0] == ''
assert con.execute("SELECT segment FROM customer_profiles WHERE party_id='c1'").fetchone()[0] == 'MARKETER'
assert con.execute("SELECT COUNT(*) FROM party_sync_outbox WHERE aggregate_type='ROLE'").fetchone()[0] == 0
assert con.execute("SELECT COUNT(*) FROM party_sync_outbox WHERE aggregate_type='IDENTITY'").fetchone()[0] == 1
assert con.execute('PRAGMA foreign_key_check').fetchall() == []
print('PASS migration 93->94: legacy classification erased; Party V2 and FKs preserved')
