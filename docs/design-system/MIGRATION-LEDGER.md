# Migration Ledger

| Session | Scope | Status | Notes |
|---:|---|---|---|
| 279 | Baseline, scanner, governance | IMPLEMENTED | v278 debt frozen; new-growth gate added. |
| 280 | Ownership, contracts, Material policy | IMPLEMENTED | Core boundary and PDF utility ownership documented. |
| 281 | Semantic foundations, RTL, Insets | IMPLEMENTED | Tokens and contrast verifier added. |
| 282 | Inventory, catalog, screenshot ADR | IMPLEMENTED | Deterministic inventory and matrix policy added. |
| 283 | Canonical component library | IMPLEMENTED | Shared Tier-1 contracts extended. |
| 284 | Shell/Home | IMPLEMENTED (static) | Shared controls and UI resources migrated; runtime screenshot gate unavailable. |
| 285 | Invoices/Inventory | IMPLEMENTED (static) | Shared controls and UI resources migrated; runtime gates unavailable. |
| 286 | Cash/Expenses/Party | IMPLEMENTED (static) | Shared controls and UI resources migrated; dynamic strings remain. |
| 287 | Logistics | IMPLEMENTED (static) | Shared controls and UI resources migrated; runtime gates unavailable. |
| 288 | Settings/Auth/Employees/Reports | IMPLEMENTED (static) | Shared controls and UI resources migrated; dynamic strings remain. |
| 289 | Full QA, performance, closure | BLOCKED | Gradle/device/emulator not available in this environment. |

| 290 | Scanner/Policy/dual-gate governance | IMPLEMENTED | Authoritative scanner and separate migration/final gates established. |
| 291 | Exception ledger and Baseline semantics | IMPLEMENTED | 6→4 bootstrap repaired; active v291 ledger created without allowance growth. |

`PARTIAL` and `BLOCKED` are deliberate states. They are not converted to PASS by changing the scanner or by adding permanent exceptions.

| 292 | Ownership repair and deterministic string contract | IMPLEMENTED (static) | Removed unused/domain-owned Design System APIs; localized Invoice/Party contracts; PdfUtils remains in `core/export`. |
| 294 | Migration Gate repair | IMPLEMENTED (static) | Four changed-hash exceptions closed; current-state snapshot synchronized by explicit user scope override; Migration/Diff/Contract gates PASS; Gradle compile blocked by offline wrapper download. |
| 295 | Core Design System correctness | BLOCKED | Static gates PASS; Core user-facing literals are zero and `VertoListRow` click behavior is implemented, but required interaction tests/compile could not execute because Gradle 8.9 wrapper download is unavailable offline; Core also has no declared Compose UI test dependency permitted by session scope. |
| 296 | Semantic color repair | IMPLEMENTED (static acceptance) | Light/Dark Danger aligned with Material3; Secondary and destructive content contrast repaired; official verifier expanded. Gradle/tests intentionally not required by explicit execution override; static gates are the acceptance authority for this session. |
| 297 | Domain String Ownership | BLOCKED | Static ownership/migration gates PASS: core/designsystem domain strings 820→0; external core ds refs 1214→0; ownership manifest complete. Required Gradle resource merge/compile not executable because the Gradle 8.9 wrapper download is unavailable in this offline environment (`UnknownHostException: services.gradle.org`). |
| 298 | Hardcoded String Elimination | IMPLEMENTED_STATIC / BUILD_ENVIRONMENT_BLOCKED | Official hardcoded debt 375→0 and legacy-focused debt 74→0; v291 active entries 29→0; Migration/Diff/Contract gates PASS. Required Gradle resource merge could not bootstrap Gradle 8.9 because `services.gradle.org` is unreachable (`UnknownHostException`), so compile/tests are not claimed PASS and this archive is not Source of Truth. |
| 299 | Material Wrapper Migration Closure | IMPLEMENTED_STATIC / BUILD_ENVIRONMENT_BLOCKED | 214 direct MUST_WRAP calls across 79 files migrated to Core wrappers; rawMaterialMustWrap 214→0; Migration/Diff/Contract gates PASS. Gradle 8.9 could not bootstrap because services.gradle.org is unreachable, so compile/tests are not claimed PASS. |
| 300 | Logistics Token & Responsive Cleanup | IMPLEMENTED_STATIC / BUILD_ENVIRONMENT_BLOCKED | rawDpOutsideApprovedTokenFiles 22→0; adaptive 360dp/1.5 contract centralized with 1:1 behavior; Migration/Diff/Contract static gates PASS. Gradle/runtime verification is reported separately and is not claimed PASS when the environment cannot bootstrap Gradle. |
| 301 | Motion System Closure | IMPLEMENTED_STATIC / BUILD_ENVIRONMENT_BLOCKED | rawMotionDurations 5→0; VertoMotion attentionPulse/long adopted at 5 call sites; static gates PASS. Gradle result is recorded in Verto-v301-report.md. |
| 302 | API Surface & Component Adoption Cleanup | IMPLEMENTED_STATIC / BUILD_ENVIRONMENT_BLOCKED | Public API 54→37; 17 unused declarations retired; inventory corrected/deterministic; static gates PASS; Gradle 8.9 bootstrap blocked by `UnknownHostException: services.gradle.org`. |
| 303 | Visual, Accessibility & Final Runtime Closure | BLOCKED_SCREENSHOT_HARNESS | Input/static preflight PASS and Core accessibility/fontScale androidTest source added without production behavior changes. Official screenshot plugin compatibility could not be proven from available local metadata; Gradle 8.9 bootstrap remains blocked by `UnknownHostException: services.gradle.org`, `adb` is unavailable, and screenshot/TalkBack/focus/reduced-motion/build gates remain NOT_RUN/BLOCKED. |
