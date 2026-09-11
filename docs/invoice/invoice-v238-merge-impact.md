# Invoice v238 → v242 Merge Impact — Session 243

## Decision

Use **v242 as the implementation Source of Truth** and preserve all v239–v242 logistics work. Do not replace v242 with v238.

The invoice repair plan names v238 as the invoice baseline. The supplied v242 archive contains lineage reports for v238, v239, v240, v241 and v242. Those reports show:

- v238: logistics planning/customs/review work; Room 61 unchanged.
- v239: logistics movement preparation/execution; Room 61 unchanged.
- v240: logistics station receipt/customs execution; Room 61 unchanged.
- v241: logistics final receiving/landed-cost close; Room 61 unchanged.
- v242: logistics UX-quality/test pass; Room 61 unchanged.

The reports consistently state no production dependency or Room migration changes in v239–v242 and scope changes to logistics allowlists.

## What this proves

1. v239–v242 contain valuable logistics progress that must be retained.
2. No lineage report indicates an intentional invoice-core rewrite after v238.
3. The current v242 invoice/payment/inventory code is therefore the correct merge target for session 243 and later repair work.
4. International purchase receiving/landed-cost work in v239–v242 is a **dependency to preserve**, especially for session 247.

## What cannot be proven from this archive alone

The actual v238 source archive is not embedded in v242, so a byte-for-byte diff of every invoice-related file between v238 and v242 cannot be independently reproduced here.

Therefore this session does **not** claim “zero invoice file bytes changed since v238.” It claims the narrower, grounded fact: the supplied lineage reports declare v239–v242 as logistics-scoped work with Room schema unchanged, and current v242 is the safe integration base.

## Merge-protection rules for 244–251

- Never copy the full v238 tree over v242.
- Keep current logistics v2 receiving, customs, operational execution, landed-cost settlement, finalization, design-system and test changes.
- Invoice repairs may change shared financial/inventory/database contracts only when required by the approved session and with focused regression tests for logistics receiving.
- Any schema migration must include both invoice financial tests and existing logistics migration/receiving tests.
- Changes to `InventoryDao` must preserve `receiveShipmentStockAtomic`, shipment receipt idempotency, landed-cost settlement and shipment reversal behavior.

## Expected overlap hotspots

Highest-risk shared files/packages:
- `data/database/.../AppDatabase.kt`
- `data/database/.../entity/InvoicePaymentEntities.kt`
- `data/database/.../entity/InventoryEntity.kt`
- `data/database/.../dao/InventoryDao.kt`
- `data/database/.../dao/InvoiceDao.kt`
- migration catalog/files
- network invoice/payment DTOs and sync
- shipment receiving adapters that consume invoice lines and update inventory cost
- report/read-model adapters

These hotspots require additive/refactoring merges, not replacement.
