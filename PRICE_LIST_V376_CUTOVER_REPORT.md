# Verto v376 — Price List Template Cutover

## Implemented
- Replaced the persisted copied-item price-list model with reusable templates that store inventory IDs only.
- Inventory remains source of truth for item name, part number, current selling price, and current availability.
- New quote draft supports direct item selection plus one or many templates; duplicate items are removed automatically.
- Each template exposes `available only` and `all` selection at quote time.
- Templates support create, edit, favorite, and confirmed delete.
- Draft price overrides are temporary and never mutate inventory or the source template.
- PDF is one canonical A4 flat table. Template names and stock quantities are never exported.
- PDF table headers repeat on every page; long item/part-number text is safely ellipsized; generation runs off the UI thread.

## Legacy removed from active runtime
- Removed legacy `PriceListHeaderEntity` / `PriceListItemEntity` runtime ownership.
- Removed direct price-list push/pull/delete SyncMisc path and its sync slots/DTOs/identifier helpers.
- Removed DataStore pending price-list deletion queue.
- Removed legacy multi-theme price-list PDF preference and selectors.
- Room migration 90→91 drops `price_list_header` and `price_list_items` after preserving unambiguous inventory selections as a template.
- Supabase v376 migration cuts `PRICE_LIST` to unified template payloads and drops the legacy server tables.

Historical migrations and historical sync evidence still contain old table names intentionally; they are not runtime owners and must remain for upgrade/audit history.

## Verification
- Runtime legacy symbol scan: PASS (no old price-list entity/sync-slot/preference symbols in Kotlin).
- XML/JSON parse checks: PASS.
- Price-list resource-reference check: PASS (32/32 present).
- Unified sync contract gate: PASS.
- Unified sync coverage progresses past PRICE_LIST; remaining failure is pre-existing coverage omissions for unrelated sync RPCs.
- Kotlin parser scan of changed Kotlin files: no syntax diagnostics detected.
- Full Gradle compile/test: BLOCKED_ENVIRONMENT. Gradle 8.9 is not cached; wrapper download is unavailable in the offline environment.
- Room `91.json` cannot be truthfully generated without the Room compiler; migration instrumentation evidence was added and will validate once Gradle runs.

## Server deployment order
1. Deploy v376 as the minimum supported client.
2. Run normal Gradle/Room build to generate/verify schema 91.
3. Apply `supabase/migrations/20260829080000_v376_price_list_template_cutover.sql`.

The Supabase migration is included in source only; it was not applied to the live server in this change.
