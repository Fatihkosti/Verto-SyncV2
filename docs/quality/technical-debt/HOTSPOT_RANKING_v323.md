# Technical Debt Hotspot Ranking — v323

Primary hotspot: InventoryDao.kt.

The ranking preserves the v323 contract: complexity, change surface, dependency fan-in/fan-out, business criticality, size, and testability are retained. Inventory persistence was decomposed by cohesive responsibility; Unified Sync, Invoice, and Logistics DAO debt is handed to session 324.

| Rank | Hotspot | Before LOC | Decision |
|---:|---|---:|---|
| 1 | InventoryDao | 1881 | Repaired in 323 |
| 2 | UnifiedSyncDao | 872 | Deferred to 324 |
| 3 | InvoiceDao | 760 | Deferred to 324 |
| 4 | LogisticsDao | 577 | Deferred to 324 |
