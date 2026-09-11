# Verto v240 — Station Receipt & Customs Execution Contract

## Scope

v240 completes station receipt and the customs event defined by the v234+ planning contract. It does not change Room, modules, dependencies, or approved route planning facts.

## Execution rules

1. **Station receipt defaults to the latest confirmed cargo.** The normal path confirms the same carton count without forcing discrepancy fields onto the user.
2. **Differences are explicit.** Difference/condition fields and the Repack path are revealed only when the user indicates a discrepancy. A changed confirmed cargo count must be recorded through Repack before a handoff can use that new count.
3. **Custody changes only on confirmation.** Opening a receipt dialog never changes custody. The atomic handoff write occurs only after a valid receipt is confirmed.
4. **Customs is not a route station for v234+ shipments.** `LogisticsCustomsPlan.afterStationId` identifies the real route station that hosts the execution event. Legacy `CUSTOMS` milestones remain executable for old rows only.
5. **Customs cannot be bypassed.** After unloading the designated host station, START_CUSTOMS/COMPLETE_CUSTOMS become the operational actions. Loading onward is rejected until customs has both start and completion facts.
6. **Broker, costs, payment, and documents stay separate facts.** Broker custody is captured on customs start; customs costs use the existing actual-cost/payment flow; customs documents are persisted as `CUSTOMS_DOCUMENT` scoped to the host milestone.
7. **Friday is excluded from customs elapsed time.** Working elapsed time is calculated in the shipment event timezone using the configured customs calendar policy. v240 uses the existing `FRIDAY_OFF` policy.
8. **Next movement opens after customs completion.** Completion returns the shipment to `AT_STATION`; movement preparation remains hidden while customs is pending/in progress and becomes available after completion.
9. **Planning remains immutable during execution.** No v240 operation rewrites planned carrier, route, customs duration, or approved plan structure.

## Persistence / architecture guardrails

- Room schema remains **61**.
- Module count remains **31**.
- No migration, dependency graph, build-logic, or settings changes.
- Existing atomic store operations are reused; no direct database access is introduced into shipment domain/application.

## Acceptance coverage

- Same-count station receipt defaults.
- Carton discrepancy requires a reason and preserves received count.
- Failed receipt validation does not transfer custody.
- Planned customs after a non-CUSTOMS route station executes correctly.
- Designated customs host cannot load onward before completion.
- Completing customs returns to station execution and exposes outgoing handoff/movement work.
- Friday is excluded from customs elapsed/delay time.
