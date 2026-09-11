# SESSION_312_FINAL.md

## Verto Sync Modernization — Session 312

### Realtime Hint-Only Accelerator — Targeted Revision Catch-Up, Burst Coalescing, Tenant-Safe Lifecycle & Publication Safety

**نوع المستند:** عقد تنفيذ مستقل ونهائي  
**الجلسة:** 312  
**الحالة:** `PLAN ONLY — EXECUTABLE ON V311 STATIC-PASS BASELINE`  
**تاريخ الصياغة:** 2026-08-21  
**مصدر الكود المفحوص:** `Verto-v311-source-of-truth.zip`  
**SHA-256 للمصدر المفحوص:** `89700b20d832bf9f787d7d0139afb55315a3484be1077c85d2c50dd183c166ca`  
**Archive entries:** `2650`  
**Production Kotlin files:** `1178`  
**عقد 311 المرجعي:** `SESSION_311_FINAL.md`  
**SHA-256 لعقد 311:** `cc788ce40ba5e8fb307d11d410f4d5d364a032a1829f9d2bae478ae012d6685f`  
**الخطة الأم:** `VERTO_SYNC_MODERNIZATION_PLAN_v304-v314.md`  
**SHA-256 للخطة:** `a767087c5f1659dcd7c660c189542ef2d7774c0f647ff9321e9f53219a7dfd97`  
**Contract authority:** `verto-unified-sync / 1`  
**Room الحالي المثبت:** `80`  
**Room المستهدف في 312:** `80` — لا Room migration مطلوبة.  
**Supabase Kotlin BOM المثبت:** `3.0.2`  
**WorkManager المثبت:** `2.10.0`  
**Runtime V2 عند البداية:** `OFF`  
**Realtime runtime عند البداية:** `OFF`  
**312 acceptance basis:** الفحوص الساكنة/model verification إلزامية. Gradle/Android/PostgreSQL runtime ليست شرطًا لـ`PASS_STATIC` إذا البيئة غير متاحة أو التنفيذ runtime متجاوز صراحة. أي أمر runtime يتم تشغيله فعليًا ويفشل يبقى فشلًا حقيقيًا. يجوز لـ312 إنشاء migration خادم جديدة خاصة بسطح Realtime الآمن عند الحاجة، لكن لا تعتبر مطبقة على PostgreSQL ما لم تُنفذ فعليًا وتنجح.

---

# 0. الحكم التنفيذي المختصر

312 ليست جلسة Data Plane جديدة، ولا Bootstrap، ولا Cutover.

هي جلسة تجعل Realtime **مسرّعًا اختياريًا فقط** فوق نظام 311 durable drain:

```text
Supabase Realtime event
        │
        ▼
Tenant/session/lifecycle validation
        │
        ▼
SyncRealtimeHint
(org, aggregate?, id?, serverRevision?)
        │
        ▼
Bounded coalescer
  ├─ dedupe duplicate hints
  ├─ retain max safe revision hint
  ├─ bound aggregate targets
  └─ overflow → normal revision pull
        │
        ▼
Persist/wake durable generation from 311
        │
        ▼
Unified revision pull accelerator
  ├─ no direct Room mutation from Realtime
  ├─ no cursor write from Realtime
  ├─ no timestamp ordering
  └─ no filtered cursor jump
        │
        ▼
Normal 311 idle proof / fallback convergence
```

القاعدتان الحاكمتان:

```text
Realtime is an accelerator, never a correctness authority.
```

```text
A Realtime hint may cause earlier work, but it may never advance a cursor,
apply a payload directly, or weaken tenant/session visibility rules.
```

---

# 1. بوابة البداية من 311

الحالة المثبتة داخل `VERTO_SYNC_ORCHESTRATION_VERIFICATION_v311.json`:

```text
finalVerdict = PASS_STATIC_DURABLE_DRAIN_RETRY_TENANT_ORCHESTRATION
               / INHERITED_307_EXCEPTIONS=9
               / ROOM_80_UNCHANGED
               / SERVER_SQL_UNCHANGED
               / RUNTIME_V2_DISABLED
               / BUILD_NOT_VERIFIED

v311 fixtures              = 325/325 PASS
v310 regression fixtures   = 499/499 PASS
v309 regression fixtures   = 272/272 PASS
v308 regression fixtures   = 137/137 PASS
MODEL_10K_PASS             = true
new311WaiverCount          = 0
runtimeV2                  = DISABLED
handoff312Authorized       = true
blockers                   = []
```

312 لا تبدأ إذا تغير هذا baseline دون توثيق:

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

قواعد 312:

1. تحمل كما هي في artifact مستقل.
2. لا يعاد تفسيرها كـPASS نظيف.
3. `new312WaiverCount = 0` شرط PASS.
4. أي استثناء تلمسه 312 يجب أن يظل `worsened=false`.
5. لا يستخدم أي استثناء لتبرير direct Realtime apply أو cross-tenant hint أو stale listener أو cursor jump.

---

# 3. ترتيب السلطات — Authority Order

عند التعارض:

1. `Verto-v311-source-of-truth.zip` ذو SHA المثبت.
2. `VERTO_SYNC_ORCHESTRATION_VERIFICATION_v311.json/.md`.
3. `docs/sync/VERTO_SYNC_REQUEST_SOURCES_v311.csv`.
4. `docs/sync/VERTO_SYNC_RETRY_MATRIX_v311.csv`.
5. `docs/sync/VERTO_SYNC_TENANT_TRANSITIONS_v311.csv`.
6. `docs/sync/VERTO_SYNC_DRAIN_STATE_v311.md`.
7. `SESSION_311_FINAL.md`.
8. `VERTO_SYNC_STRONGER_VERIFICATION_v310.json/.md`.
9. `VERTO_SYNC_PUSH_VERIFICATION_v309.json/.md`.
10. `VERTO_SYNC_PULL_VERIFICATION_v308.json/.md`.
11. `docs/sync/VERTO_UNIFIED_SYNC_CONTRACT_v304.json/.md`.
12. `UnifiedSyncContract.kt` و`SyncRealtimeHint` الحالي.
13. `RealtimeManager.kt` / `OrganizationRealtimeSource.kt` / `SupabaseOrganizationRealtimeSource.kt`.
14. `SyncManager.kt` / `SyncWorker.kt` / `SyncReliability.kt` / `SyncWorkScope.kt`.
15. `UnifiedSyncPullEngine.kt`.
16. هذا العقد.
17. الخطة الأم v304→v314.

Realtime API الحالية ليست correctness authority إذا تعارضت مع unified revision/cursor contract.

---

# 4. بصمات authority عند البداية

يجب تثبيت هذه البصمات قبل أي تعديل:

```text
Verto-v311-source-of-truth.zip
  89700b20d832bf9f787d7d0139afb55315a3484be1077c85d2c50dd183c166ca

SESSION_311_FINAL.md
  cc788ce40ba5e8fb307d11d410f4d5d364a032a1829f9d2bae478ae012d6685f

VERTO_SYNC_ORCHESTRATION_VERIFICATION_v311.json
  efe8faa263902eadc9f5041d3230dd0ad3e0df630b4ce6685f16ab36c200a2bb

app/schemas/.../AppDatabase/80.json
  1d077e2539cf8ac4a2cf618c11c0a97f7de44f0c2bf15298e923f39b75988543

data/sync/.../RealtimeManager.kt
  9b134f790cfdaab6ed929df68f71f3b1c7fac45c2e2a44160b74e160d1359238

data/sync/.../SyncManager.kt
  095cd6bbd814f1ae67942d5a4392245a7bbad7e0d5b79a808b43ea16610ac837

data/sync/.../SyncReliability.kt
  6c39618bcb3184086b0f9bf12a1fed693e9483542c748ad62029c69bf0c319ad

data/sync/.../SyncWorkScope.kt
  5cd87c2a131d31b9e08998d5e41769247c98a36a6c21aca8701ea72cabf5d729

data/sync/.../SyncWorker.kt
  b062640e940a3caec7ee8a1a84501ff91ed9d7b28db6b12e65c03fc4cefa2853

data/network/.../OrganizationRealtimeSource.kt
  bcf8acc0f69bf16547f326ad8410f40ce8c5d6d34ecc2c6ee208830a0bf29f8b

data/network/.../SupabaseOrganizationRealtimeSource.kt
  9f107466d77cbbc23ac39d4157c62e87d1a9c4f2f66313d8b8bd913b552fdaf2

data/network/.../UnifiedSyncContract.kt
  9553e1801dcf756f619ea2c28fd1bf8534f18fa32e4f74c2743cedc5460a7e6c

data/network/.../UnifiedSyncAggregateRegistry.kt
  9e35f020993e3caa21171ab6a311605f2bcfa58a910bf7814f22e157b5af7fe9

data/sync/.../pull/UnifiedSyncPullEngine.kt
  c4babd8f48bb16ff160a296238e1459a47dcc1e5592997eb5aeec717e153e6d7

data/sync/.../pull/UnifiedSyncPullRegistry.kt
  04ca782471581ef7948564c93b18ef4a1e919e7c118611b957895547c1bbf250

core/common/.../FeatureFlags.kt
  ad159d5e9f91e406127a5220c4e6729a51f659577ea4e673f9cce98f0de57203

app/.../DefaultAuthSessionCoordinator.kt
  d7f6139fae7ffe33f36ba404326f59e388ef517a8ef08937f9f2330bba32d291

app/.../DeferredStartupCoordinator.kt
  26361d634725597d834a7b27a553b96ac43806ded1e322f94775934fe1e26454

data/database/.../AppDatabase.kt
  9ff0dc54abaa87591bfdf294ce1d8888fd3993b0fbdfcf5d105ae1d3693c5bbb

data/database/.../dao/UnifiedSyncDao.kt
  2a4bf8cdde3b68731cf2d6433358ec804b757588a8c49cb5f4195becb618f68e

gradle/libs.versions.toml
  43af168fd3e0428caee1aefb5e82950da72082dddae77877f494e70c9d19d8a2

supabase/migrations/20260821062000_v305_verto_unified_sync_server.sql
  a2daf28b3a05b35907267bfc766fc6919ba5c28613854c0c0d68ee186313e908

supabase/migrations/20260821123000_v309_verto_unified_sync_push.sql
  c453748be91151702d08e67200556fdd0ab66fbe7d905eff8b9357d8ca8c9bdf

supabase/migrations/20260821150000_v310_verto_stronger_stream_bridge.sql
  43b2db6d6300bc5a59caffdffef071bd894a3356783dffc5de0a9afa2e13f1ce
```

Historical Room schemas/migrations وv305/v309/v310 server migrations لا تعدل في 312.

---

# 5. الحالة الحالية المثبتة من فحص v311

الكود الحالي يثبت:

```text
Room                                      = 80
Supabase Kotlin BOM                       = 3.0.2
Runtime V2                                = OFF
Realtime runtime                          = OFF
OrganizationRealtimeSource output         = Flow<Unit>
Realtime watched tables                   = 8
Realtime event aggregate identity         = absent
Realtime event serverRevision             = absent
Existing SyncRealtimeHint contract        = present but not wired
RealtimeManager burst behavior            = conflate + time cooldown gate
RealtimeManager trigger                   = syncManager.triggerPull()
triggerPull() in v311                      = durable requestSync(REALTIME)
Direct Realtime Room mutation              = none in RealtimeManager
Realtime target revision                  = absent
Realtime aggregate coalescer               = absent
Realtime lifecycle generation              = absent
Source stop ownership                      = global shared remover map
RealtimeManager start()                    = stop() then async source.stop() + new listener
Potential late-stop/new-start race         = present by structure
Single listener proof across rapid starts  = absent
Publication migration in v305/v309/v310    = absent
Realtime correctness fallback             = periodic/manual durable sync
Cursor authority                           = opaque scope-bound Room cursor
Numeric lastAppliedChangeRevision          = diagnostic/integrity anchor only
```

الجداول الثمانية الحالية:

```text
invoices
invoice_items
clients
payments
withdrawal_requests
commission_payments
conversations
internal_messages
```

---

# 6. الفجوات الحرجة التي تملكها 312

## 6.1 Hint information gap

اليوم:

```text
PostgresAction → Unit → durable normal sync generation
```

مفقود:

```text
aggregateType
aggregateId
serverRevision
```

لذلك لا يوجد targeting موثق.

## 6.2 Lifecycle stop/start race

اليوم `RealtimeManager.start(org)` ينفذ `stop()`، و`stop()` يطلق `source.stop()` داخل coroutine منفصلة، بينما المصدر يحتفظ بخريطة remover مشتركة.

interleaving ممكن:

```text
start tenant/session B
old async stop from A executes late
source.stop() clears current shared removers
→ B channels may be removed by stale stop
```

312 يجب أن تجعل channel ownership generation-scoped/handle-scoped، لا global stop مبهم.

## 6.3 Coalescing correctness gap

`conflate + cooldown wall-clock` ليس عقد burst صريحًا، ولا يثبت حفظ أعلى `serverRevision` أو duplicate/out-of-order semantics.

## 6.4 Publication evidence gap

لا توجد migration حالية تثبت allowlist الخاصة بـRealtime في v305/v309/v310. 312 يجب أن تجعل publication surface explicit/static-verifiable أو تسجل غيابها دون تحويل Realtime إلى correctness dependency.

---

# 7. هدف 312 الدقيق

312 تنفذ فقط:

1. تحويل Organization Realtime من `Flow<Unit>` إلى hint typed boundary.
2. استخدام `SyncRealtimeHint` الحالي أو mapping مطابق له.
3. tenant/session validation قبل قبول hint.
4. lifecycle generation/handle تمنع stale stop من قتل listener جديد.
5. single active listener per current tenant/session.
6. burst coalescing bounded.
7. duplicate hint dedupe.
8. out-of-order hint normalization.
9. حفظ أعلى revision hint المقبولة كـaccelerator فقط عند توفرها.
10. aggregate target coalescing bounded.
11. overflow degradation إلى normal unified revision pull.
12. route hint إلى durable generation من 311 قبل الاعتماد على wake.
13. event أثناء active drain لا يضيع.
14. Realtime لا يطبق Room مباشرة.
15. Realtime لا يكتب cursor مباشرة.
16. Realtime لا يستخدم timestamp كترتيب/cursor.
17. `serverRevision` لا يصبح cursor authority.
18. target revision لا يسبب cursor jump أو skip للrevisions.
19. pull يبقى `UnifiedSyncPullEngine` revision-ordered.
20. نقص aggregate/revision info → normal revision pull.
21. stale tenant/session hints = no-op.
22. reconnect لا يدعي replay guarantee.
23. missing/unpublished channel لا يكسر convergence.
24. publication allowlist صريحة وآمنة إن أضيفت server migration.
25. لا wildcard publication.
26. لا TRUNCATE handler.
27. لا sensitive payload عبر hint.
28. Room يبقى 80.
29. historical SQL يبقى byte-identical.
30. V2 وRealtime يبقيان OFF افتراضيًا.
31. 311/310/309/308 regressions تبقى صفر.
32. no new waivers.

---

# 8. ما ليست عليه 312

```text
Bootstrap / full resync                         → 313
Cursor expiry recovery                          → 313
Corruption recovery                             → 313
Reconciliation dashboard                        → 313
Default-on V2                                   → 314
Default-on Realtime                             → 314 أو rollout gate مستقل
Legacy fullSync removal                         → 314
Legacy participant removal                      → 314
Business-rule redesign                          → ممنوع
Financial/Inventory/Optimal semantic rewrite    → ممنوع
Message-domain realtime redesign                → خارج النطاق ما لم يثبت أنه جزء من Organization sync correctness
Multi-tenant Room redesign                      → خارج النطاق
FCM redesign                                    → خارج النطاق
```

---

# 9. Runtime policy

في نهاية 312:

```text
FeatureFlags.isVersionedSyncEnabled = false
FeatureFlags.isRealtimeSyncEnabled  = false
legacy runtime                      = preserved as default
V2 Realtime hint path               = implemented + directly testable
publication migration               = static-defined if required
PostgreSQL application              = NOT CLAIMED unless actually executed
bootstrap/full resync               = not implemented
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
After  = 80
```

ممنوع:

- تعديل schema80 JSON.
- إضافة 80→81 من أجل Realtime hints.
- إنشاء durable hint table محلية بلا ضرورة.
- نقل cursor authority إلى DataStore/Realtime state.

312 تعتمد على durable generation من 311، ويمكنها استخدام `sync_sequence_state` فقط لمؤشر monotonic accelerator غير cursor إذا ثبتت الحاجة، دون schema change.

---

# 11. Hint contract authority

العقد الموجود مسبقًا:

```text
SyncRealtimeHint(
  organizationId,
  aggregateType?,
  aggregateId?,
  serverRevision?
)
```

هو الشكل الحاكم.

الأصل أن يبقى `UnifiedSyncContract.kt` byte-identical.

إذا احتاج wire DTO خاص بـSupabase، يوضع في network adapter ويُmap إلى `SyncRealtimeHint`.

ممنوع تعريف عقد منافس باسم مختلف يملك semantics مختلفة.

---

# 12. Hint-only invariant

Realtime payload لا يكتب أي domain row.

ممنوع:

```text
onRealtimeEvent { dao.insert/update/delete(...) }
onRealtimeEvent { applySyncChange(...) }
onRealtimeEvent { cursor = serverRevision }
```

المسموح:

```text
onRealtimeEvent { emit SyncRealtimeHint(...) }
```

ثم orchestration/pull هو وحده الذي يطبق البيانات.

---

# 13. Realtime is not delivery authority

312 لا تفترض:

```text
Realtime events are complete
Realtime events are ordered
Realtime events are exactly once
Reconnect replays missed events
Publication is always available
```

أي gap/reconnect/missed event يُعالج لاحقًا بواسطة normal revision pull/periodic/manual wake.

---

# 14. Tenant validation

كل hint مقبولة يجب أن تحقق:

```text
hint.organizationId == active trusted scope.organizationId
active user == scope.userId
active sessionEpoch == listener sessionEpoch
listener lifecycle generation == current generation
```

mismatch:

```text
STALE_REALTIME_HINT → drop/no-op
```

لا network pull ولا Room apply.

---

# 15. Session epoch reuse

312 تستخدم session epoch من 311.

لا تنشئ auth generation منافسة.

Realtime listener يجب أن يرتبط بـ:

```text
organizationId
userId
sessionEpoch
listenerGeneration/handle
```

حيث listenerGeneration خاص بعمر القناة فقط، وsessionEpoch هو stale-session authority.

---

# 16. Listener lifecycle handle

واجهة المصدر المستهدفة يجب أن تملك ownership صريحًا، مثال:

```text
interface RealtimeSubscription {
    val id: String
    suspend fun close()
}

suspend/flow openOrganization(scope): RealtimeSubscription + Flow<SyncRealtimeHint>
```

أو equivalent structured ownership.

المهم:

```text
close(oldHandle) cannot close channels owned by newHandle
```

---

# 17. Global stop anti-pattern

312 تمنع الاعتماد correctness-wise على:

```text
source.stop() → clear every remover in shared map
```

إذا أبقيت `stop()` compatibility API، يجب أن تنادي فقط handle الحالي المعروف للmanager وتكون generation-safe.

---

# 18. Rapid stop/start race

الاختبار المركزي:

```text
start A
stop A begins but is delayed
start B completes
late stop A resumes
```

PASS فقط إذا:

```text
B remains active
A cannot remove B channels
active listener count == 1
```

---

# 19. Rapid start/start race

```text
start A
start B before A subscribe completes
```

PASS:

```text
A becomes stale and cannot emit accepted hints
B owns current lifecycle generation
no duplicate active listeners
```

---

# 20. Single listener invariant

لكل current tenant/session:

```text
activeOrganizationListenerCount <= 1
```

لا duplicate channel trees لنفس lifecycle generation.

---

# 21. Channel naming

channel identity يجب أن تتضمن ما يكفي لمنع collision التشغيلي، مثل:

```text
organization + listener generation + source/table
```

لكن لا تضع access token أو PII غير اللازمة في الاسم.

اسم channel ليس auth authority.

---

# 22. Burst coalescer

312 تضيف coalescer واضحًا بدل الاعتماد على `conflate()` وحده.

الـstate المنطقي:

```text
pending = true/false
maxServerRevision?
targets = bounded set<(aggregateType, aggregateId)>
overflowed = true/false
```

كل burst يُدمج دون unbounded memory.

---

# 23. Burst window

يجوز debounce/trailing-edge أو leading+trailing design.

لكن يجب أن يكون deterministic في model tests، وأن لا يعتمد correctness على wall-clock دقيق.

الـclock يستخدم للتجميع فقط، لا لترتيب البيانات.

---

# 24. Burst 100 events

اختبار إلزامي:

```text
100 valid same-tenant hints in one burst window
```

PASS إذا:

```text
no 100 WorkManager wakes
no 100 generation storms
one bounded coalesced pending batch
max revision retained
all correctness preserved by normal revision pull
```

الحد المطلوب:

```text
wakeCount <= 2
durableRealtimeGenerationIncrements <= 2
```

لـ100-event single-window fixture.

---

# 25. Duplicate hints

نفس:

```text
org + aggregateType + aggregateId + serverRevision
```

يتعامل معه idempotently في coalescer.

لا يخلق duplicate semantic pull obligation.

---

# 26. Out-of-order hints

مثال:

```text
rev 105
rev 103
rev 110
rev 110
```

الناتج:

```text
maxServerRevision = 110
```

لكن هذا الرقم accelerator only، وليس cursor assignment.

---

# 27. Missing revision

إذا hint لا تحمل `serverRevision`:

```text
aggregate info may be retained for observability/targeting
normal durable revision pull is requested
```

لا timestamp fallback.

---

# 28. Missing aggregate info

إذا لا `aggregateType/aggregateId`:

```text
normal revision pull
```

إذا واحد منهما مفقود والآخر موجود:

```text
normalize as untargeted hint unless existing frozen contract explicitly supports partial target
```

لا تخمين للمعرف.

---

# 29. Unknown aggregate type

إذا Realtime wire يذكر aggregate غير معروف في `UnifiedSyncAggregateRegistry`:

```text
UNKNOWN_REALTIME_TARGET
→ degrade to normal revision pull or drop accelerator metadata
```

لا direct mapper عشوائي.

---

# 30. Target set bound

الـaggregate target set يجب أن يكون bounded.

إذا تجاوز budget:

```text
overflowed = true
target set may collapse to organization-wide normal pull
```

ممنوع unbounded `MutableSet` عبر burst طويل.

---

# 31. Targeted pull definition

في 312، **targeted pull لا يعني filtered cursor jump**.

التعريف الحاكم:

> Realtime hint يسرّع تشغيل unified revision pull ويعطيه high-watermark/aggregate expectation تشخيصية أو bounded catch-up goal، بينما يبقى cursor العام متقدمًا فقط عبر الصفحات المطبقة ذرّيًا بواسطة `UnifiedSyncPullEngine`.

---

# 32. No cursor jump

ممنوع:

```text
hint.serverRevision = 500
cursor.lastAppliedRevision = 420
→ write cursor 500
```

الصحيح:

```text
pull from opaque cursor
apply pages in server order
cursor advances only via normal PullEngine transaction
```

---

# 33. Opaque cursor remains authority

`SyncCursorEntity.cursorToken` يبقى cursor authority.

`lastAppliedChangeRevision` يبقى diagnostic/integrity anchor.

312 لا تعيد بناء cursor token من `serverRevision`.

العداد:

```text
realtimeCursorReconstructionCount = 0
```

---

# 34. Revision hint is advisory

حتى عند وجود `serverRevision`:

- لا يعتبر delivery guarantee.
- لا يعتبر visibility guarantee.
- لا يصبح شرطًا يسبب infinite pull إذا لم يكن revision مرئيًا للscope.
- CAUGHT_UP من pull engine يمكن أن ينهي accelerator run وفق عقد 308/311.

أي chase loop لمجرد `targetRevision > localDiagnosticRevision`:

```text
FAIL_REALTIME_TARGET_BUSY_LOOP
```

---

# 35. Visibility safety

Realtime hint لا يجوز أن تكشف aggregate id أو metadata لمستخدم لا يملك visibility المقابلة.

أي server publication surface جديدة يجب أن تحترم على الأقل:

```text
organization membership
active user status
visibility_principal_id when applicable
required_permission when applicable
```

أو تستخدم hint أقل تفصيلًا لا يكشف الهدف.

---

# 36. Sensitive payload prohibition

Realtime hint surface لا تنشر:

```text
business payload JSON
financial amounts unless strictly required and separately approved
access/refresh tokens
mutation payload
receipt payload
authoritative snapshot payload
```

الحد الأقصى المتوقع:

```text
organizationId
aggregateType?
aggregateId?
serverRevision?
minimal diagnostic event type if needed
```

---

# 37. Publication strategy

312 يجب أن تنتج **واحدًا** من الآتي بشكل صريح:

```text
A. safe explicit publication migration for a minimal hint surface
or
B. documented no-new-publication path proving existing published sources are sufficient and safe
```

لا يجوز بقاء publication state مجهولة مع ادعاء `PUBLICATION_PASS`.

---

# 38. Preferred publication surface

إذا احتاجت migration جديدة، يفضل surface منفصلة/minimal projection أو equivalent آمن، بدل نشر كامل `verto_sync_change_log` payload.

إذا استُخدم `verto_sync_change_log` مباشرة، يجب إثبات column/row exposure minimal وvisibility-safe.

بدون هذا الإثبات:

```text
FAIL_REALTIME_SENSITIVE_PUBLICATION
```

---

# 39. Publication allowlist

ممنوع:

```text
FOR ALL TABLES
wildcard publication
schema-wide publication without explicit allowlist
```

كل table/surface منشورة يجب أن تظهر في artifact مع سبب وtenant/visibility rule.

---

# 40. No TRUNCATE handler

312 لا تضيف `TRUNCATE` كـsync event.

لا يوجد:

```text
TRUNCATE → wipe Room
TRUNCATE → full resync
```

إذا حصل server truncate خارج العقد، correctness recovery في 313/operational incident، لا Realtime direct apply.

---

# 41. INSERT/UPDATE/DELETE mapping

إذا المصدر يستمع business tables مباشرة:

- event يتحول فقط إلى hint.
- DELETE قد يفقد بعض row fields حسب replica identity؛ عند نقص identity → normal pull.
- لا direct local delete.

إذا المصدر يستمع append-only hint surface:

- يفضل `INSERT` فقط.

---

# 42. Publication absence

إذا table/surface غير منشورة أو channel subscribe فشل:

```text
log diagnostic
close/retry listener according to bounded lifecycle policy
periodic/manual durable sync remains correctness fallback
```

لا `BLOCKED_SYNC_CORRECTNESS` لمجرد غياب Realtime.

---

# 43. Publication reconnect

Reconnect لا يفترض replay.

بعد reconnect الناجح:

```text
request one durable normal revision pull
```

أو equivalent catch-up request.

هذا يعوض window المفقودة دون تحويل Realtime إلى event log authority.

---

# 44. Reconnect storm

عدة reconnect callbacks متقاربة يجب أن تمر عبر نفس coalescer.

لا wake storm.

---

# 45. Active drain hint

سيناريو:

```text
worker draining generation N
Realtime hint arrives
```

PASS:

```text
hint coalesces
311 durable generation becomes pending if needed
active drain continues or successor handles it
no hint lost solely because syncInProgress=true
```

---

# 46. Final-exit race preservation

312 لا تغير proof 311:

```text
hint accepted near worker final-exit
→ generation commit/wake remains successor-safe
```

`APPEND_OR_REPLACE`/idle CAS semantics تبقى.

---

# 47. Realtime request reason

Realtime acceleration تستخدم:

```text
SyncRequestReason.REALTIME
```

أو reason مكافئ.

لا تنشئ reason جديدة لكل table بما يغير correctness ordering.

---

# 48. Durable pending authority

pending correctness authority هو generation من 311.

الـcoalescer memory state مجرد accelerator metadata.

إذا process مات وضاعت target metadata:

```text
durable generation remains
normal unified revision pull converges
```

هذا PASS، وليس lost intent.

---

# 49. Optional durable max hint revision

يجوز استخدام `sync_sequence_state` بمفتاح جديد مثل:

```text
ORCHESTRATION_REALTIME_TARGET_REVISION
```

فقط إذا:

- monotonic max، لا increment clock-based.
- لا يصبح cursor authority.
- لا يسبب chase loop.
- لا يحتاج schema change.
- verifier يثبت أنه accelerator-only.

ليس شرطًا إذا generation fallback كافية.

---

# 50. Process death after hint

سيناريو:

```text
hint accepted
generation committed
process dies before pull
```

PASS:

```text
next wake drains generation
normal revision pull converges even if in-memory target lost
```

---

# 51. Process death before generation commit

Realtime itself is best-effort.

إذا process مات قبل durable request commit، الحدث قد يضيع، وهذا مقبول **فقط** لأن periodic/manual revision pull يحقق convergence لاحقًا.

التقرير لا يدعي durable Realtime delivery.

---

# 52. FullSync prohibition on V2 Realtime path

312 تمنع V2 hint path من استدعاء legacy:

```text
fullSync()
legacy participant-wide pull loop
timestamp pull
```

يجب أن ينتهي إلى durable V2 drain/unified pull path.

العداد:

```text
realtimeV2LegacyFullSyncTriggerCount = 0
```

---

# 53. Legacy compatibility

بما أن V2/Realtime flags OFF، legacy runtime يبقى قابلًا للعمل كما هو.

312 لا تحذف legacy `fullSync` ولا routes القديمة.

لكن path الجديد خلف flags يجب ألا يعتمد عليها.

---

# 54. Unified pull authority

كل remote apply الناتج عن Realtime acceleration يمر عبر:

```text
UnifiedSyncPullEngine
→ inbox/cursor atomic transaction
→ stronger/generic applier rules
```

لا Realtime-specific DAO apply path.

---

# 55. Pull page outcomes

312 تحترم:

```text
CAUGHT_UP
MORE_AVAILABLE
BOOTSTRAP_REQUIRED
RECOVERY_REQUIRED
```

- `MORE_AVAILABLE` → bounded continuation.
- `BOOTSTRAP_REQUIRED` / `RECOVERY_REQUIRED` → 313.
- لا full-table fallback.

---

# 56. Aggregate target semantics

aggregateType/id يمكن أن تستخدم لـ:

- dedupe.
- diagnostics.
- prioritization hint إذا PullEngine يدعمها دون cursor violation.

لا يجوز أن تستخدم لـ:

- skip previous revisions.
- apply raw business table row.
- overwrite stronger owner semantics.

---

# 57. Stronger aggregates protection

Financial/Inventory/Optimal owner310 semantics تبقى كما هي.

Realtime hint لا ينشئ generic LWW path لها.

أي owner310 remote change يصل عبر unified pull/stronger applier فقط.

---

# 58. Remote-apply echo protection

312 لا تغير 308/309 rule:

```text
REMOTE_APPLY does not enqueue new local mutation
```

العداد:

```text
realtimeRemoteApplyEnqueueCount = 0
```

---

# 59. Cursor timestamp prohibition

لا `System.currentTimeMillis()` أو `changed_at` أو `updated_at` كـcursor/order.

الوقت مسموح فقط لـ:

- debounce.
- diagnostics.
- retry scheduling غير cursor.

---

# 60. Cooldown redesign

`RealtimeTriggerGate` الحالي المبني على آخر sync time لا يكفي وحده.

312 يجوز استبداله/coexist معه بـcoalescer يضمن:

- hint during active worker محفوظ كgeneration.
- max revision لا تضيع بسبب cooldown.
- cooldown لا يمنع catch-up correctness.

---

# 61. lastSyncCompletedAtMillis boundary

يبقى display/observability metadata فقط.

ممنوع:

```text
if event within 2s of lastSync → drop permanently
```

إذا drop acceleration detail، يجب أن يكون durable generation/normal pull قد غطى الحدث أو يوجد fallback واضح.

---

# 62. Withdrawal flow compatibility

`withdrawalRequestsChanged` الحالي side-flow يجب جرده.

إذا بقي:

- لا يطبق Room مباشرة.
- لا يحمل correctness authority.
- يمكن اشتقاقه من hint أو source event.
- lifecycle safety نفسها تنطبق عليه.

لا يُكسر consumers بلا coverage artifact.

---

# 63. Messages Realtime boundary

`MessagesRealtimeSource`/presentation-specific realtime لا يُعاد تصميمه تلقائيًا في 312 إذا لم يكن جزءًا من Organization sync data plane.

يجب فقط تصنيفه في coverage artifact:

```text
IN_SCOPE_SYNC_HINT
or
OUT_OF_SCOPE_PRESENTATION_STREAM
```

لا refactor incidental.

---

# 64. Startup lifecycle

`DeferredStartupCoordinator` لا يبدأ Realtime إلا بعد trusted current scope/session availability.

إذا flag OFF:

```text
no listener started
```

إذا path direct-test enabled:

```text
scope includes current session epoch
```

---

# 65. Logout lifecycle

قبل/أثناء logout:

```text
invalidate session epoch
close current listener handle
cancel/ignore stale emissions
```

late event من old handle:

```text
STALE_REALTIME_HINT → no-op
```

---

# 66. Org switch lifecycle

الترتيب من 311 يبقى:

```text
invalidate old epoch
cancel old work
stop old realtime
clear/isolate local session data
commit new org
activate new epoch
schedule/start new scope
```

312 تجعل `stop old realtime` generation-safe ولا تسمح late stop بقتل new listener.

---

# 67. Same-org reauth

نفس organization/user مع epoch جديدة:

- old listener stale.
- new listener owns new epoch.
- old event لا يقبل.

organization equality وحدها ليست كافية.

---

# 68. User switch same org

user A listener لا يصبح listener user B حتى لو org نفسها.

لا cross-user hint acceptance.

---

# 69. Stale source callback

أي callback بعد `close()` أو بعد generation replacement:

```text
acceptedHintCount += 0
networkPullCount += 0
```

---

# 70. Source failure isolation

فشل channel لtable اختياري لا يسقط بقية channels إذا design multi-channel.

إذا design single hint surface، فشلها لا يسقط correctness fallback.

---

# 71. Source cancellation

`CancellationException` يعاد رميها/تحترم structured cancellation.

لا تسجل كnetwork failure دائم ولا trigger full sync تلقائي لا نهائي.

---

# 72. Error backoff

Realtime reconnect failure لا يستخدم tight loop.

يجب bounded retry/backoff أو library-owned reconnect موثق.

لا retry storm.

---

# 73. No network recursion

Realtime reconnect → durable pull مسموح.

لكن pull completion لا يجب أن يعيد subscribe recursively أو يخلق loop:

```text
pull → local write → realtime own echo → pull → ...
```

coalescer/cursor should converge boundedly.

---

# 74. Echo event behavior

server echo بعد push قد يولد Realtime hint.

PASS إذا:

- hint يسرع unified pull فقط.
- inbox/originMutationId reconciliation يضمن no duplicate semantic effect.
- duplicate hint لا يخلق storm.

---

# 75. Target visibility mismatch

إذا serverRevision hint لا تكون مرئية للscope أو missing بسبب permission:

- لا infinite chase.
- normal pull `CAUGHT_UP`/scope semantics تبقى الحاكم.
- hint target لا يمنع idle إلى الأبد.

---

# 76. Permission change while connected

إذا permissions/session visibility تغيرت:

- session/scope invalidation أو next validated pull يقرر authority.
- Realtime event لا يتجاوز `verto_validate_sync_scope` semantics.
- 313/314 يمكن أن تضيف broader recovery إذا لزم.

---

# 77. Server migration policy

312 يجوز أن تضيف **migration واحدة جديدة فقط** إذا publication/hint surface تتطلب ذلك.

ممنوع تعديل:

```text
v305 migration
v309 migration
v310 migration
```

الجديدة يجب أن تكون additive وRealtime-only/minimal.

---

# 78. SQL application policy

Static PASS لا يتطلب تنفيذ PostgreSQL في هذه الجلسة.

إذا أنشئت migration:

```text
postgresRequiredForStaticPass = false
postgresExecuted              = false unless actually run
serverMigrationDefined312     = true
serverMigrationApplied312     = false unless actually verified
```

أي ادعاء application بدون execution:

```text
FAIL_SQL_RUNTIME_CLAIM
```

---

# 79. SQL idempotent publication setup

إذا migration تدير `supabase_realtime` publication، يجب أن تتحقق من:

- publication existence.
- table/surface existence.
- عدم إضافة نفس table مرتين.
- explicit allowlist.
- least privilege grants.
- RLS/tenant visibility.

لا destructive publication reset.

---

# 80. Historical migration integrity

قبول 312 يتطلب:

```text
v305 SHA unchanged
v309 SHA unchanged
v310 SHA unchanged
```

أي اختلاف:

```text
FAIL_HISTORICAL_MIGRATION_DRIFT
```

---

# 81. Publication coverage artifact

إنشاء:

```text
docs/sync/VERTO_SYNC_REALTIME_PUBLICATION_v312.csv
```

أعمدة إلزامية:

```text
surface_id
schema_name
table_or_channel
source_kind
operations_observed
organization_filter
visibility_rule
published_columns
contains_business_payload
aggregate_mapping
revision_source
allowlisted
rls_or_auth_guard
fallback_if_unavailable
runtime_state
evidence
```

لا UNKNOWN/TODO/LATER في صفوف PASS.

---

# 82. Realtime source coverage artifact

إنشاء:

```text
docs/sync/VERTO_SYNC_REALTIME_SOURCES_v312.csv
```

أعمدة:

```text
source_id
call_site
current_behavior_v311
v312_hint_output
aggregate_identity
server_revision
session_epoch_checked
lifecycle_generation_checked
burst_coalesced
durable_generation_requested
direct_room_mutation
cursor_write
runtime_state
evidence
```

يغطي OrganizationRealtimeSource وكل side-flow المرتبط بها.

---

# 83. Lifecycle artifact

إنشاء:

```text
docs/sync/VERTO_SYNC_REALTIME_LIFECYCLE_v312.md
```

يوثق state machine:

```text
STOPPED
STARTING(generation)
ACTIVE(generation, scope)
CLOSING(generation)
REPLACED
STALE
FAILED_OPTIONAL
```

مع legal transitions وlate callback behavior.

---

# 84. Hint/coalescing artifact

إنشاء:

```text
docs/sync/VERTO_SYNC_REALTIME_HINT_POLICY_v312.md
```

يوثق:

- normalization.
- dedupe key.
- burst window.
- max target bound.
- overflow fallback.
- target revision semantics.
- why hint is not cursor.
- process-death degradation.

---

# 85. Static verifier artifacts

إضافة:

```text
tools/verify_sync_realtime_v312.py
tools/test_sync_realtime_verification_v312.py
scripts/verify-v312-sync-realtime.sh
```

أو أسماء مكافئة موثقة.

---

# 86. Verifier determinism

`verify-v312-sync-realtime.sh` يشغل verifier مرتين.

normalized output hash يجب أن يتطابق.

عدم determinism:

```text
FAIL_V312_VERIFIER_NONDETERMINISTIC
```

---

# 87. v312 model fixtures — الحد الأدنى

إنشاء >= `300` fixture/model assertions خاصة بـ312.

تغطي على الأقل:

```text
100-event burst
100 duplicate hints
out-of-order revisions
missing revision
missing aggregate target
unknown aggregate
aggregate target overflow
event during active drain
final-exit hint race
process death after generation
process death losing only accelerator metadata
rapid start/stop
rapid start/start
late old stop after new start
late old callback
logout event race
org switch event race
same-org reauth
user switch same org
reconnect catch-up
reconnect storm
optional unpublished table
publication absent
channel failure isolation
wrong-tenant hint
wrong-user hint
old-session-epoch hint
visibility-restricted hint
serverRevision advisory no cursor jump
invisible/non-deliverable target no busy loop
remote echo duplicate hint
no direct Room mutation
no cursor write
no timestamp cursor
Runtime V2 OFF
Realtime default OFF
Room 80 unchanged
historical SQL unchanged
```

---

# 88. Required named model passes

الـJSON النهائي يجب أن يحتوي true على الأقل:

```text
MODEL_REALTIME_HINT_ONLY_PASS
MODEL_BURST_100_COALESCED_PASS
MODEL_DUPLICATE_HINT_DEDUPE_PASS
MODEL_OUT_OF_ORDER_HINT_MAX_PASS
MODEL_MISSING_HINT_INFO_FALLBACK_PASS
MODEL_TARGET_OVERFLOW_FALLBACK_PASS
MODEL_ACTIVE_DRAIN_HINT_PASS
MODEL_FINAL_EXIT_HINT_RACE_PASS
MODEL_PROCESS_DEATH_HINT_FALLBACK_PASS
MODEL_SINGLE_LISTENER_PASS
MODEL_RAPID_STOP_START_PASS
MODEL_LATE_STOP_CANNOT_KILL_NEW_PASS
MODEL_LATE_CALLBACK_STALE_PASS
MODEL_STALE_TENANT_HINT_PASS
MODEL_SESSION_EPOCH_REALTIME_PASS
MODEL_LOGOUT_REALTIME_RACE_PASS
MODEL_ORG_SWITCH_REALTIME_RACE_PASS
MODEL_SAME_ORG_REAUTH_REALTIME_PASS
MODEL_RECONNECT_CATCHUP_PASS
MODEL_UNPUBLISHED_OPTIONAL_PASS
MODEL_PUBLICATION_NOT_CORRECTNESS_PASS
MODEL_NO_REALTIME_CURSOR_JUMP_PASS
MODEL_TARGET_REVISION_ADVISORY_PASS
MODEL_NO_TARGET_BUSY_LOOP_PASS
MODEL_NO_REALTIME_ROOM_MUTATION_PASS
MODEL_NO_REALTIME_LEGACY_FULLSYNC_PASS
MODEL_REALTIME_DEFAULT_OFF_PASS
```

---

# 89. 100-event burst test

Scenario:

```text
100 hints same tenant within one coalescing window
revisions 1..100
```

PASS:

```text
max revision metadata = 100
wakeCount <= 2
durableRealtimeGenerationIncrements <= 2
no 100-worker chain
normal pull can catch up completely
```

---

# 90. Duplicate hint test

```text
repeat same hint 100 times
```

PASS:

```text
bounded pending state
no 100 durable requests
no semantic duplicate apply
```

---

# 91. Active sync test

```text
worker active
hint arrives
```

PASS:

```text
pending durable intent remains
no suppression solely due to syncInProgress
no direct pull outside orchestrator mutex/worker discipline
```

---

# 92. Rapid lifecycle test

Controlled interleaving:

```text
A start
A stop suspended
B start + subscribe
A stop resumes
```

PASS:

```text
B active
A closed
activeListenerCount = 1
lateStopKillsNewCount = 0
```

---

# 93. Reconnect test

Scenario:

```text
listener disconnects
server changes while disconnected
listener reconnects
```

PASS:

```text
no assumption event replayed
one bounded catch-up durable request
unified revision pull recovers visible changes
```

---

# 94. Unpublished table test

إذا one optional source is unpublished:

PASS:

```text
listener/source degrades diagnostically
periodic/manual path remains usable
no correctness blocker solely from Realtime absence
```

---

# 95. Stale tenant event test

Inputs:

```text
hint org != current org
old session epoch
old listener generation
```

كلها:

```text
no generation request
no pull
no Room apply
```

---

# 96. No cursor jump test

```text
local cursor diagnostic revision = 20
hint revision = 100
```

PASS فقط إذا:

```text
cursor token unchanged until PullEngine applies pages
no direct write to lastAppliedChangeRevision from Realtime code
```

---

# 97. Advisory target no-spin test

Scenario:

```text
hint target revision cannot/does not appear in current visible scope
pull reports caught-up for scope
```

PASS:

```text
worker can reach idle
no repeated immediate pull solely chasing hint number
```

---

# 98. Direct mutation scan

Verifier يفحص Realtime files الجديدة/المعدلة.

ممنوع references مباشرة إلى:

```text
Room DAO domain writes
AppDatabase domain tables
applyChange payload mapper
cursor update DAO
```

إلا orchestration/read-only evidence المصرح بها.

---

# 99. v311 regression gate

يجب إعادة:

```text
325/325 v311 fixtures
```

بدون failure.

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

# 100. v310 regression gate

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

# 101. v309 regression gate

يجب إعادة:

```text
272/272 v309 fixtures
```

Idempotency/receipt/conflict semantics لا تتغير.

---

# 102. v308 regression gate

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

# 103. Build/Test runtime truth

إذا Gradle/SDK غير متاحة أو التنفيذ static-only:

```text
compileStatus  = NOT_RUN_ENVIRONMENT_UNAVAILABLE
```

أو:

```text
compileStatus  = NOT_RUN_USER_AUTHORIZED_STATIC_ONLY
```

ونفس الشيء للاختبارات.

إذا شُغل command فعليًا وفشل:

```text
BLOCKED_RUNTIME_FAILURE
```

لا يحول إلى PASS.

---

# 104. PostgreSQL runtime truth

إذا migration 312 أنشئت لكن لم تنفذ:

```text
postgresRequiredForStaticPass = false
postgresExecuted              = false
publicationRuntimeVerified    = false
```

Static PASS مسموح مع توثيق صريح.

إذا SQL نفذت فعليًا وفشلت:

```text
BLOCKED_POSTGRES_RUNTIME_FAILURE
```

---

# 105. New waivers

الحاكم:

```text
new312WaiverCount = 0
```

أي waiver جديدة تمنع PASS.

---

# 106. Required failure codes

على الأقل:

```text
BLOCKED_INPUT_DRIFT
BLOCKED_SCOPE_DRIFT
BLOCKED_RUNTIME_FAILURE
BLOCKED_POSTGRES_RUNTIME_FAILURE
FAIL_REALTIME_DIRECT_ROOM_MUTATION
FAIL_REALTIME_CURSOR_WRITE
FAIL_REALTIME_CURSOR_RECONSTRUCTION
FAIL_REALTIME_TIMESTAMP_CURSOR
FAIL_REALTIME_V2_LEGACY_FULLSYNC
FAIL_REALTIME_HINT_TENANT_MISMATCH
FAIL_REALTIME_HINT_SESSION_MISMATCH
FAIL_STALE_REALTIME_CALLBACK
FAIL_DUPLICATE_ACTIVE_LISTENER
FAIL_LATE_STOP_KILLS_NEW_LISTENER
FAIL_REALTIME_BURST_STORM
FAIL_REALTIME_GENERATION_STORM
FAIL_REALTIME_TARGET_UNBOUNDED
FAIL_REALTIME_TARGET_BUSY_LOOP
FAIL_REALTIME_REVISION_CURSOR_JUMP
FAIL_REALTIME_PUBLICATION_WILDCARD
FAIL_REALTIME_SENSITIVE_PUBLICATION
FAIL_REALTIME_VISIBILITY_LEAK
FAIL_REALTIME_TRUNCATE_HANDLER
FAIL_REALTIME_CORRECTNESS_DEPENDENCY
FAIL_RECONNECT_REPLAY_ASSUMPTION
FAIL_HISTORICAL_MIGRATION_DRIFT
FAIL_ROOM_SCHEMA_DRIFT
FAIL_311_REGRESSION
FAIL_310_REGRESSION
FAIL_309_REGRESSION
FAIL_308_REGRESSION
FAIL_RUNTIME_CUTOVER_EARLY
FAIL_SQL_RUNTIME_CLAIM
FAIL_V312_VERIFIER_NONDETERMINISTIC
```

---

# 107. Acceptance counters — must equal zero

```text
realtimeDirectRoomMutationCount
realtimeCursorWriteCount
realtimeCursorReconstructionCount
realtimeTimestampCursorAuthorityCount
realtimeV2LegacyFullSyncTriggerCount
staleRealtimeTenantAcceptedCount
staleRealtimeUserAcceptedCount
staleRealtimeEpochAcceptedCount
staleRealtimeGenerationAcceptedCount
duplicateActiveListenerCount
lateStopKillsNewListenerCount
lateCallbackAcceptedCount
realtimeBurstStormCount
realtimeGenerationStormCount
unboundedRealtimeTargetCount
realtimeTargetBusyLoopCount
realtimeRevisionCursorJumpCount
publicationWildcardCount
sensitiveRealtimePublicationCount
realtimeVisibilityLeakCount
truncateHandlerCount
realtimeCorrectnessDependencyCount
reconnectReplayAssumptionCount
realtimeRemoteApplyEnqueueCount
historicalMigrationChangedCount
roomSchemaChangedCount
new312WaiverCount
v311RegressionFailures
v310RegressionFailures
v309RegressionFailures
v308RegressionFailures
runtimeV2EnabledCount
realtimeDefaultEnabledCount
```

---

# 108. Acceptance counters — exact/positive values

```text
inputArchiveEntries            = 2650
inputProductionKotlinCount     = 1178
inheritedExceptionCount        = 9
roomVersionBefore/After        = 80/80
supabaseVersion                = 3.0.2
workManagerVersion             = 2.10.0
v312StaticFixtureCount         >= 300
v311RegressionFixtureCount     = 325
v310RegressionFixtureCount     = 499
v309RegressionFixtureCount     = 272
v308RegressionFixtureCount     = 137
realtimeSourceCoverageRows     > 0
publicationCoverageRows        > 0
```

ولـ100-event burst fixture:

```text
burstInputHints                 = 100
burstWakeCount                  <= 2
burstGenerationIncrementCount   <= 2
```

---

# 109. v312 verification JSON

إنشاء:

```text
VERTO_SYNC_REALTIME_VERIFICATION_v312.json
```

يحتوي على الأقل:

```text
session
inputZipName
inputZipSha256
inputArchiveEntries
inputProductionKotlinCount
planSha256
session311ContractSha256
v311VerificationSha256
v311FinalVerdict
v311Handoff312Authorized
inheritedExceptionCount
new312WaiverCount
roomVersionBefore
roomVersionAfter
schema80Sha256
supabaseVersion
workManagerVersion
realtimeSourceTypeBefore
realtimeSourceTypeAfter
realtimeWatchedSurfaceCountBefore
realtimeSourceCoverageRows
publicationCoverageRows
realtimeDirectRoomMutationCount
realtimeCursorWriteCount
realtimeCursorReconstructionCount
realtimeTimestampCursorAuthorityCount
realtimeV2LegacyFullSyncTriggerCount
staleRealtimeTenantAcceptedCount
staleRealtimeUserAcceptedCount
staleRealtimeEpochAcceptedCount
staleRealtimeGenerationAcceptedCount
duplicateActiveListenerCount
lateStopKillsNewListenerCount
lateCallbackAcceptedCount
realtimeBurstStormCount
realtimeGenerationStormCount
unboundedRealtimeTargetCount
realtimeTargetBusyLoopCount
realtimeRevisionCursorJumpCount
publicationWildcardCount
sensitiveRealtimePublicationCount
realtimeVisibilityLeakCount
truncateHandlerCount
realtimeCorrectnessDependencyCount
reconnectReplayAssumptionCount
historicalMigrationChangedCount
serverMigrationDefined312
serverMigrationApplied312
publicationRuntimeVerified
v311RegressionFailures
v310RegressionFailures
v309RegressionFailures
v308RegressionFailures
v312FixtureStats
runtimeV2
realtimeRuntime
compileStatus
unitTestStatus
postgresRequiredForStaticPass
postgresExecuted
finalVerdict
blockers
handoff313Authorized
```

---

# 110. v312 verification Markdown

إنشاء:

```text
VERTO_SYNC_REALTIME_VERIFICATION_v312.md
```

Human-readable ومطابق للـJSON.

---

# 111. Expected PASS verdict

إذا نجحت كل static gates:

```text
PASS_STATIC_REALTIME_HINT_TARGETED_LIFECYCLE
/ INHERITED_307_EXCEPTIONS=9
/ ROOM_80_UNCHANGED
/ V2_DISABLED
/ REALTIME_DEFAULT_DISABLED
/ BUILD_NOT_VERIFIED
/ POSTGRES_NOT_EXECUTED
```

إذا لا migration server جديدة مطلوبة يمكن إضافة:

```text
/ SERVER_SQL_UNCHANGED
```

إذا migration جديدة عُرفت static فقط:

```text
/ V312_REALTIME_SQL_STATIC_ONLY
```

ولا تستخدم `POSTGRES_VERIFIED` إلا بعد تنفيذ فعلي ناجح.

---

# 112. معنى PASS في 312

PASS يعني حصرًا:

> أصبح Realtime يرسل hints tenant/session-safe ومحدودة ومجمعة، لا يطبق بيانات ولا يكتب cursor، ويوقظ durable drain من 311 لتسريع unified revision pull، مع lifecycle ownership يمنع stale stop/callback وpublication surface آمنة واختيارية، بينما بقي V2 وRealtime افتراضيًا معطلين.

ولا يعني:

```text
Realtime delivery guaranteed
Realtime exactly-once
all publications runtime verified
bootstrap/full resync complete
V2 default-on
Realtime default-on
legacy sync removed
multi-device runtime tested
APK compiled unless actually verified
PostgreSQL migration applied unless actually executed
```

---

# 113. Report honesty

ممنوع كتابة:

```text
"Realtime guarantees delivery"
"serverRevision hint is the cursor"
"targeted hint lets us skip intermediate revisions"
"reconnect replays every event"
"publication is production-verified"
"Realtime is required for convergence"
"all tenant visibility is runtime-proven"
```

بدون runtime evidence.

الصحيح:

```text
Realtime is best-effort acceleration; durable generation + unified revision pull remain the recovery path.
```

---

# 114. Required implementation order

ينفذ بالترتيب:

```text
A. verify v311 ZIP SHA/entry count/Kotlin count
B. verify SESSION_311 SHA
C. verify v311 verification SHA + handoff312Authorized=true
D. freeze Room80 + historical SQL hashes
E. carry forward exactly 9 inherited exceptions
F. inventory all Realtime sources/consumers
G. inventory current 8 watched tables and event shapes
H. inventory publication evidence available in repo
I. inventory lifecycle start/stop call sites
J. freeze SyncRealtimeHint contract authority
K. define typed OrganizationRealtimeSource hint boundary
L. define listener generation/handle ownership
M. implement generation-safe start/stop
N. implement stale callback guards
O. implement bounded hint coalescer
P. implement duplicate/out-of-order normalization
Q. implement aggregate target bound + overflow fallback
R. wire accepted hint to durable 311 generation
S. remove V2 Realtime dependence on legacy fullSync
T. ensure UnifiedSyncPullEngine remains cursor/apply authority
U. ensure target revision advisory only; no cursor jump
V. handle reconnect with bounded catch-up request
W. preserve optional failure behavior
X. preserve/route withdrawal side-flow safely
Y. classify Messages Realtime boundary without incidental redesign
Z. define safe publication allowlist/migration if required
AA. static-check RLS/visibility/least privilege for new publication surface
AB. ensure no TRUNCATE handler
AC. keep Room 80
AD. keep V2 + Realtime defaults OFF
AE. emit v312 coverage/policy artifacts
AF. run >=300 v312 fixtures
AG. rerun 325 v311 fixtures
AH. rerun 499 v310 fixtures
AI. rerun 272 v309 fixtures
AJ. rerun 137 v308 fixtures + MODEL_10K_PASS
AK. run verifier twice deterministically
AL. emit JSON/MD reports
AM. verify historical SQL byte-identical
AN. document v312 SQL as NOT_EXECUTED unless actually run
AO. package v312 ZIP + SHA
```

---

# 115. Required file-scope discipline

مسموح مبدئيًا تعديل/إضافة:

```text
data/sync/.../RealtimeManager.kt
data/sync/.../SyncReliability.kt             # Realtime gate/coalescer only
data/sync/.../SyncManager.kt                 # hint orchestration boundary only
data/sync/.../*Realtime* / *Hint* files

data/network/.../OrganizationRealtimeSource.kt
data/network/.../SupabaseOrganizationRealtimeSource.kt
data/network/.../realtime DTO/mappers

app/.../DefaultAuthSessionCoordinator.kt      # lifecycle call contract only
app/.../DeferredStartupCoordinator.kt         # typed scope start only
app/.../SyncOperations or settings bridge     # compatibility routing only

data/database/.../dao/UnifiedSyncDao.kt       # read/max accelerator helper only if needed; no schema change

supabase/migrations/*v312*                    # at most one additive Realtime/publication migration

docs/sync/*v312*
tools/*v312*
scripts/*v312*
verification/report artifacts
```

---

# 116. Forbidden file-scope drift

ممنوع دون `BLOCKED_SCOPE_DRIFT`:

```text
UI redesign
business feature refactors
financial business-rule changes
inventory costing changes
Optimal domain changes
Room schema migration
bootstrap/full resync implementation
legacy deletion
Gradle dependency upgrades
FeatureFlag default-on
server push/pull contract rewrite
change-log payload redesign unrelated to safe Realtime projection
```

---

# 117. Protected data-plane files

الأصل أن تبقى byte-identical:

```text
UnifiedSyncPushEngine.kt
UnifiedSyncConflictEngine.kt
UnifiedStrongerSyncBridge.kt
UnifiedStrongerSourceFactory.kt
UnifiedStrongerSyncChangeApplier.kt
UnifiedSyncPushRegistry.kt
UnifiedSyncPullRegistry.kt
financial/inventory/optimal business writers
```

`UnifiedSyncPullEngine.kt` لا يتغير إلا إذا احتاج orchestration-facing bounded target metadata بدون تغيير cursor/apply semantics.

أي semantic data-plane rewrite:

```text
BLOCKED_310_CONTRACT_DRIFT
```

---

# 118. Unified contract protection

`UnifiedSyncContract.kt` و`UnifiedSyncAggregateRegistry.kt` يجب أن يبقيا byte-identical افتراضيًا.

312 تستخدم `SyncRealtimeHint` الموجود بدل إعادة تعريفه.

إذا تعديل contract حتمي، يمنع PASS إلا بعد regression proof صريح ويجب ألا يغير wire version `verto-unified-sync / 1` دون جلسة contract migration مستقلة.

---

# 119. No silent partial PASS

PASS ممنوع إذا تحقق أي واحد:

```text
Realtime still emits only Unit in new V2 path without typed mapping evidence
Realtime applies Room directly
Realtime writes cursor
serverRevision hint can jump cursor
hint can force infinite catch-up loop
100-event burst creates unbounded wakes
stale tenant/session listener can request sync
late old stop can kill new listener
multiple active listeners remain possible
reconnect assumes replay guarantee
publication uses wildcard
publication leaks business payload/visibility
TRUNCATE handler exists
Realtime absence breaks convergence
V2 Realtime path calls legacy fullSync
Room != 80
historical v305/v309/v310 SQL changed
one new waiver
one 311 regression
one 310 regression
one 309 regression
one 308 regression
V2 default ON
Realtime default ON
runtime SQL claimed without execution
```

---

# 120. Packaging

المخرج المتوقع بعد تنفيذ 312:

```text
Verto-v312-source-of-truth.zip
Verto-v312-source-of-truth.zip.sha256
```

لا build caches/secrets/extracted duplicate input.

---

# 121. Mandatory artifacts inside v312 archive

```text
VERTO_SYNC_REALTIME_VERIFICATION_v312.json
VERTO_SYNC_REALTIME_VERIFICATION_v312.md
docs/sync/VERTO_SYNC_REALTIME_PUBLICATION_v312.csv
docs/sync/VERTO_SYNC_REALTIME_SOURCES_v312.csv
docs/sync/VERTO_SYNC_REALTIME_LIFECYCLE_v312.md
docs/sync/VERTO_SYNC_REALTIME_HINT_POLICY_v312.md
tools/verify_sync_realtime_v312.py
tools/test_sync_realtime_verification_v312.py
scripts/verify-v312-sync-realtime.sh
Verto-v312-report.md
```

وإذا أنشئت migration:

```text
supabase/migrations/*_v312_*.sql
```

---

# 122. Output archive integrity

بعد packaging:

1. compute SHA-256.
2. count entries.
3. test ZIP open.
4. verify mandatory v312 artifacts.
5. verify schema80 unchanged.
6. verify AppDatabase/MigrationCatalog unchanged.
7. verify v305/v309/v310 migrations unchanged.
8. verify at most one new v312 server migration.
9. verify no wildcard publication.
10. verify no sensitive publication surface.
11. verify no Realtime direct Room/cursor writes.
12. verify Runtime V2 OFF.
13. verify Realtime default OFF.
14. verify no secrets/cache artifacts.
15. write SHA sidecar.

---

# 123. Final acceptance questions

قبل PASS يجب الإجابة **نعم**:

1. هل input ZIP SHA = `89700b20...`؟
2. هل archive entries = `2650`؟
3. هل production Kotlin baseline = `1178`؟
4. هل SESSION_311 SHA = `cc788ce4...`؟
5. هل v311 verification SHA = `efe8faa2...`؟
6. هل `handoff312Authorized=true`؟
7. هل v311 blockers فارغة؟
8. هل inherited exceptions exactly 9؟
9. هل `new312WaiverCount=0`؟
10. هل Room بقي 80؟
11. هل schema80 unchanged؟
12. هل AppDatabase/MigrationCatalog unchanged؟
13. هل v305/v309/v310 SQL byte-identical؟
14. هل typed `SyncRealtimeHint` boundary موجودة؟
15. هل Realtime لا يطبق Room مباشرة؟
16. هل Realtime لا يكتب cursor؟
17. هل serverRevision لا يصبح cursor authority؟
18. هل target revision لا يقفز فوق revisions وسيطة؟
19. هل missing revision يعود normal revision pull؟
20. هل missing target يعود normal revision pull؟
21. هل unknown target fail-soft إلى normal pull؟
22. هل aggregate target set bounded؟
23. هل overflow يعود organization-wide normal pull؟
24. هل burst 100 لا يصنع storm؟
25. هل duplicate hints coalesced؟
26. هل out-of-order hints تحتفظ بأعلى revision metadata؟
27. هل event أثناء active drain لا يضيع بسبب `syncInProgress`؟
28. هل durable generation من 311 هي pending authority؟
29. هل process death بعد generation لا يفقد correctness؟
30. هل فقدان in-memory target metadata بعد crash مقبول مع normal pull fallback؟
31. هل lifecycle handle/generation موجود؟
32. هل late old stop لا يوقف listener الجديد؟
33. هل late old callback no-op؟
34. هل active listener count <= 1؟
35. هل wrong org hint no-op؟
36. هل wrong user hint no-op؟
37. هل old session epoch hint no-op؟
38. هل same-org reauth stale listener مرفوض؟
39. هل user switch same org آمن؟
40. هل logout race آمن؟
41. هل org switch race آمن؟
42. هل reconnect لا يفترض replay؟
43. هل reconnect يطلب bounded catch-up؟
44. هل publication absence لا تكسر convergence؟
45. هل publication surface allowlisted؟
46. هل wildcard publication = 0؟
47. هل sensitive payload publication = 0؟
48. هل tenant/visibility rule موثقة لكل surface؟
49. هل TRUNCATE handler = 0؟
50. هل V2 Realtime path لا يستدعي legacy fullSync؟
51. هل UnifiedSyncPullEngine بقي apply/cursor authority؟
52. هل owner310 stronger semantics محفوظة؟
53. هل remote apply enqueue echo = 0؟
54. هل >=300 v312 fixtures PASS؟
55. هل 325/325 v311 regression PASS؟
56. هل 499/499 v310 regression PASS؟
57. هل 272/272 v309 regression PASS؟
58. هل 137/137 v308 regression PASS؟
59. هل MODEL_10K_PASS؟
60. هل verifier deterministic مرتين؟
61. هل Runtime V2 بقي OFF؟
62. هل Realtime default بقي OFF؟
63. هل SQL runtime status موثق بصدق؟
64. هل التقرير لا يدعي delivery/runtime proof غير منفذ؟

أي `لا` تمنع PASS.

---

# 124. Handoff إلى 313

فقط عند نجاح كل static gates:

```text
handoff313Authorized = true
```

313 يجوز أن تفترض:

```text
Realtime is hint-only
Realtime can accelerate unified revision pull
hint bursts are bounded/coalesced
stale tenant/session listeners are rejected
late stop cannot kill current listener
Realtime absence does not break convergence
cursor remains PullEngine-owned
Runtime V2 and Realtime defaults remain OFF
```

313 لا يجوز أن تفترض:

```text
bootstrap already implemented
cursor expiry recovery implemented
publication runtime applied
multi-device runtime convergence proven
```

---

# 125. Handoff إلى 314

312 لا تمنح cutover authority.

314 لا تفعل V2/Realtime إلا بعد 313 recovery gates، وبعد runtime evidence المناسبة.

لا يجوز اعتبار static publication proof مساويًا production Realtime proof.

---

# 126. الخلاصة النهائية للعقد

312 يجب أن تنهي المرحلة بهذه الحقيقة:

```text
Realtime carries hints, never truth.
Hints are tenant/session/lifecycle scoped.
Bursts coalesce into bounded durable sync intent.
serverRevision is an accelerator hint, not a cursor.
No Realtime path can skip revision order or mutate Room directly.
A stale stop cannot kill a newer listener.
A stale callback cannot request work for a new tenant/session.
Reconnect and missing publications degrade to normal revision pull.
Room remains 80.
Historical server migrations remain unchanged.
V2 and Realtime remain OFF until later gates authorize rollout.
```

إذا تعذر إثبات أي lifecycle/visibility/cursor invariant، التنفيذ يتوقف fail-closed بدل إعلان PASS جزئي.
