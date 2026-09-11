# Verto v385 — Party V2 Customer Cutover

## Scope
- Make Party V2 / CustomerProfile the authoritative customer write model.
- Remove the legacy ClientScreen navigation path and dead screen-specific code.
- Replace overloaded customer identity fields with explicit CustomerProfile fields.

## CustomerProfile V2
Explicit fields now include:
- ageYears
- purchaseContactName
- businessActivity
- workplaceName
- shopName
- workshopName
- vehicleModels
- workshopWorkerCount

New customer saves no longer overload `carType`, `specialty`, `secondaryPhones`, or customer `workplace`.
Supplier compatibility remains separate where those legacy fields are still required by the existing supplier contract.

## Data migration
- Room schema: 91 -> 92.
- Existing customer data is backfilled into the new semantic fields.
- Supabase expand/backfill migration included at:
  `supabase/migrations/20260829230000_v385_customer_profile_v2.sql`
- Remote Supabase migration was not applied by this package operation.

## Legacy removal
Removed:
- ClientScreen.kt
- ClientViewModel.kt
- ClientScreenComponents.kt
- ClientContactTopBar.kt
- `client/{clientId}` screen destination

Old persisted notification/deep-link routes are canonicalized to `client_dashboard/{id}`.

## Verification
PASS:
- `scripts/verify-party-v344.sh`
- `scripts/verify-party-v385.sh`
- Room 91 -> 92 SQL data migration simulation
- PartyIdentityModels Kotlin compilation with local kotlinc

BLOCKED_ENVIRONMENT:
- Full Gradle compile could not run because Gradle 8.9 is not installed locally and the environment has no network access to `services.gradle.org`.

## Remaining follow-up
Some legacy identity fields still exist for supplier/compatibility/sync consumers outside this customer cutover. They should be removed in a later repository-wide compatibility cleanup after all consumers are migrated.
