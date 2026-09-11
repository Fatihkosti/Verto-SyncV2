# Verto Sync Legacy Retirement — v314

Static inventory is in `VERTO_SYNC_LEGACY_INVENTORY_v314.csv`; `UNKNOWN/TODO/LATER = 0`. Classification is not deletion authority.

Retirement order is fixed: V2 default-on runtime proof → observation window → zero unexplained divergence/cursor drift → old-client gate → measured Legacy runtime use = 0 → pending deletion intents empty or identity-preservingly migrated → disable Legacy while present → runtime smoke → remove unreachable transport/code → rerun static and critical runtime smoke.

`party_sync_outbox`, `financial_outbox`, `inventory_stock_outbox`, `inventory_cost_outbox`, `optimal_outbox`, `sync_attachment_transfer`, and `sync_outbox` are stronger/durable authorities and must never be removed as Legacy cleanup. Room stays 81; dormant schema residue may remain unreachable rather than creating Room 82.

Static v314 stops before disable/delete because observation, old-client population, runtime-use counts and staging evidence are unavailable.
