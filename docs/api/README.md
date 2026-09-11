---
status: canonical
scope: system
owner: "data:network"
last_verified_against: v315
---
# Verto API / Server Integration

## Supabase client

`VertoSupabase` installs Auth, PostgREST, Realtime, Storage and Functions using Supabase Kotlin BOM **3.0.2**. Production usage is verified for Auth, PostgREST, Realtime, Storage (chat media), and Functions (`send-notification-fcm`). Installed capability alone is not treated as proof of usage.

## Verification vocabulary

- `CLIENT_VERIFIED`: a production Kotlin call exists.
- `REPOSITORY_SERVER_DEFINED`: a matching `CREATE FUNCTION` exists in repository SQL. This does **not** prove deployment.
- `RUNTIME_VERIFIED`: retained runtime/server evidence explicitly verifies the operation. v316 itself does not call the live server.

Static v315 discovery found **49 production RPC call sites / 48 unique RPC names**. **21** have repository SQL definitions and **27** do not. One RPC (`verto_resolve_sync_scope`) has explicit per-operation live evidence in the retained v314 SQL deployment report; no broader per-RPC runtime count is invented. See [RPC Reference](rpc-reference.md).

## RPC taxonomy

RPCs cover authentication/organization membership, identifiers, notifications, commissions/withdrawals, financial posting, inventory/financial sync, unified sync/bootstrap, purchase-cycle sync and Optimal/AutoDrive integration. A client call never proves the server body.

## Direct PostgREST access

Static resolution found **71 named tables**: 45 literal accesses plus constant/registry-resolved educational/logistics tables. `SyncRuntime.isGoneFromSupabase(table, ...)` also accepts a caller-supplied table name and is documented as a dynamic legacy deletion probe rather than inventing another named table.

| Table | Operations observed | Tenant/id filters observed | Production caller(s) | Source-of-truth note |
|---|---|---|---|---|
| `app_users` | select, update | id, org_id, organization_id, user_id | `data/network/src/main/kotlin/com/verto/app/data/remote/AuthAccountRemoteSource.kt`<br>`data/network/src/main/kotlin/com/verto/app/data/remote/OrganizationAccessRemoteSource.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `autodrive_users` | select | client_id | `data/network/src/main/kotlin/com/verto/app/data/repository/CommissionRemoteSource.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `budgets` | delete, select, upsert | id, organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncCash.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `cash_denominations` | delete, select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncCash.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `cash_reconciliation_sessions` | delete, select, upsert | id, organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncCash.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `cash_register` | select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncCash.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `cash_register_movements` | select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncCash.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `categories` | delete, select, upsert | id, organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncInventory.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `client_credits` | select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncClientCredits.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `client_reminders` | select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncMisc.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `clients` | delete, select | id, organization_id | `data/network/src/main/kotlin/com/verto/app/data/remote/PartySyncFallbackRemote.kt`<br>`data/network/src/main/kotlin/com/verto/app/data/sync/SyncClients.kt`<br>`feature/integration/optimal/src/main/kotlin/com/verto/app/feature/integration/optimal/data/OptimalCompaniesRemoteRefresher.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `commission_eligibility` | select | org_id | `data/network/src/main/kotlin/com/verto/app/data/repository/CommissionRemoteSource.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `commission_ledger` | select | org_id | `data/network/src/main/kotlin/com/verto/app/data/repository/CommissionRemoteSource.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `commission_payment_invoices` | select | organization_id | `data/network/src/main/kotlin/com/verto/app/data/repository/CommissionRemoteSource.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `commission_payments` | select | organization_id | `data/network/src/main/kotlin/com/verto/app/data/repository/CommissionRemoteSource.kt`<br>`data/network/src/main/kotlin/com/verto/app/data/sync/SyncMisc.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `conversations` | select, update | client_id, id, org_id | `data/network/src/main/kotlin/com/verto/app/data/repository/InternalMessagingRemoteSource.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `cost_allocations` | delete, select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncCash.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `educational_topic_targets` | delete, select, upsert | organization_id | `feature/dashboard/src/main/kotlin/com/verto/app/feature/dashboard/data/education/EducationalContentSyncParticipant.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `educational_topics` | delete, select, upsert | organization_id | `feature/dashboard/src/main/kotlin/com/verto/app/feature/dashboard/data/education/EducationalContentSyncParticipant.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `employee_permissions` | delete, select, upsert | id, org_id, organization_id, user_id | `data/network/src/main/kotlin/com/verto/app/data/remote/OrganizationAccessRemoteSource.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `expenses` | delete, select, upsert | id, organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncMisc.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `goods_receipt_lines` | select | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncPurchaseCycle.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `goods_receipts` | select | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncPurchaseCycle.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `internal_messages` | insert, select, update | client_id, id, org_id | `data/network/src/main/kotlin/com/verto/app/data/repository/InternalMessagingRemoteSource.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `inventory_items` | select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncInventory.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `inventory_units` | delete, select, upsert | id, organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncInventory.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `invoice_items` | delete, select, upsert | id, organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncInvoiceDeletion.kt`<br>`data/network/src/main/kotlin/com/verto/app/data/sync/SyncInvoiceLines.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `invoices` | delete, select, upsert | client_id, id, organization_id | `data/network/src/main/kotlin/com/verto/app/data/remote/PartySyncFallbackRemote.kt`<br>`data/network/src/main/kotlin/com/verto/app/data/sync/SyncClients.kt`<br>`data/network/src/main/kotlin/com/verto/app/data/sync/SyncInvoiceDeletion.kt`<br>… | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `item_categories` | delete, select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncInventory.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `logistics_assignments` | select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncLogisticsV2.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `logistics_cost_allocations` | select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncLogisticsV2.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `logistics_costs` | select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncLogisticsV2.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `logistics_custody_handoffs` | select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncLogisticsV2.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `logistics_documents` | select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncLogisticsV2.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `logistics_events` | select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncLogisticsV2.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `logistics_inventory_postings` | select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncLogisticsV2.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `logistics_milestones` | select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncLogisticsV2.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `logistics_partners` | select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncLogisticsV2.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `logistics_payments` | select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncLogisticsV2.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `logistics_receiving_batches` | select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncLogisticsV2.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `logistics_receiving_lines` | select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncLogisticsV2.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `logistics_recoveries` | select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncLogisticsV2.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `logistics_recovery_lines` | select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncLogisticsV2.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `logistics_recovery_postings` | select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncLogisticsV2.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `logistics_route_template_stops` | select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncLogisticsV2.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `logistics_route_templates` | select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncLogisticsV2.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `logistics_shipment_legs` | select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncLogisticsV2.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `logistics_shipment_lines` | select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncLogisticsV2.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `logistics_shipment_partner_links` | select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncLogisticsV2.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `logistics_shipment_sources` | select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncLogisticsV2.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `logistics_shipments` | select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncLogisticsV2.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `logistics_shortages` | select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncLogisticsV2.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `logistics_transport_details` | select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncLogisticsV2.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `marketer_balance` | select | org_id | `data/network/src/main/kotlin/com/verto/app/data/repository/CommissionRemoteSource.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `notes` | select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncMisc.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `notifications` | insert | none proven at parsed access window | `data/network/src/main/kotlin/com/verto/app/data/repository/CommissionRemoteSource.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `optimal_verto_links` | select | none proven at parsed access window | `feature/integration/optimal/src/main/kotlin/com/verto/app/feature/integration/optimal/data/OptimalCompaniesRemoteRefresher.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `organization_settings` | select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/remote/OrganizationSettingsRemote.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `payment_allocations` | select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncInvoicePayments.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `payments` | delete, select, upsert | client_id, id, organization_id | `data/network/src/main/kotlin/com/verto/app/data/remote/PartySyncFallbackRemote.kt`<br>`data/network/src/main/kotlin/com/verto/app/data/sync/SyncClients.kt`<br>`data/network/src/main/kotlin/com/verto/app/data/sync/SyncInvoiceDeletion.kt`<br>… | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `PRICE_LIST` templates | unified mutation/snapshot | organization_id + template id | `UnifiedOutboxWriter` / `UnifiedSyncChangeApplier` | v376 templates store inventory IDs only; inventory remains price/name/stock source of truth |
| `purchase_cycle_attachments` | select | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncPurchaseCycle.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `purchase_invoice_match_lines` | select | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncPurchaseCycle.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `purchase_invoice_matches` | select | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncPurchaseCycle.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `purchase_invoice_receipt_allocations` | select | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncPurchaseCycle.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `purchase_order_lines` | select | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncPurchaseCycle.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `purchase_orders` | select | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncPurchaseCycle.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `purchase_payment_overrides` | select | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncPurchaseCycle.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `realized_fx_events` | select, upsert | organization_id | `data/network/src/main/kotlin/com/verto/app/data/sync/SyncInvoicePayments.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |
| `withdrawal_requests` | select, update | id, org_id | `data/network/src/main/kotlin/com/verto/app/data/repository/WithdrawalCommandRemoteSource.kt` | legacy/direct transport; local Source-of-Truth role is feature-specific and must not be inferred from direct access |

### Dynamic table access

`SyncLogisticsV2` iterates 24 `LOGISTICS_V2_TABLE_CONTRACTS` names. The transport is hard-disabled by `SERVER_CONTRACT_VERIFIED = false`; table presence in this inventory is capability/source evidence, not proof of active production server use. `SyncRuntime.isGoneFromSupabase(table, ...)` performs a dynamic `select` constrained by `id` and `organization_id` for legacy deletion confirmation.

## Realtime surfaces

1. **Unified sync hints:** `public.verto_sync_realtime_hints`, inserts filtered by `organization_id`; hint/accelerator only.
2. **Messages:** `conversations` inserts/updates and `internal_messages` inserts; separate chat refresh/delivery surface.

## Storage / Functions

Chat media uses Supabase Storage buckets `chat-images` and `chat-audio` after `SecureMediaPolicy` validates type/size/path. Notification delivery optionally invokes Edge Function `send-notification-fcm`; failure does not rewrite the underlying notification record as successful delivery.

## SQL authority tiers

- `supabase/migrations/**`: migration/evolution definitions; retained v314 deployment evidence proves only the documented v305/v309/v310/v312/v313 live sequence.
- `docs/sql/**`: reference/deployment/repair SQL; repository presence is not deployment proof.
- `sql/**`: manual/supporting SQL; repository presence is not deployment proof.

## Related docs

- [RPC Reference](rpc-reference.md)
- [Authentication](auth.md)
- [Errors](errors.md)
- [Idempotency and Retry](idempotency.md)
- [Security Boundaries](../architecture/security-boundaries.md)
- [Sync Architecture](../architecture/sync-architecture.md)

## Evidence

- `data/network/src/main/kotlin/com/verto/app/data/remote/SupabaseClient.kt`.
- Production Kotlin under `data/network/src/main/**`, `feature/integration/optimal/src/main/**`, and `feature/dashboard/src/main/**`.
- `data/network/src/main/kotlin/com/verto/app/data/remote/dto/LogisticsV2Dtos.kt`.
- `docs/archive/reports/Verto-v314-SQL-deployment-report.md`.
