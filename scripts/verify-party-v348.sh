#!/usr/bin/env bash
set -euo pipefail
fail() { echo "FAIL: $1" >&2; exit 1; }
pass() { echo "PASS: $1"; }

bash ./scripts/verify-party-v347.sh >/dev/null
pass "Sessions 344-347 retained"

payment_policy=feature/payment/src/main/kotlin/com/verto/app/feature/payment/application/CustomerCreditWorkflowPolicy.kt
payment_test=feature/payment/src/test/kotlin/com/verto/app/feature/payment/application/CustomerCreditWorkflowPolicy348Test.kt
purchase_adapter=app/src/main/kotlin/com/verto/app/feature/invoice/bridge/PartyPurchaseSupplierRecommendationAdapter.kt

[[ -f "$payment_test" ]] || fail "missing Session 348 credit regression test"
grep -q '!decision.dataComplete' "$payment_policy" || fail "credit workflow must harden incomplete evidence"
grep -q 'BLOCK_MANAGER_APPROVAL_REQUIRED' "$payment_policy" || fail "incomplete credit history must require approval"
grep -q 'incomplete evidence cannot auto approve employee credit' "$payment_test" || fail "missing fail-closed regression scenario"
pass "credit save boundary fails closed on incomplete evidence"

grep -q '\.catch {' "$purchase_adapter" || fail "supplier recommendation adapter must degrade safely"
grep -q 'INTELLIGENCE_UNAVAILABLE' "$purchase_adapter" || fail "supplier recommendation unavailable reason"
grep -q 'bestSupplierId = null' "$purchase_adapter" || fail "supplier recommendation failure must return no recommendation"
pass "supplier recommendation fails open without blocking purchase"

echo 'PARTY_348_FINAL_GATE=PASS'
