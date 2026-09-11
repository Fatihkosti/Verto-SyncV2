# Verto v246 — Implementation Report

## Session

**246 — Currency model, payment allocations, and realized FX**

## Status

**Implemented in source.** The executable F246 fixed-point verification and pure Kotlin business-rule harness both pass. Full Android Gradle compilation could not start because Gradle 8.9 is not cached and the sandbox cannot resolve `services.gradle.org`.

## What changed

1. **Immutable invoice currency truth**
   - Added transaction currency, functional currency, original transaction minor amount, `invoiceExchangeRateSnapshot`, rate direction/timestamp/source, functional amount at recognition, and legacy review status.
   - Functional currency is resolved from organization settings; the invoice Domain does not hardcode SDG.
   - Local invoices normalize transaction currency to functional currency with rate 1.
   - International invoices keep their original transaction amount and historical recognition rate.

2. **Payment currency truth**
   - Payment now persists the supplier/invoice-currency amount separately from the functional cash amount.
   - Actual payment rate, direction, timestamp, source, historical functional counterpart, and realized FX difference are persisted.
   - Third-currency payment is explicitly rejected.

3. **Independent allocation and FX facts**
   - Added `payment_allocations` and `realized_fx_events` Room tables.
   - Allocation IDs and FX-event IDs are deterministic so retries do not duplicate financial effects.
   - Supplier balance remains based on transaction-currency payment amount, not functional cash value.

4. **Cash and reversal correctness**
   - Foreign payment withdraws the actual functional-currency cash equivalent.
   - Payment reversal uses the persisted functional cash amount instead of the foreign nominal amount.

5. **Legacy fail-closed behavior**
   - International legacy invoices without trustworthy currency truth become `UNKNOWN`.
   - Payments linked to those invoices become `UNKNOWN`.
   - Unknown rows are excluded from invoice aggregate payment totals and require review; they are never auto-converted to local currency/rate 1.

6. **Sync and server contract**
   - Invoice/payment DTOs and sync now carry the complete currency snapshot.
   - Allocation and FX-event tables are synchronized independently.
   - Existing local KNOWN facts are protected from blank legacy-server payloads.
   - Added additive server migration: `docs/sql/v246_invoice_currency_truth.sql`.

7. **Presentation and PDF**
   - Invoice/payment screens show the invoice transaction currency and historical rate where applicable.
   - Foreign payment UI requests the actual payment exchange rate.
   - Invoice PDF templates render the persisted invoice currency rather than silently relabeling historical totals with the current organization currency.

## Database migration

- Room schema: **63 -> 64**.
- Additive migration only.
- No dependency/library upgrade.

## Acceptance evidence

- 100 USD @ 2,500 => **100 USD** transaction truth and **250,000** functional recognition value.
- Payments 40 USD @ 2,500 + 60 USD @ 2,600 => supplier balance closes **100 USD**.
- Functional cash = **100,000 + 156,000 = 256,000**.
- Realized FX = **6,000 LOSS**.
- Third-currency payment is rejected.
- Unknown legacy international currency is rejected/fail-closed.
- v244 and v245 migration SQL verifiers still pass.

## Verification executed

- `V246_CURRENCY_TRUTH_PASS`
- `V246_CURRENCY_HARNESS_PASS`
- `V244_MIGRATION_SQL_PASS`
- `V245_MIGRATION_SQL_PASS`

See `verification-invoice-F246.md` for details.

## Static quality note

The project-wide Kotlin quality gate was already failing in v245. Safety-oriented counts did not regress: architecture violations **18 -> 18**, broad catches **20 -> 20**, dependency cycles **0 -> 0**, and not-null assertions **1 -> 1**. F246 adds two production Kotlin files and necessarily enlarges several financial models/coordinators.

## Build environment limitation

Final Android/Room compilation was attempted but the wrapper could not obtain Gradle 8.9:

```text
Downloading https://services.gradle.org/distributions/gradle-8.9-bin.zip
java.net.UnknownHostException: services.gradle.org
```

Therefore this report does **not** claim a Gradle build pass. Room schema 64 should be exported by Room on the first build in an environment with Gradle 8.9 available.

## Deployment note

Apply `docs/sql/v246_invoice_currency_truth.sql` to Supabase before relying on cross-device synchronization of the new allocation/FX facts.
