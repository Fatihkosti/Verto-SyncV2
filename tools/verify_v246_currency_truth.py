#!/usr/bin/env python3
from pathlib import Path
import sqlite3, sys
ROOT=Path(__file__).resolve().parents[1]

def fail(msg):
    print('V246_CURRENCY_TRUTH_FAIL', msg); sys.exit(1)

required = {
    'data/database/src/main/kotlin/com/verto/app/data/local/entity/InvoicePaymentEntities.kt': [
        'transaction_currency_code','functional_currency_code','invoice_exchange_rate_snapshot',
        'functional_amount_at_recognition_minor','payment_allocations','realized_fx_events',
        'legacy_currency_status',
    ],
    'feature/payment/src/main/kotlin/com/verto/app/feature/payment/application/PaymentCurrencyCalculator.kt': [
        'paymentExchangeRate','functionalCashAmountMinor','historicalFunctionalAmountMinor',
        'realizedFxDifferenceMinor','الدفع بعملة ثالثة غير مدعوم','legacyCurrencyStatus == "UNKNOWN"',
    ],
    'data/network/src/main/kotlin/com/verto/app/data/remote/dto/InvoicePaymentDtos.kt': [
        'transaction_currency_code','functional_cash_amount_minor','PaymentAllocationDto','RealizedFxEventDto',
    ],
    'docs/sql/v246_invoice_currency_truth.sql': ['payment_allocations','realized_fx_events','legacy_currency_status'],
}
for rel, needles in required.items():
    text=(ROOT/rel).read_text(encoding='utf-8')
    for needle in needles:
        if needle not in text: fail(f'{rel}: missing {needle}')

# Acceptance arithmetic is deliberately fixed-point minor units.
def minor(major): return int(round(major * 100))
def convert(amount_minor, rate): return int(round((amount_minor / 100) * rate * 100))
invoice_minor=minor(100)
historical_rate=2500
p1=minor(40); p2=minor(60)
if p1+p2 != invoice_minor: fail('transaction balance does not close')
cash1=convert(p1,2500); cash2=convert(p2,2600)
hist1=convert(p1,historical_rate); hist2=convert(p2,historical_rate)
if (cash1,cash2)!=(10_000_000,15_600_000): fail('cash equivalents')
if (cash1+cash2)//100 != 256_000: fail('functional cash total')
if (abs(cash1-hist1)+abs(cash2-hist2))//100 != 6_000: fail('realized fx')

# Execute the additive Room migration against a minimal v63 financial schema.
con=sqlite3.connect(':memory:')
con.execute('PRAGMA foreign_keys=ON')
con.executescript('''
CREATE TABLE clients(id TEXT PRIMARY KEY);
INSERT INTO clients VALUES('supplier');
CREATE TABLE invoices(
 id TEXT PRIMARY KEY, clientId TEXT NOT NULL, total_amount_minor INTEGER NOT NULL DEFAULT 0,
 purchase_scope TEXT NOT NULL DEFAULT 'LOCAL', FOREIGN KEY(clientId) REFERENCES clients(id));
CREATE TABLE payments(
 id TEXT PRIMARY KEY, invoiceId TEXT NOT NULL, amount_minor INTEGER NOT NULL DEFAULT 0,
 FOREIGN KEY(invoiceId) REFERENCES invoices(id) ON DELETE CASCADE);
INSERT INTO invoices VALUES('legacy-int','supplier',10000,'INTERNATIONAL');
INSERT INTO payments VALUES('legacy-pay','legacy-int',10000);
''')
sqls=[
"ALTER TABLE invoices ADD COLUMN transaction_currency_code TEXT NOT NULL DEFAULT ''",
"ALTER TABLE invoices ADD COLUMN functional_currency_code TEXT NOT NULL DEFAULT ''",
"ALTER TABLE invoices ADD COLUMN transaction_amount_minor INTEGER NOT NULL DEFAULT 0",
"ALTER TABLE invoices ADD COLUMN invoice_exchange_rate_snapshot TEXT NOT NULL DEFAULT ''",
"ALTER TABLE invoices ADD COLUMN exchange_rate_direction TEXT NOT NULL DEFAULT 'FUNCTIONAL_PER_TRANSACTION'",
"ALTER TABLE invoices ADD COLUMN exchange_rate_timestamp INTEGER NOT NULL DEFAULT 0",
"ALTER TABLE invoices ADD COLUMN exchange_rate_source TEXT NOT NULL DEFAULT ''",
"ALTER TABLE invoices ADD COLUMN functional_amount_at_recognition_minor INTEGER NOT NULL DEFAULT 0",
"ALTER TABLE invoices ADD COLUMN legacy_currency_status TEXT NOT NULL DEFAULT 'REVIEW_REQUIRED'",
"UPDATE invoices SET transaction_amount_minor = total_amount_minor",
"UPDATE invoices SET legacy_currency_status = 'UNKNOWN' WHERE purchase_scope = 'INTERNATIONAL'",
"ALTER TABLE payments ADD COLUMN payment_currency_code TEXT NOT NULL DEFAULT ''",
"ALTER TABLE payments ADD COLUMN supplier_amount_minor INTEGER NOT NULL DEFAULT 0",
"ALTER TABLE payments ADD COLUMN payment_exchange_rate TEXT NOT NULL DEFAULT ''",
"ALTER TABLE payments ADD COLUMN payment_exchange_rate_direction TEXT NOT NULL DEFAULT 'FUNCTIONAL_PER_TRANSACTION'",
"ALTER TABLE payments ADD COLUMN payment_exchange_rate_timestamp INTEGER NOT NULL DEFAULT 0",
"ALTER TABLE payments ADD COLUMN payment_exchange_rate_source TEXT NOT NULL DEFAULT ''",
"ALTER TABLE payments ADD COLUMN functional_cash_amount_minor INTEGER NOT NULL DEFAULT 0",
"ALTER TABLE payments ADD COLUMN historical_functional_amount_minor INTEGER NOT NULL DEFAULT 0",
"ALTER TABLE payments ADD COLUMN realized_fx_difference_minor INTEGER NOT NULL DEFAULT 0",
"ALTER TABLE payments ADD COLUMN legacy_currency_status TEXT NOT NULL DEFAULT 'REVIEW_REQUIRED'",
"UPDATE payments SET supplier_amount_minor = amount_minor",
"UPDATE payments SET legacy_currency_status = 'UNKNOWN' WHERE invoiceId IN (SELECT id FROM invoices WHERE legacy_currency_status = 'UNKNOWN')",
]
try:
    for sql in sqls: con.execute(sql)
except Exception as e: fail(f'migration execute: {e}')
row=con.execute("SELECT legacy_currency_status,transaction_currency_code,invoice_exchange_rate_snapshot FROM invoices WHERE id='legacy-int'").fetchone()
if row != ('UNKNOWN','',''): fail(f'legacy international inferred incorrectly: {row}')
pay=con.execute("SELECT legacy_currency_status,supplier_amount_minor FROM payments WHERE id='legacy-pay'").fetchone()
if pay != ('UNKNOWN',10000): fail(f'legacy payment status: {pay}')
print('V246_CURRENCY_TRUTH_PASS')
