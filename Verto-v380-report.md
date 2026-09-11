# Verto v380 Build Report

التاريخ: 2026-08-29

## النتيجة

- الحالة: `PASS`
- الأمر المنفذ: `./gradlew assembleRelease --offline --build-cache`
- Gradle: `BUILD SUCCESSFUL` خلال 10m40s
- المهام: 1217 إجمالًا؛ 722 منفذة، 486 من build cache، و9 up-to-date
- لم تُستخدم `clean`، ولم يُحذف أي build أو cache سابق.

## APK

- المسار: `Verto-v380.apk` في جذر المشروع
- الحزمة: `com.verto.app`
- التوقيع: صالح، APK Signature Scheme v2، موقّع واحد
- الحجم: 8,370,859 bytes
- SHA-256: `e566aac8678e453c4fdb98879b1e57af07007ec8575d08b81c36b97028817e01`

## ربط الخادم

- تم تمرير إعدادات الاتصال والتوقيع من `secret.md` إلى بيئة البناء فقط.
- wiring الخاص بـ Supabase موجود عبر `BuildConfig.SUPABASE_URL` و`BuildConfig.SUPABASE_ANON_KEY`.
- فحص Supabase Auth settings: HTTP 200.
- فحص جدول التطبيق `organizations`: HTTP 200.
- لم تُكتب أي قيمة سرية في الكود أو هذا التقرير.

## الملاحظات

- المحاولة الأولى توقفت بسبب عدم تعريف مسار Android SDK؛ أُعيد البناء باستخدام SDK موجود مسبقًا في البيئة، دون تعديل المشروع.
- ظهرت تحذيرات Kotlin وRoom غير حاجبة.
- تعذر strip لمكتبتين native، فتم تضمينهما كما هما؛ لم يمنع ذلك نجاح البناء.
- التوقيع محلي ومخصص للتثبيت المحلي وفق إعدادات المشروع، وليس مفتاح نشر إنتاجي.

## الأرشيف

- الاسم: `Verto-v380.zip`
- أُنشئ بعد النجاح مع استبعاد Gradle cache وملفات `build` وملفات APK و`secret.md` وملفات التوقيع والإعدادات المحلية.
- تم التحقق من عدم وجود أنماط مفاتيح خدمة أو JWT سرية في الملفات المرشحة للأرشيف.
