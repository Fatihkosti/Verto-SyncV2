#!/usr/bin/env bash
set -euo pipefail
fail() { echo "FAIL: $1" >&2; exit 1; }
pass() { echo "PASS: $1"; }

./scripts/verify-party-v344.sh >/dev/null
pass "Session 344 foundation retained"

engine=feature/party/src/main/kotlin/com/verto/app/feature/party/application/intelligence/CustomerDecisionEngine.kt
usecase=feature/party/src/main/kotlin/com/verto/app/feature/party/application/intelligence/ObserveCustomerDecisionUseCase.kt
models=feature/party/src/main/kotlin/com/verto/app/feature/party/domain/model/PartyModels.kt
mappers=feature/party/src/main/kotlin/com/verto/app/feature/party/data/PartyDataMappers.kt
service=feature/party/src/main/kotlin/com/verto/app/feature/party/application/PartyApplicationService.kt

[[ -f "$engine" && -f "$usecase" ]] || fail "customer decision engine/use case missing"
grep -q 'enum class CustomerCreditDecision' "$engine" || fail "credit decision contract"
grep -q 'ALLOW_CREDIT, CASH_ONLY, REQUIRES_APPROVAL' "$engine" || fail "three-state credit decision"
grep -q 'INCOMPLETE_FINANCIAL_HISTORY' "$engine" || fail "fail-closed incomplete history"
grep -q 'MULTI_CURRENCY_REQUIRES_EXPLICIT_POLICY' "$engine" || fail "multi-currency guard"
grep -q 'median(intervals)' "$engine" || fail "repurchase median cadence"
grep -q 'CustomerCreditPolicy' "$engine" || fail "policy thresholds must not be UI literals"
pass "deterministic explainable decision engine"

grep -q 'val amountMinor: Long' "$models" || fail "Party payment exact minor amount"
grep -q 'val currencyCode: String' "$models" || fail "Party payment currency"
grep -q 'currencyKnown: Boolean' "$models" || fail "Party payment currency provenance"
grep -q 'amountMinor = amountMinor' "$mappers" || fail "payment minor mapper"
grep -q 'paymentCurrencyCode' "$mappers" || fail "payment currency mapper"
pass "payment money provenance propagated into Party"

grep -q 'ObserveCustomerDecisionUseCase' "$service" || fail "application service decision integration"
grep -q 'observeCustomerDecision' "$service" || fail "customer decision application entrypoint"
pass "decision engine wired behind Party application service"

test=feature/party/src/test/kotlin/com/verto/app/feature/party/application/intelligence/CustomerDecisionEngine345Test.kt
[[ -f "$test" ]] || fail "Session 345 regression tests"
for marker in 'good settled history auto approves credit' 'severely overdue balance forces cash only' 'unknown legacy currency fails closed' 'repurchase prediction uses median interval not fixed ninety days' 'multiple transaction currencies cannot auto approve'; do
  grep -q "$marker" "$test" || fail "test marker: $marker"
done
pass "Session 345 regression scenarios present"

echo 'PARTY_345_STATIC_GATE=PASS'
