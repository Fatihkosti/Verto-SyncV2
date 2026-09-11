# SESSION 357 — Invoice Flow Repair

Source of Truth: `Verto-v356-source.zip`

## Implemented
- Added a visible, editable invoice date to the local sales invoice editor; selected date continues through `originalCreatedAt` into the save use case.
- Removed the customer-credit approval gate from the sales save path. Partial/unpaid settlement now saves directly as `CREDIT`; the app bridge no longer calls `enforceCustomerCredit`.
- Moved the Home invoice FAB to the screen-level `Scaffold`, so it does not disappear when the activity feed is empty.
- Replaced the invoice-details horizontally scrolling action cards with a single compact equal-width action row.
- Renamed the active `adddebt` presentation package and symbols to `invoiceeditor`; active routes are now `new_invoice/client/{clientId}` and `edit_invoice/{clientId}/{invoiceId}` with no legacy route aliases.
- Removed strings used only by the deleted credit-approval screen.
- Updated active invoice verification paths and the payment feature architecture contract for the package rename.

## Verification
- `INVOICE_FLOW_REPAIR_STATIC=PASS`
- Payment strings XML parse: PASS.
- Settlement policy executable checks: PASS.
- Active production source contains no `AddDebt`, `adddebt`, `add_debt`, or `edit_debt` references.
- Full Gradle compilation could not run in this environment because Gradle 8.9 is not cached and network access is unavailable; the wrapper attempted to fetch `services.gradle.org`.

## Scope safety
No dependency versions, Room schema, Supabase schema, inventory posting logic, payment posting logic, or unrelated application behavior was changed.
