#!/usr/bin/env python3
from pathlib import Path
import re, sqlite3, sys, tempfile, threading

ROOT = Path(__file__).resolve().parents[1]
errors = []

def read(rel):
    p = ROOT / rel
    if not p.exists():
        errors.append(f"missing:{rel}")
        return ""
    return p.read_text(encoding="utf-8")

def require(rel, needle, label):
    if needle not in read(rel): errors.append(f"{label}:{rel}")

# Production contract checks.
coordinator = read('feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/InvoiceWriteCoordinator.kt')
for needle, label in [
    ('transactionPort.inTransaction', 'single-room-owner'),
    ('store.claimWrite(', 'durable-write-claim'),
    ('persistIntegrationArtifacts(', 'outbox-in-transaction'),
    ('auditLogger.logInsert(', 'audit-in-transaction'),
    ('auditInventoryEffects(', 'inventory-audit-in-transaction'),
    ('writeId = command.writeId', 'write-id-propagation'),
]:
    if needle not in coordinator: errors.append(label)
post = read('feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/InvoicePostCommitEffects.kt')
if 'auditLogger' in post: errors.append('audit-still-post-commit')

invdao = read('data/database/src/main/kotlin/com/verto/app/data/local/dao/InventoryDao.kt')
for needle, label in [
    ('quantity = quantity - :quantity', 'stock-sql-arithmetic'),
    ('quantity >= :quantity', 'stock-conditional-guard'),
    ('val affected = deductQuantityConditional', 'stock-row-count-read'),
    ('if (affected != 1)', 'stock-row-count-enforced'),
    ('From this point onward failures MUST escape', 'post-mutation-errors-escape'),
]:
    if needle not in invdao: errors.append(label)
if 'deductStockAtomic(' in invdao and '): Result<Unit> = runCatching {' in invdao[invdao.index('deductStockAtomic('):invdao.index('addStockAtomic(')]:
    errors.append('stock-transaction-catches-post-mutation-failure')


invoice_dao = read('data/database/src/main/kotlin/com/verto/app/data/local/dao/InvoiceDao.kt')
if '@Insert(onConflict = OnConflictStrategy.ABORT)' not in invoice_dao: errors.append('local-invoice-insert-not-abort')
if 'invoice update affected $affected rows; expected exactly one' not in invoice_dao: errors.append('invoice-update-row-count-not-enforced')
repo = read('data/operations/src/main/kotlin/com/verto/app/data/repository/InvoiceRepository.kt')
if 'invoice payment insert was ignored unexpectedly' not in repo: errors.append('payment-insert-row-count-not-enforced')
cash_dao = read('data/database/src/main/kotlin/com/verto/app/data/local/dao/CashRegisterDao.kt')
if 'cash register update affected $affected rows; expected exactly one' not in cash_dao: errors.append('cash-update-row-count-not-enforced')

writer = read('feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/InvoiceInventoryWriter.kt')
if 'return@forEach' in writer: errors.append('silent-return-for-each-remains')
if '.getOrThrow()' not in writer: errors.append('stock-result-not-propagated')
if 'context.byName' not in writer or 'يجب اختياره صراحةً' not in writer: errors.append('explicit-inventory-link-contract-missing')
if 'InvoiceInventoryRevaluation' not in writer: errors.append('revaluation-event-missing')

model = read('data/database/src/main/kotlin/com/verto/app/data/local/entity/InvoicePaymentEntities.kt')
for needle, label in [
    ('tableName = "invoice_write_guard"', 'write-guard-table'),
    ('value = ["organization_id", "operation_type", "write_id"]', 'write-guard-unique-key'),
    ('unique = true', 'write-guard-unique'),
    ('supplier_invoice_ref_normalized', 'supplier-ref-normalized'),
    ('index_invoices_org_supplier_external_ref', 'supplier-ref-unique-index'),
]:
    if needle not in model: errors.append(label)

for rel in [
    'data/database/src/main/kotlin/com/verto/app/data/local/entity/InvoicePaymentEntities.kt',
    'data/database/src/main/kotlin/com/verto/app/data/local/entity/InventoryEntity.kt',
    'data/database/src/main/kotlin/com/verto/app/data/local/entity/AuditAndNotesEntities.kt',
]:
    t = read(rel)
    for f in ('sourceType', 'sourceId', 'sourceVersion', 'writeId'):
        if f not in t: errors.append(f'derived-metadata:{rel}:{f}')

vm = read('feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/InvoiceEditorViewModel.kt')
if 'activeInvoiceWriteId' not in vm or 'writeId = writeId' not in vm: errors.append('stable-ui-write-id-missing')
if 'invoiceSaveInFlight' not in vm or 'return@launch' not in vm: errors.append('double-tap-inflight-guard-missing')

schema_match = re.search(r'ROOM_SCHEMA_VERSION: Int = (\d+)', read('data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt'))
if not schema_match or int(schema_match.group(1)) < 63: errors.append('schema-63')
require('data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt', 'MIGRATION_62_63', 'migration-catalog')
require('feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/InvoiceWriteCoordinator.kt', 'بيع بمخزون سالب وفق السياسة المفعلة', 'negative-stock-audit')

# SQLite behavioral harness for the exact transaction/idempotency patterns used by F245.
def schema(con):
    con.executescript('''
    PRAGMA foreign_keys=ON;
    CREATE TABLE inventory(id TEXT PRIMARY KEY, qty INTEGER NOT NULL);
    CREATE TABLE invoice(id TEXT PRIMARY KEY, write_id TEXT NOT NULL);
    CREATE TABLE payment(id INTEGER PRIMARY KEY AUTOINCREMENT, invoice_id TEXT NOT NULL);
    CREATE TABLE cash(id INTEGER PRIMARY KEY AUTOINCREMENT, invoice_id TEXT NOT NULL);
    CREATE TABLE outbox(id INTEGER PRIMARY KEY AUTOINCREMENT, invoice_id TEXT NOT NULL);
    CREATE TABLE audit(id INTEGER PRIMARY KEY AUTOINCREMENT, invoice_id TEXT NOT NULL, note TEXT NOT NULL);
    CREATE TABLE write_guard(org TEXT NOT NULL, operation TEXT NOT NULL, write_id TEXT NOT NULL, invoice_id TEXT NOT NULL,
      UNIQUE(org,operation,write_id));
    ''')

def save(con, write_id, deductions, allow_negative=False, fail_outbox=False):
    invoice_id = f'i-{write_id}'
    try:
        con.execute('BEGIN IMMEDIATE')
        cur = con.execute('INSERT OR IGNORE INTO write_guard VALUES(?,?,?,?)', ('org','CREATE',write_id,invoice_id))
        if cur.rowcount != 1:
            con.commit(); return 'duplicate'
        con.execute('INSERT INTO invoice VALUES(?,?)', (invoice_id, write_id))
        con.execute('INSERT INTO payment(invoice_id) VALUES(?)', (invoice_id,))
        con.execute('INSERT INTO cash(invoice_id) VALUES(?)', (invoice_id,))
        negative = False
        for item_id, qty in deductions:
            cur = con.execute('UPDATE inventory SET qty=qty-? WHERE id=? AND (?=1 OR qty>=?)', (qty,item_id,1 if allow_negative else 0,qty))
            if cur.rowcount != 1: raise RuntimeError('stock')
            negative |= con.execute('SELECT qty FROM inventory WHERE id=?',(item_id,)).fetchone()[0] < 0
        if fail_outbox: raise RuntimeError('outbox')
        con.execute('INSERT INTO outbox(invoice_id) VALUES(?)',(invoice_id,))
        con.execute('INSERT INTO audit(invoice_id,note) VALUES(?,?)',(invoice_id,'negative-policy' if negative else 'invoice'))
        con.commit(); return 'ok'
    except Exception:
        con.rollback(); return 'failed'

# 1: stock 3 / sale 5 / negative forbidden -> nothing persists.
c = sqlite3.connect(':memory:', isolation_level=None); schema(c); c.execute("INSERT INTO inventory VALUES('a',3)")
if save(c,'w1',[('a',5)]) != 'failed': errors.append('case1-not-failed')
for table in ('invoice','payment','cash','outbox','audit','write_guard'):
    if c.execute(f'SELECT COUNT(*) FROM {table}').fetchone()[0] != 0: errors.append(f'case1-partial:{table}')
if c.execute("SELECT qty FROM inventory WHERE id='a'").fetchone()[0] != 3: errors.append('case1-stock-changed')

# 2: second line failure rolls first line back.
c = sqlite3.connect(':memory:', isolation_level=None); schema(c); c.executemany('INSERT INTO inventory VALUES(?,?)',[('a',3),('b',0)])
if save(c,'w2',[('a',1),('b',1)]) != 'failed': errors.append('case2-not-failed')
if dict(c.execute('SELECT id,qty FROM inventory')) != {'a':3,'b':0}: errors.append('case2-first-line-not-rolled-back')

# 3: two connections compete for the last item; exactly one wins.
with tempfile.NamedTemporaryFile(suffix='.db') as f:
    base=sqlite3.connect(f.name, isolation_level=None); schema(base); base.execute("INSERT INTO inventory VALUES('a',1)"); base.close()
    barrier=threading.Barrier(2); outcomes=[]; lock=threading.Lock()
    def worker(w):
        con=sqlite3.connect(f.name, timeout=3, isolation_level=None)
        barrier.wait(); result=save(con,w,[('a',1)]); con.close()
        with lock: outcomes.append(result)
    ts=[threading.Thread(target=worker,args=(f'c{i}',)) for i in range(2)]
    [t.start() for t in ts]; [t.join() for t in ts]
    check=sqlite3.connect(f.name)
    if sorted(outcomes) != ['failed','ok'] or check.execute("SELECT qty FROM inventory WHERE id='a'").fetchone()[0] != 0:
        errors.append(f'case3-concurrency:{outcomes}')
    check.close()

# 4: same write id ten times => one financial effect.
c=sqlite3.connect(':memory:', isolation_level=None); schema(c); c.execute("INSERT INTO inventory VALUES('a',20)")
res=[save(c,'same',[('a',1)]) for _ in range(10)]
if res.count('ok') != 1 or c.execute('SELECT COUNT(*) FROM cash').fetchone()[0] != 1 or c.execute("SELECT qty FROM inventory WHERE id='a'").fetchone()[0] != 19:
    errors.append('case4-idempotency')

# 5: outbox failure rolls all prior effects back.
c=sqlite3.connect(':memory:', isolation_level=None); schema(c); c.execute("INSERT INTO inventory VALUES('a',3)")
if save(c,'w5',[('a',1)],fail_outbox=True) != 'failed': errors.append('case5-not-failed')
for table in ('invoice','payment','cash','outbox','audit','write_guard'):
    if c.execute(f'SELECT COUNT(*) FROM {table}').fetchone()[0] != 0: errors.append(f'case5-partial:{table}')
if c.execute("SELECT qty FROM inventory WHERE id='a'").fetchone()[0] != 3: errors.append('case5-stock-changed')

# 6: negative stock only when enabled and audited.
c=sqlite3.connect(':memory:', isolation_level=None); schema(c); c.execute("INSERT INTO inventory VALUES('a',3)")
if save(c,'w6',[('a',5)],allow_negative=True) != 'ok': errors.append('case6-negative-not-allowed')
if c.execute("SELECT qty FROM inventory WHERE id='a'").fetchone()[0] != -2: errors.append('case6-wrong-stock')
if c.execute("SELECT COUNT(*) FROM audit WHERE note='negative-policy'").fetchone()[0] != 1: errors.append('case6-no-audit')

if errors:
    print('V245_ATOMIC_INVARIANTS_FAIL')
    for e in errors: print(' -',e)
    sys.exit(1)
print('V245_ATOMIC_INVARIANTS_PASS')
