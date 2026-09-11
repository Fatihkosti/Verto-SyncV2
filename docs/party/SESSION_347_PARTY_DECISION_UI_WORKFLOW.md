# VERTO — Session 347 Party Decision UI & Workflow Integration

**Source of Truth:** `Verto-v346-supplier-intelligence-engine.zip`  
**Target:** `Verto-v347-party-decision-ui-workflow.zip`  
**Date:** 2026-08-23

## هدف الجلسة

تحويل نتائج محركات العميل والمورد المبنية في 345 و346 إلى قرارات مرئية وقابلة للتنفيذ داخل Party، مع فرض قرار الائتمان في مسار حفظ فاتورة البيع وإدخال توصية المورد كإرشاد داخل مسار الشراء، من دون نقل منطق القرار إلى UI أو ViewModel.

## التنفيذ

### العميل

- عرض قرار الائتمان داخل لوحة العميل.
- عرض أسباب القرار بصورة قابلة للتفسير.
- عرض الإجراء التالي المقترح.
- عرض التأخر الحالي، دورة الشراء المتوقعة، موعد الشراء المتوقع، وإشارة خطر فقد العميل.
- الحفاظ على الربحية الحالية وعدم اختراع CLV جديد.

### المورد

- عرض Supplier Performance Score بصورة مفسرة.
- عرض Quality / Acceptance، Fill Rate، Return/Rejection، Lead Time، Lead-Time Variability، On-Time Delivery عند توفر Promised Date، Price Variance، والتكلفة الفعلية المطابقة بالفاتورة حسب العملة.
- عدم ادعاء Landed Cost عند غياب بيانات إسناد موثوقة.

### البيع الآجل

أضيفت بوابة قرار مستقلة داخل مسار حفظ الفاتورة نفسه:

- `ALLOW_CREDIT` → يسمح بالحفظ.
- `CASH_ONLY` → يمنع البيع الآجل دائمًا، حتى للمدير.
- `REQUIRES_APPROVAL` → يمنع الموظف ويسمح للمدير/الإداري.

الواجهة تعرض القرار، لكن التنفيذ الحاسم موجود عند `saveInvoice`، لذلك لا يمكن تجاوز القرار بإخفاء أو تخطي عنصر UI.

### الشراء واختيار المورد

- تمت إضافة Narrow Party Decision Read Boundary بدل اعتماد Payment أو Invoice على `PartyApplicationService` الكامل.
- تظهر توصية أفضل مورد داخل محرر الشراء عند توفر تاريخ كافٍ.
- عند إنشاء Purchase Order، إذا كان المورد المختار مختلفًا عن المورد الأفضل تاريخيًا، يعاد Warning قابل للتفسير من نوع `BETTER_SUPPLIER_HISTORY`.
- التوصية Advisory وليست Hard Block لأن السعر والجودة والمهلة والسياق التجاري قد يبررون اختيار مورد آخر.

### Promised Delivery

- بقي `promisedDeliveryAt` حقيقة صريحة وليست قيمة مستنتجة.
- لا يحسب On-Time Delivery إذا لم يكن الموعد الموعود مسجلًا.
- تعديل Supabase الخاص بـ346 كان منفذًا ومتحققًا منه قبل هذه الجلسة؛ 347 لا يحتاج Schema Migration جديدًا.

## الحدود المعمارية

```text
Party Engines
    ↓
PartyDecisionReadService
    ↓
App composition adapters
    ↓
Payment / Invoice ports
    ↓
ViewModel
    ↓
UI
```

- لا يوجد Decision Engine داخل UI.
- لا يوجد اعتماد Payment على `PartyApplicationService` الكامل.
- لا يوجد Dependency Cycle جديد.
- Supplier recommendation منفصلة عن قرار الائتمان.

## التحقق

```text
PARTY_344_STATIC_GATE = PASS
PARTY_345_STATIC_GATE = PASS
PARTY_346_STATIC_GATE = PASS
PARTY_347_STATIC_GATE = PASS
V346_SUPPLIER_INTELLIGENCE_HARNESS = PASS
V347_CREDIT_POLICY_HARNESS = PASS
V347_QUALITY_DELTA_GATE = PASS
```

### Quality delta مقابل v346

```text
architecture_violation_count: 0 -> 0
broad_catches:               38 -> 38
dependency_cycles:            0 -> 0
excessive_parameter_lists:  668 -> 668
exposed_mutable_state:        2 -> 2
global_scope:                 0 -> 0
large_files_over_500:        25 -> 25
lateinit_var:                 4 -> 4
long_functions:             480 -> 480
manual_coroutine_scopes:      1 -> 1
not_null_assertions:          7 -> 7
```

لا توجد زيادة في مؤشرات Ratchet المقاسة مقارنة بمصدر الحقيقة 346.

## قيد البيئة

البناء الكامل بواسطة Gradle لم يتم اعتماده في هذه البيئة لأن Gradle 8.9 غير متوفر محليًا والبيئة لا تستطيع تنزيله. لذلك حالة الجلسة هي Static/Pure Runtime PASS وليست Full Gradle Build PASS.

## النتيجة

Session 347 تحقق الهدف المطلوب: Party أصبح يعرض قرارات العميل والمورد، البيع الآجل أصبح محكومًا بقرار مركزي قابل للتفسير، ومسار الشراء يستفيد من Supplier Intelligence دون فرض اختيار آلي غير مبرر.
