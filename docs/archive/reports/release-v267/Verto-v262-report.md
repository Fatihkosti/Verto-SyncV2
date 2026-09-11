# Verto v262 — Atomic Server Command Sync

Status: **IMPLEMENTED / STATIC PASS**

- Replaced direct movement upserts with `inventory_apply_commands_v2`.
- Added server-side tenant/auth checks, RLS, idempotent ACKs, sequencing, quarantine and oversell conflict recording.
- Revoked authenticated direct mutation of movement and cost ledgers.
- Added contract controls that reject obsolete stock-snapshot writers.

Validation: client and SQL contracts passed static verification. The SQL migration has not been applied to a live Supabase/Postgres instance in this environment.
