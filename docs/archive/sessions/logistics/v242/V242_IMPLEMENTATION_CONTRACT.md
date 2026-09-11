# Verto v242 — Logistics Quality & Production Closure Contract

## Scope

v242 closes the v234–v242 logistics redesign through quality hardening, regression coverage, accessibility, Design System consolidation, and proof that no retired shipment UI remains reachable.

## Production rules

1. Logistics navigation exposes only the current `logisticsv2` UI flow.
2. Historical shipment rows remain preserved through the existing retirement/archive compatibility path; v242 does not delete historical data.
3. Shared UI primitives must come from the Verto Design System. Logistics may keep domain-specific compositions, but must not maintain parallel generic top bars, form cards, text fields, primary buttons, or confirmation dialogs.
4. Critical interactive targets remain at least 48dp.
5. Planning and general logistics screens force RTL and remain vertically scrollable.
6. IME Next/Done behavior remains functional after the Design System consolidation.
7. Trip-type and transport choices expose one selectable TalkBack target with selected state instead of duplicate nested actions.
8. Draft step, route workspace, and final-receiving draft remain durable through the existing `LogisticsDraftProgressStore`.
9. Document opening continues through the existing application use case and platform bridge.
10. Accepted inventory, landed-cost settlement, and close remain idempotent when stable request IDs are retried.

## Tests added

- JVM integration: purchase-invoice shipment line → accepted inventory posting → landed-cost update → close, including retries.
- Android Compose UI: 320dp + font scale 2.0 scrolling/RTL and transport-choice accessibility semantics/touch height.
- Reproducible static v242 quality guard for navigation, Design System, durability, document-open wiring, and test presence.

## Verification constraints

The supplied source package does not include `gradle/wrapper/gradle-wrapper.jar`. Full Gradle unit/instrumentation execution therefore cannot be certified inside this package. Isolated Kotlin execution and static verification are still required and must be reported separately.

## Invariants

- Room schema remains 61.
- Module count remains 31.
- No production migration is added.
- No Logistics V2 persistence schema is changed.
- Test-only Compose UI dependencies may be added because v242 explicitly requires UI tests.
