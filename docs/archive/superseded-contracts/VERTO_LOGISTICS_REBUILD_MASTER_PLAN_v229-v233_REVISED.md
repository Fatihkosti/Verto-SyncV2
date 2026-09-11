# VERTO LOGISTICS / SHIPMENTS REBUILD — MASTER EXECUTION PLAN
## Baseline: `Verto-v228.zip`
## Execution range: `v229 → v233`
## Status: SOURCE OF TRUTH / EXECUTION CONTRACT

---

# 0. الهدف

إعادة بناء تدفق الشحن واللوجستيات في Verto فوق نواة Logistics V2 الحالية، مع الحفاظ على بساطة الاستخدام وربط النظام بما يحدث على أرض الواقع.

هذه الخطة تنفذ:

1. تبسيط تعريف الشحنة وتخطيطها.
2. فصل التخطيط عن التنفيذ.
3. فصل **بدء الرحلة** عن **تحرك البضاعة فعليًا**.
4. تتبع انتقال مسؤولية البضاعة بين الجهات.
5. تتبع عدد الكراتين وحالتها عند كل استلام.
6. دمج الجمارك داخل محطة الرحلة المحددة.
7. دعم الجمارك كخيار اختياري.
8. تبسيط الاستلام النهائي إلى: كامل / ناقص.
9. توزيع Landed Cost حسب قيمة البضاعة المستلمة.
10. فصل التكلفة عن الدفع.
11. دعم النواقص والتسويات اللاحقة.
12. دعم تصحيح الأخطاء بدون إعادة كتابة التاريخ المنفذ.
13. دعم Route Templates.
14. دعم دليل جهات الشحن والمخلصين.
15. تعديل المشتريات المحلية والدولية لتتوافق مع التدفق الجديد.
16. الإبقاء على Legacy مؤقتًا حتى يثبت V2 الجديد بالكامل، ثم حذفه في آخر جلسة.
17. الالتزام الكامل بنظام التصميم والـQuality Gate الحاليين.

هذه الخطة لا تعيد تصميم شاشة الشحنات الرئيسية. ذلك يأتي بعد اكتمال v233.

---

# 1. قاعدة مصدر الحقيقة

كل جلسة تنتج ZIP كاملًا يصبح المصدر الوحيد للجلسة التالية:

`Verto-v228.zip`
→ `Verto-v229-source-of-truth.zip`
→ `Verto-v230-source-of-truth.zip`
→ `Verto-v231-source-of-truth.zip`
→ `Verto-v232-source-of-truth.zip`
→ `Verto-v233-source-of-truth.zip`

ممنوع:

- الرجوع إلى نسخة أقدم.
- دمج ملفات يدويًا من أكثر من نسخة.
- اتخاذ Product Decision جديدة داخل التنفيذ.
- إعادة تصميم UX خارج ما هو مكتوب هنا.
- تعطيل Scanner أو Tests للحصول على PASS.
- حذف Legacy قبل نجاح V2 الجديد واختباره.

الوكيل **منفذ فقط**.

---

# 2. المعمارية

المالك يبقى:

`feature:shipment`

التدفق:

`Compose`
→ `ViewModel`
→ `UseCase/Application Service`
→ `Shipment-owned Port`
→ `Adapter`

قواعد ثابتة:

- Domain لا يعرف Android/Compose/Room.
- Presentation لا تستخدم DAO مباشرة.
- Invoice / Inventory / Cash / Employee تدخل عبر Ports.
- `app` composition root فقط.
- لا Module جديد.
- لا Feature-to-Feature implementation dependency مباشر.
- المال يستخدم BigDecimal أو حسابًا دقيقًا.
- Commands المهمة Idempotent.
- Inventory posting يبقى Atomic داخل مالك المخزون.
- Design System الحالي إلزامي.

---

# 3. ما يبقى من Logistics V2

نحتفظ بالنواة الحالية ونطورها:

- `LogisticsShipment`
- Sources / invoices
- Snapshot داخلي لبنود الفواتير
- Milestones / stations
- Legs
- Custody handoffs
- Partners
- Documents
- Costs
- Events
- Receiving foundation
- Inventory posting
- Landed cost allocator
- Shortages
- Recovery
- Request IDs / Idempotency
- Employee assignment
- Cash ports
- Private attachment storage
- Permissions

لا نعيد اختراع النظام من الصفر.

---

# 4. Legacy — القرار الجديد

Legacy **لا يُحذف في v229**.

الترتيب الصحيح:

1. يبقى Legacy موجودًا أثناء بناء V2 الجديد.
2. لا نضيف له ميزات جديدة.
3. V2 الجديد يبنى ويختبر كاملًا.
4. تُراجع البيانات/الاعتماديات القديمة.
5. بعد نجاح الاختبارات النهائية في v233:
   - إزالة Legacy navigation.
   - إزالة Legacy presentation/application/data/sync.
   - إزالة أي bridge أو mapper خاص به.
   - إزالة الجداول القديمة فقط بعد Migration صحيحة.
   - استخراج أي dependency مشتركة يحتاجها V2 قبل الحذف.

الهدف النهائي بعد v233:
**V2 فقط**.

---

# 5. State Machine الجديدة

نحتاج حالات واضحة تفصل بين وجود شحنة وبين حركة البضاعة.

الحالات التشغيلية الأساسية:

`DRAFT`
→ `READY`
→ `WAITING_DEPARTURE`
→ `IN_TRANSIT`
→ `AT_STATION`
→ `CUSTOMS`
→ `RECEIVING`
→ `CLOSED`

وحالة مستقلة:

`CANCELLED`

ملاحظات:

- `READY`: التخطيط معتمد لكن الرحلة لم تبدأ.
- `WAITING_DEPARTURE`: الرحلة بدأت والبضاعة تحت مسؤولية مكتب الشحن لكنها لم تتحرك بعد.
- `IN_TRANSIT`: مرحلة نقل فعلية بدأت.
- `AT_STATION`: وصلت محطة وتنتظر إجراء/تسليم.
- `CUSTOMS`: المخلص استلم وبدأ التخليص.
- `RECEIVING`: وصلت النهاية وبدأ الاستلام النهائي.

لا نستخدم `IN_TRANSIT` قبل أن تتحرك البضاعة فعليًا.

---

# 6. الخطوة 1 — تعريف الشحنة

تظهر فقط:

### رقم الشحنة
- يولد تلقائيًا.
- Read-only.
- يظهر `0001`, `0002`, ...
- لا يستخدم كهوية داخلية.

### من
- الدولة.
- المدينة.

### إلى
- الدولة.
- المدينة.

### الموظف المتابع
- موظف نشط.

لا يظهر:

- Incoterm.
- الوزن.
- الطرود.
- الطبالي.
- الحجم.
- التأمين.
- الناقل.
- ETA.

---

# 7. رقم الشحنة

رقم العرض `0001` يجب أن يكون:

- متسلسلًا داخل المؤسسة نفسها.
- مولدًا بصورة ذرية.
- عليه Unique Constraint داخل نطاق المؤسسة.
- لا يعتمد على `MAX()+1` غير المحمي.
- لا يستخدم كـPrimary Key.

مثال:

`organization_id + shipment_number` = unique.

---

# 8. الخطوة 2 — التخطيط

الشاشة تحتوي قسمين Accordion:

1. مصادر الشحن.
2. محطات الرحلة.

يفضل فتح قسم واحد في المرة الواحدة لتقليل طول الشاشة.

---

# 9. مصادر الشحن

التدفق:

`إضافة مورد`
→ `اختيار المورد`
→ `اختيار فاتورة/فواتير`

المستخدم يرى:

- المورد.
- الفواتير فقط.

لا تظهر الأصناف في التخطيط.

النظام يحتفظ ببنود الفواتير داخليًا للاستلام والتكلفة.

### قاعدة مهمة

نستبعد سيناريو توزيع محتوى الفاتورة على كراتين متعددة جزئيًا من النظام الحالي.

إذا حصل استثناء نادر مثل:
50 كرتونة، خرجت 30 ثم 20، تتم معالجته تشغيليًا خارج النظام في هذه النسخة.

لا نبني Item-level shipment allocation الآن.

---

# 10. المورد المتأخر

إذا لم يسلم مورد قبل بدء الرحلة:

- إما الانتظار.
- أو حذف المورد/فواتيره من الشحنة.

الفاتورة المحذوفة تعود متاحة لشحنة أخرى.

لا نبدأ الرحلة مع مصدر معلّق بدون قرار.

---

# 11. محطات الرحلة

المستخدم يرسم المسار فقط.

مثال:

- القاهرة → حلفا.
- حلفا → أبوحمد.
- أبوحمد → عطبرة.

لا نطلب أثناء التخطيط:

- شركة النقل.
- المسؤول.
- الهاتف.
- الكراتين.
- الوزن.
- التكلفة.
- بيانات المركبة/السفينة/الطائرة.
- المستندات.

هذه معلومات تنفيذ وليست تخطيطًا.

---

# 12. نوع الشحن

داخل قسم المحطات:

### نوع الشحن
- موحد.
- مختلط.

### موحد
اختيار واحد:
- بري.
- بحري.
- جوي.

يُورث لكل مراحل الرحلة.

### مختلط
يتم اختيار نوع النقل عند تشغيل كل مرحلة.

---

# 13. الجمارك

بعد إدخال المحطات:

**أين الجمارك؟**

Dropdown:

- لا توجد جمارك.
- أو إحدى المحطات المدخلة.

مثال:
`حلفا`

لا نسأل داخل كل محطة إذا كانت جمارك أم لا.

---

# 14. قاعدة الدولة قبل/بعد الجمارك

إذا توجد محطة جمارك:

- قبلها: دولة المغادرة.
- من محطة الجمارك وما بعدها: دولة الوصول.

النظام يستنتج هذا تلقائيًا.

إذا لا توجد جمارك:
لا يستخدم هذا التقسيم.

---

# 15. Route Templates

يمكن حفظ المسار كقالب.

القالب يحفظ:

- من/إلى.
- المحطات.
- الترتيب.
- محطة الجمارك أو عدم وجودها.
- موحد/مختلط.
- نوع النقل إذا موحد.
- الزمن المتوقع لكل مرحلة.
- الزمن المتوقع للجمارك إن وجدت.

لا يحفظ:

- المورد.
- الفواتير.
- الناقل.
- المخلص.
- الكراتين.
- الوزن.
- التكلفة.

---

# 16. دليل جهات الشحن

تطوير `logistics_partners` الحالي.

البيانات:

- اسم الجهة.
- الدور: ناقل / مخلص.
- المسؤول الأساسي.
- الهاتف.
- ملاحظات اختيارية.

داخل الشحنة:

اختيار خوجلي
→ يملأ المسؤول والهاتف تلقائيًا.

يمكن تعديل القيم لهذه الشحنة فقط بدون تعديل الدليل الأصلي.

المخلص يدعم autocomplete:
كتابة `هش...`
→ اقتراح `هشام + الهاتف`.

---

# 17. اعتماد التخطيط

مراجعة مختصرة:

- رقم الشحنة.
- من → إلى.
- الموظف.
- الموردون والفواتير.
- المحطات.
- الجمارك.
- نوع الشحن.
- الأزمنة المتوقعة.

زر:

**اعتماد التخطيط**

الحالة تصبح:

`READY`

---

# 18. المحطة الأولى

تمثل استلام مكتب الشحن من الموردين.

الحقول:

### شركة الشحن
- الشركة.
- المسؤول.
- الهاتف.

### الموردون
إذا أكثر من مورد:
- المورد A: عدد الكراتين المستلمة.
- المورد B: عدد الكراتين المستلمة.
- ...

الإجمالي يحسب تلقائيًا.

الموردون يظهرون في المحطة الأولى فقط.

بعدها تصبح الحمولة موحدة.

### الوزن
- اختياري.

### إعادة التعبئة
إذا تغير العدد:
- العدد السابق.
- العدد النهائي.
- السبب.

---

# 19. التكلفة منفصلة عن الدفع

هذه قاعدة جديدة إلزامية.

## Cost
يمثل:
**كم كلفت الخدمة؟**

يحفظ:
- البيان.
- المبلغ.
- العملة.
- سعر الصرف.
- تاريخ سعر الصرف.
- المعادل بالجنيه السوداني.
- السياق: مرحلة/جمارك/شحنة.

## Payment
يمثل:
**هل تم دفع التكلفة؟ وكيف؟**

يحفظ منفصلًا:
- مدفوعة / غير مدفوعة.
- الصندوق/الحساب المستخدم عند الدفع.
- تاريخ الدفع.
- المرجع.
- الإثبات.
- المبلغ المدفوع.
- Request ID.

لا نخلط إنشاء Cost مع Cash posting.

يمكن UX أن يبقى بسيطًا، لكن Domain/Persistence يجب أن يفصل الاثنين.

---

# 20. العملة

لا يسمح باسم عملة حر.

استخدام ISO 4217.

أمثلة:
- SDG
- USD
- EGP
- SAR
- AED

للعملة الأجنبية:
- العملة.
- المبلغ.
- سعر الصرف.
- تاريخ سعر الصرف.
- المعادل بالجنيه السوداني تلقائيًا.

يجب أن يكون اتجاه سعر الصرف واضحًا وثابتًا في Domain.

مثال:
`1 foreign unit = X SDG`

---

# 21. الزمن المتوقع

لكل حركة:

- مدة الوصول المتوقعة.

العداد الفعلي لا يبدأ عند اعتماد التخطيط.
ولا يبدأ عند استلام مكتب الشحن.

يبدأ فقط عند:

**بدأ التحرك**

---

# 22. بدء الرحلة

بعد استلام مكتب الشحن:

زر:
**بدء الرحلة**

الحوار:
- تاريخ ووقت بدء الرحلة.
- الافتراضي الآن.
- يمكن إدخال وقت سابق.

النتيجة:
- shipment → `WAITING_DEPARTURE`
- المكتب الحالي يبقى المسؤول.
- أول Leg لا يصبح `IN_TRANSIT`.

---

# 23. بدأ التحرك

عندما تتحرك البضاعة فعليًا:

زر:
**بدأ التحرك**

الحوار:
- التاريخ والوقت الفعلي.
- الافتراضي الآن.
- قابل للتعديل للماضي.

النتيجة:
- Leg → `IN_TRANSIT`
- shipment → `IN_TRANSIT`
- يبدأ حساب الزمن المتوقع.

---

# 24. occurredAt / recordedAt

كل حدث مهم يحفظ:

### occurredAt
متى حدث فعليًا.

### recordedAt
متى سجله المستخدم داخل النظام.

التقارير تعتمد `occurredAt`.

Audit يعتمد الاثنين.

يشمل على الأقل:
- بدء الرحلة.
- بدء الحركة.
- الوصول.
- الاستلام.
- انتقال المسؤولية.
- بدء الجمارك.
- انتهاء الجمارك.
- التسليم.
- التصحيح.

---

# 25. الوصول للمحطة

عند الوصول:

- Leg الحالي يصل.
- الشحنة → `AT_STATION`.
- المسؤولية لا تنتقل تلقائيًا.
- يظهر زر **تأكيد الاستلام** للطرف التالي.

---

# 26. تأكيد الاستلام

الافتراضي:

**استلمت نفس العدد السابق**

إذا نفس العدد:
- one-tap confirmation.

إذا مختلف:
- العدد الفعلي.
- النظام يحسب الفرق.
- السبب إجباري.

Custody تنتقل فقط بعد التأكيد.

---

# 27. حالة الكراتين

الحالة ليست اختيارًا حصريًا واحدًا.

نسجل خصائص مستقلة:

- عدد الكراتين المفتوحة.
- عدد الكراتين المتضررة.

إذا صفر → لا توجد حالة.

وبالتالي يمكن للكرتونة/الحمولة أن تكون:
- مفتوحة.
- متضررة.
- أو كلاهما.

لا نحتاج تصنيفًا واحدًا يمنع اجتماع الحالتين.

---

# 28. المرفقات

المحطة تلتقط المرفق فقط.

لا تعرض Gallery داخل التشغيل.

الأنواع:

- JPG
- PNG
- MP4
- DOC
- DOCX
- PDF
- MD

### MP4
الحد:
**100MB لكل ملف**

يجب:
- استخدام Streaming.
- عدم تحميل الملف كاملًا في الذاكرة.
- التحقق من MIME.
- التحقق من file signature قدر الإمكان.
- استخدام Android SAF / Uri بشكل آمن.
- رفض الملفات المخالفة للحجم/النوع قبل النقل الكامل قدر الإمكان.

---

# 29. Metadata المرفقات

يحفظ تلقائيًا:

- shipment.
- المرحلة/المحطة/الجمارك/التكلفة حسب السياق.
- الموظف.
- وقت الرفع.
- اسم الملف.
- MIME.
- الحجم.
- hash إن كان النظام الحالي يدعمه.

العرض لاحقًا في قسم مركزي داخل الشحنة.

---

# 30. الجمارك — التدفق

إذا حلفا هي محطة الجمارك:

`القاهرة → حلفا`
→ وصول حلفا
→ البضاعة ما زالت مسؤولية شركة الشحن
→ بانتظار المخلص
→ المخلص يستلم
→ `CUSTOMS`
→ التخليص
→ المخلص يسلم
→ الناقل التالي يستلم
→ `حلفا → أبوحمد`

الجمارك ليست محطة إضافية.

---

# 31. المخلص استلم

زر:
**المخلص استلم البضاعة**

الحوار:
- المخلص.
- الهاتف.
- العدد المتوقع.
- نفس العدد / عدد مختلف.
- المفتوحة.
- المتضررة.
- وقت الاستلام الفعلي.

عند الحفظ:
- Custody → المخلص.
- تبدأ مدة التخليص.

---

# 32. تقويم الجمارك

مدة التخليص تعتمد Calendar Policy مستقلة.

الافتراضي الحالي:
- الجمعة عطلة ولا تُحسب.

لكن لا تُدفن القاعدة داخل الشاشة.

يجب وجود Domain policy قابلة لاحقًا لإضافة:
- عطلات أخرى.
- اختلاف دولة/منطقة زمنية.

يجب حفظ/استخدام timezone واضح للأحداث.

---

# 33. تكاليف الجمارك

زر:
**+ إضافة تكلفة**

الحوار:
- البيان.
- المبلغ.

العملة:
**SDG تلقائيًا**

أمثلة:
- جمارك.
- أتعاب.
- مواصفات.
- أرضيات.
- غرامة.
- غيره.

الدفع لا يختلط بهذا الحوار.
يمكن لاحقًا دفع التكلفة عبر Payment flow المنفصل.

---

# 34. انتهاء الجمارك

زر:
**تم التخليص**

ثم:
- المخلص يسلم.
- الناقل التالي يؤكد الاستلام.
- Custody تنتقل.
- المرحلة التالية تصبح جاهزة للحركة.

---

# 35. التأخير

قرار المنتج يبقى:

**التأخير التشغيلي يظهر عند تجاوز ضعف الزمن المتوقع.**

لا نضيف 80% أو 100% alerts الآن.

إذا حدث تأخير:
- يظهر حقل سبب التأخير.
- ملاحظة اختيارية.

التقرير النهائي يحفظ:
- المرحلة.
- المتوقع.
- الفعلي.
- مقدار التأخير.
- السبب.

---

# 36. التصحيح قبل حدث لاحق

إذا خوجلي سجل 15 ثم اكتشف 16 قبل وجود حدث لاحق:

يسمح بالتصحيح المباشر للحقيقة التشغيلية الحالية.

لكن يحفظ Audit:

- القديم.
- الجديد.
- السبب.
- الموظف.
- وقت التصحيح.

---

# 37. التصحيح بعد حدث لاحق

إذا بُني حدث لاحق بالفعل:

لا نعيد كتابة الماضي.

نسجل Correction Event:

- الحدث الأصلي.
- القيمة الأصلية.
- القيمة المصححة.
- السبب.
- الموظف.
- occurredAt.
- recordedAt.

الأحداث التالية لا تتغير تلقائيًا.

---

# 38. تغيير المسار أثناء الرحلة

**مؤجل.**

لا يُنفذ في هذه الخطة.

إذا واجه التنفيذ بنية موجودة حاليًا لتعديل Future Legs، لا تُحذف إن كانت مستقرة، لكن لا نعيد تصميمها ولا نضيف UX جديدًا.

---

# 39. الاستلام النهائي

عند النهاية:

سؤال واحد:

**هل البضاعة مكتملة؟**

---

# 40. إذا نعم

النظام:

- يعتبر كل بنود الفواتير مستلمة.
- المقبول = المتوقع.
- النقص = صفر.
- يحسب Landed Cost.
- يوزع التكلفة حسب قيمة البضاعة.
- يدخل المقبول للمخزون.
- يحفظ posting idempotently.

---

# 41. إذا لا

يعرض البنود:

- الصنف.
- الكمية الأصلية.
- كم ناقص؟

الحد:
`1 → الكمية الأصلية`

النظام يحسب:

`received = expected - missing`

لا نضيف:
- تالف.
- مرفوض.
- زيادة.
- quarantine.

هذه الحالات مستبعدة من UX الحالي عمدًا لتبسيط التدفق.

---

# 42. Landed Cost

قاعدة التوزيع:

**حسب قيمة البضاعة التي وصلت.**

لكل بند:

`purchaseBasis = purchaseUnitPrice × receivedQuantity`

ثم:

`allocation = linePurchaseBasis / totalReceivedPurchaseBasis × totalLogisticsCost`

مع آلية دقيقة تضمن:

`sum(allocations) == totalCost`

النقص لا يتحمل تكلفة الشحن عند الإغلاق.

لا نعدل Purchase Invoice التاريخية.

Inventory posting يحمل السعر النهائي.

---

# 43. النواقص

البضاعة الناقصة:

- لا تدخل المخزون.
- تسجل Shortage.
- تحتفظ بالقيمة والكمية.
- الشحنة يمكن أن تغلق مع نقص.

العرض:
**مغلقة بنواقص**

Lifecycle:
`CLOSED`

---

# 44. تسوية النواقص

من الشحنة المكتملة:

**تسوية**

الخيارات:

### ظهر
- الكمية.
- تدخل المخزون.
- recovery posting idempotent.

### تعويض
- قيمة التعويض.
- تسوية مالية.
- لا يدخل مخزون بدون بضاعة فعلية.

### مفقود نهائيًا
- تثبيت المتبقي كخسارة نهائية.

كلها Append-only.

---

# 45. التكلفة المتأخرة

الشحنة لا تُفتح مجددًا.

من تفاصيل الشحنة:

**إضافة تسوية تكلفة**

يحفظ:
- البيان.
- المبلغ.
- العملة.
- سعر الصرف وتاريخه إن كانت أجنبية.
- المرجع/الملاحظة.

النظام يحتفظ بـ:
- تكلفة الإغلاق.
- التسويات اللاحقة.
- التكلفة النهائية.

التوزيع بنفس قاعدة قيمة البضاعة المستلمة.

---

# 46. تعديل تكلفة المخزون بعد تسوية متأخرة

لا نعدل الفاتورة.

لا نكتب `InventoryItem.buyPrice` مباشرة.

يستخدم Inventory-owned adjustment mechanism.

إذا البنية الحالية لا تدعم ذلك:
يضاف Port/UseCase خاص بتعديل تكلفة Receipt movement بطريقة Append-only وآمنة.

---

# 47. حذف الشحنة

## المسودة
حذف نهائي مباشر:
- unlink invoices.
- delete draft.

## الشحنة المنفذة
حسب قرار المنتج، يبقى **الحذف النهائي الاستثنائي متاحًا**.

لكن يجب:
- تحذير قوي.
- إعادة الفواتير متاحة.
- عكس stock.
- عكس cash.
- حذف المرفقات.
- حذف aggregate بدون orphan data.

لا يتم الحذف الصامت.

## الإلغاء
يبقى خيار مستقل:
**إلغاء الشحنة**
مع السبب، ويحافظ على التاريخ.

---

# 48. المشتريات المحلية

السلوك الجديد:

عند اعتماد/حفظ المشتريات المحلية وفق lifecycle الموجود فعليًا في Verto:

→ تدخل المخزون مباشرة.

لا نسأل:
- مخزون أم شحنة؟

إذا المشروع لا يملك Draft/Posted lifecycle حقيقيًا:
لا ننشئ Lifecycle جديدًا من أجل هذه الخطة.

نستخدم نقطة الحفظ الحالية التي تعتبر العملية معتمدة.

---

# 49. المشتريات الدولية

عند الحفظ/الاعتماد:

- لا تدخل المخزون.
- لا تسأل عن شحنة.
- لا تطلب إنشاء شحنة.
- تصبح متاحة في مصادر الشحن.

لا نعتمد على `shipmentId != null` لتحديد عدم إدخالها للمخزون.

يجب وجود Business truth صريحة مثل:

- LOCAL
- INTERNATIONAL

متوافقة مع نموذج المشروع الحالي.

---

# 50. شاشة الشحنة المكتملة

عند فتح شحنة `CLOSED`:

- رقم الشحنة.
- المسار.
- الموظف المتابع.
- تاريخ البداية.
- تاريخ الوصول.
- الفواتير.
- التكلفة النهائية.
- قيمة البضاعة بعد التكلفة.
- نتيجة الاستلام.
- النواقص وتسويتها.
- المستندات والمرفقات.
- هل وصلت في الوقت.
- زمن التأخير.
- أين حدث التأخير.
- السبب.
- سجل الرحلة.

---

# 51. سجل الرحلة

Timeline تاريخي:

- المحطات.
- الجهات.
- انتقالات المسؤولية.
- الكراتين.
- المفتوحة/المتضررة.
- بدء الرحلة.
- بدء الحركة.
- الوصول.
- الجمارك.
- التكاليف.
- الدفع.
- التصحيحات.
- المرفقات.
- التأخير.

---

# 52. شاشة الشحنات الرئيسية

**مؤجلة.**

المسموح فقط أثناء هذه الخطة:

- إبقاء الدخول الحالي.
- إضافة مدخل لجهات الشحن عند الحاجة.
- إبقاء Legacy مؤقتًا حتى v233.
- في v233 إزالة Legacy بعد نجاح V2.
- Minimal adaptation بعد الحذف.

لا نعيد تصميم:
- Cards.
- Filters.
- KPIs.
- Active/Closed sections.

بعد v233 تُكتب خطة مستقلة.

---

# 53. Design System

كل شاشة جديدة/معدلة:

- Verto canonical controls.
- لا raw Button/TextField إذا يوجد بديل.
- لا raw dp/sp/color.
- strings resources.
- RTL.
- 48dp touch targets.
- Light/Dark.
- 320/360/412dp.
- fontScale 1.0/1.3/2.0.
- TalkBack sanity.

Design System scanner يجب أن يبقى PASS في كل جلسة.

---

# 54. Room Migrations

لا يكفي اختبار الترقية من 55 فقط.

يجب:

1. تحديد كل database versions التي ما زالت مدعومة كمسار Upgrade في المشروع.
2. الاحتفاظ بكل migrations التاريخية المطلوبة.
3. إضافة migrations الجديدة فقط.
4. اختبار الترقية من كل نسخة مدعومة إلى النسخة النهائية.
5. عدم استخدام destructive fallback.

التغييرات المتوقعة تشمل:

- shipment number sequence / uniqueness.
- purchase scope.
- state machine fields.
- route templates.
- partner representative.
- event recordedAt.
- package open/damaged counts.
- cost/payment separation.
- exchange-rate date.
- customs calendar metadata عند الحاجة.
- shortage settlement.
- late cost adjustments.
- attachment metadata.
- Legacy removal migration في v233 فقط.

---

# 55. Remote / Sync

Remote Logistics V2 يبقى OFF إذا كان OFF حاليًا.

أي schema جديد له تمثيل Remote يجب تحديث:

- DTO.
- mapping.
- SQL.
- RLS.
- verification.

لكن لا يتم تفعيل Remote لمجرد أن الخطة انتهت.

التفعيل يحتاج تحقق مستقل.

---

# 56. SESSION v229
# Schema + State Machine + Purchases + Cost/Payment Foundation

## الهدف

تثبيت الأساس بدون حذف Legacy.

## التنفيذ

1. إضافة العقد الجديد كوثيقة ACTIVE.
2. تحديث docs/INDEX.
3. إضافة State Machine الجديدة.
4. فصل Start Journey عن Start Movement في Domain.
5. إضافة atomic shipment display number per organization.
6. إضافة PurchaseScope/International truth.
7. تعديل local purchase → inventory.
8. تعديل international purchase → no inventory + available for shipping.
9. فصل Cost عن Payment في Domain/Persistence.
10. ISO 4217 + exchange rate date.
11. إضافة occurredAt/recordedAt foundation.
12. route template schema.
13. partner representative schema.
14. package open/damaged count foundation.
15. customs optional flag/station.
16. calendar policy abstraction.
17. migration tests من كل النسخ المدعومة.
18. Legacy يبقى بدون إضافة ميزات.

## Gate

- Design System PASS.
- Architecture PASS.
- Migration tests PASS.
- Local purchase regression PASS.
- International purchase does not touch stock PASS.
- Cost can exist unpaid PASS.
- Payment can reference cost PASS.
- unique shipment number concurrency test PASS.
- Build PASS.

## Output

`Verto-v229-source-of-truth.zip`

---

# 57. SESSION v230
# Planning + Route Templates + Shipping Contacts

## التنفيذ

### Step 1
- 0001 read-only.
- origin country/city.
- destination country/city.
- employee.

### Step 2
- Sources accordion.
- supplier + invoices only.
- no item UI.
- Stations accordion.
- route building.
- unified/mixed.
- customs dropdown with `لا توجد جمارك`.
- expected transit duration.
- customs expected duration.
- save route template.
- use route template.
- contacts directory.
- carrier/broker autocomplete.
- draft restore.
- review.
- approve → READY.

## Gate

- plan validation.
- invoice linking.
- supplier remove/unlink.
- template round-trip.
- customs optional.
- unified/mixed.
- draft restore.
- UX matrix.
- Design System PASS.
- Build PASS.

## Output

`Verto-v230-source-of-truth.zip`

---

# 58. SESSION v231
# Journey Execution + Custody + Customs + Attachments

## التنفيذ

1. first station carrier details.
2. multiple suppliers first station only.
3. carton totals.
4. weight.
5. repack.
6. costs.
7. separate payment flow.
8. Start Journey → WAITING_DEPARTURE.
9. Start Movement → IN_TRANSIT.
10. occurredAt + recordedAt.
11. arrival.
12. same-count receipt confirmation.
13. discrepancy + reason.
14. open carton count.
15. damaged carton count.
16. custody transition.
17. delay reason only when delayed.
18. customs wait.
19. broker pickup.
20. customs calendar excluding Friday.
21. customs costs in SDG.
22. customs completion.
23. broker → next carrier handoff.
24. attachments:
    - jpg/png/mp4/doc/docx/pdf/md.
    - mp4 100MB.
    - streaming.
    - MIME/signature checks.
25. centralized attachment metadata.
26. correction before/after downstream event.

## Gate

E2E scenario:

supplier(s)
→ carrier receives
→ journey starts
→ waits
→ movement begins
→ arrival
→ receipt confirmation
→ customs
→ next carrier
→ final station

Must verify:
- custody.
- count discrepancy.
- open + damaged together.
- historical occurredAt.
- recordedAt.
- Friday exclusion.
- unpaid cost.
- paid cost.
- attachment validation.

## Output

`Verto-v231-source-of-truth.zip`

---

# 59. SESSION v232
# Final Receiving + Landed Cost + Shortages + Late Costs + Delete

## التنفيذ

### Receiving
- complete? yes/no.
- YES → all received.
- NO → missing quantity only.
- missing ≤ expected.
- received = expected - missing.

### Inventory
- received only.
- idempotent.
- invoice immutable.

### Landed Cost
- purchase-value basis.
- exact allocation.

### Shortage
- create shortage.
- found.
- compensated.
- final loss.

### Late cost
- closed shipment stays closed.
- append adjustment.
- cost/payment remain separate.
- inventory cost adjustment through Inventory owner.

### Correction
- amend before downstream event + audit.
- append correction after downstream event.

### Delete
- draft delete.
- exceptional executed-shipment delete.
- reverse inventory/cash.
- return invoices.
- remove attachments.
- no orphan effects.

## Gate

- full receiving.
- shortage receiving.
- recovery.
- compensation.
- final loss.
- late cost.
- exact allocation.
- payment separation.
- retry idempotency.
- correction scenarios.
- permanent delete scenarios.
- Build PASS.

## Output

`Verto-v232-source-of-truth.zip`

---

# 60. SESSION v233
# Final Detail + Legacy Removal + Full Regression

## الهدف

إثبات V2 ثم إزالة Legacy.

## التنفيذ

1. completed shipment detail.
2. final cost/payment views.
3. shortage status.
4. attachments.
5. employee.
6. planned vs actual timing.
7. delay location/reason.
8. journey history.
9. run complete V2 regression suite.
10. audit Legacy dependencies.
11. migrate/resolve any legacy data requirement.
12. extract any shared dependency still used by V2.
13. remove Legacy navigation.
14. remove Legacy presentation/application/data/sync.
15. remove legacy mapper/bridges.
16. remove Legacy tables through migration.
17. verify no references remain.
18. update SQL/DTO/sync contracts.
19. keep Remote OFF unless independently verified.
20. full migration tests from every supported version.
21. full Quality Gate.
22. assembleDebug.
23. package clean Source-of-Truth ZIP.

## Final Gate

Legacy deletion occurs only after V2 tests pass in the same session.

إذا فشل V2 regression:
**لا يحذف Legacy.**

## Output

`Verto-v233-source-of-truth.zip`

هذا يصبح أساس تصميم شاشة الشحنات الرئيسية.

---

# 61. الاختبارات الحرجة

## Numbering
- concurrent creation does not duplicate shipment number.
- organization A may have 0001 and organization B may have 0001.

## Purchase
- local purchase enters inventory.
- international purchase does not.
- international invoice appears in shipment source picker.

## State
- READY does not imply movement.
- Start Journey → WAITING_DEPARTURE.
- Start Movement → IN_TRANSIT.
- arrival → AT_STATION.
- broker pickup → CUSTOMS.

## Custody
- holder changes only on confirmed receipt.
- mismatch requires reason.
- open and damaged counts may coexist.
- first station shows suppliers only.

## Time
- occurredAt may be historical.
- recordedAt is system recording time.
- Friday excluded by customs calendar.
- delay reason only when threshold reached.

## Money
- cost can remain unpaid.
- payment references cost.
- foreign exchange direction exact.
- exchange rate date persisted.
- base SDG exact.
- late adjustment append-only.

## Receiving
- full.
- shortage.
- missing cannot exceed expected.
- only received enters stock.
- invoices unchanged.

## Delete
- invoices return available.
- stock reversed.
- cash reversed.
- files removed.
- no dangling references.

## Migrations
- every supported version → final version.

---

# 62. مؤجل صراحة

لا ينفذ الآن:

- تعديل المسار أثناء الرحلة.
- unplanned deviation UX.
- QR/Batch tracking.
- تفاصيل vehicle / B/L / AWB المتخصصة.
- advanced carrier scoring.
- predictive ETA.
- 80% / 100% delay warning system.
- damaged/rejected/excess item receiving.
- item-level partial shipment allocation.
- main shipments screen redesign.
- Remote V2 activation بدون Gate مستقل.

---

# 63. مستبعد صراحة

لا يُضاف:

- item-level planned/loaded quantities.
- تالف/مرفوض/زيادة في الاستلام النهائي.
- منع الحذف النهائي المنفذ.
- تنبيهات 80%/100%.
- إلزام تفاصيل بري/بحري/جوي الآن.

---

# 64. معيار النجاح النهائي

الخطة مكتملة فقط عندما:

- V2 الجديد يعمل كاملًا.
- State Machine واضحة.
- Start Journey منفصل عن movement.
- Cost منفصل عن Payment.
- shipment number ذري وفريد لكل مؤسسة.
- customs optional.
- Friday calendar policy تعمل.
- ISO currencies تعمل.
- MP4 100MB streaming آمن.
- migrations مختبرة من كل النسخ المدعومة.
- local purchase → stock.
- international purchase → shipping pool.
- route templates تعمل.
- contacts تعمل.
- custody traceable.
- cartons open/damaged traceable.
- full/missing receiving يعمل.
- landed cost صحيح.
- shortages قابلة للتسوية.
- late cost append-only.
- corrections non-destructive.
- exceptional permanent delete يعكس الآثار.
- Legacy محذوف بعد نجاح V2.
- Design System PASS.
- Architecture PASS.
- Quality Gate PASS.
- Build PASS.

بعد ذلك تبدأ خطة مستقلة:

# SHIPMENTS MAIN SCREEN REDESIGN
