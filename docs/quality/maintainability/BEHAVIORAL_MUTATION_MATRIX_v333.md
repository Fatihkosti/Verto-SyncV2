# Behavioral Mutation Matrix v333

**Status:** `BLOCKED_ENVIRONMENT`

| ID | Risk | Mutation applied | Focused test executed | Detected | Status |
|---|---|---:|---:|---:|---|
| M1 | invoice validation bypass | yes | no | no | BLOCKED_ENVIRONMENT |
| M2 | invoice authorization bypass | yes | no | no | BLOCKED_ENVIRONMENT |
| M3 | post-commit before persistence | yes | no | no | BLOCKED_ENVIRONMENT |
| M4 | inventory effect skipped | yes | no | no | BLOCKED_ENVIRONMENT |
| M5 | required payment cash effect skipped | yes | no | no | BLOCKED_ENVIRONMENT |
| M6 | sync wake before persistence | yes | no | no | BLOCKED_ENVIRONMENT |
| M7 | sync continuation dropped | yes | no | no | BLOCKED_ENVIRONMENT |
| M8 | invalid logistics transition accepted | yes | no | no | BLOCKED_ENVIRONMENT |

## Critical contract semantic mutation

C1 applied=`true`, test_executed=`false`, detected=`false`, status=`BLOCKED_ENVIRONMENT`.

## Environment evidence

Gradle wrapper cannot download Gradle 8.9: java.net.UnknownHostException: services.gradle.org.

No blocked mutation is counted as detected.
