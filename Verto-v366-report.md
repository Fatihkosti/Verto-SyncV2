# Verto v366 — Build Report

## Result

- Release build: **SUCCESS**
- Command: `./gradlew assembleRelease --offline --build-cache`
- Duration: 12m 10s
- Gradle tasks: 1214 actionable; 931 executed; 283 restored from build cache
- No `clean` command was used, and no existing Gradle cache or build output was deleted.
- No dependency versions, architecture, or out-of-scope behavior were changed.

## Build inputs

- Gradle 8.9 was available in the local wrapper cache.
- Existing dependency and build caches were reused.
- Android SDK: `/home/aboalftooh/Android/Sdk`
- Release signing used the existing local Android debug keystore through process-only environment variables. No keystore or signing secret was added to the source tree.

## Supabase and authentication

- Project: `Verto-app`
- Project reference: `madkfvggyolmdberzmtb`
- URL: `https://madkfvggyolmdberzmtb.supabase.co`
- Project status: `ACTIVE_HEALTHY`
- Hosted Auth check: email authentication enabled, signup enabled, anonymous users disabled.
- The public client key was retrieved and supplied only in process memory to the existing `BuildConfig` path. The supplied management token and all key values were excluded from source, Git files, the APK report, and this archive.
- No SQL or server migration was required or applied.

The existing login path remains connected as:

`LoginScreen -> AuthViewModel -> AuthGatewayAdapter -> AuthRepository -> AuthAccountRemoteSource -> Supabase Auth signInWith(Email) -> active profile`

Auth unit tests: **24 passed, 0 failed, 0 errors, 0 skipped**. A real interactive login was not executed because no test-user credentials or connected Android device/emulator were available; therefore device-level end-to-end login is not claimed here. The server Auth endpoint and the app login/session contracts were verified.

## APK

- File: [Verto-v366.apk](Verto-v366.apk)
- Package: `com.verto.app`
- Label: `Verto`
- Version name/code: `1.0` / `1`
- Minimum/target SDK: `26` / `35`
- Size: `8,323,947` bytes
- SHA-256: `ec19e63b6e621d95e1f3e661989b94861a8a6aebe8d85a3089be43579957f79a`
- APK signature verification: **PASS** (APK Signature Scheme v2)

The APK is signed with the existing debug keystore for local installation. It is not a production/Play Store signing key.

## Source archive

- File: `Verto-v366.zip`
- The archive excludes Gradle caches, all `build` directories, APK/AAB files, ZIP files, local properties, environment files, signing files, IDE metadata, and temporary/log files.
- The archive was checked for Supabase management-token markers and generated signing/artifact files; none were included.

## Non-blocking warnings

The build retained existing warnings, mainly deprecated Compose APIs, coroutine opt-in notices, Room foreign-key index recommendations, and two native libraries that could not be stripped. They did not block the release build and were left unchanged to preserve scope.
