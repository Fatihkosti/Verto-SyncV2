# B01 source and project discovery

Checkpoint: `B01-V01`  
Observed: `2026-09-10T11:08:52+02:00` (`Africa/Khartoum`)

## Instructions discovered

- No `AGENTS.md` was found in the workspace.
- `README.md` identifies an Android modular monolith using Room and Supabase,
  requires the Gradle wrapper, and points to the repository quality gate.
- `CONTRIBUTING.md` requires an explicitly accepted source package and prohibits
  claiming unexecuted build, runtime, server, or migration results. It states
  that the baseline defines no mandatory Git workflow.
- `settings.gradle.kts` is the authoritative module list and contains 30
  `include(...)` declarations plus the root project.
- No separate general root backlog was found. The sync repair backlog already
  exists at the repository root, so no duplicate state document was created.

## Relevant project areas

- Android shell/composition: `app/`
- Room/database: `data/database/`
- Network contract: `data/network/`
- Sync orchestration: `data/sync/`
- Business transactions: `data/operations/`
- Feature modules: `feature/`
- Supabase history/functions: `supabase/`
- Server/verification SQL history: `sql/`, `docs/sql/`
- Existing sync verification scripts: `scripts/verify-v304-sync-contract.sh`,
  `scripts/verify-v305-sync-server.sh`, `scripts/verify-v306-sync-room.sh`, and
  `scripts/run-v314-runtime-staging.sh`

All P01-P24 paths listed by the backlog were treated as locations to inspect in
later implementation sessions. No product Kotlin, Room schema, SQL, Supabase
definition, or runtime setting was changed in B01-V01.
