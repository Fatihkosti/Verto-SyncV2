# B06-V01 schema and golden serialization

- Canonical schema: `SYNC_CONTRACT_V2.schema.json` (JSON Schema draft 2020-12).
- Kotlin contract: `FinancialSyncContractV2.kt`; explicit nulls and defaults are enabled and unknown fields fail.
- The schema has 40 named definitions. The financial snapshot requires all ten collection fields, including explicit tombstones and effect references; purchase requests require all nine child collections.
- Fixed financial fixture wire SHA-256: `8869bb431ede4f7455eb83e0d101863022bcf220f49bc1ed6cdbbb024961f2b7`.
- `FinancialSyncContractV2Test` locks that exact wire hash and proves an item-only semantic change changes `businessContentHash` even when count and total stay constant.
- The semantic projection excludes transport stream versions and every hash field. It includes the full sorted financial aggregate and explicit tombstones.
- Movement, inventory cost revision, client credit, expense, cash, and purchase money fields use recorded `Long` Minor sources where Appendix A requires them. A.16's pre-existing `CostAllocationEntity` shape remains `allocatedAmount`/`perUnitCost` finite Double; no invented domain columns or reinterpretation were introduced.
- Private local attachment URIs and inventory `imageUri` are absent from the wire schema.

The stdlib-only gate `python3 tools/test_sync_contract_v2_schema.py` checks definition coverage, required full collections, prohibited local URI fields, and Kotlin DTO presence.
