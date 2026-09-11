---
status: supporting
scope: architecture
owner: "architecture"
last_verified_against: v331
---
# Adding an Integration

An integration is an adapter behind a declared port. It does not expose a provider implementation, persistence type, Android runtime object, or network client to another feature.

## Required path

1. Identify the owning feature and the smallest semantic port.
2. Define nullability, ordering, pagination, time, money, identifiers, errors, retries, and offline behavior.
3. Register a stable logical contract ID and version when another feature or the app consumes it.
4. Declare provider and every consumer in the contract registry and feature manifests.
5. Add contract and negative evidence; add mutation evidence for critical paths.
6. Keep the integration's external dependencies inside its owning module and record its scalability risk.
7. Run the compatibility guard, feature-admission guard, architecture, persistence, and full quality gates.

Breaking semantics require a new logical version and a migration/deprecation record. Changing an API snapshot without changing the contract version is a failed admission.
