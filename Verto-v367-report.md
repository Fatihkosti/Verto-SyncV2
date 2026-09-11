# Verto v367 — Build Report

## Result

- Release build: **SUCCESS**
- Command: `./gradlew assembleRelease --offline --build-cache`
- Duration: 9m 57s
- Gradle tasks: 1217 actionable; 755 executed; 453 restored from build cache; 9 up-to-date
- No `clean` command was used, and existing Gradle/build caches were preserved.
- No dependency versions, architecture, or out-of-scope functionality were changed.

## Build inputs

- Gradle 8.9 was available in the local wrapper cache.
- Existing Gradle dependency and build caches were reused.
- Android SDK: `/home/aboalftooh/Android/Sdk`
- Release signing used the existing local debug keystore through process-only environment variables. No keystore or signing secret was added to the source tree.

## Supabase and authentication

- Project: `Verto-app`
- Project reference: `madkfvggyolmdberzmtb`
- URL: `https://madkfvggyolmdberzmtb.supabase.co`
- Management status: `ACTIVE_HEALTHY`
- Auth settings endpoint check: **PASS** (`disable_signup=false`)
- The public client key was supplied only in process memory to the existing `BuildConfig` path. The management token and all key values were excluded from source, Git files, this report, and the archive.
- No SQL or server migration was required or applied.

The existing login path remains connected through:

`LoginScreen -> AuthViewModel -> AuthGatewayAdapter -> AuthRepository -> AuthAccountRemoteSource -> Supabase Auth signInWith(Email) -> active profile`

Auth unit tests: **24 passed, 0 failed, 0 errors, 0 skipped**.

A real interactive login was not executed because no test-user credentials or connected Android device/emulator were available. Therefore device-level end-to-end login is not claimed; the server Auth endpoint, configured project, and login/session contracts were verified.

## APK

- File: `Verto-v367.apk`
- Package: `com.verto.app`
- Label: `Verto`
- Version name/code: `1.0` / `1`
- Minimum/target SDK: `26` / `35`
- Size: `8,326,907` bytes
- SHA-256: `c43a75f6d40a4f01880f814e0c2f7aa1e5a0544d4bf5dbfea52d81ac2a5ca7d0`
- APK signature verification: **PASS** (APK Signature Scheme v2)

The APK is signed with the existing debug keystore for local installation. It is not a production/Play Store signing key.

## Source archive

- File: `Verto-v367.zip`
- The archive excludes Gradle caches, all `build` directories, APK/AAB/ZIP artifacts, local properties, environment files, signing files, IDE metadata, and temporary/log files.
- The archive was checked for management-token markers and generated signing/artifact files; none were included.
- Final archive size: under 20 MB (validated after creation).
- Archive exclusion and secret-material checks: **PASS**.

## Initial environment issue

The first build attempt stopped before project compilation because Android SDK discovery was not configured. The existing SDK at `/home/aboalftooh/Android/Sdk` was then supplied through process-only `ANDROID_HOME`/`ANDROID_SDK_ROOT`; the requested offline build completed successfully. No project file was changed for this issue.

## Non-blocking warnings

The build retained existing warnings, mainly deprecated Compose APIs, coroutine opt-in notices, Room foreign-key index recommendations, and two native libraries that could not be stripped. They did not block the release build and were left unchanged to preserve scope.
