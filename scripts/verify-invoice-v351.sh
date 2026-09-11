#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
fail(){ echo "FAIL: $*" >&2; exit 1; }
pass(){ echo "PASS: $*"; }
need(){ rg -q "$1" "$2" || fail "$3"; }
forbid(){ ! rg -q "$1" "$2" || fail "$3"; }

HOST="$ROOT/feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/InvoiceEditorScreen.kt"
EDITOR="$ROOT/feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/NewSaleInvoiceEditor349.kt"
FORM="$ROOT/feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/InvoiceEditorFormState.kt"
DIALOGS="$ROOT/feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/InvoiceEditorDialogs.kt"
SCREEN="$ROOT/feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/presentation/invoice/InvoiceScreen.kt"
TABS="$ROOT/feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/presentation/invoice/InvoiceDetailsTabs350.kt"
COORD="$ROOT/feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/InvoiceWriteCoordinator.kt"
MIG="$ROOT/supabase/migrations/20260823235600_v351_invoice_adjustments_referrals.sql"

need 'NewSaleInvoiceEditor349\(' "$HOST" "sale editor not wired"
need 'if \(isSale\)' "$HOST" "sale edit does not use new editor"
need 'SaleInvoiceSettlementPolicy.evaluate' "$EDITOR" "settlement derivation missing"
need 'searchFocusTrigger\+\+' "$EDITOR" "line commit does not restore search focus"
need 'grossTotal' "$EDITOR" "invoice gross total missing"
need 'invoiceDiscount' "$EDITOR" "invoice discount not wired"
pass "349 progressive sale flow retained and extended"

need 'InvoiceTabs350\(selected = selectedTab' "$SCREEN" "details tabs missing"
need 'summary.payments.sortedByDescending' "$TABS" "payment history missing"
need 'InvoiceDetailsTab350.COMMUNICATION' "$SCREEN" "communication tab missing"
pass "350 details tabs retained"

rg -q 'ROOM_SCHEMA_VERSION: Int = (8[5-9]|9[0-9]|[1-9][0-9]{2,})' "$ROOT/data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt" || fail "Room schema regressed below 85"
need 'MIGRATION_84_85' "$ROOT/data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt" "84->85 migration missing"
need 'discount_minor' "$ROOT/data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations84To85.kt" "discount minor missing"
need 'commission_beneficiary_client_id' "$ROOT/data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations84To85.kt" "commission beneficiary missing"
need 'referrer_client_id' "$ROOT/data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations84To85.kt" "referrer draft field missing"
pass "Room schema 85 persists discount/referral attribution"

need 'SaleDiscountDialog351' "$EDITOR" "discount dialog missing"
need 'buyerEarnsCommission' "$HOST" "buyer commission attribution missing"
need 'commissionSource = if \(sale\) commissionSource else "NONE"' "$HOST" "commission source not saved"
need 'SaleReferralChip351' "$EDITOR" "referrer UI missing"
pass "discount and buyer/referrer attribution wired"

forbid 'CommissionBeforeSaveDialog' "$DIALOGS" "legacy commission-before-save dialog remains"
forbid 'showCommissionBeforeSave|draftCommission' "$FORM" "legacy commission-before-save state remains"
pass "replaced legacy commission UI removed"

need 'reverseForEdit\(request, snapshot.oldItems' "$COORD" "posted correction does not reverse stock"
need 'lifecycleVersion = oldInvoice.lifecycleVersion \+ 1' "$COORD" "correction version not incremented"
need 'تصحيح بعد الترحيل' "$COORD" "correction audit summary missing"
need 'draft.invoice.totalAmountMinor >= totalPaid.amountMinor' "$COORD" "paid amount floor missing"
need 'it.lifecycleStatus == InvoiceLifecycleStatus.POSTED' "$COORD" "edit stock availability does not account for released posting"
need 'authorization.canManageCommission\(\)' "$COORD" "commission edit permission missing"
pass "posted sale correction preserves accounting controls"

need 'InvoiceReturnDialog351' "$SCREEN" "return dialog not wired"
need 'createSalesReturn' "$ROOT/feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/presentation/invoice/InvoiceViewModel.kt" "return use case not wired"
need 'CreateInvoiceReturnCommand' "$ROOT/feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/presentation/invoice/InvoiceViewModel.kt" "immutable return command missing"
need 'permissions\?\.salesEdit == true' "$SCREEN" "return permission missing"
pass "linked return flow exposed from invoice"

need 'commission_source in' "$MIG" "server attribution source constraint missing"
need 'coalesce\(cl.client_id,i.commission_beneficiary_client_id,i.client_id\)' "$MIG" "eligibility does not resolve beneficiary"
need "status in \('WITHDRAWABLE','PAID_OUT'\)" "$MIG" "settled commission lock missing"
need 'COMMISSION_BENEFICIARY_NOT_MARKETING_PARTY' "$MIG" "beneficiary server guard missing"
forbid 'auth\.role\(\)' "$MIG" "deprecated auth.role in migration"
pass "server migration protects referral commission truth"

need 'clientMap\[row.clientId\]' "$ROOT/app/src/main/kotlin/com/verto/app/feature/commission/bridge/CommissionCalculationAdapter.kt" "commission UI still resolves buyer instead of beneficiary"
pass "commission UI resolves server beneficiary"

echo 'INVOICE_351_STATIC_GATE=PASS'
