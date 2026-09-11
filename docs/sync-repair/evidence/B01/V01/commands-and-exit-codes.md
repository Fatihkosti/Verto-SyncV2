# B01-V01 commands and exit codes

All commands were run read-only except creation of the documented evidence
directories/files. Outputs below are summarized and contain no credentials.

| Command/purpose | Exit | Observed result |
|---|---:|---|
| `find` for `Verto-425.zip` under workspace-adjacent paths, `/home/aboalftooh` to depth 6, `/tmp`, and `/mnt` | 0 | No matching archive found. |
| `sha256sum VERTO_SYNC_REPAIR_EXECUTION_CONTRACT_AR.md Verto-1.0.zip` | 0 | Contract hash matched embedded expected hash; unrelated ZIP hash was `dded7236...17e6`. |
| Embedded-contract Python integrity check copied from backlog §10.1 | 0 | `34963f94...a7609e0`; unchanged. |
| `git status --short --branch` | 128 | Workspace is not a Git repository. |
| Candidate deterministic tree fingerprint | 0 | 2,574 selected files; `4c5fa594...e13a0`. |
| `./gradlew --version` | 0 | Gradle 8.9 launched on OpenJDK 17.0.19. No build/test was run. |
| Tool availability probe | 0 | `java`, `javac`, `adb`, `psql`, `git`, `unzip`, and `sha256sum` found; `postgres` and `initdb` not found. |
| `adb devices` | 0 | ADB server started; no devices/emulators listed. |
| Android/JDK environment probe | 0 | `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `JAVA_HOME`, and `local.properties` were not configured in this shell/workspace. |
| Local signing-file search | 0 | No `.jks`, `.keystore`, `.p12`, or `.pfx` found to depth 3. |
| `./scripts/run-documentation-gate.sh` | 126 | Script is not executable in the supplied tree (`Permission denied`). |
| `bash scripts/run-documentation-gate.sh` | 1 | Gate ran and failed on pre-existing canonical/RPC/module/inventory drift plus the newly added evidence files; no PASS claimed. |
| `bash scripts/ci/run-quality-gate.sh documentation B01` | 126 | Integrated runner invokes the non-executable documentation script directly and stopped with status 126. |

No T01-T50 test was run or assigned PASS. No server, database, device data,
backup, migration, deployment, or signing action was performed.
