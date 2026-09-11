# Verto v373 — Build Report

Date: 2026-08-29

## Result

- Release build: **SUCCESS**
- Command: `./gradlew assembleRelease --offline --build-cache`
- Gradle: 8.9, served from the existing local wrapper cache
- Build time: 7m 40s
- Gradle execution: 1217 actionable tasks; 154 from cache; 324 up-to-date
- No `clean` task was used and no existing build or cache was deleted.

## APK

- Root artifact: `Verto-v373.apk`
- Original output: `app/build/outputs/apk/release/app-release.apk`
- Application ID: `com.verto.app`
- Version: `1.0` (version code `1`)
- Size: 8,355,951 bytes
- SHA-256: `a20c2f2b7896d0c606c0f16d5f920955a6fd8d1b099e815f4dfd19faabd01b06`
- Signature verification: passed with APK Signature Scheme v2

## Server configuration

- Supabase project URL: `https://madkfvggyolmdberzmtb.supabase.co`
- The supplied administrative token was used only for a read-only management API check and was not written to source, Git files, the APK configuration, this report, or the archive.
- The app release configuration was supplied transiently from the existing local release configuration; no local secrets file was added to this source tree.
- Management API check: HTTP 200

## Build-only repairs

- Added the missing `InvoiceTextScale.sp18` and `InvoiceTextScale.sp22` tokens.
- Added the missing `PaymentDimensions.dp5` token.
- No dependency versions, architecture, or unrelated functionality were changed.

## Non-blocking diagnostics

The build emitted existing warnings for Kotlin opt-in/deprecations, Room foreign-key indexing advice, and two native libraries that could not be stripped and were packaged unchanged. None prevented the signed Release APK from being produced.

## Clean archive

- Archive: `Verto-v373.zip`
- The archive excludes Gradle caches, all `build` directories, APK/AAB artifacts, local properties, environment files, keystores, logs, and the archive itself.
