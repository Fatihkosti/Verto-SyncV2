# Purchase Invoice v370 — Engineering Report

## Acceptance
Static acceptance gate: PASS.

## P0 fixed
International purchase prices can no longer be mixed with SDG logistics costs. Invoice transaction values are converted at the invoice recognition rate before they become logistics `basePurchaseUnitPrice`.

## Installments
Credit purchases can define a first due date, installment amount, and month interval. The last installment absorbs the exact remainder. Domain validation requires the schedule total to equal outstanding debt. Pending-action logic advances to the first unpaid installment after payments.

## Posted purchase edits
The user can edit a posted purchase invoice. Local inventory effects are reversed and the corrected posting is applied atomically with audit. International purchases remain outside inventory until receiving.

## Sync
Installment schedules are stored in Room and included in financial aggregate events. Financial Inbox materializes the schedule on other devices after the invoice compatibility row reaches the required lifecycle version.

## Verification limitation
Gradle 8.9 was unavailable offline, so full Android compilation/build could not run in this environment.
