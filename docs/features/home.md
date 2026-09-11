---
status: canonical
scope: feature
owner: "app + feature:dashboard"
last_verified_against: v315
---
# Home

## Purpose

Home composition: greeting/header, search, quick actions, pending actions, activity feed, badges and integration contributions.

## User workflows

- Open Home
- Search across features
- Launch/reorder quick actions
- Review/dispatch pending actions
- Review activity feed/notification badge/education

## Entry points

- `app` Home screen/view model
- dashboard API/contracts and registries
- feature contributor providers

## Business rules

- Home composes providers; it must not absorb their domain business rules.
- Permission context filters actions/results.
- Persisted quick-action/event state is organization/user scoped.

## Domain model

Dashboard API contracts, `home_quick_action_order`, `home_event_states`, educational topics/targets.

## Data ownership

Canonical business ownership: `app + feature:dashboard`. Persistence/transport remains with declared data modules; this page does not reassign module ownership.

## Local persistence

Room Home state and educational content plus contributor Room data.

## Server dependencies

Educational topic tables sync directly when available; notification badge/data comes through owning sources.

## Permissions

`HomePermissionContext` gates providers/actions; provider-specific permissions remain authoritative.

## Offline behavior

Home renders local state and provider data; server-only actions still require connectivity.

## Sync behavior

Educational content is a `SyncParticipant`; other Home content freshness follows source feature sync.

## Validation

Quick-action IDs are nonblank/unique; snooze timestamps/event keys are validated.

## Error states

Unavailable educational server tables are explicitly skipped by that participant; domain provider errors do not redefine Home truth.

## Notifications/events

Home displays notification badge and provider-generated pending/activity items.

## Critical invariants

- Quick-action order is scoped by organization/user.
- Snooze deadlines must be future/nonnegative according to use-case guards.

## Testing notes

Dashboard/Home provider tests and performance budget contract.

## Known limitations

Home is a composition surface with intentional app bridges, not a standalone data owner.

## Related docs

- [Architecture Overview](../architecture/overview.md)
- [API / Server Reference](../api/README.md)

## Evidence

- `app/src/main/kotlin/com/verto/app/ui/screens/home`
- `feature/dashboard/src/main/kotlin/com/verto/app/feature/dashboard`
- `app/src/main/kotlin/com/verto/app/feature/dashboard/bridge/RoomHomeStateStores.kt`
