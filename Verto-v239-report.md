# Verto v239 Report

## Implemented

- Added explicit movement preparation with actual carrier/contact/cartons/weight while preserving planned values as the planning baseline.
- Preparation moves READY -> WAITING_DEPARTURE without starting movement or its clock; station-to-station preparation is allowed only after the current station is unloaded.
- Added saved shipping-contact autofill and editable actual execution facts.
- Movement start is custody-gated: every shipment source must be handed to the actual carrier before "بدأت الحركة" succeeds.
- Starting movement records actual departure, starts the shipment once, and derives ETA from minute-level transit duration with legacy day fallback.
- Delay evaluation now respects minute-level transit duration.
- Actual freight cost, payment confirmation, and transfer proof remain separate operations with stable request IDs for retry safety.
- Preserved the existing operational arrival action ("وصلت المحطة") and occurredAt/recordedAt event separation.
- Added v239 execution regression tests for preparation idempotency, custody-gated start, ETA, and delay timing, plus presentation draft tests.

## Architecture

- Room schema: 61 unchanged.
- Modules: 31 unchanged.
- No migration, dependency catalog, settings, or build-logic changes.
- Planned and actual logistics facts remain separate; no silent overwrite of approved planning values.

## Verification

- v239 execution tests: PASS using isolated Kotlin compilation/execution.
- v239 domain/application Kotlin compile: PASS.
- Static quality scan: no regression versus v238 in monitored structural metrics (long functions, parameter lists, large files, broad catches, architecture violations).
- Logistics session delta guard: scope/module/Room checks PASS; the same six inherited source-package checks remain (missing Room schema `61.json` plus five legacy shipment-table declaration checks).
- Full Gradle build/tests: not runnable from this source package because `gradle/wrapper/gradle-wrapper.jar` is absent.
