# Verto v275 — Supabase Expand/RLS

- Status: CODE_READY; SERVER_APPLIED=NO; server tests NOT_RUN.
- Added versioned expand-only SQL, tenant RLS for every operation, authenticated tenant-bound RPC, operation replay and stale-revision rejection.
- Added idempotent role/profile backfill and quarantine; v1 remains available during rollout.
