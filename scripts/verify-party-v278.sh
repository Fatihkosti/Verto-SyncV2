#!/usr/bin/env bash
set -euo pipefail

fail() { echo "FAIL: $1" >&2; exit 1; }
pass() { echo "PASS: $1"; }

version=$(sed -n 's/.*ROOM_SCHEMA_VERSION: Int = \([0-9][0-9]*\).*/\1/p' data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt | head -1)
[[ -n "$version" && "$version" -ge 77 ]] || fail "Room schema Party baseline >=77"
grep -q 'MIGRATION_76_77' data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt || fail "76->77 catalog"
pass "Room 76->77 catalog"

for table in party_roles customer_profiles supplier_profiles party_migration_issues party_role_audit party_sync_outbox party_sync_conflicts; do
  grep -q "tableName = \"$table\"" data/database/src/main/kotlin/com/verto/app/data/local/entity/PartyNormalizedEntities.kt || fail "$table entity"
done
pass "normalized Party tables"

if grep -R -n -E "clientType (NOT )?LIKE '%SUPPLIER%'|clientType (NOT )?LIKE \"%SUPPLIER%\"" \
  data/database/src/main/kotlin/com/verto/app/data/local/dao/ClientDao.kt \
  data/database/src/main/kotlin/com/verto/app/data/local/dao/InventoryDao.kt; then
  fail "legacy supplier LIKE in active queries"
fi
pass "no active supplier LIKE classification"

if grep -R -n '\bDouble\b' \
  feature/party/src/main/kotlin/com/verto/app/feature/party/domain/ledger \
  feature/party/src/main/kotlin/com/verto/app/feature/party/application/ledger; then
  fail "Double in ledger contract/engine"
fi
pass "fixed-point ledger contract"

grep -q 'derivedFromPaymentId' feature/party/src/main/kotlin/com/verto/app/feature/party/application/ledger/PartyLedgerEngine.kt || fail "credit dedup"
grep -q 'it.side == side' feature/party/src/main/kotlin/com/verto/app/feature/party/application/ledger/PartyLedgerEngine.kt || fail "ledger side isolation"
grep -q 'it.occurredAt < fromInclusive' feature/party/src/main/kotlin/com/verto/app/feature/party/application/ledger/PartyLedgerEngine.kt || fail "opening chronology"
pass "ledger isolation, chronology, credit dedup"

grep -q 'observeCustomerLedger' feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientStatementViewModel.kt || fail "customer statement cutover"
grep -q 'observeSupplierLedger' feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/supplier/SupplierStatementViewModel.kt || fail "supplier statement cutover"
pass "statement cutover"

sql=docs/sql/v275_party_v2_expand.sql
for marker in 'enable row level security' 'get_my_org_id()' 'p_operation_id' 'p_base_revision' 'stale_party_revision' 'on conflict(organization_id,party_id,role) do nothing'; do
  grep -qi "$marker" "$sql" || fail "server contract marker: $marker"
done
pass "server RLS, idempotency, revision and backfill contract"

grep -q 'ACK_REPLAY' data/network/src/main/kotlin/com/verto/app/data/sync/PartySyncV2Contract.kt || fail "sync replay"
grep -q 'RECORD_STALE_CONFLICT' data/network/src/main/kotlin/com/verto/app/data/sync/PartySyncV2Contract.kt || fail "sync conflict"
grep -q 'PartyAggregateType.TOMBSTONE' data/network/src/main/kotlin/com/verto/app/data/sync/PartySyncV2Contract.kt || fail "sync tombstone"
pass "Android sync v2 policy"

echo 'PARTY_STATIC_GATE=PASS'
