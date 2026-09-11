# SESSION_334_FINAL — Cash & Expenses Financial Integrity and Atomicity

## 0. صفة العقد

هذا عقد تنفيذ مغلق النطاق للجلسة **334** فقط.

الهدف:

> جعل مسار **الصندوق + المصروفات** صحيحًا ماليًا وذريًا وقابلًا للاسترجاع، قبل الانتقال في 335 إلى المزامنة والأداء والتوسّع.

هذا العقد لا يفترض أن أي تقرير تاريخي يثبت نجاح التنفيذ.

---

# 1. Source of Truth الإلزامي

المدخل التنفيذي يجب أن يكون **ناتج Session 333 الفعلي**.

ممنوع التنفيذ على v331 أو أي نسخة أقدم لمجرد أنها استُخدمت في التحليل الذي كشف المشاكل.

قبل أي تعديل:

1. سجّل اسم ZIP المدخل.
2. سجّل SHA-256.
3. سجّل Git-less tree fingerprint أو manifest hashes للملفات المستهدفة.
4. شغّل بوابات 333 الساكنة الحالية.
5. أثبت أن 333 لا يزال صالحًا كنقطة انطلاق حسب حالة البيئة الفعلية.

إذا كان الملف المدخل ليس ناتج 333:

```text
SESSION_334 = FAIL_WRONG_INPUT
```

ولا يبدأ التنفيذ.

---

# 2. Drift Guard بين v331 وv333

التحليل الذي بُني عليه هذا العقد أثبت في خط v331 المشكلات التالية:

- `ExpenseEntity.amount` ما زال `Double`.
- `ExpenseDao` يجمع المصروفات عبر `SUM(amount)` إلى `Double`.
- `CashRegisterEntity` و`CashRegisterMovementEntity` لديهما minor-unit fields بالفعل.
- `ExpenseRepository.updateExpense()` يحسب الفرق بـ `Double`.
- إلغاء المصروف يتحقق من أي `MANUAL_ADD` بنفس `referenceId`، ما قد يخلط refund تعديل سابق مع refund الإلغاء.
- `cashRegisterManager` داخل `ExpenseRepository` اختياري، ما يسمح نظريًا بحفظ مصروف دون أثر صندوق إذا وُجد wiring غير صحيح.
- linked expense ينفّذ المصروف أولًا ثم توزيع landed cost خارج transaction العليا.
- landed-cost path يستخدم rounding إلى مئات عبر `round(... / 100) * 100`.
- `ExpensesViewModel` يبني نهاية الشهر بإضافة 31 يومًا.
- أخطاء بعض العمليات المالية لا تتحول إلى UI failure state واضح.

قبل تطبيق أي patch على v333:

```text
for each finding:
    CONFIRMED | ALREADY_FIXED | CHANGED_SEMANTICS
```

إذا تغيّر التنفيذ بين 331 و333، يُصلح **المعنى** لا أرقام الأسطر القديمة.

---

# 3. Scope الجلسة 334

الجلسة مسؤولة حصريًا عن:

1. Fixed-point source of truth للمصروفات.
2. Fixed-point calculations في جميع قرارات الصندوق المرتبطة بالمصروفات والتعديل اليدوي.
3. Atomic expense + cash effect + outbox.
4. Atomic linked expense + landed-cost local effects.
5. Idempotent/semantic expense reversal.
6. Fail-closed permissions والاعتماديات المالية.
7. تصحيح حدود الشهر.
8. إظهار فشل العملية المالية للـPresentation بدل النجاح الصامت.
9. Migration + focused correctness tests المطلوبة لإثبات البنود السابقة.

---

# 4. خارج نطاق 334 — مؤجل صراحة إلى 335

لا تنفذ في هذه الجلسة:

```text
legacy sync retirement
pushCashMovements O(N) optimization
cash register server-authority cutover
sync pagination
sync batching
sync cursor redesign
new sync protocol
query/index performance tuning غير الضروري للمigration
large-data benchmarks
performance budgets
broad observability redesign
```

وجود هذه المشاكل لا يبرر توسيع 334.

---

# 5. Financial Source of Truth

القاعدة بعد 334:

```text
money calculation source of truth = Long minor units
Double = compatibility / display / legacy transport only
```

لا يجوز لأي قرار مالي جديد أن يعتمد على arithmetic بـ `Double`.

المقياس الحالي للمشروع:

```text
Money.MINOR_SCALE = 2
Money.ROUNDING = HALF_UP
```

ويجب إعادة استخدام `Money` الحالي، لا إنشاء نظام مال موازٍ.

---

# 6. ExpenseEntity — Migration إلى minor units

إذا v333 ما زال على Room schema 81 ولم يكن الإصلاح موجودًا:

```text
ROOM_SCHEMA_VERSION: 81 -> 82
```

أضف إلى `ExpenseEntity`:

```kotlin
@ColumnInfo(name = "amount_minor", defaultValue = "0")
val amountMinor: Long = Money.fromLegacyDouble(amount).amountMinor
```

يظل `amount: Double` مؤقتًا للتوافق فقط.

بعد 334:

```text
ExpenseEntity.amountMinor = authoritative
ExpenseEntity.amount = compatibility projection
```

كل local write جديد يجب أن يكتب الاثنين من قيمة Money واحدة.

---

# 7. Migration 81 → 82

أنشئ migration مخصصة، وسجّلها في `MigrationCatalog`.

المطلوب:

1. إضافة `amount_minor` بدون إسقاط بيانات.
2. Backfill لكل الصفوف القديمة من `amount` وفق نفس scale والrounding المستخدم في `Money` قدر الإمكان.
3. عدم تغيير IDs أو lifecycle أو timestamps.
4. عدم حذف VOID rows.
5. عدم إعادة إنشاء المصروفات بهويات جديدة.

بعد migration يجب إثبات:

```text
row_count_before == row_count_after
ids_before == ids_after
active_count_before == active_count_after
void_count_before == void_count_after
for every legacy expense:
    amount_minor == canonical Money conversion of amount
```

أي mismatch:

```text
MIGRATION_81_82 = FAIL
```

---

# 8. ExpenseDao — الحساب بالـminor فقط

أوقف استخدام:

```sql
SUM(amount)
```

كمصدر حساب مالي.

المطلوب إضافة/تحويل الاستعلامات إلى:

```sql
SUM(amount_minor)
```

والنتائج الحسابية تكون `Long`.

يجب وجود models من نمط:

```text
CategoryTotalMinor(category, totalMinor)
```

ولا تستخدم `Double` في aggregation الداخلي.

يجوز تحويل النتيجة إلى Double عند حدود UI القديمة فقط.

---

# 9. ExpenseRepository — اعتماد مالي Fail-Closed

إذا بقي:

```kotlin
private val cashRegisterManager: CashRegisterManager? = null
```

فهذا ممنوع بعد 334.

يجب أن يصبح dependency مالي إلزاميًا في production wiring.

المبدأ:

```text
expense write without required cash effect = impossible
```

لا يجوز استخدام `?.onExpense(...)` أو أي optional invocation لمسار مالي إلزامي.

---

# 10. Insert Expense Invariant

قبل أي كتابة:

```text
amountMinor > 0
amount finite at compatibility boundary
category valid according to current domain rule
item valid according to current domain rule
trusted organization exists
permission expensesCreate = true
```

العملية المحلية الواحدة يجب أن تكون:

```text
BEGIN Room transaction
  persist ACTIVE expense
  apply EXPENSE cash movement using amountMinor
  enqueue EXPENSE outbox intent
COMMIT
```

أي فشل:

```text
expense persisted = no
cash changed = no
outbox inserted = no
```

---

# 11. Update Expense — الحساب بالminor

لا تستخدم:

```kotlin
updated.amount - old.amount
```

المطلوب:

```text
diffMinor = updated.amountMinor - old.amountMinor
```

مع exact arithmetic.

السلوك:

```text
diffMinor > 0 -> cash out exactly diffMinor
diffMinor < 0 -> cash in exactly abs(diffMinor)
diffMinor = 0 -> no cash movement
```

التحديث نفسه + cash effect + outbox يجب أن يبقوا في transaction واحدة.

إذا كان update endpoint موجودًا أو callable، يجب تطبيق نفس صلاحية الكتابة المعتمدة للمصروفات، ولا يبقى update غير محمي.

---

# 12. Expense Void/Reversal — إصلاح الهوية الدلالية

ممنوع بعد 334 اعتبار:

```text
movementType = MANUAL_ADD + referenceId = expenseId
```

دليلًا كافيًا أن المصروف سبق إلغاؤه.

السبب:

- خفض قيمة المصروف يمكن أن ينتج refund من نوع `MANUAL_ADD` لنفس المصروف.
- ذلك لا يعني أن VOID refund نُفذ.

المطلوب هو semantic identity صريحة باستخدام الحقول الموجودة إن كانت كافية:

```text
sourceType
sourceId
writeId
reversalWriteId
```

التوصية الملزمة دلاليًا:

```text
expense update refund:
  sourceType = EXPENSE_UPDATE_REFUND
  sourceId = expenseId

expense void refund:
  sourceType = EXPENSE_VOID_REFUND
  sourceId = expenseId
  writeId = reversalWriteId
```

يمكن اختيار أسماء ثابتة مكافئة، لكن يجب ألا تتشارك عمليتان مختلفتان الهوية الدلالية نفسها.

---

# 13. VOID Atomicity

إلغاء المصروف يجب أن يكون:

```text
BEGIN transaction
  read current expense
  if VOID -> idempotent no-op
  create/resolve reversalWriteId
  refund exactly current.amountMinor
  persist lifecycle = VOID
  persist reversalWriteId
  enqueue VOID intent using same semantic mutation identity
COMMIT
```

بعد commit:

```text
expense.lifecycleState = VOID
cash refund count for EXPENSE_VOID_REFUND = exactly 1
cash refund amount = current expense amount at void time
```

إعادة `deleteExpense()` لنفس المصروف لا تغيّر الرصيد مرة ثانية.

---

# 14. Regression الحرجة — تعديل ثم حذف

هذا اختبار إلزامي:

```text
create expense = 100.00
update expense = 80.00
  -> update refund = 20.00
void expense
  -> void refund = 80.00
```

المحصلة النهائية للصندوق مقارنة بما قبل إنشاء المصروف:

```text
0.00 net effect
```

ويجب إثبات:

```text
update refund != void refund semantically
void refund was not skipped because update refund existed
```

---

# 15. CashRegisterManager — minor-unit APIs

أضف/استخدم APIs واضحة للعمليات التي يمسها 334:

```text
onExpenseMinor(amountMinor, expenseId, ...)
manualAddMinor(amountMinor, ...)
manualDeductMinor(amountMinor, ...)
reverseMovementMinor(...)
```

يجوز إبقاء APIs الـDouble للتوافق، لكن:

```text
334 financial paths MUST convert once at boundary
then use Long minor units internally
```

ممنوع:

```text
Double -> calculation -> Double -> Money
```

المسموح:

```text
input -> Money -> amountMinor -> exact arithmetic
```

---

# 16. Overdraft Guard

قرار كفاية الرصيد يجب أن يستخدم:

```text
balanceMinor
amountMinor
```

لا `balance: Double`.

الحالة المطلوبة:

```text
allowCashOverdraft = false
balanceMinor < outgoingMinor
=> operation fails before commit
```

مع rollback كامل لأي transaction تحتوي العملية.

---

# 17. Manual Cash Adjustment — Fail Closed

المسار الحالي لا يجوز أن يعيد بصمت عند رفض `cashAdjust`.

بعد 334:

```text
permission denied
=> no balance change
=> no cash movement
=> no outbox intent
=> explicit failure result/exception reaches presentation
```

ويجب تسجيل permission denial وفق audit mechanism الموجود.

---

# 18. Linked Expense + Landed Cost — Transaction Boundary

المسار الحالي من نوع:

```text
insert expense COMMIT
then distribute landed cost item-by-item
```

ممنوع بعد 334.

للمصروف المرتبط بفاتورة شراء يجب أن تكون العملية المحلية الواحدة:

```text
BEGIN top-level AppDatabase transaction
  create expense
  cash effect
  expense outbox
  resolve eligible invoice lines
  calculate deterministic allocations
  apply every inventory landed-cost adjustment
  enqueue all required inventory intents
COMMIT
```

إذا فشل أي adjustment مطلوب:

```text
ROLLBACK EVERYTHING
```

ولا يبقى:

```text
expense without landed cost
partial inventory cost update
cash deduction without complete local workflow
partial outbox batch
```

---

# 19. Landed-Cost Allocation Arithmetic

أزل الحساب المالي المعتمد على:

```kotlin
round((base + base * ratio) / 100.0) * 100.0
```

كخوارزمية authoritative.

المطلوب:

1. تحويل تكلفة الأساس والمصروف إلى minor units قبل الحساب.
2. استخدام وزن deterministic لكل line مؤهلة.
3. توزيع المصروف دون فقد/خلق وحدات نقدية.
4. توزيع remainder deterministic.
5. tie-break ثابت لا يعتمد على hash iteration order.
6. تحويل السعر النهائي إلى legacy `Double` فقط عند DAO/API compatibility boundary إذا كان ذلك مطلوبًا حاليًا.

Invariant إلزامي:

```text
sum(allocated landed cost minor) == expense.amountMinor
```

إذا تعذر تمثيل invariant بدقة بسبب نموذج per-unit الحالي، لا تخفِ الفرق بالrounding؛ يجب:

```text
FAIL_EXACT_ALLOCATION_UNREPRESENTABLE
```

أو استخدام mechanism الدقيق الموجود في inventory cost model إذا كان v333 قد وفّره.

---

# 20. Empty / Invalid Linked Invoice

إذا `linkedInvoiceId` موجود لكن:

```text
invoice missing
no eligible inventory lines
invoice base total <= 0
required inventory item missing during apply
```

لا يجوز أن تنجح العملية وكأن التوزيع تم.

النتيجة:

```text
linked expense operation = FAIL
financial/local transaction = ROLLBACK
```

إلا إذا كان هناك business rule موثق صراحة في v333 يسمح بمصروف مرتبط بلا توزيع؛ عندها يجب إثباته بعقد/اختبار قائم قبل استخدامه.

---

# 21. Operation Result to Presentation

عمليات:

```text
add expense
delete/void expense
manual cash adjustment
linked expense
```

لا يجوز أن تختفي أخطاؤها داخل `viewModelScope.launch` بلا state قابل للعرض.

أضف state/event minimal مثل:

```text
Idle
Submitting
Success
Error(code/message)
```

أو استخدم النمط الحالي المعتمد في المشروع إذا كان موجودًا.

الهدف:

```text
backend/local rejection != UI success
```

لا تعِد تصميم الشاشة في 334.

---

# 22. Month Range Correctness

أوقف:

```text
startOfMonth + 31 days
```

استخدم calendar boundary حقيقية:

```text
startInclusive = first instant of current month
endExclusive = first instant of next month
```

مع timezone المحلي المعتمد في التطبيق.

يفضل تحويل DAO إلى:

```text
date >= startInclusive AND date < endExclusive
```

بدل `BETWEEN` لتجنب التداخل بين الفترات.

---

# 23. Public Contract Compatibility

Session 334 ممنوعة من كسر `ExpensesGateway` أو أي stable contract بلا المرور عبر compatibility policy الموجودة منذ 330.

إذا احتاجت minor-unit semantics تغيير contract:

1. فضّل additive compatible field/API.
2. لا تحذف API قديمًا في نفس الجلسة إذا له consumers.
3. حدّث contract registry/semantic tests إذا أصبحت seam عامة جديدة.
4. `contract-compatibility` يجب أن يبقى PASS.

---

# 24. Expected Production Files

القائمة التالية متوقعة، وليست إذنًا مفتوحًا:

```text
data/database/.../entity/InvoicePaymentEntities.kt
data/database/.../dao/ExpenseDao.kt
data/database/.../dao/CashRegisterDao.kt
data/database/.../AppDatabaseMigrations81To82.kt
data/database/.../MigrationCatalog.kt

data/operations/.../ExpenseRepository.kt
data/operations/.../CashRegisterManager.kt

feature/expenses/.../application/ExpensesContract.kt        # only if additive contract change required
feature/expenses/.../ui/screens/expenses/ExpensesViewModel.kt
feature/expenses/.../ui/screens/expenses/ExpensesScreen.kt   # only minimal failure rendering

app/.../feature/expenses/bridge/ExpensesOperationsAdapter.kt

feature/inventory/.../domain/port/InventoryLandedCostAdjustmentPort.kt # only if minor-unit contract is required
feature/inventory/.../data/RoomInventoryLandedCostAdjustmentAdapter.kt # same condition
```

قبل التعديل:

```text
freeze exact production allowlist in session-334 change contract
```

أي Production file خارج القائمة المجمدة = FAIL إلا إذا وُجد dependency compile fix مثبت ومضاف رسميًا للعقد قبل تعديله.

---

# 25. Forbidden Refactors

ممنوع في 334:

```text
rename unrelated packages
repository-wide formatting
new generic financial framework
new event bus
new command bus
service locator
unrelated DI cleanup
navigation redesign
screen redesign
sync rewrite
inventory rewrite
```

الإصلاح يجب أن يكون أصغر تغيير يحقق invariants.

---

# 26. Required Unit Tests — Money/Expense

أضف اختبارات على الأقل للحالات:

```text
expense 0.01 exact
expense 10.10 exact
expense 999999.99 exact
invalid zero rejected
invalid negative rejected
NaN rejected at compatibility boundary
Infinity rejected at compatibility boundary
```

ويجب ألا تعتمد assertions المالية على epsilon Double عندما يمكن assertion على minor units.

---

# 27. Required Transaction Tests

إلزامي إثبات:

```text
insert expense success -> expense + cash + outbox all committed
cash failure -> expense rollback
outbox failure -> expense + cash rollback
update increase -> exact extra cash out
update decrease -> exact refund
void -> exact refund once
void twice -> second is no-op
update-decrease then void -> correct two semantically distinct refunds
```

---

# 28. Required Linked Expense Atomicity Tests

إلزامي:

```text
linked expense success -> all effects committed
landed cost fails on first item -> everything rollback
landed cost fails in middle item -> everything rollback
missing inventory target -> everything rollback
allocation total == expense.amountMinor
same input order-independent except documented deterministic tie-break
```

اختبار middle-item failure مهم؛ لأنه يكشف partial commit الحقيقي.

---

# 29. Required Permission Tests

إلزامي:

```text
expensesCreate denied -> no write
expensesDelete denied -> no void/refund
cashAdjust denied -> no cash/outbox write
permission denial surfaced as failure
```

لا يقبل اختبار يتحقق من نص اسم الصلاحية فقط؛ يجب أن ينفذ السلوك.

---

# 30. Required Month Boundary Tests

اختبر على الأقل:

```text
February 28-day month
leap-year February
30-day month
31-day month
last millisecond/instant before next month
first instant of next month excluded from current month
```

إذا طبقة الوقت تسمح بحقن Clock/ZoneId، استخدمها بدل اختبارات تعتمد على وقت الجهاز الفعلي.

---

# 31. Migration Test

Migration 81→82 يجب اختبارها فعليًا إذا تغير schema.

الاختبار يزرع قبل migration على الأقل:

```text
ACTIVE expense with decimal amount
VOID expense with decimal amount
multiple categories
```

ثم يثبت:

```text
no row loss
amountMinor backfilled correctly
lifecycle preserved
ids preserved
new writes after migration use amountMinor correctly
```

إذا instrumentation غير متاحة:

```text
MIGRATION_TEST = BLOCKED_ENVIRONMENT
```

ولا تُحوّلها إلى PASS ساكن.

---

# 32. Mutation / Behavioral Evidence

بما أن 333 شددت behavioral mutation evidence، أضف أو حدّث ما يلزم لإثبات أن الاختبارات تكتشف فعليًا على الأقل mutations التالية:

```text
replace amountMinor diff with Double diff
skip cash movement on expense insert
skip void refund
classify update refund as void refund
remove linked-expense top-level transaction
ignore landed-cost apply failure
allow cashAdjust permission denial to return success
restore 31-day month range
```

كل mutation محسوبة يجب أن:

```text
be applied to production fixture/source
execute detecting test
make that test fail
be reported DETECTED only because test failed
```

---

# 33. Existing Gates Must Remain Green

لا تضعف أي بوابة موروثة من 319–333.

إلزامي، حسب الأسماء الفعلية في v333:

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
```

إذا تغير schema، persistence/migration gate يجب أن ترى schema 82 والمسار 81→82 الحقيقي.

---

# 34. Focused Verification Order

الترتيب المقترح:

```text
1. input/predecessor verification
2. change-contract freeze
3. architecture/dependency preflight
4. schema migration implementation
5. fixed-point expense implementation
6. reversal/idempotency implementation
7. linked-expense atomicity implementation
8. presentation failure state + month fix
9. focused unit tests
10. database/migration tests
11. behavioral mutation checks
12. static quality gates
13. Gradle verification
14. unified quality gate
15. source-of-truth admission
```

Fail-fast في كل مرحلة.

---

# 35. Mandatory Gradle Verification

عندما تسمح البيئة:

```bash
./gradlew --no-daemon testDebugUnitTest
./gradlew --no-daemon detekt
./gradlew --no-daemon lintDebug
./gradlew --no-daemon assembleDebug
```

وشغّل focused module tests للموديولات المتأثرة.

Migration/instrumentation tests تُشغّل عندما تتوفر بيئة Android المناسبة.

---

# 36. Environment Block Rule

إذا نجحت جميع الفحوص الساكنة لكن Gradle/SDK/network يمنع التحقق:

```text
SESSION_334_STATIC_IMPLEMENTATION = PASS
SESSION_334 = BLOCKED_ENVIRONMENT
SOURCE_OF_TRUTH_ADMISSION = BLOCKED_ENVIRONMENT
```

لا يجوز قول:

```text
BUILD PASS
TESTS PASS
SOURCE OF TRUTH
```

إن لم تنفذ فعليًا.

إذا schema تغير ولم تُختبر migration في بيئة مناسبة، يجب إبراز ذلك صراحة في handoff.

---

# 37. Failure Conditions

الجلسة تفشل إذا تحقق أي من التالي:

```text
wrong input artifact
Double remains authoritative for expense calculations
SUM(amount) remains financial source of truth
expense write can succeed without mandatory cash effect
expense update diff calculated with Double
void refund can be suppressed by unrelated MANUAL_ADD
repeat void changes balance twice
cash overdraft decision uses Double
cashAdjust permission denial is treated as success
linked expense can commit before required landed-cost completion
middle landed-cost failure leaves partial updates
allocation creates or loses money silently
31-day month approximation remains
migration loses/changes expense identity/lifecycle
schema changed without catalog/export update
existing architecture/compatibility/persistence gates weakened
behavioral evidence is symbol-only or fake
```

---

# 38. Required Financial Invariants at End of 334

يجب أن تصبح العبارات التالية صحيحة من الكود والاختبارات:

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

---

# 39. Required Evidence Artifacts

أنشئ على الأقل:

```text
docs/finance/SESSION_334_FINANCIAL_INTEGRITY.md
docs/finance/SESSION_334_INVARIANTS.json
docs/finance/SESSION_334_TRANSACTION_MATRIX.md
docs/finance/SESSION_334_MIGRATION_81_82.md        # إذا schema تغير
```

ويجب أن يحتوي التقرير على:

1. input filename + SHA-256.
2. preflight drift status لكل finding.
3. Room version before/after.
4. exact changed files.
5. fixed-point decisions.
6. transaction matrix.
7. reversal identity semantics.
8. linked landed-cost allocation evidence.
9. focused test commands + outputs.
10. migration test status.
11. mutation evidence status.
12. all inherited gates status.
13. Gradle/build status.
14. final admission status.

---

# 40. Completion Matrix

التقرير النهائي يجب أن يحتوي جدولًا مكافئًا:

| Objective | Required | Result | Evidence |
|---|---:|---|---|
| Expense minor-unit authority | YES | PASS/FAIL | test/path |
| 81→82 migration | IF NEEDED | PASS/BLOCKED/FAIL | migration test |
| Insert atomicity | YES | PASS/FAIL | transaction test |
| Update fixed-point | YES | PASS/FAIL | test |
| Void idempotency | YES | PASS/FAIL | test |
| Update-refund vs void-refund | YES | PASS/FAIL | regression test |
| Linked expense atomicity | YES | PASS/FAIL | failure-in-middle test |
| Allocation conservation | YES | PASS/FAIL | exact minor assertion |
| Overdraft fixed-point | YES | PASS/FAIL | test |
| Permission fail-closed | YES | PASS/FAIL | behavioral test |
| Calendar month range | YES | PASS/FAIL | boundary tests |
| UI failure propagation | YES | PASS/FAIL | ViewModel test |
| Existing gates preserved | YES | PASS/FAIL | gate outputs |
| Gradle verification | YES | PASS/BLOCKED | command outputs |

أي Required = YES وليس PASS يمنع `SESSION_334 = PASS`.

---

# 41. Handoff إلى Session 335

335 لا تبدأ من 333.

تبدأ فقط من ناتج 334 المقبول حسب قواعد المشروع.

إذا 334 = PASS:

```text
handoff335Authorized = true
```

إذا 334 = BLOCKED_ENVIRONMENT لكن implementation ثابت ومتكامل، يجوز handoff مشروط فقط إذا سياسة المشروع الحالية تسمح بذلك، مع:

```text
NO TEST PASS INHERITED
NO BUILD PASS INHERITED
NO SOURCE-OF-TRUTH PASS INHERITED
```

ويجب على 335 إعادة التحقق من الحدود المالية التي تعتمد عليها قبل تغيير sync/performance.

---

# 42. ما يُترك تحديدًا لـ335

بعد نجاح 334، 335 تتولى:

```text
server-authoritative cash semantics
legacy cash_register push retirement/hardening
pushCashMovements delta/outbox behavior
expense/cash sync convergence
indexes for expense range/category queries
large-history performance
pagination/batching where needed
focused sync/concurrency/performance regression suite
```

335 ممنوعة من إعادة فتح صحة minor-unit أو reversal semantics إلا لإصلاح regression مثبت.

---

# 43. Final Definition of Done

Session 334 تعتبر DONE فقط عندما يصبح مستحيلاً سلوكيًا أن يحدث أي من التالي دون فشل اختبار/بوابة:

```text
1. 10.10 expense becomes a different cash amount due to floating arithmetic.
2. expense persists while required cash effect is missing.
3. cash effect persists while expense/outbox transaction fails.
4. reducing an expense prevents its later VOID refund.
5. VOID twice refunds twice.
6. linked expense persists after middle landed-cost failure.
7. allocation silently creates or loses money.
8. denied cash adjustment looks successful.
9. February query includes rows from the next month because of +31 days.
```

---

# 44. Required Final Status Block

التقرير النهائي يجب أن يطبع:

```text
SESSION_334 = PASS | BLOCKED_ENVIRONMENT | FAIL

input_v333_verified = PASS | FAIL
financial_integrity = PASS | FAIL
expense_minor_unit_authority = PASS | FAIL
cash_minor_unit_authority = PASS | FAIL
expense_insert_atomicity = PASS | FAIL
expense_update_atomicity = PASS | FAIL
expense_void_atomicity = PASS | FAIL
expense_void_idempotency = PASS | FAIL
update_refund_void_refund_separation = PASS | FAIL
linked_expense_atomicity = PASS | FAIL
landed_cost_conservation = PASS | FAIL
permission_fail_closed = PASS | FAIL
month_boundary_correctness = PASS | FAIL
ui_failure_propagation = PASS | FAIL

room_schema_before = <n>
room_schema_after = <n>
migration_test = PASS | BLOCKED_ENVIRONMENT | NOT_REQUIRED | FAIL

focused_tests = PASS | BLOCKED_ENVIRONMENT | FAIL
behavioral_mutations = PASS | BLOCKED_ENVIRONMENT | FAIL
architecture_guard = PASS | FAIL
persistence_guard = PASS | FAIL
contract_compatibility_guard = PASS | FAIL
maintainability_testability = PASS | FAIL
technical_debt_ratchet = PASS | FAIL
kotlin_quality = PASS | FAIL

detekt = PASS | BLOCKED_ENVIRONMENT | FAIL
lint = PASS | BLOCKED_ENVIRONMENT | FAIL
full_unit_tests = PASS | BLOCKED_ENVIRONMENT | FAIL
assemble_debug = PASS | BLOCKED_ENVIRONMENT | FAIL
source_of_truth_admission = PASS | BLOCKED_ENVIRONMENT | FAIL

handoff335Authorized = true | false
```

---

# END OF SESSION_334_FINAL
