# Verto v247 — Implementation Report

Session 247 is implemented on top of v246.

Implemented:
- latest purchase price for the whole current stock balance, with no weighted average;
- atomic local-purchase quantity + latest-price posting and immutable revaluation event;
- existing sell price is preserved on later purchases;
- immutable sale cost/revenue/profit snapshots captured inside the invoice transaction;
- international purchases remain outside inventory until accepted receiving;
- shipment receiving/landed-cost settlement updates current buy price and records revaluation/landed-cost adjustment events;
- late landed-cost changes reprice only the current remaining balance, never historical sale snapshots;
- Room 64->65 migration;
- Supabase invoice-line snapshot DTO/push/pull contract and additive SQL;
- focused F247 verifier and sale snapshot unit tests.

Verification: F244/F245 migration checks, F246 currency check, F247 costing check and a Kotlin fixed-point harness all pass. Full Gradle compilation could not run because Gradle 8.9 is not cached and the sandbox has no internet access.

See `verification-invoice-F247.md` for details.
