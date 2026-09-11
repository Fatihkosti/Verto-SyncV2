#!/usr/bin/env python3
import sqlite3, sys

ALLOWED = {'INDIVIDUAL','COMPANY','WORKSHOP_OWNER','MARKETER','TRADER','DISTRIBUTOR'}
EXPECTED = {
    'INDIVIDUAL':'INDIVIDUAL', 'CAR_OWNER':'INDIVIDUAL', 'OTHER':'INDIVIDUAL',
    'COMPANY':'COMPANY', 'INSTITUTION':'COMPANY',
    'WORKSHOP_OWNER':'WORKSHOP_OWNER', 'MECHANIC':'WORKSHOP_OWNER',
    'MARKETER':'MARKETER',
    'TRADER':'TRADER', 'SHOP_OWNER':'TRADER', 'COMPETITOR':'TRADER',
    'DISTRIBUTOR':'DISTRIBUTOR', 'WHOLESALE_TRADER':'DISTRIBUTOR',
    'ALIEN':'INDIVIDUAL',
}

con = sqlite3.connect(':memory:')
con.execute('PRAGMA foreign_keys=ON')
con.executescript('''
CREATE TABLE clients(id TEXT PRIMARY KEY, organization_id TEXT NOT NULL);
CREATE TABLE customer_profiles(
  organization_id TEXT NOT NULL,
  party_id TEXT NOT NULL,
  segment TEXT NOT NULL,
  FOREIGN KEY(party_id) REFERENCES clients(id),
  PRIMARY KEY(organization_id,party_id)
);
''')
for i, old in enumerate(EXPECTED):
    pid=f'p{i}'
    con.execute('INSERT INTO clients VALUES(?,?)',(pid,'org'))
    con.execute('INSERT INTO customer_profiles VALUES(?,?,?)',('org',pid,old))
con.commit()

con.execute('''
UPDATE customer_profiles
   SET segment = CASE
       WHEN segment IN ('INDIVIDUAL','CAR_OWNER','OTHER') THEN 'INDIVIDUAL'
       WHEN segment IN ('COMPANY','INSTITUTION') THEN 'COMPANY'
       WHEN segment IN ('WORKSHOP_OWNER','MECHANIC') THEN 'WORKSHOP_OWNER'
       WHEN segment = 'MARKETER' THEN 'MARKETER'
       WHEN segment IN ('TRADER','SHOP_OWNER','COMPETITOR') THEN 'TRADER'
       WHEN segment IN ('DISTRIBUTOR','WHOLESALE_TRADER') THEN 'DISTRIBUTOR'
       ELSE 'INDIVIDUAL'
   END
''')
rows = {old: con.execute('SELECT segment FROM customer_profiles WHERE party_id=?',(f'p{i}',)).fetchone()[0]
        for i, old in enumerate(EXPECTED)}
errors=[]
for old, want in EXPECTED.items():
    got=rows[old]
    if got != want: errors.append(f'{old}: expected {want}, got {got}')
if set(rows.values()) - ALLOWED:
    errors.append('migration produced unapproved segment(s): '+str(set(rows.values())-ALLOWED))
fk = con.execute('PRAGMA foreign_key_check').fetchall()
if fk: errors.append('foreign_key_check failed: '+repr(fk))
if errors:
    for e in errors: print('FAIL:',e)
    sys.exit(1)
print('PASS migration 94->95: all legacy segments normalized to six approved values; FKs preserved')
print('PARTY_388_MIGRATION_94_95=PASS')
