---
status: canonical
scope: system
owner: "core:designsystem"
last_verified_against: v314
---
# Verto Design System Contract — Active v226 Enforcement

## 1. Ownership hierarchy

Verto uses one shared Design System owner: `core/designsystem`.

Ownership is layered as follows:

1. **Foundation tokens — Core-owned**
   - spacing, radii, elevation, stroke, alpha, common icon sizes, and minimum touch size
   - typography scale and app font family
   - raw app palette and Dark/Light semantic color mapping
2. **Shared component tokens — Core-owned**
   - reusable geometry that belongs to a shared primitive rather than a business feature
3. **Feature/component compatibility tokens — feature-owned**
   - specialized geometry needed by a specific screen/domain or migration-preservation layout
   - must not be promoted to Foundation merely to remove a literal

`core/designsystem` must never import Feature, Data, Domain implementation, or app screen packages.

## 2. Color contract

Runtime Presentation consumes semantic/theme APIs, not raw business-state palette values.

Use:

- `SuccessColor` / `SuccessContainer`
- `WarningColor` / `WarningContainer`
- `InfoColor` / `InfoContainer`
- `WaitingColor` / `WaitingContainer`
- `OfflineColor` / `OfflineContainer`
- `PermissionColor` / `PermissionContainer`
- `DisabledColor` / `DisabledContainer`
- `ErrorColor` / `ErrorContainer` or Material error semantics where appropriate
- `MaterialTheme.colorScheme` for theme surfaces/error where appropriate

`StatusGreen`, `StatusOrange`, `StatusRed`, and related `Status*` values are raw/theme-construction palette values. Presentation and shared runtime components must not bind business states directly to them.

`Color(0x...)` is forbidden in Presentation outside an explicitly documented allowlist.

## 3. Typography contract

Normal screen UI uses `MaterialTheme.typography` or `VertoTypography`.

Direct `.sp` in Presentation is forbidden. A feature may keep an owner-local `TextScale` in its registered token file only when an exact legacy size is required to preserve layout and is not suitable as a global type token.

New reusable text styles belong in Core typography; feature-specific geometry does not.

## 4. Spacing and size contract

Direct `.dp` in Presentation is forbidden except documented intrinsic/visual exceptions.

Shared values should alias Core `VertoSpacing`, `VertoSize`, `VertoRadius`, `VertoStroke`, or shared component tokens. Specialized chart, preview, or feature geometry stays in the owning feature token file.

Do not add a feature-specific measurement to Foundation just to reduce local token count.

## 5. Shared component ownership

Generic reusable primitives belong exclusively to `core/designsystem`, including:

- `VertoCard`
- `VertoTopBar`
- `VertoEmptyState`
- `VertoLoadingState`
- `VertoStatusBanner`
- `VertoTextField`
- `VertoPrimaryButton`
- `VertoSecondaryButton`
- `SettingsCard`
- `SettingsSectionHeader`
- `SettingsDivider`
- `SettingsNavRow`
- `DialogTextField`

A component that understands Party, Invoice, Shipment, Report, Inventory, or another domain stays with that feature. Domain awareness is not moved into Core merely to remove duplication.

## 6. RTL and navigation contract

Back navigation uses `Icons.AutoMirrored.Filled.ArrowBack`.

Forward/detail affordances use an AutoMirrored directional icon. Hardcoded `ArrowBack`, `ArrowForward`, `ChevronLeft`, or `ChevronRight` from non-auto-mirrored icon sets are forbidden for navigation UI.

A `navigationIcon` must not use `Icons.AutoMirrored.Filled.ArrowForward` as a back affordance.

## 7. Touch-target contract

Shared interactive primitives use a minimum target of `VertoSize.minTouchTarget` = 48dp.

Explicit `IconButton` sizing below 48dp is forbidden. The interactive target may be 48dp while the visual icon remains smaller.

## 8. Allowed exceptions

Only documented exceptions are accepted:

1. `core/designsystem/.../AppIcons.kt`
   - two `24.dp` values are intrinsic `ImageVector` default width/height metadata, not screen layout geometry.
2. `feature/settings/.../SettingsPrintTokens.kt`
   - fixed print/preview template RGB colors are document-template assets, not runtime semantic business-state colors.
3. Chart/preview/specialized geometry
   - may remain in an owning feature token file when it is genuinely geometric rather than general screen spacing.

Any new exception must be explicit, file-scoped, justified, and added to static enforcement. “Temporary” untracked exceptions are not allowed.

## 9. Adding a new UI component

Before adding a component:

1. Determine whether it is generic or domain-aware.
2. Reuse an existing Core primitive if the behavior matches.
3. Use semantic colors and central typography.
4. Use Core dimensions for common layout values.
5. Keep feature geometry in a registered owner-local token file.
6. Ensure RTL directional icons are AutoMirrored.
7. Ensure interactive targets are at least 48dp.
8. Run the Design System scanner before delivery.

## 10. Enforcement

Primary static guard:

```bash
python3 scripts/design-system-scan.py --check
```

The scanner is the active Design System verifier. Historical freeze scripts are not part of the current contract.

The guard rejects new scattered `dp/sp/raw Color`, feature-local semantic palettes, duplicate guarded primitives, direct `Status*` use, non-mirrored navigation arrows, undersized explicit `IconButton` targets, and Core dependency leaks.

From v226 it also enforces:

- raw `Button`, `OutlinedButton`, `OutlinedTextField`, and `TextField` use in Feature/App production Presentation where canonical Verto controls are the generic owner;
- no-growth of hard-coded user-visible Presentation strings;
- file-scoped legacy exceptions through `config/design-system/v226-enforcement-exceptions.json`;
- SHA-256 review locks on every file carrying a legacy exception; any covered file change invalidates the exception until explicitly reviewed;
- stale or reduced exception debt must be removed/tightened before the scanner returns PASS.

`TextButton` is not blanket-banned because v225 found no generic Verto text-action primitive. `AlertDialog` is not blanket-banned because `VertoConfirmationDialog` covers only confirmation semantics, not arbitrary dialog content. Both remain subject to component ownership review.

The central quality entry point is:

```bash
scripts/ci/run-quality-gate.sh all
```

Its required order is Design System enforcement → Detekt → Android Lint → tests → Debug build. Every individual Gradle gate is also protected by the Design System scanner first.

UX/UI review requirements are defined by the active `docs/UX_UI_QUALITY_CONTRACT.md`.

## 11. Runtime verification state

Runtime verification is not deferred by contract. The v226 central gate requires Detekt, Android Lint, tests, and Debug build after Design System enforcement. If the execution environment cannot bootstrap the repository Gradle version, the execution report must mark those stages BLOCKED rather than claiming PASS or weakening the gate.

## 12. Session 296 semantic color and contrast contract

- Feature/runtime presentation consumes semantic theme roles; raw `Status*` values are theme-construction inputs only.
- `ErrorColor` and `ErrorContainer` are theme-aware compatibility accessors for `danger` and `dangerContainer`.
- Filled destructive controls use `OnDanger` (or Material3 `onError`) as content color.
- `MaterialTheme.colorScheme.error*` and `LocalVertoColors.current.danger*` represent the same active Light/Dark contract.
- `Secondary` uses `OnSecondary`; `SecondaryContainer` has an explicit `onSecondaryContainer` in both themes.
- Small semantic text/icon foreground-background pairs are gated at `>= 4.5:1`. Disabled content is exempt by explicit policy.
- Alpha-derived colors are not accepted as small semantic text when compositing would reduce contrast below `4.5:1`; alpha remains suitable for decorative backgrounds/borders where text contrast is not claimed.

## 13. Session 297 string ownership boundary

- `core/designsystem` owns only strings required by Design System components.
- Feature/domain copy must not be placed in Design System.
- Cross-feature common app text approved by the v297 ownership manifest lives in `core/common`, not Design System.
- Feature-domain strings remain local to their owning feature; shared sibling usage does not justify feature-to-feature dependencies.
- Resource ownership moves must preserve the runtime string value and placeholder signature exactly.

## 14. Session 298 hardcoded-resource contract

- Official scanner-defined user-facing hardcoded debt must remain zero after the v298 migration; scanner coverage is not a claim that every repository `String` literal is UI copy.
- Feature/domain copy is owned by its feature resource module. Exact approved Core/Common and canonical Design System strings are reused rather than duplicated.
- Compose presentation resolves resources with `stringResource`; non-Compose boundaries resolve through Android `Context` or an explicitly injected resolver without moving domain copy to Core.
- Dynamic copy uses positional Android resource placeholders while preserving operand order and runtime formatting. Literal whitespace required by runtime output is resource-escaped/quoted so it is not normalized away.
- Technical protocol/separator literals are not converted into UI resources: character separators remain characters and MIME protocol values remain private technical constants.
- New string resources require at least one post-migration consumer, no divergent name collision, and a matching placeholder signature.

## 15. Session 299 Material wrapper ownership contract

- Direct Material3 `IconButton`, `Card`, `TopAppBar`, `ModalBottomSheet`, `TabRow`, and `ScrollableTabRow` calls are not owned by App/Feature presentation after v299; their generic ownership is `core/designsystem` through the corresponding Verto wrapper.
- v299 wrappers are compatibility-shaped ownership boundaries. They preserve caller callbacks, state objects, modifiers, explicit colors/shapes/elevation/borders/indicators, content lambdas, and Material3 defaults rather than redesigning the UI.
- `VertoCard` retains its pre-v299 canonical branch when no compatibility argument is supplied. Migrated raw Material cards select the Material-compatible branch with `contentPadding = PaddingValues(VertoSpacing.none)` as a zero-padding discriminator; the marker does not add layout padding.
- Existing `VertoTopBar` and the pre-v299 icon/content-description overload of `VertoIconButton` remain valid. Public API cleanup or consolidation is deferred to its dedicated later session.
- Feature-local generic replacements for these six Material components are not permitted. Material3 implementation calls inside `core/designsystem` remain an implementation detail of the wrappers.

## 16. Session 300 dimension and adaptive ownership contract

- Production raw `dp` outside approved token owners remains zero after v300.
- Shipment/Logistics-specific geometry is owned by `LogisticsV2Tokens`; shared geometry reuses foundation tokens by semantic role, not numeric coincidence.
- Responsive inline/stack decisions use `VertoAdaptiveTokens`; local `BoxWithConstraints` may continue to supply available content width while the threshold and decision rule remain centralized.
- The inline-stack contract preserves strict `availableWidth < 360dp` behavior and forces stacking at `fontScale >= 1.5f`.
- Existing compact/medium/expanded breakpoints remain unchanged; the v300 inline threshold is a separate local-content adaptive contract.

## 17. Session 301 motion ownership contract

- Production animation durations do not use raw integer timing after v301; raw production motion violations remain zero.
- Animation timings owned by the Design System are referenced through `VertoMotion`.
- `VertoMotion.attentionPulse` is `800ms`; `VertoMotion.long` is `1500ms`.
- Tokenization must not alter easing, repeat mode, animation primitive, or initial/target behavior.
- Operational debounce, polling, and navigation delays are not automatically Motion tokens.
- No project-wide reduced-motion policy existed in the v300 pre-state, so v301 does not invent one; runtime accessibility verification remains session 303 scope.


## 18. Session 302 public API surface contract

- Every public Design System declaration must have a production consumer or explicit system-level justification; the v302 post-state has no zero-consumer public declaration.
- Consumer inventory recognizes both `Name(...)` and Kotlin trailing-lambda `Name { ... }` call forms.
- Same-name local functions are not attributed to Core without explicit import, wildcard import, same-package ownership, or fully-qualified reference evidence.
- Compatibility-shaped Material ownership wrappers may coexist with opinionated form components when their caller and behavior contracts are materially different.
- Unused public forwarding aliases/shims are not retained after v302.
- The current official public surface is 37 declarations: 33 visual/composable components, 1 public styling helper, 2 supporting enums/types, and 1 public icon asset.
- Adding a future public component requires an owner, a documented contract/value above raw Material, and production consumer evidence.
- Session 303 owns screenshot, TalkBack/focus, font-scale, RTL runtime, and reduced-motion accessibility closure.

## 19. Session 303 visual and accessibility runtime closure contract

- Final Design System closure requires static gates **and** runtime evidence; static Migration/Final PASS alone is insufficient.
- Official visual scope remains 33 components. Screenshot evidence must cover all 33 and the 12 canonical Light/Dark, 320/360/412dp, portrait/landscape, RTL, mixed-content, and fontScale 1.0/1.3/1.5/2.0 configurations defined by Session 303.
- Screenshot baselines use the official Compose Preview Screenshot Testing plugin only when a pinned version is proven compatible with the repository AGP/Kotlin/JDK baseline. Verification compares references and never auto-updates goldens.
- First-baseline goldens require explicit review evidence before acceptance; regenerating a reference to hide a failure is prohibited.
- Interactive Design System targets require at least 48dp **logical** size. Instrumented measurement converts semantic pixel bounds using runtime density rather than treating 48px as 48dp.
- Accessibility closure requires automated semantics/state checks plus a real TalkBack and focus-order audit. Decorative icons remain non-focusable; icon-only actions require meaningful labels; selected/disabled/error/loading states must remain truthful.
- fontScale 2.0, RTL, 320dp narrow-layout reachability, modal focus isolation, and mixed Arabic/English/numeric content are runtime acceptance requirements.
- Reduced-motion closure requires executing the inherited motion sites with system animator scales set to zero and restoring the original system values afterward.
- `FINAL_SOURCE_OF_TRUTH` requires screenshot verification, reviewed goldens, Core and Shipment connected tests, TalkBack/focus, fontScale/RTL/reduced-motion runtime, compile/build/unit tests, Detekt, Lint, quality gate, and final static gates all to PASS.
- If Gradle, the official screenshot plugin, device/emulator, or TalkBack runtime is unavailable, the affected gate is `BLOCKED`/`NOT_RUN`; documentation or static inspection cannot promote it to PASS.
