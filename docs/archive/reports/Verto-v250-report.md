# Verto v250 — Implementation Report

Session 250 is implemented on top of v249.

Implemented:
- fixed-point functional-currency report aggregation from historical recognition snapshots;
- historical realized profit from `unitCostAtSale`/line cost snapshots, separated from current replacement margin;
- international supplier statement with original/paid/remaining transaction currency, functional cash, realized FX and historical rate;
- invoice/payment/cash/inventory/Outbox reconciliation diagnostics and legacy data-quality checks;
- operational inventory valuation using current quantity × latest purchase price;
- fixed-point RFM monetary/profit cache calculation with mixed-functional-currency fail-closed behavior;
- currency-explicit PDF/export/share paths;
- focused F250 tests and reproducible verifier;
- Room schema remains 67; query-plan audit did not justify a new index/migration.

Verification: F246, F247, F249 and F250 verifiers pass. Targeted Kotlin fixed-point margin execution and RFM compilation pass.

Gradle Android/Room tasks could not begin because Gradle 8.9 is not cached and the sandbox has no network access.

See `verification-invoice-F250.md` for details.
