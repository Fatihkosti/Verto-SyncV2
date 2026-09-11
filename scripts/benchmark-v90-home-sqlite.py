#!/usr/bin/env python3
"""Diagnostic SQLite baseline for Home/search queries. Not an Android/Room benchmark."""
from __future__ import annotations
import random, sqlite3, statistics, tempfile, time
from pathlib import Path

SEED=90
random.seed(SEED)
COUNTS={"clients":20000,"inventory_items":30000,"invoices":50000,"payments":80000,"inventory_movements":120000}

def ms(fn, repeats=9):
    values=[]
    for _ in range(repeats):
        t=time.perf_counter_ns(); fn(); values.append((time.perf_counter_ns()-t)/1e6)
    values.sort()
    return statistics.median(values), values[max(0, int(len(values)*0.95)-1)]

with tempfile.TemporaryDirectory(prefix='verto-v90-') as td:
    db=sqlite3.connect(str(Path(td)/'bench.db'))
    db.executescript('''
    PRAGMA journal_mode=OFF; PRAGMA synchronous=OFF; PRAGMA temp_store=MEMORY;
    CREATE TABLE clients(id TEXT PRIMARY KEY,name TEXT NOT NULL,phone TEXT NOT NULL,createdAt INTEGER NOT NULL);
    CREATE TABLE inventory_items(id TEXT PRIMARY KEY,partNumber TEXT NOT NULL,name TEXT NOT NULL,isService INTEGER NOT NULL,quantity REAL NOT NULL,minQuantity REAL NOT NULL,sellPrice REAL NOT NULL,createdAt INTEGER NOT NULL);
    CREATE TABLE invoices(id TEXT PRIMARY KEY,invoiceNumber TEXT NOT NULL,clientId TEXT NOT NULL,type TEXT NOT NULL,category TEXT NOT NULL,totalAmount REAL NOT NULL,createdAt INTEGER NOT NULL,dueDate INTEGER,status TEXT NOT NULL,voided INTEGER NOT NULL DEFAULT 0);
    CREATE INDEX index_invoices_clientId ON invoices(clientId);
    CREATE INDEX index_invoices_createdAt ON invoices(createdAt);
    CREATE TABLE payments(id TEXT PRIMARY KEY,invoiceId TEXT NOT NULL,clientId TEXT NOT NULL,amount REAL NOT NULL,paidAt INTEGER NOT NULL);
    CREATE INDEX index_payments_invoiceId ON payments(invoiceId);
    CREATE INDEX index_payments_paidAt ON payments(paidAt);
    CREATE TABLE inventory_movements(id TEXT PRIMARY KEY,itemId TEXT NOT NULL,movementType TEXT NOT NULL,quantity REAL NOT NULL,createdAt INTEGER NOT NULL);
    CREATE INDEX index_inventory_movements_itemId ON inventory_movements(itemId);
    ''')
    now=1_800_000_000_000
    db.executemany('INSERT INTO clients VALUES(?,?,?,?)', ((f'c{i}',f'عميل {i:05d}',f'09{i:08d}',now-i*1000) for i in range(COUNTS['clients'])))
    db.executemany('INSERT INTO inventory_items VALUES(?,?,?,?,?,?,?,?)', ((f'p{i}',f'PT-{i:06d}',f'قطعة {i:06d}',1 if i%97==0 else 0,float(i%40),float(i%7),float((i%500)+1),now-i*2000) for i in range(COUNTS['inventory_items'])))
    db.executemany('INSERT INTO invoices VALUES(?,?,?,?,?,?,?,?,?,?)', ((f'i{i}',f'INV-{i:07d}',f'c{i%COUNTS["clients"]}','SALE','CREDIT' if i%3==0 else 'CASH',float((i%9000)+100),now-i*5000,now+(i%40-20)*86400000,'OPEN',0) for i in range(COUNTS['invoices'])))
    db.executemany('INSERT INTO payments VALUES(?,?,?,?,?)', ((f'pay{i}',f'i{i%COUNTS["invoices"]}',f'c{i%COUNTS["clients"]}',float((i%2000)+10),now-i*3000) for i in range(COUNTS['payments'])))
    db.executemany('INSERT INTO inventory_movements VALUES(?,?,?,?,?)', ((f'm{i}',f'p{i%COUNTS["inventory_items"]}','OUT' if i%2 else 'IN',float((i%5)+1),now-i*7000) for i in range(COUNTS['inventory_movements'])))
    db.commit()

    queries={
      'client_contains':("SELECT id,name,phone FROM clients WHERE name LIKE ? OR phone LIKE ? LIMIT 20",('%123%', '%123%')),
      'inventory_contains':("SELECT id,name,partNumber FROM inventory_items WHERE name LIKE ? OR partNumber LIKE ? LIMIT 20",('%123%', '%123%')),
      'invoice_number_contains':("SELECT id,invoiceNumber,totalAmount FROM invoices WHERE invoiceNumber LIKE ? LIMIT 20",('%123%',)),
      'low_stock_count':("SELECT count(*) FROM inventory_items WHERE quantity <= minQuantity",()),
      'slow_moving_90d':("SELECT count(*) FROM inventory_items i WHERE i.isService=0 AND i.quantity>0 AND NOT EXISTS (SELECT 1 FROM inventory_movements m WHERE m.itemId=i.id AND m.movementType='OUT' AND m.createdAt>=?)",(now-90*86400000,)),
      'due_credit_remaining':("SELECT i.id,i.invoiceNumber,i.totalAmount-COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.invoiceId=i.id),0) remaining FROM invoices i WHERE i.category='CREDIT' AND i.status='OPEN' AND i.voided=0 AND i.dueDate<=? LIMIT 50",(now,)),
      'client_balances':("SELECT c.id,c.name,COALESCE((SELECT SUM(i.totalAmount) FROM invoices i WHERE i.clientId=c.id AND i.category='CREDIT' AND i.voided=0),0)-COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.clientId=c.id),0) remaining FROM clients c LIMIT 500",()),
    }
    print('# Verto v90 SQLite diagnostic baseline')
    print(f'SEED={SEED}')
    print('DATASET=' + ','.join(f'{k}:{v}' for k,v in COUNTS.items()))
    for name,(sql,args) in queries.items():
        # warmup
        db.execute(sql,args).fetchall()
        median,p95=ms(lambda: db.execute(sql,args).fetchall())
        plan=' | '.join(str(row) for row in db.execute('EXPLAIN QUERY PLAN '+sql,args).fetchall())
        print(f'RESULT {name} median_ms={median:.3f} p95_ms={p95:.3f}')
        print(f'PLAN {name} {plan}')
