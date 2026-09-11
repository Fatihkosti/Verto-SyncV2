---
status: supporting
scope: architecture-verification
owner: "architecture"
last_verified_against: v330
---
# Contract Compatibility — v330

The v330 registry contains the stable cross-feature and integration contracts selected from actual Verto ports/gateways. The compatibility guard snapshots their logical API surface and classifies drift as unchanged, compatible, breaking, or a new version.

## Result

- Active stable contracts with breaking drift: `0`
- Unversioned cross-feature/integration contracts: `0`
- Unknown providers: `0`
- Unknown consumers: `0`
- Duplicate contract IDs: `0`
- Critical contract evidence sets: `4`

The current source remains the accepted v330 baseline. A future breaking change must introduce a new logical version or an explicit deprecation/removal record; updating the snapshot alone is not an admission path.
