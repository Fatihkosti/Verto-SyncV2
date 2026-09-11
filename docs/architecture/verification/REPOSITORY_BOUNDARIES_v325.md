# Repository Boundaries v325

- Stable Room entry points remain `UnifiedSyncDao`, `InvoiceDao`, and `LogisticsDao`.
- Cohesive contracts are implementation details of `:data:database`.
- Room entities do not become application contracts.
- Existing data-adapter storage references are recorded for Session 326 port hardening; public application/domain/presentation leaks remain zero.
