# SESSION_338_FINAL.md

## Verto Home — Session 338

### Quick Actions Engineering Hardening Contract

**نوع المستند:** عقد تنفيذ مستقل وصارم  
**الجلسة:** 338  
**الحالة:** `PLAN ONLY — ENGINEERING ONLY — VISUAL UX FROZEN`  
**تاريخ الصياغة:** 2026-08-22  
**Baseline الفحص / Source of Truth:** `Verto-v337-pending-actions-repaired.zip`  
**SHA-256 للـbaseline:** `17397e52ccdb6308b19d6b771c6a1149a3580f7998aaadb0dd8773a24470ef39`  
**Archive entries:** `3248`  
**Kotlin files:** `1400`  
**النطاق الوظيفي:** Home → **Quick Actions فقط**  
**Schema/Migration:** `FORBIDDEN`  
**Server/Sync changes:** `FORBIDDEN`  
**Visual UX changes:** `STRICTLY FORBIDDEN`

---

# 0. الحكم التنفيذي

الجلسة 338 تعالج **المشكلات الهندسية فقط** في Quick Actions.

المطلوب إصلاح:

```text
Permission enforcement
Trusted dispatch
Stale-action rejection
One-shot effect reliability
Busy-state safety
Quick stock count threading
Expense command authorization
Destination fail-closed validation
Ordering correctness
Concurrency
Tests
Verification
```

مع تجميد الشكل الحالي بالكامل.

---

# 1. قيد بصري غير قابل للتفاوض

يبقى Quick Actions بصريًا كما هو.

**يمنع تمامًا في 338:**

```text
تحويل LazyRow إلى Grid
تغيير التمرير الأفقي
تغيير عدد البطاقات الظاهرة
تغيير snapping
تغيير page indicators
تغيير موضع Organizer
تغيير حجم البطاقات
تغيير الألوان
تغيير الأيقونات
تغيير الخطوط
تغيير المسافات
تغيير ترتيب العناصر الافتراضي
إضافة صف ثانٍ
إضافة زر "عرض الكل"
إعادة تصميم Organizer
إضافة chooser بصري جديد
إضافة BottomSheet/Popup جديد للمسار الطبيعي
```

ويجب أن يبقى:

```kotlin
LazyRow
rememberSnapFlingBehavior(...)
QuickActionPageIndicators(...)
QuickActionOrganizerTile
```

بنفس السلوك البصري الحالي.

### بوابة صريحة

إذا تغير الـUX البصري للـQuick Actions:

```text
FAIL_VISUAL_SCOPE_BREACH
```

حتى لو كان التغيير "أفضل".

---

# 2. الوضع الحالي المثبت

البنية الحالية:

```text
Feature QuickActionProvider
    ↓
QuickActionProviderRegistry
    ↓
ObserveQuickActionsUseCase
    ↓
ObserveOrderedQuickActionsUseCase
    ↓
HomeViewModel
    ↓
HomeQuickActionsSection
    ↓
QuickActionTile
```

والـProviders الحالية:

```text
InvoiceQuickActionProvider
InventoryQuickActionProvider
ExpensesQuickActionProvider
PartyQuickActionProvider
PaymentQuickActionProvider
```

والأوامر الحالية:

```text
فاتورة بيع
فاتورة شراء
إضافة صنف
إضافة عميل
تسجيل دفعة
إضافة مصروف
جرد سريع
```

---

# 3. ما هو جيد ويجب الحفاظ عليه

يمنع 338 من إعادة بناء هذه الأجزاء بلا ضرورة:

- Hilt multibinding للـProviders.
- `QuickActionProviderRegistry`.
- رفض duplicate provider IDs.
- رفض duplicate action IDs.
- الترتيب الافتراضي deterministic.
- `QuickAction.isAllowedBy(context)`.
- فلترة الصلاحيات داخل provider والـaggregator.
- حفظ ترتيب المستخدم.
- Scope الحفظ:
  - `organizationId`
  - `userId`
- الحفاظ على مواقع الأوامر المخفية مؤقتًا بسبب الصلاحيات.
- `distinctUntilChanged`.
- Stable item keys.
- Organizer الحالي.
- Scroll state الحالي.
- الشكل الحالي للبطاقات.

---

# 4. المشكلة P0 — UI Action Object ليس Security Boundary

الحالة الحالية:

```kotlin
fun onQuickActionClicked(action: QuickAction)
```

ثم يتم Dispatch مباشرة من `action.destination`.

هذا يعني أن HomeViewModel يثق بكائن وصل من UI.

الـUI ليس authority.

## المطلوب

عند الضغط:

1. استقبل `actionId` أو استخرج id فقط.
2. اقرأ `homePermissionContext.value`.
3. ابحث عن **canonical action** في `quickActions.value`.
4. إذا لم يكن الـid موجودًا حاليًا:
   - ارفض التنفيذ.
5. أعد التحقق:
   - `action.isAllowedBy(context)`.
6. استخدم canonical action فقط في dispatch.
7. لا تستخدم destination قادمًا من UI إذا لم يطابق canonical state.

### الهدف

منع:

```text
stale action
forged action
old restored object
permission-revoked action
mutated destination arguments
```

من التنفيذ.

---

# 5. Stale Permission Race

قد يظهر action للمستخدم، ثم تتغير الصلاحيات قبل الضغط.

وقد يفتح المستخدم Dialog المصروف، ثم تزال الصلاحية قبل الحفظ.

لذلك يجب وجود فحصين منفصلين:

```text
Check A: عند click/dispatch
Check B: عند command execution
```

لا يكفي أن الزر لم يعد ظاهرًا.

---

# 6. Expense Authorization — P0

الحالة الحالية:

```text
ExpensesQuickActionProvider
→ EXPENSES_CREATE
→ Home
→ addExpenseFromHome(...)
→ ExpensesOperationsAdapter.addExpense(...)
```

لكن:

```kotlin
ExpensesOperationsAdapter.addExpense(...)
```

لا يعيد التحقق من `expensesCreate`.

والـAdapter لديه أصلًا:

```text
PermissionProvider
WriteAuditPort
SessionReader
```

ويستخدم pattern مماثلًا في `adjustCash()`.

## المطلوب

قبل أي كتابة داخل `addExpense()`:

```kotlin
permissionProvider.canNow { it.expensesCreate }
```

إذا الرفض:

1. لا كتابة.
2. لا cash/inventory side effects.
3. لا outbox side effects.
4. سجل `permission denied` عبر audit infrastructure الحالي.
5. ارجع failure/exception متوافقًا مع contract الحالي، أو استخدم نمط failure المعتمد في المشروع.
6. Home يعرض رسالة مناسبة بدون claim بالنجاح.

### ممنوع

```text
silent return مع UI success
الاعتماد على visibility فقط
كتابة المصروف ثم اكتشاف الصلاحية
```

---

# 7. Home Expense Execution Guard

حتى مع hardening داخل `ExpensesOperationsAdapter`:

`addExpenseFromHome()` يجب أيضًا أن يتحقق من:

```text
current context != null
RECORD_EXPENSE currently available
EXPENSES_CREATE currently granted
```

قبل إطلاق command.

إذا تغيرت الصلاحية أثناء dialog:

```text
reject
close/leave dialog حسب السلوك الحالي
show existing error mechanism
no write
```

لا تضف Dialog جديد لهذا الغرض.

---

# 8. Quick Stock Count — P0/P1

الحالة الحالية:

```kotlin
viewModelScope.launch {
    quickStockCountDocumentGateway.generate()
}
```

وفي:

`AndroidQuickStockCountDocumentGateway.generate()`

يتم:

```text
loadRows
load settings
create PdfDocument
render pages
write file
```

ولا يوجد dispatcher boundary واضح.

هذا قد يضع CPU/File work على Main.

## المطلوب

يجب أن يتم:

```text
PDF rendering
file creation
file write
```

خارج Main thread.

### الحل المقبول

إما:

```kotlin
withContext(Dispatchers.IO)
```

أو Dispatcher injectable وفق نمط المشروع.

إذا تم الفصل بدقة:

```text
DB suspend access → existing Room dispatcher
render CPU → Default
file write → IO
```

فهذا أفضل، لكن لا يلزم تعقيد زائد.

### شرط الإغلاق

لا يجوز أن تنفذ `PdfDocument` rendering أو `file.outputStream()` على Main.

---

# 9. Quick Stock Permission Guard

الجرد السريع يحتاج:

```text
INVENTORY_EDIT
```

عند العرض.

بعد 338 يجب أيضًا فحص الصلاحية عند التنفيذ.

إذا action أصبح stale:

```text
no PDF generation
no row loading
no file creation
```

---

# 10. Busy-State Correctness

الحالة الحالية:

```kotlin
if (id in busySet) return
setBusy(true)
...
setBusy(false)
```

لكن cleanup ليس داخل `finally`.

## المطلوب

كل command async من Quick Actions يجب أن يتبع:

```kotlin
setBusy(id, true)
try {
    ...
} finally {
    setBusy(id, false)
}
```

بحيث:

```text
exception
cancellation
effect failure
permission rejection after launch
```

لا تترك action عالقًا في busy state.

---

# 11. Atomic Busy Set Update

بدل read-modify-write اليدوي:

```kotlin
_busyQuickActionIds.value = _busyQuickActionIds.value + id
```

يفضل:

```kotlin
_busyQuickActionIds.update { ... }
```

أو آلية atomic مكافئة.

### اختبار

Simulated concurrent completion لأمرين:

```text
expense busy
stock count busy
```

لا يجوز أن يمسح انتهاء أحدهما busy state الخاص بالآخر.

---

# 12. Double-Tap / Re-entrancy

يجب إثبات:

```text
double tap expense save → one command
double tap stock count → one PDF generation
```

### المطلوب

- guard قبل launch.
- guard داخل execution boundary إن لزم.
- busy state deterministic.
- no duplicate success effects.

---

# 13. One-shot Effects Reliability

الحالة الحالية:

```kotlin
MutableSharedFlow(extraBufferCapacity = 1)
tryEmit(...)
```

تستخدم لبعض:

```text
Navigate
ShowExpenseDialog
```

وقد يفشل `tryEmit()` بدون handling.

## المطلوب

لا يجوز إسقاط Quick Action effect بصمت.

اختر نمطًا موثوقًا مثل:

```text
Channel(BUFFERED) + receiveAsFlow()
```

أو SharedFlow strategy مثبتة لا تسقط الحدث المطلوب.

### يجب تغطية:

```text
collector not ready momentarily
rapid consecutive navigation request
rotation/lifecycle transition
```

### ممنوع

```text
tryEmit() result ignored
```

إذا بقي `tryEmit` يجب فحص النتيجة ومعالجتها صراحة.

---

# 14. Navigation Must Use Canonical Action

أي Navigate effect يجب أن يحمل destination من:

```text
current canonical QuickAction
```

وليس من object قديم وصل من UI.

---

# 15. Destination Resolver — Fail Closed

الحالة الحالية:

```kotlin
INVOICE_EDITOR:
purchase -> purchase
else -> sale
```

و:

```kotlin
PAYMENT_ENTRY:
supplier -> suppliers
else -> clients
```

هذا يعني argument malformed أو missing يتحول إلى route حقيقي.

بعد 338:

## Invoice

المسموح فقط:

```text
type=sale
type=purchase
```

أي قيمة أخرى:

```text
null / rejected
```

## Payment

المسموح فقط:

```text
partyType=client
partyType=supplier
```

أي قيمة أخرى:

```text
null / rejected
```

### ممنوع

```text
unknown → sale
unknown → client
```

---

# 16. Payment Quick Action — لا تغيير UX

الحالة الحالية عند امتلاك الصلاحيتين:

```text
CLIENTS_ADD_PAYMENT
SUPPLIERS_ADD_PAYMENT
```

تختار provider حاليًا `client` بسبب ترتيب `when`.

هذه نقطة Product/UX منفصلة.

## في 338

**لا تضف chooser.**
**لا تغيّر label.**
**لا تضف Quick Action ثانية.**
**لا تغيّر الشكل.**

يتم فقط:

- توثيق السلوك الحالي.
- اختباره ليكون deterministic.
- التأكد أن argument الناتج valid.
- منع malformed fallback داخل resolver.

قرار تجربة "عميل أم مورد؟" يؤجل لجلسة UX/Payments مستقلة.

---

# 17. Quick Action Contract Validation

عند startup/registry aggregation يجب التحقق من:

```text
id not blank
label not blank
declared action IDs unique
provider IDs unique
destination id not blank
known action IDs map to expected destination family
```

لا يلزم إضافة coupling غير ضروري بين dashboard API وapp routes.

المطلوب validation على boundary الأنسب.

---

# 18. Provider Exception Isolation

إذا Provider واحد أخفق أثناء observation:

يجب تقييم الوضع الحالي بعناية.

إذا architecture الحالية تجعل failure في provider يفشل combine كله، يجوز إصلاح ذلك داخل 338 بحيث:

```text
one provider failure
≠
all quick actions disappear
```

مع:

```text
CancellationException rethrown
error logged
no PII
```

لكن لا تخترع fallback actions.

---

# 19. Permission Filtering Must Remain Double-Layered

يجب الحفاظ على:

```text
Provider-level filter
+
ObserveQuickActionsUseCase merge filter
+
Execution-time verification
```

هذا Defense-in-depth مقصود.

لا تحذف أي طبقة بحجة "التكرار".

---

# 20. Ordering Must Not Regress

`ObserveOrderedQuickActionsUseCase` الحالي جيد.

338 لا تغير قواعد ترتيب المستخدم.

يجب أن تظل:

```text
explicit save only
organization + user scope
temporarily hidden IDs preserved
newly restored actions appended deterministically
reset restores default
```

---

# 21. Ordering Save Race

أثناء فتح Organizer قد تتغير الصلاحيات.

الكود الحالي يعيد:

```text
currentlyAvailable
normalize
merge with stored order
```

هذا السلوك يجب الحفاظ عليه.

### اختبارات إلزامية

```text
permission removed while organizer open
permission added while organizer open
hidden action returns to safe prior slot
new action added
duplicate requested IDs rejected
blank ID rejected
```

---

# 22. Visual Organizer Frozen

لا تغير:

```text
drag UI
long-press behavior
dialog appearance
buttons
layout
labels
spacing
```

أي تحسين Accessibility بصري أو إعادة ترتيب للـOrganizer مؤجل.

يسمح فقط بإصلاح engineering defect غير المرئي إن وجد.

---

# 23. Home Quick Actions UI Frozen

الملفات:

```text
HomeQuickActions.kt
HomeQuickActionComponents.kt
```

تعتبر **محميّة بصريًا**.

يجوز تعديلها فقط إذا احتاج dispatch أن يمرر:

```text
action.id بدل QuickAction object
```

أو wiring هندسي مماثل.

### لا يجوز أن يتغير:

```text
LazyRow
width calculation
visibleCount policy
spacing
snap behavior
organizer tile position
indicators
tile styling
icon
label
busy visual behavior
```

---

# 24. Scroll State Must Remain Compatible

الحفاظ على:

```text
quickActionScrollKey
quickActionScrollOffset
```

وأي restoration logic الحالي.

بعد 338:

```text
عودة الشاشة
تغير state
permission refresh
```

لا يعيد المستخدم لبداية الشريط دون سبب إذا كان behavior الحالي يحافظ على الموضع.

---

# 25. No Business Redesign

338 لا تملك صلاحية تغيير:

```text
Invoice creation rules
Purchase rules
Inventory item creation rules
Customer creation rules
Payment posting rules
Expense accounting rules
Quick stock report content
Cash register semantics
Sync/outbox semantics
```

فقط hardening للمسار الذي ينطلق من Quick Actions.

---

# 26. No Schema Changes

ممنوع:

```text
Room version bump
new migration
entity change
table change
column change
index change
server SQL change
Supabase change
```

Quick Action ordering schema الحالي يبقى كما هو.

---

# 27. Parallel Sessions Safety

كما في 337، التنفيذ قد يتم فوق نسخة أحدث من Verto-v337-pending-actions-repaired.zip.

لذلك:

1. `Verto-v337-pending-actions-repaired.zip` = Source of Truth.
2. عند التنفيذ استخدم أحدث Source of Truth من المستخدم.
3. احسب SHA-256.
4. قارن الملفات الحساسة.
5. لا تستبدل ملفًا أحدث بنسخة Verto-v337-pending-actions-repaired.zip.
6. ادمج 338 فوق أحدث تغييرات.
7. إذا حدث conflict جوهري:
   - `BLOCKED_PENDING_SCOPE_DRIFT`

### ممنوع

```text
mass restore
whole-file replacement from Verto-v337-pending-actions-repaired.zip
rollback changes from parallel sessions
mass formatting
```

---

# 28. البصمات المرجعية للملفات الحساسة

```text
HomeContracts.kt
3037206ce2c40b3cdadfd3048499648d7e3735e274b303602db9b4dd21bbc2db

ObserveQuickActionsUseCase.kt
e7e788ef133dd4d3a2d90e7d36d2e3372dcce43245b8771e9358fc42c7e10e8e

ObserveOrderedQuickActionsUseCase.kt
fc2e13b3cfa68f29dc172553f87acf14cd6adf474e08e2e4f2c7ec2a8ca7811c

QuickActionProviderRegistry.kt
f94fe8cafc9d7dc52610530c7947573ad11bb1823f6b455a81d58ec991c99384

PaymentQuickActionProvider.kt
22b4c49f50e1d0274bdae279aeb6e435c4fc4c03dfaf913e0743bbcff00ffa7b

AndroidQuickStockCountDocumentGateway.kt
9671a3cb41fcaab771d081f36fffda7bdad6b028b73367de9fce64337b602bba

ExpensesOperationsAdapter.kt
4b99262e4c6d190fb6a7eed5252591e520e3d35ea5a7ce36a5329e52722a874c

QuickActionDestinationResolver.kt
4c6ed8b961626eef1d8e4787884819fdfe2c510e48126e5ba57d89457353b14f

HomeQuickActions.kt
c35ea59f6b2fa1f60515fa5b3e34c600a41ad74775eb8e387441738633b42f2c

HomeQuickActionComponents.kt
047db30acb070c96335587610e1d4062f002a19c61cbeb340c130090bcdf6363

HomeQuickActionEffect.kt
a43d8eb0a49f786f4ae91ba7aca883db9290a5f52e398e27046a25b0cd3da217

HomeViewModel.kt
f13f99e42a2355a31baf9b306e3cefd3f9ffe7d0f577adfcdc18ebaf9700961a
```

هذه fingerprints لإثبات ما تم فحصه، وليست أمر rollback.

---

# 29. الملفات المسموح تعديلها

## Dashboard Quick Action application

```text
feature/dashboard/.../quickaction/*
```

## Dashboard API

```text
HomeContracts.kt
```

فقط إذا احتاج hardening additive/minimal.

لا تغيّر public contract بشكل واسع إذا كان الحل الأبسط آمنًا.

## Feature providers

```text
InvoiceQuickActionProvider.kt
InventoryQuickActionProvider.kt
ExpensesQuickActionProvider.kt
PartyQuickActionProvider.kt
PaymentQuickActionProvider.kt
```

تعديلات validation/security فقط.

## Quick Stock

```text
AndroidQuickStockCountDocumentGateway.kt
QuickStockCountContract.kt
QuickStockRowsSource.kt
RoomQuickStockRowsSource.kt
```

فقط threading/testability/hardening.

## App shell

```text
QuickActionDestinationResolver.kt
HomeQuickActionEffect.kt
HomeViewModel.kt
```

## Shared expense bridge

```text
ExpensesOperationsAdapter.kt
```

فقط permission enforcement حول `addExpense()` مع audit مطابق للنمط الحالي.

## UI wiring

```text
HomeQuickActions.kt
HomeQuickActionComponents.kt
```

فقط wiring غير بصري عند الضرورة.

---

# 30. الملفات المحمية

ممنوع لمس:

```text
HomeHeader*
HomePendingAction*
HomeActivity*
Drawer*
Search*
FAB*
Navigation routes unrelated to resolver
Invoice write logic
Payment write logic
Inventory movement logic
Expense UI screen
Cashbox UI
Sync*
Server*
Room migrations
```

إلا ضرورة مثبتة ومحدودة جدًا.

---

# 31. Quick Stock Data Scope

إذا `QuickStockRowsSource` يقرأ كل المخزون المطلوب للجرد فهذا قد يكون صحيحًا وظيفيًا.

338 لا تغير محتوى تقرير الجرد.

لكن يجب التحقق من:

```text
no accidental cross-organization data
no archived/service leakage if current contract excludes them
deterministic ordering
no Main-thread rendering
```

لا تضف pagination للتقرير إذا كانت ستغير مخرجاته.

---

# 32. Tenant Safety

Quick Actions نفسها مرتبطة بـ:

```text
HomePermissionContext.organizationId
HomePermissionContext.userId
```

والترتيب محفوظ بهذه الحدود.

يجب اختبار:

```text
Org A order ≠ Org B order
User A order ≠ User B order
```

وأي command يتم تنفيذه يجب أن يستخدم session/org الحالية من write boundary المعتمد.

---

# 33. Expense Audit Requirement

عند رفض `EXPENSES_CREATE`:

سجل audit عبر الأدوات الموجودة.

مثال دلالي:

```text
action = expense_create
source = home_quick_action أو ما يعادلها إن كان النظام يدعم source
reason = permission_denied
```

بدون:

```text
client PII
invoice PII
full expense statement إذا كان حساسًا
```

---

# 34. Error Semantics

لا تعرض success إذا command فشل أو رُفض.

ويجب أن تكون الحالات:

```text
permission denied
validation failure
PDF generation failure
I/O failure
destination rejected
```

قابلة للتمييز داخليًا.

يمكن استخدام `ErrorHumanizer` الحالي للرسالة النهائية.

---

# 35. Cancellation Semantics

`CancellationException` لا تتحول إلى generic error.

في:

```text
expense
stock count
provider observations
effect dispatch
```

يجب الحفاظ على coroutine cancellation.

وفي جميع الحالات:

```text
busy cleanup in finally
```

---

# 36. Destination Test Matrix

## Invoice

```text
type=sale      → sale route
type=purchase  → purchase route
type=""        → reject
missing type   → reject
type=unknown   → reject
```

## Payment

```text
partyType=client    → clients route
partyType=supplier  → suppliers route
missing             → reject
blank               → reject
unknown             → reject
```

## Inventory/Client

```text
known destination → expected route
unknown id        → null
```

---

# 37. Canonical Dispatch Test Matrix

اختبر:

```text
visible canonical action → executes
action id not visible → rejected
action removed by permission change → rejected
forged action object with same id/different destination → canonical destination only
unknown action id → rejected
stale object after provider refresh → canonical current state used
```

---

# 38. Expense Test Matrix

اختبر:

```text
permission granted → one insert
permission denied → zero insert
permission revoked after dialog open → zero insert
invalid amount → zero insert
blank statement → zero insert
double submit → one insert
adapter direct call without permission → zero insert
denied attempt → audit written
```

---

# 39. Quick Stock Test Matrix

اختبر:

```text
permission granted → generate once
permission denied → zero generate calls
permission revoked before execution → zero generate calls
double tap → one generate
generator failure → busy cleared
cancellation → busy cleared
PDF render not on Main
file write not on Main
success → one OpenPdf effect
```

---

# 40. Busy-State Test Matrix

اختبر:

```text
expense busy only
stock count busy only
both busy concurrently
expense completes first
stock completes first
one fails
one is cancelled
```

وفي كل حالة:

```text
other action busy state preserved
```

---

# 41. Effect Delivery Test Matrix

اختبر:

```text
Navigate emitted once
ShowExpenseDialog emitted once
ExpenseSaved emitted once
OpenPdf emitted once
collector attaches slightly later
two distinct effects in sequence
```

لا silent loss.

---

# 42. Provider Tests

تغطية مباشرة لـ:

```text
InvoiceQuickActionProvider
InventoryQuickActionProvider
ExpensesQuickActionProvider
PartyQuickActionProvider
PaymentQuickActionProvider
```

لكل Provider:

```text
required permission absent
required permission present
correct id
correct label
correct destination
correct defaultOrder
declared ids match emitted ids
```

---

# 43. Registry Tests

اختبر:

```text
duplicate provider ID → fail
duplicate action ID across providers → fail
blank declared ID → fail
deterministic provider sort
```

---

# 44. ObserveQuickActionsUseCase Tests

اختبر:

```text
no providers → empty
allowed actions only
provider emits undeclared ID → fail
duplicate emitted action IDs → fail
default ordering deterministic
```

إذا تم إضافة provider isolation:

```text
one provider failure does not kill all
CancellationException propagates
```

---

# 45. ObserveOrderedQuickActionsUseCase Tests

اختبر:

```text
default order
saved order
new action appended
hidden action preserved
restored permission returns action
save explicit order
reset
tenant scope
user scope
permission changed while organizer open
```

---

# 46. UI Regression Gate — Visual Freeze

338 يجب أن يتضمن verification يثبت عدم تغير هذه القيم/البنية بصريًا.

على الأقل static assertions أو diff review:

```text
LazyRow still present
rememberSnapFlingBehavior still present
QuickActionPageIndicators still present
Organizer remains last item
visibleCount policy unchanged
actionWidth calculation unchanged
spacing unchanged
tile style tokens unchanged
labels unchanged
icons unchanged
```

إذا أمكن Screenshot test:

```text
pre-338 reference
post-338
```

والنتيجة يجب أن تكون visually equivalent.

---

# 47. No UX Change to Payment

اختبار regression:

عند الصلاحيتين معًا:

```text
PaymentQuickActionProvider behavior remains deterministic
```

ولا تظهر:

```text
new chooser
new dialog
second payment tile
new label
```

---

# 48. No UX Change to Busy State

لا تغير spinner/disabled styling الحالي.

يسمح فقط بإصلاح المنطق الذي يحدد `busyActionIds`.

---

# 49. No UX Change to Organizer

لا تغير drag affordance أو شكل dialog.

إذا ظهرت مشكلة Accessibility تحتاج تغييرًا بصريًا:

```text
DEFERRED
```

ولا تدخلها في 338.

---

# 50. Static Build Gates

شغّل ما تسمح به البيئة:

```bash
./gradlew :feature:dashboard:testDebugUnitTest
./gradlew :feature:invoice:testDebugUnitTest
./gradlew :feature:inventory:testDebugUnitTest
./gradlew :feature:expenses:testDebugUnitTest
./gradlew :feature:party:testDebugUnitTest
./gradlew :feature:payment:testDebugUnitTest
./gradlew :app:testDebugUnitTest
```

ثم إن أمكن:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

استخدم أسماء tasks الحقيقية للـmodules.

---

# 51. ممنوع تجاوز الاختبارات

ممنوع:

```text
-x test
ignoreFailures
|| true
تعليق test
حذف test
تحويل assertion إلى log
```

---

# 52. Static Assertions

يجب أن يفحص verification script/report:

```text
Quick Actions UI still uses LazyRow
Quick Action click does not trust arbitrary UI destination
expense add has execution-time permission guard
quick stock rendering is off Main
busy cleanup uses finally or equivalent
resolver does not fallback unknown invoice type to sale
resolver does not fallback unknown payment type to client
Room schema version unchanged
no migration added
```

---

# 53. Performance Verification

لا يلزم تغيير تصميم أو إضافة benchmark framework جديد.

إذا توفر runtime:

### Dataset

```text
100
1,000
5,000 inventory rows
```

### اختبر

```text
tap quick stock
Home frame responsiveness
PDF generation completion
no ANR
no long Main-thread block
```

الهدف الأساسي:

```text
Main-thread PDF render/write = 0
```

وليس رقم milliseconds مصطنعًا.

---

# 54. StrictMode / Thread Verification

إذا أمكن:

شغّل Quick Stock مع StrictMode أو test dispatcher evidence.

يجب ألا يظهر:

```text
disk write on main
long PDF rendering on main
```

---

# 55. Scope Diff Gate

قبل التسليم:

صنف الملفات:

```text
338-owned
shared-minimal
unrelated
```

يجب أن يكون:

```text
unrelated = 0
```

وأي `shared-minimal` يجب شرحه.

---

# 56. No Mass Refactor

ممنوع تحويل 338 إلى:

```text
HomeViewModel full refactor
Navigation architecture refactor
Dashboard API rewrite
Command bus migration
MVI migration
new design system
new Quick Actions UX
```

الهدف Hardening محدود.

---

# 57. Preferred Implementation Shape

هذا شكل إرشادي، وليس إلزاميًا حرفيًا:

```text
UI:
onClick(action.id)

HomeViewModel:
resolveCurrentAuthorizedAction(actionId)
    ↓
canonical action
    ↓
dispatch

Navigation actions:
canonical destination
    ↓
QuickActionDestinationResolver
    ↓
fail closed

Expense:
show existing dialog
    ↓
submit
    ↓
recheck authorization
    ↓
adapter permission enforcement
    ↓
write

Quick stock:
recheck authorization
    ↓
busy guard
    ↓
off-main generate
    ↓
OpenPdf effect
```

---

# 58. Do Not Overengineer QuickAction Model

لا يلزم في 338 تحويل `QuickAction` إلى sealed command architecture إذا كان:

```text
canonical lookup
execution guard
strict resolver
```

يحققان الهدف بأقل diff.

إذا اختار المنفذ تغيير public contract:

يجب إثبات أن التغيير:

```text
smaller risk
fully tested
does not touch visual UX
does not affect unrelated features
```

وإلا ارفضه.

---

# 59. HomeViewModel Modification Rule

يسمح فقط بتعديل:

```text
quickActions state
quickAction effects
busy quick actions
onQuickActionClicked
addExpenseFromHome
generateQuickStockCount
save/reset order if tests expose bug
```

لا تلمس:

```text
Pending Actions
Activity Feed
Header
Search
Drawer
Sync refresh
```

---

# 60. Definition of Done

لا تعتبر 338 مكتملة إلا إذا تحقق:

```text
[ ] latest execution baseline SHA recorded
[ ] no unrelated rollback
[ ] horizontal LazyRow unchanged
[ ] visual layout unchanged
[ ] organizer visually unchanged
[ ] indicators unchanged
[ ] click uses canonical current action
[ ] stale action rejected
[ ] permission rechecked at dispatch
[ ] expense permission rechecked at command execution
[ ] ExpensesOperationsAdapter blocks unauthorized addExpense
[ ] denied expense audited
[ ] quick stock permission rechecked at execution
[ ] PDF rendering/writing off Main
[ ] busy cleanup guaranteed
[ ] busy updates concurrency-safe
[ ] double-tap prevented
[ ] one-shot effects not silently dropped
[ ] destination resolver fail-closed
[ ] payment UX unchanged
[ ] ordering semantics unchanged
[ ] org/user ordering isolation tested
[ ] provider tests added
[ ] registry tests added
[ ] ordered use case tests added
[ ] resolver tests added
[ ] HomeViewModel quick-action tests added
[ ] expense guard tests added
[ ] quick-stock thread/guard tests added
[ ] Room schema unchanged
[ ] no migration
[ ] no sync/server change
[ ] static gates attempted honestly
[ ] runtime limitations documented honestly
```

---

# 61. Final Verdict States

## PASS

فقط إذا:

```text
engineering implementation complete
tests pass
no visual change
no scope breach
no permission gap
no Main-thread PDF generation
no resolver fallback defect
```

## PASS_STATIC_RUNTIME_BLOCKED

يجوز فقط إذا:

```text
implementation complete
static/unit gates pass
runtime/device-only validation blocked by environment
```

## FAIL_VISUAL_SCOPE_BREACH

أي تغيير بصري/UX في Quick Actions.

## FAIL_SECURITY

إذا بقي:

```text
stale action executable
expense unauthorized write possible
stock count unauthorized execution possible
```

## FAIL_THREADING

إذا بقي PDF rendering/file writing على Main.

## FAIL_CORRECTNESS

إذا تغير:

```text
ordering
labels
payment behavior
route semantics for valid inputs
```

بشكل غير مقصود.

## FAIL_PARALLEL_SESSION_SAFETY

إذا تم rollback أو overwrite لتغييرات جلسات أخرى.

## BLOCKED_PENDING_SCOPE_DRIFT

إذا latest source تغير جذريًا داخل نفس الملفات ولا يمكن الدمج بأمان.

---

# 62. التقرير النهائي الإلزامي

أنشئ:

```text
VERTO_SESSION_338_VERIFICATION.md
```

ويحتوي:

## Baseline

```text
filename
SHA-256
archive entries
Kotlin count
Room schema version
```

## Scope

```text
files added
files modified
files deleted
```

## Engineering Before/After

| المشكلة | قبل | بعد | الدليل |
|---|---|---|---|
| UI object trust | مباشر | canonical dispatch | test/source |
| stale permission | قابل للسباق | fail closed | test |
| expense authorization | visibility-based | execution guard | test/audit |
| stock threading | dispatcher غير واضح | off-main | test |
| busy cleanup | غير مضمون | guaranteed | test |
| busy concurrency | read-modify-write | atomic | test |
| effect delivery | tryEmit قابل للفقد | reliable | test |
| invoice resolver | unknown→sale | reject | test |
| payment resolver | unknown→client | reject | test |

## Visual Freeze Evidence

```text
LazyRow unchanged
snap unchanged
indicators unchanged
tile dimensions unchanged
spacing unchanged
organizer unchanged
labels/icons unchanged
```

## Tests

لكل command:

```text
command
result
failure reason
```

## Runtime

```text
device/emulator
StrictMode/thread evidence
BLOCKED_ENVIRONMENT if unavailable
```

## Verdict

واحد فقط من القسم 61.

---

# 63. Artifact التسليم

اسم مقترح:

```text
Verto-v338-quick-actions-engineering-hardened.zip
```

ويحتوي:

```text
source
tests
VERTO_SESSION_338_VERIFICATION.md
```

ولا يحذف ملفات أو تقارير جلسات أخرى.

---

# 64. جملة الإغلاق المطلوبة

عند النجاح:

```text
Session 338 completed: Home Quick Actions were hardened engineering-wise only.
The horizontal LazyRow UX and visual presentation remain unchanged.
Execution-time permissions, trusted dispatch, off-main Quick Stock generation,
fail-closed destination resolution, concurrency safety, and tests were added.
No Room schema, server, sync, or unrelated feature behavior was changed.
```

إذا runtime محجوب:

```text
Session 338 implementation is statically complete.
Runtime-only verification is BLOCKED_ENVIRONMENT and is not claimed as PASS.
```

---

# 65. المعنى الحاكم للعقد

338 ليست جلسة إعادة تصميم.

هي جلسة:

```text
نفس Quick Actions التي يراها المستخدم اليوم
+
نفس التمرير الأفقي
+
نفس الشكل
+
نفس الترتيب
+
تنفيذ داخلي أكثر أمانًا وصحة وقابلية للاختبار
```

أي تحسين يحتاج تغييرًا بصريًا أو اختيارًا جديدًا للمستخدم:

```text
DEFER
```

ولا يدخل 338.
