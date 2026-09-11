#!/usr/bin/env python3
from pathlib import Path
import re, sys
ROOT = Path(__file__).resolve().parents[1]
errors=[]

def text(rel):
    p=ROOT/rel
    if not p.exists(): errors.append(f'missing:{rel}'); return ''
    return p.read_text(encoding='utf-8')

def has(rel, needle, label):
    if needle not in text(rel): errors.append(f'{label}:{rel}')

# Core fixed-point types and parser.
has('core/common/src/main/kotlin/com/verto/app/money/Money.kt','val amountMinor: Long','money-minor')
has('core/common/src/main/kotlin/com/verto/app/money/Money.kt','data class ExchangeRate','exchange-rate')
has('core/common/src/main/kotlin/com/verto/app/money/Money.kt','data class Quantity','quantity')
has('core/common/src/main/kotlin/com/verto/app/money/Money.kt','arabicDigits','arabic-parser')
has('core/common/src/main/kotlin/com/verto/app/money/Money.kt','Math.multiplyExact','overflow-guard')

validator=text('feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/InvoiceSaveValidator.kt')
for forbidden in ('toIntOrNull() ?: 1','toDoubleOrNull() ?: 0.0'):
    if forbidden in validator: errors.append('validator-fallback:'+forbidden)
if 'val activePrice = if (command.isSale) sellPrice else buyPrice' not in validator:
    errors.append('purchase-total-not-buy-price')
if 'Money' not in validator or 'Quantity' not in validator:
    errors.append('validator-not-fixed-point')

invwriter=text('feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/InvoiceInventoryWriter.kt')
for forbidden in ('toIntOrNull() ?: 1','toDoubleOrNull() ?: 0.0'):
    if forbidden in invwriter: errors.append('inventory-fallback:'+forbidden)
if 'buyPriceMinor = validatedItem.buyPrice.amountMinor' not in invwriter:
    errors.append('inventory-buy-minor-not-updated')

screen=text('feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/InvoiceEditorScreen.kt')
if 'Money.parseOrNull(if (isSale) item.sellPrice else item.buyPrice)' not in screen:
    errors.append('ui-domain-price-rule-diverged')

composer=text('feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/InvoiceEditorItemComposer.kt')
if 'placeholder = "سعر البيع (اختياري)"' not in composer:
    errors.append('optional-sell-price-ui-missing')
if 'value = if (isSale) item.sellPrice else item.buyPrice' not in composer:
    errors.append('purchase-primary-buy-price-ui-missing')

# Persistence schema and migration.
schema_text = text('data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt')
schema_match = re.search(r'ROOM_SCHEMA_VERSION: Int = (\d+)', schema_text)
if not schema_match or int(schema_match.group(1)) < 62: errors.append('schema-version')
has('data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt','MIGRATION_61_62','migration-catalog')
mig=text('data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations61To62.kt')
for col in ['total_amount_minor','buy_price_minor','sell_price_minor','total_price_minor','amount_minor','balance_minor','unit_price_minor']:
    if col not in mig: errors.append('migration-column:'+col)
for rel in [
 'data/database/src/main/kotlin/com/verto/app/data/local/entity/InvoicePaymentEntities.kt',
 'data/database/src/main/kotlin/com/verto/app/data/local/entity/InventoryEntity.kt',
]:
    if 'Money.fromLegacyDouble' not in text(rel): errors.append('entity-minor-bridge:'+rel)

cash=text('data/database/src/main/kotlin/com/verto/app/data/local/dao/CashRegisterDao.kt')
if 'val before = Money.ofMinor(current.balanceMinor)' not in cash or 'balanceAfterMinor = after.amountMinor' not in cash:
    errors.append('cash-still-double-source')

cmd=text('feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/domain/model/InvoiceWriteModels.kt')
for sig in ['val initialPayment: Money','val commission: Money','val exchangeRate: ExchangeRate']:
    if sig not in cmd: errors.append('domain-command-fixed-point:'+sig)

# Acceptance tests are present.
tests=text('feature/invoice/src/test/kotlin/com/verto/app/feature/invoice/application/InvoiceMoneyCoreTest.kt')
for phrase in ['local purchase supplier total uses buy price only','invalid quantity never falls back to one','blank buy price is rejected','zero buy price is rejected','decimal multiplication is exact']:
    if phrase not in tests: errors.append('acceptance-test:'+phrase)
coretests=text('core/common/src/test/kotlin/com/verto/app/money/MoneyCoreTest.kt')
if 'arabic and english numerals normalize to same money' not in coretests: errors.append('arabic-parser-test')

if errors:
    print('V244_MONEY_CORE_FAIL')
    for e in errors: print(' -',e)
    sys.exit(1)
print('V244_MONEY_CORE_PASS')
