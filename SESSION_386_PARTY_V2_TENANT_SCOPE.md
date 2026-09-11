# Verto Session 386 — Party V2 tenant-scoped reads/sync/balances

## Result
PASS_STATIC / PASS_MIGRATION. Android compilation is BLOCKED_ENVIRONMENT because the Gradle 8.9 wrapper is not cached and network access to services.gradle.org is unavailable.

## Implemented
- Customer and supplier profiles are keyed by `(organization_id, party_id)` locally.
- Room schema advanced 92 -> 93 with a data-preserving migration.
- Party directory list/search/detail/paging reads now require the active organization.
- Customer balance, purchase balance, paid totals, overdue checks, and invoice counts are constrained by `organization_id`.
- Global/unscoped client DAO list/count/prefix-search entry points were removed.
- Profile read/delete operations now require `organization_id`.
- Legacy client sync fallback pushes/pulls only the active organization's identities.
- Legacy PARTY_IDENTITY transport is skipped whenever Party V2 is authoritative.
- PARTY_IDENTITY / CUSTOMER_PROFILE / SUPPLIER_PROFILE recovery follows the unified `sync_outbox`; PARTY_ROLE remains on its dedicated `party_sync_outbox` bridge.
- Recovery pruning for customer/supplier profiles is tenant-scoped.
- Backup client collection/clear selection is restricted to the selected organization.
- Shipment supplier-profile lookup is tenant-scoped.
- Optimal company readers now use Party V2 customer roles/profiles instead of legacy `clientType`/`carType`, and all invoice projections are organization-scoped.

## Migration behavior
`MIGRATION_92_93` copies an existing customer/supplier profile once for every matching active Party role organization. Orphaned legacy profiles are retained under an empty organization id so organization-scoped readers cannot leak them and later sync/recovery can repair them.

## Verification
- `scripts/verify-party-v386.sh` -> `PARTY_386_STATIC_GATE=PASS`
- `scripts/verify-party-v386-migration.py` -> `PARTY_386_MIGRATION_92_93=PASS`
- `scripts/verify-party-v385.sh` -> `PARTY_385_STATIC_GATE=PASS`
- `scripts/verify-party-v344.sh` -> `PARTY_344_STATIC_GATE=PASS`
- Gradle compile attempt -> `BLOCKED_ENVIRONMENT` (`UnknownHostException: services.gradle.org`, Gradle 8.9 wrapper absent locally).

## Important remaining work
This session intentionally does not redesign destructive Settings reset semantics or the permanent Party identity deletion contract. Those should be handled separately because Party identity is shared while roles are organization-scoped.
