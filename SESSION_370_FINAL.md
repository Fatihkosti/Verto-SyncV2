# VERTO SESSION 370 FINAL

## Scope
Purchase invoice hardening for local and international purchases.

## Implemented
- International invoice -> Logistics currency boundary now preserves transaction/functional currencies and recognition exchange rate.
- International purchase unit prices are converted to functional/base currency before receiving and Landed Cost.
- International logistics fails closed when currency/rate metadata is invalid or missing.
- International invoice display shows transaction currency, recognition rate and functional value.
- International edit derives scope/currency/rate from the persisted invoice and the editor is scrollable.
- Purchase credit supports contractual installment schedules: explicit first due date, installment amount, recurrence interval, exact final remainder.
- Installment schedule sum must exactly equal invoice outstanding balance.
- Payments advance the effective due date to the next unpaid installment.
- Installments are persisted in Room schema 89 and round-trip through Financial Outbox/Inbox.
- Posted purchase invoice edits are permitted through the existing atomic correction path: reverse old local stock effect, persist corrected invoice, repost inventory/payment effects and audit in one transaction.
- International invoices continue to skip inventory until logistics receiving.

## Verification
- PURCHASE_INVOICE_370_STATIC_GATE=PASS
- ANDROID_STRINGS_XML_PASS
- KOTLINC_POLICY_PASS
- SQLITE_MIGRATION_SHAPE_PASS

## Build status
Full Gradle compile was attempted with offline/build-cache, but Gradle 8.9 is not present in the environment. The wrapper attempted to reach services.gradle.org and failed with UnknownHostException because network access is unavailable. No Kotlin/Android compile failure was produced.

## Note
Room schema version is 89. `AppDatabaseMigrations88To89.kt` is included. The canonical `89.json` export is intentionally left for Room's annotation processor to generate on the first successful Gradle build rather than shipping a hand-generated identity hash.
