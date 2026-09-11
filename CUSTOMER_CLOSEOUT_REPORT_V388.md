# Verto v388 — Customer Party V2 Closeout Report

Date: 2026-08-30

## Scope

This session closes the three customer failures found by the v387 customer test suite:

1. Customer taxonomy was broader than the approved six groups.
2. Normal customer removal still used permanent delete.
3. CustomerDashboard did not consume CustomerProfile V2.

## Fixes

### 1. Six authoritative customer segments

`CustomerSegment` now contains exactly:

- `INDIVIDUAL`
- `COMPANY` — company/institution
- `WORKSHOP_OWNER`
- `MARKETER`
- `TRADER`
- `DISTRIBUTOR`

Legacy storage values are normalized at migration/read compatibility boundaries. They are not restored as domain enum values.

Legacy mapping:

- `CAR_OWNER`, `OTHER` → `INDIVIDUAL`
- `INSTITUTION` → `COMPANY`
- `MECHANIC` → `WORKSHOP_OWNER`
- `SHOP_OWNER`, `COMPETITOR` → `TRADER`
- `WHOLESALE_TRADER` → `DISTRIBUTOR`

`COMPETITOR` is no longer a customer segment. A competitor is derived from a Party having both active `CUSTOMER` and active `SUPPLIER` roles. Max now uses that definition.

### 2. Role-specific archive instead of normal hard delete

The customer/supplier list no longer calls `deleteClientPermanently`.

Normal removal calls `archiveRole(partyId, role)` and archives only the selected Party role. A dual-role Party therefore keeps its other role and identity/history intact.

The explicit permanent-delete repository method remains guarded for exceptional administrative cleanup; it is not used by normal customer list UX.

### 3. CustomerDashboard reads CustomerProfile V2

`ClientDashboardViewModel` observes `CustomerProfile` through PartyApplicationService. `ClientDashboardScreen` now renders `CustomerProfileCard` with semantic fields appropriate to the segment:

- individual: age, workplace, vehicles
- company/institution: purchasing contact, business activity, vehicles
- workshop owner: workshop name, business activity, worker count
- marketer: business activity
- trader: shop name, business activity
- distributor: business activity

### 4. Room migration 94 → 95

Added `MIGRATION_94_95` and advanced Room schema to 95. The migration normalizes every legacy customer segment into the six allowed values without rebuilding parent tables or disturbing foreign keys.

SQLite migration harness result:

`PARTY_388_MIGRATION_94_95=PASS`

### 5. Supabase v388 migration applied

Applied live migration `v388_customer_closeout` to project `Verto-app`.

Post-migration server verification:

- invalid customer segments: **0**
- active customer without profile: **0**
- customer profile without customer role: **0**
- Party role ↔ client organization mismatch: **0**
- CustomerProfile ↔ client organization mismatch: **0**
- legacy `clients.client_types` column: **0**
- company profiles: **2**
- AutoDrive-eligible profile segments (`MARKETER` + `WORKSHOP_OWNER`): **8**
- active dual-role customer+supplier parties: **3**
- Max RPCs use dual roles and do not use `segment='COMPETITOR'`.

Current server customer profile distribution after normalization:

- `INDIVIDUAL`: 50
- `COMPANY`: 2
- `WORKSHOP_OWNER`: 3
- `MARKETER`: 5
- `TRADER`: 1
- `DISTRIBUTOR`: 0

## Verification

- `scripts/test-customers-v388.py`: **52 PASS / 0 FAIL**
- `scripts/test-customers-v387-room-sql.py`: **PASS**
  - tenant isolation
  - search isolation
  - archived role visibility isolation
- `scripts/verify-party-v388-migration.py`: **PASS**
- `scripts/verify-party-v348-runtime.sh`: **PASS**
- Party domain + legacy role mapper direct `kotlinc`: **PASS**
- Party strings XML parse: **PASS**
- Supabase data integrity checks: **PASS**

### Full Gradle compile

Command attempted:

`./gradlew :app:compileDebugKotlin --offline --build-cache`

Result: **BLOCKED_ENVIRONMENT** before project compilation. Gradle wrapper requires Gradle 8.9, which is not cached locally, and `services.gradle.org` cannot be reached from this execution environment (`UnknownHostException`). This is not recorded as a Kotlin/Android compile failure.

## Remaining compatibility debt outside the three v387 failures

The Party identity compatibility model still contains legacy generic identity columns such as `workplace`, `carType`, `specialty`, and `secondaryPhones` because supplier/older integration compatibility has not yet been fully removed. Customer classification and CustomerProfile V2 no longer depend on those fields as authority. Their final physical removal should be a separate cutover after all remaining supplier/integration consumers are migrated.
