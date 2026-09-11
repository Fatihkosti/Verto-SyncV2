# VERTO SESSION 340 VERIFICATION

## Final verdict

`PASS_STATIC_RUNTIME_BLOCKED`

Session 340 is implemented inside the frozen Home Search + Header scope. The dedicated static verifier passes **60/60**. Three actual-source Kotlin harnesses pass for Header policy, canonical click authorization, and the bounded/failure-isolated search aggregator. Full Gradle unit/lint/assemble execution is **BLOCKED_ENVIRONMENT** before task execution because Gradle Wrapper 8.9 must be downloaded from `services.gradle.org` and DNS/network access is unavailable. No Gradle/Android/Room runtime PASS is claimed.

---

## Baseline

| Item | Evidence |
|---|---|
| Input filename | `Verto-v339-activity-feed-engineering-repaired.zip` |
| SHA-256 | `73b0da3842ebccec3b96e049bddfa3962d43224b45a502adcf696c256fd0add0` |
| Archive entries | `3262` |
| Extracted files | `2286` |
| Kotlin files | `1405` |
| Room schema version | `83` |

The baseline SHA matches the Session 340 contract exactly.

---

## Scope

### Modified files — 22

```text
app/src/main/kotlin/com/verto/app/ui/screens/home/HomeHeader.kt
app/src/main/kotlin/com/verto/app/ui/screens/home/HomeHeaderPolicy.kt
app/src/main/kotlin/com/verto/app/ui/screens/home/HomeScreenContent.kt
app/src/main/kotlin/com/verto/app/ui/screens/home/search/HomeSearchGridLayout.kt
app/src/main/kotlin/com/verto/app/ui/screens/home/search/HomeSearchResultSections.kt
app/src/main/kotlin/com/verto/app/ui/screens/home/search/HomeSearchScreen.kt
app/src/main/kotlin/com/verto/app/ui/screens/home/search/HomeSearchSections.kt
app/src/main/kotlin/com/verto/app/ui/screens/home/search/HomeSearchViewModel.kt
data/database/src/main/kotlin/com/verto/app/data/local/dao/ClientDao.kt
data/database/src/main/kotlin/com/verto/app/data/local/dao/InventoryCatalogReadDao.kt
data/database/src/main/kotlin/com/verto/app/data/local/dao/InvoiceReadDao.kt
data/database/src/main/kotlin/com/verto/app/data/local/dao/PaymentDao.kt
feature/dashboard/api/src/main/kotlin/com/verto/feature/dashboard/api/HomeContracts.kt
feature/dashboard/src/main/kotlin/com/verto/app/feature/dashboard/application/search/UnifiedHomeSearchUseCase.kt
feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/application/search/InventoryHomeSearchProvider.kt
feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/data/search/RoomInventoryHomeSearchSource.kt
feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/search/InvoiceHomeSearchProvider.kt
feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/data/search/RoomInvoiceHomeSearchSource.kt
feature/party/src/main/kotlin/com/verto/app/feature/party/application/search/PartyHomeSearchProvider.kt
feature/party/src/main/kotlin/com/verto/app/feature/party/data/search/RoomPartyHomeSearchSource.kt
feature/payment/src/main/kotlin/com/verto/app/feature/payment/application/search/PaymentHomeSearchProvider.kt
feature/payment/src/main/kotlin/com/verto/app/feature/payment/data/search/RoomPaymentHomeSearchSource.kt
```

### Added files — 10

```text
app/src/main/kotlin/com/verto/app/ui/screens/home/search/HomeSearchClickPolicy.kt
app/src/test/kotlin/com/verto/app/ui/screens/home/HomeHeaderPolicy340Test.kt
app/src/test/kotlin/com/verto/app/ui/screens/home/search/HomeSearchClickPolicy340Test.kt
feature/dashboard/src/test/kotlin/com/verto/app/feature/dashboard/application/search/HomeSearchHardening340Test.kt
feature/inventory/src/test/kotlin/com/verto/app/feature/inventory/application/search/InventoryHomeSearchProvider340Test.kt
feature/invoice/src/test/kotlin/com/verto/app/feature/invoice/application/search/InvoiceHomeSearchProvider340Test.kt
feature/party/src/test/kotlin/com/verto/app/feature/party/application/search/PartyHomeSearchProvider340Test.kt
feature/payment/src/test/kotlin/com/verto/app/feature/payment/application/search/PaymentHomeSearchProvider340Test.kt
tools/quality/session340_verify.py
VERTO_SESSION_340_VERIFICATION.md
```

### Deleted files

`None`

### Parallel-session classification

```text
340-owned     = Search/Header providers, read queries, policies, tests, verifier
shared-minimal = HomeContracts.kt, HomeScreenContent.kt and Search callback plumbing only
unrelated      = 0
```

No Pending Actions, Quick Actions, Activity Feed, Drawer, FAB, Sync, migration, server SQL, or unrelated feature file changed.

---

## Search before / after

| Problem | Before 340 | After 340 | Evidence |
|---|---|---|---|
| Tenant scope | several business providers relied on active session above DAO | party/invoice/payment reads are SQL organization-scoped; inventory fails closed through proven organization-owned relations | DAO source + static verifier |
| Permission query gate | invoice/payment could reach source before category permission decision | provider returns before source when no required permission; category flags pushed into SQL | provider tests/static verifier |
| Click authorization | UI passed visible `destination` directly | click sends stable result/action identity; current context is reread; search is rerun; only canonical current result/action can navigate | `HomeSearchClickPolicy340Test` + Kotlin harness |
| Cancellation | ViewModel `runCatching` could turn cancellation into generic error | `CancellationException` explicitly rethrown in ViewModel and aggregator | source/static + Kotlin harness |
| Provider failure | failure isolated but silent | failure isolated + PII-free in-process diagnostics | source/static + Kotlin harness |
| Provider work | merge bounded; provider-local defense incomplete | provider query limit remains bounded and SQL has deterministic `ORDER BY ... LIMIT` | DAO/static verifier |
| Destination | UI-carried destination trusted | current canonical destination validated by `HomeSearchDestinationResolver`; malformed/unknown fails closed | click policy tests |
| Result identity | `kind + key` | `providerId + kind + key` | source/test |

### Search constants preserved

```text
Debounce                 = 180 ms
MAX_RESULTS_PER_PROVIDER = 24
MAX_RESULTS_PER_KIND     = 6
MAX_TOTAL_RESULTS        = 24
Search transport         = local-first / Room-backed
```

### Search provider evidence

| Provider | Required pre-gate | Tenant ownership path | Query limit | Ordering |
|---|---|---|---|---|
| Party | `CLIENTS_VIEW` | active `party_roles.organization_id`; invoice/payment balance subqueries scoped to same org | caller <=24 | exact, normalized name, createdAt, id |
| Invoice | `SALES_VIEW` and/or `PURCHASES_VIEW` | `invoices.organization_id` | caller <=24 | exact invoice number, createdAt, id |
| Payment | sale/purchase view permission before query | `payments.invoiceId → invoices.organization_id` | caller <=24 | exact/name quality, paidAt, id |
| Inventory | `INVENTORY_VIEW` | proven org-owned movement/cost/invoice/shipment relation | caller <=24 | exact name/part/barcode, name, updatedAt, id |
| Screens/Actions | catalog entry permission | no Room tenant rows | caller <=24 | existing deterministic catalog ranking |

### Inventory ownership limitation

`inventory_items` itself has no `organization_id` in Room schema 83. Session 340 therefore does not infer ownership from the active session. Search includes an inventory row only when current-organization ownership is provable through an existing organization-owned movement, cost revision, invoice line/invoice, or shipment line. A brand-new/service item with no such relation can therefore be omitted. Fixing this completely requires a schema/data ownership model and is deferred because Session 340 forbids migrations.

`FOLLOW_UP_SEARCH_INVENTORY_OWNERSHIP_SCHEMA`

### Query escaping / injection

Search providers use bound Room parameters. Search normalization reduces the active prefix/identifier keys to normalized letters/digits; no raw user SQL concatenation was introduced. Payment `LIKE` receives the normalized identifier key, not raw query text.

---

## Header before / after

| Aspect | Before 340 | After 340 |
|---|---|---|
| Morning messages | 1 | 30 |
| Day messages | 1 | 30 |
| Evening messages | 1 | 30 |
| Rotation | none | deterministic 30-day local-date cycle |
| Persistence | none | none |
| Random | none | none |
| Boundary update | composition-dependent `Calendar.getInstance()` | lifecycle-aware next-boundary scheduler |
| Polling | none/implicit | one wake-up at next 00:00/04:00/12:00/16:00 boundary |
| Network/DB | none | none |

### Message catalog evidence

```text
Morning count  = 30
Day count      = 30
Evening count  = 30
Morning unique = 30
Day unique     = 30
Evening unique = 30
All 90 exact phrases are cross-period unique
Longest Morning message = 47 Arabic characters
Longest Day message     = 48 Arabic characters
Longest Evening message = 47 Arabic characters
```

All 90 messages were manually reviewed for period fit, concise operational tone, and avoidance of religious/political content, employee blame, unrealistic promises, or runtime personalization.

### Rotation / clock contract

```text
index = floorMod(localDate.toEpochDay(), 30)
Morning = 04:00–11:59
Day     = 12:00–15:59
Evening = 16:00–03:59
```

At midnight the period remains Evening while the local-date index advances. On lifecycle resume the current local date/time is recomputed immediately. No second/minute/global ticker was added.

---

## Tests authored

```text
HomeHeaderPolicy340Test.kt
- 03:59 / 04:00 / 11:59 / 12:00 / 15:59 / 16:00 / 23:59 / 00:00 boundaries
- 30/30/30 catalog size + uniqueness + <=55 chars
- 30 consecutive dates unique per period
- date + 30 recurrence
- same-date period messages differ
- next-boundary scheduler examples
- midnight index transition
- blank-name fallback

HomeSearchClickPolicy340Test.kt
- canonical current result accepted
- stale/unknown result rejected
- revoked result permission rejected
- forged action rejected
- revoked action permission rejected
- malformed destination rejected
- provider identity collision rejected

HomeSearchHardening340Test.kt
- 4 providers × 1,000 rows remain bounded to 24 total / 6 per kind
- provider identity stamping
- provider failure isolation + diagnostics
- CancellationException propagation
- merge-level permission defense
- duplicate provider ID rejection

Party/Invoice/Payment/Inventory provider tests
- permission denied => source query count 0
- current organization context propagated
- wrong organization rejected where applicable
- sale/purchase category gates propagated before query
- requested limit propagated
```

---

## Actual-source Kotlin harness evidence

### Header policy

Compiled actual `HomeHeaderPolicy.kt` with a JVM harness.

```text
SESSION340_HEADER_HARNESS_PASS
```

Verified real 30-message catalog, 30-day recurrence, local period boundaries and midnight scheduling.

### Canonical click authorization

Compiled actual `HomeContracts.kt` + `HomeSearchClickPolicy.kt` with a JVM harness.

```text
SESSION340_CLICK_POLICY_HARNESS_PASS
```

Verified stable provider/type/entity identity, canonical result acceptance, stale result rejection and permission revocation rejection.

### Search aggregator

Compiled actual:

```text
HomeContracts.kt
SearchTextNormalizer.kt
HomeSearchProviderRegistry.kt
UnifiedHomeSearchUseCase.kt
```

and executed 4 providers × 1,000 results plus failure/cancellation probes.

```text
SESSION340_SEARCH_HARNESS_PASS size=24
```

This is real JVM evidence for the pure project source. It is not represented as Android/Room runtime evidence.

---

## Static verifier

Command:

```bash
python tools/quality/session340_verify.py . /mnt/data/Verto-v339-activity-feed-engineering-repaired.zip
```

Result:

```text
SESSION340_STATIC_VERIFICATION 60/60 PASS
```

It verifies baseline identity, 30/30/30 catalogs, message length/uniqueness, deterministic local-date rotation, boundary-only scheduling, no Random/DB/network/minute polling, debounce 180ms, total cap 24, parallel providers, cancellation propagation, PII-free provider diagnostics, canonical click reauthorization, fail-closed destinations, provider permission pre-gates, tenant DAO predicates, bounded deterministic SQL, required tests, Room schema 83, parallel scope, protected files, no migration/server/sync changes, and unchanged Search/Header visual signatures.

---

## Gradle gates

Attempted without bypasses:

```bash
./gradlew \
  :feature:dashboard:testDebugUnitTest \
  :feature:party:testDebugUnitTest \
  :feature:invoice:testDebugUnitTest \
  :feature:inventory:testDebugUnitTest \
  :feature:payment:testDebugUnitTest \
  :app:testDebugUnitTest \
  testDebugUnitTest lintDebug assembleDebug --stacktrace
```

Result before any Gradle task execution:

```text
BLOCKED_ENVIRONMENT
Downloading https://services.gradle.org/distributions/gradle-8.9-bin.zip
java.net.UnknownHostException: services.gradle.org
```

No `-x test`, `ignoreFailures`, `|| true`, disabled test, deleted test, or weakened assertion was used.

---

## Visual freeze

The verifier compares visual/layout-signature lines of the baseline and Session 340 Search/Header UI files. Result:

```text
Search/Header visual signature unchanged = PASS
```

Changes in UI files are limited to dynamic header state and authorization callback plumbing. Search card/grid/header structure, colors, typography, spacing, sizes, icons and existing visual layout are unchanged.

---

## Schema / migration / server / sync

```text
Room schema version before = 83
Room schema version after  = 83
New migration              = none
Entity schema change       = none
Server SQL change          = none
Sync protocol change       = none
```

---

## Runtime evidence

```text
Pure Kotlin actual-source harnesses = PASS
Android/Compose runtime             = BLOCKED_ENVIRONMENT
Room runtime query execution        = BLOCKED_ENVIRONMENT
Device/emulator typing measurement  = BLOCKED_ENVIRONMENT
Lifecycle/device boundary simulation = BLOCKED_ENVIRONMENT
```

No runtime-only PASS is claimed.

---

## Definition of Done

```text
[x] latest baseline SHA recorded
[x] no unrelated rollback
[x] Search visual UX unchanged
[x] Header visual UX unchanged
[x] Search remains local-first
[x] debounce preserved at 180ms
[x] result cap preserved at 24
[x] providers bounded
[x] tenant scope enforced where ownership is provable
[x] provider permissions checked before expensive queries
[x] merge permission gate preserved
[x] click-time permission rechecked
[x] click-time organization context re-read and canonical search rerun
[x] canonical current result used
[x] malformed/unknown destination rejected
[x] CancellationException preserved
[x] provider failure isolated
[x] provider failure observable without raw query/PII
[x] search tests added
[x] 30 Morning messages
[x] 30 Day messages
[x] 30 Evening messages
[x] all messages unique within each period
[x] deterministic local-date rotation
[x] no same-period repeat within 30 days
[x] correct 30-day recurrence
[x] correct Morning/Day/Evening boundaries
[x] midnight changes message index while staying Evening
[x] lifecycle resume recomputes header immediately
[x] no minute polling
[x] no network for header
[x] no DB persistence for greeting history
[x] Room schema unchanged
[x] no migration
[x] no server/sync changes
[x] static gates executed honestly: 60/60 PASS
[x] actual-source pure Kotlin harnesses executed successfully
[x] Gradle/runtime blocker documented honestly
```

---

## Final statement

Session 340 implementation is statically complete. Home Search is tenant-aware at provable ownership boundaries, permission-gated before business-source work, bounded, cancellation-correct, failure-isolated, canonically reauthorized on click, and fail-closed for invalid destinations. The Header now rotates through 30 Morning, 30 Day, and 30 Evening messages deterministically without repeating the same period message within 30 days, and updates only at local date/period boundaries or lifecycle resume.

Search remains local-first. The visual UX of Search and Header remains unchanged. No Room migration, server, sync, or unrelated feature redesign was introduced.

Runtime/full-Gradle verification is `BLOCKED_ENVIRONMENT` and is not claimed as PASS.
