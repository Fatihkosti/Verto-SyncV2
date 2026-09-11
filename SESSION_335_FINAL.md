# SESSION_335_FINAL — Cash & Expenses Sync, Performance and Convergence

## 0. صفة العقد

هذا عقد تنفيذ مغلق النطاق للجلسة **335** فقط.

الهدف النهائي:

> جعل الصندوق والمصروفات قابلة للتزامن متعدد الأجهزة دون أن يكتب العميل رصيدًا مشتقًا فوق السيرفر، وإزالة المسارات O(N)، وإثبات التقارب والأداء على تاريخ بيانات كبير.

هذه الجلسة هي المكملة مباشرة لـ **SESSION_334_FINAL**.

لا تبدأ من v333.
لا تبدأ من v331.
لا تعتمد على تقرير قديم بدل ناتج 334 الفعلي.

---

# 1. Source of Truth الإلزامي

المدخل المقبول:

```text
actual output of Session 334
```

قبل أي تعديل:

1. سجل اسم ZIP المدخل.
2. سجل SHA-256.
3. سجل Room schema version.
4. سجل حالة `SESSION_334`.
5. سجل `handoff335Authorized`.
6. سجل exact changed-file manifest.
7. شغّل بوابات 334 الموروثة التي تسمح بها البيئة.
8. أثبت أن invariants المالية لـ334 ما زالت قائمة.

إذا كان المدخل ليس ناتج 334:

```text
SESSION_335 = FAIL_WRONG_INPUT
```

إذا كان:

```text
handoff335Authorized = false
```

فلا يبدأ التنفيذ.

إذا كان 334:

```text
BLOCKED_ENVIRONMENT
```

يجوز المتابعة فقط حسب سياسة المشروع الحالية، لكن:

```text
NO BUILD PASS INHERITED
NO TEST PASS INHERITED
NO SOURCE_OF_TRUTH PASS INHERITED
```

---

# 2. Invariants الموروثة من 334 — ممنوع كسرها

335 لا تعيد تصميم صحة المال.

يجب أن تبقى صحيحة:

```text
EXPENSE_AMOUNT_AUTHORITY = MINOR_UNITS
CASH_BALANCE_AUTHORITY = MINOR_UNITS
EXPENSE_INSERT_ATOMIC = true
EXPENSE_UPDATE_ATOMIC = true
EXPENSE_VOID_ATOMIC = true
EXPENSE_VOID_IDEMPOTENT = true
UPDATE_REFUND_DISTINCT_FROM_VOID_REFUND = true
LINKED_EXPENSE_LANDED_COST_ATOMIC = true
LANDED_COST_ALLOCATION_PRESERVES_MONEY = true
CASH_OVERDRAFT_DECISION_FIXED_POINT = true
FINANCIAL_PERMISSION_FAILURE_FAILS_CLOSED = true
MONTH_BOUNDARY_CALENDAR_CORRECT = true
UI_CANNOT_REPORT_REJECTED_WRITE_AS_SUCCESS = true
```

أي regression في واحد منها:

```text
SESSION_335 = FAIL_334_REGRESSION
```

إلا إذا أصلحت regression المثبت فقط دون توسيع نطاق 334.

---

# 3. Historical Baseline Drift Guard

التحليل الذي كشف مشكلات الصندوق في v331 أثبت دلاليًا:

```text
CashSyncParticipant:
  legacy PUSH invokes pushCashRegister()
  legacy PUSH invokes pushCashMovements()

pushCashRegister():
  reads local cash register
  upserts cash_register server row

pushCashMovements():
  reads all local movements
  filters some conflicts
  upserts entire list

pullCashMovements():
  uses timestamp watermark
  not the unified revision cursor

UnifiedStrongerSyncBridge:
  CASH_REGISTER = SERVER_AUTHORITATIVE_NO_CLIENT_PUSH
  CASH_MOVEMENT = APPEND_ONLY command/ledger fact

server migration v310:
  verto_apply_cash_movement_v310
  locks server cash register
  applies amountMinor delta
  writes movement
  updates server register
  returns authoritative balanceBefore/After
  emits secondary CASH_REGISTER change

FeatureFlags baseline:
  v2 disabled by default
  financial v2 disabled by default
  legacy fallback enabled
```

335 لا تفترض بقاء هذه التفاصيل كما هي بعد 332/333/334.

قبل patch:

```text
for each baseline fact:
    CONFIRMED
    ALREADY_FIXED
    CHANGED_SEMANTICS
```

أصلح المعنى الحالي، لا أرقام أسطر v331.

---

# 4. Scope الجلسة 335

335 مسؤولة حصريًا عن:

1. server-authoritative cash register semantics.
2. إيقاف client balance snapshot push.
3. إنهاء أو عزل legacy `pushCashRegister`.
4. إنهاء O(N) `pushCashMovements`.
5. تحويل cash movement sync إلى durable delta/outbox.
6. ضمان أن CASH_MOVEMENT يستخدم owner/stronger route الصحيح.
7. تقارب expense/cash عبر unified revision pull.
8. منع dual-writer بين Legacy وV2.
9. فهارس Room للمصروفات والاستعلامات المالية المثبتة.
10. pagination/batching والحدود الهيكلية.
11. large-history verification.
12. multi-device/idempotency/concurrency tests.
13. sync recovery/replay tests.
14. performance regression gates.
15. observability محدودة لمسار sync المالي فقط.

---

# 5. خارج نطاق 335

ممنوع:

```text
UI redesign
expense UX redesign
new accounting module
general sync rewrite
new global event bus
new command bus
new database abstraction
new financial framework
inventory redesign
invoice redesign
reports redesign
server architecture rewrite
remote config product rollout redesign
Realtime redesign unrelated to cash/expense
full app performance project
```

335 تصلح الصندوق والمصروفات فقط.

---

# 6. Authority Model النهائي

بعد 335:

```text
CASH_MOVEMENT:
  client may create durable movement command
  server applies movement idempotently
  server computes authoritative balance before/after
  server commits canonical cash register

CASH_REGISTER:
  server authoritative
  client read model only
  client MUST NOT push arbitrary balance snapshot
```

القاعدة:

```text
cash register balance is derived server state
not a client-owned mutable fact
```

---

# 7. ممنوع Client Balance Push

بعد 335 يجب ألا يستطيع المسار العادي استدعاء:

```text
pushCashRegister()
```

بطريقة ترسل:

```text
local balance -> server cash_register upsert
```

المقبول أحد حلين فقط:

```text
A. remove legacy register push from participant
B. retain function for compatibility but make normal runtime unreachable and fail-closed
```

يفضل A إن لم يوجد consumer مشروع.

لا يقبل:

```text
timestamp last-write-wins on cash register
```

ولا:

```text
if local updatedAt newer then overwrite server
```

لأن الرصيد مشتق من ledger.

---

# 8. Legacy `pushCashMovements` Retirement

المسار الذي يفعل:

```text
getAllMovementsSync()
then upload all rows
```

يجب ألا يبقى ضمن normal sync ownership.

بعد 335:

```text
normal cash movement push rows read from history = 0
```

الرفع يكون فقط من durable pending intents/outbox.

لا يقبل حل:

```text
getAllMovementsSync().takeLast(100)
```

لأنه يظل غير صحيح دلاليًا.

لا يقبل:

```text
updatedAt > lastPushTime
```

إذا لم يكن له idempotent durable write identity.

---

# 9. Stronger/Owner Route Verification

قبل التنفيذ حدد owner الفعلي لـ:

```text
EXPENSE
CASH_MOVEMENT
CASH_REGISTER
```

المطلوب في التقرير:

```text
aggregate
local outbox owner
push mapper
server endpoint/RPC
receipt owner
pull mapper
recovery owner
```

إذا CASH_MOVEMENT ينتج outbox في مكان بينما push engine يمرره عبر generic validator الذي يرفض owner310:

```text
FAIL_OWNER_ROUTE_MISMATCH
```

يجب إصلاح route بشكل صريح.

---

# 10. CASH_MOVEMENT Outbox Contract

كل حركة جديدة يجب أن تمتلك:

```text
organizationId
aggregateType = CASH_MOVEMENT
aggregateId
mutationId
writeId
sourceType
sourceId
sourceVersion
movementType
amountMinor
createdAt
operation = COMMAND | REVERSE
```

وفق semantics الحالية.

ممنوع payload authoritative يعتمد على:

```text
amount: Double
balanceBefore from client
balanceAfter from client
```

يجوز إبقاء compatibility projections، لكن السيرفر لا يثق بها.

---

# 11. Signed Delta Convention

يجب تجميد convention واحدة:

```text
amountMinor signed delta
```

مثال:

```text
cash in  +10000
cash out -10000
```

أو إذا contract الحالي يفصل type + absolute amount:

```text
canonical server mapper converts to signed delta exactly once
```

لكن ممنوع وجود تفسيرين مختلفين بين:

```text
expense
manual add
manual deduct
invoice sale
invoice purchase
void/reversal
```

يجب اختبار كل نوع.

---

# 12. Server Contract — لا تخمين

335 تستخدم contract موجودًا فقط إذا أثبتته.

يجب التحقق من وجود:

```text
verto_apply_cash_movement_v310
```

أو البديل الحالي المكافئ.

والتحقق أن العقد الفعلي:

1. tenant-scoped.
2. authenticated-safe.
3. idempotent by writeId/mutation identity.
4. row-locks authoritative cash register.
5. applies exact minor-unit delta.
6. creates immutable movement.
7. updates register atomically.
8. returns authoritative materialization.
9. emits or exposes canonical cash register change.
10. rejects duplicate writeId with different identity.

إذا server contract المطلوب غير موجود فعليًا:

```text
SESSION_335 = BLOCKED_SERVER_CONTRACT
```

ولا يُسمح بإعادة تفعيل legacy client balance push كحل.

---

# 13. لا SQL جديد افتراضيًا

القاعدة:

```text
335 Android-first unless existing server contract is insufficient
```

إذا migration v310 الحالية تكفي:

```text
NO NEW SQL
```

إذا اكتُشفت فجوة server حقيقية:

1. وثقها.
2. لا تخمن contract.
3. لا تعدل SQL إلا إذا كان نطاق المشروع يسمح بذلك صراحة.
4. إن لم يسمح:

```text
BLOCKED_SERVER_CONTRACT
```

---

# 14. Expense Sync Ownership

بعد 334 أصبحت expense مالية أدق.

335 يجب أن تضمن أن expense sync لا يعيد:

```text
Double-authoritative payload
legacy dirty push competing with unified outbox
absence-as-delete
timestamp-LWW override of financial lifecycle
```

يجب تحديد:

```text
EXPENSE owner route
```

ثم منع dual writer.

---

# 15. Dual-Writer Prohibition

لنفس aggregate، لا يجوز في نفس rollout أن يعمل:

```text
Legacy writer + V2 writer
```

خصوصًا:

```text
EXPENSE
CASH_MOVEMENT
CASH_REGISTER
```

يجب أن تحتوي rollout policy على ownership decision حاسمة.

مثال:

```text
if financialV2Owner == true:
    legacy expense/cash PUSH = disabled
    unified push = enabled
```

ولا:

```text
try V2
then also legacy as fallback
```

بعد احتمال نجاح V2.

---

# 16. Fallback Rule

Legacy fallback يجوز فقط قبل انتقال ownership.

بعد أن يصبح financial aggregate V2-owned:

```text
fallback MUST NOT mutate same financial aggregate
```

عند فشل V2:

```text
preserve outbox
surface retry/recovery state
do not client-upsert cash balance
```

---

# 17. Cutover Granularity

لا تجعل cutover المالي العام يغيّر كل المالية إذا 335 لا تحتاج ذلك.

إذا architecture تسمح:

```text
cash/expense-specific ownership
```

أفضل من تغيير عالمي يجر:

```text
invoice
payment
commission
inventory
```

إلى rollout غير مختبر.

إذا current policy لا تسمح granular ownership، وثق الأثر واختبر كل owner المتأثر قبل التفعيل.

---

# 18. Feature Flag Safety

لا تغيّر defaults إلى ON بلا دليل.

المطلوب:

```text
implementation ready
cutover eligibility explicit
production default remains fail-safe until admission
```

إذا التفعيل يحتاج Staging two-device evidence، لا تتحايل عليه.

335 يمكن أن تكمل الكود وتبقى:

```text
CUTOVER_READY = true
PRODUCTION_ENABLED = false
```

إذا runtime evidence غير متوفر.

---

# 19. Unified Pull — المصدر المقبول

Cash/expense remote convergence يجب أن يعتمد على:

```text
revision/cursor change feed
```

وليس:

```text
client wall clock watermark
created_at >= lastPulledAt
```

في المسار V2-owned.

يجب استخدام الـengine الحالي إن كان يحقق:

```text
opaque cursor
tenant scope
strict revisions
atomic page apply
cursor CAS
bounded page size
recovery on stale cursor
```

---

# 20. Timestamp Pull Retirement

`KEY_LAST_PULLED_CASH_MVTS` أو المكافئ:

- يجوز إبقاؤه لمسار Legacy غير المالك.
- ممنوع أن يكون authority لمسار V2 المالي.

بعد cutover:

```text
cash convergence correctness does not depend on device clock
```

---

# 21. Cash Register Pull Semantics

CASH_REGISTER remote apply:

```text
server snapshot wins
```

لكن يجب حماية local pending intent.

المبدأ:

```text
pending movement command is not erased by a stale local rewrite
authoritative register comes from receipt/change feed
```

إذا local UI تعرض optimistic balance، يجب تحديد reconciliation semantics دون خلق snapshot push.

---

# 22. Receipt-Driven Reconciliation

بعد ACK لحركة:

```text
server authoritative balanceBeforeMinor
server authoritative balanceAfterMinor
serverVersion
serverRevision
```

يجب أن تصل إلى local state عبر الآلية الحالية:

```text
receipt authoritative payload
or revision feed
```

لا يجوز اعتبار:

```text
HTTP 200 only
```

كافيًا إذا payload/receipt identity لا يطابق mutation.

---

# 23. Idempotency

إعادة إرسال نفس:

```text
writeId
mutationId
aggregateId
payload identity
```

يجب ألا تضيف حركة ثانية.

اختبر:

```text
first send -> APPLIED
same send -> REPLAYED/NO_OP
server balance changes once
movement row count changes once
```

---

# 24. Idempotency Conflict

نفس writeId مع payload مختلف:

```text
must fail
```

النتيجة المطلوبة:

```text
REQUIRES_REVIEW or deterministic conflict
```

ممنوع:

```text
last write wins
```

---

# 25. Multi-Device Concurrency

اختبار إلزامي:

```text
initial server balance = 100.00

device A:
  expense -30.00

device B:
  manual deduct -20.00

both submit concurrently
```

النتيجة:

```text
server final = 50.00
exactly 2 movement facts
each writeId unique
no lost update
both devices converge to 50.00
```

يجب ألا تعتمد النتيجة على ترتيب وصول client snapshots.

---

# 26. Concurrent Same-Source Replay

اختبر جهازين يحملان نفس offline action بعد restore/replay إن كان write identity مشتركة.

المطلوب:

```text
same logical mutation => one server effect
```

إذا restored backups تولد identity جديدة لنفس action، وثق ذلك كخطر ولا تعتبر replay idempotent.

---

# 27. Expense + Cash Convergence

سيناريو:

```text
Device A creates expense 75.25 offline
local:
  expense present
  cash effect present
  durable outbox present

network returns:
  cash movement command accepted
  expense command accepted
```

بعد pull:

```text
expense same semantic identity
cash movement one fact
cash register authoritative
no duplicate local movement
no dirty-loop
outboxes acknowledged
```

---

# 28. Partial Remote Success

اختبر:

```text
expense remote succeeds
cash movement transient fails
```

أو العكس حسب command ordering.

المطلوب:

```text
dependency/order contract deterministic
pending intent preserved
retry cannot double-apply successful part
local UI exposes pending/recovery state if relevant
```

لا يجوز التحول إلى:

```text
legacy fallback push balance
```

---

# 29. Command Ordering

إذا expense + cash movement ضمن batch مترابط:

يجب تجميد:

```text
commandBatchId
commandOrder
dependsOnMutationId
```

بشكل يتوافق مع server invariants.

المطلوب اختبار:

```text
dependency pending
dependency acknowledged
dependency rejected
dependency missing
```

---

# 30. Outbox Boundedness

الـpush يجب أن يكون bounded.

الحدود الحالية إن بقيت:

```text
DEFAULT_MAX_MUTATIONS = 50
MAX_MUTATIONS = 500
MAX_SCAN = 500
```

يجوز تغييرها فقط بدليل.

Invariant:

```text
single sync invocation does not scan or send entire financial history
```

---

# 31. Pull Boundedness

الحدود الحالية إن بقيت:

```text
DEFAULT_PAGE_SIZE = 100
MAX_PAGE_SIZE = 200
DEFAULT_MAX_PAGES = 5
DEFAULT_MAX_CHANGES = 1000
```

تظل bounded.

لا ترفع الحدود لتخفي backlog.

---

# 32. More-Available Scheduling

إذا push/pull يعيد:

```text
MORE_AVAILABLE
```

يجب أن توجد scheduling semantics تمنع:

```text
single giant blocking run
```

وتضمن eventual catch-up.

لا تنشئ busy loop.

---

# 33. Expense Query Index Strategy

بعد 334 تحقق من SQL الفعلي.

الاستعلامات المهمة غالبًا:

```text
ACTIVE expenses ordered by date
ACTIVE expenses date range
SUM amount_minor by date range
GROUP BY category within date range
```

لا تضف indexes عشوائيًا.

استخدم:

```text
EXPLAIN QUERY PLAN
```

قبل وبعد.

الحد الأدنى المتوقع إذا query shape يطابق:

```text
(lifecycle_state, date)
```

وقد يلزم covering index مدروس لفئة/category.

---

# 34. Index Acceptance

Index يقبل فقط إذا:

1. مستخدم من query planner.
2. يخدم query حقيقية.
3. لا يكرر index قائمًا.
4. لا يضيف write amplification بلا قيمة.
5. migration test يمر.
6. schema export يطابق.

---

# 35. Full Scan Gate

للاستعلامات المالية المحددة:

```text
month expenses
range total
category summary
recent cash movements
pending outbox
```

يجب إثبات query plan.

لا تقبل:

```text
SCAN expenses
```

على جدول التاريخ الكامل إذا كان يمكن استخدام index مناسب.

يجوز scan للنتيجة المحدودة بعد index seek.

---

# 36. Cash Movement Query Indexes

راجع queries الجديدة من 334/335.

مرشحات محتملة:

```text
createdAt
writeId
sourceType + sourceId
movementType + referenceId
```

لا تضفها كلها افتراضيًا.

أضف فقط ما تثبته:

```text
actual DAO lookup + EXPLAIN
```

---

# 37. Room Migration

إذا 335 تضيف indexes أو schema metadata:

```text
schema_after_335 = schema_after_334 + 1
```

فقط إذا schema تغير فعليًا.

لا hardcode رقمًا قبل قراءة ناتج 334.

إذا 334 خرجت schema 82:

```text
335 likely 82 -> 83
```

لكن التنفيذ يجب أن يتحقق.

---

# 38. Migration Requirements

أي migration في 335 يجب أن:

```text
preserve all expenses
preserve all cash movements
preserve cash register
preserve outbox
preserve inbox/cursor
preserve financial identities
preserve amountMinor
preserve reversal identity
```

Indexes فقط لا تبرر إعادة بناء بيانات مالية.

---

# 39. Large-History Dataset

أضف fixture/benchmark data لا يقل عن:

```text
100,000 expenses
250,000 cash movements
25,000 outbox rows historical/terminal mix
5,000 pending/retry outbox rows
```

إذا instrumentation environment لا تتحمل الحجم:

```text
use scalable host-side/query-plan fixture
```

لكن لا تدّعي device latency دون device benchmark.

---

# 40. Performance Metrics — Structural

هذه إلزامية حتى لو الجهاز غير متاح:

```text
legacy full cash-history read per normal push = 0
client cash-register snapshot writes per normal push = 0
push candidates <= configured scan bound
sent mutations <= invocation bound
pull page size <= max page bound
changes/invocation <= max changes bound
expense range query uses intended index
outbox eligible query uses intended index
```

---

# 41. Performance Metrics — Runtime

إذا بيئة benchmark متاحة، سجل:

```text
dataset size
device/emulator
warmup
iterations
median
p95
max
```

للعمليات:

```text
monthly expense query
expense category aggregation
recent cash movements
pending outbox claim
100-change pull apply
50-mutation push preparation
```

لا تضع أرقام latency نهائية قبل القياس.

---

# 42. Relative Performance Gate

إذا لا توجد baseline latency موثوقة:

اعتمد:

```text
no full-history O(N)
query plan indexed
bounded work per invocation
allocation count bounded
```

ويمكن إضافة مقارنة قبل/بعد على نفس البيئة.

---

# 43. Memory Gate

ممنوع في sync path:

```text
getAllMovementsSync()
getAllExpensesSync()
```

ثم filter في Kotlin لأغراض push المعتادة.

الاستثناء فقط:

```text
explicit backup/export/admin tool
```

خارج normal sync.

---

# 44. Allocation Gate

لا تنشئ:

```text
history.map { DTO }
```

لكل cash history في كل sync.

التحويل يكون على batch pending فقط.

---

# 45. Retry Semantics

Transient failure:

```text
intent remains durable
attemptCount increments
nextAttemptAt bounded backoff
no duplicate local mutation
no balance snapshot fallback
```

---

# 46. Authentication Failure

401/403:

```text
financial intent preserved
no destructive terminal drop
session/auth state surfaced
no legacy fallback
```

---

# 47. Validation Failure

Permanent validation failure:

```text
REJECTED or REQUIRES_REVIEW
```

حسب policy.

لا يظل في infinite retry.

---

# 48. Rate Limit

429:

```text
retryable
bounded backoff
no busy loop
no fallback writer
```

---

# 49. Scope/Tenant Safety

كل push/pull:

```text
organizationId from trusted session/scope
```

لا من payload editable.

اختبر:

```text
wrong org in local outbox
cross-org dependency
cross-org remote change
```

كلها fail closed.

---

# 50. Cash Register Tenant Identity

إذا local Room يحتفظ:

```text
id = "main"
```

بدون organizationId داخل entity، يجب إثبات isolation architecture.

المقبول فقط إذا:

```text
database/session lifecycle guarantees one trusted organization at a time
and org switch clears/re-hydrates correctly
```

إذا لم يوجد هذا الضمان:

```text
FAIL_TENANT_ISOLATION
```

ولا تؤجل بصمت.

---

# 51. Organization Switch Test

اختبر:

```text
org A cash = 100
switch to org B cash = 20
```

ثم:

```text
B UI never shows A balance
B sync never pushes A movement
return A restores A authoritative state correctly
```

هذا اختبار إلزامي لأن entity المحلي قد يستخدم `"main"`.

---

# 52. Bootstrap

جهاز جديد بلا local cash state:

```text
bootstrap/recovery
```

يجب أن يجلب:

```text
authoritative cash register
required movement history window/state
expense state
cursor baseline
```

دون client push.

---

# 53. Recovery After Local DB Loss

إذا local DB أعيد بناؤه:

```text
server remains authority
```

لا يجوز أن يبدأ client برصيد 0 ثم يرفع 0 للسيرفر.

اختبار:

```text
server balance nonzero
fresh install
sync
server balance unchanged
local converges to server
```

---

# 54. Recovery After Cursor Loss

إذا cursor مفقود أو stale:

```text
BOOTSTRAP_REQUIRED / RECOVERY_REQUIRED
```

ثم recovery صحيح.

ممنوع الرجوع إلى timestamp cash pull كauthority صامت.

---

# 55. Realtime

Realtime إن كان موجودًا:

```text
hint only
```

لا يصبح source of truth.

الفقد أو التكرار في Realtime لا يغيّر correctness لأن revision pull يعالج الفجوة.

---

# 56. Observability — Minimal

أضف counters/events فقط لمسار 335:

```text
cash_v2_push_attempted
cash_v2_push_applied
cash_v2_push_replayed
cash_v2_push_retry
cash_v2_push_rejected
cash_v2_push_review
cash_v2_pull_applied
cash_v2_recovery_required
legacy_cash_push_invoked
cash_server_authority_violation
```

استخدم mechanism المشروع الحالي.

لا تضف analytics SDK جديدًا.

---

# 57. Forbidden Metric

ممنوع إرسال:

```text
raw financial amounts
client names
invoice notes
expense notes
PII
```

في diagnostic telemetry غير المخصص لذلك.

---

# 58. Legacy Invocation Tripwire

إذا بقي legacy function للتوافق، أضف test/guard يثبت:

```text
when cash/expense V2 owner active:
  pushCashRegister calls = 0
  pushCashMovements calls = 0
  pushExpenses legacy calls = 0
```

---

# 59. No Silent Downgrade

إذا V2 server contract غير متاح:

```text
do not silently downgrade financial writer
```

المطلوب:

```text
BLOCKED / retry / explicit state
```

بحسب rollout policy.

---

# 60. Required Unit Tests — Routing

أضف tests:

```text
CASH_REGISTER classified server-authoritative
CASH_REGISTER client push rejected
CASH_MOVEMENT owner route accepted through stronger path
generic owner310 route rejected
expense owner route deterministic
financial V2 ownership disables legacy writer
legacy mode does not invoke V2 before eligibility
```

---

# 61. Required Unit Tests — Payload

اختبر:

```text
0.01 movement
10.10 movement
999999.99 movement
negative outgoing delta
positive incoming delta
writeId stable
source identity stable
no client authoritative balance fields required
```

Assertions على `Long`.

---

# 62. Required Unit Tests — Idempotency

```text
same write replay
same mutation receipt replay
same write different payload
same aggregate different write
stale lease result
receipt mutation mismatch
receipt aggregate mismatch
```

---

# 63. Required Unit Tests — Retry

```text
network exception
401
403
429
timeout
server retryable
permanent validation
conflict
```

تحقق من state transitions.

---

# 64. Required Integration Tests — Cash

سيناريوهات:

```text
manual add
manual deduct
expense insert
expense increase
expense decrease
expense void
invoice cash sale if shared cash manager touches same ledger
invoice purchase if shared cash manager touches same ledger
```

الهدف ليس إعادة اختبار كل invoice domain؛ بل إثبات أن كل cash source يستخدم نفس server-authoritative ledger contract.

---

# 65. Required Integration Test — No O(N)

Fake DAO أو spy:

```text
250,000 historical cash movements
1 pending movement
```

normal push:

```text
historical movement rows loaded = 0
pending rows loaded <= batch bound
sent = 1
```

---

# 66. Required Integration Test — Large Pending Batch

```text
5,000 pending
```

invocation 1:

```text
sent <= 50 default
MORE_AVAILABLE
```

تكرار scheduling يصل في النهاية إلى caught up.

لا OOM.

---

# 67. Required Integration Test — Pull Pagination

مثال:

```text
1,250 remote changes
page size 100
max pages 5
```

run 1:

```text
<= 500 changes
MORE_AVAILABLE
```

runs التالية تكمل دون:

```text
duplicate apply
cursor skip
revision gap
```

---

# 68. Required Multi-Device Tests

على الأقل:

```text
A and B both online
A offline then reconnect
B offline then reconnect
A/B concurrent outgoing
A/B concurrent incoming
same logical replay
different movements same initial balance
device clock skew +12h
device clock skew -12h
```

النتيجة لا تعتمد على clocks.

---

# 69. Required Expense Convergence Tests

```text
create on A appears on B
update on A converges
void on A converges once
B cannot resurrect VOID with stale local state
category/month summaries after pull are correct
cash effect remains one movement
```

---

# 70. Required Recovery Tests

```text
cursor missing
cursor stale
inbox anchor missing
database empty
expired lease
app killed after server applied but before local ACK
app killed after local lease before network
```

كلها eventual convergence.

---

# 71. Kill-Point Matrix

اختبر على الأقل:

```text
before lease
after lease
before request
after server apply before receipt persist
after receipt before terminal state
during pull before transaction
during pull after inbox insert
during pull before cursor CAS
```

المطلوب:

```text
no money duplicated
no mutation lost
cursor never skips unapplied change
```

---

# 72. Expense Index Tests

أنشئ DB fixture ثم:

```text
EXPLAIN QUERY PLAN
```

للـqueries الأساسية.

التقرير يجب أن يحتوي plan text.

لا يكفي وجود annotation `@Index`.

---

# 73. Migration Test 335

إذا schema تغير:

```text
N -> N+1
```

اختبر DB تحتوي:

```text
ACTIVE expenses
VOID expenses
decimal legacy projection
amountMinor values
cash register nonzero
250+ cash movements
pending outbox
acknowledged outbox
sync cursor
sync inbox
```

بعد migration:

```text
row counts preserved
money preserved
identities preserved
indexes exist
query plan uses expected index
```

---

# 74. Backup/Restore Compatibility

لأن BackupManager يحتوي cash register/movements:

335 يجب أن تتحقق أن restore لا يحول client إلى server authority.

بعد restore:

```text
restored local state = cache/intents according to policy
next sync must reconcile from server
must not upload restored register snapshot
```

اختبر backup قديم إن كان contract الحالي يدعمه.

---

# 75. No Dirty Loop

بعد remote apply:

```text
isDirty/pending state must not be recreated
```

اختبر:

```text
pull authoritative expense
pull authoritative cash movement
pull authoritative cash register
```

ثم normal push:

```text
0 echo mutations
```

إلا إذا protocol يتطلب ACK منفصل موثق.

---

# 76. Append-Only Cash Movement

remote cash movement:

```text
immutable
```

إذا نفس ID وصل بمحتوى مختلف:

```text
FAIL / REQUIRES_REVIEW
```

لا `REPLACE`.

---

# 77. Register Snapshot Freshness

cash register snapshot يجب أن يأتي من server revision order.

لا تعتمد على:

```text
remoteUpdatedAt > local.updatedAt
```

كمحدد صحة وحيد داخل V2.

---

# 78. Expense Lifecycle Convergence

`VOID` لا يتحول إلى physical delete إلا إذا domain الحالي ينص صراحة.

sync must preserve:

```text
lifecycleState
voidedAt
voidReason
reversalWriteId
amountMinor
```

---

# 79. Forbidden Deletes

CASH_MOVEMENT:

```text
NO CLIENT DELETE
```

CASH_REGISTER:

```text
NO CLIENT DELETE
```

أي generic DELETE route:

```text
FAIL
```

---

# 80. Static Performance Guard

أضف script/test يفشل إذا عاد normal cash sync إلى patterns مثل:

```text
getAllMovementsSync()
postgrest["cash_register"].upsert from local balance
timestamp watermark as V2 authority
```

يجب أن يكون semantic قدر الإمكان، لا grep هش فقط.

---

# 81. Mutation Evidence

اختبارات 335 يجب أن تكشف mutations التالية:

```text
re-enable pushCashRegister in V2 owner
replace outbox batch with getAllMovementsSync
remove writeId
allow duplicate write replay
ignore receipt identity mismatch
advance cursor before domain apply
disable org-scope check
turn server-authoritative register into client LWW
remove expense query index
remove MORE_AVAILABLE bound
fallback to legacy after V2 may have applied
```

كل mutation:

```text
must make detecting test fail
```

---

# 82. Existing Gates Must Remain Green

لا تضعف بوابات 319–334.

حسب الأسماء الفعلية:

```text
architecture_guard
dependency_gate
persistence_guard
contract_compatibility_guard
maintainability_testability
feature_scalability_admission
technical_debt_ratchet
differential_quality
kotlin_quality
documentation
design_system
financial_integrity_334
```

---

# 83. Expected Production Areas

قد تشمل:

```text
feature/payment/.../CashSyncParticipant.kt
data/network/.../SyncCash.kt
data/network/.../UnifiedStrongerSyncBridge.kt
data/sync/.../push/*
data/sync/.../pull/*
data/sync/.../rollout/*
data/database/.../dao/ExpenseDao.kt
data/database/.../dao/CashRegisterDao.kt
data/database/.../entity/*
data/database/.../migration/*
app/.../feature/expenses/bridge/*
core/common/.../FeatureFlags.kt
```

لكن هذه ليست allowlist تلقائية.

قبل التعديل:

```text
freeze exact changed-file allowlist
```

---

# 84. Supabase Files

لا تعدل:

```text
supabase/migrations/*
```

إلا بعد إثبات أن existing server contract غير كاف وأن تغيير server داخل scope المصرح.

الأصل:

```text
reuse v310 authority
```

---

# 85. Forbidden Refactors

ممنوع:

```text
move all sync packages
rename entire financial layer
repository-wide formatting
replace Supabase client
replace Room
introduce new scheduler
new dependency
new serialization library
new money library
```

---

# 86. Execution Order

الترتيب الإلزامي:

```text
1. verify v334 artifact
2. verify 334 invariants
3. freeze current cash/expense ownership map
4. verify actual server contract
5. verify owner310/stronger push routing
6. freeze change allowlist
7. remove client register authority
8. retire O(N) cash movement legacy push
9. wire durable movement route
10. prevent dual writer
11. align expense sync ownership
12. align revision pull
13. add/prove indexes
14. add migration if needed
15. add focused tests
16. add multi-device/concurrency tests
17. add performance structural tests
18. run inherited gates
19. run Gradle verification
20. produce admission report
```

Fail-fast.

---

# 87. Server Preflight Evidence

التقرير يجب أن يثبت من code/schema artifacts المتاحة:

```text
cash apply endpoint name
authorization behavior
idempotency identity
unique constraint/index
row lock behavior
minor-unit arithmetic
authoritative receipt
secondary register change
```

إذا runtime server inspection غير متاح:

```text
SERVER_RUNTIME_VERIFICATION = BLOCKED_ENVIRONMENT
```

ولا تدّع أنه deployed.

---

# 88. Gradle Verification

عندما تسمح البيئة:

```bash
./gradlew --no-daemon testDebugUnitTest
./gradlew --no-daemon detekt
./gradlew --no-daemon lintDebug
./gradlew --no-daemon assembleDebug
```

وشغّل focused tests للموديولات المتأثرة.

---

# 89. Database Verification

إذا migration تغيرت:

```text
Room migration test mandatory
schema export diff mandatory
persistence gate mandatory
```

---

# 90. Device/Staging Verification

لإعلان cutover production-ready:

```text
two-device staging test
network interruption
replay
concurrent movement
fresh install bootstrap
org switch
```

إذا غير متاح:

```text
CUTOVER_RUNTIME = BLOCKED_ENVIRONMENT
```

---

# 91. Environment Block Rule

إذا implementation مكتملة، والفحوص الساكنة/الوحدات المتاحة تمر، لكن Staging/device/server runtime غير متاح:

```text
SESSION_335_STATIC_IMPLEMENTATION = PASS
SESSION_335 = BLOCKED_ENVIRONMENT
PRODUCTION_CUTOVER = NOT_AUTHORIZED
```

لا تقل:

```text
multi-device PASS
server deployment PASS
production cutover PASS
```

بدون تشغيل فعلي.

---

# 92. Failure Conditions

الجلسة تفشل إذا بقي أي من التالي في owner V2:

```text
client pushes cash register balance snapshot
cash push scans full history
cash correctness depends on device timestamp watermark
same write can apply twice
legacy + V2 both mutate same financial aggregate
V2 failure silently falls back to legacy writer
owner310 outbox routed through incompatible generic validator
cash register can be overwritten by stale client
cross-org cash data can leak
remote apply recreates dirty push loop
cursor advances past unapplied page
expense range stays unindexed without evidence
large-history push is O(N)
```

---

# 93. Required End-State Invariants

يجب أن تصبح:

```text
CASH_REGISTER_SERVER_AUTHORITATIVE = true
CLIENT_CASH_REGISTER_PUSH = false
CASH_MOVEMENT_APPEND_ONLY_IDEMPOTENT = true
CASH_MOVEMENT_FULL_HISTORY_PUSH = false
CASH_PUSH_SOURCE = DURABLE_PENDING_OUTBOX
CASH_PULL_SOURCE = REVISION_CURSOR
DEVICE_CLOCK_REQUIRED_FOR_CASH_CORRECTNESS = false
EXPENSE_SYNC_DUAL_WRITER = false
CASH_SYNC_DUAL_WRITER = false
V2_TO_LEGACY_FINANCIAL_SILENT_FALLBACK = false
PUSH_WORK_PER_INVOCATION_BOUNDED = true
PULL_WORK_PER_INVOCATION_BOUNDED = true
EXPENSE_RANGE_QUERY_INDEXED = true
MULTI_DEVICE_LOST_UPDATE = false
ORG_SCOPE_FAILS_CLOSED = true
334_FINANCIAL_INVARIANTS_PRESERVED = true
```

---

# 94. Required Evidence Artifacts

أنشئ على الأقل:

```text
docs/finance/SESSION_335_SYNC_AUTHORITY.md
docs/finance/SESSION_335_OWNERSHIP_MATRIX.md
docs/finance/SESSION_335_CONVERGENCE_MATRIX.md
docs/finance/SESSION_335_PERFORMANCE_EVIDENCE.md
docs/finance/SESSION_335_QUERY_PLANS.md
docs/finance/SESSION_335_SERVER_CONTRACT_EVIDENCE.md
docs/finance/SESSION_335_MIGRATION.md          # if schema changed
```

---

# 95. Ownership Matrix Template

التقرير النهائي يجب أن يحتوي:

| Aggregate | Local source | Push owner | Server owner | Pull owner | Legacy writer enabled? |
|---|---|---|---|---|---|
| EXPENSE | ... | ... | ... | ... | NO/YES |
| CASH_MOVEMENT | ... | ... | ... | ... | NO |
| CASH_REGISTER | read model | NONE | server | unified pull | NO |

أي dual ownership غير موثق:

```text
FAIL
```

---

# 96. Performance Matrix Template

| Operation | Dataset | Before complexity | After complexity | Bound/Index | Result |
|---|---:|---|---|---|---|
| cash push | 250k history | O(N) | O(batch) | <= push batch | PASS/FAIL |
| expense month | 100k expenses | scan/index | indexed range | query plan | PASS/FAIL |
| category summary | 100k expenses | ... | ... | query plan | PASS/FAIL |
| cash recent | 250k movements | ... | ... | LIMIT + index | PASS/FAIL |
| pull | 1250 changes | bounded pages | bounded pages | <=1000/run | PASS/FAIL |

---

# 97. Convergence Matrix Template

| Scenario | Expected final cash | Movement count | Duplicate? | Both devices converge? |
|---|---:|---:|---|---|
| A -30, B -20 from 100 | 50 | 2 | NO | YES |
| replay same write | unchanged after first | 1 | NO | YES |
| offline expense | exact | 1 cash fact | NO | YES |
| app kill after server apply | exact | 1 | NO | YES |

---

# 98. Completion Matrix

| Objective | Required | Result | Evidence |
|---|---:|---|---|
| 334 invariants preserved | YES | PASS/FAIL | regression suite |
| server authority verified | YES | PASS/BLOCKED/FAIL | contract evidence |
| client register push removed | YES | PASS/FAIL | routing test |
| full-history cash push removed | YES | PASS/FAIL | large-history test |
| durable movement route | YES | PASS/FAIL | outbox test |
| owner310 routing correct | YES | PASS/FAIL | routing test |
| dual writer prevented | YES | PASS/FAIL | rollout test |
| revision pull for V2 cash | YES | PASS/FAIL | cursor test |
| idempotent replay | YES | PASS/FAIL | replay test |
| multi-device no lost update | YES | PASS/BLOCKED/FAIL | concurrency test |
| org isolation | YES | PASS/FAIL | org-switch test |
| expense indexes | YES | PASS/FAIL | EXPLAIN |
| large-history boundedness | YES | PASS/FAIL | structural test |
| migration | IF NEEDED | PASS/BLOCKED/FAIL | Room test |
| inherited gates | YES | PASS/FAIL | gate outputs |
| Gradle verification | YES | PASS/BLOCKED/FAIL | commands |
| staging cutover | FOR PROD | PASS/BLOCKED/FAIL | runtime evidence |

---

# 99. Definition of Done

335 تعتبر DONE فقط عندما يصبح مستحيلاً سلوكيًا أن يحدث واحد من التالي دون فشل اختبار/بوابة:

```text
1. stale device overwrites authoritative cash register.
2. every sync reloads 250k cash movements.
3. same writeId changes cash twice.
4. V2 succeeds remotely then legacy fallback applies again.
5. expense/cash state depends on phone clock.
6. device A and B lose one concurrent movement.
7. fresh install uploads zero register over real server balance.
8. org B displays or pushes org A cash state.
9. remote pull creates local dirty echo loop.
10. cursor advances after failed financial apply.
11. month expense query returns via uncontrolled full-history scan.
12. 334 fixed-point/reversal invariants regress.
```

---

# 100. Required Final Status Block

التقرير النهائي يجب أن يطبع:

```text
SESSION_335 = PASS | PASS_STATIC_RUNTIME_BLOCKED | BLOCKED_SERVER_CONTRACT | BLOCKED_ENVIRONMENT | FAIL

input_v334_verified = PASS | FAIL
handoff335_authorized = PASS | FAIL
financial_334_regression = PASS | FAIL

cash_register_server_authority = PASS | FAIL
client_register_push_retired = PASS | FAIL
cash_full_history_push_retired = PASS | FAIL
cash_outbox_delta_route = PASS | FAIL
owner310_stronger_route = PASS | FAIL
expense_sync_owner = PASS | FAIL
financial_dual_writer_guard = PASS | FAIL
legacy_silent_fallback_guard = PASS | FAIL

cash_pull_revision_cursor = PASS | FAIL
device_clock_independence = PASS | FAIL
cash_idempotency = PASS | FAIL
cash_replay = PASS | FAIL
cash_concurrency = PASS | BLOCKED_ENVIRONMENT | FAIL
two_device_convergence = PASS | BLOCKED_ENVIRONMENT | FAIL
org_switch_isolation = PASS | FAIL
fresh_install_bootstrap = PASS | BLOCKED_ENVIRONMENT | FAIL
recovery_after_apply_before_ack = PASS | FAIL

expense_query_indexes = PASS | FAIL
query_plan_evidence = PASS | FAIL
large_history_bounded_push = PASS | FAIL
bounded_pull = PASS | FAIL
performance_runtime = PASS | BLOCKED_ENVIRONMENT | NOT_RUN

server_contract_static = PASS | FAIL
server_contract_runtime = PASS | BLOCKED_ENVIRONMENT | FAIL
production_cutover = AUTHORIZED | NOT_AUTHORIZED

room_schema_before = <n>
room_schema_after = <n>
migration_test = PASS | BLOCKED_ENVIRONMENT | NOT_REQUIRED | FAIL

focused_tests = PASS | BLOCKED_ENVIRONMENT | FAIL
mutation_evidence = PASS | BLOCKED_ENVIRONMENT | FAIL
architecture_guard = PASS | FAIL
dependency_gate = PASS | FAIL
persistence_guard = PASS | FAIL
contract_compatibility_guard = PASS | FAIL
maintainability_testability = PASS | FAIL
feature_scalability_admission = PASS | FAIL
technical_debt_ratchet = PASS | FAIL
kotlin_quality = PASS | FAIL
documentation_gate = PASS | FAIL

detekt = PASS | BLOCKED_ENVIRONMENT | FAIL
lint = PASS | BLOCKED_ENVIRONMENT | FAIL
full_unit_tests = PASS | BLOCKED_ENVIRONMENT | FAIL
assemble_debug = PASS | BLOCKED_ENVIRONMENT | FAIL
source_of_truth_admission = PASS | BLOCKED_ENVIRONMENT | FAIL
```

---

# 101. Final Handoff Rule

بعد 335:

إذا:

```text
SESSION_335 = PASS
```

فميزة الصندوق والمصروفات تعتبر مغلقة من ناحية العقدين:

```text
334 = Financial Integrity & Atomicity
335 = Sync + Performance + Convergence
```

ولا تُفتح جلسة 336 لنفس المشاكل إلا إذا:

```text
new runtime evidence
new production incident
new benchmark regression
new server contract change
```

---

# END OF SESSION_335_FINAL
