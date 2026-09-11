# B05 packet, ownership, lease, and ACK map

| Boundary | Production symbol | B05 invariant |
|---|---|---|
| Unified producer transaction | `UnifiedOutboxWriter.enqueue` → `FrozenMutationStore.captureUnified` | Outbox row, intent packet, all protected-key generations and references share the caller's Room transaction. |
| Specialized-owner adapter | `FrozenMutationStore.captureOwner` | Financial, Party, inventory, Optimal, and attachment owners retain their original source row; the adapter captures packet/ref authority without inventing a generic owner. Producer-specific wiring and complete DTO snapshots remain B06. |
| Packet persistence | `SyncRepairV2Dao.insertMutationPacketIfAbsent` | Same mutation ID must reproduce source, business identity, intent text/hash, version family, predecessor, and batch identity or fail idempotency. |
| Version chain | `readLatestUnresolvedUnifiedPredecessor`, `readUnambiguousAppliedVersion`, `prepareOnce` | Unknown stays null; an offline successor waits; only an acknowledged predecessor's returned server version becomes its frozen base version. |
| Frozen member | `FrozenMutationCodec`, `freezeMutationWireRaw` | Canonical intent is separate from the one-time wire; the SHA covers the exact stored UTF-8 text. |
| Sealed batch | `SyncBatchCoordinatorV2.seal/prepareOnce` | Ordered manifest points to original owner packet references; exact member strings/hashes and optional immutable snapshot blobs are frozen once. |
| Lease | `UnifiedSyncOutboxDao.tryLease/renewLease` | Claim binds token plus `scopeEpoch`; duration is 120 seconds and renewal cadence is 30 seconds. Retry/terminal CAS requires both. |
| Dispatch | `UnifiedSyncPushEngine.applyWithLeaseRenewal`, `UnifiedSyncPushRemote.applyFrozen` | The stored text/hash is passed on every attempt; the lease token is a separate transport argument. B07 owns the matching server-v2 receipt implementation. |
| ACK | `FrozenMutationStore.acknowledgeUnified` | Receipt mutation ID and request hash must match the frozen packet, then terminal CAS checks lease token, epoch, and semantic fingerprint. Changed/later protected intent is observed before releasing only the old source refs. |
| Pull echo | `FrozenMutationStore.acknowledgeAuthoritativeEcho` | Origin ID alone is insufficient; the authoritative payload hash must equal captured business content. |
| Old request | `classifyPreviousMutation` | Only exact saved bytes or a matching receipt are usable; otherwise result is `OUTCOME_UNKNOWN`, never rebuilt under the old ID. |

Room schema 98 adds only nullable `sync_outbox.lease_scope_epoch`, preserving schema-97 data while making the scope epoch part of the lease CAS.

