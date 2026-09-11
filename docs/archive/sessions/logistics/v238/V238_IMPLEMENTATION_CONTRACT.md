# Verto Logistics v238 Implementation Contract

## Scope

v238 completes shipment planning details 5/6 and 6/6:

- Customs planning as a separate event after a route station.
- Customs checkpoint, expected duration, historical suggestions, and planning documents.
- Review summary and compact timeline with cities only.
- Deep-link edit from review and return-to-review after save.
- Final validation, idempotent plan approval, revision 1, and READY transition.
- Future planned-leg edits only, with mandatory reason and atomic revision history.

## Invariants

1. Customs is never written as a new route milestone.
2. Country identity is not rendered in customs/review/timeline.
3. Planning attachments are not execution/payment proof.
4. Approval revalidates the current persisted aggregate and eligible purchase invoices.
5. Initial approval creates exactly revision 1 and READY in one transaction.
6. Approved plan edits never reopen or rewrite started/completed movement history.
7. A future planned-leg edit requires a reason and writes the operational update, audit event, and consecutive FUTURE_EDIT revision atomically.
8. No Room schema, migration, module, or dependency-graph change is owned by v238.

## Verification

- Domain/application v238 sources compile with standalone `kotlinc` against the project domain contracts.
- Logistics session guard must pass with Room schema 61 and 31 modules.
- Full Gradle verification requires the omitted `gradle-wrapper.jar`; the source package does not contain it.
