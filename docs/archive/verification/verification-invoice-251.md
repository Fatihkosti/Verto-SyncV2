# Verto Invoice Repair — Session 251 Partial Verification

## Decision

**BLOCKED**

هذه الجلسة نُفذت جزئيًا داخل GPT فقط، ولم يبدأ أي جزء من 252.

سبب القرار: اختبارات المصدر والمحاكاة المحلية نجحت، لكن بوابة 251 تشترط إثبات Gradle/Room Migration/DB Instrumented/Device/E2E على بيئة Android فعلية. Gradle 8.9 غير متاح في البيئة الحالية، كما أن ملفات Room schema التاريخية المطلوبة غير مكتملة. لذلك لا يجوز اعتبار 251 ناجحة.

---

## Source of Truth

- Input: `Verto-v250-source-of-truth.zip`
- Output: `Verto-v251-partial-source-of-truth.zip`
- Room schema عند البداية: **67**
- Room schema بعد إصلاح P1 المكتشف في 251: **68**
- Migration الجديدة: `MIGRATION_67_68`

---

## مراجعة الجلسات 243–250

| Session | ما تم تثبيته/مراجعته | نتيجة إعادة التحقق داخل GPT |
|---|---|---|
| 243 | baseline + invariants + flow map + allowlist | تمت مراجعة وثائق baseline؛ verifier التاريخي توصيفي للنسخة 243 وليس gate صالحًا للنسخة الحالية |
| 244 | Money / Quantity / ExchangeRate + fixed-point shadows | `V244_MONEY_CORE_PASS`, `V244_MIGRATION_SQL_PASS` |
| 245 | atomic transaction + idempotency + conditional stock + concurrency model | `V245_ATOMIC_INVARIANTS_PASS`, `V245_MIGRATION_SQL_PASS` |
| 246 | multi-currency + payment allocation + realized FX + unknown legacy currency | `V246_CURRENCY_TRUTH_PASS` |
| 247 | Last Purchase Price + revaluation + unitCostAtSale + Landed Cost | `V247_INVENTORY_COSTING_PASS` |
| 248 | lifecycle POSTED/VOID + reversal + optimistic versioning | `V248_INVOICE_LIFECYCLE_PASS` |
| 249 | durable financial Outbox/Inbox + retry/order/conflict contracts | `V249_FINANCIAL_SYNC_PASS` |
| 250 | mixed-currency reports + historical margin + reconciliation diagnostics | `V250_REPORTS_RECONCILIATION_PASS` |

تم تعديل verifiers التاريخية 244/245/248/249/250 بحيث تقبل schema أحدث من schema الجلسة الأصلية بدل الفشل لمجرد أن Room تقدم من 62/63/66/67 إلى 68. الشروط الوظيفية الأصلية بقيت كما هي.

---

## ما تم فحصه في 251

### Money Core
- `Money`, `Quantity`, `ExchangeRate`, parsing/rounding/overflow.
- write authorization للسداد أصبح يعتمد `SUM(amount_minor)` بدل `SUM(amount)`.
- السداد الجماعي أصبح يقسم ويحسب المتبقي والـFX بـminor units بدل حسابات `Double` داخلية.
- فائض السداد `client_credits` كان لا يزال `Double/SUM(REAL)`؛ تم إصلاحه إلى `amount_minor` كمصدر الحقيقة مع compatibility projection فقط.

### Transactions / Idempotency / Concurrency
- invoice transaction ownership وإجبار child failures على rollback.
- conditional stock deduction.
- durable write guard.
- payment retry identity.
- bulk-payment child IDs deterministic من parent requestId.
- payment/invoice/bulk requestId أصبح يحتفظ به عبر `SavedStateHandle` حتى النجاح، فلا يتغير بعد process recreation.

### Multi-currency
- transaction currency منفصلة عن functional currency.
- recognition-rate snapshot ثابت.
- payment rate/cash/realized FX محفوظة كحقائق تاريخية.
- third currency مرفوضة.
- legacy international unknown يفشل مغلقًا.

### Last Purchase Price / unitCostAtSale / Landed Cost
- السياسة ما زالت: أحدث سعر شراء يعيد تسعير كامل الرصيد الحالي؛ لا Weighted Average.
- sale cost snapshots لا يعاد حسابها بعد تغير السعر.
- international purchase لا يدخل المخزون قبل receipt.
- landed cost يعتمد accepted received quantities والتوزيع deterministic.

### Lifecycle / Void / Reversal
- POSTED immutable ماليًا.
- VOID يحتاج reason/permission/version guard.
- paid invoice يحتاج reversal صريح.
- reversal يستخدم historical payment/allocation/FX/cash facts.
- hard delete للـPOSTED/VOID ممنوع.

### Outbox / Inbox
- durable Outbox داخل owner transaction.
- unique `(organization, operation, writeId)`.
- aggregate sequence + predecessor blocking.
- Inbox dedupe/server revision.
- out-of-order/conflict policy لا يستخدم LWW للـPOSTED/VOID.
- retry backoff محدود.

### Reports / Reconciliation
- mixed currencies تجمع stored functional snapshots فقط.
- historical gross profit يستخدم cost snapshots.
- current replacement margin منفصل.
- reconciliation يغطي invoice/payment/cash/inventory/outbox identity diagnostics.

### Room Migrations
- `MigrationCatalog` متصل برمجيًا من **1 → 68** دون gap في التسجيل: `MIGRATION_CATALOG_1_TO_68_CONTIGUOUS_PASS`.
- ملفات schema الموجودة فعليًا: `39..55`, `60`, `61` فقط.
- ملفات schema المفقودة ضمن نطاق الاختبار 39→68: **56, 57, 58, 59, 62, 63, 64, 65, 66, 67, 68**.
- لا تم اختلاق JSON schemas يدويًا؛ يجب استعادتها/تصديرها من النسخ التاريخية المطابقة.

---

## أخطاء P0/P1 المكتشفة وإصلاحها

### P0
- **لا يوجد P0 مؤكد مكتشف من الفحص المحلي الحالي.**
- هذا لا يعني إثبات غياب P0 على جهاز/قاعدة حقيقية؛ الاختبارات الكاملة لم تعمل.

### P1-251-01 — direct remote-first payment/reversal
**المشكلة:** عند تفعيل financial mutation flag كان `RecordPaymentCoordinator`/`ReversePaymentCoordinator` يستطيعان استدعاء الخادم قبل تثبيت المعاملة المحلية. نجاح الخادم ثم ضياع الرد/فشل المحلي يخرق offline-first وexactly-once intent.

**الإصلاح:** إزالة الفرع remote-first من مسار التنفيذ؛ الدفع والعكس أصبحا local transaction + durable Outbox فقط، وطبقة sync هي المالكة للتسليم/retry.

**اختبار regression مضاف:** `PaymentCoordinatorsTest` يثبت أن تفعيل legacy remote flag لا يستدعي remote mutation.

### P1-251-02 — overpayment authorization على `Double SUM`
**المشكلة:** التحقق النهائي من المتبقي كان يعتمد `SUM(amount)`/`Double`.

**الإصلاح:** إضافة `getTotalPaidMinor()` عبر DAO/repository/port واستخدام `invoice.totalAmountMinor - paidMinor` داخل owner transaction.

### P1-251-03 — request identity تضيع بعد process death
**المشكلة:** payment/invoice/bulk payment IDs كانت في ذاكرة ViewModel أو تُولد من جديد في بعض المسارات، ما يسمح لهوية جديدة عند إعادة إنشاء العملية.

**الإصلاح:** حفظ pending request/write IDs في `SavedStateHandle` حتى نجاح العملية، وتمرير parent `requestId` إلى bulk allocator بدل `UUID.randomUUID()` داخل كل retry.

### P1-251-04 — advance credit ليس fixed-point
**المشكلة:** `client_credits.amount` و`SUM(amount)` كانا مصدر الحقيقة المالي لفائض السداد الجماعي، مخالفًا invariant 243 بأن القيم المالية لا تعتمد Double/Float كمصدر حقيقة.

**الإصلاح:** schema **67→68** يضيف `amount_minor INTEGER`, backfill + range guards؛ entity/domain/DAO/sync أصبحت تعتمد minor units، و`Double` بقي boundary compatibility فقط.

### P1-GATE-251-05 — Room schema exports ناقصة — **متبقٍ ومانع**
لا يمكن تشغيل `MigrationTestHelper` لكل المسارات المدعومة لأن schemas **56–59 و62–68** غير موجودة. هذا مانع خروج حقيقي حتى لو كان MigrationCatalog متصلًا.

---

## الاختبارات التي أضيفت أو عُدلت

### Unit tests
- `core/common/src/test/kotlin/com/verto/app/money/MoneyPropertyF251Test.kt`
  - 2,000 حالة deterministic add/subtract/serialization round-trip.
  - HALF_UP boundary.
  - deterministic exchange-rate conversion.
- `feature/payment/src/test/kotlin/com/verto/app/feature/payment/application/PaymentCoordinatorsTest.kt`
  - overpayment.
  - DB minor total ضد presentation stale remaining.
  - same request retry = one local effect.
  - legacy remote flag لا يتجاوز local-first.
  - reversal duplicate/remote regression.
- اختبارات 244/245/248/249/250 static verifier عُدلت لتكون schema-forward-compatible.

### DB Integration tests — مكتوبة ولم تُشغّل
`data/database/src/androidTest/kotlin/com/verto/app/data/local/InvoiceFinancialDb251Test.kt`
- duplicate write identity.
- rollback بعد injected mid-transaction failure.
- بيعان متزامنان لآخر قطعة: exactly one succeeds.
- local purchase 10@100 + 10@200 => 20@200 مع revaluation 1000.
- advance credit 0.1 + 0.2 => 30 minor units.
- duplicate Outbox identity stored once.

### Migration tests — مكتوبة ولم تُشغّل
`data/database/src/androidTest/kotlin/com/verto/app/data/local/InvoiceFinancialMigration251Test.kt`
- كل schema من 39 حتى الحالي إلى 68.
- legacy international v61 يبقى `UNKNOWN` بعد migration.
- v67 client credit 0.1 + 0.2 backfill إلى 30 minor units.

### Sync Contract tests — لا تحتاج server فعليًا، مكتوبة ولم تُشغّل عبر Gradle
`data/network/src/test/kotlin/com/verto/app/data/sync/FinancialSyncContractF251Test.kt`
- older remote version keeps local.
- equal-version POSTED echo idempotent.
- POSTED→VOID next version clean/dirty behavior.
- VOID cannot resurrect as POSTED.
- version jump requires review.
- retry backoff monotonic/capped.

### Local executable verification tools
- `tools/verify_v251_local_contracts.py`
  - source guards.
  - SQLite rollback.
  - duplicate write/outbox identity.
  - two contenders for final stock item.
- `tools/verify_v251_client_credit_money.py`
  - fixed-point client credit source/sync/migration contract.

---

## ما تم تشغيله فعليًا داخل GPT

نجح:

```text
V244_MONEY_CORE_PASS
V244_MIGRATION_SQL_PASS
V245_ATOMIC_INVARIANTS_PASS
V245_MIGRATION_SQL_PASS
V246_CURRENCY_TRUTH_PASS
V247_INVENTORY_COSTING_PASS
V248_INVOICE_LIFECYCLE_PASS
V249_FINANCIAL_SYNC_PASS
V250_REPORTS_RECONCILIATION_PASS
V251_CLIENT_CREDIT_MONEY_PASS
V251_LOCAL_CONTRACTS_PASS
MIGRATION_CATALOG_1_TO_68_CONTIGUOUS_PASS
F251_MONEY_KOTLIN_HARNESS_PASS
```

`verify-kotlin-quality-static.py scan` اشتغل وخرج بالمقاييس الحالية، بدون dependency cycles/global scope/exposed mutable state؛ لكنه ليس بديلًا عن compile/lint.

### Gradle الفعلي
تمت محاولة:

```bash
./gradlew :core:common:testDebugUnitTest \
  :feature:invoice:testDebugUnitTest \
  :feature:payment:testDebugUnitTest \
  :feature:inventory:testDebugUnitTest \
  :feature:shipment:testDebugUnitTest \
  :feature:reports:testDebugUnitTest \
  :data:operations:testDebugUnitTest \
  :data:network:testDebugUnitTest \
  --offline --no-daemon
```

النتيجة: **لم تبدأ أي مهمة Gradle/Kotlin**. الـwrapper حاول تنزيل `gradle-8.9-bin.zip` ثم فشل بـ:

```text
java.net.UnknownHostException: services.gradle.org
```

لذلك لا يوجد ادعاء بأن Unit tests عبر Gradle أو Room compile أو build نجحت.

---

## سيناريوهات الإغلاق الإلزامية

| # | السيناريو | تحقق داخل GPT | حالة بوابة 251 |
|---|---|---|---|
| 1 | oversell والسالب ممنوع | SQLite F245/F251 PASS + Android DB test مكتوب | PARTIAL — Room test لم يعمل |
| 2 | فشل منتصف Transaction | SQLite rollback PASS + Android DB test مكتوب | PARTIAL |
| 3 | بيعان متزامنان لآخر قطعة | SQLite concurrency PASS + Android DB test مكتوب | PARTIAL |
| 4 | local purchase يعيد تسعير كامل الرصيد | F247 PASS + Android DB test مكتوب | PARTIAL |
| 5 | sale historical cost لا يتغير | F247 PASS + unit test موجود | PARTIAL — Gradle لم يعمل |
| 6 | international purchase لا يدخل stock قبل receipt | F247/source test contract موجود | PARTIAL — Gradle لم يعمل |
| 7 | partial receipt + Landed Cost | F247 + LandedCost tests موجودة | PARTIAL — Gradle لم يعمل |
| 8 | دفعتان دوليتان بسعرين وFX صحيح | F246 arithmetic PASS + unit test موجود | PARTIAL — Gradle لم يعمل |
| 9 | Void بعد partial payment | F248 contract + unit test موجود | PARTIAL — Gradle لم يعمل |
| 10 | server success ثم response lost/retry | F249 SQLite exact-once simulation PASS + local-first regression | PARTIAL — live server غير متحقق |
| 11 | migration لسجل دولي مجهول | SQLite F246 PASS + MigrationTest مكتوب | **BLOCKED** — schema assets/device missing |
| 12 | mixed-currency report + full reconciliation | F250 PASS + tests موجودة | PARTIAL — real DB/Gradle لم يعمل |

لا يوجد أي سيناريو يُعلن CLOSED بالكامل اعتمادًا على قراءة الكود فقط.

---

## ما تعذر التحقق منه داخل GPT

1. Gradle 8.9 compilation/tests/build.
2. Room code generation/schema export للنسخة 68.
3. `MigrationTestHelper` لكل 39→68.
4. DB integration الحقيقي عبر Android SQLite/Room.
5. rotation/process death/double tap/RTL على Android lifecycle حقيقي.
6. Supabase SQL F249 على server فعلي، بما في ذلك response-lost retry الحقيقي وRLS/constraints.
7. old-client/new-client coexistence على backend فعلي.
8. realistic large DB migration/performance.
9. full E2E: invoice types × payment × Post/Payment/Void/Receive/Sync.
10. reconciliation=zero على نسخة قاعدة بيانات واقعية للمستخدم.
11. backup/restore recovery drill فعلي.
12. `assembleDebug`, `lintDebug`, release-level smoke tests.

---

## أوامر Gradle الدقيقة المطلوبة على اللابتوب

### 0 — تأكد أن Gradle wrapper 8.9 متاح

```bash
cd /path/to/Verto-v251-partial-source-of-truth
chmod +x gradlew
./gradlew --version
```

### 1 — تصدير schema 68 الحالي

```bash
./gradlew :data:database:kspDebugKotlin --no-daemon
```

ثم تحقق:

```bash
test -f app/schemas/com.verto.app.data.local.AppDatabase/68.json
```

**مهم:** هذا لا يعيد إنشاء schemas التاريخية 56–59 و62–67. يجب استعادتها من Source-of-Truth المطابق لكل schema أو checkout تاريخي مطابق، ثم الاحتفاظ بها في نفس مجلد schemas. لا تُنشأ يدويًا.

### 2 — Unit tests

```bash
./gradlew \
  :core:common:testDebugUnitTest \
  :feature:invoice:testDebugUnitTest \
  :feature:payment:testDebugUnitTest \
  :feature:inventory:testDebugUnitTest \
  :feature:shipment:testDebugUnitTest \
  :feature:reports:testDebugUnitTest \
  :feature:party:testDebugUnitTest \
  :data:operations:testDebugUnitTest \
  :data:network:testDebugUnitTest \
  --no-daemon
```

### 3 — Compile modules المتأثرة

```bash
./gradlew \
  :data:database:compileDebugKotlin \
  :data:operations:compileDebugKotlin \
  :data:network:compileDebugKotlin \
  :feature:invoice:compileDebugKotlin \
  :feature:payment:compileDebugKotlin \
  :feature:inventory:compileDebugKotlin \
  :feature:shipment:compileDebugKotlin \
  :feature:reports:compileDebugKotlin \
  :feature:party:compileDebugKotlin \
  :app:compileDebugKotlin \
  --no-daemon
```

### 4 — Room DB integration فقط — Emulator/Device

```bash
./gradlew :data:database:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.verto.app.data.local.InvoiceFinancialDb251Test \
  --no-daemon
```

### 5 — Room migrations فقط — بعد استعادة كل schemas

```bash
./gradlew :data:database:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.verto.app.data.local.InvoiceFinancialMigration251Test \
  --no-daemon
```

### 6 — كل database instrumented tests

```bash
./gradlew :data:database:connectedDebugAndroidTest --no-daemon
```

### 7 — build + lint

```bash
./gradlew :app:assembleDebug :app:lintDebug --no-daemon
```

### 8 — static/local contract verification

```bash
python3 tools/verify_v244_money_core.py
python3 tools/verify_v244_migration_sql.py
python3 tools/verify_v245_atomic_invariants.py
python3 tools/verify_v245_migration_sql.py
python3 tools/verify_v246_currency_truth.py
python3 tools/verify_v247_inventory_costing.py
python3 tools/verify_v248_invoice_lifecycle.py
python3 tools/verify_v249_financial_sync.py
python3 tools/verify_v250_reports_reconciliation.py
python3 tools/verify_v251_client_credit_money.py
python3 tools/verify_v251_local_contracts.py
python3 scripts/verify-kotlin-quality-static.py scan
```

---

## Emulator / Device tests المطلوبة

1. تشغيل `InvoiceFinancialDb251Test` كاملًا.
2. تشغيل `InvoiceFinancialMigration251Test` بعد استعادة schemas.
3. Add Payment: double tap سريع → حركة مالية واحدة فقط.
4. Add Payment: ابدأ الحفظ، اقتل process، أعد فتح الشاشة/العملية → retry بنفس requestId ولا duplicate.
5. Add Invoice/AddDebt: نفس اختبار process death للـwriteId.
6. Client/Supplier bulk payment: process death/retry → نفس parent requestId؛ لا payment/credit duplicate.
7. rotation أثناء payment/invoice submit.
8. RTL مع field validation قرب الحقل، وعدم فقد الإدخال/الهوية.
9. offline create/post/payment/void ثم reconnect وترتيب Outbox.
10. server response-lost simulation ثم retry؛ server financial effect واحد.
11. out-of-order/duplicate remote events؛ لا resurrect للـVOID ولا field merge للـPOSTED.
12. international purchase → no stock قبل receiving؛ partial receipt → landed cost accepted-only.
13. two-device/parallel sale لآخر قطعة على قاعدة حقيقية.
14. large migration DB + large report/reconciliation performance.

---

## Backup / Recovery drill المطلوب قبل اختبار Migration على قاعدة واقعية

اسم قاعدة التطبيق: `verto_db`, application id: `com.verto.app`.

لبيئة اختبار debuggable فقط:

```bash
mkdir -p backup-251
adb shell am force-stop com.verto.app
adb exec-out run-as com.verto.app cat databases/verto_db > backup-251/verto_db
adb exec-out run-as com.verto.app cat databases/verto_db-wal > backup-251/verto_db-wal 2>/dev/null || true
adb exec-out run-as com.verto.app cat databases/verto_db-shm > backup-251/verto_db-shm 2>/dev/null || true
sha256sum backup-251/verto_db*
```

بعد migration، افتح التطبيق وشغّل reconciliation ثم احتفظ بنتائج قبل/بعد. أي فشل migration أو فرق غير مفسر = rollback للاختبار وعدم اعتماد 251.

---

## Allowlist evidence notes

تم لمس ملفات `feature/party/**` لأن Client/Supplier bulk-payment surfaces هي callers مباشرة لمسار invoice/payment المالي. بدون تمرير requestId ثابت وحساب advance credit من minor units، كان retry/process death يستطيع إنشاء هوية مالية جديدة أو إعادة إدخال Double كمصدر حقيقة. لم يتم تغيير UX الطرف/المورد أو إضافة ميزة جديدة.

تم لمس `SyncClientCredits.kt` لأن advance credit الناتج من السداد الجماعي يجب أن يغادر/يدخل boundary الشبكة من `amount_minor` عبر `BigDecimal`، لا من `Double`.

---

## Remaining P0/P1

- **P0 known open:** 0 من الفحص المحلي الحالي.
- **P1 code defects discovered in 251:** 4، تم إصلاحها في المصدر.
- **P1 gate blocker open:** Room schema exports ناقصة: `56,57,58,59,62,63,64,65,66,67,68`.
- **Verification blockers:** Gradle/Room/Device/E2E/live-server لم تُشغّل، ولذلك لا يمكن تحويل القرار إلى PASS داخل GPT.

## Final decision

# BLOCKED

لا تبدأ 252. إعادة تقييم 251 يجب أن تتم بعد استعادة Room schemas وتشغيل الأوامر أعلاه فعليًا على اللابتوب/Emulator أو Device، وإرفاق النتائج دون أي فشل P0/P1 أو اختلاف reconciliation/migration.
