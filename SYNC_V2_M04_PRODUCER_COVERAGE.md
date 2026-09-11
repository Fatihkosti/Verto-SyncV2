# Sync V2 M04 Producer Coverage

Date: 2026-09-09

## Scope

Current registry: **35 aggregates**. M04 converts producer capture only; transport completion remains M05.

## Authorities

- Generic `sync_outbox`: Party identity/profiles, notes/reminders, purchase-cycle commands, inventory metadata, budgets, expenses/cash commands, price lists, organization settings, shipments/attachments, educational content, team observations, and supported invoice metadata commands.
- Stronger durable authorities retained: `party_sync_outbox`, `financial_outbox`, `inventory_stock_outbox`, `inventory_cost_outbox`, `optimal_outbox`. They are bridged, not copied into a competing generic outbox.
- Server-owned/no-client-push: notification/read-model authorities, cash register authority, commission payment authority where defined by registry/bridge.

## M04 findings and corrections

1. `UnifiedOutboxWriter` accepted only `Map<String,String?>`, serializing numbers/booleans as JSON strings. Fixed to typed canonical JSON.
2. `RoomAdvanceCreditAdapter` inserted `CLIENT_CREDIT` before enqueueing outside the Room transaction. Fixed: row + intent are one transaction.
3. `RoomTeamObservationRepository` skipped unified intent creation while Legacy owned transport. Fixed: every local producer action captures V2; the dirty bit remains only a temporary transport-compatibility mirror.
4. Scalar payloads across current generic producers were normalized to native Boolean/Number/null types. Contract-encoded strings (for example delimited line/item collections) remain strings intentionally.
5. `UnifiedOutboxWriter` now rejects enqueue/attachment capture outside an active Room transaction (`M04_PRODUCER_TRANSACTION_REQUIRED`).

## Verification

- M04 deterministic static gate: **16/16 PASS**.
- Registry: **35/35 unique**.
- Stronger bridge registry: **17/17 retained**.
- Known generic producer files with transaction boundary: **19/19**.
- Native scalar stringification regression scan: **PASS**.
- Added 3 canonical-payload unit tests; execution is **BLOCKED** because Gradle 8.9 cannot be downloaded in this offline environment.
- Repository-wide Kotlin quality ratchet is already failing in the baseline with identical counts; M04 did not increase those counts.

## Safety state

- Global V2 enable: **NO**.
- Legacy fallback deletion: **NO**.
- `legacyWritesFenced`: **false**, intentionally deferred until M05 transport replacement.
