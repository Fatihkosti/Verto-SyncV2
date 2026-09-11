# Verto v235 Implementation Report

## Result

v235 implements the first complete, saveable shipment-planning Wizard slice on top of v234 without changing the Room schema or project dependency graph.

## Delivered

- Verto planning scaffold with two-stage progress, fixed navigation, RTL and IME-safe layout.
- Shipment-definition UI matching the bundled planning reference structure.
- Read-only generated shipment number.
- Free origin/destination country entry and city entry.
- History-only country suggestions ranked by usage frequency then recency.
- Canonical country normalization with legacy ISO-key compatibility.
- Structured origin/destination draft data and partial-draft restoration.
- Exact definition validation messages and first-invalid-field bring-into-view behavior.
- Save/exit confirmation from the first planning screen.
- Existing active-employee selection preserved.
- Country removed from visible post-definition route/review/execution/analytics paths while retaining hidden routing/reporting metadata.
- Unit tests added for country normalization and definition validation.

## Persistence / data model

- Room schema: **61 → 61**.
- Migrations added: **none**.
- `sourceLocation` / `destinationLocation` written by the v235 definition save path are city-only display values.
- Structured location details retain country key/name/city for internal processing and future reporting.

## Verification

### Passed

- Direct Kotlin compile: country normalizer + shipment validation.
- Direct Kotlin compile: header-save use case with minimal local port/injection stubs.
- Direct Kotlin compile: v235 definition presentation model with a local planning-step stub.
- Definition smoke test: valid flow, exact validation behavior, normalized same-location rejection, and city-only legacy display.
- Static quality comparison against v234: no regression.
  - architecture violations: 18 → 18
  - broad catches: 20 → 20
  - dependency cycles: 0 → 0
  - excessive parameter lists: 451 → 451
  - large files: 12 → 12
  - long functions: 330 → 330
- Country-visibility source audit: no active v235 planning/detail call-site renders the static country picker or analytics country list.
- Logistics session delta guard: 0 deletions, 0 out-of-allowlist changes, 31 modules, Room 61, no dependency-graph drift.

### Environment-limited

- Full Gradle compile/tests could not run because the Gradle 8.9 wrapper distribution is not cached locally and this sandbox has no network access.
- Screenshot/golden tests could not run because no screenshot-test harness is configured for the shipment module and Gradle cannot execute here.
- Device runtime checks for 320/360dp, FontScale 2.0 and TalkBack therefore remain unexecuted in this environment.
- The repository guard still reports the pre-existing v234 omission of `app/schemas/com.verto.app.data.local.AppDatabase/61.json`; v235 does not fabricate a Room export because it introduces no schema change. With the legacy-removal mode matching this source tree, this is the guard's only remaining failure.

These are verification limitations only; they are not reported as passing tests.

## v236 handoff

Use this packaged v235 tree as the next source of truth and read `docs/logistics/v235/V235_IMPLEMENTATION_CONTRACT.md` before implementing the suppliers/invoices planning slice.
