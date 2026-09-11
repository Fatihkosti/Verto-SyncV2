# BRQ OTP Edge Functions — Read-only export

هذه الملفات نسخة محلية من المصدر المنشور للدالتين:

- `send-phone-otp`
- `verify-phone-otp`

تم الاستخراج بوضع القراءة فقط من Supabase Management API عبر endpoint جسم الدالة، ثم فك حزمة ESZIP. لا توجد ملفات مشتركة محلية داخل الحزمتين؛ كل دالة تحتوي `index.ts` فقط، مع imports خارجية من Supabase JS عبر `esm.sh`.

تم فحص المصدر بحثًا عن قيم مفاتيح أو Tokens أو بيانات مستخدمين. لا تُحفظ قيم متغيرات البيئة في هذه النسخة. أسماء متغيرات البيئة مذكورة في `BRQ-OTP-CONTRACT.md` فقط.

لم يتم استدعاء أي دالة تشغيلية، ولم يتم نشر أو تعديل أي شيء على Supabase.
