# Verto Sync Push Verification — v309

- **Verdict:** `FAIL_STATIC_V309`
- **Input:** `Verto-v308-source-of-truth.zip` / `b7723b93d958cb0d47bd5f4d7a4487c53093a40d0ba2ad0517abcf5b6adb03b3` / 1729 entries.
- **Room:** `79 → 80`; schema79 unchanged; one additive `sync_conflict` table.
- **Server:** v305 unchanged; v309 adds authenticated `verto_apply_sync_mutation`, tenant+mutation advisory lock, canonical request hash, immutable receipts and fail-closed optimistic conflict semantics.
- **Runtime adapter truth:** 15 generic owner307 targets are `SHADOW_PUSH_ONLY_NOT_RUNTIME_SAFE`; supplied v308 source does not contain verified business-table server adapters/legacy version capture. No APPLIED business effect is fabricated.
- **Stronger streams:** PARTY_ROLE keeps `party_sync_outbox`; Notification has no client push; 17 owner310 aggregates remain deferred.
- **Push engine:** bounded PENDING/RETRY scan, CAS lease, expired-lease recovery, dependency + aggregate ordering, stale-result protection.
- **Conflict:** Room durable, deterministic conflict id, server-wins no-enqueue, rebase always allocates a new mutation id, review/reject states persist.
- **Echo:** exact `originMutationId` ACK is inside the same Room page transaction as remote apply, inbox state and opaque cursor CAS; mismatches fail closed.
- **Coverage:** 34/34 classified; 16/16 owner307 paths classified; owner310 17/17 protected.
- **Fixtures:** v309 `272/272` PASS; v308 pull regression `137/137` PASS.
- **Inherited exceptions:** 9; worsened=0; new309Waivers=0.
- **Runtime:** V2 remains OFF. Android SDK/PostgreSQL runtime unavailable/not executed; no runtime claim.
- **Verifier normalized hash:** `14bf20b1340c90962007d19ce61c19ecc73713d8b67292f1c9c98255dfc87857`
