# Verto Sync Stronger Bridge Verification — v310

- **Verdict:** `FAIL_STATIC_V310`
- **Input:** `Verto-v309-source-of-truth.zip` / `e45f32c009ae605d010361cf12da188230ef3bf8eed0650bba81bb35dc21817d` / `2622` entries / `1175` production Kotlin files.
- **Room:** `80 → 80`; schema80 unchanged.
- **Historical server migrations:** v305/v309 byte-identical; v310 additive migration `43b2db6d6300bc5a59caffdffef071bd894a3356783dffc5de0a9afa2e13f1ce`.
- **Owner310:** `17/17` classified; blocked `0`; generic LWW `0`; new waivers `0`.
- **Stronger authorities:** financial_outbox/inbox, inventory stock/cost outboxes, and optimal_outbox preserved.
- **Semantic protections:** posted/ledger hard delete `0`; immutable cost rewrite `0`; duplicate Financial/Inventory/Optimal model violations `0`.
- **Unified bridge:** stronger effect → unified change → immutable receipt in one PostgreSQL function transaction path; replay is checked before business dispatch.
- **Pull:** owner310 uses unified revision delivery path; timestamp cursor authority `0`; REMOTE_APPLY enqueue echo `0`.
- **Fixtures:** v310 `499/499` PASS; v309 `272/272` PASS; v308 `137/137` PASS + `MODEL_10K_PASS`.
- **Runtime/build/PostgreSQL:** deliberately not executed by explicit user authorization; recorded as `NOT_RUN_USER_AUTHORIZED_STATIC_ONLY` / `POSTGRES_NOT_EXECUTED`. This is not a new waiver and no runtime claim is made.
- **Runtime V2:** `OFF`.
- **Handoff:** 311=`false`, 312=`false` on static evidence only.
- **Verifier normalized hash:** `b1dd497ff5d31d3056aac5bcf804adf0f2269639fc76ad643140e45a9bed3d0e`
