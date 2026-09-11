# Verto v266 — Read Models and Operations

Status: **IMPLEMENTED / STATIC PASS**

- Added a canonical ledger-derived inventory read model and drift detector.
- Added operational metrics plus fast-mover and slow-stock queries.
- Fixed sales analytics to filter by `movement_kind = SALE` and use occurrence time.
- Added indexes and query-plan verification to the release gate.

Validation: SQLite reference/query-plan checks passed. Android-device performance benchmarking remains pending.
