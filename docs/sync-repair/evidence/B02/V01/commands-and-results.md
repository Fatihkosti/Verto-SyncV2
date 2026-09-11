# B02-V01 commands and results

| Check | Result |
|---|---|
| Search workspace for APK/SQLite/DB/WAL/SHM files | Only `Verto-1.0.apk`; no user database sidecars |
| `adb devices` | Exit 0; empty device list |
| Supabase project/branch inventory | Verto-app healthy; only default main branch |
| Read-only catalog SQL | Exported functions, tables, constraints, indexes, triggers, policies, grants, contract, registries, storage config, and migrations |
| Live migration history | 214 entries; head `20260909194457 / verto_compressed_backup_archive_payload` |
| Local migration inventory | 30 SQL files; head `20260909095614_m08_sync_v2_migration_state.sql` |
| `apksigner verify --verbose --print-certs Verto-1.0.apk` | Exit 0; v2 signature valid; one RSA-4096 signer |
| `jq empty .../SYNC_SERVER_DEFINITIONS/*.json` | Exit 0 |
| `sha256sum -c .../artifact-hashes.sha256` | Exit 0 after manifest creation |
| Embedded contract SHA-256 check | PASS; `34963f943ecc8d359711aaf1f2c4fc3805085dd61c988107edd2c2875a7609e0` |

## B02-V02 recheck

| Check | Result |
|---|---|
| ADB/package inventory | One authorized device; `com.verto.app` 1.0/versionCode 1 |
| `run-as com.verto.app` / root / Android backup policy | Non-debuggable; no `su`; `allowBackup=false` |
| App-specific external files | Directory exists and is empty |
| Installed APK extraction to private temporary directory | PASS; SHA-256 `541515ac21e1427d2dd88bf736b300822b798d05e6516bd23a03f0a5a201c80d` |
| Installed APK signer comparison | PASS; certificate SHA-256 matches local APK |
| Live backup archive aggregate check | Zero rows; no payload or tenant identifier read |

All Supabase calls were read-only metadata/configuration queries. No DDL, DML, Edge invocation, deployment, branch creation, or tenant-row inspection occurred.
