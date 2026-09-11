## Verto sync repair B13-V02 — acceptance recheck; blockers unchanged — 2026-09-11
- Revalidated the supplied B13-V01 WIP tree without changing product code; product-scope hash remains `158fccae5d886f47b1101453d64406522512e8666a2b5cbcf0be6cdfad5a9711`.
- B13 static 48/48, native SQLite, and B06/B11/B12 local regression precursors pass again.
- Gradle 8.9 remains unavailable in cache, network resolution fails, and no usable Android SDK/adb is present; Room/KSP/process-kill acceptance therefore remains NOT_RUN.
- B08 server bootstrap seal integration remains unproven; no live SQL, production data, deployment, APK, GitHub, or Drive action occurred.
- G-B13 remains BLOCKED. B14 was not started because B13 is an explicit dependency and the resume contract forbids advancing without its acceptance evidence.

## Verto sync repair B13-V01 — sealed bootstrap over protected local work; acceptance blocked — 2026-09-11
- Added durable `STAGED` → `STAGED_VERIFIED` bootstrap states, schema 101 seal fields, snapshot digest/coverage/high-watermark/delta-token verification, and resumable staging.
- Added full unresolved-work protection manifest across unified/Party/financial/inventory/Optimal outboxes, attachments, pending references, frozen mutation/batch bytes, and captured local-content generations.
- Promotion now rechecks `SyncPendingProtection` per row inside the cutover transaction; protected rows remain local as `WAITING_LOCAL`, unprotected rows use REMOTE_APPLY, and applied server authority is written only after materialization.
- Added explicit tombstone policy/version checks and kept absence-pruning disabled for financial/inventory/local rows without proven deletion authority.
- Reworked M03 gating so bootstrap staging may progress while migration evidence is unresolved, destructive promotion/pull remain blocked, and unrelated prepared push work still gets an independent push opportunity.
- B13 static 48/48, native SQLite, and B06/B11/B12 regression precursors PASS. Gradle/Room/Android could not run because Gradle 8.9 is unavailable offline; B08 server seal fields are also absent from checked-in SQL.
- G-B13 remains BLOCKED; T18/T31–T34/T46 remain NOT_RUN. No live SQL, production data, APK, GitHub or Drive action occurred. Delivery remains WIP_NOT_RELEASE_READY.

## Verto sync repair B12-V01 — mutable/versioned expense implemented; integration acceptance blocked — 2026-09-11
- Kept expense identity stable across update/VOID and added versioned before/after revision intent with hashes, actor, captured base version, and append-only local/server history.
- Frozen the expense revision and its cash delta in the producer transaction; expense and cash mutations share one batch/dependency chain so retry does not recompute from newer state.
- Centralized expense cash semantics on minor units: note-only = 0, 10000→15000 = -5000, and VOID from 15000 = +15000. The receive applier validates revision/delta and never creates the producer-owned cash effect again.
- Added B12 DTO/schema definitions, Room-open history guards, mutable remote materialization, and a Supabase migration/adapter with server history and domain-drift validation.
- Non-zero server cash effects fail closed with `B12_EXPENSE_BATCH_REQUIRED` until B07 atomic expense+cash batching exists and is proven; no partial financial server write is accepted.
- B12 static 22/22, native SQLite, schema and selected pure-Kotlin checks PASS. Gradle/Room/Android, full T21/T27, PostgreSQL RPC/migration and B07 atomic batching remain NOT_RUN/BLOCKED.
- G-B12 remains BLOCKED and R08 OPEN. No live SQL, production data, APK, GitHub or Drive action occurred. Delivery remains WIP_NOT_RELEASE_READY.

## Verto sync repair B11-V01 — explicit conflict review implemented; runtime acceptance blocked — 2026-09-11
- Replaced automatic conflict winner/rebase behavior with durable human review for generic optimistic-version aggregates; non-generic financial/inventory/state-machine facts fail closed as `DOMAIN_CORRECTION_REQUIRED`.
- Added immutable local/remote conflict evidence, append-only decision audit, redacted field-diff UI, authorization checks, and explicit server-accept / local-resend actions.
- Server acceptance records `SUPERSEDED_WITH_PROOF` rather than ACK and preserves newer local intent instead of overwriting it. Local resend creates a new frozen mutation on the exact displayed server version and links it with `supersedes_mutation_id`; the old mutation remains pending-proof until authoritative receipt/echo proves the replacement.
- Added matching-echo resolution and predecessor/order handling so later local intents remain protected while a replacement proves the superseded conflict.
- Added Room99→100 migration for the new lifecycle states/evidence/audit/supersedes relation and append-only guards, including fresh-schema open guards for conflict proof and frozen outbox semantics.
- Added B11 policy/redaction/permission tests plus host static and SQLite contract probes; both host probes PASS. Gradle8.9/Android/Room/KSP/Hilt/Compose runtime and T30 remain NOT_RUN because the wrapper distribution is unavailable offline and network access is unavailable.
- G-B10 and G-B11 remain BLOCKED; no server, production data, APK, GitHub, or Drive action occurred. Delivery remains WIP_NOT_RELEASE_READY.

## Verto sync repair B10-V01 — durable scoped inbox; acceptance blocked — 2026-09-10
- Implemented B10 locally at the user's explicit request, starting from the verified B09-V02 product tree (1,851/1,851 entries match). This permits implementation, not a waiver of G-B09/G-B10 or production activation.
- Split complete-group receipt/manifest/cursor CAS from atomic per-group business apply. Added touched-key/dependency metadata, six explicit states, exact replay checks, derived DTO child protection, and covered-prefix applied checkpoints.
- Added Room98→99 migration preserving all event columns, durable apply generations and post-commit scoped WorkManager continuations; unchanged waits are not successful syncs or fast retry loops. Known future outbox retries are preserved while Inbox waits.
- Added first-group1001 support, shared soft1000 budgets, canonical UTF-8 SHA-256/2MiB groups, per-scope64MiB+2MiB admission and real free-space/SQLiteFull safeguards. Stored groups remain applicable before a new page is requested.
- Added 24 JVM test methods and 14 Inbox Room methods, plus one financial late-original test (financial Room total34). Ran 15 native SQLite tests and 12 actual policy methods with an assertion shim; compiled selected actual sources/test signatures using explicit collaborator stubs. None is Android/Room/Hilt/JSON runtime acceptance.
- Re-ran B09 612 source/SQLite-model checks, 347 Kotlin mapping/Money assertions and 3 semantic identity checks (codec stubbed), plus B06 static gates. G-B09/G-B10 and all Txx remain unclosed.
- Bound the client to the explicit B08 `verto_pull_sync_changes_v2` wire contract without legacy fallback; no server function/migration was deployed or verified. Real Gradle8.9/Android/KSP/schema99 export and B08/B20 round trip remain blocked. No APK or B11 work.

## Verto sync repair B09-V02 — shared payment-write false conflict repaired; runtime gate still blocked — 2026-09-10
- Resumed the uploaded B09-V01 tree at its explicit B09.01 NEXT_ACTION; all 1,851 prior product-manifest entries matched. No B10 work or server change.
- Reproduced a receive-side false conflict: InvoiceVoidCoordinator emits distinct payment reversals with the same request/write ID, while the validator incorrectly required unique PAYMENT businessIdentity. Preserve paymentId/hash uniqueness, permit shared payment write correlation, and retain non-payment business-key uniqueness.
- Added three JVM and two Room regression methods for shared reversals, replay, duplicate identities and immutable-content protection (totals: 12 JVM / 33 Room; NOT_RUN).
- Re-ran 347 actual Kotlin mapping/Money assertions and three actual semantic-validator identity checks (codec stubbed), plus 612 source/SQLite-model checks and B06 schema/producer static gates. The prior regression failed before the fix and passed afterwards.
- Updated canonical idempotency documentation, Backlog and evidence. DTOs, golden fixture, schema98, frozen packets, domain rules and external services are unchanged. Gradle8.9/Android runtime prerequisites remain missing; G-B09 remains BLOCKED and NEXT remains B09.01 validation, not B10.
- Documentation baseline comparison: existing gate failures remain; the two new evidence documents are inventoried. No full quality or release admission claimed.

## Verto sync repair B09-V01 — full financial remote materializer implemented; validation blocked — 2026-09-10
- Replaced the INVOICE/PAYMENT FinancialInbox-only receive route with `FinancialMaterializerV2`: strict v2 DTO/envelope/scope/hash/version validation; proven party/inventory references; all nine financial tables and 153 DTO fields.
- Added lossless shared producer/receiver mappers, ABORT inserts, selective mutable updates, immutable equality, explicit child tombstones, return/purchase-match protection, and dependency-ordered reversals. Minor values remain Long; compatibility doubles are one-way projections.
- Deferred owner-effect verification and financial applied authority until all group business rows exist; all inbox APPLIED markers and opaque cursor/checkpoint commit in the same Room transaction. No local cash/stock/commission producer runs during financial REMOTE_APPLY. Durable receive/apply splitting remains B10.
- Added 31 real Room instrumentation methods (including 13 fault-injection boundaries through the actual Pull Engine), nine JVM contract tests, and a shared golden fixture. They are supplied but NOT_RUN because Gradle/Android are unavailable here.
- Passed 612 source/real-schema SQLite checks and 347 actual Kotlin mapping/Money assertions. The SQLite projection is a Python model; the selected Kotlin signature smoke uses explicit collaborator stubs. Neither is a Room/Gradle acceptance result. B06 schema/producer static gates remain passing.
- G-B09 remains BLOCKED; next action is B09.01 validation on an equipped build/device environment. B07/B08/server activation, old-event repair, two-device tests and release remain unclaimed. Embedded contract and Room schema 98 are unchanged.

## Verto sync repair B06-V01 — complete v2 snapshots and producer capture — 2026-09-10
- Added the complete typed v2 financial, inventory, client-credit, cash, expense, purchase, batch, receipt, and envelope contract plus a 40-definition JSON Schema and fail-closed validators.
- Replaced summary financial events with immutable full aggregate snapshots, deterministic semantic hashes, explicit tombstones/effect references, and the unambiguous applied financial base version.
- Moved financial, inventory, cash/reconciliation, expense, client-credit, and purchase child capture into their producer transactions; packet generations, pending references, and original batch identity are frozen before commit.
- Removed business-database access from owner310 prepare so retries can only reuse persisted payload bytes; private attachment URIs and inventory image URIs remain outside the wire contract.
- Passed the schema and production-wiring gates, 195 JVM tests, two Room atomic commit/rollback tests on Pixel_8 AVD, and whole-app debug Kotlin compilation. T09/T12/T19/T20 remain NOT_RUN until their full server/authoritative-apply scenarios.

## Verto sync repair B05-V01 — frozen requests, version chains, and scoped ACK — 2026-09-10
- Added transaction-bound immutable mutation packets, per-protected-key generations/references, original-owner batch manifests, and the additive Room 97→98 lease-scope migration.
- Added exact UTF-8 frozen member/batch text with SHA-256 verification, predecessor receipt-derived base versions, 120-second leases with 30-second renewal, and lease token/epoch plus receipt/hash acknowledgement CAS.
- Added fail-closed handling for historical requests: retry only verified stored bytes, apply only a matching receipt, otherwise `OUTCOME_UNKNOWN`; authoritative pull echoes now require matching content rather than origin ID alone.
- Passed 68 sync JVM tests, 27 database JVM tests, 12 focused Android/Room tests, golden wire/hash checks, and whole-app debug Kotlin compilation. T08/T15/T17/T23/T39 remain NOT_RUN until their full later-stage scenarios.

## Verto sync repair B04-V01 — unified ownership and pending protection — 2026-09-10
- Registered one explicit source owner for all 35 sync aggregates and each specialized queue, with fail-closed handling for unknown or organization-unproven sources.
- Added transaction-bound content references and one pending-protection service shared by Pull, Bootstrap staging, M03, health, logout, and organization switching.
- Preserved every non-terminal state, dependent invoice/payment/inventory/attachment content, and legacy Party rows without tenant evidence; disabled blind Bootstrap pruning until its B13 content-aware implementation.
- Passed 64 sync JVM tests, 27 database JVM tests, five focused Android/Room tests, and whole-app debug Kotlin compilation. T10/T33 remain NOT_RUN until their full repair/Bootstrap scenarios.

## Verto sync repair B03-V01 — Room schema 97 and sync authority stores — 2026-09-10
- Added the additive Room 96→97 migration, generated schema, sync version/generation/packet/reference/batch/inbox/evidence/snapshot stores, cursor and attachment lifecycle fields, expense history, and fixed-point cash fields.
- Added transactional DAO invariants for observed versus applied authority, stored local generations, immutable snapshots, and sealed zero-based batches.
- Passed 27 JVM tests and five focused Android/Room tests on a local Pixel_8 AVD, including non-empty migration preservation and fail-closed invalid legacy money. No server or user-device data was changed.

## Verto sync repair B02-V02 — device/signing recheck — 2026-09-10
- Verified the connected device's installed Verto 1.0 APK and confirmed its signing certificate matches the local APK without accessing signing keys.
- Confirmed that the installed release is non-debuggable, Android backup is disabled, root is unavailable, and the live authenticated backup archive is empty; a consistent Room/WAL/attachment backup therefore remains blocked without bypassing device security.
- Queried only server metadata and aggregate archive count. No production write, tenant payload read, reinstall, root attempt, or device data mutation occurred.

## Verto sync repair B02-V01 — server catalog captured; environment gates blocked — 2026-09-10
- Exported the live unified-sync RPC definitions and hashes plus relevant table columns, constraints, indexes, triggers, RLS, grants, owners, contract, coverage registries, and the complete 214-entry migration name/version history using read-only catalog queries.
- Confirmed live/source drift: two post-M08 live migrations are absent locally, historical clean-rebuild SQL remains incomplete, and two RPC names still referenced by `SyncProtocolV2Remote` are absent live.
- Documented the organization/principal/epoch fence and rollback procedure and verified the local APK's v2 signature without accessing signing secrets.
- B02 remains blocked because no consistent device Room/WAL/attachment backup, installed package, isolated PostgreSQL target, sanitized fixture, or complete clean-rebuild baseline is available. No product/server/data change or T01–T50 PASS was made.

## Verto sync repair B01-V01 — source preparation blocked — 2026-09-10
- Added the B01 evidence layout, candidate-tree fingerprint, project command/environment inventory, and source-difference report without changing product Kotlin, Room, SQL, or Supabase code.
- The required `Verto-425.zip` was not available and the candidate workspace is not a Git repository; source acceptance and repair-branch creation remain blocked rather than being inferred from the visible files.
- Backlog/contract integrity checks passed. The documentation gate itself ran via `bash` but failed on existing governance/inventory drift; its integrated entry point is blocked because the supplied script lacks its executable bit.
- No T01–T50 test, server action, data action, deployment, or release build was performed.

## Verto sync repair B01-V02 — source tree accepted — 2026-09-10
- Recorded the user's designation of the current directory as the already-extracted Verto-425 source and accepted its pre-execution deterministic tree fingerprint as the baseline identity.
- Closed B01 in tree-hash mode without claiming that the unavailable ZIP-container SHA-256 was computed.
- Verified all source-packager required paths and 23/24 contract reference paths; `LegacySyncV2IntentRepairCoordinator.kt` is absent and remains an explicit source-layout difference for its owning implementation session.
- No product code, server definition, database data, or T01–T50 result changed.

## M04 — Sync V2 producer cutover — 2026-09-09
- Enforced Room-transaction ownership for every generic V2 intent and attachment enqueue.
- Preserved native JSON scalar types instead of stringifying numbers/booleans.
- Fixed CLIENT_CREDIT atomicity and made TEAM_OBSERVATION always capture a durable V2 intent.
- Preserved stronger financial, inventory, Party-role, and Optimal outboxes without duplicate generic copies.
- M04 deterministic gate: 16/16 PASS. Gradle compile/unit execution is BLOCKED_ENVIRONMENT because Gradle 8.9 is not cached and services.gradle.org is unreachable.

## v388 — Customer Party V2 closeout
- Reduced the authoritative customer taxonomy to exactly six segments: INDIVIDUAL, COMPANY, WORKSHOP_OWNER, MARKETER, TRADER, DISTRIBUTOR; COMPANY represents company/institution.
- Removed COMPETITOR as a customer segment. Competitor behavior is now derived from the same party having both active CUSTOMER and SUPPLIER roles; Max uses that role-derived contract.
- Replaced normal customer/supplier permanent-delete UX with role-specific archive, preserving the other role for dual-role parties.
- Connected CustomerDashboard to CustomerProfile V2 and renders semantic profile fields instead of overloaded legacy identity fields.
- Added Room 94→95 normalization and applied the matching Supabase v388 migration. Live server data now has zero invalid customer segments, zero client_types column, and no Party/Profile organization mismatches.
- Customer closeout gate: 52/52 PASS; Room SQL isolation PASS; migration 94→95 PASS; pure Kotlin Party domain compile PASS. Full Gradle compile is BLOCKED_ENVIRONMENT because Gradle 8.9 is not cached and services.gradle.org is unreachable.

## v380 — Price-list template and inline-price UX repair
- Editing an existing price-list template now opens only that template's current items; the full inventory is available only through an explicit "إضافة من المخزون" action.
- Adding from inventory is a dedicated sub-flow: already-selected template items are excluded, new items are added directly, and the user returns to the template item list before saving.
- Choosing either "المتوفر فقط" or "إضافة الكل" now closes the templates dialog immediately after adding the template to the draft.
- Draft item prices are now directly editable inline; every valid positive value updates the draft automatically without an edit/save dialog.
- Removed the obsolete price edit dialog, edit/reset controls, reset ViewModel method, and unused related strings.
- Static source/XML verification passed; Gradle compilation is environment-blocked because Gradle 8.9 is not cached and network download is unavailable.

## v378 — Optimal live company selection repair
- Made the Optimal join-code picker server-authoritative for company eligibility instead of trusting stale Room rows.
- Remote refresh now returns the exact current Verto COMPANY client IDs for the active organization after merging the server snapshot locally.
- The join-code screen stays loading until that authoritative snapshot succeeds, then filters Room projections against the server IDs; removed/deleted/stale local companies can no longer be selected.
- Kept the existing company-specific RPC (`verto_issue_optimal_registration_code`) and server-side validation unchanged.
- Full Gradle compile remains environment-blocked because Gradle 8.9 is not cached and network download is unavailable.

## v376 — Price-list templates and legacy cutover
- Rebuilt price lists around reusable inventory-linked templates: direct items, multiple templates, available-only/all, deduplication, favorites, edit/delete, and temporary quote price overrides.
- Replaced copied price/name/stock persistence with inventory IDs only; inventory is now the source of truth.
- Replaced the legacy price-list PDF themes with one flat professional A4 export; template names never appear in customer PDF.
- Removed legacy direct price-list sync, pending-deletion queue, old runtime entities, and old print-template preferences.
- Added Room 90→91 and Supabase v376 cutover migrations. Live Supabase was not changed in this source patch.
- Static verification passed; full Gradle build remains environment-blocked because Gradle 8.9 is not cached and network download is unavailable.

## v371 — Invoice legacy cleanup
- Removed unreachable shipment-editor UI, obsolete shipment use cases, dead toggles, dead DAO queries, unused helper/state, and orphaned shipment-dialog strings.
- Preserved the active `payment.purchase-shipment.v1` stable contract and historical evidence.
- Purchase invoice v370 static gate remains PASS; v371 legacy cleanup gate PASS.
- Full Gradle compile remains environment-blocked because Gradle 8.9 is not cached.

## v368 — Sale credit save / diagnostic repair
- Fixed the active organization currency from blank to `SDG` on Supabase; blank functional currency was the actual invoice-save blocker.
- Added backward-compatible SDG fallback for pre-F246 migrated organizations whose cached currency is blank.
- Upgraded unknown-error diagnostics to include redacted operation, message, and sanitized stack trace in logcat/local crash logs.
- No invoice totals, settlement rules, stock rules, or credit policy changed.


## v363 — Error presentation closeout
- Added shared structured error presentation policy and design-system renderer.
- Added localized shared error copy and recovery actions.
- Migrated Home Search to typed error presentation with retry.
- Generic IOException now remains unknown instead of being mislabeled.
- Added anti-regression gate for raw throwable text and message heuristics.
---
status: canonical
scope: system
owner: "release-governance"
last_verified_against: v321
---
# Changelog

- v321: removed the 18 currently admitted architecture findings (`VARCH-003=4`, `VARCH-005=2`, `VARCH-012=12`), decoupled Optimal from five provider implementation modules via app composition bridges, repaired Inventory/Party layer boundaries, classified Dashboard API edges as Public contracts, and tightened the forward debt Ratchet; final admission is `FINAL_ADMISSION_BLOCKED_ENVIRONMENT` because Gradle 8.9 could not be resolved in the execution environment.
- v320: activated Architecture Guard v2, deterministic complexity Ratchet, dependency/data/public-API admission, session Change Contracts, unified quality orchestration, stale-evidence protection, guard regression tests, and fail-closed source-of-truth packaging; production/runtime source remains unchanged.
- v319: established source-bound architecture contracts, 18 feature manifests, dependency/data-ownership registries, legacy-debt Ratchet baseline, and ADR governance; no production behavior changed.

This changelog starts with the v315 documentation foundation. Earlier release/session history is preserved in `docs/archive/` and is not reconstructed here without verification.

## Unreleased

### v362 Error migration to structured classification — 2026-08-24

- Migrated user-visible exception paths in organization, dashboard education, invoices, expenses, parties, commissions, Optimal integration, payments, shipment/logistics, and sync away from direct `Throwable.message`.
- Replaced invoice-save text-token guessing with structural `ErrorClassifier` handling for conflict and connectivity failures.
- Replaced logistics purchase-plan and document text parsing with stable `BusinessRuleFailureException` codes produced at the domain/data boundary.
- Replaced the `sync_skipped` string sentinel with a typed `SYNC_SKIPPED` business failure.
- Reworked Optimal outbox failure persistence to store stable diagnostic codes rather than arbitrary exception/server text; user copy now maps exact codes only.
- Preserved explicit business-validation copy and existing feature workflows; no database schema, navigation, invoice semantics, inventory semantics, or Supabase writes changed.
- Session verification: v361 invariants `13/13 PASS`, v362 migration gate `25/25 PASS`, pure Kotlin classifier/Optimal compile `PASS`; parser-level Kotlin scan reports no syntax diagnostics.
- Gradle verification remains environment-blocked because Gradle 8.9 is not cached and `services.gradle.org` is unreachable.
- Error presentation components, resource consolidation, retry/action UX, and final anti-regression UI gate remain Session 363.

### v361 Unified error-classification foundation — 2026-08-24

- Added a presentation-agnostic `AppFailure` taxonomy in `core:common` covering network, remote/server, device/local-storage, business, security, conflict, rate limiting, and unknown failures.
- Added structured `RemoteFailureMetadata` / `RemoteFailureException` so server status/code/target can be classified without parsing server text.
- Added `ErrorClassifier` as the single classification source; coroutine cancellation is rethrown as control flow.
- Fixed two misleading legacy heuristics in `ErrorHumanizer`: Arabic text is no longer trusted as user-safe, and generic `IOException` is no longer labeled as an internet failure.
- Kept `ErrorHumanizer` as a compatibility adapter for current callers while delegating classification to the new core contract.
- Added focused classifier regression tests plus Session 361 static verification. New core sources compile and 10 direct classification scenarios pass under local `kotlinc`.
- Gradle verification remains environment-blocked because Gradle 8.9 is not cached and `services.gradle.org` is unreachable.
- Feature-by-feature migration of remaining raw `Throwable.message` UI paths is explicitly deferred to Session 362.

### v360 Home recent activity completion — 2026-08-24

- Finalized recent activity as the last Home section using the existing seven-day window with a strict 30-event cap, newest first.
- Made the recent-activity card show up to five 48dp rows before internal scrolling; shorter feeds shrink instead of reserving empty rows.
- Added bottom protection so the final activity rows are not obscured by the Home FAB.
- Preserved the v359 content order and existing visual language; broader visual redesign is intentionally deferred until an approved screen reference is supplied.
- Added Session 360 static verification (`10/10 PASS`). Gradle tests remain environment-blocked because Gradle 8.9 is not cached and the execution environment cannot resolve `services.gradle.org`.
- No Room schema, Supabase schema, navigation contract, or feature behavior outside Home recent activity changed.

### v359 Home information capture & education — 2026-08-24

- Replaced the single generic observation capture with three explicit categories: idea, market information, and complaint; pages support horizontal navigation and idle auto-rotation every five minutes.
- Kept submit visually prominent while preserving blank-input validation; successful local capture now thanks the user and remains offline-first.
- Persisted observation category end-to-end through Room, sync DTOs, Supabase, and the manager review screen.
- Added Room migration 87→88 plus migration instrumentation coverage; the generated Room 88 schema export is environment-blocked because Gradle 8.9 is unavailable.
- Updated the education detail flow so the card shows the summary, while “read more” opens the topic title and full content without repeating the summary.
- Preserved the agreed Home order: pending actions → information capture → education → recent activity; recent-activity behavior remains deferred to v360.
- Applied and verified the `v359_team_observation_categories` migration on the Verto Supabase project.

### v351 Invoice adjustments, referrals & post-posting corrections — 2026-08-24

- Extended the progressive sale editor to post-save sale edits; replaced the old sale-edit path while preserving live purchase/international flows.
- Added invoice-level discount as first-class persisted data; item prices remain unchanged.
- Added explicit commission beneficiary/source attribution: buyer marketer/workshop by default, or optional marketer/workshop referrer for ordinary/company buyers.
- Added linked sale-return UI backed by the existing immutable return use case.
- Allowed controlled POSTED sale corrections by reversing/reposting inventory effects, preserving posted identity/timestamps, incrementing lifecycle version, and writing audit evidence.
- Added Room migration 84→85 and a Supabase migration for discount/referral commission truth; the Supabase migration is packaged but was not applied to production in this session.
- Removed the replaced legacy pre-save commission UI and its dead state/dialog paths.
- `INVOICE_351_STATIC_GATE=PASS`; Android compile remains environment-blocked because Gradle 8.9 is unavailable and the execution environment cannot reach services.gradle.org.

### v350 Invoice details tabs & collection UX — 2026-08-23

- Replaced the legacy invoice-detail layout with a fixed invoice header/financial summary and three direct tabs: items, payments, communication.
- Exposed the existing real payment history and added partial/full settlement entry; full settlement pre-fills the current remaining balance.
- Added live CASH / TRANSFER / CHECK method selection to payment entry and removed the old non-functional thank-you placeholder dialog.
- Added invoice/reminder/thank-you WhatsApp-open history through the existing audit log, explicitly recording OPENED rather than claiming message delivery.
- Fixed the thank-you remaining-balance double subtraction.
- Removed replaced invoice-detail components and the dead invoice-screen add-debt callback.
- `INVOICE_350_STATIC_GATE=PASS`; Android build is not claimed because the supplied source has no `gradle-wrapper.jar`.

### v349 Sale invoice progressive editor — 2026-08-23

- Replaced the new local-sale entry experience with a sale-only progressive editor: items first, no upfront cash/credit choice.
- Added direct walk-in cash save; selected customers receive paid/remaining settlement and credit decision only when a balance remains.
- Added focused item-entry flow (search → quantity → Done → search), compact line list with edit/delete + Undo, and fixed count/total/save bar.
- Extended Party-to-Payment decision reads with current outstanding/overdue balances for the credit gate without changing server schema.
- Preserved the legacy editor only for still-live purchase/international/edit paths; it is no longer reachable for new local sale creation.
- `INVOICE_349_STATIC_GATE=PASS`; Gradle build is environment-blocked because Gradle 8.9 cannot be downloaded (`UnknownHostException: services.gradle.org`).

### v348 Party Intelligence final hardening — 2026-08-23

- Hardened credit-save authorization so incomplete evidence can never auto-approve credit at the workflow boundary.
- Made supplier intelligence strictly advisory: recommendation-flow failures no longer block purchase-order creation.
- Added Session 348 regression/runtime/quality gates while preserving all 344–347 gates and the legacy Party gate.
- No Room or Supabase schema migration added in 348; Gradle 8.9 remains unavailable in the execution environment.

### v336 Cash convergence & expense dependency repair — 2026-08-23

- Reconciled server-authoritative cash movement balance projections without weakening immutable cash intent.
- Added explicit parent dependency metadata for expense decrease/VOID cash refunds while preserving existing expense cash ordering.
- Added 34 host behavioral checks, M1-M14 executable behavioral mutation evidence, and 40 focused Kotlin tests.
- Preserved Room schema 83 and introduced no server SQL migration.
- Static quality gates pass; Gradle/device runtime remains environment-blocked, so production cutover is not authorized.


### v317 Documentation Archive & Deduplication — 2026-08-22

- Revalidated all 20 residual root Historical documents; all remain dependency-proven compatibility paths, so no unsafe root move was performed.
- Recorded the retained F252 exact-byte duplicate pair without deleting either historical path.
- Converted `docs/design-system/DESIGN-SYSTEM-CONTRACT.md` into a deprecated compatibility-only document while preserving `DESIGN_SYSTEM_CONTRACT.md` as the sole Canonical authority.
- Reconciled archive manifest (including removal of one stale manifest-only v314 build-report claim), inventory and Canonical ownership; added v317 verification/report evidence.
- No non-Markdown/runtime behavior changed; Android build, tests and server runtime were not run for this documentation-only session.

### v316 Documentation Content — 2026-08-22

- Reconstructed current architecture, module map, data flow, sync architecture and security boundaries from v315 source.
- Reconstructed API/server documentation covering 48 production RPCs, 71 named direct PostgREST tables, Auth, errors and idempotency/retry status without inventing missing server semantics.
- Added 16/16 production-critical feature documents with evidence-backed ownership/invariants.
- Updated canonical navigation/map/inventory and added v316 verification/report artifacts.
- No Kotlin, Gradle, SQL, Room schema, migration or runtime behavior changed. Build/tests/server runtime were not run for this documentation-only session.

## v315 Documentation Foundation — 2026-08-22

- Added repository entry points: `README.md`, `CONTRIBUTING.md`, and `CHANGELOG.md`.
- Rebuilt documentation navigation around domains and Canonical ownership.
- Added exhaustive Markdown inventory, Canonical responsibility mapping, archive manifest, and documentation verification evidence.
- Archived safely movable historical reports, verification outputs, session contracts, and superseded plans without changing their bytes.
- Retained executable path-coupled historical evidence at legacy paths.
- Preserved all pre-existing non-Markdown files byte-for-byte.
- No Android build, unit, integration, instrumentation, or server runtime PASS is claimed by this documentation-only session.

## v345 — Party Customer Decision Engine
- Added explainable, fail-closed customer credit decisions from actual settlement history.
- Added payment minor-unit/currency provenance to Party financial models.
- Added customer-specific repurchase prediction and next-action signal.
- Added Session 345 static gate and regression scenarios.

## v381 — Optimal company visibility repair backport
- Backported the secure Optimal company-selection refresh onto v380.
- Removed stale cached organization filtering from live COMPANY discovery; server RLS is authoritative.
- Replaced forbidden direct `optimal_verto_links` reads with `verto_list_optimal_links()` RPC.
- Repairs the cached organization id when live tenant data proves a different organization.
