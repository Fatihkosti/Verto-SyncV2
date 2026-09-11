# SESSION_336_FINAL — Cash Multi-Device Convergence & Expense Dependency Repair

## 0. صفة العقد

هذا عقد تنفيذ مغلق النطاق للجلسة **336** فقط.

الجلسة إصلاح تصحيحي مباشر لأخطاء P0 المكتشفة بعد تنفيذ 335.

الهدف:

> إصلاح تقارب حركات الصندوق متعددة الأجهزة دون كسر append-only semantics، وضمان أن Refund الناتج من UPDATE/VOID للمصروف لا يصل إلى السيرفر قبل mutation المصروف الذي يعتمد عليه.

الجلسة ليست إعادة كتابة لـ335.

الجلسة لا تعيد فتح 334.

---

# 1. Source of Truth الإلزامي

المدخل المقبول:

```text
Verto-v335-final-admission-blocked-environment.zip
```

أو artifact مطابق له byte-for-byte إذا أعيدت تسميته.

قبل أي تعديل:

1. سجل اسم ZIP.
2. سجل SHA-256.
3. سجل Room schema version.
4. سجل Git-less file manifest.
5. سجل نتائج 335 الفعلية.
6. سجل إخفاقات 335 التي أدت إلى 336.
7. لا تعتمد على تقرير 335 وحده.
8. اقرأ الكود الفعلي.

إذا المدخل ليس ناتج 335:

```text
SESSION_336 = FAIL_WRONG_INPUT
```

---

# 2. سبب فتح 336

تم اكتشاف خطأين P0 بعد 335.

## P0-A — Multi-device cash convergence conflict

المسار المحلي:

```text
CashMovementSyncWriter.record()
```

يبني حركة محلية ويثبت فيها:

```text
balanceBeforeMinor
balanceAfterMinor
```

اعتمادًا على الرصيد المحلي وقت إنشاء الحركة.

السيرفر:

```text
verto_apply_cash_movement_v310()
```

يعيد حساب:

```text
balanceBeforeMinor
balanceAfterMinor
```

حسب الترتيب الحقيقي الذي طبقت به العمليات على السيرفر.

ثم Pull الحالي:

```text
UnifiedStrongerSyncChangeApplier.applyCashMovement()
```

يفعل:

```kotlin
if (old == null) insert
else require(old == row) { "IMMUTABLE_CASH_MOVEMENT_CONFLICT" }
```

هذا يجعل الاختلاف المشروع في projection السببي للحركة يبدو كأنه immutable conflict.

مثال:

```text
Server initial = 100.00

Device A local:
  -30.00
  before=100.00
  after=70.00

Device B local:
  -20.00
  before=100.00
  after=80.00

Server order:
  A first  => before=100.00 after=70.00
  B second => before=70.00  after=50.00
```

عند وصول authoritative B إلى B:

```text
local B:
  before=100.00
  after=80.00

server B:
  before=70.00
  after=50.00
```

الهوية المالية للحركة نفسها صحيحة:

```text
id
writeId
movementType
amountMinor
referenceId
source identity
```

لكن projection المرتبطة بترتيب ledger مختلفة.

الحالة الحالية تنتهي إلى:

```text
IMMUTABLE_CASH_MOVEMENT_CONFLICT
```

وهذا خطأ.

---

# 3. P0-B — Refund dependency gap

في مسارات:

```text
expense UPDATE decrease
expense VOID
```

يتم إنشاء حركة عكسية للصندوق عبر:

```text
CashRegisterManager.reverseMovementMinor()
```

لكن identity الحالية للحركة العكسية لا تمرر:

```text
dependsOnMutationId
```

وبالتالي يمكن أن يصبح outbox الخاص بالـRefund مؤهلًا للإرسال قبل أن تصبح mutation المصروف:

```text
ACKNOWLEDGED
```

هذا يسمح لحالة مثل:

```text
refund applied on server
expense VOID/UPDATE rejected
```

أو:

```text
refund applied first
app/server interruption before expense transition
```

فتنشأ حالة مالية غير منطقية.

---

# 4. النتيجة المطلوبة

بعد 336 يجب أن تصبح:

```text
CASH_MOVEMENT_IMMUTABLE_INTENT = true
CASH_MOVEMENT_SERVER_PROJECTION_RECONCILABLE = true
MULTI_DEVICE_CASH_CONVERGENCE = true
LEGITIMATE_SERVER_REORDER_DOES_NOT_CONFLICT = true
SEMANTIC_MOVEMENT_DRIFT_STILL_CONFLICTS = true

EXPENSE_CREATE_CASH_DEPENDENCY = true
EXPENSE_INCREASE_CASH_DEPENDENCY = true
EXPENSE_DECREASE_REFUND_DEPENDENCY = true
EXPENSE_VOID_REFUND_DEPENDENCY = true

REFUND_BEFORE_EXPENSE_ACK = impossible
DEPENDENCY_CYCLE = impossible
SELF_DEPENDENCY = impossible
```

---

# 5. خارج نطاق 336

ممنوع:

```text
UI redesign
expense UX changes
new financial module
new event sourcing framework
new sync engine
new outbox implementation
new server protocol family
new cash table
new accounting ledger redesign
new database library
new money library
new scheduler
general performance work
general indexing work
new feature flags
global sync cutover
```

---

# 6. القرار المعماري الحاسم

يجب الفصل بين نوعين من البيانات داخل `CashRegisterMovementEntity`.

## 6.1 Immutable financial intent

هذه حقول يجب أن تتطابق بين local/server:

```text
id
movementType
amountMinor
referenceId
note
sourceType
sourceId
sourceVersion
writeId
createdAt
```

مع مراعاة أن:

```text
amount
```

هو compatibility projection مشتق من `amountMinor` وليس authority مستقلًا.

## 6.2 Server-authoritative causal projection

هذه ليست جزءًا من immutable command identity:

```text
balanceBeforeMinor
balanceAfterMinor
balanceBefore
balanceAfter
```

لأنها تعتمد على ترتيب السيرفر للـledger.

---

# 7. قاعدة immutable الجديدة

ممنوع استخدام:

```kotlin
old == row
```

كمعيار وحيد لحركة Cash Movement موجودة محليًا.

يجب إضافة semantic comparison صريح.

مثال دلالي مقبول:

```kotlin
sameCashMovementIntent(old, remote)
```

يفحص:

```text
old.id == remote.id
old.movementType == remote.movementType
old.amountMinor == remote.amountMinor
old.referenceId == remote.referenceId
old.note == remote.note
old.sourceType == remote.sourceType
old.sourceId == remote.sourceId
old.sourceVersion == remote.sourceVersion
old.writeId == remote.writeId
old.createdAt == remote.createdAt
```

لا يقارن:

```text
balanceBefore
balanceBeforeMinor
balanceAfter
balanceAfterMinor
amount Double
```

`amount` يجب اشتقاقه/التحقق منه من `amountMinor`.

---

# 8. Amount authority

بعد 336:

```text
amountMinor = authoritative monetary delta
```

و:

```text
amount = compatibility projection
```

عند remote apply:

```text
canonicalAmount = Money.ofMinor(remote.amountMinor).toLegacyDouble()
```

إذا payload يحتوي `amount` مختلفًا عن canonical minor units اختلافًا جوهريًا:

```text
SERVER_PROTOCOL_INCONSISTENCY
```

أو يجب تجاهل raw Double واستخدام canonical derivation.

لا تجعل `Double` سبب conflict إذا `amountMinor` صحيح.

---

# 9. Server projection reconciliation

إذا:

```text
old == null
```

أدخل remote movement.

إذا:

```text
old != null
sameCashMovementIntent(old, remote) == true
```

فالسلوك المطلوب:

```text
authoritative balances from server replace provisional local balances
```

أي:

```text
old.balanceBeforeMinor = remote.balanceBeforeMinor
old.balanceAfterMinor  = remote.balanceAfterMinor
old.balanceBefore      = canonical(remote.balanceBeforeMinor)
old.balanceAfter       = canonical(remote.balanceAfterMinor)
old.amount             = canonical(remote.amountMinor)
```

ولا تغير immutable intent fields.

---

# 10. Semantic conflict يبقى fail-closed

إذا نفس movement id أو write identity لكن أحد immutable fields مختلف:

مثال:

```text
same id
same writeId
different amountMinor
```

أو:

```text
same id
different sourceId
```

أو:

```text
same id
different movementType
```

النتيجة:

```text
IMMUTABLE_CASH_MOVEMENT_CONFLICT
```

لا تستخدم server-wins صامت على semantic intent.

---

# 11. DAO contract المطلوب

يجب أن يوجد DAO API واضح للمصالحة.

مثال دلالي:

```text
reconcileMovementAuthoritativeProjection(...)
```

المسؤولية:

```text
UPDATE cash_register_movements
SET
  amount = canonicalAmount,
  balanceBefore = authoritativeBefore,
  balanceBeforeMinor = authoritativeBeforeMinor,
  balanceAfter = authoritativeAfter,
  balanceAfterMinor = authoritativeAfterMinor
WHERE id = :id
```

بعد semantic verification.

الأفضل أن تكون:

```text
read + semantic verify + projection update
```

داخل نفس Room transaction المملوكة للـPull Engine.

---

# 12. ممنوع full-row REPLACE

ممنوع حل المشكلة بـ:

```text
@Insert(REPLACE)
```

أو:

```text
DELETE old
INSERT remote
```

لأن هذا يخفي semantic drift ويكسر append-only reasoning.

---

# 13. No schema change by default

336 لا تحتاج schema جديدة لحل P0-A.

الحقول موجودة بالفعل.

الأصل:

```text
room_schema_after_336 = room_schema_before_336
```

إذا تم تغيير schema دون ضرورة موثقة:

```text
FAIL_SCOPE_EXPANSION
```

---

# 14. Cash register reconciliation

بعد تطبيق remote `CASH_MOVEMENT`:

لا يجب تعديل register اعتمادًا على local before/after.

الـregister يبقى:

```text
SERVER_AUTHORITATIVE
```

ويأتي من:

```text
CASH_REGISTER secondary change
```

أو authoritative receipt/read model حسب protocol الحالي.

ممنوع:

```text
set local register = remote movement.balanceAfter
```

إذا كان protocol يمكن أن يحتوي حركات أخرى قبل وصول register secondary change.

---

# 15. Change ordering

يجب فحص ترتيب:

```text
secondary CASH_REGISTER change
primary CASH_MOVEMENT change
```

في server change log.

لكن correctness لا يجوز أن يعتمد على وصولهما UI-perfect order.

بعد اكتمال transaction/pull page:

```text
cash register = server authoritative value
cash movement projection = server authoritative causal values
```

---

# 16. Receipt semantics

Push Engine في APPLIED/REPLAYED/NO_OP حاليًا يقر outbox ثم يعتمد على unified pull للت materialization.

336 لا تحول هذا إلى architecture جديدة.

لكن يجب اختبار:

```text
server applies movement
app dies before local authoritative echo
retry returns immutable receipt
pull later reconciles projection
```

لا duplicate.

---

# 17. Server SQL rule

لا تعديل SQL في 336 افتراضيًا.

الدليل الحالي:

```text
verto_apply_cash_movement_v310
```

يحسب:

```text
v_before = server register
v_after = v_before + delta
```

ويرجع authoritative payload.

كما أن:

```text
verto_apply_sync_mutation
```

يحفظ immutable receipt ويعيد نفس receipt عند replay لنفس mutation hash.

إذًا أصل P0-A في client reconciliation.

الحالة الافتراضية:

```text
NEW_SERVER_SQL = NOT_REQUIRED
```

أي SQL جديد يحتاج دليل عكس ذلك.

---

# 18. Expense dependency model

يجب أن تكون كل Cash mutation المشتقة من Expense مرتبطة بالـExpense mutation التي تبررها.

المصفوفة النهائية:

| Expense operation | Cash effect | Cash dependsOn |
|---|---|---|
| CREATE | EXPENSE cash out | expense CREATE mutation |
| UPDATE increase | additional cash out | expense UPDATE mutation |
| UPDATE same | none | N/A |
| UPDATE decrease | refund cash in | expense UPDATE mutation |
| VOID | refund cash in | expense VOID mutation |

---

# 19. Existing good behavior — ممنوع كسره

في 335 يوجد سلوك جيد لمسارات cash-out:

```text
recordMovementMinor(... sourceType="EXPENSE", writeId=expenseMutation)
```

يؤدي إلى:

```text
dependsOnMutationId = expenseMutation
```

هذا يغطي على الأقل:

```text
CREATE
UPDATE increase
```

336 يجب ألا تكسر ذلك.

---

# 20. Reverse API repair

يجب تعديل:

```text
CashRegisterManager.reverseMovementMinor(...)
```

بحيث يمكن تمرير dependency صريحة.

مثال contract:

```kotlin
reverseMovementMinor(
    originalType,
    amountMinor,
    referenceId,
    writeId,
    sourceType,
    dependsOnMutationId
)
```

أو design مكافئ أوضح.

---

# 21. Expense reverse dependency

في:

```text
ExpenseRepository.refund(...)
```

يجب تمرير:

```text
dependsOnMutationId = expense mutation id
```

لنفس العملية.

في UPDATE decrease:

```text
expenseMutation = mutation
refundMutation != expenseMutation
refund.dependsOnMutationId = expenseMutation
```

في VOID:

```text
expenseMutation = writeId
refundMutation != expenseMutation
refund.dependsOnMutationId = writeId
```

---

# 22. Mutation identity distinction

ممنوع الخلط بين:

```text
writeId
mutationId
dependsOnMutationId
```

القواعد:

```text
Expense outbox mutationId = expenseMutationId

Cash movement:
  movement writeId = stable business write identity
  cash outbox mutationId = its own unique/deterministic cash mutation id
  dependsOnMutationId = expenseMutationId
```

يجب ألا يكون:

```text
cashMutationId == dependsOnMutationId
```

لأن persistence guard يمنع self dependency.

---

# 23. Dependency row may be enqueued later in same transaction

في ExpenseRepository:

قد يحدث:

```text
cash outbox enqueue
then expense outbox enqueue
```

داخل نفس Room transaction.

هذا مسموح فقط لأن eligibility يفحص dependency عند push time.

يجب اختبار:

```text
transaction commit contains both rows
cash row depends on expense row
cash row not eligible until expense ACK
```

---

# 24. Dependency state matrix

Cash refund/cash effect يجب أن يتصرف هكذا:

```text
dependency missing:
  BLOCKED
  no network call

dependency PENDING:
  BLOCKED
  no network call

dependency LEASED:
  BLOCKED
  no network call

dependency RETRY:
  BLOCKED
  no network call

dependency ACKNOWLEDGED:
  eligible

dependency REJECTED:
  blocked/failed deterministically

dependency REQUIRES_REVIEW:
  blocked/failed deterministically
```

لا fallback.

---

# 25. No refund before ACK

اختبار إلزامي:

```text
expense VOID mutation = PENDING
refund cash mutation = PENDING dependsOn VOID
```

شغّل push.

المطلوب:

```text
VOID may be sent
refund must not be sent in same eligibility pass before VOID becomes ACKNOWLEDGED
```

إذا engine يعيد scanning داخل نفس run بعد ACK، يجوز إرسال refund لاحقًا فقط بعد أن يرى dependency ACKNOWLEDGED فعليًا.

---

# 26. Rejection behavior

سيناريو:

```text
expense VOID rejected
refund dependency exists
```

المطلوب:

```text
refund must never apply
refund becomes blocked/failed according to existing dependency policy
local recovery state explicit
```

ممنوع:

```text
refund APPLIED
expense ACTIVE
```

---

# 27. Update-decrease behavior

مثال:

```text
old expense = 100
new expense = 70
refund = +30 cash
```

المطلوب:

```text
Expense UPDATE mutation ACK first
Refund +30 second
```

السيرفر النهائي:

```text
expense = 70
cash += 30
```

exactly once.

---

# 28. Update-increase behavior

مثال:

```text
old expense = 70
new expense = 100
cash effect = -30
```

المطلوب:

```text
Expense UPDATE ACK first
Cash -30 second
```

تحقق أن behavior الموجود في 335 ما زال يحقق ذلك.

---

# 29. Create behavior

مثال:

```text
new expense = 50
cash effect = -50
```

المطلوب:

```text
expense mutation is parent
cash mutation depends on parent
```

لا regression.

---

# 30. VOID behavior

مثال:

```text
ACTIVE 75
VOID
refund +75
```

المطلوب:

```text
VOID ACK
then refund
```

إذا VOID replayed/no-op لنفس reversal identity:

```text
refund also idempotent
```

ولا duplication.

---

# 31. Local transaction atomicity

334 invariant يبقى:

```text
expense state + local cash effect + outbox rows
```

داخل Room transaction واحدة.

336 لا تقسمها.

إذا dependency metadata يفشل:

```text
whole local transaction must roll back
```

---

# 32. Multi-device reconciliation algorithm

الـPull logic المطلوب دلاليًا:

```text
remote = decode authoritative movement
local = find movement by aggregateId

if local == null:
    insert authoritative movement
else:
    if !sameImmutableIntent(local, remote):
        throw IMMUTABLE_CASH_MOVEMENT_CONFLICT

    if authoritativeProjectionDiffers(local, remote):
        update only authoritative projection fields
```

---

# 33. Projection difference is expected

هذه الحالات لا تعتبر conflict:

```text
balanceBeforeMinor differs
balanceAfterMinor differs
balanceBefore Double differs
balanceAfter Double differs
```

طالما immutable intent نفسه.

---

# 34. Semantic difference is not expected

هذه حالات conflict:

```text
movementType differs
amountMinor differs
referenceId differs
sourceType differs
sourceId differs
sourceVersion differs
writeId differs
createdAt differs
note differs
```

إذا `note` في domain الحالي ليست immutable business identity، يجب إثبات ذلك قبل استثنائها.

الأصل: immutable.

---

# 35. CreatedAt rule

السيرفر الحالي يستخدم:

```text
createdAt from client payload
```

لذلك المتوقع تطابقه.

إذا decode fallback يستخدم changedAt بدل createdAt بسبب payload ناقص:

```text
SERVER_PROTOCOL_INCONSISTENCY
```

لا تقبل silent mismatch لحركة V2.

---

# 36. Stable movement id

يجب الحفاظ على:

```text
stableMovementId(org, writeId)
```

كما هو أو equivalent.

لا تولد movement id جديد أثناء reconciliation.

---

# 37. Stable write identity

نفس business mutation replay:

```text
same writeId
same movement id
same semantic intent
```

ولا حركة ثانية.

---

# 38. Server reordering test — A then B

Fixture:

```text
initial=10000 minor

A local:
  delta=-3000
  provisional 10000->7000

B local:
  delta=-2000
  provisional 10000->8000
```

Server applies:

```text
A 10000->7000
B 7000->5000
```

Pull on B:

```text
B row reconciled to 7000->5000
no conflict
```

Final:

```text
B register=5000
```

---

# 39. Server reordering test — B then A

نفس fixture.

Server:

```text
B 10000->8000
A 8000->5000
```

Pull on A:

```text
A row reconciled to 8000->5000
no conflict
```

Final:

```text
A register=5000
```

---

# 40. Three-device convergence

اختبار:

```text
initial 100
A -10
B -20
C +5
```

كل جهاز ينشئ local provisional state من 100.

السيرفر يطبق بترتيب محدد.

بعد pull الكامل:

```text
all devices register=75
all 3 movement rows have server causal projections
no immutable conflict
```

---

# 41. Same-device sequential movements

اختبار:

```text
A -10
A -20
```

إذا local order يطابق server order:

```text
projection already equal
remote apply no-op
```

لا unnecessary write.

---

# 42. Remote-first movement

جهاز B لم ينشئ الحركة محليًا.

عند pull:

```text
old=null
insert exact authoritative movement
```

لا تغيير.

---

# 43. Semantic corruption test

Local:

```text
same id/writeId
amountMinor=-2000
```

Remote:

```text
amountMinor=-3000
```

المطلوب:

```text
IMMUTABLE_CASH_MOVEMENT_CONFLICT
```

---

# 44. Source corruption test

Local:

```text
sourceType=EXPENSE
sourceId=E1
```

Remote:

```text
sourceType=EXPENSE
sourceId=E2
```

المطلوب:

```text
IMMUTABLE_CASH_MOVEMENT_CONFLICT
```

---

# 45. Double projection test

Local amount:

```text
-20.0
amountMinor=-2000
```

Remote raw amount:

```text
-20.000000000000004
amountMinor=-2000
```

يجب ألا تعتمد semantic identity على raw Double.

استخدم minor units.

---

# 46. Authoritative projection update test

Local row:

```text
beforeMinor=10000
afterMinor=8000
```

Remote same intent:

```text
beforeMinor=7000
afterMinor=5000
```

بعد apply:

```text
beforeMinor=7000
afterMinor=5000
before=70.0
after=50.0
amount canonical
```

---

# 47. No register mutation by projection reconciliation

نفس الاختبار السابق.

إذا local register كان:

```text
5000
```

بعد reconcile movement فقط:

```text
register remains 5000
```

الـmovement applier لا يقرر authority للـregister.

---

# 48. Cash register authoritative change test

بعد وصول `CASH_REGISTER`:

```text
balanceMinor = server balance
```

حتى لو local provisional كان مختلفًا.

---

# 49. Pull transaction/cursor safety

إذا projection reconciliation يفشل semantic check:

```text
page transaction fails
cursor must not advance
```

نفس invariant v308.

---

# 50. Dependency test — create

أنشئ Expense CREATE.

افحص outbox:

```text
expenseMutation exists
cashMutation exists
cash.dependsOnMutationId == expenseMutation.mutationId
cash.dependsOnMutationId != cash.mutationId
```

---

# 51. Dependency test — update increase

أنشئ Expense UPDATE زيادة.

المطلوب نفس dependency contract.

---

# 52. Dependency test — update decrease

أنشئ Expense UPDATE نقصان.

المطلوب:

```text
refund movement operation = REVERSE
refund.dependsOnMutationId == expenseUpdateMutation
```

هذه كانت فجوة 335.

---

# 53. Dependency test — void

أنشئ VOID.

المطلوب:

```text
refund movement operation = REVERSE
refund.dependsOnMutationId == expenseVoidMutation
```

هذه كانت فجوة 335.

---

# 54. Dependency test — zero delta

UPDATE لا يغير amount.

المطلوب:

```text
no cash movement
no cash dependency row
expense mutation only
```

---

# 55. Dependency no-cycle test

لا يوجد:

```text
expense depends on cash
cash depends on expense
```

في نفس العملية.

الاتجاه الوحيد:

```text
cash effect -> depends on expense mutation
```

---

# 56. Dependency no-self test

لكل cash outbox:

```text
dependsOnMutationId != mutationId
```

---

# 57. Dependency missing test

احذف/غيّب parent row في fixture.

المطلوب:

```text
cash mutation not sent
reason = DEPENDENCY_MISSING
```

---

# 58. Dependency rejected test

Parent:

```text
REJECTED
```

Child refund:

```text
not sent
```

ولا local fallback.

---

# 59. Dependency retry test

Parent:

```text
RETRY
```

Child:

```text
blocked
```

بعد parent ACK:

```text
child becomes eligible
```

---

# 60. Crash after expense ACK before refund

سيناريو:

```text
expense ACK
app dies
refund still pending
```

restart:

```text
refund becomes eligible
applies once
```

---

# 61. Crash after refund server apply before local ACK

نفس refund write replay.

المطلوب:

```text
server effect once
immutable receipt replay
outbox eventually ACK
no duplicate cash movement
```

---

# 62. Crash after local transaction before network

المطلوب:

```text
both expense and cash outbox rows survive
dependency survives
ordering remains correct
```

---

# 63. Multi-device + refund

سيناريو:

```text
A voids expense and creates refund
B creates unrelated cash movement
```

server ordering قد يجعل refund causal balances تختلف عن A local projection.

بعد pull:

```text
refund movement reconciles
no immutable conflict
expense remains VOID
all devices converge
```

---

# 64. Server-authoritative projections are not command payload authority

Client push payload لـCASH_MOVEMENT يجب أن يبقى معتمدًا على:

```text
movementType
amountMinor
referenceId
note
sourceType
sourceId
sourceVersion
writeId
createdAt
```

ممنوع إضافة:

```text
balanceBeforeMinor
balanceAfterMinor
```

كمطالبات authoritative من العميل.

إذا موجودة legacy compatibility لا يعتمد السيرفر عليها.

---

# 65. No client balance snapshot regression

336 ممنوع أن تعيد:

```text
pushCashRegister()
```

أو:

```text
local balance upsert to server
```

---

# 66. O(N) regression guard

336 ممنوع أن تعيد:

```text
getAllMovementsSync()
```

داخل normal sync push.

---

# 67. 334 invariants الموروثة

يجب الحفاظ على:

```text
EXPENSE_AMOUNT_AUTHORITY = MINOR_UNITS
EXPENSE_INSERT_ATOMIC = true
EXPENSE_UPDATE_ATOMIC = true
EXPENSE_VOID_ATOMIC = true
EXPENSE_VOID_IDEMPOTENT = true
UPDATE_REFUND_DISTINCT_FROM_VOID_REFUND = true
LINKED_EXPENSE_LANDED_COST_ATOMIC = true
CASH_OVERDRAFT_DECISION_FIXED_POINT = true
MONTH_BOUNDARY_CALENDAR_CORRECT = true
UI_CANNOT_REPORT_REJECTED_WRITE_AS_SUCCESS = true
```

---

# 68. 335 invariants الموروثة

يجب الحفاظ على:

```text
CASH_REGISTER_SERVER_AUTHORITATIVE = true
CLIENT_CASH_REGISTER_PUSH = false
CASH_MOVEMENT_FULL_HISTORY_PUSH = false
CASH_PUSH_SOURCE = DURABLE_PENDING_OUTBOX
CASH_PULL_SOURCE = REVISION_CURSOR
DEVICE_CLOCK_REQUIRED_FOR_CASH_CORRECTNESS = false
FINANCIAL_DUAL_WRITER = false
SILENT_LEGACY_FALLBACK = false
PUSH_BOUNDED = true
PULL_BOUNDED = true
```

---

# 69. Required production files — expected

قد تحتاج تعديلات في:

```text
data/operations/src/main/kotlin/com/verto/app/utils/CashRegisterManager.kt
data/operations/src/main/kotlin/com/verto/app/data/repository/ExpenseRepository.kt
data/database/src/main/kotlin/com/verto/app/data/local/dao/CashRegisterDao.kt
data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedStrongerSyncChangeApplier.kt
```

وقد تضيف helper صغيرًا في أقرب package صحيح.

---

# 70. Forbidden production files by default

لا تعدل دون دليل:

```text
Supabase migrations
FeatureFlags
UI screens
ViewModels
invoice domain
inventory domain
Gradle dependencies
Room entity schema
global sync contract
server RPC names
```

---

# 71. Change allowlist

قبل التنفيذ:

1. اكتب exact allowlist.
2. لا تعدل ملفًا خارجه إلا مع documented reason.
3. أي scope creep يظهر في final report.

---

# 72. Test locations — expected

أضف focused tests في أقرب modules:

```text
data/sync/src/test/...
data/operations/src/test/...
data/database/src/androidTest/... only if truly needed
```

يفضل host unit tests لمعظم 336.

---

# 73. Testability helper

يجوز استخراج pure helper مثل:

```text
sameCashMovementIntent()
canonicalAuthoritativeCashProjection()
```

إذا كان ذلك يجعل الاختبارات مباشرة.

لا تنشئ framework.

---

# 74. Required focused test names

يفضل أسماء واضحة مثل:

```text
CashMovementAuthoritativeProjection336Test
ExpenseCashDependency336Test
CashMultiDeviceConvergence336Test
CashMovementSemanticConflict336Test
```

الأسماء يمكن أن تختلف، لكن السلوك إلزامي.

---

# 75. Required test count

حد أدنى:

```text
P0-A convergence/reconciliation: 10 tests
P0-B dependency/order: 10 tests
regression/idempotency/crash: 6 tests
```

إجمالي:

```text
>= 26 focused behavioral assertions/tests
```

لا يحسب grep test كاختبار سلوكي.

---

# 76. Mutation evidence — P0-A

يجب أن تقتل الاختبارات mutations التالية:

```text
M1: restore old == row strict equality
M2: include balanceBeforeMinor in immutable identity
M3: include balanceAfterMinor in immutable identity
M4: allow amountMinor mismatch
M5: allow writeId mismatch
M6: update cash register from movement projection
M7: replace whole row without semantic verification
M8: compare raw Double amount as authority
```

كل واحدة:

```text
DETECTED
```

---

# 77. Mutation evidence — P0-B

يجب أن تقتل:

```text
M9: remove dependsOnMutationId from update-decrease refund
M10: remove dependsOnMutationId from VOID refund
M11: child uses its own mutationId as dependency
M12: child sent while parent PENDING
M13: child sent while parent REJECTED
M14: reverse API drops dependency
```

كل واحدة:

```text
DETECTED
```

---

# 78. ممنوع fake mutation evidence

لا يقبل:

```text
assert True
hardcoded DETECTED
grep-only check claiming runtime behavior
```

Mutation evidence يجب أن يغير السلوك أو source fixture ويجعل test حقيقي يفشل.

---

# 79. Static guard

أضف verifier محدودًا لـ336 يثبت:

```text
no old == row strict cash conflict
reverse dependency field exists
ExpenseRepository passes dependency
no client cash register push
no getAllMovementsSync normal push regression
```

لكن static verifier لا يحل محل tests.

---

# 80. Server evidence

أنشئ:

```text
docs/finance/SESSION_336_SERVER_EVIDENCE.md
```

يثبت:

```text
server computes causal balances
server immutable receipt replay exists
CASH_REGISTER secondary change exists
no SQL change required
```

مع paths/line references من repository.

---

# 81. Convergence evidence

أنشئ:

```text
docs/finance/SESSION_336_CONVERGENCE_EVIDENCE.md
```

يشمل:

```text
2-device A then B
2-device B then A
3-device
same-device no-op
semantic corruption
projection reconcile
```

---

# 82. Dependency evidence

أنشئ:

```text
docs/finance/SESSION_336_EXPENSE_DEPENDENCY_EVIDENCE.md
```

يشمل المصفوفة:

```text
CREATE
UPDATE increase
UPDATE equal
UPDATE decrease
VOID
```

---

# 83. 334/335 regression evidence

أنشئ ملفًا غير فارغ:

```text
docs/finance/SESSION_336_REGRESSION_EVIDENCE.json
```

يحتوي على نتائج حقيقية.

ممنوع 0-byte evidence.

---

# 84. JSON evidence schema minimum

```json
{
  "session": 336,
  "input": "...",
  "p0_a": {
    "multi_device_convergence": "PASS|FAIL|BLOCKED_ENVIRONMENT",
    "semantic_conflict": "PASS|FAIL"
  },
  "p0_b": {
    "update_decrease_dependency": "PASS|FAIL",
    "void_dependency": "PASS|FAIL"
  },
  "regressions": {
    "session_334": "PASS|FAIL|BLOCKED_ENVIRONMENT",
    "session_335": "PASS|FAIL|BLOCKED_ENVIRONMENT"
  }
}
```

---

# 85. Runtime simulation

إذا لا يوجد جهازان فعليان:

نفذ deterministic host-side simulation لحالتين/ثلاثة devices.

يجوز إعلان:

```text
MULTI_DEVICE_BEHAVIORAL_SIMULATION = PASS
```

لكن لا تقل:

```text
TWO_PHYSICAL_DEVICES = PASS
```

بدون جهازين.

---

# 86. Physical staging

للـproduction cutover النهائي، إن أمكن:

```text
2 physical/emulated independent clients
same organization
same initial server state
concurrent cash commands
pull convergence
```

إذا غير متاح:

```text
TWO_DEVICE_RUNTIME = BLOCKED_ENVIRONMENT
```

---

# 87. No new performance project

336 لا تقيس 100k/250k datasets مجددًا إلا كregression smoke.

التركيز correctness.

---

# 88. Performance regression smoke

يجب فقط إثبات:

```text
reconciliation is O(1) per pulled movement
dependency check uses existing indexed mutation id
no full-history scan introduced
```

---

# 89. Complexity guard

لا تحل P0 بكتابة function ضخم مع branching زائد.

يفضل pure helpers صغيرة.

إذا architecture complexity gate يفشل بسبب 336:

```text
SESSION_336 = FAIL
```

---

# 90. Documentation drift

حدث:

```text
CHANGELOG.md
docs/INDEX.md or canonical docs map if project policy requires
session contract evidence
```

وفق بوابات المشروع.

---

# 91. Session contract metadata

أنشئ/حدث:

```text
docs/architecture/contracts/sessions/session-336.json
```

مع:

```text
baseline_snapshot
changed_files
risk_classification
invariants
verification commands
```

لا تكرر خطأ 334 القديم في missing baseline snapshot.

---

# 92. Required gate execution

شغّل كل بوابات المشروع الحالية ذات الصلة.

على الأقل:

```text
change-contract
architecture_guard
dependency_gate
persistence_guard
contract_compatibility_guard
maintainability_testability
feature_scalability_admission
technical_debt_ratchet
kotlin_quality
documentation_gate
financial 334 guard
financial sync 335 guard
financial 336 guard
```

استخدم الأسماء الفعلية الموجودة.

---

# 93. Gradle verification

إذا البيئة تسمح:

```bash
./gradlew --no-daemon testDebugUnitTest
./gradlew --no-daemon detekt
./gradlew --no-daemon lintDebug
./gradlew --no-daemon assembleDebug
```

وشغّل focused module tests.

---

# 94. Build blocked semantics

إذا Gradle distribution/network غير متاح:

```text
GRADLE = BLOCKED_ENVIRONMENT
```

ولا تعتبره PASS.

لكن 336 لا يجوز أن تمر إذا focused host/static tests نفسها تفشل.

---

# 95. Room migration

المتوقع:

```text
NOT_REQUIRED
```

إذا لم يتغير schema.

إذا تغير schema:

```text
migration test mandatory
schema export mandatory
```

---

# 96. Server migration

المتوقع:

```text
NOT_REQUIRED
```

إذا تم إنشاء migration جديد دون حاجة حقيقية:

```text
FAIL_SCOPE_EXPANSION
```

---

# 97. Completion gate — P0-A

لا تعتبر P0-A مغلقة إلا إذا:

```text
server reorder changes balances
local same-intent row accepts authoritative projection
no conflict
semantic mismatch still conflicts
cash register remains server authoritative
all devices converge
```

---

# 98. Completion gate — P0-B

لا تعتبر P0-B مغلقة إلا إذا:

```text
update decrease refund depends on expense update
void refund depends on expense void
create/increase still depend correctly
parent rejection prevents child send
no dependency cycle
no self dependency
```

---

# 99. Definition of Done

336 DONE فقط إذا أصبح مستحيلاً دون فشل test أن يحدث:

```text
1. legitimate server reordering causes IMMUTABLE_CASH_MOVEMENT_CONFLICT.
2. server-authoritative balance projection is rejected because local optimistic projection differs.
3. semantic amount/write/source mismatch is silently accepted.
4. movement reconciliation overwrites cash register authority.
5. VOID refund reaches server before VOID ACK.
6. UPDATE-decrease refund reaches server before UPDATE ACK.
7. rejected expense mutation still allows refund.
8. dependency points to the child itself.
9. create/update-increase dependency regresses.
10. 334 fixed-point/atomicity regresses.
11. 335 server-authoritative/no-O(N) guarantees regress.
```

---

# 100. Required final report

أنشئ:

```text
docs/finance/SESSION_336_FINAL_REPORT.md
```

ويجب أن يحتوي:

```text
input artifact
SHA-256
changed files
P0-A root cause
P0-A implemented fix
P0-B root cause
P0-B implemented fix
test matrix
mutation matrix
gate matrix
Gradle status
runtime status
remaining risks
final admission
```

---

# 101. Final status block

التقرير النهائي يطبع بالضبط:

```text
SESSION_336 = PASS | PASS_STATIC_RUNTIME_BLOCKED | BLOCKED_ENVIRONMENT | FAIL

input_v335_verified = PASS | FAIL

p0_cash_multi_device_root_cause = CONFIRMED | NOT_REPRODUCED | FAIL
cash_immutable_intent_helper = PASS | FAIL
cash_authoritative_projection_reconcile = PASS | FAIL
cash_server_reorder_a_then_b = PASS | BLOCKED_ENVIRONMENT | FAIL
cash_server_reorder_b_then_a = PASS | BLOCKED_ENVIRONMENT | FAIL
cash_three_device_convergence = PASS | BLOCKED_ENVIRONMENT | FAIL
cash_semantic_conflict_guard = PASS | FAIL
cash_register_server_authority_preserved = PASS | FAIL
client_balance_snapshot_push_absent = PASS | FAIL
cash_full_history_push_absent = PASS | FAIL

p0_expense_refund_dependency_root_cause = CONFIRMED | NOT_REPRODUCED | FAIL
expense_create_cash_dependency = PASS | FAIL
expense_increase_cash_dependency = PASS | FAIL
expense_decrease_refund_dependency = PASS | FAIL
expense_void_refund_dependency = PASS | FAIL
dependency_rejected_parent_guard = PASS | FAIL
dependency_missing_parent_guard = PASS | FAIL
dependency_self_cycle_guard = PASS | FAIL

session_334_regression = PASS | BLOCKED_ENVIRONMENT | FAIL
session_335_regression = PASS | BLOCKED_ENVIRONMENT | FAIL

focused_tests = PASS | BLOCKED_ENVIRONMENT | FAIL
mutation_evidence = PASS | BLOCKED_ENVIRONMENT | FAIL
static_finance_336 = PASS | FAIL

room_schema_before = <n>
room_schema_after = <n>
room_migration = NOT_REQUIRED | PASS | BLOCKED_ENVIRONMENT | FAIL
server_sql_change = NOT_REQUIRED | REQUIRED | FAIL

change_contract = PASS | FAIL
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
unit_tests = PASS | BLOCKED_ENVIRONMENT | FAIL
assemble_debug = PASS | BLOCKED_ENVIRONMENT | FAIL

two_device_runtime = PASS | BLOCKED_ENVIRONMENT | NOT_RUN | FAIL
production_cutover = AUTHORIZED | NOT_AUTHORIZED
```

---

# 102. Admission rule

يجوز:

```text
SESSION_336 = PASS
```

فقط إذا:

```text
all P0 behavioral tests PASS
all available static gates PASS
no 334/335 regression
Gradle verification PASS
runtime evidence required by environment/project policy PASS
```

إذا كل implementation/focused tests/gates تمر لكن Gradle أو device runtime محجوب فعليًا:

```text
SESSION_336 = PASS_STATIC_RUNTIME_BLOCKED
production_cutover = NOT_AUTHORIZED
```

إذا P0 behavior نفسه غير مثبت:

```text
SESSION_336 = FAIL
```

---

# 103. Handoff

بعد نجاح 336:

لا تفتح 337 لنفس P0.

المرحلة التالية تعود إلى roadmap الأصلية فقط.

يُفتح إصلاح جديد فقط عند:

```text
new runtime failure
new server contract drift
new production incident
new regression evidence
```

---

# END OF SESSION_336_FINAL
