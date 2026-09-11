# DAO Decomposition v325

Three stable Room entry points now delegate to cohesive contracts. No Room entity, schema, or accessor changed.

| DAO | Input LOC | Final facade LOC |
|---|---:|---:|
| UnifiedSyncDao | 872 | 23 |
| InvoiceDao | 760 | 66 |
| LogisticsDao | 577 | 20 |

Transaction identities are preserved; Gradle/runtime characterization remains blocked by the environment.
