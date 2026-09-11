# Invoice Repair Allowlist — Sessions 244–251

This is the modification boundary established by session 243. A later session may use only the paths relevant to its stated scope. Any file outside these paths requires an explicit evidence note in that session's verification report.

## Core invoice/payment domain and application

- `feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/**`
- `feature/invoice/src/test/**`
- `feature/payment/src/main/kotlin/com/verto/app/feature/payment/**`
- `feature/payment/src/test/**`

## Inventory integration required by invoice rules

- `feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/domain/**`
- `feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/application/**`
- `feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/data/**`
- `feature/inventory/src/test/**`

Presentation under inventory is not automatically allowed except where a session explicitly changes validation/price entry UX.

## Database / migrations / DAOs / entities

- `data/database/src/main/kotlin/com/verto/app/data/local/AppDatabase.kt`
- `data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt`
- `data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations*.kt`
- `data/database/src/main/kotlin/com/verto/app/data/local/Converters.kt`
- `data/database/src/main/kotlin/com/verto/app/data/local/entity/InvoicePaymentEntities.kt`
- `data/database/src/main/kotlin/com/verto/app/data/local/entity/InventoryEntity.kt`
- `data/database/src/main/kotlin/com/verto/app/data/local/entity/FinanceAndAccessEntities.kt`
- `data/database/src/main/kotlin/com/verto/app/data/local/dao/InvoiceDao.kt`
- `data/database/src/main/kotlin/com/verto/app/data/local/dao/PaymentDao.kt`
- `data/database/src/main/kotlin/com/verto/app/data/local/dao/InventoryDao.kt`
- `data/database/src/main/kotlin/com/verto/app/data/local/dao/CashRegisterDao.kt`
- `data/database/src/main/kotlin/com/verto/app/data/local/dao/AuditLogDao.kt`
- `data/database/src/androidTest/**`
- `data/database/src/test/**`
- exported Room schemas under the project's configured schema directory

## Data operations / transaction ownership / repositories

- `data/operations/src/main/kotlin/com/verto/app/data/operations/transaction/**`
- `data/operations/src/main/kotlin/com/verto/app/data/operations/invoice/**`
- `data/operations/src/main/kotlin/com/verto/app/data/operations/payment/**`
- `data/operations/src/main/kotlin/com/verto/app/data/repository/InvoiceRepository.kt`
- `data/operations/src/main/kotlin/com/verto/app/utils/AuditLogger.kt`

## Network and sync contracts

- `data/network/src/main/kotlin/com/verto/app/data/remote/FinancialPostingRemote.kt`
- `data/network/src/main/kotlin/com/verto/app/data/remote/dto/InvoicePaymentDtos.kt`
- `data/network/src/main/kotlin/com/verto/app/data/sync/SyncInvoiceHeaders.kt`
- `data/network/src/main/kotlin/com/verto/app/data/sync/SyncInvoiceLines.kt`
- `data/network/src/main/kotlin/com/verto/app/data/sync/SyncInvoicePayments.kt`
- new invoice-specific sync/outbox/inbox files under `data/network` / `data/sync` when required
- `data/sync/src/main/kotlin/com/verto/app/data/sync/**` only where invoice operation ordering/retry contracts require it

## App bridges and financial shared utilities

- `app/src/main/kotlin/com/verto/app/feature/invoice/bridge/**`
- `app/src/main/kotlin/com/verto/app/feature/payment/bridge/**`
- `app/src/main/kotlin/com/verto/app/pdf/Invoice*.kt`
- `core/common/src/main/kotlin/com/verto/app/utils/MoneyMath.kt` or its replacement Money Core files
- DI modules only when bindings must change for these contracts

## International receiving / landed cost overlap

Changes here are allowed only for session 247/250/251 compatibility and must preserve v239–v242 logistics behavior:

- `app/src/main/kotlin/com/verto/app/feature/shipment/bridge/LogisticsV2AppAdapters.kt`
- `app/src/main/kotlin/com/verto/app/feature/shipment/bridge/LogisticsReceivingAdapters.kt`
- `app/src/main/kotlin/com/verto/app/feature/shipment/bridge/LogisticsV232SettlementAdapters.kt`
- related focused shipment tests only

Do not redesign logistics screens/routes in invoice sessions.

## Reports / reconciliation

Allowed in 250–251 only:
- `feature/reports/src/main/kotlin/**`
- invoice/report bridges under `app/src/main/kotlin/com/verto/app/feature/reports/**`
- report tests

## Server contract artifacts

Allowed when the repository owns the required change:
- invoice/payment SQL migration or verification files under `docs/sql/**` or `sql/**`
- contract documentation under `docs/**`

If authoritative Supabase invoice/payment DDL is absent, the session must provide additive SQL rather than claiming an unseen constraint already exists.

## Explicitly out of scope by default

- shipment planning/execution UI redesign
- dashboard unrelated behavior
- auth
- organization UX
- messages
- unrelated settings
- design system changes unrelated to invoice UI in 254
- dependency upgrades unrelated to the financial repair
