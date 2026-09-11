# Verto v248 — Implementation Report

Session 248 is implemented on top of v247.

Implemented:
- explicit invoice lifecycle `DRAFT / POSTED / VOID`, separate from cash/credit terms and derived payment state;
- optimistic lifecycle versioning and append-only void metadata (`voidedAt`, reason, request/write id);
- financial immutability for POSTED invoices, with safe descriptive-edit allow-list only;
- hard-delete protection for POSTED/VOID invoices locally and in the additive Supabase SQL contract;
- immutable invoice-line descriptive snapshots (SKU/unit) in addition to the F247 price/cost snapshots;
- idempotent Void orchestration that reverses original inventory, payment allocations, realized FX and actual historical functional cash values;
- explicit payment disposition required before voiding paid invoices; missing historical cash evidence fails closed;
- linked shipment/receipt invoices are blocked from direct Void so the dependent chain cannot be broken;
- reversal of the F247 latest-purchase-price revaluation only when the voided purchase is still the latest pricing event;
- permission gates for posting, safe descriptive edit, stock override, FX approval, manual landed-cost authority and Void;
- direct commission/payment-term writes restricted to DRAFT invoices;
- both invoice-details and client-details Void entry points now require a reason and explicit payment handling;
- Room 65->66 migration, lifecycle DTO/sync fields, additive Supabase SQL, and focused F248 verifier/tests.

Verification: F244/F245/F246/F247/F248 source/static migration checks pass, conflict markers are clean, and old Void bypass entry points are absent. Full Gradle compilation could not start because Gradle 8.9 is not cached and the sandbox has no network access.

See `verification-invoice-F248.md` for details.
