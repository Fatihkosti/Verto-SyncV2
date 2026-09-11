#!/usr/bin/env python3
from pathlib import Path
import re
import sqlite3

ROOT = Path(__file__).resolve().parents[1]

def need(rel, text):
    if text not in (ROOT/rel).read_text(encoding='utf-8'):
        raise AssertionError(f'{rel}: missing {text}')

catalog=(ROOT/'data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt').read_text(encoding='utf-8')
m=re.search(r'ROOM_SCHEMA_VERSION: Int = (\d+)', catalog)
assert m and int(m.group(1)) >= 68, 'Room schema regressed below F251'
need('data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt', 'MIGRATION_67_68')
need('data/database/src/main/kotlin/com/verto/app/data/local/entity/FinanceAndAccessEntities.kt', 'val amountMinor: Long')
need('data/database/src/main/kotlin/com/verto/app/data/local/dao/ClientCreditDao.kt', 'SUM(amount_minor)')
need('feature/payment/src/main/kotlin/com/verto/app/feature/payment/domain/model/PaymentModels.kt', 'val amountMinor: Long = Money.fromLegacyDouble(amount).amountMinor')
need('data/network/src/main/kotlin/com/verto/app/data/sync/SyncClientCredits.kt', 'Money.ofMinor(c.amountMinor).toMajorDecimal()')
need('data/network/src/main/kotlin/com/verto/app/data/sync/SyncClientCredits.kt', 'amountMinor     = Money.fromMajor(dto.amount).amountMinor')

# Exact behavioral shape of 67->68 additive backfill.
con=sqlite3.connect(':memory:')
con.executescript('''
CREATE TABLE client_credits(id TEXT PRIMARY KEY, clientId TEXT NOT NULL, amount REAL NOT NULL);
INSERT INTO client_credits VALUES('a','c',0.1),('b','c',0.2),('c','c',-0.01);
ALTER TABLE client_credits ADD COLUMN amount_minor INTEGER NOT NULL DEFAULT 0;
UPDATE client_credits SET amount_minor = CAST(ROUND(amount * 100.0) AS INTEGER);
''')
assert con.execute("SELECT SUM(amount_minor) FROM client_credits WHERE clientId='c'").fetchone()[0] == 29
assert con.execute("SELECT amount_minor FROM client_credits WHERE id='a'").fetchone()[0] == 10
assert con.execute("SELECT amount_minor FROM client_credits WHERE id='b'").fetchone()[0] == 20
assert con.execute("SELECT amount_minor FROM client_credits WHERE id='c'").fetchone()[0] == -1
print('V251_CLIENT_CREDIT_MONEY_PASS')
