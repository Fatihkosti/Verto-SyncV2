# Token Catalog

## Semantic color roles

`Surface`, `OnSurface`, `SurfaceVariant`, `OnSurfaceVariant`, `Primary`, `OnPrimary`, `SuccessContainer`, `OnSuccessContainer`, `WarningContainer`, `OnWarningContainer`, `DangerContainer`, `OnDangerContainer`, `InfoContainer`, `OnInfoContainer`, `Border`, `BorderStrong`, `DisabledSurface`, `DisabledContent`, and `FocusIndicator` are the semantic roles exposed by the Design System.

## Typography roles

`PageTitle`, `SectionTitle`, `CardTitle`, `Body`, `BodySecondary`, `Label`, `Metadata`, `Amount`, and `Action` map to the central Verto typography scale. Tajawal remains available for document rendering; Compose UI uses the configured Arabic-first family.

## Geometry and motion

The shared scale is 8pt with documented half-step exceptions. Common spacing, radius, stroke, elevation, minimum touch target, content width, safe padding, and IME behavior are tokenized. Motion roles are `Instant`, `Fast`, `Normal`, `Slow`, and `Emphasized`.

Raw literals are permitted only in foundation definitions, intrinsic vector metadata, or a feature-owned asset/template token file.

## Session 296 semantic color contract

Runtime danger/error is theme-aware and is owned by `VertoColors`, not by the raw `StatusRed*` palette.

| Role | Light | Dark | Content |
|---|---|---|---|
| Danger | `#B91C1C` | `#F87171` | `OnDanger` |
| DangerContainer | `#FFEEEE` | `#4A171B` | `OnDangerContainer` |
| OnDanger | `#FFFFFF` | `#10111A` | Danger fill content |
| OnDangerContainer | `#7F1D1D` | `#F7F8FC` | Danger container content |
| Secondary | `#3B82F6` | `#3B82F6` | `OnSecondary` |
| OnSecondary | `#10111A` | `#10111A` | Secondary fill content |
| SecondaryContainer | `#EEF5FF` | `#172D52` | `OnSecondaryContainer` |

`ErrorColor`/`ErrorContainer` remain compatibility names, but resolve through the active theme's Danger roles. Material3 `error`, `onError`, `errorContainer`, and `onErrorContainer` map to the same semantic values. Small semantic text/icon foreground-background pairs must meet at least `4.5:1`; disabled content is explicitly exempt by policy.
