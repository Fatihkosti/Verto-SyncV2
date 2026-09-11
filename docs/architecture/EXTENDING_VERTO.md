---
status: supporting
scope: architecture
owner: "architecture"
last_verified_against: v330
---
# Extending Verto

Verto remains a modular monolith. A new capability extends it through a declared, owner-defined contract; it does not import another feature's implementation.

## Approved path

1. Identify the required capability.
2. Reuse an existing stable contract when semantics match.
3. If absent, the owning feature defines the smallest contract.
4. Register the semantic ID and logical version.
5. Declare the provider and owner manifest.
6. Declare every consumer.
7. Add behavior-focused contract, negative, and integration evidence according to risk.
8. Run the compatibility and architecture gates before admission.

Stable contracts must document nullability, ordering, pagination, time, money, ID, error, and offline-first semantics when applicable.

## Prohibited extension path

New features must not depend on a foreign DAO, Room Entity, repository implementation, ViewModel, Compose type, Android Context, or arbitrary internal class. A feature manifest declaration never turns an implementation into a public API.

Do not introduce a plugin engine, runtime discovery, reflection registry, service locator, generic command bus, or generic event bus for ordinary feature growth.
