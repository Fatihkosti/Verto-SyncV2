#!/usr/bin/env bash
set -euo pipefail

fail() { echo "FAIL: $1" >&2; exit 1; }
pass() { echo "PASS: $1"; }

bash ./scripts/verify-party-v346.sh >/dev/null
pass "Session 346 retained"

party_read=feature/party/src/main/kotlin/com/verto/app/feature/party/application/PartyDecisionReadService.kt
party_models=feature/party/src/main/kotlin/com/verto/app/feature/party/application/PartyDecisionReadModels.kt
client_vm=feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientDashboardViewModel.kt
client_screen=feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientDashboardScreen.kt
supplier_vm=feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/supplier/SupplierDashboardViewModel.kt
supplier_screen=feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/supplier/SupplierDashboardScreen.kt
decision_cards=feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/shared/PartyDecisionCards.kt
payment_port=feature/payment/src/main/kotlin/com/verto/app/feature/payment/application/port/PaymentDebtWorkflowPort.kt
payment_policy=feature/payment/src/main/kotlin/com/verto/app/feature/payment/application/CustomerCreditWorkflowPolicy.kt
payment_models=feature/payment/src/main/kotlin/com/verto/app/feature/payment/application/model/PaymentDecisionModels.kt
payment_bridge=app/src/main/kotlin/com/verto/app/feature/payment/bridge/PaymentPresentationBridge.kt
payment_party_bridge=app/src/main/kotlin/com/verto/app/feature/payment/bridge/PaymentPartyBridge.kt
payment_screen=feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/InvoiceEditorScreen.kt
payment_decision_integration=feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/InvoiceDecisionIntegration.kt
payment_decision_cards=feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/InvoiceDecisionCards.kt
purchase_ports=feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/domain/port/PurchaseCyclePorts.kt
purchase_coordinator=feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/PurchaseCycleCoordinator.kt
purchase_adapter=app/src/main/kotlin/com/verto/app/feature/invoice/bridge/PartyPurchaseSupplierRecommendationAdapter.kt

for f in "$party_read" "$party_models" "$decision_cards" "$payment_policy" "$payment_bridge" "$payment_party_bridge" "$payment_decision_integration" "$payment_decision_cards" "$purchase_adapter"; do
  [[ -f "$f" ]] || fail "missing $f"
done

# Cross-feature read API is narrow and decision-only.
grep -q 'class PartyDecisionReadService' "$party_read" || fail "narrow Party decision service"
grep -q 'observeCustomerDecisionGate' "$party_read" || fail "customer decision read API"
grep -q 'observeSupplierRecommendation' "$party_read" || fail "supplier recommendation read API"
! grep -q 'PartyApplicationService' "$payment_bridge" || fail "Payment must not depend on full PartyApplicationService"
grep -q 'PaymentPartyBridge' "$payment_bridge" || fail "Payment composes through Party boundary"
grep -q 'PartyDecisionReadService' "$payment_party_bridge" || fail "Payment Party boundary uses narrow decision service"
pass "cross-feature decision boundary is narrow"

# UI observes application-owned decisions; no engine math in VM/UI.
grep -q 'observeCustomerDecision' "$client_vm" || fail "customer dashboard decision observation"
grep -q 'CustomerDecisionCard' "$client_screen" || fail "customer decision card"
grep -q 'observeSupplierPerformance' "$supplier_vm" || fail "supplier dashboard intelligence observation"
grep -q 'SupplierDecisionCard' "$supplier_screen" || fail "supplier scorecard UI"
! grep -R -q 'CustomerDecisionEngine.evaluate\|SupplierIntelligenceEngine.evaluate' feature/party/src/main/kotlin/com/verto/app/feature/party/presentation || fail "decision math leaked into presentation"
pass "Party UI renders decisions without recalculating them"

# Credit workflow is fail-closed and authoritative at save bridge.
grep -q 'PaymentCreditDecision' "$payment_models" || fail "payment decision model"
grep -q 'CustomerCreditWorkflowPolicy' "$payment_policy" || fail "credit workflow policy"
grep -q 'BLOCK_CASH_ONLY' "$payment_policy" || fail "cash-only hard block"
grep -q 'BLOCK_MANAGER_APPROVAL_REQUIRED' "$payment_policy" || fail "manager approval gate"
grep -q 'CUSTOMER_CREDIT_CASH_ONLY' "$payment_bridge" || fail "authoritative cash-only enforcement"
grep -q 'CUSTOMER_CREDIT_REQUIRES_MANAGER_APPROVAL' "$payment_bridge" || fail "authoritative manager approval enforcement"
grep -q 'InvoiceCustomerDecisionSection' "$payment_screen" || fail "customer decision section exposed in invoice editor"
grep -q 'InvoiceSupplierDecisionSection' "$payment_screen" || fail "supplier decision section exposed in invoice editor"
grep -q 'CustomerCreditDecisionBanner' "$payment_decision_integration" || fail "credit decision banner wired"
pass "sales credit decision is enforced and visible"

# Supplier recommendation remains advisory in both invoice editor and PO creation.
grep -q 'observeSupplierRecommendation' "$payment_port" || fail "payment supplier recommendation port"
grep -q 'SupplierRecommendationBanner' "$payment_decision_integration" || fail "purchase editor supplier recommendation"
grep -q 'PurchaseSupplierRecommendationPort' "$purchase_ports" || fail "PO supplier recommendation port"
grep -q 'supplierRecommendationWarnings' "$purchase_coordinator" || fail "PO advisory recommendation integration"
grep -q 'BETTER_SUPPLIER_HISTORY' "$purchase_coordinator" || fail "explainable PO supplier warning"
grep -q 'PartyDecisionReadService' "$purchase_adapter" || fail "PO recommendation adapter"
pass "supplier recommendation is advisory and wired into purchase workflow"

# promised delivery remains explicit, never inferred.
grep -q 'promisedDeliveryAt' feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/domain/model/PurchaseCycleModels.kt || fail "PO promised delivery field retained"
grep -q 'موعد التسليم الموعود لا يمكن أن يسبق' "$purchase_coordinator" || fail "PO promised delivery validation retained"
pass "promised delivery contract retained"

# Pure policy compile + runtime harness.
tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT
kotlinc "$payment_models" "$payment_policy" -d "$tmpdir/policy.jar"
cat >"$tmpdir/Harness.kt" <<'KOT'
package com.verto.app.feature.payment.application
import com.verto.app.feature.payment.application.model.*

fun main() {
    fun d(value: PaymentCreditDecision) = PaymentCustomerDecision(value, true, emptyList(), 0, 0)
    check(CustomerCreditWorkflowPolicy.evaluate(d(PaymentCreditDecision.ALLOW_CREDIT), false) == CreditSaveAuthorization.ALLOW)
    check(CustomerCreditWorkflowPolicy.evaluate(d(PaymentCreditDecision.CASH_ONLY), true) == CreditSaveAuthorization.BLOCK_CASH_ONLY)
    check(CustomerCreditWorkflowPolicy.evaluate(d(PaymentCreditDecision.REQUIRES_APPROVAL), false) == CreditSaveAuthorization.BLOCK_MANAGER_APPROVAL_REQUIRED)
    check(CustomerCreditWorkflowPolicy.evaluate(d(PaymentCreditDecision.REQUIRES_APPROVAL), true) == CreditSaveAuthorization.ALLOW_MANAGER_OVERRIDE)
    println("V347_CREDIT_POLICY_HARNESS=PASS")
}
KOT
kotlinc "$payment_models" "$payment_policy" "$tmpdir/Harness.kt" -include-runtime -d "$tmpdir/harness.jar"
java -jar "$tmpdir/harness.jar"

echo 'PARTY_347_STATIC_GATE=PASS'
