# VERTO — Invoice F246 Verification

## Result

**F246 source/behavior verification: PASS.** Full Android Gradle compilation is **BLOCKED by environment**, not reported as passing.

## Verified invariants

- Invoice persistence contains `transactionCurrencyCode`, `functionalCurrencyCode`, `transactionAmountMinor`, `invoiceExchangeRateSnapshot`, direction, timestamp, source, functional recognition amount, and legacy status.
- Local invoice currency is normalized from organization functional currency with rate 1.
- International invoice amount remains in transaction currency; functional recognition value is a separate fixed-point fact.
- Payment persists supplier amount and functional cash amount separately.
- Actual payment rate is fixed to the direction `FUNCTIONAL_PER_TRANSACTION` and carries timestamp/source metadata.
- Third-currency payment is rejected.
- `PaymentAllocation` is a separate persisted fact.
- Realized FX is a separate event and is not folded into supplier settlement.
- Retry identities for allocation and FX event are deterministic.
- Foreign payment reversal uses persisted functional cash truth.
- Old international rows with untrusted/missing currency facts become `UNKNOWN`, not local/rate 1.
- Unknown currency rows are excluded from invoice payment aggregates.
- Invoice/payment sync carries the new facts; blank old-server currency payloads cannot erase local KNOWN payment truth.
- All four invoice PDF variants use invoice transaction currency snapshots.

## Acceptance cases

| Case | Result |
|---|---|
| 100 USD @ 2,500 stores 100 USD + 250,000 functional | PASS |
| Pay 40 @ 2,500 then 60 @ 2,600 closes 100 USD | PASS |
| Functional cash is 100,000 + 156,000 | PASS |
| Realized FX is 6,000 LOSS | PASS |
| Third currency is rejected | PASS |
| Unknown legacy international row is not auto-local | PASS |
| v244 migration SQL remains valid | PASS |
| v245 migration SQL remains valid | PASS |

## Executed verifiers

```text
python3 tools/verify_v246_currency_truth.py
V246_CURRENCY_TRUTH_PASS
```

A pure Kotlin harness compiled the real `Money`, `PaymentModels`, and `PaymentCurrencyCalculator` source with `kotlinc`, then executed the core F246 arithmetic/rejection cases:

```text
V246_CURRENCY_HARNESS_PASS
```

Previous migration guards:

```text
V244_MIGRATION_SQL_PASS
V245_MIGRATION_SQL_PASS
```

## Migration

- Room: **63 -> 64**.
- New columns are additive.
- New tables: `payment_allocations`, `realized_fx_events`.
- The migration marks untrusted legacy international currency facts `UNKNOWN` without inventing currency or rate.
- Server-side additive SQL is included in `docs/sql/v246_invoice_currency_truth.sql` with tenant RLS for the new tables.

## Static quality comparison

| Metric | v245 | v246 |
|---|---:|---:|
| architecture violations | 18 | 18 |
| broad catches | 20 | 20 |
| dependency cycles | 0 | 0 |
| not-null assertions | 1 | 1 |
| excessive parameter lists | 481 | 493 |
| large files >500 | 14 | 16 |
| long functions | 343 | 348 |
| production Kotlin files | 1076 | 1078 |

The global gate was already failing before F246. F246 did not add architecture violations, broad catches, cycles, or not-null assertions. Complexity counts increased because the session adds persisted immutable currency metadata across Room/Domain/DTO/sync boundaries.

## Gradle limitation

Attempted final compilation:

```text
./gradlew :feature:payment:compileDebugKotlin :feature:invoice:compileDebugKotlin :data:database:compileDebugKotlin :data:network:compileDebugKotlin :app:compileDebugKotlin --offline --no-daemon --stacktrace
```

The wrapper attempted to download Gradle 8.9 and failed before project compilation:

```text
java.net.UnknownHostException: services.gradle.org
```

No Gradle/Android test success is claimed.
