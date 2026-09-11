# SESSION_311_FINAL.md

## Verto Sync Modernization — Session 311

### Durable WorkManager Drain — Pending Generation, Retry Taxonomy, Checkpoints & Tenant-Safe Orchestration

**نوع المستند:** عقد تنفيذ مستقل ونهائي  
**الجلسة:** 311  
**الحالة:** `PLAN ONLY — EXECUTABLE ON V310 STATIC-PASS BASELINE`  
**تاريخ الصياغة:** 2026-08-21  
**مصدر الكود المفحوص:** `Verto-v310-source-of-truth.zip`  
**SHA-256 للمصدر المفحوص:** `7b9a7070e389e0ec047c9252fde03f1891c2ffeaef406f97c3f9f30d15275b30`  
**Archive entries:** `1765`  
**Production Kotlin files:** `1178`  
**عقد 310 المرجعي:** `SESSION_310_FINAL.md`  
**SHA-256 لعقد 310:** `6b26c6de0cc3bd500ddc15fee830f5fcea1fd84c937d4667584c268e49384094`  
**الخطة الأم:** `VERTO_SYNC_MODERNIZATION_PLAN_v304-v314.md`  
**SHA-256 للخطة:** `a767087c5f1659dcd7c660c189542ef2d7774c0f647ff9321e9f53219a7dfd97`  
**Contract authority:** `verto-unified-sync / 1`  
**Room الحالي المثبت:** `80`  
**Room المستهدف في 311:** `80` — لا migration Room مطلوبة؛ يستعمل 311 `sync_sequence_state` كـdurable generation authority.  
**AndroidX WorkManager المثبت:** `2.10.0`  
**Runtime V2 عند البداية:** `OFF`  
**311 acceptance basis:** الفحوص الساكنة/model verification إلزامية. Gradle/Android runtime ليس شرطًا لـ`PASS_STATIC` إذا البيئة غير متوفرة أو جرى تجاوز runtime صراحة. أي Build/Test يتم تشغيله فعليًا ويفشل يبقى فشلًا حقيقيًا. لا SQL runtime مطلوب في 311 لأن الجلسة محلية orchestration-only ولا تنشئ migration خادم جديدة.

---

# 0. الحكم التنفيذي المختصر

311 ليست جلسة Data Plane جديدة، ولا Realtime Targeting، ولا Bootstrap، ولا Cutover.

هي جلسة تجعل **طلب المزامنة نفسه durable** وتمنع ضياعه بسبب `ExistingWorkPolicy.KEEP` أو mutex أو انتقال tenant.

النموذج المستهدف:

```text
manual / foreground / realtime / startup / periodic request
        │
        ▼
Durable requested generation (Room.sync_sequence_state)
        │
        ├── enqueue/wake WorkManager successor-safe
        │
        ▼
Tenant + session-epoch validation
        │
        ▼
loop:
    snapshot requestedGeneration
    drain eligible authoritative push sources
    pull unified revision delta
    inspect newer generation / eligible work
    CAS mark drainedGeneration only if still idle
    if race/new intent: continue
    if bounded continuation needed: enqueue successor, then stop
        │
        ▼
exit only after durable idle proof
```

القاعدتان الحاكمتان:

```text
WorkManager is a wake-up mechanism, not the source of sync intent.
```

```text
A sync request committed to durable generation must survive KEEP races,
active-worker overlap, process death, and tenant/session changes.
```

---

# 1. بوابة البداية من 310

الحالة المثبتة داخل `VERTO_SYNC_STRONGER_VERIFICATION_v310.json`:

```text
finalVerdict = PASS_STATIC_STRONGER_FINANCIAL_INVENTORY_OPTIMAL_BRIDGE
               / INHERITED_307_EXCEPTIONS=9
               / ROOM_80_UNCHANGED
               / RUNTIME_V2_DISABLED
               / BUILD_NOT_VERIFIED
               / POSTGRES_NOT_EXECUTED
               / RUNTIME_EXECUTION_BYPASSED_BY_USER

owner310AggregateCount       = 17
owner310CoverageRows         = 17
owner310BridgeReadyCount     = 17
owner310BlockedCount         = 0
v310 fixtures                = 499/499 PASS
v309 regression fixtures     = 272/272 PASS
v308 regression fixtures     = 137/137 PASS
MODEL_10K_PASS               = true
new310WaiverCount            = 0
runtimeV2                    = DISABLED
handoff311Authorized         = true
blockers                     = []
```

311 لا تبدأ إذا تغير هذا baseline دون توثيق:

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

قواعد 311:

1. تحمل كما هي في artifact مستقل.
2. لا يعاد تسميتها PASS نظيفًا.
3. `new311WaiverCount = 0` شرط PASS.
4. أي استثناء تلمسه 311 يجب أن يظل `worsened=false`.
5. لا يستخدم أي استثناء لتبرير lost sync intent أو stale-tenant execution أو retry misclassification.

---

# 3. ترتيب السلطات — Authority Order

عند التعارض:

1. `Verto-v310-source-of-truth.zip` ذو SHA المثبت.
2. `VERTO_SYNC_STRONGER_VERIFICATION_v310.json/.md`.
3. `docs/sync/VERTO_SYNC_STRONGER_COVERAGE_v310.csv`.
4. `docs/sync/VERTO_SYNC_STRONGER_SERVER_ADAPTERS_v310.csv`.
5. `docs/sync/VERTO_SYNC_STRONGER_PRODUCER_COVERAGE_v310.csv`.
6. `docs/sync/VERTO_SYNC_307_EXCEPTION_CARRYFORWARD_v310.json`.
7. `SESSION_310_FINAL.md`.
8. `VERTO_SYNC_PUSH_VERIFICATION_v309.json/.md`.
9. `VERTO_SYNC_PULL_VERIFICATION_v308.json/.md`.
10. `docs/sync/VERTO_UNIFIED_SYNC_CONTRACT_v304.json/.md`.
11. `SyncWorker.kt` / `SyncManager.kt` / `SyncReliability.kt` / `SyncWorkScope.kt` الحالية.
12. `UnifiedSyncPushEngine.kt` و`UnifiedSyncPullEngine.kt`.
13. `UnifiedStrongerSyncBridge.kt` وregistry الخاص بـ310.
14. هذا العقد.
15. الخطة الأم v304→v314.

WorkManager policy الحالية ليست correctness authority إذا تعارضت مع durable generation.

---

# 4. بصمات authority عند البداية

يجب تثبيت هذه البصمات قبل أي تعديل:

```text
data/sync/.../SyncWorker.kt
  16eab4a5533bf791e860281c08c142c2fa8d0f618471a1b54b7189f2f6c03a43

data/sync/.../SyncManager.kt
  e825c4d4a599da57f8c4b50152da9960fd199db87032ad8a4864e5db7ddbf063

data/sync/.../SyncReliability.kt
  a0f0f60c290524114f0ac0477383394ce494febce418986c9e8646ec63cf98a2

data/sync/.../SyncWorkScope.kt
  e9f7e0a050bf16995c82d1c9177c4656af2ad0fb3b4e9f859181ba59d2374697

data/sync/.../RealtimeManager.kt
  f5671b96b17c3f562391f97e7c9562f83c246f08d5f4921186ba4282a78270b6

core/common/.../FeatureFlags.kt
  ad159d5e9f91e406127a5220c4e6729a51f659577ea4e673f9cce98f0de57203

app/.../DefaultAuthSessionCoordinator.kt
  1f5e2a557f26f81886c342da5349e0340d00f5feaf1e223c927e98d6fbb1d2fa

app/.../OptimalSyncBridge.kt
  94aa1fc7ab8d69542c806bda9ea1048e2724f4c12c529c39a5249a4059b59b37

data/database/.../AppDatabase.kt
  9ff0dc54abaa87591bfdf294ce1d8888fd3993b0fbdfcf5d105ae1d3693c5bbb

data/database/.../MigrationCatalog.kt
  0a7f2b7263dd19723b9b68da6988f1b3e649b420325cf50b8694505d28c880c7

data/database/.../entity/UnifiedSyncEntities.kt
  3f0e9df13932677f73465697c2c21455886e63b447cd4e37d7c05343a0fffdd0

data/database/.../dao/UnifiedSyncDao.kt
  da459eae99695278f541ad48a30d74dbd0074483ce27605cfa15c6da1c736cfb

data/sync/.../push/UnifiedSyncPushEngine.kt
  0edc3258944b282558f3f204400d5d2e55c73bf517d032bd110bd94ee653c333

data/sync/.../pull/UnifiedSyncPullEngine.kt
  c4babd8f48bb16ff160a296238e1459a47dcc1e5592997eb5aeec717e153e6d7

data/network/.../UnifiedStrongerSyncBridge.kt
  43767bcc6147ef54a22ad61659d39f8e81bda2900657b817efb71dfcef398d54

data/sync/.../push/UnifiedStrongerSourceFactory.kt
  e2f9cf77f30695c07f0340d65bfccbe94ec38521eb321a41512c9e5888ccfb5f

data/sync/.../pull/UnifiedStrongerSyncChangeApplier.kt
  47043c3b0cf07ae955a647f65647bf3644d3e7aabd1bc7e93888c653b081fbf9

data/sync/.../push/UnifiedSyncPushRegistry.kt
  5a0083da2dd25f3947c22e9b083b0fb0fb7fad30aaac4c6aaf20d3914ee99c51

data/sync/.../pull/UnifiedSyncPullRegistry.kt
  04ca782471581ef7948564c93b18ef4a1e919e7c118611b957895547c1bbf250

gradle/libs.versions.toml
  43af168fd3e0428caee1aefb5e82950da72082dddae77877f494e70c9d19d8a2

VERTO_SYNC_STRONGER_VERIFICATION_v310.json
  0872e3f81b8b1a6968f19e0c305fcf5e92e6a7d9d15d0673ce17e98055a8211f

supabase/migrations/20260821150000_v310_verto_stronger_stream_bridge.sql
  43b2db6d6300bc5a59caffdffef071bd894a3356783dffc5de0a9afa2e13f1ce
```

Historical Room schemas/migrations وserver migrations لا تعدل في 311.

---

# 5. الحالة الحالية المثبتة من فحص v310

الكود الحالي يثبت:

```text
Room                                     = 80
WorkManager                              = 2.10.0
Immediate unique work policy             = ExistingWorkPolicy.KEEP
Periodic unique work policy              = ExistingPeriodicWorkPolicy.KEEP
SyncWorker                               = calls SyncManager.fullSync(scope)
SyncManager concurrency                  = Mutex
Second fullSync while locked             = Result.failure("sync_skipped...")
SyncRetryPolicy                          = classifies "sync_skipped" as TRANSIENT
Realtime while syncInProgress            = trigger suppressed/dropped
Realtime trigger                         = SyncManager.triggerPull() -> fullSync()
Durable requested generation             = absent
Durable drained generation               = absent
Work scope                               = organizationId + userId only
Session epoch in worker input             = absent
prepareSessionForOrg clear failure        = Result ignored
prepareSessionForOrg lastOrg write        = occurs after ignored clear result
prepareSessionForOrg cancels old worker   = no
prepareSessionForOrg stops realtime       = no
Unified V2 push engine                    = implemented, not production scheduled
Unified V2 pull engine                    = implemented, not production scheduled
Owner310 stronger bridges                 = 17/17 static-ready
Runtime V2                                = OFF
```

---

# 6. الفجوة الحرجة التي تملكها 311

يوجد race فعلي:

```text
worker A running
request B arrives
syncNow(B) → enqueueUniqueWork(... KEEP ...)
KEEP refuses successor because A exists
SyncManager may also return sync_skipped while lock is held
A finishes without durable knowledge of B
→ request B can disappear
```

وRealtime اليوم يملك مسار خسارة مباشر:

```text
syncInProgress == true
→ RealtimeTriggerGate.shouldTrigger(...) == false
→ no pending durable intent
```

هذه هي الفجوة المركزية في 311.

---

# 7. هدف 311 الدقيق

311 تنفذ فقط:

1. durable requested generation.
2. durable drained generation.
3. request-before-wake ordering.
4. successor-safe immediate WorkManager policy.
5. worker drain loop مع idle CAS.
6. عدم خسارة request أثناء worker قائم.
7. عدم خسارة realtime request أثناء worker قائم.
8. عدم استخدام mutex contention كnetwork retry.
9. retry taxonomy حقيقية.
10. 429/timeout/transient 5xx retry فقط كـnetwork transient.
11. auth fail closed بالنسبة للworker ولا يحول mutation إلى permanent business rejection بسبب transport auth.
12. validation/permanent fail closed.
13. conflict لا يصبح WorkManager network retry.
14. delayed outbox retry لا يسبب busy loop.
15. process-death continuation.
16. stale worker scope/session rejection.
17. tenant switch sequencing آمن.
18. عدم كتابة `lastOrgId=new` قبل نجاح transition.
19. old worker cancellation + stale guard.
20. old realtime stop قبل تثبيت session الجديدة.
21. V2 orchestration wiring يكون جاهزًا خلف feature flag فقط.
22. Room يبقى 80.
23. Server SQL يبقى byte-identical.
24. 310/309/308 regressions تبقى صفر.
25. Runtime V2 يبقى OFF.

---

# 8. ما ليست عليه 311

```text
Realtime targeted aggregate hints      → 312
Realtime serverRevision targeting      → 312
Realtime publication redesign          → 312
Realtime burst architecture النهائية   → 312
Full resync / trusted baseline          → 313
Cursor retention expiry recovery        → 313
Observability dashboard الكامل          → 313
Default-on V2                           → 314
Legacy sync removal                     → 314
Server migration جديدة                  → ليس ضمن 311
Room migration 80→81                    → ليست مطلوبة في التصميم المعتمد هنا
Business-rule redesign                  → ممنوع
Financial/Inventory/Optimal semantics   → لا تعاد صياغتها
```

---

# 9. Runtime policy

في نهاية 311:

```text
FeatureFlags.isVersionedSyncEnabled = false
FeatureFlags.isRealtimeSyncEnabled  = false (كما هو ما لم يتغير خارج scope؛ لا تفعّلها 311)
legacy runtime                      = preserved as default
V2 drain orchestration              = implemented + directly testable
V2 WorkManager production cutover   = OFF
Realtime targeted behavior          = not implemented
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

311 يجب أن تعيد استخدام:

```text
sync_sequence_state
```

كد durable orchestration generation authority.

ممنوع:

- تعديل schema80 JSON.
- إضافة 80→81 لمجرد generation counters.
- إنشاء DataStore-only generation كcorrectness authority إذا كان Room counter متاحًا.
- تعديل migrations 1→80.

إذا ظهر invariant لا يمكن تمثيله بأمان باستخدام `sync_sequence_state` + session epoch صغير غير شخصي:

```text
BLOCKED_SCHEMA_SCOPE_DRIFT
```

ولا تضاف migration عشوائية.

---

# 11. Durable generation authority

يستخدم `sync_sequence_state` بمفتاحين tenant-scoped:

```text
counter_kind = ORCHESTRATION_REQUESTED_GENERATION
counter_kind = ORCHESTRATION_DRAINED_GENERATION
aggregate_type = ''
aggregate_id   = ''
organization_id = current trusted organization
```

الحاكم:

```text
requestedGeneration >= drainedGeneration >= 0
```

ولا يجوز إنقاص أي منهما.

---

# 12. Generation request transaction

طلب sync جديد يجب أن ينفذ:

```text
Room transaction:
    ensure requested counter exists
    requestedGeneration += 1
    return new generation
commit
THEN wake/enqueue WorkManager
```

ممنوع:

```text
enqueue WorkManager first
then persist generation
```

لأن crash بينهما يفقد intent.

---

# 13. Request enqueue failure

إذا نجح generation commit ثم فشل enqueue:

```text
requestedGeneration remains > drainedGeneration
```

ولا يمحى الطلب.

Periodic/startup/next successful wake يجب أن يكتشفه لاحقًا.

لا rollback للgeneration بسبب فشل WorkManager API.

---

# 14. Immediate WorkManager policy

311 تمنع:

```text
ExistingWorkPolicy.KEEP
```

كمسار immediate correctness.

بما أن WorkManager الحالي `2.10.0`، السياسة المستهدفة:

```text
ExistingWorkPolicy.APPEND_OR_REPLACE
```

أو policy مكافئة مثبتة باختبار race تمنع ضياع successor.

`REPLACE` ممنوع كحل افتراضي إذا كان يلغي worker قائمًا أثناء network/Room transition.

---

# 15. Periodic Work policy

يجوز إبقاء:

```text
ExistingPeriodicWorkPolicy.KEEP
```

لأن periodic work مجرد wake source وليس source of intent.

Periodic worker عند التشغيل يجب أن:

1. يتحقق من tenant/session.
2. إذا لا يوجد requested generation جديد لكنه يوجد eligible V2 work، يرفع أو يتبنى generation موثقة حسب API المعتمد.
3. لا يخلق duplicate semantic intent.

---

# 16. Session epoch

`organizationId + userId` وحدهما لا يكفيان لتمييز stale job بعد logout/login أو session replacement.

311 تضيف opaque local `sessionEpoch` monotonic لا يحتوي token ولا PII.

خصائصه:

```text
increment on authenticated session activation/replacement
captured in SyncWorkScope input
checked before drain
checked between bounded passes
stale epoch → StaleSyncWorkScopeException / success-no-op worker outcome
```

يجب أن يبقى epoch خارج البيانات التي تمسح بطريقة قد تعيد استخدام القيمة مباشرة وتسمح collision مع stale job.

لا يخزن access token أو refresh token في WorkManager input.

---

# 17. SyncWorkScope المستهدف

يمتد semantic scope إلى:

```text
SyncWorkScope(
  organizationId,
  userId,
  sessionEpoch
)
```

و`stableKey` يجب أن يبقى tenant/account scoped، مع توثيق هل epoch يدخل work name أم input فقط.

القاعدة الموصى بها:

- unique work name = stable tenant/account identity.
- sessionEpoch = stale-work validation input.

حتى لا تنشأ أسماء work غير محدودة لكل login.

---

# 18. Worker validation hard gate

قبل أي push/pull:

```text
await auth initialization
require active session
resolve current profile
compare organizationId
compare userId
compare sessionEpoch
```

أي mismatch:

```text
STALE_SCOPE → Result.success() after no domain/network mutation
```

لا `Result.retry()`.

---

# 19. Worker drain loop

النمط المستهدف:

```text
validate scope + epoch
repeat bounded passes:
    observed = read requestedGeneration
    pushResult = drain eligible authoritative push sources
    pullResult = pull unified delta
    validate scope + epoch again

    if pullResult requires bootstrap/recovery:
        stop with explicit deferred-to-313 state

    if eligible work remains now:
        continue

    if requestedGeneration changed since observed:
        continue

    if CAS markDrained(observed) succeeds:
        re-read requestedGeneration
        if requested <= drained:
            success idle
        else:
            continue

if pass budget exhausted while intent/work remains:
    enqueue bounded continuation
    success current worker
```

---

# 20. Idle proof

Worker لا ينهي بسبب:

```text
"push call returned"
"pull call returned"
"mutex unlocked"
"WorkManager has no queued successor"
```

بل بسبب durable proof:

```text
requestedGeneration == drainedGeneration
AND no eligible-now authoritative push work
AND pull reported caught-up for current cursor
AND scope/session epoch still current
```

---

# 21. Idle CAS race

الاختبار المركزي:

```text
worker reads requested=10
worker finishes push/pull
request arrives → requested=11
worker attempts mark drained=10
```

PASS فقط إذا:

```text
worker does not exit as idle for generation 11
```

`markDrained(10)` لا يجوز أن يمحو أو يساوي generation 11.

---

# 22. Request during final-exit window

يجب اختبار أصعب window:

```text
worker performs final idle check
new request commits generation
request enqueue occurs while current work still RUNNING
current worker exits
```

PASS يتطلب واحدًا من:

- worker sees generation and continues، أو
- `APPEND_OR_REPLACE` leaves successor، أو
- equivalent proven wake handoff.

لا يسمح بوجود lost-intent window.

---

# 23. Bounded continuation ≠ network retry

إذا worker وصل budget تشغيلي مع `MORE_AVAILABLE`:

```text
schedule continuation successor
return Result.success()
```

أو equivalent continuation state.

ممنوع تصنيفها:

```text
TRANSIENT_NETWORK
```

لأن backlog ليس network failure.

---

# 24. Delayed retry no-spin

`sync_outbox` قد يحتوي `RETRY` مع `next_attempt_at > now`.

`countBacklog > 0` وحده لا يسمح بالloop المستمر.

311 يجب أن تميز:

```text
eligibleNow
nextEligibleAt
terminal/review backlog
```

أو equivalent query/result.

PASS:

```text
future retry row does not cause busy loop
worker exits/schedules future wake correctly
```

---

# 25. Unified push orchestration

311 لا تغير mutation semantics من 309/310.

هي فقط تستدعي data plane المحدد وتتعامل مع outcome.

يجب أن يبقى:

```text
at-least-once transport
immutable mutation identity
lease/CAS
receipt/conflict semantics
```

كما هي.

---

# 26. Stronger owner310 orchestration

الـ17 owner310 يحتفظون بالauthoritative durable intent الذي أثبته 310.

ممنوع أن تنشئ 311 generic `sync_outbox` نسخة ثانية لكي يسهل scheduling.

الـdrain coordinator يجب أن يستدعي authoritative source adapter/runtime المعتمد لكل stronger family دون duplicate durable intent.

---

# 27. Owner310 runtime states

311 تحترم الحالات:

```text
STRONGER_BRIDGE_READY_SHADOW
STRONGER_PULL_READY_SHADOW
SERVER_AUTHORITATIVE_NO_CLIENT_PUSH
PRESERVED_EXISTING_STRONGER_RUNTIME
```

`SERVER_AUTHORITATIVE_NO_CLIENT_PUSH` لا يحصل على producer صناعي.

---

# 28. Pull order

الترتيب الحاكم لكل pass:

```text
push eligible authoritative local intent
then pull unified server revision delta
```

السبب:

- يسمح للserver receipt/change echo بتسوية accepted mutations.
- يحافظ على 309/308 contracts.

لا timestamp pull fallback.

---

# 29. Pull outcomes

311 تتعامل صراحة مع:

```text
CAUGHT_UP
MORE_AVAILABLE
BOOTSTRAP_REQUIRED
RECOVERY_REQUIRED
```

القواعد:

- `MORE_AVAILABLE` → continuation، لا network retry.
- `BOOTSTRAP_REQUIRED` → defer/fail-closed to 313، لا full-table timestamp fallback.
- `RECOVERY_REQUIRED` → defer/fail-closed to 313.

---

# 30. Retry taxonomy — الحاكم

التصنيف المطلوب:

```text
TRANSIENT_NETWORK
RATE_LIMITED
AUTHENTICATION
VALIDATION
PERMANENT_PROTOCOL
CONFLICT_DOMAIN
STALE_SCOPE
RECOVERY_REQUIRED
LOCAL_STORAGE_FAILURE
CANCELLED
```

يجوز أسماء مكافئة، لكن لا collapse يضيع المعنى.

---

# 31. `sync_skipped` ممنوع كnetwork transient

الحالة الحالية:

```text
SyncRetryPolicy.classify(...) treats "sync_skipped" as TRANSIENT
```

311 يجب أن تزيل هذا المسار.

بعد durable generation، lock contention يعني:

```text
request is pending / another drain owns execution
```

وليس network failure.

العداد:

```text
syncSkippedAsTransientCount = 0
```

---

# 32. Authentication failures

401/403/auth failure:

- لا يتحول إلى generic network retry loop.
- لا يحول immutable business mutation إلى `REJECTED` لمجرد transport auth failure.
- worker يوقف الدورة ويترك intent قابلة للاستئناف بعد session/auth recovery.
- لا يحاول cross-tenant fallback.

إذا intent leased، يعاد إلى safe retry/pending state وفق CAS دون تغيير business identity.

---

# 33. Transient network failures

يشمل على الأقل:

```text
timeout
socket/connectivity
502
503
504
```

تظل retryable bounded.

WorkManager retry وoutbox row retry لا يجوز أن يتضاربا إلى exponential × exponential غير محدود بلا توثيق.

---

# 34. 429 / Retry-After

429 يجب أن:

- يصنف rate-limited/transient.
- يحترم `retryAfterEpochMillis` أو server hint عندما متاحًا.
- لا busy-loop قبل الوقت.
- لا يحول mutation إلى rejected.

---

# 35. Validation/permanent failures

أمثلة:

```text
malformed payload
contract unsupported
invalid operation
permission/business validation permanent
server protocol inconsistency proven permanent
```

لا WorkManager network retry.

تسجل fail-closed/terminal/review وفق data-plane contract.

---

# 36. Conflict ليس network retry

`SyncReceiptStatus.CONFLICT` أو domain conflict:

```text
→ conflict engine / REQUIRES_REVIEW / domain resolution
```

وليس:

```text
Result.retry() because "sync failed"
```

العداد:

```text
conflictAsNetworkRetryCount = 0
```

---

# 37. Cancellation

`CancellationException` لا تلتقط كفشل دائم ولا transient business failure.

يجب إعادة رميها/احترام structured cancellation.

لا تغير outbox terminal state بسبب worker cancellation وحده.

---

# 38. Process death

سيناريو:

```text
requested generation committed
worker starts
process dies mid-push or after server commit
app restarts
```

PASS إذا:

- requested generation لا تضيع.
- source leases recover حسب 309/310 rules.
- same mutation/business identity يعاد استخدامها.
- next worker drains pending work.
- no duplicate semantic effect.

---

# 39. Process death after drain before mark

إذا push/pull اكتمل ثم مات process قبل `drainedGeneration` commit:

```text
next worker repeats safely
```

هذا مقبول لأن idempotency + inbox/cursor atomicity تمنع semantic duplication.

لا يكتب drained generation قبل اكتمال pass.

---

# 40. Mutex semantics

`SyncManager` mutex يبقى local serialization aid فقط.

لا يصبح durable requested-intent authority.

إذا وصل request أثناء lock:

```text
generation increments
request accepted
no sync_skipped loss
```

---

# 41. Foreground/manual request coverage

فحص v310 وجد `13` call sites/wrappers مرتبطة بـ`fullSync()` في app/features/data sync chain.

311 يجب أن تنتج coverage artifact يثبت أن كل entry point في V2 path:

- يرفع generation، أو
- يمر عبر compatibility wrapper يرفع generation عند V2 enabled.

ممنوع direct V2 data-plane bypass من UI.

---

# 42. Realtime request في 311

311 لا تنفذ targeted hint.

لكن يجب أن تمنع الضياع الحالي:

```text
syncInProgress == true
```

لا يجوز أن يؤدي إلى إسقاط كل نية realtime.

في 311 يكفي:

```text
normal sync generation request
```

ثم 312 تحولها إلى targeted/coalesced hint semantics.

---

# 43. Realtime boundary protection

311 لا تغير:

- server publication semantics.
- aggregate targeting.
- serverRevision hint payload.
- reconnect architecture النهائي.

هذه 312.

التغيير المسموح فقط هو توجيه trigger الحالي إلى durable request بدل direct `fullSync()`/drop.

---

# 44. Startup/restore request

Authentication restore الذي يحتاج initial sync يجب أن:

- يثبت active scope/session epoch.
- يرفع generation قبل wake.
- لا يستخدم unscoped work name.

في legacy mode يبقى السلوك الحالي متاحًا حتى 314.

---

# 45. Tenant switching — state machine

الانتقال المستهدف:

```text
OLD_ACTIVE
→ INVALIDATE_OLD_SESSION_EPOCH
→ CANCEL_OLD_WORK
→ STOP_OLD_REALTIME
→ WAIT/VERIFY_OLD_DRAIN_CANNOT_COMMIT
→ CLEAR/ISOLATE_LOCAL_SESSION_DATA
→ VERIFY_CLEAR_SUCCESS
→ COMMIT_NEW_LAST_ORG
→ ACTIVATE_NEW_SESSION_EPOCH
→ SCHEDULE_NEW_SCOPE
→ NEW_ACTIVE
```

أي failure قبل `COMMIT_NEW_LAST_ORG` يترك transition غير مكتمل ولا يدعي نجاح المؤسسة الجديدة.

---

# 46. Current `prepareSessionForOrg` bug

الحالة الحالية:

```text
if old org != new org:
    clearLocalData()   // Result ignored
setLastOrganizationId(newOrg)
```

311 يجب أن تمنع:

```text
clearLocalData failed
AND lastOrgId became newOrg anyway
```

العداد:

```text
earlyNewOrgCommitCount = 0
```

---

# 47. Old worker cancellation

قبل تفعيل tenant جديدة:

- cancel unique periodic old scope.
- cancel immediate chain old scope.
- stale scope/epoch guard يبقى الحماية النهائية حتى لو cancellation late.

لا تعتمد correctness على أن `cancel()` لحظي.

---

# 48. Old realtime stop

311 يجب أن يوقف listener القديم في transition.

لكن structured generation-safe start/stop race النهائي يبقى 312.

في 311 gate المطلوب:

```text
old org realtime cannot issue accepted request after new tenant activation
```

---

# 49. Logout أثناء push

سيناريو إلزامي:

```text
worker leases mutation
network call in progress
logout/session epoch invalidated
response returns
```

PASS إذا:

- لا cross-tenant apply.
- source CAS/identity تمنع stale row corruption.
- old worker لا يبدأ operation جديدة بعد stale detection.
- no new-org generation drained by old worker.

---

# 50. Org switch أثناء pull

سيناريو إلزامي:

```text
old-org pull network request in flight
switch org
old response arrives
```

PASS فقط إذا old response لا يطبق داخل Room الجديدة.

Scope validation + session transition serialization يجب أن تمنع ذلك.

---

# 51. Same-org reauthentication

إذا user/org نفسهما لكن session أعيدت:

- stale old worker يرفض عبر epoch.
- new worker يلتقط epoch الجديدة.
- لا يعتمد فقط على `organizationId:userId`.

---

# 52. Multi-user same organization

`userId` يبقى جزءًا من work scope.

Worker لuser A لا يواصل كuser B حتى لو `organizationId` نفسه.

---

# 53. Tenant local-data strategy

v310 ما زال single-active-tenant local Room مع clear/isolation strategy الحالية.

311 لا تعيد تصميم DB multi-tenant.

إنما تضمن أن switching لا يسمح stale writer أثناء/بعد clear.

إضافة `organization_id` الشاملة لكل row أو DB-per-tenant تبقى تحسينًا طويل المدى خارج هذه الجلسة.

---

# 54. LastOrgId authority

`lastOrgId` يبقى session bookkeeping فقط.

ليس sync cursor.

لا يستخدم لتجاوز auth profile/scope validation.

---

# 55. DataStore policy في 311

يسمح DataStore فقط لـ:

- session epoch غير الشخصي/غير السري.
- legacy compatibility checkpoint/report الحالي.

لا يستخدم كبديل عن durable requested/drained generation الموجود في Room.

---

# 56. Generic outbox retry visibility

311 يجوز أن تضيف DAO queries orchestration-only مثل:

```text
countEligibleOutboxNow(org, now)
nextEligibleRetryAt(org)
hasLeasedRows(org)
```

بدون schema change.

هذه queries لا تغير mutation state machine.

---

# 57. Stronger outbox retry visibility

إذا drain coordinator يحتاج معرفة `eligibleNow` من Financial/Inventory/Optimal:

- يستخدم source-owned query/repository semantics.
- لا ينسخ rows إلى generic outbox.
- لا يتجاوز source ordering/lease policy.

---

# 58. WorkManager retry vs domain retry

الحاكم:

```text
Domain/outbox retry = one semantic mutation waiting for safe resend.
WorkManager retry    = wake orchestration after transient execution failure.
Continuation         = more bounded work exists; not an error.
```

لا تخلط الثلاثة.

---

# 59. Backoff policy

WorkManager backoff يبقى bounded ومناسبًا للtransient worker failure.

Outbox `next_attempt_at` يبقى source-specific delivery authority.

Worker لا يتجاهل row backoff بسبب WorkManager wake مبكر.

---

# 60. Manual sync user semantics

عند manual request أثناء worker:

المستخدم لا يحصل على `sync_skipped` كفشل لأن الطلب صار queued durably.

يجوز API أن تعيد outcome مثل:

```text
ACCEPTED_NEW_DRAIN
COALESCED_WITH_ACTIVE_DRAIN
```

لكن لا تدعي completion قبل durable idle.

---

# 61. `syncCompleted` semantics

event `syncCompleted` لا يطلق لمجرد انتهاء pass إذا generation أحدث ما زالت pending.

يطلق عند durable idle completion للجولة المطلوبة/المعنى المحدد موثقًا.

لا يضلل UI بأن sync انتهى بينما requested > drained.

---

# 62. `lastSyncCompletedAtMillis`

هو observability/display metadata فقط.

لا يستخدم لترتيب correctness أو suppress durable request.

Realtime cooldown الذي يعتمد عليه لا يجوز أن يمحو generation request مؤكدة.

---

# 63. Reports

Persisted sync report يجب أن يميز على الأقل:

```text
completed idle
queued/pending generation
transient failure
auth blocked
validation/permanent
stale scope
recovery required
```

ولا يخزن secrets/payloads/tokens.

---

# 64. Runtime V2 wrapper

يفضل إنشاء abstraction واضحة مثل:

```text
interface SyncDrainCoordinator {
    suspend fun request(scope, reason): Long
    suspend fun drain(scope): SyncDrainResult
}
```

أو تصميم مكافئ.

لا تضع WorkManager API داخل domain/Room transaction.

---

# 65. Drain reasons

يجب على الأقل تمييز مصادر الطلب تشخيصيًا:

```text
MANUAL
FOREGROUND
REALTIME
STARTUP
PERIODIC
OUTBOX_WRITE
CONTINUATION
```

reason observability فقط؛ لا تغير correctness priority دون عقد منفصل.

---

# 66. Outbox-write wake

إذا producer جديد في V2 ينشئ durable outbox intent، يجب أن يوجد مسار wake أو periodic guarantee.

لا يشترط 311 تعديل كل producer إذا 307 already schedules elsewhere، لكن coverage يجب أن يثبت source-by-source كيف سيستيقظ drain.

---

# 67. No duplicate scheduling intent

رفع generation مرة واحدة لlogical request يكفي.

ممنوع wrapper chain واحد يرفع generation في ثلاث طبقات لنفس click بلا سبب.

الزيادة الزائدة ليست correctness failure إذا آمنة، لكنها يجب أن تكون bounded ومثبتة لتجنب storms.

---

# 68. No direct network in generation transaction

ممنوع:

```text
Room transaction {
    increment generation
    network push/pull
}
```

الtransaction فقط لتثبيت state المحلية.

---

# 69. No cursor write in orchestrator

311 لا تكتب `sync_cursor` مباشرة.

فقط `UnifiedSyncPullEngine` يملك cursor advancement الذري من 308.

---

# 70. No receipt/conflict reinterpretation

311 لا تحول:

```text
CONFLICT
REQUIRES_REVIEW
REJECTED
```

إلى worker retry لمجرد وجود backlog.

---

# 71. No derived-state push

كل قيود 310 المتعلقة بالFinancial/Inventory/Optimal تبقى.

لا scheduling optimization يبرر push balance snapshot أو generic LWW.

---

# 72. Server migration policy

311 لا تنشئ server migration جديدة.

يجب أن تبقى byte-identical:

```text
v305 migration
v309 migration
v310 migration
```

أي اختلاف:

```text
FAIL_HISTORICAL_MIGRATION_DRIFT
```

---

# 73. Room protected files

يجب أن تبقى byte-identical:

```text
app/schemas/.../AppDatabase/80.json
AppDatabase.kt
MigrationCatalog.kt
AppDatabaseMigrations79To80.kt
historical Room migrations
```

يجوز تعديل `UnifiedSyncDao.kt` فقط لإضافة queries/transactions orchestration-safe دون schema change.

---

# 74. Feature flag protection

`FeatureFlags.isVersionedSyncEnabled` يبقى `false` افتراضيًا.

يمكن للكود الجديد أن يكون خلف هذا flag.

ممنوع verifier أن يمر إذا default تغير إلى true.

---

# 75. 312 boundary

311 تسلم 312 فقط:

```text
durable generation
successor-safe wake
active-worker request preservation
tenant/session-safe drain entry
normal realtime request no-loss
```

312 تملك:

```text
SyncHint
aggregate targeting
serverRevision hint
burst coalescing النهائي
lifecycle generation-safe realtime channels
```

---

# 76. 313 boundary

إذا pull أعاد:

```text
BOOTSTRAP_REQUIRED
RECOVERY_REQUIRED
```

311 لا تمسح الجداول ولا تستخدم timestamp fallback.

تسجل الحالة وتؤجل safe recovery إلى 313.

---

# 77. 314 boundary

311 لا تحذف legacy participants ولا legacy timestamp paths.

لا تجعل V2 default-on.

Cutover/removal في 314 فقط بعد 312/313 gates.

---

# 78. Required request-source coverage artifact

إنشاء:

```text
docs/sync/VERTO_SYNC_REQUEST_SOURCES_v311.csv
```

أعمدة إلزامية:

```text
source_id
source_type
call_site
current_behavior_v310
v311_entry_point
generation_incremented
work_wake_policy
tenant_scope
session_epoch_checked
can_arrive_during_active_worker
lost_intent_prevention
runtime_state
evidence
```

يجب تغطية كل manual/foreground/realtime/startup/periodic/specialized scheduling entry point المعروف.

---

# 79. Retry matrix artifact

إنشاء:

```text
docs/sync/VERTO_SYNC_RETRY_MATRIX_v311.csv
```

أعمدة:

```text
failure_class
examples
outbox_transition
worker_result
workmanager_retry
retry_after_authority
user_action_required
terminal_or_nonterminal
v311_rule
evidence
```

لا UNKNOWN/TODO/LATER.

---

# 80. Tenant transition artifact

إنشاء:

```text
docs/sync/VERTO_SYNC_TENANT_TRANSITIONS_v311.csv
```

يغطي:

```text
login
restore
logout
org_switch
same_org_reauth
user_switch_same_org
stale_worker
stale_realtime
clear_failure
```

مع preconditions/actions/postconditions/failure behavior.

---

# 81. Drain state artifact

إنشاء:

```text
docs/sync/VERTO_SYNC_DRAIN_STATE_v311.md
```

يوثق state machine والحالات القانونية:

```text
IDLE
REQUESTED
DRAINING
CONTINUATION_SCHEDULED
AUTH_BLOCKED
RECOVERY_REQUIRED
STALE_SCOPE
```

هذه logical states؛ لا يلزم table جديدة إذا derived safely من generation/outbox/outcome.

---

# 82. Static verifier artifacts

يجب إضافة:

```text
tools/verify_sync_orchestration_v311.py
tools/test_sync_orchestration_verification_v311.py
scripts/verify-v311-sync-orchestration.sh
```

أو أسماء مكافئة موثقة.

---

# 83. Verifier determinism

`verify-v311-sync-orchestration.sh` يشغل verifier مرتين.

الـnormalized output hash يجب أن يتطابق.

عدم determinism:

```text
FAIL_V311_VERIFIER_NONDETERMINISTIC
```

---

# 84. v311 model fixtures — الحد الأدنى

إنشاء >= `300` fixture/model assertions خاصة بـ311.

تغطي على الأقل:

```text
request generation monotonicity
requested/drained invariant
syncNow during worker
realtime during worker
idle CAS race
final-exit enqueue race
APPEND_OR_REPLACE successor
process death before enqueue
process death mid-drain
process death after server commit
process death after drain before drained mark
429
retry-after future timestamp
timeout
502/503/504
auth 401/403
validation permanent
conflict non-network
stale tenant input
stale session epoch
logout during push
org switch during pull
same-org reauth
user switch same org
clear failure
lastOrg commit ordering
delayed retry no-spin
bounded continuation
cursor bootstrap defer to 313
runtime flag remains off
```

---

# 85. Required named model passes

الـJSON النهائي يجب أن يحتوي true على الأقل:

```text
MODEL_REQUEST_GENERATION_MONOTONIC_PASS
MODEL_SYNCNOW_DURING_WORKER_PASS
MODEL_REALTIME_DURING_WORKER_PASS
MODEL_IDLE_CAS_RACE_PASS
MODEL_FINAL_EXIT_RACE_PASS
MODEL_SUCCESSOR_SAFE_WAKE_PASS
MODEL_PROCESS_DEATH_PENDING_INTENT_PASS
MODEL_PROCESS_DEATH_UNKNOWN_COMMIT_PASS
MODEL_DELAYED_RETRY_NO_SPIN_PASS
MODEL_429_RETRY_PASS
MODEL_TIMEOUT_RETRY_PASS
MODEL_AUTH_NOT_NETWORK_RETRY_PASS
MODEL_AUTH_NOT_BUSINESS_REJECTION_PASS
MODEL_PERMANENT_VALIDATION_FAIL_CLOSED_PASS
MODEL_CONFLICT_NOT_NETWORK_RETRY_PASS
MODEL_STALE_WORK_SCOPE_PASS
MODEL_SESSION_EPOCH_PASS
MODEL_LOGOUT_DURING_PUSH_PASS
MODEL_ORG_SWITCH_DURING_PULL_PASS
MODEL_CLEAR_FAILURE_NO_NEW_ORG_COMMIT_PASS
MODEL_BOUNDED_CONTINUATION_PASS
MODEL_RECOVERY_DEFERRED_313_PASS
```

---

# 86. syncNow during worker test

Scenario:

```text
worker A starts generation 10
manual syncNow commits generation 11 while A is running
```

PASS:

```text
generation 11 persisted
no sync_skipped loss
A continues or successor drains 11
drainedGeneration eventually >= 11 in model
```

---

# 87. Realtime during worker test

Scenario:

```text
active drain
realtime event arrives
```

PASS:

```text
normal v311 generation increments/coalesces safely
no event-derived intent is lost solely because syncInProgress=true
```

Targeted semantics ليست مطلوبة قبل 312.

---

# 88. Final-exit race test

Scenario controlled interleaving:

```text
A sees idle at generation N
B commits generation N+1
B wakes immediate work while A still RUNNING
A exits
```

PASS:

```text
N+1 remains runnable/drainable
```

أي lost successor = FAIL.

---

# 89. Process-kill test

Scenario:

```text
request generation committed
kill before enqueue
restart / periodic wake
```

PASS إذا pending generation مكتشفة ولا تضيع.

---

# 90. 429 test

PASS:

```text
no terminal reject
no busy loop
retry-after respected when available
identity unchanged
```

---

# 91. Timeout test

PASS:

```text
transient classification
same mutation/business identity on retry
no duplicate semantic effect under 309/310 model
```

---

# 92. Auth test

PASS:

```text
401/403 != generic network retry loop
401/403 != permanent business mutation REJECTED solely because transport auth failed
worker stops safely
intent recoverable after valid session
```

---

# 93. Permanent validation test

PASS:

```text
no WorkManager retry storm
fail closed classification
no generation infinite loop caused by terminal invalid row
```

---

# 94. Conflict test

PASS:

```text
conflict stored/reconciled by data plane
worker does not retry as network
pending generation can reach idle if no eligible network work remains
```

---

# 95. Stale WorkManager input test

Inputs:

```text
wrong org
wrong user
old session epoch
```

كلها:

```text
no network mutation
no Room domain apply
no new tenant state touch
Result.success/no-op stale outcome
```

---

# 96. clear failure test

فرض `clearLocalData()` failure.

PASS:

```text
new lastOrgId not persisted
new session not marked active
new worker not scheduled as committed tenant
transition surfaces failure
```

---

# 97. Delayed retry test

ضع outbox:

```text
state=RETRY
next_attempt_at=now+1h
```

PASS:

```text
worker does not spin
no immediate semantic resend
future wake remains possible
```

---

# 98. Bounded backlog test

اجعل push/pull أكبر من invocation budget.

PASS:

```text
continuation scheduled
current worker does not report network failure
requested generation not incorrectly marked drained before completion
```

---

# 99. v310 regression gate

يجب إعادة:

```text
499/499 v310 fixtures
```

بدون failure.

ويجب أن تبقى:

```text
duplicateFinancialEffectViolationCount = 0
duplicateInventoryEffectViolationCount = 0
duplicateOptimalEffectViolationCount   = 0
strongerIdentityRetryMutationCount     = 0
strongerReceiptAtomicityViolationCount = 0
strongerChangeAppendViolationCount     = 0
owner310LwwFallbackCount               = 0
remoteApplyEnqueueCount                = 0
```

---

# 100. v309 regression gate

يجب إعادة:

```text
272/272 v309 fixtures
```

بدون failure.

Idempotency/receipt/conflict semantics لا تتغير.

---

# 101. v308 regression gate

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

# 102. Build/Test runtime truth

إذا Gradle/SDK غير متاحة أو التنفيذ مصرح له static-only:

```text
compileStatus  = NOT_RUN_ENVIRONMENT_UNAVAILABLE
```

أو:

```text
compileStatus  = NOT_RUN_USER_AUTHORIZED_STATIC_ONLY
```

ونفس الشيء للاختبارات.

لا يعتبر failure.

لكن إذا شغلت command فعلية وفشلت:

```text
BLOCKED_RUNTIME_FAILURE
```

ولا يجوز تغيير التقرير إلى PASS فقط.

---

# 103. PostgreSQL runtime

311 لا تحتاج SQL migration جديدة ولا PostgreSQL execution.

التقرير الصحيح:

```text
postgresRequired = false
postgresExecuted = false
serverSqlChanged = false
```

ولا تستخدم `POSTGRES_NOT_EXECUTED` كblocker لهذه الجلسة.

---

# 104. New waivers

الحاكم:

```text
new311WaiverCount = 0
```

أي waiver جديدة تمنع PASS.

---

# 105. Required failure codes

يجب أن تكون الأخطاء المهمة قابلة للتصنيف، على الأقل:

```text
BLOCKED_INPUT_DRIFT
BLOCKED_SCHEMA_SCOPE_DRIFT
FAIL_LOST_SYNC_INTENT
FAIL_IMMEDIATE_KEEP_RACE
FAIL_REQUEST_BEFORE_PERSIST
FAIL_GENERATION_REGRESSION
FAIL_DRAINED_AHEAD_OF_REQUESTED
FAIL_IDLE_CAS_RACE
FAIL_SYNC_SKIPPED_AS_TRANSIENT
FAIL_AUTH_AS_NETWORK_RETRY
FAIL_AUTH_MUTATION_PERMANENT_REJECT
FAIL_CONFLICT_AS_NETWORK_RETRY
FAIL_PERMANENT_AS_RETRY
FAIL_DELAYED_RETRY_BUSY_LOOP
FAIL_STALE_SCOPE_EXECUTION
FAIL_STALE_SESSION_EPOCH_EXECUTION
FAIL_TENANT_SWITCH_ORDER
FAIL_NEW_ORG_COMMITTED_AFTER_CLEAR_FAILURE
FAIL_OLD_WORKER_CROSS_TENANT_WRITE
FAIL_OLD_REALTIME_CROSS_TENANT_REQUEST
FAIL_CURSOR_AUTHORITY_BYPASS
FAIL_BOOTSTRAP_SCOPE_DRIFT
FAIL_310_REGRESSION
FAIL_309_REGRESSION
FAIL_308_REGRESSION
FAIL_RUNTIME_CUTOVER_EARLY
FAIL_HISTORICAL_MIGRATION_DRIFT
BLOCKED_RUNTIME_FAILURE
```

---

# 106. Acceptance counters — must equal zero

```text
lostSyncIntentCount
immediateKeepCorrectnessCount
requestWakeBeforePersistCount
generationRegressionCount
drainedAheadOfRequestedCount
idleCasViolationCount
finalExitRaceLossCount
syncSkippedAsTransientCount
authAsNetworkRetryCount
authMutationPermanentRejectCount
conflictAsNetworkRetryCount
permanentAsRetryCount
delayedRetryBusyLoopCount
staleScopeExecutionCount
staleSessionEpochExecutionCount
earlyNewOrgCommitCount
oldWorkerCrossTenantWriteCount
oldRealtimeCrossTenantRequestCount
directV2UiDataPlaneBypassCount
cursorAuthorityBypassCount
bootstrapImplementedIn311Count
new311WaiverCount
v310RegressionFailures
v309RegressionFailures
v308RegressionFailures
historicalMigrationChangedCount
roomSchemaChangedCount
serverSqlChangedCount
runtimeV2EnabledCount
```

---

# 107. Acceptance counters — exact/positive values

```text
inputArchiveEntries             = 1765
inputProductionKotlinCount      = 1178
inheritedExceptionCount         = 9
roomVersionBefore/After         = 80/80
workManagerVersion              = 2.10.0
owner310AggregateCount          = 17
v311StaticFixtureCount          >= 300
v310RegressionFixtureCount      = 499
v309RegressionFixtureCount      = 272
v308RegressionFixtureCount      = 137
requestSourceCoverageRows       > 0
retryMatrixRows                 > 0
tenantTransitionCoverageRows    >= 9
```

---

# 108. v311 verification JSON

إنشاء:

```text
VERTO_SYNC_ORCHESTRATION_VERIFICATION_v311.json
```

يحتوي على الأقل:

```text
session
inputZipName
inputZipSha256
inputArchiveEntries
inputProductionKotlinCount
planSha256
session310ContractSha256
v310VerificationSha256
v310FinalVerdict
v310Handoff311Authorized
inheritedExceptionCount
new311WaiverCount
roomVersionBefore
roomVersionAfter
schema80Sha256
workManagerVersion
immediateWorkPolicyBefore
immediateWorkPolicyAfter
periodicWorkPolicy
requestedGenerationAuthority
drainedGenerationAuthority
sessionEpochAuthority
requestSourceCoverageRows
retryMatrixRows
tenantTransitionCoverageRows
lostSyncIntentCount
immediateKeepCorrectnessCount
requestWakeBeforePersistCount
generationRegressionCount
drainedAheadOfRequestedCount
idleCasViolationCount
finalExitRaceLossCount
syncSkippedAsTransientCount
authAsNetworkRetryCount
authMutationPermanentRejectCount
conflictAsNetworkRetryCount
permanentAsRetryCount
delayedRetryBusyLoopCount
staleScopeExecutionCount
staleSessionEpochExecutionCount
earlyNewOrgCommitCount
oldWorkerCrossTenantWriteCount
oldRealtimeCrossTenantRequestCount
directV2UiDataPlaneBypassCount
cursorAuthorityBypassCount
bootstrapImplementedIn311Count
serverSqlChangedCount
historicalMigrationChangedCount
v310RegressionFailures
v309RegressionFailures
v308RegressionFailures
v311FixtureStats
runtimeV2
compileStatus
unitTestStatus
postgresRequired
postgresExecuted
finalVerdict
blockers
handoff312Authorized
```

---

# 109. v311 verification Markdown

إنشاء:

```text
VERTO_SYNC_ORCHESTRATION_VERIFICATION_v311.md
```

Human-readable ومطابق للـJSON.

---

# 110. Expected PASS verdict

إذا نجحت كل static gates:

```text
PASS_STATIC_DURABLE_DRAIN_RETRY_TENANT_ORCHESTRATION
/ INHERITED_307_EXCEPTIONS=9
/ ROOM_80_UNCHANGED
/ SERVER_SQL_UNCHANGED
/ RUNTIME_V2_DISABLED
/ BUILD_NOT_VERIFIED
```

إذا runtime/build نفذ فعليًا ونجح، يعدل suffix بصدق فقط.

---

# 111. معنى PASS في 311

PASS يعني حصرًا:

> أصبحت نية المزامنة durable ومقيدة بالtenant/session، ولا تضيع بسبب `KEEP` أو mutex أو worker قائم؛ وأصبح worker يثبت idle قبل الإغلاق، مع retry taxonomy صحيحة وtenant switching fail-closed، بينما بقي V2 production runtime معطلًا.

ولا يعني:

```text
Realtime targeted hints complete
bootstrap/full resync complete
V2 default-on
legacy sync removed
multi-device instrumentation executed
APK compiled unless actually verified
server migrations applied
```

---

# 112. Report honesty

ممنوع كتابة:

```text
"WorkManager guarantees exactly-once execution"
"no sync request can ever be delayed"
"all retries are automatic"
"tenant switching is runtime-proven"
"V2 is production active"
```

بدون runtime evidence.

الصحيح:

```text
Durable intent + idempotent data plane makes worker wakeups replay-safe;
WorkManager execution itself remains at-least-once/best-effort scheduled.
```

---

# 113. Required implementation order

ينفذ بالترتيب:

```text
A. verify v310 ZIP SHA/entry count/Kotlin count
B. verify SESSION_310 SHA
C. verify v310 verification SHA + handoff311Authorized=true
D. freeze baseline manifest/hashes
E. carry forward exactly 9 inherited exceptions
F. inventory every sync request source/call site
G. inventory WorkManager unique names/policies/tags
H. inventory current retry classifications
I. inventory tenant/session transition paths
J. freeze Room 80 + server SQL
K. design requested/drained generation on sync_sequence_state
L. design sessionEpoch stale-job guard
M. implement DAO generation transactions
N. implement request-before-wake API
O. replace immediate KEEP correctness path with APPEND_OR_REPLACE/equivalent
P. implement bounded drain loop + idle CAS
Q. distinguish continuation vs network retry
R. prevent delayed retry busy loop
S. remove sync_skipped from transient network taxonomy
T. preserve auth failures as recoverable transport/session state, not business rejection
U. wire manual/foreground/startup/periodic request sources
V. route current realtime trigger to durable normal request only
W. harden tenant switching ordering
X. ensure old worker/realtime stale guards
Y. keep V2 feature flag OFF
Z. emit v311 coverage artifacts
AA. run >=300 v311 fixtures
AB. rerun 499 v310 fixtures
AC. rerun 272 v309 fixtures
AD. rerun 137 v308 fixtures + MODEL_10K_PASS
AE. run verifier twice deterministically
AF. emit JSON/MD reports
AG. verify Room 80 unchanged
AH. verify server migrations unchanged
AI. verify Runtime V2 OFF
AJ. package v311 ZIP + SHA
```

---

# 114. Required file-scope discipline

مسموح مبدئيًا تعديل/إضافة:

```text
data/sync/.../SyncWorker.kt
data/sync/.../SyncManager.kt
data/sync/.../SyncReliability.kt
data/sync/.../SyncWorkScope.kt
data/sync/.../RealtimeManager.kt            # minimal request-routing change only

data/sync/.../*Drain* / *Orchestration* files

data/database/.../dao/UnifiedSyncDao.kt     # queries/transactions only; no schema change

data/preferences/.../SyncPreferencesStore.kt

data/preferences/.../PreferencesManager.kt # sessionEpoch only if needed

app/.../DefaultAuthSessionCoordinator.kt
app/.../OptimalSyncBridge.kt
app/.../DI modules needed for orchestration

compatibility wrappers/call sites necessary to route manual/foreground requests

docs/sync/*v311*
tools/*v311*
scripts/*v311*
verification/report artifacts
```

---

# 115. Forbidden file-scope drift

ممنوع دون `BLOCKED_SCOPE_DRIFT`:

```text
UI redesign
business feature refactors
financial business-rule changes
inventory costing changes
Optimal domain changes
server migration creation/modification
Room schema migration
Realtime targeted payload redesign
bootstrap/full resync implementation
legacy deletion
Gradle dependency upgrades
FeatureFlag default-on
```

---

# 116. Protected 310 data-plane files

يجب ألا تعدل إلا إذا verifier يثبت ضرورة orchestration-facing outcome فقط وبلا semantic change:

```text
UnifiedStrongerSyncBridge.kt
UnifiedStrongerSourceFactory.kt
UnifiedStrongerSyncChangeApplier.kt
UnifiedSyncPushRegistry.kt
UnifiedSyncPullRegistry.kt
financial/inventory/optimal business writers
```

إذا تعديل data plane semantic required:

```text
BLOCKED_310_CONTRACT_DRIFT
```

إلا إذا كان إصلاح retry transport classification المملوك صراحة لـ311 ومثبتًا regression-safe.

---

# 117. PushEngine change boundary

يجوز لـ311 تعديل `UnifiedSyncPushEngine` فقط في حدود:

- expose eligible-now/next-retry orchestration info.
- preserve auth failure without permanent mutation rejection.
- expose failure category cleanly.
- لا تغيير idempotency/receipt/conflict identity semantics.

أي تغيير business effect mapping = scope drift.

---

# 118. PullEngine change boundary

يفضل ألا يتغير `UnifiedSyncPullEngine`.

إذا احتاج orchestrator outcome metadata إضافية، يضاف بدون تغيير:

```text
cursor authority
atomic page apply
REMOTE_APPLY behavior
scope validation
```

---

# 119. Tenant safety hard gate

كل drain pass:

- يثبت organization الحالي من auth/session authority.
- يثبت user الحالي.
- يثبت sessionEpoch.
- لا يثق WorkManager input وحده.
- لا يطبق old response على new tenant.

---

# 120. No silent partial PASS

PASS ممنوع إذا تحقق أي واحد:

```text
request generation غير durable
immediate KEEP ما زال correctness path
request يمكن أن يصل بعد idle check ويضيع
realtime أثناء worker ما زال drop بلا pending intent
sync_skipped ما زال transient network
401/403 يمكن أن يرفض mutation business نهائيًا بسبب transport auth
conflict يسبب WorkManager network retry
future next_attempt_at يسبب spin
old org worker يكتب بعد switch
clear failure ثم lastOrgId=new
stale session epoch ينفذ network work
Room != 80
server SQL changed
one new waiver
one 310 regression
one 309 regression
one 308 regression
Runtime V2 ON
```

---

# 121. Packaging

المخرج المتوقع بعد تنفيذ 311:

```text
Verto-v311-source-of-truth.zip
Verto-v311-source-of-truth.zip.sha256
```

لا build caches/secrets/extracted duplicate input.

---

# 122. Mandatory artifacts inside v311 archive

```text
VERTO_SYNC_ORCHESTRATION_VERIFICATION_v311.json
VERTO_SYNC_ORCHESTRATION_VERIFICATION_v311.md
docs/sync/VERTO_SYNC_REQUEST_SOURCES_v311.csv
docs/sync/VERTO_SYNC_RETRY_MATRIX_v311.csv
docs/sync/VERTO_SYNC_TENANT_TRANSITIONS_v311.csv
docs/sync/VERTO_SYNC_DRAIN_STATE_v311.md
tools/verify_sync_orchestration_v311.py
tools/test_sync_orchestration_verification_v311.py
scripts/verify-v311-sync-orchestration.sh
Verto-v311-report.md
```

الأسماء المكافئة مسموحة فقط إذا التقرير يربطها صراحة.

---

# 123. Output archive integrity

بعد packaging:

1. compute SHA-256.
2. count entries.
3. test ZIP open.
4. verify mandatory v311 artifacts.
5. verify schema80 unchanged.
6. verify AppDatabase/MigrationCatalog unchanged.
7. verify v305/v309/v310 server migrations unchanged.
8. verify Room remains 80.
9. verify WorkManager immediate correctness policy no longer KEEP-only.
10. verify Runtime V2 OFF.
11. verify no secrets/cache artifacts.
12. write SHA sidecar.

---

# 124. Final acceptance questions

قبل PASS يجب الإجابة **نعم**:

1. هل input SHA = `7b9a7070...`؟
2. هل archive entries = `1765`؟
3. هل production Kotlin baseline = `1178`؟
4. هل SESSION_310 SHA = `6b26c6de...`؟
5. هل v310 verification SHA = `0872e3f8...`؟
6. هل v310 `handoff311Authorized=true`؟
7. هل v310 blockers فارغة؟
8. هل inherited exceptions exactly 9؟
9. هل `new311WaiverCount=0`؟
10. هل Room بقي 80؟
11. هل schema80 unchanged؟
12. هل AppDatabase/MigrationCatalog unchanged؟
13. هل server v305/v309/v310 migrations unchanged؟
14. هل serverSqlChangedCount=0؟
15. هل requested generation durable؟
16. هل drained generation durable؟
17. هل requested >= drained دائمًا؟
18. هل request يثبت قبل WorkManager wake؟
19. هل enqueue failure لا يمسح pending generation؟
20. هل immediate `KEEP` لم يعد correctness authority؟
21. هل successor-safe wake مثبت؟
22. هل final-exit race لا يفقد intent؟
23. هل worker يخرج فقط بعد durable idle proof؟
24. هل continuation منفصلة عن network retry؟
25. هل delayed RETRY لا يسبب busy loop؟
26. هل `sync_skipped` لم يعد transient network؟
27. هل 429 retryable ومحترم؟
28. هل timeout/5xx transient bounded؟
29. هل auth ليس network retry loop؟
30. هل auth transport failure لا يرفض mutation business نهائيًا؟
31. هل validation/permanent fail closed؟
32. هل conflict ليس network retry؟
33. هل cancellation لا تغير business terminal state؟
34. هل process death يحفظ pending intent؟
35. هل unknown commit يعاد بنفس identity؟
36. هل manual أثناء worker لا يضيع؟
37. هل realtime أثناء worker لا يضيع؟
38. هل startup/periodic scopes tenant-safe؟
39. هل WorkManager input يشمل session epoch أو equivalent stale-session proof؟
40. هل wrong org stale job no-op؟
41. هل wrong user stale job no-op؟
42. هل old epoch stale job no-op؟
43. هل logout أثناء push آمن؟
44. هل org switch أثناء pull لا يطبق old response؟
45. هل clear failure يمنع `lastOrgId=new`؟
46. هل old worker cancellation موجود مع stale guard؟
47. هل old realtime stop قبل new active state؟
48. هل same-org reauth stale worker مرفوض؟
49. هل direct V2 UI data-plane bypass = 0؟
50. هل cursor يظل ملك PullEngine فقط؟
51. هل bootstrap لم ينفذ في 311؟
52. هل owner310 stronger identities لم تتغير؟
53. هل no duplicate generic durable intent؟
54. هل >=300 v311 fixtures PASS؟
55. هل 499/499 v310 regression PASS؟
56. هل 272/272 v309 regression PASS؟
57. هل 137/137 v308 regression PASS؟
58. هل MODEL_10K_PASS؟
59. هل verifier deterministic مرتين؟
60. هل Runtime V2 بقي OFF؟
61. هل التقرير لا يدعي runtime لم يُشغل؟

أي `لا` تمنع PASS.

---

# 125. Handoff إلى 312

فقط عند نجاح كل gates الساكنة:

```text
handoff312Authorized = true
```

312 يجوز أن تفترض:

```text
normal sync request is durable
a request during active drain is not lost
successor-safe WorkManager wake exists
tenant/session scope is validated
worker idle handshake is race-safe
retry taxonomy is available
Runtime V2 still OFF
```

ولا يجوز أن تفترض targeted Realtime موجودًا مسبقًا.

---

# 126. Handoff إلى 313

311 لا تمنح 313 completion authority مباشرة.

هي فقط تضمن أن حالات:

```text
BOOTSTRAP_REQUIRED
RECOVERY_REQUIRED
```

تصل fail-closed دون timestamp fallback.

313 تبدأ بعد 312 حسب التسلسل الأم.

---

# 127. الخلاصة النهائية للعقد

311 يجب أن تنهي المرحلة بهذه الحقيقة:

```text
Sync intent is durable before WorkManager is asked to wake.
Immediate KEEP can no longer discard correctness-critical intent.
Active-worker requests become a newer durable generation.
Worker exits only after a race-safe idle proof.
Retries preserve their real category.
Auth/conflict/permanent failures are not misreported as network retries.
Tenant/session changes invalidate stale workers before local apply.
Room remains 80.
Server SQL remains unchanged.
V2 remains OFF until 312/313/314 complete their responsibilities.
```

إذا تعذر إثبات أي race أو tenant invariant، التنفيذ يتوقف fail-closed بدل إعلان PASS جزئي.
