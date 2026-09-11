# Shipment Logistics V3 — Feature Boundary Manifest

## Ownership

- Owner module: `feature:shipment`.
- Shipment logistics business rules remain owned by the shipment feature.
- `app` is the composition root and bridge layer only; it is not the owner of shipment business logic.

## Mandatory internal flow

```text
Compose Screen
    ↓
ViewModel
    ↓
UseCase / Application Service
    ↓
Consumer-owned Port
    ↓
Adapter / Room / App Bridge
```

## Boundaries

### Domain

Owns business rules, policies, validation, value objects, commands, and consumer-owned Port contracts.

Must not import Android, Compose, Room, Supabase, DAO, DTO, database entities, or another feature implementation.

### Application

Owns shipment use cases, orchestration, idempotency rules, correction/audit semantics, and presentation-oriented read services.

Must depend on shipment-owned domain/application contracts rather than concrete infrastructure or another feature implementation.

### Presentation

Owns Compose screens/components, ViewModels, UI state, and presentation mapping.

Must not access DAOs or infrastructure directly. Business writes flow through application use cases/ports using unidirectional state flow.

### App bridges / adapters

`app` wires shipment-owned Ports to existing infrastructure and other feature capabilities. Bridges translate contracts; they do not move shipment business rules out of `feature:shipment`.

## Cross-feature consumers

Shipment Logistics V3 consumes these capabilities only through shipment-owned Ports with `app` bridges/adapters where required:

- employee directory;
- purchase invoice/source reads;
- inventory posting;
- cash posting;
- private logistics documents.

No direct feature-to-feature implementation dependency is permitted.

## Persistence and remote boundary

- Local planning/execution persistence is authoritative for the offline UX.
- Legacy shipment compatibility is preserved.
- Remote Logistics V2 activation remains OFF until a later explicitly authorized server-verification contract.
- No production path may activate remote Logistics V2 during v197–v211.

## Current governance

This manifest defines the active shipment ownership and dependency boundaries. Current architecture rules are governed by `docs/architecture/ARCHITECTURE_CONTRACT.md` and `docs/architecture/FEATURE_ADMISSION_CONTRACT.md`; current Android/server expectations are recorded in `docs/verto-server-contract-v142.md`. Historical shipment session execution contracts are intentionally non-normative after v227. Any future logistics redesign requires a new explicit master plan rather than reactivating historical session contracts.
