# Verto v374 — Build Report

Date: 2026-08-29

## Result

- Release build: **SUCCESS**
- Command: `./gradlew assembleRelease --offline --build-cache`
- Gradle: 8.9 from the existing local wrapper cache
- Gradle execution: 1217 actionable tasks; 450 from cache; 9 up-to-date
- No `clean` task was used and no existing build or cache was deleted.

## APK

- Root artifact: `Verto-v374.apk`
- Original output: `app/build/outputs/apk/release/app-release.apk`
- Application ID: `com.verto.app`
- Version: `1.0` (version code `1`)
- Size: 8,356,911 bytes
- SHA-256: `be25716417b6d74d79d5e1d3e391005fc380b7b28ebd36308f7ed5039fa7fd84`
- Signature verification: passed with APK Signature Scheme v2

## Server configuration

- Supabase project URL: `https://madkfvggyolmdberzmtb.supabase.co`
- The supplied administrative token was used only for a transient, read-only management API request (HTTP 200).
- The public client key was resolved transiently for the release build and is compiled into the app's existing `BuildConfig` path as required for Supabase client access. The supplied administrative token was not written to source, Git files, this report, or the archive.
- No Supabase CLI link was written because the CLI is not installed; the project endpoint was verified directly without changing server state.

## Build environment

- Android SDK: `/home/aboalftooh/Android/Sdk`, supplied transiently through environment variables.
- Release signing: the existing local Android debug keystore was supplied transiently because this checkout contains no release keystore or release signing properties. The resulting APK is signed and verifiable, but it is not signed with a production release keystore.
- No dependency was downloaded or updated; offline resolution used the existing Gradle/dependency cache.

## Diagnostics

The build emitted existing non-blocking warnings for Kotlin opt-in/deprecations, Room foreign-key indexing advice, and two native libraries that could not be stripped and were packaged unchanged. None prevented the signed Release APK from being produced.

## Clean archive

- Archive: `Verto-v374.zip`
- The archive excludes Gradle caches, all `build` directories, APK/AAB artifacts, local and environment property files, Google Services configuration, keystores, key/certificate files, and the archive itself.
