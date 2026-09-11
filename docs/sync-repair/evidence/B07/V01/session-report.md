# B07-V01 — atomic V2 batch/receipts evidence

Date: 2026-09-12
Scope: Supabase project Verto-app + execution branch `b07-b08-release-20260912`.

## Implemented
- V2 contract row (`verto-unified-sync`, version 2) added without removing V1.
- Auth-derived V2 scope resolution and validation.
- Exact UTF-8 request hashing before JSONB conversion.
- Atomic batch RPC `verto_apply_sync_batch_v2(p_wire_json text,p_wire_sha256 text)`.
- Batch/member receipt stores with RLS enabled and direct table access revoked.
- Deterministic batch/root locks, dependency/blob/order validation and replay.
- V2 receipt lookup with explicit FOUND / NOT_FOUND / LEGACY_UNPROVABLE behavior.
- Android client routes frozen singleton V2 writes through the atomic batch RPC and refuses to send a sealed batch member as an independent write.

## Applied server migrations
- `b07_atomic_batch_receipts_v2_r2`

The first draft was rejected transactionally by the pre-existing `contract_version=1` check; no partial DDL from that draft remained. The successful migration widened only the contract metadata check to versions 1 and 2 and retained V1.

## PostgreSQL acceptance actually run
- V2 scope + capabilities: PASS.
- T14 replay precursor: same frozen batch applied once then replayed 10 times with identical receipt/result: PASS.
- T16: same mutation identity with different body/base contract rejected without a second effect: PASS.
- T47: first valid member followed by a rejected member caused full group rollback; zero legacy receipts, zero V2 member receipts and zero change-log facts for the attempted group: PASS.
- T45/T49 security/contract: cross-organization access blocked, anonymous capabilities blocked, and a V1 scope rejected by V2 read paths: PASS.

All mutation/rollback fixtures were executed inside explicit PostgreSQL transactions and rolled back. No user business fixture was retained.

## Security
- New receipt tables have RLS enabled and no direct `anon`/`authenticated` table grants.
- Public helper execution is revoked; only intended authenticated RPC entry points are granted.
- Supabase security advisor reports the new receipt tables as `RLS enabled/no policy`, which is intentional because they are RPC-only and table grants are revoked. No new anonymous SECURITY DEFINER V2 entry point was introduced.

## Remaining acceptance dependency
Full Android/Room build and test evidence is produced by the final CI run. This report does not promote an Android test that was not executed.