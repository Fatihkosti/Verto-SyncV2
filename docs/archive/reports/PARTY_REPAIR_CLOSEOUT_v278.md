# PARTY REPAIR CLOSEOUT v278

## Outcome

`CODE_READY_PARTIAL` — local financial/domain/Room/cutover implementation is present; final acceptance remains blocked by unavailable build, server and device gates.

## Original problems

| Problem | Result |
|---|---|
| SALE/PURCHASE mixed for one party | CLOSED in ledger/dashboard/statement paths |
| Payment-derived credit double count | CLOSED in ledger engine |
| Period chronology/opening | CLOSED in engine contract |
| Currency combination | CLOSED in engine; UI exposes balances by currency |
| COMPETITOR treated as third/net ledger | CLOSED in statements and normalized mapping |
| Identity and roles coupled in `clientTypes` | PARTIAL: normalized source active; deprecated compatibility parsers retained |
| Supplier country/currency in generic fields | PARTIAL: Profile is source for new writes; legacy projection retained |
| Supplier `LIKE` classification | CLOSED in active audited queries |
| Destructive party delete | CLOSED: archive by default and hard-delete role guard |
| Versioned/idempotent sync | PARTIAL: contract/outbox/conflicts ready; remote transport rollout blocked |
| Tenant RLS | CODE_READY; SERVER_APPLIED=NO |

## Migrations

- Room: `76→77` implemented, additive; `clients` and legacy columns retained.
- Supabase: `docs/sql/v275_party_v2_expand.sql` created; not applied.
- Counts/quarantine on production: NOT_RUN because no production database was accessed.

## Verification

- `scripts/verify-party-v278.sh`: PASS.
- Ledger/mapper/sync/catalog unit tests: WRITTEN, NOT_RUN.
- Gradle compilation and Room schema export: NOT_RUN (Gradle distribution unavailable; network blocked).
- Android migration/device/process-death tests: NOT_RUN.
- Postgres/RLS/two-organization tests: NOT_RUN.
- Two-device offline/conflict/tombstone tests: NOT_RUN.

## Remaining compatibility

- `clients.clientType`, `carType`, `secondaryPhones` remain compatibility projections.
- Deprecated `ClientType` parsers remain for form serialization and old-device rollout only.
- `verto_upsert_client_v1` remains during mixed-version deployment.

## Acceptance

- CODE_READY: PARTIAL.
- SERVER_APPLIED: NO.
- VERIFIED_ON_TWO_DEVICES: NO.
- RELEASE_READY: NO.
