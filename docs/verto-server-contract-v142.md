# Verto ↔ Server Contract — v142

## Scope
Consolidated Android-side contract after sessions v138–v141. This document records current client expectations; it does not change Supabase.

## Data surfaces

| Surface | Client operation | Tenant scope / identity | Deletion semantics | Idempotency / ownership |
|---|---|---|---|---|
| invoices, invoice_items, payments | read/write sync | `organization_id` | invoice `voided` is business state, not physical deletion; physical deletion requires authoritative server contract | repeated remote rows/events must converge deterministically |
| clients | read/write sync | `organization_id` | absence from incremental pull is never deletion | upsert/replay must converge |
| inventory_items, inventory_movements, inventory_units, item_categories, categories | read/write sync | organization-scoped application contract | absence is non-authoritative | repeated sync must be safe |
| budgets, cost_allocations, cash_reconciliation_sessions, cash_denominations, cash_register, cash_register_movements | read/write sync | organization-scoped application contract | absence is non-authoritative | repeated sync must be safe |
| expenses, notes, client_reminders, price_list_header, price_list_items | read/write sync | organization-scoped application contract | absence is non-authoritative | repeated sync must be safe |
| shipments, shipment_stops, shipment_costs, shipment_receipts | read/write sync | organization-scoped application contract | requires explicit server deletion signal for offline convergence | repeated sync must be safe |
| commission_payments | **read-only ordinary sync** | `organization_id` | server-owned financial state | ordinary sync must never create payout records |
| commission_payment_invoices | read | server-authorized payout identity | server-owned | returned state must represent committed server command |
| withdrawal_requests | read + explicit server-owned commands | `org_id` | server-owned transition | repeated command must return explicit idempotent/invalid-transition result |
| conversations, internal_messages | read/write through messaging gateway/contracts | `org_id` + server-owned conversation ID | delete unsupported until SERVER_PLAN provides safe contract | no synthesized cross-tenant conversation identity |
| app_users, employee_permissions | authenticated reads | authenticated user/organization contract | server-owned | authorization remains RLS/server responsibility |
| notifications | authenticated reads/mutations | authenticated user/organization contract | server-owned | read mutations should be repeat-safe |
| optimal_verto_links, organization_settings, autodrive_users | integration/config reads as used by current client | server-defined organization/link identity | server-owned | server validates ownership |
| commission_eligibility, commission_ledger, marketer_balance | financial/commission reads | server-defined marketer/org identity | server-owned | client does not bypass server business logic |

## RPCs called
Current source calls include `allocate_invoice_number`, `confirm_shipment_v2`, `post_payment_v2`, `get_marketer_stats`, `get_my_notifications_cache`, `mark_notification_read`, `mark_all_notifications_read`, `register_push_token_v2`, `revoke_push_token_v2`, plus financial commands `pay_out_commission`, `pay_out_free_amount`, and `complete_withdrawal`.

Financial RPCs are commands, not synchronization. Failure must remain failure locally; Android must not emulate privileged success. `SERVER_PLAN` must provide authenticated-safe grants, transition validation, and explicit idempotency behavior.

## Realtime subscriptions
Organization Realtime uses independently owned channels and treats events only as change signals. Verified filters currently used: `invoices.organization_id`, `invoice_items.organization_id`, `clients.organization_id`, `payments.organization_id`, `commission_payments.organization_id`, `conversations.org_id`, `internal_messages.org_id`, and `withdrawal_requests.org_id`. Message Realtime is independently managed and additionally scopes conversation detail by organization + conversation identity.

Realtime publication gaps/failures are optional failures: periodic/manual synchronization remains the fallback and source of convergence. RLS remains the security boundary.

## Deletion/convergence contract
Incremental query absence means `UNKNOWN_NOT_RETURNED`, never deletion. Client protocol distinguishes upserted, unchanged, explicitly deleted/tombstoned, and unknown/not-returned. `SERVER_PLAN` must provide one authoritative tenant-scoped mechanism: soft delete (`deleted_at`), `sync_tombstones`, or revision/changelog feed. Sync cursors/checkpoints may advance only after the corresponding remote batch is successfully applied.

## Required unresolved guarantees
- `SERVER_PLAN`: authoritative deletion feed; Realtime publication coverage; RLS/FK tenant integrity; safe commission/withdrawal RPC grants and idempotency; canonical race-safe conversation open/get-or-create; safe message/conversation deletion if retained; secure unread/read mutation contract; `commission_payment_invoices` read contract or equivalent RPC response.
- `AUTODRIVE_PLAN`: AutoDrive-owned Realtime, full-pull reconciliation, payment ownership/filtering, notifications, and local cache behavior.
- `DEFERRED_TEST`: runtime, RLS, E2E, offline replay, multi-device convergence, load, and publication-failure tests.

## Security boundary
No Android component may contain `service_role`, privileged database bypass, or server-admin behavior. Supabase authenticated APIs + RLS/server business logic remain authoritative.
