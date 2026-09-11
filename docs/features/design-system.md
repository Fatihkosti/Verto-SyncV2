---
status: canonical
scope: feature
owner: "core:designsystem"
last_verified_against: v315
---
# Design System

## Purpose

Feature-facing explanation of how production features consume the canonical Design System.

## User workflows

- Use canonical components/scaffolds/forms
- Apply foundation/component tokens
- Review accessibility/quality constraints before feature UI changes

## Entry points

- `core:designsystem` components/theme
- feature-specific design tokens that consume shared primitives

## Business rules

- The authoritative design rules live in `DESIGN_SYSTEM_CONTRACT.md` and `UX_UI_QUALITY_CONTRACT.md`; this page does not duplicate them.
- Domain-specific business behavior must not be moved into `core:designsystem`.

## Domain model

Compose components, colors, typography, foundation/component tokens.

## Data ownership

Canonical business ownership: `core:designsystem`. Persistence/transport remains with declared data modules; this page does not reassign module ownership.

## Local persistence

No business persistence.

## Server dependencies

Not applicable.

## Permissions

Not applicable; feature permissions remain in owning features.

## Offline behavior

Fully local UI infrastructure.

## Sync behavior

Not applicable.

## Validation

Component API/quality tooling and feature UI validation are separate concerns.

## Error states

UI state/error components render errors but do not own business error taxonomy.

## Notifications/events

Not applicable.

## Critical invariants

- Shared Design System must remain domain-agnostic.
- Feature UI should prefer canonical shared components over legacy wrappers where contracts require it.

## Testing notes

Design-system static scanners/catalogs and UI checks referenced by canonical contracts.

## Known limitations

Legacy Material/hardcoded debt may remain under exception/verification ledgers; this page does not declare it resolved.

## Related docs

- [Design System Contract](../design-system/DESIGN_SYSTEM_CONTRACT.md)
- [UX/UI Quality Contract](../UX_UI_QUALITY_CONTRACT.md)

## Evidence

- `core/designsystem/src/main/kotlin/com/verto/app/ui/components`
- `core/designsystem/src/main/kotlin/com/verto/app/ui/theme`
- `docs/design-system/DESIGN_SYSTEM_CONTRACT.md`
- `docs/UX_UI_QUALITY_CONTRACT.md`
