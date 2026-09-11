#!/usr/bin/env bash
set -euo pipefail
payment_models=feature/payment/src/main/kotlin/com/verto/app/feature/payment/application/model/PaymentDecisionModels.kt
payment_policy=feature/payment/src/main/kotlin/com/verto/app/feature/payment/application/CustomerCreditWorkflowPolicy.kt
tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT
cat >"$tmpdir/Harness.kt" <<'KOT'
package com.verto.app.feature.payment.application
import com.verto.app.feature.payment.application.model.*
fun main() {
    fun d(value: PaymentCreditDecision, complete: Boolean = true) = PaymentCustomerDecision(value, complete, emptyList(), 0, 0)
    check(CustomerCreditWorkflowPolicy.evaluate(d(PaymentCreditDecision.ALLOW_CREDIT), false) == CreditSaveAuthorization.ALLOW)
    check(CustomerCreditWorkflowPolicy.evaluate(d(PaymentCreditDecision.CASH_ONLY), true) == CreditSaveAuthorization.BLOCK_CASH_ONLY)
    check(CustomerCreditWorkflowPolicy.evaluate(d(PaymentCreditDecision.REQUIRES_APPROVAL), false) == CreditSaveAuthorization.BLOCK_MANAGER_APPROVAL_REQUIRED)
    check(CustomerCreditWorkflowPolicy.evaluate(d(PaymentCreditDecision.REQUIRES_APPROVAL), true) == CreditSaveAuthorization.ALLOW_MANAGER_OVERRIDE)
    check(CustomerCreditWorkflowPolicy.evaluate(d(PaymentCreditDecision.ALLOW_CREDIT, false), false) == CreditSaveAuthorization.BLOCK_MANAGER_APPROVAL_REQUIRED)
    check(CustomerCreditWorkflowPolicy.evaluate(d(PaymentCreditDecision.ALLOW_CREDIT, false), true) == CreditSaveAuthorization.ALLOW_MANAGER_OVERRIDE)
    println("V348_CREDIT_FAIL_CLOSED_HARNESS=PASS")
}
KOT
kotlinc "$payment_models" "$payment_policy" "$tmpdir/Harness.kt" -include-runtime -d "$tmpdir/harness.jar"
java -jar "$tmpdir/harness.jar"
