# Verto v387 — Customer Test Report

Date: 2026-08-30
Scope: customer Party V2 cutover, tenant isolation, balances, migrations, sync, backup/restore, and external customer consumers (Optimal / AutoDrive / Max server contracts).

## Executive result

**Customer acceptance gate: FAIL (3 functional gaps).**

The Party V2 foundation itself passed: semantic customer profiles, organization-scoped customer reads/balances, Room migrations, Party V2 sync contract, backup v6, server RLS/data integrity, and removal of runtime `clientType/clientTypes` authority.

The failures are end-state product gaps, not Party V2 data corruption:
1. Customer taxonomy still exposes legacy segments instead of the approved six groups.
2. Customer-list delete UI still invokes permanent delete; Party V2 normal customers always have roles, so repository hard-delete rejects them.
3. ClientDashboard does not consume/display CustomerProfile V2 fields.

## Executed tests

### 1) Customer source/contract gate
Command: `python3 scripts/test-customers-v387.py`

Result: **43 PASS / 3 FAIL**.

Passed coverage includes:
- Explicit `CustomerProfile` fields: age, purchase contact, business activity, workplace, shop, workshop, vehicles, worker count.
- No opaque `vehicleInformation` in the domain profile.
- Room customer-profile key `(organization_id, party_id)`.
- `clientType` retained only as an inert Android Room compatibility tombstone.
- Customer add/edit clears overloaded identity fields and writes CustomerProfile V2.
- No runtime `.clientType/.clientTypes` classification authority.
- All customer-facing list/search/balance/invoice-count DAO methods require `organizationId`.
- Financial customer queries constrain invoices to `organizationId`.
- Repository resolves trusted organization scope.
- Room migrations 92→93 and 93→94 execute successfully.
- Sync uses `verto_upsert_party_v2` and does not send `client_types`.
- Optimal local company read uses Party V2 customer profiles.
- Backup v6 contains party roles + customer profiles + supplier profiles and preserves legacy checksum compatibility.
- Old `ClientScreen` is absent.

Failed acceptance assertions:
- Approved six customer groups only — **FAIL**. Current enum: `INDIVIDUAL, COMPANY, INSTITUTION, CAR_OWNER, MECHANIC, SHOP_OWNER, WORKSHOP_OWNER, MARKETER, TRADER, DISTRIBUTOR, WHOLESALE_TRADER, COMPETITOR, OTHER`.
- Customer list delete action archives instead of permanent delete — **FAIL**. `ClientsListScreen` calls `deleteClientPermanently`.
- Customer dashboard consumes CustomerProfile V2 — **FAIL**. Dashboard does not observe/display the new profile.

### 2) Exact Room SQL behavior harness
Command: `python3 scripts/test-customers-v387-room-sql.py`

Result: **3 PASS / 0 FAIL**.

The harness executes the exact SQL text extracted from `ClientDao` and verifies:
- `getAllClientsWithBalance` isolates organization, profile, invoices, and payments.
- `searchClientsWithBalance` isolates search results by organization.
- Archiving CUSTOMER role in org A hides the customer only from org A while preserving org B visibility.

### 3) Pure Kotlin runtime harness
Compiled with local `kotlinc` and executed without Gradle.

Result: **11 PASS / 0 FAIL**.

Verified:
- Legacy competitor compatibility maps to CUSTOMER + SUPPLIER.
- Unknown legacy classifications are not guessed.
- Sale debt/payment balance math.
- Other-customer financial records are ignored.
- Purchase payable offsets party net balance.
- CustomerProfile explicit semantic fields preserve values.

### 4) Existing v387 Party gate
Command: `bash scripts/verify-party-v387.sh`

Result: **PASS**.

Included successful Room 92→93 and 93→94 migration simulations and all Party v387 static checks.

### 5) Live Supabase customer integrity — read only
Project: Verto-app.

**PASS**:
- `clients.client_types` column count = 0.
- Orphan Party roles = 0.
- Orphan customer profiles = 0.
- Active customer without profile = 0.
- Customer profile without customer role = 0.
- Invalid customer role statuses = 0.
- Invalid ages = 0.
- Invalid workshop worker counts = 0.
- Role/client organization mismatch = 0.
- Profile/client organization mismatch = 0.
- Invoice orphan customer = 0.
- Invoice/customer organization mismatch = 0.
- Payment/invoice customer mismatch = 0.
- No public RPC reads or writes the removed `clients.client_types` column.
- RLS is enabled on `clients`, `party_roles`, `customer_profiles`, `supplier_profiles`.
- Each of those four tables has SELECT/INSERT/UPDATE/DELETE tenant policies; policy predicates/checks use the current organization.

Observed live counts for organization `32493f9d-f887-4a69-bee7-fc94bec27fc7`:
- Active customers: 43
- Active suppliers: 9
- Company profiles: 2
- AutoDrive-eligible profiles (`MARKETER` or `WORKSHOP_OWNER`): 8

### 6) Gradle/JUnit execution
Command: `./gradlew :feature:party:testDebugUnitTest --offline --build-cache`

Result: **BLOCKED_ENVIRONMENT** before compilation.

Reason: Gradle 8.9 distribution is not cached locally. Wrapper attempts `https://services.gradle.org/distributions/gradle-8.9-bin.zip`, but this runtime has no internet access (`UnknownHostException`). This is not a Kotlin test failure.

### 7) Android Room instrumentation tests added
File: `data/database/src/androidTest/kotlin/com/verto/app/data/local/CustomerPartyV2TenantIsolation387Test.kt`

Cases added:
1. Customer list is tenant-scoped by active Party role.
2. Same party can project different customer profiles per organization without leakage.
3. Customer balance ignores invoices/payments from other organizations.
4. Archived customer role disappears from that organization's reads.

Execution: **BLOCKED_ENVIRONMENT** by the same Gradle-wrapper limitation; Android instrumentation also requires an Android test runtime/device.

### 8) Pure JUnit Party V2 contract tests added
File: `feature/party/src/test/kotlin/com/verto/app/feature/party/application/CustomerPartyV2Contract387Test.kt`

Cases added:
1. Profile fields keep explicit one-purpose semantics.
2. Unknown legacy classification is never guessed into a customer role.
3. Legacy competitor compatibility remains dual-role until taxonomy cleanup.

Execution through Gradle: **BLOCKED_ENVIRONMENT**. Equivalent domain behavior was executed successfully through the local Kotlin runtime harness above.

## Conclusion

v387 passes the Party V2 data foundation and tenant-isolation tests, including live server integrity. Customers should **not** yet be declared complete because three acceptance failures remain: taxonomy cleanup, delete/archive UX semantics, and CustomerDashboard/Profile V2 rendering.
