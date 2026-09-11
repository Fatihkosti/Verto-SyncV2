# Session 380 — Price-list UX repair

## Source of truth
- Input: `Verto-v379-home-idea-send-button-repaired.zip`
- Scope: price-list template editing, template selection dismissal, draft price editing only.

## Implemented
1. Existing template editor shows only items already inside that template.
2. `إضافة من المخزون` explicitly opens inventory selection; existing template items are excluded from the add list.
3. Template items can be removed directly from the template view.
4. Selecting a template via `المتوفر فقط` or `إضافة الكل` adds its items and closes the templates dialog immediately.
5. Final quote prices are editable inline and update the draft automatically for every valid positive decimal value.
6. Delete remains available for each final quote item.

## Legacy removed in this scope
- `EditPriceDialog`.
- `editingPrice` screen state.
- `onEditPrice` / `onResetPrice` card callbacks.
- `resetDraftPrice()` ViewModel method.
- `price_list_edit_price` / `price_list_reset_price` strings.

## Verification
- Kotlin delimiter/static scan: PASS for the three modified price-list Kotlin files.
- Android resource XML parse: PASS.
- Legacy symbol grep for removed price-edit path: PASS (0 active references).
- Gradle compile: BLOCKED_ENVIRONMENT. Wrapper requires Gradle 8.9, which is not cached; network access to `services.gradle.org` is unavailable.

## Functional boundaries preserved
- Inventory remains the source of truth for item name, current sell price, stock and part number.
- Template persistence remains inventory-ID based.
- PDF behavior and server schema were not changed.
