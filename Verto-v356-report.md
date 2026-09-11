# Verto v356 — Release Build Report

## Result

- Release build: **SUCCESS**
- Command: `./gradlew assembleRelease --offline --build-cache`
- Duration: 10m 39s
- Gradle tasks: 1214 actionable; 838 executed, 367 restored from build cache, 9 up-to-date
- No `clean` command was used, and no existing build or Gradle cache was deleted.
- No dependency or library version was changed.
- No application architecture or out-of-scope behavior was changed.

## Build environment and repair

The first build attempt stopped during configuration because the Android SDK location was not defined. The project source was not changed. The retry supplied `ANDROID_HOME` and `ANDROID_SDK_ROOT` as process-only environment variables and completed successfully.

The cached Gradle 8.9 distribution, dependency cache, and build cache were reused. Before building, the global Gradle cache was approximately 14 GB, including approximately 1.6 GB of dependency modules and 2.7 GB of build-cache entries.

## Supabase and authentication

- Connected build configuration to the active healthy Supabase project named `Verto-app` using process-only environment variables.
- The supplied management token was used only in memory to discover the project and retrieve its public client key. It was not written to source, configuration files, Git files, artifacts, or this report.
- Hosted Auth settings responded successfully and confirmed email authentication is enabled and anonymous authentication is disabled.
- The APK contains the server URL and public client key through the existing `BuildConfig` path; no secret/service-role key was embedded.
- Release authentication unit tests: **26 passed, 0 failed, 0 errors, 0 skipped**.
- Existing login/session integration compiled successfully, including the server-backed session gate.

A complete interactive sign-in was not performed because no test-user credentials and no connected Android device or emulator were available. Therefore the build, server reachability, hosted Auth configuration, and authentication contracts are verified, but end-to-end UI login remains a device acceptance step.

No SQL operation was required for this build, so no migration or server schema change was applied.

## APK

- File: `Verto-v356.apk`
- Package: `com.verto.app`
- Application label: `Verto`
- Version name/code: `1.0` / `1`
- Minimum/target SDK: 26 / 35
- Size: 8,319,951 bytes
- SHA-256: `4a4d4ebe9a959ba7e940333166fd5dcf44840e48d46dbe2c5759536102d6ac11`
- APK signature verification: **PASS**

No production release keystore was present in the supplied project or its nearby workspace. To produce an installable APK without adding secrets, the Release variant was signed with the existing Android debug keystore. The APK is minified and resource-shrunk, but this signature is not suitable for Play Store publication or future production updates. A production-signed APK requires the owner's release keystore and credentials supplied outside the repository.

## Warnings retained

The build emitted non-blocking warnings already present in the source, primarily deprecated Compose icons/components, coroutine opt-in notices, future Kotlin compatibility notices, two Room foreign-key index recommendations, and two native libraries that could not be stripped. They were not modified because they do not block this build and changing them would exceed the requested repair scope.

## Source archive policy

The source archive excludes build directories, Gradle caches, APK/AAB files, signing files, local properties, environment files, IDE metadata, logs, temporary files, and previously generated ZIP files. A pre-archive scan found no Supabase management-token markers or secret-file candidates in the included source.
