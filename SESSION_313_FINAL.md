# SESSION_313_FINAL.md

## Verto Sync Modernization — Session 313

### Recovery, Safe Bootstrap/Full Resync, Cursor Expiry, Reconciliation & Privacy-Safe Observability

**نوع المستند:** عقد تنفيذ مستقل ونهائي  
**الجلسة:** 313  
**الحالة:** `PLAN ONLY — EXECUTABLE ON V312 STATIC-PASS BASELINE`  
**تاريخ الصياغة:** 2026-08-21  
**مصدر الكود المفحوص:** `Verto-v312-source-of-truth.zip`  
**SHA-256 للمصدر المفحوص:** `acab081179e77e2a37c7a2358c49ece22a6c995691cec7558d4a9c1a5baaeebd`  
**Archive entries:** `2664`  
**Production Kotlin files:** `1179`  
**عقد 312 المرجعي:** `SESSION_312_FINAL.md`  
**SHA-256 لعقد 312:** `3b2c829ae1b77b6717f646f9ef130c3c23c4af4904d3c036664d9bca182fd020`  
**الخطة الأم:** `VERTO_SYNC_MODERNIZATION_PLAN_v304-v314.md`  
**SHA-256 للخطة:** `a767087c5f1659dcd7c660c189542ef2d7774c0f647ff9321e9f53219a7dfd97`  
**Contract authority:** `verto-unified-sync / 1`  
**Room الحالي المثبت:** `80`  
**Room المستهدف في 313:** `81` — Migration `80→81` مطلوبة فقط لحالة recovery/bootstrap staging/health durability.  
**Supabase Kotlin BOM المثبت:** `3.0.2`  
**WorkManager المثبت:** `2.10.0`  
**Runtime V2 عند البداية:** `OFF`  
**Realtime runtime عند البداية:** `OFF`  
**313 acceptance basis:** الفحوص الساكنة/model verification إلزامية. Gradle/Android/PostgreSQL runtime ليست شرطًا لـ`PASS_STATIC` إذا البيئة غير متاحة أو التنفيذ runtime متجاوز صراحة. أي أمر runtime يتم تشغيله فعليًا ويفشل يبقى فشلًا حقيقيًا. 313 تتطلب Server migration additive واحدة لمعالجة اكتمال bootstrap snapshot/recovery surface؛ لا تعتبر مطبقة على PostgreSQL ما لم تُنفذ فعليًا وتنجح.

---

# 0. الحكم التنفيذي المختصر

313 ليست Cutover، وليست إزالة Legacy، وليست جلسة Fault Injection النهائية.

هي الجلسة التي تجعل V2 قادرًا على **استعادة نفسه** بدل أن يتوقف عند:

```text
BOOTSTRAP_REQUIRED / RECOVERY_REQUIRED
```

المسار المستهدف:

```text
Pull detects no cursor / expired cursor / corruption / reconciliation mismatch
        │
        ▼
Trusted scope + session validation
        │
        ▼
Durable Recovery State = IN_PROGRESS
        │
        ▼
Freeze V2 drain ownership, never delete pending outboxes
        │
        ▼
Begin trusted server bootstrap handshake
(snapshot + baseline cursor from the same authority boundary)
        │
        ▼
Page snapshot into Room staging only
(no domain mutation yet)
        │
        ▼
Validate completeness / coverage / payloads / scope
        │
        ▼
Atomic mirror cutover
  ├─ preserve every unacked outbox
  ├─ rebuild authoritative mirror
  ├─ restore/replay pending local overlay safely
  ├─ install baseline cursor
  └─ mark READY
        │
        ▼
Resume normal 311 durable drain
  ├─ push pending local mutations
  ├─ pull revisions after bootstrap baseline
  └─ reconcile / expose diagnostics
```

القواعد الحاكمة:

```text
Recovery may rebuild cache; it may never destroy unacked intent.
```

```text
Bootstrap cursor comes from the same trusted snapshot handshake.
It is never reconstructed from timestamps or max(local revision).
```

```text
A snapshot materialization is not a business command replay.
Financial/inventory facts must be restored as authoritative materialization only.
```

---

# 1. بوابة البداية من 312

الحالة المثبتة داخل `VERTO_SYNC_REALTIME_VERIFICATION_v312.json`:

```text
finalVerdict = PASS_STATIC_REALTIME_HINT_TARGETED_LIFECYCLE
               / INHERITED_307_EXCEPTIONS=9
               / ROOM_80_UNCHANGED
               / V2_DISABLED
               / REALTIME_DEFAULT_DISABLED
               / BUILD_NOT_VERIFIED
               / POSTGRES_NOT_EXECUTED
               / V312_REALTIME_SQL_STATIC_ONLY

v312 fixtures              = 300/300 PASS
v311 regression fixtures   = 325/325 PASS
v310 regression fixtures   = 499/499 PASS
v309 regression fixtures   = 272/272 PASS
v308 regression fixtures   = 137/137 PASS + MODEL_10K_PASS
new312WaiverCount          = 0
runtimeV2                  = DISABLED
realtimeRuntime            = DISABLED
handoff313Authorized       = true
blockers                    = []
```

313 لا تبدأ إذا تغير baseline دون توثيق:

```text
BLOCKED_INPUT_DRIFT
```

---

# 2. الاستثناءات الموروثة

تبقى الاستثناءات الموروثة من 307 **9 فقط**:

```text
room_version_79
persisted_sequence_authority
payload_bound_preserved
producer_discovery_unclassified_zero
stronger_outboxes_proven
datastore_delete_authority_zero
dirty_only_authority_zero
org_settings_room_canonical
attachment_intent_persisted
```

قواعد 313:

1. تحمل كما هي في artifact مستقل.
2. لا يعاد تفسيرها كـPASS نظيف.
3. `new313WaiverCount = 0` شرط PASS.
4. `room_version_79` يصبح historical exception؛ رفع Room إلى 81 لا يعتبر حلًا تلقائيًا له.
5. أي استثناء يلامس recovery يجب أن يظل `worsened=false`.
6. لا يستخدم أي استثناء لتبرير مسح outbox أو فقد local intent أو bootstrap ناقص.

---

# 3. ترتيب السلطات — Authority Order

عند التعارض:

1. `Verto-v312-source-of-truth.zip` ذو SHA المثبت.
2. `VERTO_SYNC_REALTIME_VERIFICATION_v312.json/.md`.
3. `SESSION_312_FINAL.md`.
4. `VERTO_SYNC_ORCHESTRATION_VERIFICATION_v311.json/.md`.
5. `VERTO_SYNC_STRONGER_VERIFICATION_v310.json/.md`.
6. `VERTO_SYNC_PUSH_VERIFICATION_v309.json/.md`.
7. `VERTO_SYNC_PULL_VERIFICATION_v308.json/.md`.
8. `docs/sync/VERTO_UNIFIED_SYNC_CONTRACT_v304.json/.md`.
9. `UnifiedSyncContract.kt` + `UnifiedSyncAggregateRegistry.kt`.
10. `UnifiedSyncPullEngine.kt` + `UnifiedSyncPullRemote.kt`.
11. `UnifiedSyncDao.kt` + Room schema 80.
12. v305 bootstrap/reconciliation server RPCs.
13. v309/v310 server push/stronger bridges.
14. v312 Realtime migration/artifacts.
15. هذا العقد.
16. الخطة الأم v304→v314.

أي legacy `InitialSyncPolicy(roomIsEmpty)` ليست recovery authority إذا تعارضت مع هذا العقد.

---

# 4. بصمات authority عند البداية

يجب تثبيت هذه البصمات قبل أي تعديل:

```text
Verto-v312-source-of-truth.zip
  acab081179e77e2a37c7a2358c49ece22a6c995691cec7558d4a9c1a5baaeebd

SESSION_312_FINAL.md
  3b2c829ae1b77b6717f646f9ef130c3c23c4af4904d3c036664d9bca182fd020

VERTO_SYNC_REALTIME_VERIFICATION_v312.json
  15972df37277dc667529d13ca014f6433acec6f8eda06b3a12751efce503bec3

app/schemas/.../AppDatabase/80.json
  1d077e2539cf8ac4a2cf618c11c0a97f7de44f0c2bf15298e923f39b75988543

data/network/.../UnifiedSyncContract.kt
  9553e1801dcf756f619ea2c28fd1bf8534f18fa32e4f74c2743cedc5460a7e6c

data/network/.../UnifiedSyncAggregateRegistry.kt
  9e35f020993e3caa21171ab6a311605f2bcfa58a910bf7814f22e157b5af7fe9

data/network/.../UnifiedSyncPullRemote.kt
  0945b7f60d363be0f022cab620033e2896a559a5f57dfb42a87fa79dfd78454c

data/network/.../UnifiedSyncPullWire.kt
  7862415803fa75228c3c19db5f333f06088cc7077a632077881c5ab4410a6370

data/sync/.../pull/UnifiedSyncPullEngine.kt
  c4babd8f48bb16ff160a296238e1459a47dcc1e5592997eb5aeec717e153e6d7

data/sync/.../pull/UnifiedSyncPullRegistry.kt
  04ca782471581ef7948564c93b18ef4a1e919e7c118611b957895547c1bbf250

data/sync/.../SyncManager.kt
  095cd6bbd814f1ae67942d5a4392245a7bbad7e0d5b79a808b43ea16610ac837

data/sync/.../SyncWorker.kt
  b062640e940a3caec7ee8a1a84501ff91ed9d7b28db6b12e65c03fc4cefa2853

data/sync/.../SyncReliability.kt
  6c39618bcb3184086b0f9bf12a1fed693e9483542c748ad62029c69bf0c319ad

data/sync/.../SyncWorkScope.kt
  5cd87c2a131d31b9e08998d5e41769247c98a36a6c21aca8701ea72cabf5d729

data/sync/.../RealtimeManager.kt
  94e249cd1ac4af90e8d73eba81b66ddd6b0b05e70e1d885fedded730ecefbf90

data/database/.../AppDatabase.kt
  9ff0dc54abaa87591bfdf294ce1d8888fd3993b0fbdfcf5d105ae1d3693c5bbb

data/database/.../MigrationCatalog.kt
  0a7f2b7263dd19723b9b68da6988f1b3e649b420325cf50b8694505d28c880c7

data/database/.../dao/UnifiedSyncDao.kt
  2a4bf8cdde3b68731cf2d6433358ec804b757588a8c49cb5f4195becb618f68e

data/database/.../entity/UnifiedSyncEntities.kt
  3f0e9df13932677f73465697c2c21455886e63b447cd4e37d7c05343a0fffdd0

core/common/.../FeatureFlags.kt
  ad159d5e9f91e406127a5220c4e6729a51f659577ea4e673f9cce98f0de57203

supabase/migrations/20260821062000_v305_verto_unified_sync_server.sql
  a2daf28b3a05b35907267bfc766fc6919ba5c28613854c0c0d68ee186313e908

supabase/migrations/20260821123000_v309_verto_unified_sync_push.sql
  c453748be91151702d08e67200556fdd0ab66fbe7d905eff8b9357d8ca8c9bdf

supabase/migrations/20260821150000_v310_verto_stronger_stream_bridge.sql
  43b2db6d6300bc5a59caffdffef071bd894a3356783dffc5de0a9afa2e13f1ce

supabase/migrations/20260821170000_v312_realtime_hint_surface.sql
  8455f3ae99a34696985e1b3eb1bcb3449c12122c748a1f5819fc972b3378e304
```

Historical Room schema80 وv305/v309/v310/v312 server migrations لا تعدل في 313.

---

# 5. الحالة الحالية المثبتة من فحص v312

الكود الحالي يثبت:

```text
Room                                         = 80
Unified aggregate registry                   = 34
Direct/shadow pull aggregates                = 17
Stronger owner310 aggregates                 = 17
SyncBootstrapState enum                      = present
BootstrapSession contract                    = present
SyncReconciliationManifest contract          = present
UnifiedSyncPullOutcome.BOOTSTRAP_REQUIRED    = present
UnifiedSyncPullOutcome.RECOVERY_REQUIRED     = present
Client beginBootstrap RPC binding            = absent
Client pullBootstrapPage binding             = absent
Client reconciliation-manifest binding       = absent
Durable bootstrap state table                = absent
Durable bootstrap staging table              = absent
Durable sync-health state table               = absent
InitialSyncPolicy                            = Room-empty boolean only
Pull cursor states in Room                   = ACTIVE / BOOTSTRAP_REQUIRED / INVALIDATED
Pull on missing cursor                       = BOOTSTRAP_REQUIRED
Pull on expired cursor                       = mark BOOTSTRAP_REQUIRED + RECOVERY_REQUIRED
SyncManager handling recovery outcome        = return SyncDrainResult.RECOVERY_REQUIRED
SyncWorker handling RECOVERY_REQUIRED        = Result.success(), no recovery execution
v305 begin bootstrap RPC                     = present
v305 bootstrap page RPC                      = present
v305 reconciliation manifest RPC             = present
v305 bootstrap snapshot authority            = verto_sync_snapshot_state
v305 snapshot backfill                       = explicitly absent by design
v309 migration snapshot maintenance          = present in v309 function body
v310 replaces verto_apply_sync_mutation       = yes
v310 replacement snapshot upsert/remove calls = 0
v310 replacement append-change calls          = present
v310 effective snapshot continuity             = incomplete for both owner307 generic and owner310 stronger paths
v310 stronger CREATE TRIGGER bridge            = absent
v312 Realtime                                = hint-only, optional
Runtime V2                                   = OFF
Realtime runtime                             = OFF
```

---

# 6. الفجوة الحرجة الجديدة: Bootstrap Snapshot Completeness

هذه الفجوة **تمنع** ادعاء Safe Full Resync اليوم.

v305 يقول صراحة إن:

```text
verto_sync_snapshot_state
```

هو surface الـbootstrap، لكنه لا يبتكر backfill ولا producer cutover.

v309 migration تحتوي بالفعل على snapshot upsert/remove داخل نسخة `verto_apply_sync_mutation` الخاصة بها.

لكن v310 **تستبدل** `verto_apply_sync_mutation` بالكامل. فحص body البديل في v310 يثبت:

```text
verto_append_sync_change(...) calls          = present
verto_upsert_sync_snapshot_state(...) calls  = 0
verto_remove_sync_snapshot_state(...) calls  = 0
```

لذلك إذا طبقت migrations بالترتيب، لا توجد continuity مثبتة للـ17 owner310، كما أن owner307 generic path الذي كان يحدث snapshot في v309 قد يفقد هذا السلوك بعد replacement في v310.

بالتالي:

```text
server bootstrap RPC exists
!=
server bootstrap snapshot is complete for all 34 aggregates
```

313 لا يجوز أن تبني client recovery فوق snapshot ناقصة.

أي PASS دون سد هذه الفجوة:

```text
FAIL_BOOTSTRAP_SNAPSHOT_COVERAGE
```

---

# 7. هدف 313 الدقيق

313 تنفذ فقط:

1. Client bindings لـbegin bootstrap/pull bootstrap/reconciliation manifest.
2. Durable bootstrap/recovery state.
3. Durable raw snapshot staging قبل domain cutover.
4. Room `80→81` migration additive ومحدودة بالمزامنة.
5. Safe initial sync state بدل Room-empty authority في V2 path.
6. Cursor-expiry recovery flow.
7. Corruption-triggered recovery flow.
8. Safe full resync يحفظ كل unacked outbox.
9. Atomic mirror cutover + baseline cursor install.
10. Bootstrap session resume/restart semantics.
11. Recovery after process death.
12. Explicit 34-aggregate snapshot coverage proof.
13. Restore effective snapshot continuity after the v310 function replacement for all applicable aggregates, مع إثبات owner310 17/17.
14. Server backfill/materialization proof for pre-existing data.
15. Reconciliation manifests كـanti-entropy فقط.
16. Scoped repair إذا proven safe؛ وإلا full recovery.
17. No command replay for financial/inventory snapshot restoration.
18. Pending local mutation preservation/replay contract.
19. Observability snapshot privacy-safe.
20. Outbox depth/age/retry/conflict/dead-letter diagnostics.
21. Last successful push/pull/failure diagnostics.
22. Last observed server revision + last applied revision + lag diagnostics.
23. Realtime connected state diagnostic only.
24. Worker generation diagnostic only.
25. Full-resync count.
26. Logout/org-switch/session-epoch safety أثناء recovery.
27. V2 وRealtime يظلان OFF افتراضيًا.
28. Legacy remains intact.
29. 312/311/310/309/308 regressions تبقى صفر.
30. no new waivers.

---

# 8. ما ليست عليه 313

```text
V2 default-on                               → 314
Realtime default-on                         → 314 rollout gate
Multi-device fault injection                → 314
Legacy removal                              → 314
Timestamp marker deletion                   → 314
DataStore legacy deletion queue deletion    → 314
Generic dirty/clean removal                 → 314
Business-rule redesign                      → ممنوع
UI redesign                                 → ممنوع
CRDT conversion                             → ممنوع
Event Sourcing rewrite                      → ممنوع
Attachment binary migration                 → خارج النطاق
New conflict policy                         → خارج النطاق
Financial command redesign                  → ممنوع
Inventory costing redesign                  → ممنوع
```

---

# 9. Runtime policy

في نهاية 313:

```text
FeatureFlags.isVersionedSyncEnabled = false
FeatureFlags.isRealtimeSyncEnabled  = false
legacy runtime                      = preserved as default
V2 recovery path                    = implemented + directly testable
Room 81                             = statically defined
server v313 migration               = statically defined
PostgreSQL application              = NOT CLAIMED unless actually executed
```

أي default-on مبكر:

```text
FAIL_RUNTIME_CUTOVER_EARLY
```

---

# 10. Room schema policy

الحاكم:

```text
Before = 80
After  = 81
```

السبب الوحيد للرفع:

```text
durable recovery/bootstrap staging + health metadata
```

ممنوع استخدام 81 لإعادة تصميم domain tables.

---

# 11. Migration 80→81 — الجداول المسموحة

313 تنشئ بحد أقصى الجداول الجديدة التالية:

```text
sync_recovery_state
sync_bootstrap_stage
sync_health_state
```

يجوز دمج `sync_health_state` داخل `sync_recovery_state` إذا ظل الفصل المنطقي واضحًا، لكن لا يجوز حذف bootstrap staging durability.

---

# 12. sync_recovery_state contract

الحد الأدنى للحقول المنطقية:

```text
scope_id                         PK
organization_id
sync_principal_id
contract_family
contract_version
scope_definition_version
state
reason
bootstrap_session_id?
baseline_cursor?
baseline_revision?              diagnostic only
next_page_token?
expected_snapshot_rows?
staged_snapshot_rows
recovery_generation
attempt_count
last_error_code?
started_at?
updated_at
completed_at?
```

الحالات المسموحة:

```text
NOT_STARTED
IN_PROGRESS
READY
RECOVERY_REQUIRED
```

يجوز إضافة `CUTOVER_READY` داخليًا إذا verifier يثبت transitions، لكن لا تغير contract-facing states الأربع.

---

# 13. sync_bootstrap_stage contract

الحد الأدنى:

```text
scope_id
bootstrap_session_id
ordinal
aggregate_type
aggregate_id
entity_version?
payload_version
payload_json
partition_key
content_fingerprint
```

قيود:

```text
PK(scope_id, bootstrap_session_id, ordinal)
UNIQUE(scope_id, bootstrap_session_id, aggregate_type, aggregate_id)
payload_version > 0
ordinal > 0
payload bounded
fingerprint nonblank
```

ممنوع stage في DataStore أو memory فقط.

---

# 14. sync_health_state contract

يحفظ فقط metadata تشغيلية غير حساسة، مثل:

```text
scope_id
organization_id
last_successful_push_at?
last_successful_pull_at?
last_failure_category?
last_failure_code?
last_reconciliation_at?
last_reconciliation_status?
full_resync_count
updated_at
```

لا payload ولا token ولا Authorization.

---

# 15. Migration integrity

قبول 313 يتطلب:

```text
schema80 SHA unchanged
MIGRATION_80_81 exists exactly once
MigrationCatalog ends at 81
AppDatabase version = 81
schema81 exported
80→81 migration preserves all pre-existing rows
```

أي تعديل schema80:

```text
FAIL_SCHEMA80_DRIFT
```

---

# 16. Bootstrap Remote boundary

إضافة boundary مثل:

```text
interface UnifiedSyncBootstrapRemote {
    suspend fun resolveScope(): SyncScope
    suspend fun begin(scope: SyncScope): BootstrapStart
    suspend fun pullPage(sessionId: String, pageToken: String, limit: Int): BootstrapPage
    suspend fun reconciliationManifest(scope: SyncScope, partitionToken: String?): ReconciliationPage
}
```

أو equivalent موثق.

---

# 17. Existing contract protection

الأصل أن يبقى:

```text
UnifiedSyncContract.kt
UnifiedSyncAggregateRegistry.kt
```

byte-identical.

Wire DTOs/Bootstrap domain DTOs الجديدة توضع في ملفات منفصلة إن أمكن.

لا تغيير:

```text
contract family = verto-unified-sync
contract version = 1
aggregate count = 34
```

---

# 18. Bootstrap handshake invariant

ممنوع:

```text
1. pull snapshot
2. later query max(revision)
3. invent baseline cursor
```

المطلوب:

```text
snapshot + baseline cursor originate from one trusted server bootstrap handshake
```

v305 semantics هي baseline، وأي v313 server change يجب أن يحافظها أو يقويها.

---

# 19. Cursor opacity

`baseline_cursor`:

- nonblank.
- server-owned.
- scope-bound.
- opaque.
- لا يتحول إلى Long محليًا.
- لا يعاد بناؤه من `baseline_revision`.

`baseline_revision` diagnostic anchor فقط.

---

# 20. No timestamp recovery authority

ممنوع:

```text
updated_at
changed_at
System.currentTimeMillis()
lastSyncAt
```

كمصدر ترتيب أو resume cursor أو recovery baseline.

الوقت مسموح فقط للـdiagnostics/TTL/backoff.

---

# 21. Initial Sync authority

V2 path لا يعتمد على:

```text
Room is empty == initial sync required
```

الحاكم يصبح:

```text
trusted scope + durable recovery state + presence/validity of active cursor
```

Legacy path يمكن أن يبقي `InitialSyncPolicy(roomIsEmpty)` حتى 314.

---

# 22. Initial Sync states

السلوك:

```text
no recovery row + no cursor      → NOT_STARTED → bootstrap
IN_PROGRESS                      → resume/restart bootstrap
READY + ACTIVE valid cursor      → normal drain
RECOVERY_REQUIRED                → recovery bootstrap
READY + missing cursor           → fail closed → RECOVERY_REQUIRED
```

---

# 23. Empty Room is not sufficient proof

الحالات التالية كلها يجب أن تُعالج:

```text
Room not empty but cursor absent
Room partially restored from backup
Room contains stale domain rows but no sync state
Room contains sync tables only
Room contains local pending outbox after cache damage
```

لا `Room empty` shortcut.

---

# 24. Cursor expiry flow

عند `CURSOR_EXPIRED`:

```text
1. do not advance cursor
2. mark current cursor BOOTSTRAP_REQUIRED
3. persist recovery state RECOVERY_REQUIRED
4. preserve all outboxes
5. schedule/continue recovery under same trusted scope
6. begin new bootstrap
```

لا timestamp fallback.

---

# 25. Cursor corruption triggers

على الأقل:

```text
blank cursor token
scope identity mismatch
invalid cursor state
missing local anchor inbox row for lastAppliedChangeRevision
server CURSOR_EXPIRED
server SCOPE_MISMATCH requiring new scope bootstrap
reconciliation divergence with no pending local overlay explanation
staged bootstrap duplicate with divergent fingerprint
```

كلها fail closed.

---

# 26. Missing inbox anchor proof

إذا:

```text
cursor.lastAppliedChangeRevision = R
R != null
```

يجب أن يوجد inbox row مطابق:

```text
scope_id + server_revision = R
apply_state = APPLIED
```

وإلا:

```text
RECOVERY_REQUIRED
```

لا تفترض contiguous revisions؛ gaps server-side طبيعية.

---

# 27. Recovery ownership

Recovery تنفذ تحت نفس orchestration ownership/mutex discipline.

ممنوع worker مستقل ينافس:

```text
UnifiedSyncPushEngine
UnifiedSyncPullEngine
normal drain
```

لنفس scope.

---

# 28. Recovery generation

كل recovery intent يجب أن يكون durable.

يجوز استخدام `sync_sequence_state` generation مستقلة أو generation 311 مع recovery marker، بشرط:

```text
process death cannot lose recovery obligation
```

لا memory boolean كauthority.

---

# 29. Safe Full Resync — التعريف

Full Resync لا يعني:

```text
clearAllTables()
then download again
```

التعريف الحاكم:

```text
stage authoritative snapshot
validate it completely
preserve local durable intent
atomically replace recoverable mirror
install baseline cursor in same transaction
resume pending mutations afterward
```

---

# 30. Outbox preservation absolute rule

Recovery لا تمسح أو تعيد إنشاء IDs لأي unacked mutation.

ممنوع حذف/إفراغ:

```text
sync_outbox
party_sync_outbox
financial_outbox
inventory_stock_outbox
inventory_cost_outbox
optimal_outbox
sync_attachment_transfer
```

إلا terminal diagnostic cleanup خارج recovery وبعقد منفصل.

---

# 31. Outbox identity digest

قبل cutover وبعده، وقبل resume network، verifier/model يجب أن يثبت:

```text
pendingOutboxIdentityDigestBefore == pendingOutboxIdentityDigestAfter
```

الـdigest يغطي على الأقل:

```text
owner
mutation/event/command id
organization
aggregate identity
state
semantic fingerprint/request identity when present
```

لا يطبع payload.

---

# 32. Outbox lease handling

عند recovery:

- leases المنتهية يمكن إعادتها لحالة قابلة للمحاولة وفق 309.
- active lease لا يحذف.
- recovery لا تعتبر `LEASED` = synced.
- process-death lease recovery تبقى authoritative.

---

# 33. Push freeze semantics

"freeze outbound" تعني:

```text
no network push while authoritative mirror cutover is unresolved
```

ولا تعني:

```text
reject every local user write
```

أي local write أثناء staging يجب أن يبقى durable في outbox، ثم يدخل overlay/replay قبل READY أو بعده وفق policy موثقة.

---

# 34. Local write during staging

سيناريو إلزامي:

```text
bootstrap pages are being staged
user creates/edits one durable mutation
```

PASS إذا:

```text
mutation remains in outbox
cutover does not erase its local intent
post-recovery push can still submit same mutationId
```

---

# 35. Snapshot staging is not live apply

ممنوع:

```text
pull bootstrap page → immediately mutate domain tables
```

قبل اكتمال snapshot.

الصحيح:

```text
pull bootstrap page → validate → persist stage → next token
```

ثم cutover بعد `snapshot_complete=true`.

---

# 36. Bootstrap page validation

كل page تتحقق من:

```text
session id
scope ownership
page token continuity
ordinal strictly increasing
aggregate known in 34 registry
payload version exact
payload size bound
aggregate id nonblank
partition key nonblank
fingerprint stable
no duplicate divergent aggregate
```

---

# 37. Snapshot expected-row count

`verto_begin_sync_bootstrap` يعيد `snapshot_row_count`.

قبل cutover:

```text
staged unique rows == expected snapshot rows
snapshot_complete == true
```

وإلا:

```text
FAIL_BOOTSTRAP_INCOMPLETE
```

---

# 38. Bootstrap session expiry

إذا server يعيد:

```text
BOOTSTRAP_RESTART_REQUIRED
```

أو TTL انتهت:

```text
1. do not reuse baseline cursor
2. discard only stale stage rows for that bootstrap session
3. preserve outboxes
4. begin a fresh server bootstrap
```

---

# 39. Bootstrap process-death resume

بعد كل page commit محلي:

```text
next_page_token
staged_snapshot_rows
bootstrap_session_id
state
```

تكتب transactionally مع stage page.

process death بعد page N:

```text
resume from persisted token if server session valid
```

---

# 40. Process death during cutover

كل domain mirror replacement + baseline cursor install + READY transition يجب أن تكون داخل **Room transaction واحدة**.

بالتالي:

```text
crash before commit → old mirror + recovery state remains
crash after commit  → new mirror + baseline cursor + READY
```

ممنوع half-cutover observable state.

---

# 41. No fake SyncChange for bootstrap

ممنوع إنشاء:

```text
SyncChange(revision = ordinal or baselineRevision, operation = UPSERT)
```

لمجرد إعادة استخدام PullEngine.

Bootstrap row ليست change-log event.

يجب وجود snapshot/materialization applier مخصص أو adapter equivalent يثبت semantics.

---

# 42. Snapshot materialization semantics

الـbootstrap applier:

- لا يكتب inbox event وهمي.
- لا يولد outbox.
- لا ينفذ business command.
- لا يعيد posting مالي.
- لا يعيد حركة مخزون كأمر.
- لا يعيد إرسال إشعار.
- يكتب authoritative materialized state فقط.

---

# 43. Stronger aggregates protection

الـ17 owner310 aggregates تبقى تحت stronger semantics.

ممنوع في recovery:

```text
generic LWW
command replay
financial event duplication
inventory movement duplication
Optimal outbox echo
```

---

# 44. Financial snapshot restoration

INVOICE/PAYMENT/CLIENT_CREDIT/EXPENSE/CASH_* وغيرها:

```text
snapshot → read-model/materialized state restoration
```

وليس:

```text
snapshot → execute original command again
```

أي duplicated ledger effect:

```text
FAIL_RECOVERY_FINANCIAL_DUPLICATE_EFFECT
```

---

# 45. Inventory snapshot restoration

`INVENTORY_MOVEMENT` و`INVENTORY_COST_REVISION` facts immutable.

Recovery يمكنها insert/reconcile canonical facts، لكنها لا تولد command جديدًا ولا تغير cost semantics.

أي duplicate movement identity:

```text
FAIL_RECOVERY_INVENTORY_DUPLICATE_EFFECT
```

---

# 46. Delete semantics during full resync

Snapshot تمثل current authoritative state.

إذا row محلية ليست في snapshot ولا يحميها pending local mutation:

```text
remove/prune according to aggregate recovery adapter
```

DELETE لا يستنتج من timestamp.

ARCHIVE/VOID/CANCEL تبقى rows إذا server snapshot تمثلها كstate.

---

# 47. Full resync after server delete

اختبار إلزامي:

```text
local has entity X
server snapshot omits X because authoritative DELETE
```

PASS:

```text
X absent after atomic cutover
no local delete outbox generated
```

---

# 48. Pending local overlay

إذا aggregate لديها pending local mutation أثناء recovery:

313 يجب أن تختار واحدة موثقة لكل aggregate:

```text
A. replay local optimistic materialization from durable outbox without creating a new mutation
B. preserve local row across prune and let original mutation reconcile after resume
C. block READY with explicit REQUIRES_REVIEW if neither A nor B is safe
```

لا silent overwrite.

---

# 49. Pending overlay coverage

كل 34 aggregate يجب أن يكون لها recovery disposition:

```text
REPLAY_FROM_UNIFIED_OUTBOX
PRESERVE_STRONGER_LOCAL_STATE
SERVER_READ_ONLY_NO_LOCAL_PENDING
BLOCK_REVIEW_IF_PENDING
```

لا `UNKNOWN`.

---

# 50. Recovery adapter registry

إنشاء registry مستقل أو equivalent يربط كل aggregate بـ:

```text
snapshot apply adapter
prune strategy
pending-local strategy
outbox owner
stronger owner
local table set
server snapshot source
```

يجب أن يغطي بالضبط 34/34.

---

# 51. Atomic cutover table allowlist

Recovery لا تستخدم `clearAllTables()`.

يجب وجود allowlist صريحة للجداول التي يسمح بإعادة بنائها.

Forbidden preservation set يشمل على الأقل:

```text
all outboxes
sync_sequence_state
sync_recovery_state
sync_health_state
conflict evidence needed for review
attachment transfer intent
```

---

# 52. Inbox during full resync

`sync_inbox` ليست source of truth للmirror.

313 تختار policy موثقة:

```text
retain old inbox as diagnostics
or
prune rows <= baseline after successful cutover
```

لكن لا يجوز حذف inbox قبل نجاح cutover، ولا يجب أن يتسبب old inbox في replay بعد baseline cursor.

---

# 53. Cursor install atomicity

baseline cursor تكتب فقط داخل cutover transaction التي أنهت mirror rebuild.

ممنوع:

```text
install cursor → then apply snapshot
```

أو:

```text
mark READY → then install cursor
```

---

# 54. Cursor state after successful bootstrap

النهاية:

```text
sync_cursor.state = ACTIVE
cursor_token = server baseline_cursor
scope identity = current trusted scope
lastAppliedChangeRevision = baselineRevision? diagnostic only
pageHighWatermark = baselineRevision? diagnostic only if exact server evidence
minAvailableRevision = server evidence only
```

لا numeric field يعيد بناء token.

---

# 55. Cursor state after failed bootstrap

الفشل قبل cutover:

```text
old cursor stays BOOTSTRAP_REQUIRED/invalid as appropriate
recovery state != READY
no new baseline cursor installed
```

---

# 56. Scope change during bootstrap

إذا org/user/principal/scopeDefinition/sessionEpoch تغير:

```text
STALE_RECOVERY_SCOPE
```

- stop current recovery.
- لا cutover.
- stage القديم لا يستخدم للscope الجديد.
- outboxes لا تنقل cross-tenant.

---

# 57. Logout during recovery

قبل/أثناء logout:

```text
session epoch invalidates recovery authority
worker/coroutine cancellation is advisory
scope check is authoritative
```

late bootstrap page:

```text
no stage commit for new session
no cutover
```

---

# 58. Org switch during recovery

ترتيب 311 يبقى الحاكم.

old recovery لا يجوز أن:

```text
install cursor for new org
clear new org mirror
write stage under new scope
resume old outbox in new tenant
```

---

# 59. Same-org reauth

نفس org/user مع sessionEpoch جديدة:

- old recovery stale.
- server bootstrap session القديمة لا تعطي client authority تلقائيًا.
- restart/resume يتطلب current session validation.

---

# 60. Server migration policy

313 تتطلب **migration واحدة additive جديدة** بسبب gap المثبت في snapshot coverage.

ممنوع تعديل:

```text
v305
v309
v310
v312
```

الجديدة مسؤولة فقط عن recovery/bootstrap completeness.

---

# 61. v313 server migration minimum responsibility

يجب أن تحقق **إما**:

```text
A. complete and continuously maintained verto_sync_snapshot_state for all 34 aggregates
```

أو:

```text
B. a new authoritative bootstrap materializer independent of snapshot_state,
   with equivalent gap-free snapshot+baseline semantics for all 34 aggregates
```

بما أن v305 bootstrap الحالي يعتمد snapshot_state، الخيار A هو الافتراضي المفضل.

---

# 62. Effective snapshot continuity after v310 replacement

إذا اختير A:

الـv313 migration يجب أن تعيد snapshot maintenance لكل APPLIED path يمر عبر replacement v310، وليس owner310 فقط.

كل owner310 APPLIED mutation يجب أن يحدّث/يزيل snapshot state في **نفس PostgreSQL transaction** التي:

```text
applies stronger business effect
appends unified change
writes immutable receipt
```

أي update لاحق asynchronously:

```text
FAIL_SNAPSHOT_CHANGE_ATOMICITY
```

---

# 63. Secondary stronger changes

v310 يمكن أن يولد `secondary_changes`.

313 يجب أن تضمن لكل secondary change:

```text
change_log append
+ corresponding snapshot materialization/removal
```

في نفس transaction.

---

# 64. Existing-data backfill

Future continuity وحدها لا تكفي.

قبل Safe Full Resync يجب أن توجد static-defined backfill/materialization strategy للبيانات الموجودة قبل 313.

PASS يتطلب:

```text
34/34 aggregate bootstrap source coverage
0 aggregate with "future writes only"
```

---

# 65. Backfill ordering

إذا backfill تستخدم change-log revisions:

- revisions server-owned.
- `source_kind='MIGRATION'` أو equivalent contract-valid.
- snapshot updated_revision يجب أن تشير إلى matching committed change.
- gaps مسموحة.
- لا timestamp ordering.

---

# 66. Backfill idempotency

إعادة تشغيل migration لا يجب أن تضاعف business effects.

Backfill قد تضيف sync metadata/change history فقط، ولا تعيد تنفيذ business commands.

---

# 67. Bootstrap coverage gate

إنشاء:

```text
docs/sync/VERTO_SYNC_BOOTSTRAP_COVERAGE_v313.csv
```

بالأعمدة:

```text
aggregate_type
payload_version
owner_session
server_authoritative_source
bootstrap_materializer
snapshot_continuity_path
delete_or_absence_rule
visibility_rule
backfill_defined
future_write_covered
stronger_semantics_preserved
runtime_state
evidence
```

الصفوف:

```text
exactly 34
```

ولا UNKNOWN/TODO/LATER في PASS.

---

# 68. Outbox preservation artifact

إنشاء:

```text
docs/sync/VERTO_SYNC_OUTBOX_PRESERVATION_v313.csv
```

يغطي على الأقل:

```text
sync_outbox
party_sync_outbox
financial_outbox
inventory_stock_outbox
inventory_cost_outbox
optimal_outbox
sync_attachment_transfer
```

الأعمدة:

```text
owner_id
table_name
identity_columns
active_states
lease_behavior
recovery_preservation_rule
pending_overlay_strategy
clear_forbidden
process_death_safe
evidence
```

---

# 69. Recovery lifecycle artifact

إنشاء:

```text
docs/sync/VERTO_SYNC_RECOVERY_STATE_v313.md
```

يوثق state machine:

```text
NOT_STARTED
  → IN_PROGRESS
  → READY
  → RECOVERY_REQUIRED
  → IN_PROGRESS

IN_PROGRESS
  → IN_PROGRESS(resume next page)
  → IN_PROGRESS(restart new bootstrap session)
  → READY(atomic cutover)
  → RECOVERY_REQUIRED(fail closed)
```

---

# 70. Bootstrap policy artifact

إنشاء:

```text
docs/sync/VERTO_SYNC_BOOTSTRAP_POLICY_v313.md
```

يوثق:

- trusted scope.
- server handshake.
- staging.
- resume.
- expiry restart.
- atomic cutover.
- cursor install.
- pending mutation overlay.
- no fake SyncChange.
- no command replay.

---

# 71. Reconciliation role

Reconciliation هي:

```text
ANTI_ENTROPY_ONLY_NOT_PRIMARY_FEED
```

كما في v304.

لا تحل محل revision pull.

---

# 72. Reconciliation manifest remote

Client يستهلك `verto_get_reconciliation_manifest` أو v313 equivalent.

كل manifest يجب أن يتحقق من:

```text
scopeId
aggregateType
partitionKey
rowCount
contentHashOrVersionDigest
manifestRevision
```

---

# 73. Canonical local reconciliation

Local digest يجب أن يستخدم canonicalization ثابتة:

```text
stable field order
stable row order
stable numeric representation
stable null handling
no locale-dependent formatting
no wall-clock diagnostic fields
```

---

# 74. Pending mutations and reconciliation

إذا partition فيها pending local mutation، server/local divergence قد تكون متوقعة.

313 لا تعلن corruption مباشرة.

السلوك:

```text
DEFERRED_PENDING_LOCAL_MUTATIONS
```

أو compare against remote-base representation إذا موجودة ومثبتة.

---

# 75. Reconciliation mismatch flow

إذا لا pending local mutations ويوجد mismatch:

```text
1. record diagnostic mismatch
2. attempt scoped authoritative repair only if registry proves safe
3. rerun manifest
4. if still mismatch → RECOVERY_REQUIRED full bootstrap
```

لا timestamp repair.

---

# 76. Scoped repair constraints

Scoped repair لا يجوز أن:

- يقفز global cursor.
- يغير revisions السابقة.
- يعيد command.
- يمس pending outbox.
- يستخدم filtered pull لتقدم global cursor.

إذا لا يمكن إثبات ذلك:

```text
fallback to full bootstrap
```

---

# 77. Reconciliation artifact

إنشاء:

```text
docs/sync/VERTO_SYNC_RECONCILIATION_v313.md
```

يوثق:

- when to run.
- when to defer.
- canonicalization.
- mismatch categories.
- scoped repair eligibility.
- full recovery escalation.

---

# 78. Observability goal

يجب أن يمكن تفسير حالة المزامنة دون فتح قاعدة البيانات يدويًا.

313 تبني data-level `SyncHealthSnapshot` أو equivalent.

---

# 79. Required health fields

على الأقل:

```text
currentTenant
scopeId
recoveryState
lastObservedServerRevision
lastAppliedRevision
syncLag
unifiedOutboxDepth
strongerOutboxDepth
oldestPendingMutationAge
retryCount
conflictCount
deadLetterOrReviewCount
lastSuccessfulPushAt
lastSuccessfulPullAt
lastFailureCategory
lastFailureCode
realtimeState
requestedGeneration
drainedGeneration
fullResyncCount
lastReconciliationStatus
```

---

# 80. Server revision diagnostic truth

`lastObservedServerRevision` يشتق من server-provided high-watermark/baseline فقط.

ممنوع تسميته `current server revision` إذا لم يكن observation حديثًا.

الاسم/report يجب أن يوضح:

```text
last observed
```

---

# 81. Sync lag diagnostic

يجوز:

```text
max(0, lastObservedServerRevision - lastAppliedRevision)
```

كتشخيص تقريبي فقط عندما كلاهما من نفس visible scope.

لا يستخدم lag كcursor أو correctness authority.

---

# 82. Outbox depth coverage

Health تجمع active depth من كل outbox owners، لا `sync_outbox` وحدها.

يجب فصل:

```text
unified
generic stronger/legacy-owned
attachments
```

أو تقديم total + breakdown.

---

# 83. Oldest pending mutation

يحفظ/يعرض age فقط أو createdAt metadata.

لا يطبع payload أو note مالي أو attachment URI.

---

# 84. Dead-letter definition

لا تخترع table جديدة إذا غير لازمة.

`deadLetterOrReviewCount` يمكن أن يجمع terminal states مثل:

```text
REQUIRES_REVIEW
REJECTED
permanent conflict/review states
```

ويجب توثيق تعريفه.

---

# 85. Conflict count

يغطي على الأقل:

```text
sync_conflict
party_sync_conflicts
inventory_sync_conflicts
```

وfinancial/Optimal equivalents إذا كانت موجودة ومستخدمة.

---

# 86. Realtime diagnostic

`realtimeState` diagnostic only:

```text
DISABLED
CONNECTING
CONNECTED
DEGRADED
STOPPED
```

لا يصبح readiness شرطًا للمزامنة.

---

# 87. Observability privacy

ممنوع logging/persistence لـ:

```text
access token
refresh token
Authorization header
full mutation payload
full financial body
receipt authoritative payload
attachment local URI if sensitive
password/OTP
Supabase session secret
```

---

# 88. Error privacy

`lastFailureCode` يجب أن يكون normalized code.

ممنوع حفظ raw exception message إذا قد يحتوي server body أو identifier حساس.

---

# 89. Log redaction scan

Verifier يفحص sync/recovery code الجديدة ويمنع patterns مثل:

```text
Authorization
Bearer 
access_token
refresh_token
payload=
mutation=
receipt=
```

في logs production الجديدة.

---

# 90. Observability artifact

إنشاء:

```text
docs/sync/VERTO_SYNC_OBSERVABILITY_v313.md
```

يوثق لكل metric:

```text
name
source
durability
scope
freshness semantics
privacy classification
correctness authority = false/true
```

الأغلب `correctness authority=false`.

---

# 91. Recovery reasons

على الأقل:

```text
INITIAL_BOOTSTRAP
CURSOR_EXPIRED
CURSOR_CORRUPT
SCOPE_CHANGED
LOCAL_ANCHOR_MISSING
RECONCILIATION_MISMATCH
MANUAL_SAFE_RESYNC
BOOTSTRAP_SESSION_RESTART
```

Reason لا يغير data semantics.

---

# 92. Manual Safe Resync

يجوز إضافة API داخلية/diagnostic:

```text
requestSafeFullResync(reason)
```

لكن لا UI redesign.

Manual resync تمر بنفس recovery engine؛ لا `clearAllTables()` shortcut.

---

# 93. Worker handling

في V2 path:

`SyncDrainResult.RECOVERY_REQUIRED` لا يبقى terminal success بلا recovery.

يجب أن يتحول إلى:

```text
run/resume recovery under bounded worker policy
or schedule one durable recovery continuation
```

---

# 94. Bounded worker behavior

Recovery لا تحتكر worker بلا حدود.

يجب budget للـbootstrap pages/rows per invocation.

إذا بقي work:

```text
CONTINUATION_SCHEDULED
```

مع durable token/state.

---

# 95. No recovery retry storm

Transient network أثناء bootstrap:

- WorkManager retry/backoff وفق 311.
- page token durable.
- لا restart من page 1 لمجرد timeout غير مؤكد إذا session/token لا تزال صالحة.

---

# 96. Permanent validation failure

Payload version unknown/aggregate unknown/divergent duplicate:

```text
RECOVERY_REQUIRED + diagnostic failure
```

لا infinite retry.

---

# 97. Auth failure

401/403 أثناء recovery:

```text
AUTH_BLOCKED
```

لا wipe local data ولا baseline cursor.

---

# 98. Stale scope

stale WorkManager input أثناء recovery:

```text
STALE_SCOPE → success/no-op for old worker
```

مع عدم cutover.

---

# 99. Server bootstrap visibility

كل snapshot row يجب أن تخضع لنفس trusted scope visibility:

```text
organization
principal
required_permission
scope_definition_version
```

لا cross-tenant row staging.

---

# 100. Visibility change mid-bootstrap

إذا scope definition أو principal visibility تغيرت:

- old bootstrap لا تتحول READY تلقائيًا.
- recovery تعيد resolve scope.
- mismatch يؤدي restart.

---

# 101. Snapshot coverage and visibility

Coverage 34/34 لا تعني أن كل مستخدم يرى كل row.

المطلوب:

```text
aggregate materializer exists
+ per-row visibility filtering remains correct
```

---

# 102. Server backfill privacy

Backfill migration لا تنسخ payload إلى logs/audit text خارج sync snapshot/change structures.

لا temporary plaintext dump artifacts داخل repo.

---

# 103. Historical migration integrity

قبول 313 يتطلب:

```text
v305 SHA unchanged
v309 SHA unchanged
v310 SHA unchanged
v312 SHA unchanged
```

أي اختلاف:

```text
FAIL_HISTORICAL_MIGRATION_DRIFT
```

---

# 104. SQL application policy

Static PASS لا يتطلب تنفيذ PostgreSQL.

إذا migration v313 موجودة ولم تنفذ:

```text
postgresRequiredForStaticPass = false
postgresExecuted              = false
serverMigrationDefined313     = true
serverMigrationApplied313     = false
bootstrapRuntimeVerified      = false
```

أي ادعاء application بدون execution:

```text
FAIL_SQL_RUNTIME_CLAIM
```

---

# 105. SQL runtime failure truth

إذا نفذت migration فعليًا وفشلت:

```text
BLOCKED_POSTGRES_RUNTIME_FAILURE
```

لا تحول إلى PASS_STATIC بعد محاولة runtime فاشلة دون توثيق blocker.

---

# 106. Room runtime/build truth

إذا Gradle/SDK غير متاحة أو static-only مصرح:

```text
compileStatus = NOT_RUN_ENVIRONMENT_UNAVAILABLE
```

أو:

```text
compileStatus = NOT_RUN_USER_AUTHORIZED_STATIC_ONLY
```

إذا Gradle command شغل فعليًا وفشل:

```text
BLOCKED_RUNTIME_FAILURE
```

---

# 107. Room migration model gates

حتى دون Android runtime يجب verifier يثبت static:

```text
80.json byte-identical
81.json exists
Migration(80,81) exists
MigrationCatalog includes it exactly once
Room entity/schema columns match
no DROP of pre-existing tables in 80→81
no clearAllTables in migration
```

---

# 108. Required v313 artifacts

إنشاء:

```text
VERTO_SYNC_RECOVERY_VERIFICATION_v313.json
VERTO_SYNC_RECOVERY_VERIFICATION_v313.md

docs/sync/VERTO_SYNC_BOOTSTRAP_COVERAGE_v313.csv
docs/sync/VERTO_SYNC_OUTBOX_PRESERVATION_v313.csv
docs/sync/VERTO_SYNC_RECOVERY_STATE_v313.md
docs/sync/VERTO_SYNC_BOOTSTRAP_POLICY_v313.md
docs/sync/VERTO_SYNC_RECONCILIATION_v313.md
docs/sync/VERTO_SYNC_OBSERVABILITY_v313.md

tools/verify_sync_recovery_v313.py
tools/test_sync_recovery_verification_v313.py
scripts/verify-v313-sync-recovery.sh

Verto-v313-report.md
```

---

# 109. Server migration artifact

إضافة exactly one:

```text
supabase/migrations/*_v313_*.sql
```

موضوعها:

```text
bootstrap snapshot completeness / recovery support only
```

لا push-policy redesign unrelated to snapshot continuity.

---

# 110. Static verifier determinism

`verify-v313-sync-recovery.sh` يشغل verifier مرتين.

normalized output hash يجب أن يتطابق.

عدم determinism:

```text
FAIL_V313_VERIFIER_NONDETERMINISTIC
```

---

# 111. v313 model fixtures — الحد الأدنى

إنشاء >= `350` fixture/model assertions خاصة بـ313.

تغطي على الأقل:

```text
fresh install/no cursor
non-empty Room/no cursor
partial Room/no cursor
valid READY + active cursor
cursor expired
blank cursor
scope mismatch cursor
missing last-applied inbox anchor
bootstrap page 1..N
bootstrap session expiry
bootstrap process death after page
bootstrap process death before cutover
crash inside cutover transaction
crash after cutover before stage cleanup
snapshot row duplicate same fingerprint
snapshot row duplicate divergent fingerprint
unknown aggregate
wrong payload version
wrong tenant bootstrap row
wrong scope bootstrap session
visibility change mid-bootstrap
34 aggregate coverage
17 owner310 snapshot continuity
existing-data backfill coverage
secondary stronger snapshot continuity
server DELETE absent from snapshot
ARCHIVE preserved as state
VOID/REVERSE preserved as state
pending unified mutation during recovery
pending Party mutation
pending financial outbox
pending inventory stock outbox
pending inventory cost outbox
pending Optimal outbox
pending attachment transfer
leased mutation during recovery
local write during staging
outbox identity digest preservation
no fake SyncChange bootstrap apply
no command replay financial
no command replay inventory
atomic cursor install
no timestamp recovery cursor
recovery while active drain
logout during bootstrap
org switch during bootstrap
same-org reauth during bootstrap
stale worker recovery
transient bootstrap retry
permanent validation failure
reconciliation equal
reconciliation mismatch
reconciliation pending-local defer
scoped repair no cursor jump
full recovery escalation
health metrics aggregation
log redaction
Runtime V2 OFF
Realtime OFF
historical SQL unchanged
schema80 unchanged
Room 81 migration
```

---

# 112. Required named model passes

الـJSON النهائي يجب أن يحتوي true على الأقل:

```text
MODEL_INITIAL_BOOTSTRAP_STATE_PASS
MODEL_NONEMPTY_ROOM_MISSING_CURSOR_RECOVERY_PASS
MODEL_CURSOR_EXPIRED_RECOVERY_PASS
MODEL_CURSOR_CORRUPTION_RECOVERY_PASS
MODEL_MISSING_INBOX_ANCHOR_RECOVERY_PASS
MODEL_BOOTSTRAP_GAP_FREE_PASS
MODEL_BOOTSTRAP_PAGE_RESUME_PASS
MODEL_BOOTSTRAP_SESSION_EXPIRED_RESTART_PASS
MODEL_BOOTSTRAP_PROCESS_DEATH_PASS
MODEL_ATOMIC_RECOVERY_CUTOVER_PASS
MODEL_FULL_RESYNC_PRESERVES_OUTBOX_PASS
MODEL_PENDING_LOCAL_OVERLAY_PASS
MODEL_LOCAL_WRITE_DURING_BOOTSTRAP_PASS
MODEL_FULL_RESYNC_DELETE_PRUNE_PASS
MODEL_NO_FAKE_CHANGE_BOOTSTRAP_PASS
MODEL_NO_RECOVERY_COMMAND_REPLAY_PASS
MODEL_FINANCIAL_RECOVERY_NO_DUPLICATE_EFFECT_PASS
MODEL_INVENTORY_RECOVERY_NO_DUPLICATE_EFFECT_PASS
MODEL_SNAPSHOT_34_AGGREGATE_COVERAGE_PASS
MODEL_OWNER310_SNAPSHOT_CONTINUITY_PASS
MODEL_EXISTING_DATA_BACKFILL_PASS
MODEL_STRONGER_SECONDARY_SNAPSHOT_PASS
MODEL_SCOPE_SESSION_RECOVERY_PASS
MODEL_LOGOUT_RECOVERY_RACE_PASS
MODEL_ORG_SWITCH_RECOVERY_RACE_PASS
MODEL_SAME_ORG_REAUTH_RECOVERY_PASS
MODEL_RECONCILIATION_MANIFEST_PASS
MODEL_RECONCILIATION_PENDING_DEFER_PASS
MODEL_RECONCILIATION_ESCALATION_PASS
MODEL_OBSERVABILITY_PRIVACY_PASS
MODEL_NO_TIMESTAMP_RECOVERY_AUTHORITY_PASS
MODEL_V2_DEFAULT_OFF_PASS
MODEL_REALTIME_DEFAULT_OFF_PASS
MODEL_ROOM_80_TO_81_PASS
```

---

# 113. Fresh install test

Scenario:

```text
fresh Room 81
trusted session
no cursor
recovery state absent
```

PASS:

```text
NOT_STARTED → bootstrap
no legacy timestamp full sync on V2 path
baseline cursor installed only after snapshot cutover
```

---

# 114. Non-empty Room/no cursor test

Scenario:

```text
Room contains stale/cache rows
no active cursor
```

PASS:

```text
bootstrap still runs
Room-nonempty does not suppress initial recovery
```

---

# 115. Cursor expiry test

Scenario:

```text
remote pull returns CURSOR_EXPIRED
```

PASS:

```text
no cursor jump
no timestamp fallback
outbox preserved
recovery durable
bootstrap begins/resumes
```

---

# 116. Full resync with pending mutation test

Scenario:

```text
pending mutation M exists before recovery
server snapshot baseline does not include M yet
```

PASS:

```text
same mutation identity survives cutover
local intent not silently overwritten
post-recovery push/reconcile can complete M exactly once
```

---

# 117. Full resync after delete test

Scenario:

```text
local X exists
server authoritative snapshot omits X
no pending local X mutation
```

PASS:

```text
X removed in cutover
no synthetic delete outbox
```

---

# 118. App reinstall test

App reinstall / new Room:

```text
no local cursor
no recovery state
server account has existing data
```

PASS:

```text
bootstrap restores mirror
no dependence on legacy last-pulled timestamps
```

---

# 119. Cache partial state test

Scenario:

```text
cursor claims applied revision R
required local inbox anchor/domain manifest corrupted
```

PASS:

```text
fail closed to RECOVERY_REQUIRED
no continue-from-corrupt-cursor
```

---

# 120. Bootstrap session expiry test

Scenario:

```text
stage pages 1..k
server session expires
```

PASS:

```text
stale stage session discarded
outboxes unchanged
new bootstrap session starts
old baseline cursor not reused
```

---

# 121. Atomic cutover crash test

Inject failure halfway through domain materialization.

PASS:

```text
Room transaction rolls back
old mirror/cursor remain coherent
recovery remains retryable
```

---

# 122. Snapshot continuity coverage test

أولًا يجب إثبات أن owner307 generic paths التي كان v309 يحدث snapshot لها لم تعد بلا continuity بعد replacement v310.

ثم لكل 17 owner310 aggregate:

```text
future APPLIED stronger change → snapshot/materializer updated atomically
```

وexisting-data materialization موجودة.

أي aggregate ناقصة:

```text
FAIL_OWNER310_BOOTSTRAP_COVERAGE
```

---

# 123. 34-aggregate coverage test

`VERTO_SYNC_BOOTSTRAP_COVERAGE_v313.csv`:

```text
rows = 34
unique aggregate_type = 34
set == UnifiedSyncAggregateRegistry.byId.keys
```

---

# 124. Reconciliation equal test

بعد idle/outbox-empty:

```text
server manifest == local canonical manifest
```

PASS:

```text
status = CONVERGED
no recovery scheduled
```

---

# 125. Reconciliation mismatch test

بدون pending local mutation:

```text
manifest mismatch
```

PASS:

```text
scoped safe repair or full recovery escalation
never cursor jump
```

---

# 126. Privacy test

Verifier/model يفحص:

```text
health snapshots
persisted reports
logs
failure normalization
```

PASS:

```text
no token
no Authorization
no full payload
no full financial body
```

---

# 127. v312 regression gate

يجب إعادة:

```text
300/300 v312 fixtures
```

ويظل:

```text
realtimeDirectRoomMutationCount       = 0
realtimeCursorWriteCount              = 0
lateStopKillsNewListenerCount         = 0
lateCallbackAcceptedCount             = 0
realtimeTargetBusyLoopCount           = 0
new312WaiverCount                     = 0
```

---

# 128. v311 regression gate

يجب إعادة:

```text
325/325 v311 fixtures
```

ويظل:

```text
lostSyncIntentCount                   = 0
immediateKeepCorrectnessCount         = 0
idleCasViolationCount                 = 0
finalExitRaceLossCount                = 0
staleSessionEpochExecutionCount       = 0
oldRealtimeCrossTenantRequestCount    = 0
new311WaiverCount                     = 0
```

---

# 129. v310 regression gate

يجب إعادة:

```text
499/499 v310 fixtures
```

ويظل:

```text
duplicateFinancialEffectViolationCount = 0
duplicateInventoryEffectViolationCount = 0
duplicateOptimalEffectViolationCount   = 0
owner310LwwFallbackCount               = 0
remoteApplyEnqueueCount                = 0
```

---

# 130. v309 regression gate

يجب إعادة:

```text
272/272 v309 fixtures
```

Idempotency/receipt/conflict semantics لا تتغير.

---

# 131. v308 regression gate

يجب إعادة:

```text
137/137 v308 fixtures
MODEL_10K_PASS
```

ويظل:

```text
timestampV2CursorAuthorityCount = 0
atomic cursor violations         = 0
remote apply enqueue echo        = 0
```

---

# 132. New waivers

الحاكم:

```text
new313WaiverCount = 0
```

أي waiver جديدة تمنع PASS.

---

# 133. Required failure codes

على الأقل:

```text
BLOCKED_INPUT_DRIFT
BLOCKED_SCOPE_DRIFT
BLOCKED_RUNTIME_FAILURE
BLOCKED_POSTGRES_RUNTIME_FAILURE
FAIL_SCHEMA80_DRIFT
FAIL_ROOM_81_MIGRATION
FAIL_BOOTSTRAP_SNAPSHOT_COVERAGE
FAIL_OWNER310_BOOTSTRAP_COVERAGE
FAIL_EXISTING_DATA_BACKFILL_GAP
FAIL_SNAPSHOT_CHANGE_ATOMICITY
FAIL_BOOTSTRAP_INCOMPLETE
FAIL_BOOTSTRAP_SCOPE_MISMATCH
FAIL_BOOTSTRAP_SESSION_REUSE_AFTER_EXPIRY
FAIL_BOOTSTRAP_DUPLICATE_DIVERGENT_ROW
FAIL_BOOTSTRAP_UNKNOWN_AGGREGATE
FAIL_BOOTSTRAP_PAYLOAD_VERSION
FAIL_RECOVERY_OUTBOX_LOSS
FAIL_RECOVERY_STRONGER_OUTBOX_LOSS
FAIL_RECOVERY_ATTACHMENT_INTENT_LOSS
FAIL_RECOVERY_CLEAR_ALL_TABLES
FAIL_RECOVERY_NONATOMIC_CUTOVER
FAIL_RECOVERY_CURSOR_INSTALL_EARLY
FAIL_RECOVERY_CURSOR_JUMP
FAIL_RECOVERY_TIMESTAMP_AUTHORITY
FAIL_RECOVERY_FAKE_CHANGE_APPLY
FAIL_RECOVERY_FINANCIAL_DUPLICATE_EFFECT
FAIL_RECOVERY_INVENTORY_DUPLICATE_EFFECT
FAIL_RECOVERY_PENDING_OVERWRITE
FAIL_STALE_RECOVERY_SCOPE
FAIL_CROSS_TENANT_RECOVERY_STAGE
FAIL_RECONCILIATION_CURSOR_ADVANCE
FAIL_RECONCILIATION_PENDING_FALSE_POSITIVE
FAIL_RECOVERY_RETRY_STORM
FAIL_SENSITIVE_SYNC_DIAGNOSTIC
FAIL_SYNC_SECRET_LOGGING
FAIL_HISTORICAL_MIGRATION_DRIFT
FAIL_312_REGRESSION
FAIL_311_REGRESSION
FAIL_310_REGRESSION
FAIL_309_REGRESSION
FAIL_308_REGRESSION
FAIL_RUNTIME_CUTOVER_EARLY
FAIL_SQL_RUNTIME_CLAIM
FAIL_V313_VERIFIER_NONDETERMINISTIC
```

---

# 134. Acceptance counters — must equal zero

```text
bootstrapSnapshotCoverageGapCount
owner310BootstrapCoverageGapCount
existingDataBackfillGapCount
snapshotChangeAtomicityViolationCount
bootstrapIncompleteAcceptedCount
expiredBootstrapSessionReusedCount
bootstrapDuplicateDivergentAcceptedCount
unknownBootstrapAggregateAcceptedCount
wrongBootstrapPayloadVersionAcceptedCount
recoveryOutboxLostCount
recoveryStrongerOutboxLostCount
recoveryAttachmentIntentLostCount
recoveryClearAllTablesCount
nonAtomicRecoveryCutoverCount
earlyBaselineCursorInstallCount
recoveryCursorJumpCount
recoveryTimestampAuthorityCount
fakeBootstrapSyncChangeCount
recoveryFinancialDuplicateEffectCount
recoveryInventoryDuplicateEffectCount
pendingLocalOverwriteCount
staleRecoveryScopeAcceptedCount
crossTenantRecoveryStageCount
reconciliationCursorAdvanceCount
reconciliationPendingFalsePositiveCount
recoveryRetryStormCount
sensitiveSyncDiagnosticCount
syncSecretLoggingCount
historicalMigrationChangedCount
schema80ChangedCount
new313WaiverCount
v312RegressionFailures
v311RegressionFailures
v310RegressionFailures
v309RegressionFailures
v308RegressionFailures
runtimeV2EnabledCount
realtimeDefaultEnabledCount
```

---

# 135. Acceptance counters — exact/positive values

```text
inputArchiveEntries                = 2664
inputProductionKotlinCount         = 1179
inheritedExceptionCount            = 9
aggregateRegistryCount             = 34
directAggregateCount               = 17
owner310AggregateCount             = 17
roomVersionBefore/After            = 80/81
supabaseVersion                    = 3.0.2
workManagerVersion                 = 2.10.0
bootstrapCoverageRows              = 34
bootstrapCoverageUniqueAggregates  = 34
outboxPreservationOwnerRows        >= 7
v313StaticFixtureCount             >= 350
v312RegressionFixtureCount         = 300
v311RegressionFixtureCount         = 325
v310RegressionFixtureCount         = 499
v309RegressionFixtureCount         = 272
v308RegressionFixtureCount         = 137
serverMigrationV313Count           = 1
```

---

# 136. v313 verification JSON

إنشاء:

```text
VERTO_SYNC_RECOVERY_VERIFICATION_v313.json
```

يحتوي على الأقل:

```text
session
inputZipName
inputZipSha256
inputArchiveEntries
inputProductionKotlinCount
planSha256
session312ContractSha256
v312VerificationSha256
v312FinalVerdict
v312Handoff313Authorized
inheritedExceptionCount
new313WaiverCount
roomVersionBefore
roomVersionAfter
schema80Sha256
schema81Sha256
migration8081Sha256
aggregateRegistryCount
directAggregateCount
owner310AggregateCount
bootstrapCoverageRows
owner310BootstrapCoverageRows
existingDataBackfillGapCount
outboxPreservationOwnerRows
bootstrapSnapshotCoverageGapCount
snapshotChangeAtomicityViolationCount
recoveryOutboxLostCount
recoveryStrongerOutboxLostCount
recoveryAttachmentIntentLostCount
recoveryCursorJumpCount
recoveryTimestampAuthorityCount
fakeBootstrapSyncChangeCount
recoveryFinancialDuplicateEffectCount
recoveryInventoryDuplicateEffectCount
pendingLocalOverwriteCount
staleRecoveryScopeAcceptedCount
crossTenantRecoveryStageCount
reconciliationCursorAdvanceCount
sensitiveSyncDiagnosticCount
syncSecretLoggingCount
historicalMigrationChangedCount
serverMigrationDefined313
serverMigrationApplied313
bootstrapRuntimeVerified
v312RegressionFailures
v311RegressionFailures
v310RegressionFailures
v309RegressionFailures
v308RegressionFailures
v313FixtureStats
runtimeV2
realtimeRuntime
compileStatus
unitTestStatus
postgresRequiredForStaticPass
postgresExecuted
finalVerdict
blockers
handoff314Authorized
```

---

# 137. v313 verification Markdown

إنشاء:

```text
VERTO_SYNC_RECOVERY_VERIFICATION_v313.md
```

Human-readable ومطابق للـJSON.

---

# 138. Expected PASS verdict

إذا نجحت كل static gates:

```text
PASS_STATIC_RECOVERY_BOOTSTRAP_CURSOR_OBSERVABILITY
/ INHERITED_307_EXCEPTIONS=9
/ ROOM_81
/ SNAPSHOT_COVERAGE_34_STATIC
/ V2_DISABLED
/ REALTIME_DEFAULT_DISABLED
/ BUILD_NOT_VERIFIED
/ POSTGRES_NOT_EXECUTED
/ V313_RECOVERY_SQL_STATIC_ONLY
```

ولا تستخدم:

```text
POSTGRES_VERIFIED
BOOTSTRAP_RUNTIME_VERIFIED
MULTI_DEVICE_VERIFIED
```

إلا بعد تنفيذ فعلي مناسب.

---

# 139. معنى PASS في 313

PASS يعني حصرًا:

> يوجد recovery/bootstrap path durable ومحدد، يحفظ unacked local intent، يمرحّل snapshot كاملة وآمنة عبر staging ثم cutover ذري، يعالج cursor expiry/corruption دون timestamps أو cursor jumps، يثبت تغطية bootstrap لكل 34 aggregate بما فيها owner310، ويعرض تشخيصًا privacy-safe، بينما يبقى V2/Realtime افتراضيًا معطلين.

ولا يعني:

```text
server migration applied in production
Android build verified
multi-device convergence runtime proven
fault injection complete
V2 ready for default-on
legacy safe to delete
all inherited 307 exceptions removed
```

---

# 140. Report honesty

ممنوع كتابة:

```text
"full resync is production verified"
"all server snapshots are runtime verified"
"cursor expiry recovery was tested on PostgreSQL"
"Room migration 81 was device-tested"
"multi-device convergence is proven"
"pending mutations can never be lost under every legacy producer"
```

بدون runtime evidence.

الصحيح:

```text
Recovery correctness is statically/model verified; runtime server/device execution remains separately gated.
```

---

# 141. Required implementation order

ينفذ بالترتيب:

```text
A. verify v312 ZIP SHA/entry count/Kotlin count
B. verify SESSION_312 SHA
C. verify v312 verification SHA + handoff313Authorized=true
D. freeze schema80 + historical SQL hashes
E. carry forward exactly 9 inherited exceptions
F. inventory current bootstrap/recovery/cursor paths
G. inventory all 34 aggregate pull/materialization owners
H. inventory all active outbox owners
I. prove current owner310 snapshot continuity gap
J. define Room81 recovery/stage/health schema
K. implement MIGRATION_80_81 + schema81
L. define bootstrap wire/client remote
M. define durable recovery state machine
N. implement page staging + fingerprint validation
O. implement bootstrap resume/restart
P. define 34-aggregate recovery registry
Q. define pending local overlay strategy per aggregate
R. implement snapshot materialization without fake SyncChange
S. implement atomic cutover + cursor install
T. implement cursor corruption/expiry routing
U. replace V2 initial-sync Room-empty authority
V. integrate recovery into 311 durable drain
W. implement process-death continuation
X. create v313 server migration for snapshot completeness
Y. cover owner310 primary + secondary snapshot continuity
Z. define existing-data backfill/materialization for 34/34
AA. preserve visibility/scope rules
AB. bind reconciliation manifest client
AC. implement local canonical manifest + pending defer
AD. implement scoped repair/fallback policy
AE. implement privacy-safe SyncHealthSnapshot
AF. add DAO counters for all outbox/conflict owners
AG. ensure no secret/payload logging
AH. keep V2 + Realtime defaults OFF
AI. emit v313 coverage/policy artifacts
AJ. run >=350 v313 fixtures
AK. rerun 300 v312 fixtures
AL. rerun 325 v311 fixtures
AM. rerun 499 v310 fixtures
AN. rerun 272 v309 fixtures
AO. rerun 137 v308 fixtures + MODEL_10K_PASS
AP. run verifier twice deterministically
AQ. emit JSON/MD reports
AR. verify historical SQL byte-identical
AS. document v313 SQL as NOT_EXECUTED unless actually run
AT. package v313 ZIP + SHA
```

---

# 142. Required file-scope discipline

مسموح مبدئيًا تعديل/إضافة:

```text
data/database/.../AppDatabase.kt
data/database/.../MigrationCatalog.kt
data/database/.../AppDatabaseMigrations80To81.kt
data/database/.../entity/*SyncRecovery* / *Bootstrap* / *Health*
data/database/.../dao/UnifiedSyncDao.kt
app/schemas/.../AppDatabase/81.json

data/network/.../UnifiedSyncBootstrapRemote.kt
data/network/.../UnifiedSyncBootstrapWire.kt

data/sync/.../recovery/*
data/sync/.../SyncManager.kt
data/sync/.../SyncWorker.kt
data/sync/.../SyncReliability.kt
data/sync/.../pull/UnifiedSyncPullEngine.kt       # recovery routing/anchor validation only

data/sync/.../pull/*Snapshot*Applier.kt
app/.../sync presentation bridge                 # data exposure only, no redesign
app/.../DefaultAuthSessionCoordinator.kt          # initial/recovery scheduling only

supabase/migrations/*_v313_*.sql                  # exactly one additive recovery migration

docs/sync/*v313*
tools/*v313*
scripts/*v313*
verification/report artifacts
```

---

# 143. Forbidden file-scope drift

ممنوع دون `BLOCKED_SCOPE_DRIFT`:

```text
UI redesign
business feature refactors
financial business-rule changes
inventory costing changes
Optimal domain redesign
legacy removal
Gradle dependency upgrades
FeatureFlag default-on
Realtime redesign
push conflict-policy redesign unrelated to snapshot continuity
change contract version
aggregate registry expansion
```

---

# 144. Protected data-plane files

الأصل أن تبقى byte-identical:

```text
UnifiedSyncPushEngine.kt
UnifiedSyncConflictEngine.kt
UnifiedStrongerSyncBridge.kt
UnifiedStrongerSourceFactory.kt
UnifiedSyncPushRegistry.kt
financial/inventory/optimal business writers
RealtimeManager.kt
RealtimeHintCoalescer.kt
OrganizationRealtimeSource.kt
SupabaseOrganizationRealtimeSource.kt
```

`UnifiedSyncChangeApplier.kt` يفضل أن يبقى unchanged ويضاف snapshot applier مستقل.

أي استخدام command writer داخل snapshot recovery:

```text
BLOCKED_RECOVERY_DATA_PLANE_DRIFT
```

---

# 145. Unified Pull protection

`UnifiedSyncPullEngine` يبقى revision-feed authority بعد READY.

Recovery لا تستبدله.

بعد bootstrap baseline:

```text
normal pull starts from baseline_cursor and advances atomically as in 308
```

---

# 146. Realtime protection

312 guarantees تبقى:

```text
Realtime = hint only
no direct Room mutation
no cursor write
no delivery guarantee
```

Recovery لا تجعل Realtime prerequisite.

---

# 147. No silent partial PASS

PASS ممنوع إذا تحقق أي واحد:

```text
client still has no bootstrap execution path
initial V2 sync still depends only on Room-empty
bootstrap pages apply live before complete stage
full resync uses clearAllTables
one active outbox can be erased
bootstrap cursor installed before domain cutover
cutover is not one Room transaction
bootstrap uses fake SyncChange revision
financial/inventory snapshot replays commands
cursor expiry advances cursor
recovery uses timestamp cursor
effective snapshot continuity is missing for any v310 APPLIED path
owner310 snapshot coverage < 17
aggregate bootstrap coverage < 34
existing-data backfill undefined for one aggregate
snapshot continuity not atomic with stronger change
wrong tenant bootstrap row can stage
expired bootstrap session cursor can be reused
reconciliation can advance global cursor
pending local mutation causes silent overwrite
health/logging leaks secrets/payload
Room != 81
schema80 changed
historical v305/v309/v310/v312 SQL changed
one new waiver
one 312 regression
one 311 regression
one 310 regression
one 309 regression
one 308 regression
V2 default ON
Realtime default ON
runtime SQL claimed without execution
```

---

# 148. Packaging

المخرج المتوقع بعد تنفيذ 313:

```text
Verto-v313-source-of-truth.zip
Verto-v313-source-of-truth.zip.sha256
```

لا build caches/secrets/extracted duplicate input.

---

# 149. Mandatory artifacts inside v313 archive

```text
VERTO_SYNC_RECOVERY_VERIFICATION_v313.json
VERTO_SYNC_RECOVERY_VERIFICATION_v313.md
docs/sync/VERTO_SYNC_BOOTSTRAP_COVERAGE_v313.csv
docs/sync/VERTO_SYNC_OUTBOX_PRESERVATION_v313.csv
docs/sync/VERTO_SYNC_RECOVERY_STATE_v313.md
docs/sync/VERTO_SYNC_BOOTSTRAP_POLICY_v313.md
docs/sync/VERTO_SYNC_RECONCILIATION_v313.md
docs/sync/VERTO_SYNC_OBSERVABILITY_v313.md
tools/verify_sync_recovery_v313.py
tools/test_sync_recovery_verification_v313.py
scripts/verify-v313-sync-recovery.sh
supabase/migrations/*_v313_*.sql
app/schemas/com.verto.app.data.local.AppDatabase/81.json
Verto-v313-report.md
```

---

# 150. Output archive integrity

بعد packaging:

1. compute SHA-256.
2. count entries.
3. test ZIP open.
4. verify mandatory v313 artifacts.
5. verify schema80 unchanged.
6. verify schema81 present.
7. verify exactly one 80→81 migration.
8. verify v305/v309/v310/v312 migrations unchanged.
9. verify exactly one new v313 server migration.
10. verify bootstrap coverage 34/34.
11. verify owner310 coverage 17/17.
12. verify no `clearAllTables()` in V2 recovery path.
13. verify no outbox tables in recovery clear allowlist.
14. verify no fake SyncChange bootstrap construction.
15. verify no recovery cursor timestamp authority.
16. verify Runtime V2 OFF.
17. verify Realtime default OFF.
18. verify no secrets/cache artifacts.
19. write SHA sidecar.

---

# 151. Final acceptance questions

قبل PASS يجب الإجابة **نعم**:

1. هل input ZIP SHA = `acab0811...`؟
2. هل archive entries = `2664`؟
3. هل production Kotlin baseline = `1179`؟
4. هل SESSION_312 SHA = `3b2c829a...`؟
5. هل v312 verification SHA = `15972df3...`؟
6. هل `handoff313Authorized=true`؟
7. هل v312 blockers فارغة؟
8. هل inherited exceptions exactly 9؟
9. هل `new313WaiverCount=0`؟
10. هل Room أصبح 81 فقط للأغراض المسموحة؟
11. هل schema80 byte-identical؟
12. هل 80→81 migration additive؟
13. هل schema81 exported؟
14. هل v305/v309/v310/v312 SQL byte-identical؟
15. هل هناك migration v313 واحدة فقط؟
16. هل client bootstrap remote موجود؟
17. هل durable recovery state موجودة؟
18. هل durable bootstrap staging موجودة؟
19. هل initial V2 sync لم يعد Room-empty authority فقط؟
20. هل no cursor → bootstrap؟
21. هل CURSOR_EXPIRED → recovery دون cursor jump؟
22. هل blank/corrupt cursor fail closed؟
23. هل missing last-applied inbox anchor يطلب recovery؟
24. هل bootstrap snapshot+baseline من handshake واحدة؟
25. هل baseline cursor opaque؟
26. هل baseline revision diagnostic only؟
27. هل bootstrap pages لا تطبق domain مباشرة؟
28. هل staged rows == expected rows قبل cutover؟
29. هل expired bootstrap session يعاد من البداية دون reuse cursor؟
30. هل process death يستأنف من durable page token؟
31. هل cutover Room transaction واحدة؟
32. هل cursor install داخل نفس cutover transaction؟
33. هل `clearAllTables()` غير مستخدم في V2 recovery؟
34. هل جميع unacked unified outbox rows محفوظة؟
35. هل Party outbox محفوظة؟
36. هل financial outbox محفوظة؟
37. هل inventory stock/cost outboxes محفوظة؟
38. هل Optimal outbox محفوظة؟
39. هل attachment intent محفوظة؟
40. هل pending mutation أثناء staging لا تضيع؟
41. هل bootstrap لا ينشئ fake SyncChange؟
42. هل snapshot apply لا ينفذ commands؟
43. هل financial duplicate effects = 0؟
44. هل inventory duplicate effects = 0؟
45. هل bootstrap coverage = 34/34؟
46. هل owner310 coverage = 17/17؟
47. هل existing-data backfill/materialization محددة 34/34؟
48. هل owner310 future snapshot continuity ذرية؟
49. هل secondary changes snapshot-covered؟
50. هل DELETE absent rows تزال safely؟
51. هل pending local overlay له disposition لكل aggregate؟
52. هل wrong tenant/scope bootstrap row no-op/fail؟
53. هل logout race آمن؟
54. هل org switch race آمن؟
55. هل same-org reauth stale recovery مرفوض؟
56. هل reconciliation anti-entropy فقط؟
57. هل reconciliation لا تقدم global cursor؟
58. هل pending local mutation تؤجل reconciliation false-positive؟
59. هل mismatch يصعد إلى safe repair/full recovery؟
60. هل health snapshot تشمل lag/outboxes/conflicts/retries؟
61. هل last observed server revision موصوف بصدق؟
62. هل secrets/tokens/payloads ممنوعة من logs؟
63. هل >=350 v313 fixtures PASS؟
64. هل 300/300 v312 regression PASS؟
65. هل 325/325 v311 regression PASS؟
66. هل 499/499 v310 regression PASS؟
67. هل 272/272 v309 regression PASS؟
68. هل 137/137 v308 + MODEL_10K PASS؟
69. هل verifier deterministic مرتين؟
70. هل Runtime V2 بقي OFF؟
71. هل Realtime default بقي OFF؟
72. هل SQL runtime status موثق بصدق؟
73. هل التقرير لا يدعي runtime proof غير منفذ؟

أي `لا` تمنع PASS.

---

# 152. Handoff إلى 314

فقط عند نجاح كل static gates:

```text
handoff314Authorized = true
```

314 يجوز أن تفترض:

```text
V2 has a durable bootstrap/recovery state machine
cursor expiry routes to safe recovery
full resync preserves unacked intent by contract/model proof
bootstrap snapshot coverage is 34/34 statically defined
owner310 snapshot continuity is statically defined
reconciliation diagnostics exist
privacy-safe health metrics exist
Room is 81
Runtime V2 and Realtime defaults remain OFF
```

314 لا يجوز أن تفترض:

```text
PostgreSQL v313 migration applied
Android migration runtime verified
multi-device convergence proven
fault injection passed
production rollout safe
legacy removable
```

---

# 153. Handoff شرط runtime في 314

قبل أي default-on في 314 يجب تنفيذ runtime evidence المناسبة، خصوصًا:

```text
80→81 migration on real Room
v313 server migration on PostgreSQL/staging
fresh bootstrap
cursor-expiry bootstrap
pending mutation recovery
process death
multi-device convergence
```

Static PASS 313 لا يساوي rollout authority.

---

# 154. الخلاصة النهائية للعقد

313 يجب أن تنهي المرحلة بهذه الحقيقة:

```text
Missing or expired cursor no longer means "stop".
It means durable, scope-safe recovery.

Bootstrap is staged before it touches the live mirror.
The baseline cursor belongs to the same trusted snapshot handshake.

Full resync may rebuild cache, but it cannot erase unacked intent.
Pending mutations survive process death and cutover.

Bootstrap rows are materialized state, not commands and not fake change-log events.
Financial and inventory effects are never replayed to reconstruct cache.

All 34 aggregates have explicit bootstrap coverage.
The 17 stronger owner310 aggregates have continuous snapshot/materializer coverage.
Pre-existing server data has a defined bootstrap materialization/backfill path.

Cursor expiry never falls back to timestamps.
Reconciliation detects divergence but never advances the global cursor.
Observability explains sync health without exposing secrets or payloads.

Room moves only from 80 to 81 for recovery durability.
Historical server migrations remain immutable.
V2 and Realtime remain OFF until 314 proves runtime convergence and rollout safety.
```

إذا تعذر إثبات snapshot completeness أو outbox preservation أو atomic cutover، التنفيذ يتوقف fail-closed بدل إعلان PASS جزئي.
