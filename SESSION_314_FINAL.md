# SESSION_314_FINAL.md

## Verto Sync Modernization — Session 314

### Multi-Device Fault Injection, Staged Rollout, Kill Switches, Cutover & Safe Legacy Retirement

**نوع المستند:** عقد تنفيذ مستقل ونهائي للجلسة الأخيرة من خطة v304→v314  
**الجلسة:** 314  
**الحالة:** `PLAN ONLY — EXECUTABLE ON V313 STATIC-RECOVERY BASELINE; FINAL CUTOVER REQUIRES RUNTIME EVIDENCE`  
**تاريخ الصياغة:** 2026-08-21  
**مصدر الكود المفحوص:** `Verto-v313-source-of-truth.zip`  
**SHA-256 للمصدر المفحوص:** `a853781617dfb24b4ffb013afce994a9863825a4a4509582acaea17eefafe21e`  
**Archive entries:** `1820`  
**Production Kotlin files:** `1191`  
**عقد 313 المرجعي:** `SESSION_313_FINAL.md`  
**SHA-256 لعقد 313:** `7a420fab0082b3fce37340727ae010b457b485567bbd7a46541c8fc6b05a7bd9`  
**الخطة الأم:** `VERTO_SYNC_MODERNIZATION_PLAN_v304-v314.md`  
**SHA-256 للخطة:** `a767087c5f1659dcd7c660c189542ef2d7774c0f647ff9321e9f53219a7dfd97`  
**Contract authority:** `verto-unified-sync / 1`  
**Room الحالي:** `81`  
**Room المستهدف في 314:** `81` — لا Migration جديدة مطلوبة أو مسموحة في المسار الطبيعي لـ314.  
**Aggregate registry:** `34`  
**Direct/session307-308 aggregates:** `17`  
**Stronger/session310 aggregates:** `17`  
**Supabase Kotlin BOM:** `3.0.2`  
**WorkManager:** `2.10.0`  
**Runtime V2 عند البداية:** `OFF`  
**Realtime runtime عند البداية:** `OFF`  
**v313 server migration:** `DEFINED / NOT EXECUTED`  
**v313 bootstrap runtime:** `NOT VERIFIED`  
**v313 static fixtures:** `494/494 PASS`  
**v312 regression:** `300/300 PASS`  
**v311 regression:** `325/325 PASS`  
**v310 regression:** `499/499 PASS`  
**v309 regression:** `272/272 PASS`  
**v308 regression:** `137/137 PASS + MODEL_10K_PASS`  

---

# 0. الحكم التنفيذي المختصر

314 ليست جلسة “تشغيل Flag وانتهى”.

هي جلسة إثبات أن المسار الجديد يستطيع النجاة من:

```text
network faults
clock skew
process death
retries after uncertain commit
reordered/duplicate delivery
multi-device concurrent mutation
tenant/session transitions
Realtime loss
cursor/recovery transitions
```

ثم فقط بعد إثبات runtime مناسب:

```text
Shadow → Controlled V2 ownership → Financial/Inventory → Realtime → Default-on
```

ثم فقط بعد نافذة مراقبة مثبتة وعدم وجود clients قديمة تعتمد Legacy:

```text
Disable Legacy → prove zero use → retire Legacy transport
```

القواعد الحاكمة:

```text
No runtime proof = no final cutover.
```

```text
No safe rollback proof = no write ownership expansion.
```

```text
Realtime may improve latency; disabling it must not change eventual convergence.
```

```text
Legacy retirement is an evidence-gated consequence of rollout success,
not a prerequisite for declaring static implementation complete.
```

---

# 1. حقيقة baseline من v313

`VERTO_SYNC_RECOVERY_VERIFICATION_v313.json` في المصدر الحالي يثبت:

```text
staticGatesPassed                     = true
finalVerdict                          = BLOCKED_RUNTIME_FAILURE / STATIC_RECOVERY_GATES_PASS
handoff314Authorized                  = false
blocker                               = Gradle 8.9 distribution unavailable before compilation
compileStatus                         = BLOCKED_RUNTIME_FAILURE_GRADLE_8_9_DISTRIBUTION_UNAVAILABLE
unitTestStatus                        = NOT_RUN_ANDROID_RUNTIME_UNAVAILABLE
postgresExecuted                      = false
serverMigrationDefined313             = true
serverMigrationApplied313             = false
bootstrapRuntimeVerified              = false
Room                                  = 81
aggregateRegistryCount                = 34
owner310AggregateCount                = 17
bootstrapCoverageRows                 = 34
bootstrapSnapshotCoverageGapCount     = 0
existingDataBackfillGapCount          = 0
new313WaiverCount                     = 0
runtimeV2                             = DISABLED
realtimeRuntime                       = DISABLED
```

SHA-256 لـv313 verification JSON:

```text
2cd5814dfcd7cc3680fa9b435559c7eaef830b27c308c2a236b11ab9c8cc4529
```

---

# 2. كيفية التعامل مع handoff314Authorized=false

313 لم تفشل ساكنًا؛ فشلت محاولة runtime بسبب البيئة قبل الترجمة.

لذلك 314 لها مستويان رسميان:

```text
314-S = static/pre-cutover implementation and model verification
314-R = real runtime/staging proof and final cutover authority
```

يجوز تنفيذ `314-S` على baseline الحالية بشرط أن يكون blocker الموروث **هو نفسه فقط** ولا توجد static regressions.

لا يجوز لـ`314-S`:

```text
default-on V2
turn off legacy fallback permanently
claim PostgreSQL v313 applied
claim Room 80→81 runtime verified
claim two-device convergence
claim final plan completion
```

`314-R` لا تبدأ cutover حتى يزول blocker ويكتمل runtime evidence.

هذا ليس waiver جديدة؛ هو فصل صريح بين static readiness وruntime rollout authority.

---

# 3. بوابة البداية

قبل أي تعديل يجب التحقق:

```text
input ZIP SHA                       = a8537816...
archive entries                     = 1820
production Kotlin                   = 1191
SESSION_313 SHA                     = 7a420fab...
plan SHA                            = a767087c...
v313 verification SHA              = 2cd5814d...
v313 staticGatesPassed              = true
v313 new313WaiverCount              = 0
Room                                = 81
aggregate registry                  = 34
owner310                            = 17
V2 default                          = OFF
Realtime default                    = OFF
```

إذا تغير أحدها دون توثيق:

```text
BLOCKED_INPUT_DRIFT
```

إذا ظهرت static failure جديدة داخل v313 verification:

```text
BLOCKED_313_STATIC_BASELINE
```

---

# 4. الاستثناءات الموروثة

تبقى استثناءات 307 الموروثة **9 فقط** كما حملتها 308→313.

قواعد 314:

1. تحمل في artifact مستقل.
2. لا يعاد تفسيرها كـPASS نظيف.
3. `new314WaiverCount = 0` شرط كل PASS.
4. أي استثناء يمس cutover يجب أن يكون `worsened=false`.
5. لا تستخدم لتبرير Legacy delete غير آمن أو tenant leak أو duplicate financial effect.

---

# 5. ترتيب السلطات

عند التعارض:

1. `Verto-v313-source-of-truth.zip` ذو SHA المثبت.
2. `VERTO_SYNC_RECOVERY_VERIFICATION_v313.json/.md`.
3. `SESSION_313_FINAL.md`.
4. `VERTO_SYNC_REALTIME_VERIFICATION_v312.json/.md`.
5. `VERTO_SYNC_ORCHESTRATION_VERIFICATION_v311.json/.md`.
6. `VERTO_SYNC_STRONGER_VERIFICATION_v310.json/.md`.
7. `VERTO_SYNC_PUSH_VERIFICATION_v309.json/.md`.
8. `VERTO_SYNC_PULL_VERIFICATION_v308.json/.md`.
9. `docs/sync/VERTO_UNIFIED_SYNC_CONTRACT_v304.json/.md`.
10. `UnifiedSyncContract.kt` + `UnifiedSyncAggregateRegistry.kt`.
11. v313 bootstrap/recovery artifacts.
12. هذا العقد.
13. الخطة الأم v304→v314.

---

# 6. بصمات authority عند البداية

يجب تثبيت البصمات التالية قبل التعديل:

```text
Verto-v313-source-of-truth.zip
  a853781617dfb24b4ffb013afce994a9863825a4a4509582acaea17eefafe21e

SESSION_313_FINAL.md
  7a420fab0082b3fce37340727ae010b457b485567bbd7a46541c8fc6b05a7bd9

VERTO_SYNC_RECOVERY_VERIFICATION_v313.json
  2cd5814dfcd7cc3680fa9b435559c7eaef830b27c308c2a236b11ab9c8cc4529

VERTO_SYNC_RECOVERY_VERIFICATION_v313.md
  2713af2fcb730afb7a2c469bed67c82cd01d7c37e6b2b701e9d4cb5538b4d5fe

core/common/.../FeatureFlags.kt
  ad159d5e9f91e406127a5220c4e6729a51f659577ea4e673f9cce98f0de57203

data/sync/.../SyncManager.kt
  44105766579f6b95adc8b224a187a22d100ade98d4c54f539ac6eabe36b0a3c9

data/sync/.../SyncWorker.kt
  a4508c7532f7119da1659f11236f9b801b0e810e979eddf1c27dd481e69f09e9

data/sync/.../SyncReliability.kt
  6c39618bcb3184086b0f9bf12a1fed693e9483542c748ad62029c69bf0c319ad

data/sync/.../RealtimeManager.kt
  94e249cd1ac4af90e8d73eba81b66ddd6b0b05e70e1d885fedded730ecefbf90

data/sync/.../pull/UnifiedSyncPullEngine.kt
  d76076b476021afd4b53d4db06575e7cc98a9fc58619d4f6281c11eb52b4b14c

data/sync/.../push/UnifiedSyncPushEngine.kt
  d666e85adcdb57b4f8459670c2070afad8b846b770cbfd586990e076a08718e6

data/sync/.../recovery/UnifiedSyncRecoveryEngine.kt
  6548d9f171f66a8b26050af384ad746552e717e3c95bce579aa843f5192d8763

data/sync/.../recovery/UnifiedSyncRecoveryRegistry.kt
  a065c3f5b99ff0399daa2630fd9509152216a013a91f21229c77acb919c90810

data/sync/.../recovery/UnifiedSyncReconciliationEngine.kt
  2dd1941bf0ad488bda75597ed94b4b2ef09d9647416250e2588bbf4b0b0e2caf

data/sync/.../recovery/SyncHealthSnapshot.kt
  2348c897011a782f2dff6ace0d05f3ccfd4d8536f498babb4bb3da2fff368c4e

data/network/.../UnifiedSyncContract.kt
  9553e1801dcf756f619ea2c28fd1bf8534f18fa32e4f74c2743cedc5460a7e6c

data/network/.../UnifiedSyncAggregateRegistry.kt
  9e35f020993e3caa21171ab6a311605f2bcfa58a910bf7814f22e157b5af7fe9

data/database/.../AppDatabase.kt
  0ded17d4e4edc88bb42e4dc3625c9d32e5c6e1cf9ab0a44422c52d5e1386a6f4

data/database/.../MigrationCatalog.kt
  32f91a3e0531d10e0d9461503f499df20297d6d2fab01446ddc849630297b8ae

app/schemas/.../AppDatabase/81.json
  67dbae5091531378e8b4806b1ed7a9206c751b7080169835a349dddd4d9499db

supabase/migrations/20260821183000_v313_recovery_bootstrap_snapshot.sql
  442e91b24edc287a0b1b79df3f99a01d5eecb158fbef5b3043c4de5470ad0444
```

Historical SQL v305/v309/v310/v312/v313 لا يعدل في 314.

---

# 7. الحالة الحالية المثبتة من فحص v313

الكود الحالي يثبت:

```text
Room                                      = 81
V2 master flag                            = isVersionedSyncEnabled=false
Realtime flag                             = isRealtimeSyncEnabled=false
Independent V2 pull kill switch           = absent
Independent V2 push kill switch           = absent
Independent financial kill switch         = absent for sync rollout
Independent inventory kill switch         = absent for sync rollout
Independent legacy fallback kill switch   = absent
Rollout wave authority                    = absent
Shadow pull comparator                    = absent
Multi-device runtime evidence             = absent
Fault-injection runtime evidence           = absent
PostgreSQL v313 application               = absent
Room 80→81 runtime proof                  = absent
Legacy timestamp pull code                = present
Legacy DataStore deletion snapshot        = present
Legacy fullSync fallback                  = present
Realtime                                  = hint-only
Recovery                                  = static/model verified
Bootstrap aggregate coverage              = 34/34 static
owner310 coverage                         = 17/17 static
```

---

# 8. Legacy baseline inventory المثبت

فحص source الحالي يثبت وجود:

```text
KEY_LAST_PULLED_* occurrences  = 86 across 9 Kotlin files
getLastPulledAt occurrences    = 14 across 8 Kotlin files
setLastPulledAt occurrences    = 25 across 9 Kotlin files
legacy pull markers in SyncRuntime = 13
captureDeletionSnapshot usages = 3 across 2 files
fullSync( occurrences           = 20 across 14 files
```

هذه الأرقام baseline discovery وليست وحدها acceptance target؛ verifier يجب أن يميز production runtime authority من أسماء APIs/tests المشروعة.

---

# 9. Legacy pull files المثبتة

الـtimestamp-based pull authority موجود على الأقل في:

```text
data/network/.../SyncInventory.kt
data/network/.../SyncInvoiceHeaders.kt
data/network/.../SyncCash.kt
data/network/.../SyncMisc.kt
data/network/.../SyncClients.kt
data/network/.../SyncInvoicePayments.kt
data/network/.../SyncRuntime.kt
data/preferences/.../PreferenceKeys.kt
data/preferences/.../SyncPreferencesStore.kt
```

لا يجوز حذفها قبل نجاح gates المحددة لاحقًا.

---

# 10. هدف 314 الدقيق

314 تنفذ فقط:

1. Fault-injection harness deterministic قابل للتكرار.
2. Multi-device convergence harness.
3. ستة kill switches مستقلة على الأقل.
4. Rollout policy/state machine للـWave 0→6.
5. Shadow pull comparison لا يغير live Room/cursor/outbox.
6. Ownership matrix تمنع تشغيل Legacy وV2 ككاتبين لنفس aggregate.
7. Rollback semantics آمنة قبل/بعد write cutover.
8. Runtime evidence schema منفصل عن model evidence.
9. Network fault matrix.
10. Device-clock skew matrix.
11. Process-death matrix.
12. Data ordering/duplicate/pagination matrix.
13. Multi-device conflict matrix.
14. Tenant/auth/RLS adversarial matrix.
15. Realtime-off parity gate.
16. Staged rollout Wave 0→6.
17. Default-on فقط بعد runtime gates.
18. Legacy disable فقط بعد default-on observation gate.
19. Legacy retirement فقط بعد zero-use + old-client gate.
20. إزالة timestamp cursor runtime authority عند final retirement.
21. إزالة DataStore deletion queue runtime authority عند final retirement.
22. إزالة generic dirty/clean push paths التي استبدلت، دون مسح durable stronger outboxes.
23. إزالة direct legacy fullSync production callers عند final retirement.
24. V2 contract version يبقى 1.
25. aggregate registry يبقى 34.
26. Room يبقى 81.
27. لا new server migration في المسار الطبيعي.
28. 313→308 regressions تبقى صفر.
29. no new waivers.
30. تقارير صادقة تفصل STATIC_PRECUTOVER عن FINAL_RUNTIME_PASS.

---

# 11. ما ليست عليه 314

```text
CRDT rewrite                         → ممنوع
Event Sourcing rewrite               → ممنوع
business-rule redesign               → ممنوع
financial semantics redesign         → ممنوع
inventory costing redesign           → ممنوع
new aggregate ids                    → ممنوع
contract v2                          → ممنوع
Room 82                              → ممنوع افتراضيًا
new PostgreSQL feature migration     → ممنوع افتراضيًا
Realtime as correctness authority    → ممنوع
Legacy deletion before evidence      → ممنوع
blind rollback from V2 writes to legacy dirty push → ممنوع
```

---

# 12. نتيجتان مختلفتان رسميًا

## 12.1 Static/pre-cutover verdict

إذا نفذت كل static/model gates لكن runtime غير متاح:

```text
PASS_STATIC_314_PRECUTOVER
/ FINAL_CUTOVER_BLOCKED_RUNTIME
/ ROOM_81_UNCHANGED
/ V2_DEFAULT_OFF
/ REALTIME_DEFAULT_OFF
/ LEGACY_PRESERVED
/ V313_SQL_NOT_EXECUTED
```

هذا verdict يسمح بالتغليف لكنه **لا** يغلق الخطة v304→v314.

## 12.2 Final runtime verdict

لا يستخدم إلا بعد كل runtime/rollout/legacy gates:

```text
PASS_FINAL_SYNC_V2_ROLLOUT
/ MULTI_DEVICE_CONVERGED
/ REALTIME_OPTIONALITY_PROVEN
/ ROOM_81_RUNTIME_VERIFIED
/ V313_SQL_APPLIED_STAGING
/ V2_DEFAULT_ON
/ LEGACY_RUNTIME_RETIRED
```

---

# 13. Room schema policy

الحاكم:

```text
Before = 81
After  = 81
```

314 لا تحتاج durable state جديدة.

أي `Migration(81,82)` دون `BLOCKED_SCOPE_DRIFT`:

```text
FAIL_ROOM_SCHEMA_DRIFT_314
```

---

# 14. Server migration policy

لا migration v314 جديدة افتراضيًا.

المطلوب runtime في 314-R هو **تطبيق واختبار v313 migration الموجودة** في staging مناسب.

أي تعديل v313 SQL:

```text
FAIL_HISTORICAL_MIGRATION_DRIFT
```

أي v314 SQL جديدة دون blocker مثبت في contract semantics:

```text
BLOCKED_SERVER_SCOPE_DRIFT
```

---

# 15. Required kill switches

يجب وجود authority واحدة قابلة للاختبار للستة التالية على الأقل:

```text
V2_PULL
V2_PUSH
V2_FINANCIAL
V2_INVENTORY
REALTIME_HINTS
LEGACY_FALLBACK
```

يجوز الإبقاء على `isVersionedSyncEnabled` كـcompatibility/master envelope، لكن لا يبقى هو kill switch الوحيد.

---

# 16. Kill-switch defaults في static/pre-cutover build

قبل runtime approval:

```text
V2_PULL          = OFF
V2_PUSH          = OFF
V2_FINANCIAL     = OFF
V2_INVENTORY     = OFF
REALTIME_HINTS   = OFF
LEGACY_FALLBACK  = ON
```

`314-S` يجب أن ينتهي بهذه defaults.

---

# 17. Kill-switch invariants

```text
V2_FINANCIAL=ON requires V2_PUSH=ON and V2_PULL=ON
V2_INVENTORY=ON requires V2_PUSH=ON and V2_PULL=ON
REALTIME_HINTS=ON requires V2_PULL=ON
LEGACY_FALLBACK=OFF requires final rollout gate
```

أي combination غير صالح:

```text
FAIL_INVALID_ROLLOUT_FLAG_COMBINATION
```

---

# 18. Kill switches must be scope-aware

التفعيل يجب أن يدعم على الأقل:

```text
global default
organization allowlist/denylist
environment (local/staging/production)
```

لا يلزم RemoteConfig dependency جديدة.

يجوز تنفيذ provider abstraction مع in-process defaults قابلة للاستبدال لاحقًا.

---

# 19. Rollout policy authority

إنشاء abstraction مثل:

```text
SyncRolloutPolicy
SyncRolloutSnapshot
SyncRolloutWave
SyncAggregateOwnership
```

أو equivalent موثق.

القرار runtime لا يجب أن يكون موزعًا على if-statements غير متناسقة.

---

# 20. Waves المعتمدة

```text
WAVE_0_SYNTHETIC
WAVE_1_TEST_ORG_LEGACY_AUTHORITY
WAVE_2_V2_PULL_SHADOW
WAVE_3_V2_NONFINANCIAL_WRITE
WAVE_4_V2_INVENTORY_FINANCIAL
WAVE_5_REALTIME_ACCELERATION
WAVE_6_DEFAULT_ON
```

لا قفز من Wave 1 إلى Wave 4 أو 6.

---

# 21. Wave 0

```text
environment = local/staging synthetic
live production ownership = none
fault injection = enabled in test harness only
legacy deletion = forbidden
```

الغرض: إثبات harness نفسه قبل أي مستخدم حقيقي.

---

# 22. Wave 1

```text
organization = one explicit test organization
legacy remains authoritative
V2 writes = OFF
V2 live apply = OFF
Realtime = OFF
```

يجمع baseline telemetry فقط.

---

# 23. Wave 2 — Shadow pull

V2 يقرأ revision feed ويقارن النتيجة المتوقعة مع authoritative local/legacy result **دون**:

```text
Room domain mutation
cursor commit
outbox mutation
conflict state mutation
recovery transition
```

Shadow evidence فقط.

---

# 24. Shadow comparator contract

إنشاء `UnifiedSyncShadowComparator` أو equivalent.

يجب أن ينتج:

```text
aggregate_type
scope_id
server_revision_range
row_count
canonical_digest
comparison_status
mismatch_category
```

دون payload حساس.

---

# 25. Shadow comparator privacy

ممنوع artifact/log يحتوي:

```text
full row payload
financial body
access token
Authorization header
attachment URI
password/OTP
```

Digests يجب أن تكون one-way وغير قابلة للاستخدام كـcursor.

---

# 26. Wave 2 gate

قبل Wave 3:

```text
shadow unexplained divergence count = 0
cursor mutation by shadow            = 0
Room mutation by shadow              = 0
outbox mutation by shadow            = 0
```

أي mismatch غير مفسر يمنع التقدم.

---

# 27. Wave 3 — Non-financial write ownership

يسمح V2 write فقط للـaggregates ذات sensitivity غير المالية/ledger وفق registry.

لا hardcoded list منفصلة دون verification against registry.

على الأقل تُستبعد:

```text
LEDGER_AFFECTING
FINANCIAL
INVENTORY_LEDGER
```

---

# 28. Wave 3 examples from current registry

Non-financial candidates تشمل مثلًا:

```text
PARTY_IDENTITY
PARTY_ROLE
CUSTOMER_PROFILE
SUPPLIER_PROFILE
NOTE
REMINDER
INVENTORY_ITEM
INVENTORY_UNIT
CATEGORY
ITEM_CATEGORY
PRICE_LIST
NOTIFICATION
ORGANIZATION_SETTINGS
SHIPMENT
EDUCATIONAL_CONTENT
OPTIMAL_VEHICLE
OPTIMAL_MAINTENANCE
OPTIMAL_FOLLOW_UP
```

هذه أمثلة مستخرجة من registry؛ verifier النهائي يعتمد registry نفسه لا القائمة النصية.

---

# 29. Wave 4 — Inventory/Financial

لا يدخل أي aggregate حساس حتى تمر Wave 3 runtime gate.

الحساس يشمل registry sensitivities:

```text
FINANCIAL
LEDGER_AFFECTING
INVENTORY_LEDGER
```

وتبقى stronger semantics من 310 حاكمة.

---

# 30. Wave 4 finance invariants

لكل financial/ledger mutation:

```text
same mutation/command id on retry
same authoritative receipt
no duplicate ledger effect
no generic LWW fallback
no legacy replay after V2 commit
```

---

# 31. Wave 4 inventory invariants

```text
movement identity immutable
cost revision immutable/versioned
no duplicate stock movement
no duplicate cost revision
no legacy dirty snapshot overwrite
```

---

# 32. Wave 5 — Realtime

Realtime يفتح بعد ثبوت periodic/manual convergence.

المطلوب:

```text
Realtime ON  → faster wake possible
Realtime OFF → same eventual authoritative result
```

---

# 33. Realtime optionality gate

نفذ نفس scenario مرتين:

```text
A. Realtime hints ON
B. Realtime hints OFF
```

بعد drain/recovery idle:

```text
canonical final state A == canonical final state B
outbox terminal identities A == B
server authoritative effects A == B
```

---

# 34. Wave 6 — Default-on

لا default-on إلا إذا:

```text
all prior runtime waves passed
no unexplained divergence
no cursor drift
no tenant leak
no duplicate financial/inventory effect
recovery runtime passed
Realtime optionality passed
kill switches verified
rollback/pause procedure verified
```

---

# 35. Default-on semantics

عند Wave 6:

```text
V2_PULL       = ON
V2_PUSH       = ON
V2_FINANCIAL  = ON
V2_INVENTORY  = ON
Realtime      = policy-controlled, not correctness-required
```

`LEGACY_FALLBACK` يبقى مؤقتًا ON فقط خلال observation window، لكنه لا يملك write authority للaggregates التي أصبحت V2 authoritative.

---

# 36. Ownership rule — أهم قاعدة rollout

لنفس aggregate/scope في نفس اللحظة:

```text
one authoritative writer path only
```

ممنوع:

```text
legacy push + V2 push
legacy delete queue + V2 delete mutation
legacy timestamp pull + V2 live pull apply
```

لنفس ownership slice.

---

# 37. Ownership states

كل aggregate يجب أن يكون في واحدة فقط:

```text
LEGACY_AUTHORITATIVE
V2_SHADOW_READ
V2_AUTHORITATIVE
V2_PAUSED_SAFE
RETIRED_LEGACY
```

لا `DUAL_WRITE_AUTHORITATIVE`.

---

# 38. Safe rollback semantics

قبل V2 write ownership:

```text
rollback → LEGACY_AUTHORITATIVE
```

بعد V2 write ownership ووجود committed V2 mutations:

```text
rollback != replay legacy dirty queue
```

الصحيح:

```text
V2_PAUSED_SAFE
preserve durable outboxes
periodic/manual writes paused or gated as required
recover/reconcile
resume V2 or perform explicit migration procedure
```

---

# 39. Legacy fallback definition

`LEGACY_FALLBACK=ON` لا يعني أن legacy يستطيع دائمًا استعادة write ownership فورًا.

هو يسمح legacy فقط حيث ownership matrix تقول إنه ما زال authoritative وآمن.

---

# 40. Fault-injection harness architecture

إنشاء harness deterministic مثل:

```text
FaultPlan
FaultPoint
FaultingSyncRemote
CommitOracle
DeviceHarness
VirtualTransport
DeterministicScheduler/seed
```

أو equivalent.

كل run يجب أن يسجل seed/scenario بدون payload حساس.

---

# 41. Determinism requirement

نفس:

```text
seed + initial state + fault plan
```

يجب أن ينتج نفس:

```text
final digest
outbox terminal set
cursor state
conflict state
server effect count
```

---

# 42. Network matrix — mandatory

على الأقل:

```text
offline → online
packet loss
timeout before server commit
timeout after server commit
HTTP 429
HTTP 500/502/503/504
reconnect loops
```

---

# 43. Timeout before commit invariant

```text
client mutation remains retryable
no false ACK
no cursor jump
no data loss
```

---

# 44. Timeout after commit invariant

Server may have committed while client missed response.

Retry must produce:

```text
same idempotent receipt/effect
server effect count = 1
client eventually ACKED/APPLIED
```

---

# 45. 429 invariant

```text
respect retry/backoff policy
no busy loop
lease/process-death semantics preserved
no mutation drop
```

---

# 46. 5xx invariant

```text
transient classification where contract permits
bounded retries
no permanent cursor advance on failed apply
no command duplication
```

---

# 47. Reconnect loop invariant

Realtime reconnect loop لا يجوز أن ينتج:

```text
fullSync storm
unbounded generation storm
parallel drain ownership
cursor writes from realtime
```

---

# 48. Device-clock matrix — mandatory

```text
device +24h
device -24h
same local timestamp for multiple rows
```

PASS إذا:

```text
server revision ordering unaffected
cursor unchanged by wall clock
idempotency unaffected
```

---

# 49. Process matrix — mandatory

```text
kill during local transaction
kill after durable enqueue
kill during push
kill after server commit before ACK persistence
kill during pull
kill before cursor commit
```

ويضاف من 313 regression:

```text
kill during bootstrap staging
kill during recovery cutover
```

---

# 50. Kill during local transaction

PASS إذا atomic local writer لا ينتج half-intent:

```text
domain+outbox both commit
or both rollback
```

---

# 51. Kill after enqueue

بعد process restart:

```text
same durable mutation id remains discoverable
WorkManager/generation can resume
```

---

# 52. Kill during push

Lease recovery من 309 يبقى authority.

ممنوع اعتبار `LEASED` = synced.

---

# 53. Kill after server commit

هذا أهم exactly-once-effect test.

PASS:

```text
retry same mutation id
server effect count remains 1
receipt resolves local state
```

---

# 54. Kill during pull

إذا page apply transaction لم تكتمل:

```text
cursor does not advance
```

إذا اكتملت:

```text
cursor and Room commit together
```

---

# 55. Data matrix — mandatory

```text
more than one page
duplicate event
reordered network responses
delete/recreate
edit/delete conflict
unsupported payload version
```

---

# 56. Multi-page invariant

لا cutoff بعد page 1.

يجب إثبات:

```text
all pages consumed until server termination token
no item loss across page boundaries
cursor final = server-owned committed token
```

---

# 57. Duplicate event invariant

```text
same server change delivered twice
```

لا ينتج duplicate domain effect أو outbox echo.

---

# 58. Reordered response invariant

Late response لا يملك authority فوق scope/generation الحالي.

لا cursor regression ولا stale apply.

---

# 59. Delete/recreate invariant

يجب احترام identity/version semantics للaggregate.

لا resurrect stale deleted state بسبب timestamp.

---

# 60. Unsupported payload invariant

```text
fail closed
no cursor past unsupported change
normalized diagnostic
no raw sensitive payload logging
```

---

# 61. Multi-device matrix — mandatory

على الأقل:

```text
A and B edit same client
A deletes while B edits
A offline for long period then returns
invoice/payment ordering
inventory movement conflict
cash/expense commands
shipment state transitions
```

---

# 62. Same client concurrent edit

للaggregates optimistic/versioned:

```text
conflict explicit or deterministic resolution per registry
no silent lost update
both devices converge after drain
```

---

# 63. Delete vs edit

النتيجة تتبع delete/conflict policy للaggregate، لا arrival timestamp.

كلا الجهازين يجب أن يصلا لنفس authoritative state.

---

# 64. Long-offline device

عند عودة A:

```text
expired cursor may bootstrap
pending local intent preserved
no timestamp fallback
final convergence with B/server
```

---

# 65. Invoice/payment ordering

اختبر على الأقل:

```text
invoice created on A
payment mutation on B or delayed delivery
response reordering
retry after commit
```

PASS:

```text
financial effects exactly once
no orphan silent payment
conflict/review explicit when ordering precondition unmet
```

---

# 66. Inventory conflict

اختبر نفس stock item على جهازين.

PASS:

```text
movement identities preserved
no duplicate movement
server-authoritative reconciliation converges
```

---

# 67. Cash/expense commands

لا LWW.

أي duplicate financial effect:

```text
FAIL_MULTI_DEVICE_FINANCIAL_DUPLICATE_EFFECT
```

---

# 68. Shipment state transitions

يجب احترام state-machine ordering.

Late stale transition لا تعكس state authoritative أحدث.

---

# 69. Tenancy matrix — mandatory

```text
logout/login
switch organization
stale WorkManager job
stale Realtime channel
RLS adversarial tests
```

---

# 70. Logout/login invariant

late push/pull/recovery result من epoch قديم:

```text
must not mutate new session scope
```

---

# 71. Organization switch invariant

old org worker/channel لا يكتب:

```text
new org Room slice
new org cursor
new org outbox
new org recovery state
```

---

# 72. Stale WorkManager invariant

stale scope:

```text
success/no-op
no network ownership
no cutover
no cross-tenant mutation
```

---

# 73. Stale Realtime invariant

late callback بعد stop/switch:

```text
ignored
no generation request for new tenant
```

---

# 74. RLS adversarial runtime gate

في staging/PostgreSQL حقيقي اختبر على الأقل:

```text
org A token requesting org B bootstrap
org A pulling org B changes
org A applying mutation scoped to org B
org A requesting reconciliation manifest for org B
```

PASS = denied/no rows حسب contract، مع zero cross-tenant leakage.

---

# 75. Runtime environment requirements for FINAL PASS

`PASS_FINAL` يتطلب تنفيذًا حقيقيًا مناسبًا لـ:

```text
Android/Room migration 80→81
PostgreSQL v313 migration
bootstrap RPCs
push RPCs
pull RPCs
recovery
WorkManager process-death/restart behavior
two logical/physical clients against same staging authority
RLS
Realtime ON/OFF comparison
```

Model-only لا يحل محلها.

---

# 76. Room runtime gate

يجب اختبار database حقيقية على الأقل:

```text
schema80 populated → migrate → schema81
pre-existing domain rows preserved
pre-existing outboxes preserved
recovery tables created
app opens successfully
```

---

# 77. PostgreSQL v313 runtime gate

قبل bootstrap runtime:

```text
apply v313 migration in staging
verify function/trigger/materializer objects
verify backfill coverage
verify rerun/idempotent safety where applicable
```

`serverMigrationApplied313=true` فقط بعد نجاح فعلي.

---

# 78. Bootstrap runtime gate

على server المطبق فعليًا:

```text
fresh app/account with existing data
begin bootstrap
multi-page snapshot if possible
atomic cutover
baseline cursor installed
normal pull after baseline
```

---

# 79. Cursor-expiry runtime gate

يجب محاكاة/إنشاء cursor expired حقيقي أو server test hook موثق.

PASS:

```text
recovery starts
outbox preserved
new trusted bootstrap
no timestamp fallback
```

---

# 80. Pending-mutation recovery runtime gate

أثناء وجود mutation غير ACKed:

```text
force safe full resync
```

PASS:

```text
same mutation identity survives
server final effect once
client converges
```

---

# 81. Process-death runtime gate

على Android/Room runtime اختبر على الأقل:

```text
kill after enqueue
kill during/after uncertain push
kill during bootstrap pages
kill before/after recovery cutover boundary
```

---

# 82. Multi-device convergence definition

بعد توقف writes وترك drains تصل idle:

```text
canonical materialized digest device A
= canonical materialized digest device B
= server-visible canonical digest
```

للنطاق المرئي نفسه.

---

# 83. Convergence excludes local diagnostics

لا تدخل في canonical digest:

```text
retry timestamps
worker ids
local log timestamps
realtime connection state
health freshness timestamps
```

---

# 84. Conflict convergence

قد تختلف conflict UX metadata مؤقتًا، لكن authoritative domain state لا يجوز أن تختلف بعد resolution/drain.

---

# 85. Fault injection outcome taxonomy

كل scenario ينتهي بأحد:

```text
CONVERGED
EXPECTED_REVIEW_REQUIRED
EXPECTED_AUTH_BLOCKED
EXPECTED_RECOVERY_REQUIRED_THEN_CONVERGED
FAIL
```

لا `UNKNOWN` في final matrix.

---

# 86. Fault matrix artifact

إنشاء:

```text
docs/sync/VERTO_SYNC_FAULT_MATRIX_v314.csv
```

الأعمدة:

```text
scenario_id
category
fault
aggregate_scope
device_count
runtime_or_model
seed
expected_outcome
actual_outcome
cursor_invariant
outbox_invariant
effect_count_invariant
tenant_invariant
evidence
```

---

# 87. Multi-device artifact

إنشاء:

```text
docs/sync/VERTO_SYNC_MULTI_DEVICE_MATRIX_v314.csv
```

بـ7 scenarios الأساسية على الأقل.

---

# 88. Kill-switch artifact

إنشاء:

```text
docs/sync/VERTO_SYNC_KILL_SWITCHES_v314.md
```

يوثق:

```text
flag
scope
default
dependencies
safe-on prerequisites
safe-off behavior
rollback semantics
correctness authority
```

---

# 89. Rollout matrix artifact

إنشاء:

```text
docs/sync/VERTO_SYNC_ROLLOUT_MATRIX_v314.csv
```

صف لكل Wave/aggregate group على الأقل.

الأعمدة:

```text
wave
group
ownership
v2_pull
v2_push
financial
inventory
realtime
legacy_fallback
entry_gate
exit_gate
runtime_evidence
```

---

# 90. Legacy inventory artifact

إنشاء:

```text
docs/sync/VERTO_SYNC_LEGACY_INVENTORY_v314.csv
```

كل legacy surface يصنف:

```text
surface_id
file
symbol
category
aggregate_or_scope
current_runtime_callers
replacement
retire_gate
final_action
runtime_use_count
evidence
```

الفئات:

```text
TIMESTAMP_PULL
DATASTORE_DELETE_QUEUE
GENERIC_DIRTY_PUSH
LEGACY_FULL_SYNC
LEGACY_SYNCV2_SCAFFOLD
LEGACY_REALTIME_FULLSYNC
DORMANT_SCHEMA_RESIDUE
```

---

# 91. Legacy inventory completeness

PASS static يتطلب:

```text
all known legacy paths classified
UNKNOWN = 0
TODO = 0
LATER = 0
```

لا يتطلب حذفها في `314-S`.

---

# 92. Legacy runtime-use telemetry

قبل final retirement يجب قياس الاستخدام الفعلي للlegacy path خلال observation window.

الحاكم:

```text
legacyRuntimeUseCount = 0
```

بعد أن تكون V2 default-on للمؤسسات المستهدفة.

---

# 93. Observation window

العقد لا يخترع مدة زمنية ثابتة بلا بيانات تشغيلية.

المطلوب artifact runtime يثبت:

```text
window_start
window_end
client population covered
sync runs observed
legacy fallback invocations
unexplained divergence
cursor drift
critical conflicts
```

لا final retirement إذا window غير موجودة.

---

# 94. Old-client compatibility gate

قبل إزالة مفاتيح/مسارات Legacy يجب إثبات:

```text
minimum supported client version no longer needs legacy
```

أو أن server compatibility policy تمنع clients القديمة من الاعتماد عليه.

بدون ذلك:

```text
BLOCKED_OLD_CLIENT_DEPENDENCY
```

---

# 95. DataStore deletion queue retirement

ممنوع:

```text
clear pending deletion set and delete code
```

المطلوب قبل retirement:

```text
all entries empty
or deterministically migrated to durable authoritative outbox with same identity
```

أي pending item lost:

```text
FAIL_LEGACY_DELETE_INTENT_LOSS
```

---

# 96. Timestamp marker retirement

بعد final cutover:

```text
KEY_LAST_PULLED_* no longer read/write sync authority
getLastPulledAt/setLastPulledAt no longer used by production sync paths
```

بقايا preference bytes على جهاز قديم لا تعتبر authority إذا لا يوجد runtime reader.

---

# 97. Generic dirty/clean retirement

لا تحذف كل `isDirty` من domain؛ قد يكون له معنى UI/business.

المطلوب فقط:

```text
no generic dirty snapshot network writer remains authoritative where V2/stronger outbox owns sync
```

Verifier يجب أن يفحص call graph/known sync files، لا regex أعمى فقط.

---

# 98. Stronger outboxes are NOT legacy

ممنوع حذف:

```text
party_sync_outbox
financial_outbox
inventory_stock_outbox
inventory_cost_outbox
optimal_outbox
sync_attachment_transfer
sync_outbox
```

هذه durable intent authorities من 307/309/310/313.

---

# 99. Legacy fullSync retirement

بعد final gate:

```text
production UI/worker/scheduler must route through durable V2 orchestration
```

`SyncManager.fullSync` إن بقي للاختبار أو compatibility يجب ألا يكون production reachable مع `LEGACY_FALLBACK=OFF`.

---

# 100. Realtime legacy behavior

312 أثبتت أن Realtime لا يستدعي legacy fullSync.

314 يجب أن تحافظ:

```text
realtimeDirectRoomMutationCount = 0
realtimeCursorWriteCount = 0
legacyFullSyncFromRealtimeCount = 0
```

---

# 101. Obsolete SyncV2 scaffolding

أي old cursor/run state في DataStore مثل numeric `syncV2Cursor` لا يحذف إلا بعد إثبات عدم وجود production caller.

لا تحول numeric legacy cursor إلى opaque V2 cursor.

---

# 102. No cursor migration fabrication

ممنوع:

```text
legacy timestamp/numeric cursor → synthesize V2 opaque cursor
```

الانتقال إلى V2 يتم عبر trusted bootstrap/recovery من 313.

---

# 103. Final legacy retirement order

الترتيب الإلزامي:

```text
1. V2 default-on runtime proven
2. observation window completed
3. zero unexplained divergence/cursor drift
4. old-client gate passed
5. legacy runtime use = 0
6. deletion queues empty/migrated
7. disable legacy fallback
8. run fault/convergence smoke again
9. remove unreachable legacy transport/code
10. rerun full static + runtime smoke
```

---

# 104. Disable before delete

لا تحذف Legacy في نفس اللحظة التي يتم فيها أول تعطيل.

يجب أن يوجد state/evidence يثبت:

```text
LEGACY_DISABLED_BUT_PRESENT
```

ثم بعد smoke/observation:

```text
LEGACY_RETIRED
```

إذا بيئة التنفيذ لا تسمح observation حقيقية، ينتهي 314 عند `LEGACY_PRESERVED` أو `LEGACY_DISABLED_BUT_PRESENT` ولا يدعي final removal.

---

# 105. Runtime rollback after legacy disabled

إذا ظهر blocker بعد تعطيل legacy وقبل الحذف:

```text
re-enable legacy only for slices where ownership rollback is safe
```

لا تعيد legacy write authority فوق V2 committed aggregates تلقائيًا.

---

# 106. Financial rollback rule

بعد أول V2 financial commit:

```text
legacy financial replay fallback = forbidden
```

البديل:

```text
V2_PAUSED_SAFE + reconcile/recovery/manual review
```

---

# 107. Inventory rollback rule

بعد V2 inventory movement/cost commit:

```text
legacy dirty stock snapshot writer cannot overwrite authoritative movement ledger
```

---

# 108. Shipment rollback rule

لا تعيد state machine إلى legacy timestamp/order behavior بعد V2 state transition commit.

---

# 109. Health/telemetry gates

اعتمد `SyncHealthSnapshot` من 313 ووسع فقط بما يلزم rollout:

```text
rolloutWave
ownershipMode
shadowMismatchCount
legacyFallbackUseCount
v2PullEnabled
v2PushEnabled
financialEnabled
inventoryEnabled
realtimeEnabled
```

دون payload.

---

# 110. Cursor drift definition

Drift لا يعني gap رقمي طبيعي.

يعتبر drift إذا:

```text
cursor token belongs to wrong scope
lastApplied evidence contradicts Room/inbox
cursor advances past unapplied/unsupported change
shadow/live canonical state diverges without pending-local explanation
```

---

# 111. Divergence categories

على الأقل:

```text
EXPECTED_PENDING_LOCAL
EXPECTED_VISIBILITY_DIFFERENCE
EXPECTED_CONFLICT_REVIEW
UNEXPLAINED_DOMAIN_MISMATCH
CURSOR_ANCHOR_MISMATCH
CROSS_TENANT_MISMATCH
```

Final rollout يتطلب كل `UNEXPLAINED_* = 0`.

---

# 112. No sensitive telemetry

نفس privacy rules 313 تبقى.

إضافة منع:

```text
shadow canonical source rows
RLS adversarial request bodies
financial command bodies
raw server receipts
```

من artifacts/logs العامة.

---

# 113. Runtime evidence artifact

إنشاء:

```text
VERTO_SYNC_RUNTIME_EVIDENCE_v314.json
VERTO_SYNC_RUNTIME_EVIDENCE_v314.md
```

الـJSON لا يمكن أن يساوي runtime PASS إذا evidence files غير موجودة/موقعة ببصمة داخلية من نفس run.

---

# 114. Required runtime evidence fields

على الأقل:

```text
room8081RuntimeExecuted
room8081RuntimePassed
postgres313Executed
postgres313Passed
bootstrapRuntimeExecuted
bootstrapRuntimePassed
cursorExpiryRuntimeExecuted
cursorExpiryRuntimePassed
pendingMutationRecoveryExecuted
pendingMutationRecoveryPassed
processDeathRuntimeExecuted
processDeathRuntimePassed
twoDeviceRuntimeExecuted
twoDeviceRuntimePassed
rlsAdversarialExecuted
rlsAdversarialPassed
realtimeParityExecuted
realtimeParityPassed
rolloutWavesExecuted
observationWindowPresent
legacyRuntimeUseCount
legacyDisabledSmokePassed
legacyRemovalExecuted
runtimeEnvironmentFingerprint
```

---

# 115. Runtime truth rule

إذا field `*Executed=false`:

```text
corresponding *Passed must be false
```

أي contradiction:

```text
FAIL_RUNTIME_EVIDENCE_FABRICATION
```

---

# 116. Rollout evidence per wave

كل Wave runtime لها:

```text
started_at
ended_at
organizations
client_instances
flags_snapshot
entry_gate_result
exit_gate_result
divergence_count
cursor_drift_count
critical_effect_violation_count
```

---

# 117. Runtime client identity privacy

لا تخزن user email/phone/token.

استخدم opaque test-client labels مثل:

```text
DEVICE_A
DEVICE_B
TEST_ORG_HASH
```

---

# 118. Model fixture minimum

314-S تنشئ >= `600` fixture/model assertions جديدة خاصة بـ314.

لا تحتسب regressions 313→308 ضمن الـ600.

---

# 119. Required model categories

على الأقل:

```text
kill_switch_matrix
rollout_wave_transitions
ownership_exclusivity
safe_rollback
shadow_no_side_effects
network_faults
clock_skew
process_death
data_ordering
multi_device
financial_exactly_once
inventory_exactly_once
tenancy
realtime_optionality
legacy_inventory
legacy_retirement_gates
runtime_truth_model
privacy
```

---

# 120. Required named model passes

الـJSON النهائي يجب أن يحتوي true على الأقل:

```text
MODEL_KILL_SWITCH_INDEPENDENCE_PASS
MODEL_INVALID_FLAG_COMBINATION_FAIL_CLOSED_PASS
MODEL_WAVE_ORDERING_PASS
MODEL_SHADOW_PULL_NO_ROOM_MUTATION_PASS
MODEL_SHADOW_PULL_NO_CURSOR_MUTATION_PASS
MODEL_SHADOW_PULL_NO_OUTBOX_MUTATION_PASS
MODEL_OWNERSHIP_SINGLE_WRITER_PASS
MODEL_POST_V2_WRITE_ROLLBACK_SAFE_PASS
MODEL_NETWORK_OFFLINE_ONLINE_PASS
MODEL_PACKET_LOSS_PASS
MODEL_TIMEOUT_BEFORE_COMMIT_PASS
MODEL_TIMEOUT_AFTER_COMMIT_IDEMPOTENT_PASS
MODEL_RATE_LIMIT_BACKOFF_PASS
MODEL_5XX_RETRY_PASS
MODEL_RECONNECT_NO_STORM_PASS
MODEL_CLOCK_PLUS_24H_PASS
MODEL_CLOCK_MINUS_24H_PASS
MODEL_SAME_TIMESTAMP_NO_AUTHORITY_PASS
MODEL_KILL_AFTER_ENQUEUE_PASS
MODEL_KILL_AFTER_SERVER_COMMIT_PASS
MODEL_KILL_DURING_PULL_PASS
MODEL_MULTI_PAGE_PASS
MODEL_DUPLICATE_EVENT_PASS
MODEL_REORDERED_RESPONSE_PASS
MODEL_DELETE_RECREATE_PASS
MODEL_EDIT_DELETE_CONFLICT_PASS
MODEL_UNSUPPORTED_PAYLOAD_FAIL_CLOSED_PASS
MODEL_TWO_DEVICE_CLIENT_CONVERGENCE_PASS
MODEL_LONG_OFFLINE_DEVICE_RECOVERY_PASS
MODEL_INVOICE_PAYMENT_ORDERING_PASS
MODEL_INVENTORY_MULTI_DEVICE_PASS
MODEL_CASH_EXPENSE_EXACTLY_ONCE_PASS
MODEL_SHIPMENT_STATE_MACHINE_PASS
MODEL_LOGOUT_LOGIN_SCOPE_PASS
MODEL_ORG_SWITCH_SCOPE_PASS
MODEL_STALE_WORKER_PASS
MODEL_STALE_REALTIME_PASS
MODEL_REALTIME_OPTIONAL_CONVERGENCE_PASS
MODEL_LEGACY_DELETE_INTENT_PRESERVATION_PASS
MODEL_LEGACY_TIMESTAMP_AUTHORITY_RETIREMENT_PASS
MODEL_DIRTY_SYNC_AUTHORITY_RETIREMENT_PASS
MODEL_STRONGER_OUTBOX_PRESERVED_PASS
MODEL_OLD_CLIENT_GATE_PASS
MODEL_RUNTIME_TRUTH_PASS
MODEL_V2_DEFAULT_OFF_PRECUTOVER_PASS
MODEL_ROOM81_UNCHANGED_PASS
MODEL_NO_V314_SQL_PASS
```

---

# 121. Network model amplification

على الأقل `100` من fixtures 314 يجب أن تغطي transport uncertainty/retry/interleavings، لا مجرد boolean config.

---

# 122. Multi-device model amplification

على الأقل `120` fixture interleavings عبر جهازين.

يجب تغطية:

```text
A before B
B before A
concurrent
A offline then B
B offline then A
retry duplicates
```

---

# 123. Financial/inventory model amplification

على الأقل `100` fixtures combined للـfinancial/inventory stronger semantics.

أي duplicate effect counter >0 يمنع PASS.

---

# 124. Tenant model amplification

على الأقل `60` fixtures للscope/session/channel/worker races.

---

# 125. Legacy retirement model amplification

على الأقل `80` fixtures تشمل:

```text
pending legacy delete queue
old client present
legacy use > 0
unexplained divergence > 0
V2 committed financial mutation
V2 committed inventory mutation
legacy disabled smoke failure
```

كلها يجب أن تمنع الحذف عندما يلزم.

---

# 126. Runtime matrix minimum

Final runtime evidence يجب أن يغطي **34 scenario classes** من الخطة على الأقل:

```text
network    = 7
time       = 3
process    = 6
data       = 6
multi      = 7
tenancy    = 5
TOTAL      = 34
```

يجوز scenario واحد يغطي أكثر من class، لكن artifact يجب أن يثبت coverage لكل class.

---

# 127. Critical runtime scenarios cannot be model-only

هذه إلزامية runtime لـPASS_FINAL:

```text
timeout after real server commit
two-device same aggregate conflict
invoice/payment ordering
inventory movement conflict
logout/org-switch stale worker
RLS cross-tenant denial
Realtime ON/OFF convergence
Room 80→81 migration
v313 PostgreSQL migration/bootstrap
pending mutation full recovery
```

---

# 128. 313 regression gate

يجب إعادة:

```text
494/494 v313 fixtures
```

ويظل:

```text
bootstrapSnapshotCoverageGapCount = 0
owner310BootstrapCoverageGapCount = 0
existingDataBackfillGapCount = 0
recoveryOutboxLostCount = 0
recoveryStrongerOutboxLostCount = 0
recoveryAttachmentIntentLostCount = 0
recoveryCursorJumpCount = 0
recoveryTimestampAuthorityCount = 0
fakeBootstrapSyncChangeCount = 0
recoveryFinancialDuplicateEffectCount = 0
recoveryInventoryDuplicateEffectCount = 0
new313WaiverCount = 0
```

---

# 129. 312 regression gate

```text
300/300 PASS
```

ويظل:

```text
realtimeDirectRoomMutationCount = 0
realtimeCursorWriteCount = 0
lateStopKillsNewListenerCount = 0
lateCallbackAcceptedCount = 0
realtimeTargetBusyLoopCount = 0
```

---

# 130. 311 regression gate

```text
325/325 PASS
```

ويظل:

```text
lostSyncIntentCount = 0
idleCasViolationCount = 0
finalExitRaceLossCount = 0
staleSessionEpochExecutionCount = 0
oldRealtimeCrossTenantRequestCount = 0
```

---

# 131. 310 regression gate

```text
499/499 PASS
```

ويظل:

```text
duplicateFinancialEffectViolationCount = 0
duplicateInventoryEffectViolationCount = 0
duplicateOptimalEffectViolationCount = 0
owner310LwwFallbackCount = 0
remoteApplyEnqueueCount = 0
```

---

# 132. 309 regression gate

```text
272/272 PASS
```

Idempotency/receipt/conflict/lease semantics لا تتغير.

---

# 133. 308 regression gate

```text
137/137 PASS + MODEL_10K_PASS
```

ويظل:

```text
timestampV2CursorAuthorityCount = 0
atomicCursorViolationCount = 0
remoteApplyEnqueueEchoCount = 0
```

---

# 134. New waivers

```text
new314WaiverCount = 0
```

أي waiver جديدة تمنع PASS_STATIC وPASS_FINAL.

---

# 135. Required failure codes

على الأقل:

```text
BLOCKED_INPUT_DRIFT
BLOCKED_313_STATIC_BASELINE
BLOCKED_313_RUNTIME_HANDOFF
BLOCKED_RUNTIME_REQUIRED
BLOCKED_RUNTIME_FAILURE
BLOCKED_POSTGRES_RUNTIME_FAILURE
BLOCKED_OLD_CLIENT_DEPENDENCY
BLOCKED_SERVER_SCOPE_DRIFT
BLOCKED_SCOPE_DRIFT
FAIL_ROOM_SCHEMA_DRIFT_314
FAIL_HISTORICAL_MIGRATION_DRIFT
FAIL_NEW_V314_SERVER_MIGRATION
FAIL_INVALID_ROLLOUT_FLAG_COMBINATION
FAIL_WAVE_ORDER_VIOLATION
FAIL_SHADOW_ROOM_MUTATION
FAIL_SHADOW_CURSOR_MUTATION
FAIL_SHADOW_OUTBOX_MUTATION
FAIL_UNEXPLAINED_SHADOW_DIVERGENCE
FAIL_DUAL_AUTHORITATIVE_WRITER
FAIL_UNSAFE_LEGACY_ROLLBACK
FAIL_TIMEOUT_AFTER_COMMIT_DUPLICATE_EFFECT
FAIL_RETRY_STORM
FAIL_CURSOR_CLOCK_AUTHORITY
FAIL_PROCESS_DEATH_INTENT_LOSS
FAIL_PULL_CURSOR_ATOMICITY
FAIL_MULTI_PAGE_TRUNCATION
FAIL_DUPLICATE_EVENT_EFFECT
FAIL_REORDERED_STALE_APPLY
FAIL_UNSUPPORTED_PAYLOAD_ADVANCE
FAIL_MULTI_DEVICE_NONCONVERGENCE
FAIL_MULTI_DEVICE_FINANCIAL_DUPLICATE_EFFECT
FAIL_MULTI_DEVICE_INVENTORY_DUPLICATE_EFFECT
FAIL_SHIPMENT_STATE_REGRESSION
FAIL_STALE_SCOPE_MUTATION
FAIL_STALE_REALTIME_REQUEST
FAIL_RLS_CROSS_TENANT_ACCESS
FAIL_REALTIME_OPTIONALITY
FAIL_LEGACY_DELETE_INTENT_LOSS
FAIL_LEGACY_RUNTIME_USE_NONZERO
FAIL_LEGACY_RETIRED_BEFORE_OBSERVATION
FAIL_LEGACY_RETIRED_WITH_OLD_CLIENTS
FAIL_LEGACY_TIMESTAMP_AUTHORITY_REMAINS
FAIL_LEGACY_DIRTY_SYNC_AUTHORITY_REMAINS
FAIL_STRONGER_OUTBOX_REMOVED
FAIL_RUNTIME_EVIDENCE_FABRICATION
FAIL_RUNTIME_CUTOVER_CLAIM
FAIL_V2_DEFAULT_ON_WITHOUT_RUNTIME
FAIL_LEGACY_REMOVAL_WITHOUT_RUNTIME
FAIL_313_REGRESSION
FAIL_312_REGRESSION
FAIL_311_REGRESSION
FAIL_310_REGRESSION
FAIL_309_REGRESSION
FAIL_308_REGRESSION
FAIL_SENSITIVE_ROLLOUT_DIAGNOSTIC
FAIL_V314_VERIFIER_NONDETERMINISTIC
```

---

# 136. Acceptance counters — must equal zero

```text
invalidRolloutFlagCombinationCount
waveOrderViolationCount
shadowRoomMutationCount
shadowCursorMutationCount
shadowOutboxMutationCount
unexplainedShadowDivergenceCount
dualAuthoritativeWriterCount
unsafeLegacyRollbackCount
timeoutAfterCommitDuplicateEffectCount
retryStormCount
clockCursorAuthorityCount
processDeathIntentLossCount
pullCursorAtomicityViolationCount
multiPageTruncationCount
duplicateEventEffectCount
reorderedStaleApplyCount
unsupportedPayloadAdvanceCount
multiDeviceNonConvergenceCount
multiDeviceFinancialDuplicateEffectCount
multiDeviceInventoryDuplicateEffectCount
shipmentStateRegressionCount
staleScopeMutationCount
staleRealtimeRequestCount
rlsCrossTenantAccessCount
realtimeOptionalityViolationCount
legacyDeleteIntentLossCount
legacyTimestampAuthorityActiveAfterRetirementCount
legacyDirtySyncAuthorityActiveAfterRetirementCount
strongerOutboxRemovedCount
runtimeEvidenceFabricationCount
historicalMigrationChangedCount
room81ChangedCount
newV314ServerMigrationCount
sensitiveRolloutDiagnosticCount
new314WaiverCount
v313RegressionFailures
v312RegressionFailures
v311RegressionFailures
v310RegressionFailures
v309RegressionFailures
v308RegressionFailures
```

---

# 137. Acceptance values — static/pre-cutover

```text
inputArchiveEntries                = 1820
inputProductionKotlinCount         = 1191
inheritedExceptionCount            = 9
aggregateRegistryCount             = 34
directAggregateCount               = 17
owner310AggregateCount             = 17
roomVersionBefore/After            = 81/81
serverMigrationV314Count           = 0
v314StaticFixtureCount             >= 600
v313RegressionFixtureCount         = 494
v312RegressionFixtureCount         = 300
v311RegressionFixtureCount         = 325
v310RegressionFixtureCount         = 499
v309RegressionFixtureCount         = 272
v308RegressionFixtureCount         = 137
killSwitchCount                    >= 6
rolloutWaveCount                   = 7
faultScenarioClassCount            >= 34 documented
legacyInventoryUnknownCount        = 0
```

---

# 138. Acceptance values — final runtime only

```text
room8081RuntimePassed              = true
postgres313Passed                  = true
bootstrapRuntimePassed             = true
cursorExpiryRuntimePassed          = true
pendingMutationRecoveryPassed      = true
processDeathRuntimePassed          = true
twoDeviceRuntimePassed             = true
rlsAdversarialPassed               = true
realtimeParityPassed               = true
rolloutWavesExecuted               = [0,1,2,3,4,5,6]
unexplainedDivergenceCount         = 0
cursorDriftCount                   = 0
criticalDuplicateEffectCount       = 0
legacyRuntimeUseCount              = 0
observationWindowPresent           = true
oldClientDependencyCount           = 0
legacyDisabledSmokePassed          = true
```

`legacyRemovalExecuted=true` مطلوب فقط إذا أُعلن `PASS_FINAL ... LEGACY_RUNTIME_RETIRED`.

---

# 139. Static verifier determinism

`verify-v314-sync-cutover.sh` يشغل verifier مرتين.

normalized JSON hash يجب أن يتطابق.

عدم determinism:

```text
FAIL_V314_VERIFIER_NONDETERMINISTIC
```

---

# 140. Required verification artifacts

إنشاء:

```text
VERTO_SYNC_CUTOVER_VERIFICATION_v314.json
VERTO_SYNC_CUTOVER_VERIFICATION_v314.md
VERTO_SYNC_RUNTIME_EVIDENCE_v314.json
VERTO_SYNC_RUNTIME_EVIDENCE_v314.md

docs/sync/VERTO_SYNC_FAULT_MATRIX_v314.csv
docs/sync/VERTO_SYNC_MULTI_DEVICE_MATRIX_v314.csv
docs/sync/VERTO_SYNC_ROLLOUT_MATRIX_v314.csv
docs/sync/VERTO_SYNC_LEGACY_INVENTORY_v314.csv
docs/sync/VERTO_SYNC_KILL_SWITCHES_v314.md
docs/sync/VERTO_SYNC_CUTOVER_POLICY_v314.md
docs/sync/VERTO_SYNC_LEGACY_RETIREMENT_v314.md

tools/verify_sync_cutover_v314.py
tools/test_sync_cutover_verification_v314.py
scripts/verify-v314-sync-cutover.sh
scripts/run-v314-runtime-staging.sh

Verto-v314-report.md
```

---

# 141. Runtime script honesty

`run-v314-runtime-staging.sh` لا يجوز أن يحول missing environment إلى PASS.

إذا environment ناقصة:

```text
runtimeExecuted = false
runtimeStatus = NOT_RUN_ENVIRONMENT_UNAVAILABLE
```

إذا command بدأ فعليًا وفشل:

```text
runtimeExecuted = true
runtimeStatus = BLOCKED_RUNTIME_FAILURE
```

---

# 142. Build/runtime policy

بسبب blocker الموروث، 314-S تستطيع PASS ساكن فقط إذا **لم تعِد ادعاء compile success**.

إذا Gradle command شغل في 314 وفشل:

```text
BLOCKED_RUNTIME_FAILURE
```

ولا يتحول إلى `BUILD_NOT_RUN`.

---

# 143. PostgreSQL policy

إذا لم ينفذ:

```text
postgres313Executed = false
postgres313Passed = false
```

إذا نفذ وفشل:

```text
BLOCKED_POSTGRES_RUNTIME_FAILURE
```

---

# 144. No synthetic final runtime PASS

Model harness قد يثبت design behavior لكنه لا يغير:

```text
twoDeviceRuntimeExecuted=false
```

إلى true.

---

# 145. Verification JSON minimum fields

`VERTO_SYNC_CUTOVER_VERIFICATION_v314.json` يحتوي على الأقل:

```text
session
inputZipName
inputZipSha256
inputArchiveEntries
inputProductionKotlinCount
planSha256
session313ContractSha256
v313VerificationSha256
v313FinalVerdict
v313StaticGatesPassed
v313Handoff314Authorized
inheritedRuntimeBlocker
inheritedExceptionCount
new314WaiverCount
roomVersionBefore
roomVersionAfter
schema81Sha256
aggregateRegistryCount
directAggregateCount
owner310AggregateCount
killSwitchCount
rolloutWaveCount
legacyInventoryRows
legacyInventoryUnknownCount
faultScenarioClassCount
v314FixtureStats
shadowRoomMutationCount
shadowCursorMutationCount
shadowOutboxMutationCount
unexplainedShadowDivergenceCount
dualAuthoritativeWriterCount
unsafeLegacyRollbackCount
multiDeviceNonConvergenceCount
multiDeviceFinancialDuplicateEffectCount
multiDeviceInventoryDuplicateEffectCount
realtimeOptionalityViolationCount
legacyDeleteIntentLossCount
legacyRuntimeUseCount
oldClientDependencyCount
historicalMigrationChangedCount
newV314ServerMigrationCount
runtimeEvidence
v313RegressionFailures
v312RegressionFailures
v311RegressionFailures
v310RegressionFailures
v309RegressionFailures
v308RegressionFailures
staticVerdict
finalRuntimeVerdict
runtimeV2Default
realtimeDefault
legacyFallbackDefault
legacyRemovalExecuted
blockers
planCompletionAuthorized
```

---

# 146. Static verdict expected

إذا static/model gates كلها نجحت فقط:

```text
PASS_STATIC_314_PRECUTOVER
/ INHERITED_307_EXCEPTIONS=9
/ ROOM_81_UNCHANGED
/ KILL_SWITCHES_DEFINED
/ WAVES_0_TO_6_DEFINED
/ FAULT_MODELS_PASS
/ V2_DEFAULT_OFF
/ REALTIME_DEFAULT_OFF
/ LEGACY_PRESERVED
/ FINAL_CUTOVER_BLOCKED_RUNTIME
```

---

# 147. Final runtime verdict expected

فقط عند نجاح كل gates:

```text
PASS_FINAL_SYNC_V2_ROLLOUT
/ INHERITED_307_EXCEPTIONS=9
/ ROOM_81_RUNTIME_VERIFIED
/ V313_POSTGRES_APPLIED_STAGING
/ MULTI_DEVICE_CONVERGED
/ REALTIME_OPTIONALITY_PROVEN
/ V2_DEFAULT_ON
/ LEGACY_RUNTIME_RETIRED
/ PLAN_v304_v314_COMPLETE
```

---

# 148. Meaning of static PASS

يعني فقط:

> آليات fault injection/rollout/kill-switch/ownership/legacy-retirement gates موجودة ومثبتة model/static، regressions صفر، ولا تم إجراء cutover غير مثبت.

لا يعني:

```text
production rollout occurred
server migration applied
multi-device real runtime passed
legacy safe to remove
V2 safe default-on
```

---

# 149. Meaning of final PASS

يعني:

> تم إثبات المزامنة V2 عبر runtime متعدد الأجهزة، faults حقيقية/مناسبة، tenant/RLS، recovery، Realtime optionality، staged rollout؛ ثم عطّل Legacy وأزيل runtime authority القديم دون فقد intent أو duplicate effects.

---

# 150. Required implementation order

ينفذ بالترتيب:

```text
A. verify v313 ZIP/hash/counts
B. verify SESSION_313 + plan + verification hashes
C. record inherited runtime blocker exactly
D. freeze Room81 + all historical SQL hashes
E. carry forward exactly 9 inherited exceptions
F. inventory current feature flags
G. inventory all legacy sync surfaces
H. inventory 34 aggregate sensitivities/owners
I. define six independent kill switches
J. define rollout policy + seven waves
K. define ownership state machine
L. define safe rollback semantics
M. implement shadow comparator with zero side effects
N. implement rollout telemetry fields privacy-safe
O. implement deterministic fault harness
P. implement network fault models
Q. implement clock-skew models
R. implement process-death models
S. implement data ordering/duplicate models
T. implement multi-device models
U. implement tenancy/session models
V. implement Realtime ON/OFF parity model
W. create legacy inventory artifact
X. define retirement gate per legacy surface
Y. prove stronger outboxes are never classified legacy
Z. add runtime evidence schema/artifacts
AA. add runtime staging script that fails honestly
AB. add >=600 v314 fixtures
AC. rerun 494 v313 fixtures
AD. rerun 300 v312 fixtures
AE. rerun 325 v311 fixtures
AF. rerun 499 v310 fixtures
AG. rerun 272 v309 fixtures
AH. rerun 137 v308 + MODEL_10K
AI. run v314 verifier twice deterministically
AJ. emit static JSON/MD/report
AK. if runtime unavailable: stop cutover, keep flags OFF + legacy preserved, package static-precutover archive
AL. if runtime available: verify Room80→81 on device
AM. apply/test v313 PostgreSQL migration on staging
AN. run bootstrap/cursor-expiry/pending-recovery/process-death runtime gates
AO. run two-device/RLS/Realtime parity matrix
AP. execute rollout Waves 0→6 in order with evidence
AQ. complete observation/old-client/zero-legacy-use gates
AR. disable legacy fallback
AS. rerun runtime smoke with legacy disabled
AT. retire unreachable legacy runtime code only if all gates pass
AU. rerun static + critical runtime smoke
AV. set V2 default-on only for final qualified configuration
AW. emit final runtime verdict
AX. package archive + SHA
```

---

# 151. Required file-scope discipline

مسموح مبدئيًا تعديل/إضافة:

```text
core/common/.../FeatureFlags.kt
core/common/.../*SyncRollout*

data/sync/.../SyncManager.kt
data/sync/.../SyncWorker.kt
data/sync/.../RealtimeManager.kt
data/sync/.../SyncOperations.kt
data/sync/.../rollout/*
data/sync/.../testing/* or test-only fault harness
data/sync/.../recovery/SyncHealthSnapshot.kt   # rollout diagnostics only

data/network/... legacy sync files            # only for gated retirement, not redesign

data/preferences/... legacy sync preference keys/store # only after retirement gates

app/.../sync presentation bridge               # routing only, no UI redesign
feature/... SyncParticipant adapters           # ownership routing only

docs/sync/*v314*
tools/*v314*
scripts/*v314*
verification/report artifacts
```

---

# 152. Protected files

الأصل أن تبقى byte-identical ما لم fault fix مثبت يمنع 314:

```text
UnifiedSyncContract.kt
UnifiedSyncAggregateRegistry.kt
UnifiedSyncPushWire.kt
UnifiedSyncPullWire.kt
UnifiedSyncBootstrapWire.kt
UnifiedSyncRecoveryRegistry.kt
AppDatabase.kt
MigrationCatalog.kt
AppDatabaseMigrations80To81.kt
schema81.json
v305/v309/v310/v312/v313 SQL
financial/inventory domain business writers
```

أي تغيير semantics يحتاج `BLOCKED_SCOPE_DRIFT` بدل توسيع الجلسة بصمت.

---

# 153. Protected contract facts

```text
contract family = verto-unified-sync
contract version = 1
aggregate count = 34
Room = 81
Realtime = hint only
server revision = ordering authority
opaque cursor = cursor authority
outbox = durable mutation intent
```

---

# 154. Forbidden file-scope drift

ممنوع:

```text
UI redesign
new business features
schema redesign
financial rule changes
inventory costing changes
Optimal redesign
new aggregate types
new Gradle dependencies merely for rollout
RemoteConfig/Firebase dependency addition without necessity
```

---

# 155. Fault harness production isolation

Fault injection code لا يجوز أن يكون قابلًا للتفعيل في production user flow بخطأ config بسيط.

الأفضل:

```text
test source set
internal debug boundary
explicit staging-only provider
```

Final verifier يجب أن يثبت `productionFaultInjectionEnabledCount=0`.

---

# 156. Shadow path production safety

Shadow mode لا يرسل mutations.

أي POST/RPC mutation من shadow:

```text
FAIL_SHADOW_WRITE_SIDE_EFFECT
```

---

# 157. Rollout config fail-closed

إذا rollout config ناقصة/غير صالحة:

قبل V2 ownership:

```text
stay legacy authoritative
```

بعد V2 write ownership:

```text
enter V2_PAUSED_SAFE
```

لا تختار writer عشوائيًا.

---

# 158. Manual sync behavior during rollout

Manual sync يجب أن يحترم نفس ownership/flags مثل Worker.

ممنوع manual button يتجاوز kill switches أو يشغل legacy fullSync بينما worker على V2 لنفس aggregate.

---

# 159. Periodic sync behavior during rollout

Periodic WorkManager wake لا يقرر ownership بنفسه؛ يقرأ rollout policy authoritative snapshot.

---

# 160. Realtime behavior during rollout

Realtime لا يفتح aggregate ownership؛ فقط wake/hint للـV2 path المسموح أصلًا.

---

# 161. Recovery behavior during rollout

إذا V2 slice دخل `RECOVERY_REQUIRED`:

```text
pause unsafe expansion
run 313 recovery
preserve outboxes
resume same ownership only after READY
```

لا fallback timestamp pull لإصلاح recovery.

---

# 162. Reconciliation behavior during rollout

Manifest mismatch في Wave 2/3/4 يمنع التوسع إذا غير مفسر.

Reconciliation لا تقدم cursor.

---

# 163. Legacy retirement and Room residue

يجوز إبقاء أعمدة schema قديمة dormant إذا حذفها يتطلب Room 82.

هذه لا تعتبر runtime legacy إذا:

```text
no production reader/writer authority
no network path depends on them
classified DORMANT_SCHEMA_RESIDUE
```

314 لا ترفع Room فقط لتنظيف الأعمدة.

---

# 164. Final production caller gate

قبل `LEGACY_RUNTIME_RETIRED` يجب verifier يثبت:

```text
production reachable timestamp pull callers = 0
production reachable DataStore deletion queue sync callers = 0
production reachable legacy dirty network writers = 0
production reachable legacy fullSync callers = 0
production reachable old numeric cursor authority = 0
```

باستثناء code kept deliberately unreachable for one disabled observation build؛ عند final removal يجب 0 runtime reachable.

---

# 165. Final stronger-owner gate

بعد Legacy retirement:

```text
7 durable outbox/attachment owners from v313 preservation artifact remain preserved
```

لا تعتبرهم legacy cleanup.

---

# 166. Final Realtime-disabled gate

بعد final cutover، أعد تشغيل critical convergence smoke مع:

```text
REALTIME_HINTS=OFF
```

PASS إلزامي.

---

# 167. Final offline gate

على جهاز V2 default-on:

```text
create durable mutation offline
restart process if possible
come online
```

PASS:

```text
same mutation id reaches terminal authoritative state once
```

---

# 168. Final timeout-after-commit gate

على aggregate مالي أو inventory stronger واحد على الأقل في runtime staging:

```text
server commits
client loses response
client retries
```

PASS:

```text
server business effect count = 1
```

---

# 169. Final cross-tenant gate

قبل PASS_FINAL:

```text
crossTenantReadLeakCount = 0
crossTenantWriteLeakCount = 0
crossTenantRecoveryLeakCount = 0
crossTenantRealtimeLeakCount = 0
```

---

# 170. Final plan-completion authority

`planCompletionAuthorized=true` فقط إذا:

```text
finalRuntimeVerdict startsWith PASS_FINAL
legacy retirement gate passed
Realtime optionality passed
multi-device convergence passed
```

Static PASS alone:

```text
planCompletionAuthorized=false
```

---

# 171. Report honesty — static mode

إذا runtime غير منفذ، التقرير يجب أن يقول صراحة:

```text
The v314 rollout and fault-injection controls are statically/model verified.
Final multi-device/runtime cutover has not been proven.
V2 remains default OFF and Legacy remains available.
```

---

# 172. Report honesty — final mode

ممنوع كتابة:

```text
production proven
all real-world networks proven
zero future divergence guaranteed
```

حتى بعد staging runtime.

الصحيح:

```text
Required staging/runtime gates defined by SESSION_314 passed for the tested matrix.
```

---

# 173. No silent partial PASS

`PASS_FINAL` ممنوع إذا تحقق أي واحد:

```text
v313 SQL not applied in runtime environment
Room migration not runtime verified
multi-device runtime not executed
Realtime optionality not executed
RLS adversarial test not executed
one critical duplicate effect
one unexplained divergence
one cursor drift
one cross-tenant leak
one unsupported payload cursor advance
one stronger outbox removed
one pending legacy delete intent lost
legacy retired while old clients still depend on it
legacy runtime use > 0
observation evidence absent
V2 default-on before gates
one new waiver
one regression 313→308
```

---

# 174. Static packaging

إذا انتهت الجلسة عند static/pre-cutover:

```text
Verto-v314-source-of-truth.zip
Verto-v314-source-of-truth.zip.sha256
```

مسموح، لكن داخل التقرير/verification:

```text
finalRuntimeVerdict = BLOCKED_RUNTIME_REQUIRED
planCompletionAuthorized = false
legacyRemovalExecuted = false
```

---

# 175. Final packaging integrity

بعد packaging:

1. compute SHA-256.
2. test ZIP open.
3. count entries/Kotlin files.
4. verify required v314 artifacts.
5. verify Room still 81.
6. verify schema81 unchanged unless explicitly documented blocker (normally unchanged).
7. verify no v314 SQL migration.
8. verify v305/v309/v310/v312/v313 SQL unchanged.
9. verify contract/registry unchanged.
10. verify >=600 v314 fixtures.
11. verify 494/300/325/499/272/137 regressions.
12. verify no production fault injection enabled.
13. verify kill-switch count >=6.
14. verify seven rollout waves.
15. verify legacy inventory UNKNOWN=0.
16. if static mode: verify defaults OFF/OFF/legacy ON.
17. if final mode: verify runtime evidence complete and legacy retirement gates.
18. scan secrets/cache/build artifacts.
19. write SHA sidecar.

---

# 176. Mandatory artifacts inside v314 archive

```text
SESSION_314_FINAL.md
VERTO_SYNC_CUTOVER_VERIFICATION_v314.json
VERTO_SYNC_CUTOVER_VERIFICATION_v314.md
VERTO_SYNC_RUNTIME_EVIDENCE_v314.json
VERTO_SYNC_RUNTIME_EVIDENCE_v314.md
docs/sync/VERTO_SYNC_FAULT_MATRIX_v314.csv
docs/sync/VERTO_SYNC_MULTI_DEVICE_MATRIX_v314.csv
docs/sync/VERTO_SYNC_ROLLOUT_MATRIX_v314.csv
docs/sync/VERTO_SYNC_LEGACY_INVENTORY_v314.csv
docs/sync/VERTO_SYNC_KILL_SWITCHES_v314.md
docs/sync/VERTO_SYNC_CUTOVER_POLICY_v314.md
docs/sync/VERTO_SYNC_LEGACY_RETIREMENT_v314.md
tools/verify_sync_cutover_v314.py
tools/test_sync_cutover_verification_v314.py
scripts/verify-v314-sync-cutover.sh
scripts/run-v314-runtime-staging.sh
Verto-v314-report.md
```

---

# 177. Final acceptance questions — baseline/static

قبل `PASS_STATIC_314_PRECUTOVER` يجب الإجابة نعم:

1. هل input ZIP SHA صحيح؟
2. هل entries = 1820؟
3. هل production Kotlin = 1191؟
4. هل SESSION_313 SHA صحيح؟
5. هل v313 verification SHA صحيح؟
6. هل v313 staticGatesPassed=true؟
7. هل blocker الموروث موثق دون إخفائه؟
8. هل inherited exceptions exactly 9؟
9. هل new314WaiverCount=0؟
10. هل Room بقي 81؟
11. هل schema81 بلا drift؟
12. هل لا توجد v314 SQL migration؟
13. هل historical SQL بلا drift؟
14. هل registry بقي 34؟
15. هل kill switches >=6؟
16. هل flags dependencies fail closed؟
17. هل rollout waves = 7؟
18. هل ownership single-writer مثبت؟
19. هل shadow mode بلا Room mutation؟
20. هل shadow بلا cursor mutation؟
21. هل shadow بلا outbox mutation؟
22. هل rollback بعد V2 write لا يعيد legacy replay؟
23. هل fault matrix تشمل 34 classes؟
24. هل network faults مغطاة؟
25. هل clock skew مغطى؟
26. هل process death مغطى؟
27. هل pagination/duplicate/reordering مغطى؟
28. هل multi-device models مغطاة؟
29. هل finance/inventory duplicate effect = 0؟
30. هل tenant/session races مغطاة؟
31. هل Realtime optionality model PASS؟
32. هل legacy inventory لا يحتوي UNKNOWN؟
33. هل stronger outboxes مصنفة non-legacy؟
34. هل retirement gate يمنع deletion queue loss؟
35. هل old-client gate موجود؟
36. هل observation gate موجود؟
37. هل runtime evidence schema تمنع fake PASS؟
38. هل >=600 v314 fixtures PASS؟
39. هل 494/494 v313 regression PASS؟
40. هل 300/300 v312 PASS؟
41. هل 325/325 v311 PASS؟
42. هل 499/499 v310 PASS؟
43. هل 272/272 v309 PASS؟
44. هل 137/137 v308 + MODEL_10K PASS؟
45. هل verifier deterministic مرتين؟
46. هل V2 default بقي OFF؟
47. هل Realtime default بقي OFF؟
48. هل Legacy fallback بقي ON في static-only build؟
49. هل التقرير لا يدعي runtime؟

أي `لا` تمنع static PASS.

---

# 178. Final acceptance questions — runtime/cutover

قبل `PASS_FINAL_SYNC_V2_ROLLOUT` يجب الإجابة نعم:

1. هل Gradle/Android runtime gate نفذ ونجح؟
2. هل Room 80→81 migration اختبرت فعليًا؟
3. هل v313 PostgreSQL migration طبقت ونجحت على staging؟
4. هل bootstrap runtime نجح؟
5. هل cursor-expiry recovery runtime نجح؟
6. هل pending mutation full-resync runtime نجح؟
7. هل process-death runtime نجح؟
8. هل timeout-after-server-commit exactly-once effect ثبت؟
9. هل multi-device runtime نفذ على جهازين/عميلين؟
10. هل same-client concurrent edit converged؟
11. هل delete/edit converged according to policy؟
12. هل long-offline recovery converged؟
13. هل invoice/payment ordering safe؟
14. هل inventory movement conflict safe؟
15. هل cash/expense exactly-once؟
16. هل shipment state-machine safe؟
17. هل logout/login stale results blocked؟
18. هل org switch stale work blocked؟
19. هل stale Realtime channel blocked؟
20. هل RLS cross-tenant tests رفضت الوصول؟
21. هل Realtime ON/OFF final state متطابق؟
22. هل Wave 0 نجحت؟
23. هل Wave 1 نجحت؟
24. هل Wave 2 shadow divergence unexplained = 0؟
25. هل Wave 3 non-financial V2 write نجحت؟
26. هل Wave 4 finance/inventory نجحت؟
27. هل Wave 5 Realtime نجحت؟
28. هل Wave 6 default-on gate نجحت؟
29. هل cursor drift = 0؟
30. هل unexplained divergence = 0؟
31. هل critical duplicate effect = 0؟
32. هل observation window موجودة؟
33. هل legacy runtime use = 0؟
34. هل لا توجد clients مدعومة تحتاج legacy؟
35. هل pending legacy deletion queues empty/migrated؟
36. هل legacy disabled smoke نجح؟
37. هل stronger outboxes بقيت؟
38. هل timestamp pull authority = 0 بعد retirement؟
39. هل DataStore deletion sync authority = 0 بعد retirement؟
40. هل generic dirty network authority = 0 بعد retirement؟
41. هل legacy fullSync production reachability = 0؟
42. هل numeric legacy cursor authority = 0؟
43. هل final Realtime-OFF smoke نجح؟
44. هل final offline→online smoke نجح؟
45. هل regressions 313→308 ما زالت صفر؟
46. هل new314WaiverCount=0؟
47. هل final report يصف حدود runtime بدقة؟

أي `لا` تمنع final PASS.

---

# 179. Plan completion

الخطة `v304→v314` لا تعتبر مكتملة عند static-only packaging.

تعتبر مكتملة فقط عندما:

```text
multi-device convergence = proven in required runtime matrix
Realtime disabled parity = proven
V2 default-on gate = passed
Legacy runtime authority = retired safely
planCompletionAuthorized = true
```

---

# 180. الخلاصة النهائية للعقد

314 يجب أن تنهي المشروع بإحدى حقيقتين فقط:

### الحالة A — البيئة لا تسمح runtime الكامل

```text
V2 rollout controls exist.
Fault models and ownership gates pass statically.
Room remains 81.
No historical migration changes.
V2 and Realtime remain default OFF.
Legacy remains available.
Final cutover is explicitly blocked on runtime evidence.
```

### الحالة B — runtime/rollout الكامل نُفذ ونجح

```text
Two clients converge under network/process/time/data faults.
Retries after uncertain commits do not duplicate financial or inventory effects.
Tenant/session transitions cannot leak cross-tenant state.
Realtime is proven optional for correctness.
Recovery/bootstrap works on the applied v313 server surface.
Rollout advances Wave 0→6 without unexplained divergence or cursor drift.
V2 becomes default-on only after those gates.
Legacy is disabled, observed unused, then retired without losing pending intent.
Stronger outboxes remain intact.
Room stays 81 and unified-sync contract remains version 1.
```

إذا لم يتحقق runtime proof، ممنوع تحويل `314-S` إلى “الخطة اكتملت” بالصياغة فقط.
