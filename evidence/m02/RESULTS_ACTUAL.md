# M02 Actual Results (sanitized)

All identifiers that can identify users, organizations, scopes, cursor tokens, or credentials were removed.

## Live state
- Migration head: `20260908154626 / m02_team_observation_visibility_alignment`
- Contract: `verto-unified-sync v1`, status `EXPAND_ONLY`
- `min_available_revision = 0`
- Aggregate coverage: `35`
- `TEAM_OBSERVATION` present: `true`
- M02 function-surface fingerprint: `862ae50f015f920da7aaf0361a8d952b` (20 functions)

## Snapshot ↔ Feed consistency
```json
{"latest_identities":1683,"exact_consistent":1625,"superseded_party_aliases":58,"suppressed_synthetic_changes":3,"unresolved":0}
```
The 58 aliases have later canonical `partyId:ROLE` state. Revisions 420–422 are quarantined synthetic v393 smoke changes only. No immutable feed row or financial domain row was deleted.

## Old cursor convergence
Starting from a server-issued cursor immediately before revisions 420–422:
```text
has_more=true
returned revisions=[591,738]
suppressed_seen=0
```
The cursor advanced past the quarantined synthetic changes without replaying them.

## Delayed COMMIT / revision lock
A separate pg_cron session held the same per-organization transaction advisory lock for 20 seconds:
```text
while delayed transaction active: pg_try_advisory_xact_lock = false
after delayed transaction committed: pg_try_advisory_xact_lock = true
cron execution: succeeded, ~20 seconds
```
This verifies a later same-organization transaction cannot allocate a revision ahead of the delayed transaction.

## Transaction page boundary
An existing 3-change transaction was pulled with `p_limit=1`:
```text
returned_rows=3
transaction_ids=1
transaction_size=3
revisions=[3219,3220,3221]
ends_at_transaction_boundary=true
```
The transaction was not split across pages.

## TEAM_OBSERVATION visibility
```text
author_can_read=true
unrelated_cannot_read=false
```
Pull, bootstrap, manifest, and realtime hint now align with the product RLS rule: author or manager.

## Static source verification
`python tools/m02/verify_m02_source_contract.py` => PASS for exact 35-aggregate registry, TEAM_OBSERVATION push/pull/recovery, rollout ownership fence, PARTY_ROLE canonical identity, M02 migrations, and no global V2 activation.

## Build/reproducibility closeout
- GitHub Actions Gradle/unit/compile gate: **PASS** (`database`, `sync`, `network` unit tests + `:app:compileDebugKotlin`).
- Clean server reconstruction: **BLOCKED_SOURCE**. Live history has 211 migration records but 23 have no SQL payload, including the initial `remote_schema`, and the historical baseline dump is not materialized in this runtime.
