# Session 373 — Party directory sync repair

## Root cause
The production-default sync path is still Legacy (`FeatureFlags`: V2 off, legacy fallback on). `ClientSyncParticipant` calls `SyncRuntime.pullClients()`, which previously fetched only `clients`. The customer/supplier list is role-driven (`party_roles`), while invoice suggestions read `clients` directly. A fresh/current Room database could therefore contain clients but zero normalized roles, producing exactly the observed state: invoice suggestions work while the directory shows `0`.

## Server verification
Supabase `Verto-app` contains the normalized Party schema (`party_roles`, `customer_profiles`, `supplier_profiles`). Current server evidence observed during this repair: 67 clients, 60 active customers, 9 active suppliers; normalized projection rows are present.

## Changes
- `SyncClients.pullClients()` now hydrates `party_roles`, `customer_profiles`, and `supplier_profiles` in the legacy-authoritative production path.
- Removed the old behavioral hole where an empty incremental `clients` delta returned early and skipped Party projections.
- Client + normalized Party remote application is committed in one Room transaction before advancing the legacy clients pull marker.
- Added explicit DTOs for the server normalized Party schema, including server revision/timestamps and tombstones.
- Remote Party apply preserves pending/dirty local Party changes.
- Customer/supplier paging now excludes `party_roles.deleted_at` tombstones.
- Added synchronous customer-profile DAO read required for conflict-safe remote apply.

## Verification
- `scripts/verify-party-directory-sync-v373.sh`: PASS.
- `scripts/verify-invoice-v372.sh`: PASS (invoice repair retained).
- Gradle compilation could not run in this environment because the Gradle 8.9 distribution is not cached and network access is unavailable; the wrapper attempted to reach `services.gradle.org`.

## Expected runtime result
After the next successful full sync, Room receives normalized roles/profiles and the customer/supplier PagingSource invalidates and repopulates without changing invoice suggestion behavior.
