# M02 Evidence Bundle

هذه الحزمة قابلة للمراجعة بدون أسرار أو معرفات مستخدمين/منظمات/Scopes/Cursors.

- `M02_SERVER_BASELINE_MANIFEST.json`: source/live baseline + migration history + fingerprints + blocker.
- `M02_SOURCE_DIFF.json`: الملفات المعدلة/المضافة وبصماتها.
- `RESULTS_ACTUAL.md`: النتائج الحية المنفذة بعد sanitization.
- `source_contract_verification.json`: نتيجة الفاحص الساكن.
- `source_contract_verification.stdout.txt`: output قابل للمقارنة.
- `sql/*.sql`: أوامر تحقق قابلة لإعادة التنفيذ.
- `runtime/*`: دليل عوائق Gradle/PostgreSQL المحلية.
- `reports/*.md`: تقارير M02 النهائية.

Gradle/unit/compile أصبح **PASS** عبر GitHub Actions. لا يُمنح `PASS` لـclean-rebuild؛ حالته `BLOCKED_SOURCE` بسبب غياب baseline تاريخي materialized كامل.
