# Persistence Transaction Matrix v326

All critical workflows have one explicit local transaction owner. No network, auth refresh, storage upload, or unbounded retry is introduced inside Room transactions. Runtime failure injection remains dependent on Gradle/device availability.

| Workflow | Owner | Transaction owner | Idempotency |
|---|---|---|---|
| invoice-posting | invoice | Invoice posting coordinator | IDEMPOTENT_BY_REQUEST_KEY |
| inventory-movement | inventory | Inventory stock transaction coordinator | IDEMPOTENT_BY_REQUEST_KEY |
| payment-posting | payment | Payment transaction coordinator | IDEMPOTENT_BY_REQUEST_KEY |
| logistics-state-transition | shipment | Outer AppDatabase.withTransaction | IDEMPOTENT_BY_REQUEST_KEY |
| logistics-receiving | shipment | Outer AppDatabase.withTransaction | IDEMPOTENT_BY_REQUEST_KEY |
| logistics-cost-posting | shipment | Outer AppDatabase.withTransaction | IDEMPOTENT_BY_REQUEST_KEY |
| sync-local-write | sync | AppDatabase.withTransaction | IDEMPOTENT_BY_IDENTITY |
| sync-outbox | sync | Room @Transaction | IDEMPOTENT_BY_IDENTITY |
| sync-inbox | sync | AppDatabase.withTransaction | IDEMPOTENT_BY_IDENTITY |
| sync-cursor | sync | AppDatabase.withTransaction | IDEMPOTENT_BY_IDENTITY |
| sync-conflict | sync | Room @Transaction | IDEMPOTENT_BY_IDENTITY |
| invoice-update-with-items | invoice | Room @Transaction | IDEMPOTENT_BY_IDENTITY |
| inventory-receiving | inventory | Room @Transaction/outer transaction | IDEMPOTENT_BY_REQUEST_KEY |
| payment-allocation | payment | Payment transaction coordinator | IDEMPOTENT_BY_REQUEST_KEY |
