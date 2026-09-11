# Verto Sync Pull Verification — v308

- **Verdict:** `FAIL_STATIC`
- **Input:** `Verto-v307-source-of-truth.zip` / `eec7c332b26ab6cafec3fa2f2ecf0f03278d6591664f433094083a1e67c736ba` / 1712 entries
- **Room:** `79 → 79`; schema79 unchanged.
- **Server:** unchanged; v305 migration `a2daf28b3a05b35907267bfc766fc6919ba5c28613854c0c0d68ee186313e908`.
- **307 carry-forward:** exactly 9 documented exceptions; worsened=0; new308Waivers=0.
- **Pull RPCs:** `verto_resolve_sync_scope` + `verto_pull_sync_changes`; opaque `String` cursor from Room only.
- **Atomicity:** whole validated page commits inbox + domain apply + APPLIED + cursor CAS in one Room transaction; no network inside.
- **Replay:** canonical JSON + deterministic semantic SHA-256; `receivedAt` excluded.
- **Coverage:** 34/34 classified; 17 shadow/read-only appliers; 17 stronger aggregates deferred fail-closed to 310.
- **Notification:** explicit UPSERT/DELETE only; page absence never deletes; legacy snapshot remains default fallback.
- **Legacy:** 26 pull functions preserved; V2 runtime remains OFF.
- **Fixtures:** 137 model/static fixtures, 0 failures; MODEL_10K_PASS.
- **Build/runtime:** not executed; no runtime claim.
- **Handoff:** 309=false, 310=false, 311=false.
- **Verifier normalized hash:** `739e1a84ed5000410f87753819eb4ee2fb2ad56d6fde1a42023f7c0f56fe7b65`
