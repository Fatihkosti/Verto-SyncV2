# تقرير بناء Verto 1.0

## النتيجة

- الحالة: `PASS`
- الأمر: `./gradlew assembleRelease --offline --build-cache`
- Gradle: `8.9`
- الزمن: `12m 3s`
- المهام: `1217` إجمالًا؛ `915` منفذة، `293` من build cache، و`9` `UP-TO-DATE`.
- لم تُستخدم `clean` ولم يُحذف أي build أو cache سابق.

## APK

- المسار: `Verto-1.0.apk` في جذر المشروع.
- الحزمة: `com.verto.app`
- الإصدار: `1.0`، versionCode `1`.
- الحجم: `8,370,775` bytes.
- SHA-256: `a05a043109e23230862c4b2c6d9bf107a6fa5b67b45067cb941ccc7d593c7bea`
- التوقيع: صالح باستخدام APK Signature Scheme v2؛ باستخدام Verto Release keystore المرفق.

## ربط الخادم

- تم تمرير إعدادات Supabase من `secret.md` إلى بيئة البناء مؤقتًا، دون كتابتها في الكود أو التقرير أو الأرشيف.
- الربط البرمجي موجود عبر `BuildConfig.SUPABASE_URL` و`BuildConfig.SUPABASE_ANON_KEY` في وحدة الشبكة.
- فحص Supabase: قراءة endpoint `organizations` أعادت HTTP 200.
- لم يُستخدم مفتاح الإدارة داخل تطبيق Android.

## تثبيت الهاتف

- تم اكتشاف الهاتف عبر ADB.
- تم تثبيت APK كتحديث فوق النسخة الحالية بنجاح (`Success`) دون حذف بيانات التطبيق.
- حالة الحزمة بعد التثبيت: `com.verto.app`، versionCode `1`، versionName `1.0`.

## الأرشيف

- الاسم: `Verto-1.0.zip`.
- الحجم: `18,443,419` bytes، أقل من 20MB.
- تم استبعاد Gradle cache، ومخلفات build، وملفات APK، و`secret.md`، و`local.properties`، وملفات التوقيع، وملفات CI التي قد تحتوي إعدادات حساسة.
- فحص الأنماط الحساسة داخل الأرشيف: لا توجد نتائج.

## ملاحظات

- المحاولة الأولى توقفت بسبب عدم تعريف Android SDK في البيئة؛ أُعيد البناء باستخدام SDK الموجود مسبقًا عبر متغيرات بيئية مؤقتة فقط، دون تعديل المشروع.
- ظهرت تحذيرات Kotlin/Room وstrip لمكتبتين native، ولم تمنع نجاح البناء.
