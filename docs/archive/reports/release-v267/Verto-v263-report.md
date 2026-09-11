# Verto v263 — Cursor Pull and Conflict Recovery

Status: **IMPLEMENTED / STATIC PASS**

- Added atomic Room cursor application for movement and cost streams.
- Persisted server sequence and ACK/retry/review state in outboxes.
- Added durable local conflict records, including oversell outcomes.
- Added server pull functions ordered by monotonic sequence.

Validation: deterministic and contract checks passed. Real multi-device/offline convergence testing remains pending.
