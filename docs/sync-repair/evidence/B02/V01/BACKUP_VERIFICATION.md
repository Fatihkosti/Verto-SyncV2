# B02.01 — BACKUP VERIFICATION

- Captured: 2026-09-10T11:37:39+02:00 (`Africa/Khartoum`)
- Status: `BLOCKED_BACKUP_UNVERIFIED`
- Workspace search found no SQLite/Room database, `-wal`, or `-shm` files outside generated build state.
- On the B02-V02 recheck, one authorized Android device was connected and `com.verto.app` 1.0 (versionCode 1) was present.
- The installed package is a non-debuggable release: `run-as com.verto.app` is denied, the device has no `su`, and the manifest sets `allowBackup=false`. ADB therefore cannot read the app-private Room database or its WAL/SHM files without bypassing Android security.
- The app-specific external files directory is empty; this does not prove that no private/content-provider attachments exist.
- The live `verto_backup_archives` table currently contains zero archives, so the authenticated server recovery RPC has no archive to return and cannot substitute for the missing local Room state.
- `Verto-425.zip` denotes the already-extracted source tree per the user; it is not a user-data backup.
- The live Supabase database and the local `Verto-1.0.apk` are not substitutes for a consistent device backup.

No database or attachment content was read, copied, modified, or deleted. Therefore separate-copy SQLite open/integrity checks and file hashes were not possible.

Unblock input: provide a supported in-app diagnostic/export that creates a consistent SQLite backup including pending sync state and referenced attachments, or provide a private authorized OS-level extraction. Replacing/reinstalling/rooting the current app is forbidden before that backup exists. The copy must then be opened separately and checked before any repair or migration.
