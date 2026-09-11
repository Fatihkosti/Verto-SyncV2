# Verto Sync Observability — v313

| Metric | Source | Durable | Scope | Freshness | Privacy | Correctness authority |
|---|---|---:|---|---|---|---:|
| recoveryState | sync_recovery_state | yes | scope | current local | metadata | yes |
| lastObservedServerRevision | server baseline/high-watermark | yes | scope | last observed | metadata | no |
| lastAppliedRevision | active cursor | yes | scope | local apply | metadata | yes |
| syncLag | observed-applied | no | scope | approximate | metadata | no |
| outbox depth/breakdown | all 7 durable owners | no | tenant | query time | counts | no |
| oldest pending age | outbox createdAt | no | tenant | query time | age only | no |
| retry/conflict/review counts | outboxes/conflict stores | no | tenant | query time | counts | no |
| push/pull success | sync_health_state | yes | scope | last success | metadata | no |
| failure category/code | normalized sync_health_state | yes | scope | last failure | normalized only | no |
| realtimeState | feature/runtime state | no | process | current | metadata | no |
| generations | sync_sequence_state | yes | tenant | current local | counters | yes |
| fullResyncCount | sync_health_state | yes | scope | cumulative | count | no |
| reconciliationStatus | sync_health_state | yes | scope | last run | metadata | no |

Forbidden in persisted diagnostics/logs: access/refresh tokens, Authorization/Bearer values, mutation/financial payload bodies, receipt bodies, OTP/passwords, sensitive attachment URIs, session secrets, and raw server exception bodies.
