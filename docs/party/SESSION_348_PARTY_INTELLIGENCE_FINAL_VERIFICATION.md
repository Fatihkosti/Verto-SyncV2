# VERTO — Session 348 Party Intelligence Final Verification & Hardening

**Source of Truth:** `Verto-v347-party-decision-ui-workflow.zip`  
**Target:** `Verto-v348-party-intelligence-final-hardened.zip`  
**Date:** 2026-08-23

## الهدف

إغلاق سلسلة Party Intelligence 344→347 عبر إعادة التحقق المتسلسل، وفحص حدود القرار نفسها ضد حالات الانحراف المستقبلية، دون توسيع الميزة إلى Dashboard تجميلي أو منطق قرار داخل UI.

## ما تم اكتشافه وإصلاحه

### 1. Fail-closed في بوابة البيع الآجل

كان `CustomerDecisionEngine` نفسه يعيد `REQUIRES_APPROVAL` عند نقص البيانات، لكن `CustomerCreditWorkflowPolicy` كان يثق في قيمة `decision` وحدها. نظريًا، أي انحراف mapping مستقبلي قد يمرر:

```text
ALLOW_CREDIT + dataComplete=false
```

ويؤدي إلى السماح الآلي.

تم تقوية بوابة الحفظ بحيث:

- `CASH_ONLY` يبقى Hard Block حتى للمدير.
- أي `dataComplete=false` لا يمكن أن يحصل على Auto Approval.
- الموظف يُمنع ويحتاج موافقة مدير.
- المدير يمكنه اتخاذ Override صريح وفق نفس مسار الموافقة الحالي.

بهذا أصبح Fail-closed موجودًا في **المحرك وفي Workflow Boundary** وليس في طبقة واحدة فقط.

### 2. Supplier Recommendation لا تعطل الشراء

التوصية صممت في 347 كـ Advisory، لكن خطأ Runtime في Intelligence Flow كان يمكن أن ينتقل إلى `createOrder()` ويمنع إنشاء أمر شراء.

تم تقوية `PartyPurchaseSupplierRecommendationAdapter` بحيث عند تعذر Intelligence:

```text
bestSupplierId = null
reason = INTELLIGENCE_UNAVAILABLE
```

وبالتالي:

- أمر الشراء يستمر.
- لا يتم اختراع أفضل مورد.
- لا يتحول Analytics إلى Dependency حرجة لمسار الشراء.

## إعادة التحقق

تم إعادة تشغيل:

```text
PARTY_344_STATIC_GATE = PASS
PARTY_345_STATIC_GATE = PASS
PARTY_346_STATIC_GATE = PASS
PARTY_347_STATIC_GATE = PASS
PARTY_STATIC_GATE      = PASS
```

وأضيفت بوابة 348:

```text
V348_CREDIT_FAIL_CLOSED_HARNESS = PASS
V348_QUALITY_RATCHET            = PASS
PARTY_348_FINAL_GATE            = PASS
```

## Quality Ratchet

القيم بعد Hardening مطابقة لخط أساس 347، ولا توجد زيادة:

```text
architecture_violation_count: 0
broad_catches:               38
dependency_cycles:            0
excessive_parameter_lists:  668
exposed_mutable_state:        2
global_scope:                 0
large_files_over_500:        25
lateinit_var:                 4
long_functions:             480
manual_coroutine_scopes:      1
not_null_assertions:          7
```

## حالة Supabase

تعديل 346 الخاص بـ `purchase_orders.promised_delivery_at` تم تطبيقه والتحقق منه قبل 347. لا توجد Migration جديدة في 348.

## القيود المتبقية

- Full Gradle Build ما زال غير مثبت داخل بيئة التنفيذ الحالية بسبب عدم توفر Gradle 8.9 محليًا وعدم توفر تنزيل الشبكة.
- Landed Cost لا يزال غير معروض كمؤشر مورد ما لم يوجد attribution موثوق للـPO/المورد.
- توصية المورد تظل Advisory وليست Hard Block.

## قرار الاعتماد

سلسلة Party Intelligence 344→348 أصبحت **مغلقة وظيفيًا على مستوى Static/Pure Runtime** مع حدود قرار أكثر صلابة. لا يوجد سبب لإضافة عقد خامس لنفس المحرك قبل ظهور متطلب أعمال جديد أو نتائج Full Gradle/Device testing.
