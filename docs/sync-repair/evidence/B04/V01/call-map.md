# B04 unified ownership/protection call map

## Authority

- `SyncOwnershipRegistry`: exhaustive 35/35 aggregate mapping plus the specialized attachment owner. The registry has no default branch; unknown queue names raise `BLOCKED_OWNER_UNPROVEN`.
- `SyncPendingProtection.capture`: verifies the real owner row and non-terminal owner state inside an existing Room transaction before inserting content/generation/hash references.
- `SyncPendingProtection.releaseAfterTerminal`: deletes only one source's references, inside Room, after the same owner row proves a terminal state.
- `SyncPendingProtection.isProtected`: resolves content references against the current owner state. Unknown owners and orphan references are protected fail-closed. Its compatibility lookup protects pre-B04 rows without assigning unknown types.

## Runtime consumers

| Required path | Call site | Result |
|---|---|---|
| Pull | `UnifiedSyncPullEngine.guardPendingLocalMutation` | invokes `SyncPendingProtection.isProtected` before applying a remote change. |
| Bootstrap | `UnifiedSyncSnapshotApplier.shouldPreservePending` uses the same call for every staged row. Old direct per-table absence-prune SQL is inactive until B13 can enumerate rows and call the same content-aware guard. |
| M03 | `LegacySyncV2MigrationCoordinator.prepare` requires every `MIGRATED` candidate to be protected by the central service; otherwise it records `M03_PENDING_PROTECTION_UNPROVEN` and review instead of claiming migration. |
| Health | `SyncHealthMonitor.hasUnconfirmedWork` delegates to the service's exhaustive owner counts. Unknown states are non-terminal by negative terminal predicates. |
| Logout/org switch | `SyncManager.ensureSessionCanEnd` and `prepareSessionForOrg` call the health port, so both inherit the same centralized pending decision. |

## Tenant and content safeguards

- Party's legacy outbox has no organization column. `SyncRepairV2Dao.readPartyRoleSourceState` and counts require a matching organization-scoped `party_roles` row; an unproven row is never adopted.
- PAYMENT remains owned by `financial_outbox`; its aggregate root is invoiceId while paymentId and originalPaymentId are separate protected content keys.
- `FAILED`, `BLOCKED`, `REQUIRES_REVIEW`, `REJECTED`, `LOCAL_RETAINED`, and every unknown state remain protected. Only the explicit owner terminal predicates release protection.
- Attachments are checked in addition to the business owner, so the document parent cannot be overwritten while its transfer is unresolved.
