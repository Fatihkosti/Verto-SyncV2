#!/usr/bin/env bash
set -euo pipefail

fail() { echo "FAIL: $1" >&2; exit 1; }
pass() { echo "PASS: $1"; }

catalog=data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt
version=$(sed -n 's/.*ROOM_SCHEMA_VERSION: Int = \([0-9][0-9]*\).*/\1/p' "$catalog" | head -1)
[[ -n "$version" && "$version" -ge 77 ]] || fail "Room schema must retain Party v2 baseline (>=77)"
grep -q 'MIGRATION_76_77' "$catalog" || fail "76->77 Party baseline migration missing"
pass "Party schema baseline retained at Room $version"

for table in party_roles customer_profiles supplier_profiles party_migration_issues party_role_audit party_sync_outbox party_sync_conflicts; do
  grep -q "tableName = \"$table\"" data/database/src/main/kotlin/com/verto/app/data/local/entity/PartyNormalizedEntities.kt || fail "$table entity"
done
pass "normalized Party tables"

client_vm=feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientDashboardViewModel.kt
supplier_vm=feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/supplier/SupplierDashboardViewModel.kt
if grep -n 'singleOrNull() ?: 0' "$client_vm" "$supplier_vm"; then
  fail "silent multi-currency zero remains in Party dashboards"
fi
grep -q 'PartyDashboardMetricsCalculator' "$client_vm" || fail "client dashboard calculator cutover"
grep -q 'PartyDashboardMetricsCalculator' "$supplier_vm" || fail "supplier dashboard calculator cutover"
pass "currency-safe dashboard calculator cutover"

grep -q 'suspend fun insertParty' feature/party/src/main/kotlin/com/verto/app/feature/party/domain/repository/PartyDirectoryGateway.kt || fail "explicit normalized Party write contract"
grep -q 'explicitSupplier: SupplierProfile?' feature/party/src/main/kotlin/com/verto/app/data/repository/ClientRepository.kt || fail "atomic normalized supplier write"
grep -q 'supplier country/currency no longer overload identity fields' feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/addclient/AddEditClientViewModel.kt || fail "supplier legacy overload cutover"
grep -q 'supplierCountry' feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/addclient/AddEditClientScreen.kt || fail "supplier normalized profile write"
pass "normalized supplier profile is authoritative for new edits"

clv=feature/reports/src/main/kotlin/com/verto/app/feature/reports/application/analytics/ClvCalculator.kt
grep -q 'projectionYears' "$clv" || fail "CLV future horizon"
if grep -q 'freqPerYear.*lifespanYears' "$clv"; then
  fail "old algebraically-cancelling CLV formula remains"
fi
pass "CLV projection no longer collapses to historic profit"

for test in \
  feature/party/src/test/kotlin/com/verto/app/feature/party/application/PartyDashboardMetrics344Test.kt \
  feature/reports/src/test/kotlin/com/verto/app/feature/reports/application/analytics/ClvCalculator344Test.kt; do
  [[ -f "$test" ]] || fail "missing test $test"
done
pass "Session 344 regression tests present"

echo 'PARTY_344_STATIC_GATE=PASS'
