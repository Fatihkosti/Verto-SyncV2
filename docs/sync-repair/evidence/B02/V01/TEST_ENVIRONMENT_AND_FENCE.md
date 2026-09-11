# B02.04–B02.05 — isolated environment, fence, rollback, and signing

## Isolated PostgreSQL environment

Status: `BLOCKED_TEST_ENVIRONMENT_UNAVAILABLE` and `BLOCKED_CLEAN_REBUILD_SOURCE_MISSING`.

- Supabase branch inventory contains only the default production branch; no isolated development branch exists.
- Creating a hosted branch may incur cost and was not authorized, so none was created.
- Local `psql` exists, but `postgres` and `initdb` are absent; no local PostgreSQL server can be initialized here.
- The source cannot reproduce the full live schema because historical migration SQL is incomplete. The live catalog/history cannot safely be invented into a baseline.
- No sanitized test organization/data set was supplied. No tenant business rows were read from production.

Unblock inputs: an authorized isolated Supabase/PostgreSQL target, an authoritative materialized schema baseline plus all subsequent migrations, and a sanitized fixture organization with principals/devices. Rebuild must start empty, apply immutable historical files without rewriting them, then apply new forward-only migrations.

B02-V02 cost discovery reports a Supabase development branch price of USD 0.01344/hour for the owning organization. The user declined this recurring charge because the current plan is free and explicitly directed that branch creation be skipped. No branch was created; B02.04 remains blocked rather than being marked done.

## Fence plan (documentation only)

Current production control is already fail-closed (`default_wave=0`, `kill_switch=true`, `legacy_fallback_enabled=true`). It was read only and not changed.

Before any future test transition:

1. Pin one sanitized test organization and explicit test principals/devices; never derive organization scope from the currently selected UI organization.
2. Record resolved `scope_id`, principal identity, contract family/version, schema version, and a new monotonic `scopeEpoch` at login/scope change.
3. Stop workers and claims from the prior epoch; reject lease/ACK/cleanup whose org, principal, scope, or epoch differs.
4. Capture pre-fence cursors and read immutable receipts/change-log after the fence for only that scope. ACK requires exact mutation ID, request hash, generation/content hash, and receipt scope.
5. Keep production pruning disabled. Do not delete Room rows, WAL files, attachments, outbox evidence, server receipts, or immutable financial/inventory events.
6. Roll forward only in the isolated environment: wave 0 → explicitly allowlisted shadow scope after entry-health checks. Do not disable the kill switch or legacy fallback in this phase.
7. Rollback means re-enable/retain the kill switch, return the test scope to wave 0, invalidate the new epoch and leases, preserve receipts/outbox, and run reconciliation/bootstrap diagnostics. It never means mass-marking ACK/CLEAN/APPLIED or reusing mutation IDs.

## Signing status

`Verto-1.0.apk` SHA-256 is `16bb9817ef3a04cb6002cd6f4da29c86ce37f27988512d2464a8c688529996bb`. Android build-tools `apksigner verify --verbose --print-certs` passed: one RSA-4096 signer, APK Signature Scheme v2, certificate SHA-256 `9b3279d33b56797153e2dad6c9c1d8e10fb31f24b35ded77b35677d06907c9c7`.

On B02-V02 the installed base APK was copied to a mode-0600 temporary directory and verified independently. It is versionName 1.0/versionCode 1, SHA-256 `541515ac21e1427d2dd88bf736b300822b798d05e6516bd23a03f0a5a201c80d`, and has the same certificate/public-key fingerprints as the local APK. The APK bytes differ, but signer identity matches. Signing status is `PASS_INSTALLED_SIGNER_MATCHES_LOCAL`; no key material or secret was accessed or stored.

The temporary installed APK is outside the repository and contains no extracted application data.
