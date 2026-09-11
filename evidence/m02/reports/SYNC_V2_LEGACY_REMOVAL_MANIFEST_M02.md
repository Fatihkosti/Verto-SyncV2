# SYNC V2 — M02 Legacy Removal Manifest

## M02 policy
M02 does **not** authorize global V2 activation or Legacy deletion.

## Changes to Legacy

| Area | Action | Status |
|---|---|---|
| TEAM_OBSERVATION legacy participant | Preserved | PASS |
| TEAM legacy transport when V2 owns | Fenced/disabled for that ownership state | PASS |
| TEAM legacy transport in legacy/shadow state | Preserved | PASS |
| Other legacy sync paths | No deletion | PASS |
| Global rollout flags | Not activated | PASS |
| Change-log history | Not deleted | PASS |
| Historical PARTY_ROLE aliases | Preserved and classified | PASS |

## Files deleted
**0**.

## Why TEAM legacy was not removed
The user explicitly prohibited deleting Legacy or globally enabling V2 during M02. The safe M02 change is an ownership fence preventing concurrent legacy+V2 transport, while preserving rollback/fallback behavior until the later cutover milestone authorizes removal.

## M03
Not started.
