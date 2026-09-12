# B07-V02 — final atomic batch / receipt acceptance

Date: 2026-09-12  
Branch: `b07-b08-release-20260912`  
Product commit under acceptance: `ad9c2887a33fafb3de45abd7a7cd58e1a710c63d`

## Result

`G-B07: PASS` for the declared B07 gate.

- The client sends a sealed batch through one immutable V2 request. Members of a sealed batch are excluded from singleton/legacy push routes.
- First-dispatch evidence is committed before transport. A matching authoritative receipt acknowledges every owner in one Room transaction; any tenant, order, count, hash, membership, or status mismatch fails closed.
- Replay recovery releases protection only after the exact terminal receipt. It neither deletes unresolved local work nor creates another durable intent.
- The live server derives tenant/principal from Auth, hashes raw UTF-8 text before JSONB, validates member/blob/dependency/version/size, locks tenant and roots deterministically, rechecks receipts under lock, and commits domain facts/revisions/receipts atomically.

## PostgreSQL evidence

- Two independent database sessions invoked the same newly frozen batch concurrently. Both returned the same `APPLIED` response and the same request SHA-256.
- Ten subsequent invocations returned one identical response.
- Assertions after concurrency + replay: exactly one batch receipt, one member receipt, one domain receipt, and one change-log fact.
- T16 body/base mismatch: rejected as idempotency conflict with no second effect.
- T47 valid member followed by invalid member: full rollback; no partial receipt or fact.
- Receipt tables: RLS enabled, direct `anon`/`authenticated` table access revoked; RPC access is Auth-scoped.
- The temporary concurrency table/function had no public/authenticated grants and was removed by the immediately following cleanup migration. Removal was verified through `to_regclass`/`to_regprocedure`.

## Android/JVM evidence

- `./gradlew --stacktrace testDebugUnitTest`: PASS.
- All eight Android suites were invoked. App 3/3, design system 16/16, operations 2/2, sync 61/61, auth 3/3, inventory 4/4, shipment 6/6, and the focused B07/B08 Room gate 8/8 passed. Database ran 49 tests (one skipped) with ten unrelated failures itemized in `commands-and-results.md`.
- GitHub Actions run: https://github.com/Fatihkosti/Verto-SyncV2/actions/runs/34695836595.

The full T15 scenario (lost response followed by a new edit) remains `NOT_RUN` in the global Txx tracker; B07's receipt/dispatch components are tested, but no broader Txx claim is inferred.
