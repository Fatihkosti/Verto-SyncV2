# SYNC V2 — M02 Server Contract Audit (Final)

**Date:** 2026-09-08  
**Baseline Git commit:** `50b097c439a0b5cc5cf5153b8fd3cfe2bd9a6a89`  
**Baseline ZIP SHA-256:** `7932ef4c2a0ec2f4909cd6fd2ecdfadd3011340e1b44be868304546b56d31257`  
**Live migration head:** `20260908154626 / m02_team_observation_visibility_alignment`  
**M02 overall gate:** **NOT CLOSED**  

> سبب عدم الإغلاق ليس فشل إصلاحات M02. الإصلاحات الجوهرية مطبقة ومتحققة حيًا، لكن إثبات إعادة بناء السيرفر من قاعدة نظيفة وGradle build لم يُنفذا بسبب قيود البيئة الموضحة أدناه.

## 1. فصل اكتمال الفحص عن اكتمال الإصلاح

| البند | فحص | إصلاح | تحقق فعلي | الحالة |
|---|---:|---:|---:|---|
| تثبيت نسخة المصدر والسيرفر | نعم | — | Git/SHA + live history | PASS |
| 61 Snapshot↔Feed historical differences | نعم | نعم | live assertion | PASS |
| Cursor قديم عبر الفروقات | نعم | نعم | live old-cursor pull | PASS |
| تغطية 34/35 وTEAM_OBSERVATION | نعم | نعم | source verifier + live coverage | PASS |
| TEAM push/pull/recovery | نعم | نعم | source verifier + live contract | PASS |
| TEAM RLS visibility parity | نعم | نعم | author/unrelated live test | PASS |
| PARTY_ROLE canonical identity | نعم | نعم | source verifier | PASS |
| delayed COMMIT ordering | نعم | لا يحتاج | concurrent DB-session test | PASS |
| transaction page boundary | نعم | لا يحتاج | pull limit=1 test | PASS |
| tenant isolation/idempotency | نعم | لا يحتاج | live negative/replay tests | PASS |
| clean database reproduction | نعم | baseline موثق | complete materialized source baseline unavailable | BLOCKED_SOURCE |
| Kotlin/Gradle build | نعم | source patched | GitHub Actions unit/compile gate | **PASS** |

## 2. Server baseline / source drift

المصدر المفحوص قبل M02 ينتهي عند migration:
`20260830113005_v390_price_list_template_sync_runtime.sql`.

السيرفر الحي يحتوي migrations لاحقة حتى `20260908154626`. تم تسجيل التسلسل الكامل بعد v390 في:
`evidence/m02/M02_SERVER_BASELINE_MANIFEST.json`.

تمت إضافة migrations الخاصة بإغلاق M02 للمصدر بأسماء تطابق history الحي:

- `20260908153803_m02_sync_contract_closeout.sql`
- `20260908154626_m02_team_observation_visibility_alignment.sql`

لكن migrations التاريخية الأصلية v391…v419 وما بعدها ليست كلها موجودة كملفات SQL في ZIP الأساس. لذلك **لا يوجد دليل clean-reset كامل** من هذا المصدر وحده. أنشئ M02 baseline موثقًا يثبت migration history، contract، coverage، وبصمة سطح الوظائف الحية، لكنه لا يُسمّى clean-rebuild proof.

**M02 function-surface fingerprint:** `862ae50f015f920da7aaf0361a8d952b` لعدد 20 وظيفة مختارة.

## 3. إصلاح الـ61 historical differences

النتيجة الحالية:

```json
{"latest_identities":1683,"exact_consistent":1625,"superseded_party_aliases":58,"suppressed_synthetic_changes":3,"unresolved":0}
```

التصنيف:

- **58 PARTY_ROLE**: IDs تاريخية بصيغة `partyId` ولها تغيير أحدث وصورة حالية قانونية بصيغة `partyId:ROLE`. لم تُحذف السجلات التاريخية.
- **3 تغييرات v393 smoke**: revisions `420,421,422`. تم عزلها في `verto_sync_change_suppressions` بعد تحقق guarded identities. لا تغيير على جداول المال، ولا حذف من immutable change log.

`min_available_revision` بقي `0`; لم نُجبر الأجهزة القديمة على bootstrap ولم نقلّص نافذة offline.

### Old cursor

اختبار cursor صادر من السيرفر قبل 420–422 أعطى:

```text
returned revisions=[591,738]
suppressed_seen=0
has_more=true
```

أي أن الجهاز القديم يتجاوز synthetic smoke ولا يعيد أثرًا ماليًا، ثم يواصل الـFeed الطبيعي.

## 4. 35/35 وTEAM_OBSERVATION

بعد الإصلاح:

- Client aggregate registry = **35**.
- Live bootstrap coverage = **35**.
- `TEAM_OBSERVATION` موجود في العقد server/client.
- Push عبر Unified Outbox عند ملكية V2.
- Pull materializer يكتب Room clean state.
- Recovery/bootstrap/prune مسجلة.
- Legacy participant يتوقف عندما تكون الملكية V2؛ لا يوجد legacy+V2 double transport.
- في legacy/shadow ownership يبقى المسار القديم كما هو، حسب شرط المستخدم بعدم حذف Legacy أو تفعيل V2 شاملًا.

أُصلحت أيضًا visibility: RLS المنتج يسمح للمؤلف بقراءة ملاحظته بينما v403 كان manager-only. بعد M02 تستخدم Pull/Bootstrap/Manifest/Realtime predicate موحدًا: **author OR required permission**.

نتيجة الاختبار:

```text
author_can_read=true
unrelated_cannot_read=false
```

## 5. PARTY_ROLE identity

تم توحيد aggregate identity إلى `partyId:ROLE` في:

- push bridge
- pull echo / reconciliation
- recovery canonical mapping

وهذا يمنع إعادة تكوين aliases التاريخية التي سببت 58 discrepancy.

## 6. Concurrent delayed COMMIT

اختبار فعلي بجلسة منفصلة أمسك نفس per-organization transaction advisory lock لمدة 20 ثانية.

```text
while delayed transaction active: pg_try_advisory_xact_lock=false
after COMMIT: pg_try_advisory_xact_lock=true
```

النتيجة: later same-org transaction لا يستطيع حجز revision متأخرة قبل COMMIT للمعاملة السابقة. **PASS**.

## 7. Transaction boundary pagination

تم اختيار transaction فعلية من 3 changes، ثم pull بـ`p_limit=1`:

```text
returned_rows=3
transaction_ids=1
transaction_size=3
revisions=[3219,3220,3221]
ends_at_transaction_boundary=true
```

المعاملة لم تُقسّم بين الصفحات. **PASS**.

## 8. Re-executable evidence

SQL commands موجودة في `evidence/m02/sql/`:

1. `01_server_state.sql`
2. `02_snapshot_feed_consistency.sql`
3. `03_old_cursor_convergence.sql`
4. `04_delayed_commit_lock.sql`
5. `05_transaction_page_boundary.sql`
6. `06_team_observation_visibility.sql`
7. `07_contract_fingerprints.sql`
8. `08_migration_history.sql`

النتائج المنفذة بعد إزالة identifiers والأسرار موجودة في `evidence/m02/RESULTS_ACTUAL.md`.

## 9. Environment blockers

### Clean reproduction — BLOCKED_SOURCE

تمت إعادة فحص العائق بعد توفر GitHub Actions وSupabase live access. النتيجة:

- المصدر الحالي لا يحتوي كل SQL التاريخي اللازم لإعادة بناء baseline نظيف.
- `supabase_migrations.schema_migrations` يحتوي 211 record، لكن 23 record بلا SQL محفوظ، ومنها `20260428070925 / remote_schema`؛ لذلك history الحي وحده لا يمكنه إعادة بناء قاعدة فارغة.
- dump تاريخي موثق موجود باسم `schema(2).sql` وبصمة `fb83bf253fefe7f7b684a41dc5df6e06b0aa933a2c13e26cb835842e1c7f9ea8`، لكنه غير materialized داخل runtime الحالي ولم يوجد في Google Drive المتصل.
- Supabase development branch غير متاحة على الخطة الحالية.

لذلك clean rebuild لا يزال غير قابل للتنفيذ دون اختلاق baseline.

### Gradle — PASS

GitHub Actions run `34269688388` / job `102207819733` نفذ بنجاح database/sync/network unit tests و`:app:compileDebugKotlin`. دليل ذلك في `evidence/m02/runtime/GITHUB_ACTIONS_GRADLE_GATE.md`.

## 10. Final M02 verdict

- **Inspection completeness:** PASS.
- **Live server/data repairs:** PASS.
- **TEAM/PARTY product-source repairs (static):** PASS.
- **Concurrency and transaction-boundary evidence:** PASS.
- **Clean server reproduction:** BLOCKED_SOURCE.
- **Gradle build/unit tests:** **PASS**.
- **M02 gate:** **NOT CLOSED** فقط حتى ينجح clean rebuild من source truth.

لم يتم تفعيل V2 عالميًا، ولم يُحذف Legacy. M03 نُفذت لاحقًا وأغلقت مستقلة عن هذا التقرير.

## 11. Supabase advisor post-check

بعد DDL تم تشغيل Security وPerformance advisors. لا توجد finding جديدة تُثبت خللًا في M02 core. جدول `verto_sync_change_suppressions` ظهر كـ`RLS enabled / no policy`، وهذا مقصود لأنه server-only وتم سحب صلاحيات `PUBLIC/anon/authenticated` ومنحها لـ`service_role` فقط. مرجع Supabase لفحص هذه الحالة: https://supabase.com/docs/guides/database/database-linter?lint=0008_rls_enabled_no_policy

بقية التحذيرات (search_path القديمة، pg_net في public، RLS performance، FKs/indexes، SECURITY DEFINER surfaces) موجودة على نطاق المشروع ولم تُعدّل ضمن M02 حتى لا يتسع النطاق.

## 12. Migration-text parity caveat

نسختا M02 المحليتان تحملان نفس **version/name** المسجلين حيًا، لكن normalized SQL hash ليس مطابقًا للنص المخزن في `schema_migrations`. لذلك clean reproduction يبقى `BLOCKED_SOURCE` وليس PASS. البصمات موثقة في `M02_SERVER_BASELINE_MANIFEST.json`؛ لا يعتمد التقرير على تطابق اسم الملف وحده.
