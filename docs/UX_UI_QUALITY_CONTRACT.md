---
status: canonical
scope: system
owner: "core:designsystem + owning feature presentation"
last_verified_against: v314
---
# Verto UX/UI Quality Contract

Status: **ACTIVE**  
Owner: `core:designsystem` + owning Feature presentation layer

## Scope

Applies to every new screen and every major UI migration. It is a QA contract, not a redesign mandate.

## Mandatory review matrix

Every covered screen must be reviewed at:

- width: 320dp
- width: 360dp
- width: 412dp
- fontScale: 1.0
- fontScale: 1.3
- fontScale: 2.0
- RTL layout direction
- Light theme
- Dark theme
- TalkBack sanity check

## Acceptance rules

- No clipped, overlapping, or unreachable primary content/actions.
- Primary and secondary actions remain operable with large font scaling.
- Interactive targets are at least 48dp.
- Directional navigation is RTL-aware.
- User-visible states are not communicated by color alone; text/icon/semantics must also convey meaning.
- Theme-dependent surfaces and states use semantic/theme colors.
- Generic controls use canonical Verto Design System components when a behavior-compatible primitive exists.
- New or modified production UI must not introduce raw visual constants or hard-coded user-visible strings.
- TalkBack focus order is coherent; meaningful controls expose labels/content descriptions where required.

## Evidence

For a new screen or major migration, the execution report must record the matrix cases actually checked and any accepted file-scoped exception. No directory-wide or wildcard exception is valid.
