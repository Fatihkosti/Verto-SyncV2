#!/usr/bin/env python3
import re, sqlite3, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
src=(ROOT/'data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations61To62.kt').read_text()
# Minimal v61 financial tables: enough to execute every ALTER/UPDATE used by F244.
creates=[
'''CREATE TABLE invoices(totalAmount REAL NOT NULL, commission REAL NOT NULL)''',
'''CREATE TABLE invoice_items(buyPrice REAL NOT NULL,sellPrice REAL NOT NULL,totalPrice REAL NOT NULL,adjustedPurchasePrice REAL NOT NULL)''',
'''CREATE TABLE payments(amount REAL NOT NULL)''',
'''CREATE TABLE cash_register(balance REAL NOT NULL)''',
'''CREATE TABLE cash_register_movements(amount REAL NOT NULL,balanceBefore REAL NOT NULL,balanceAfter REAL NOT NULL)''',
'''CREATE TABLE inventory_items(buyPrice REAL NOT NULL,sellPrice REAL NOT NULL)''',
'''CREATE TABLE inventory_movements(unitPrice REAL NOT NULL)''',
]
con=sqlite3.connect(':memory:')
for sql in creates: con.execute(sql)
con.execute('INSERT INTO invoices VALUES(200.0,1.25)')
con.execute('INSERT INTO invoice_items VALUES(100.0,150.0,200.0,0.1)')
con.execute('INSERT INTO payments VALUES(40.1)')
con.execute('INSERT INTO cash_register VALUES(99.99)')
con.execute('INSERT INTO cash_register_movements VALUES(-40.1,140.09,99.99)')
con.execute('INSERT INTO inventory_items VALUES(100.0,150.0)')
con.execute('INSERT INTO inventory_movements VALUES(100.0)')
# Execute constant SQL literals from add/backfill sections only.
section=src[src.index('private fun addMinorColumns'):src.index('private fun installRangeGuards')]
for sql in re.findall(r'db\.execSQL\("([^"]+)"\)', section):
    con.execute(sql)
checks={
 'invoices':('total_amount_minor',20000),
 'invoice_items':('buy_price_minor',10000),
 'payments':('amount_minor',4010),
 'cash_register':('balance_minor',9999),
 'cash_register_movements':('amount_minor',-4010),
 'inventory_items':('buy_price_minor',10000),
 'inventory_movements':('unit_price_minor',10000),
}
for table,(col,expected) in checks.items():
    got=con.execute(f'SELECT {col} FROM {table}').fetchone()[0]
    if got!=expected:
        print('V244_MIGRATION_SQL_FAIL',table,col,got,expected); sys.exit(1)
print('V244_MIGRATION_SQL_PASS')
