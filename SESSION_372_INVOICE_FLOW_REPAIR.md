# Verto v372 — Invoice Flow Repair

## Scope
Invoice creation/edit/display routing, credit due dates, purchase installment schedules, invoice drafts, and inactive invoice-editor legacy cleanup.

## Implemented
- Fixed credit sale save to require and persist a valid contractual due date/installment instead of `dueDate=0`.
- Routed international purchases through the modern purchase editor rather than the removed generic legacy editor.
- Preserved existing purchase installment schedules during edits unless the user explicitly changes them; changed outstanding totals require schedule reconciliation.
- Persisted invoice draft due installments through Room and process death.
- Replaced unstable dirty detection with a stable content baseline and removed timestamp-induced false changes.
- Added Room migration 89 -> 90 for `due_installments_json`; schema version is 90.
- Removed inactive generic invoice editor files and dead invoice symbols.
- Updated static regression gates and added the v372 invoice gate.

## Verification
PASS:
- INVOICE_349_STATIC_GATE
- INVOICE_351_STATIC_GATE
- PURCHASE_INVOICE_369_STATIC_GATE
- PURCHASE_INVOICE_370_STATIC_GATE
- INVOICE_LEGACY_CLEANUP_371_STATIC_GATE
- INVOICE_372_STATIC_GATE
- DEAD_SYMBOL_CHECK

## Build limitation
A full Android Gradle compilation could not run in this environment because Gradle 8.9 is not cached locally and external network access is unavailable. No dependency versions were changed.
