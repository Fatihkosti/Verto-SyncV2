#!/usr/bin/env bash
set -euo pipefail
fail(){ echo "FAIL: $1" >&2; exit 1; }
pass(){ echo "PASS: $1"; }

[[ ! -f feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientScreen.kt ]] || fail "legacy ClientScreen still exists"
! rg -q 'Screen\.Client\b|fun ClientScreen\(' app feature || fail "legacy client route/screen reference remains"
rg -q 'client_dashboard/\$\{raw.removePrefix\("client/"\)\}' app/src/main/kotlin/com/verto/app/notifications/NotificationRoutePolicy.kt || fail "legacy deep-link redirect missing"
pass "legacy ClientScreen removed and old deep links canonicalized"

profile=feature/party/src/main/kotlin/com/verto/app/feature/party/domain/model/PartyIdentityModels.kt
for field in ageYears purchaseContactName businessActivity workplaceName shopName workshopName vehicleModels workshopWorkerCount; do
  rg -q "val $field" "$profile" || fail "missing CustomerProfile field $field"
done
! rg -q 'vehicleInformation' "$profile" || fail "overloaded/opaque vehicleInformation remains in domain profile"
pass "CustomerProfile has single-purpose fields"

vm=feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/addclient/AddEditClientViewModel.kt
rg -q 'carType\s*=\s*""' "$vm" || fail "customer identity still receives carType"
rg -q 'secondaryPhones\s*=\s*""' "$vm" || fail "customer identity still receives secondaryPhones"
rg -q 'customerProfileDraft' "$vm" || fail "typed profile draft missing"
pass "new customer writes use Party V2 profile instead of overloaded identity fields"

catalog=data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt
schema=$(sed -n 's/.*ROOM_SCHEMA_VERSION: Int = \([0-9][0-9]*\).*/\1/p' "$catalog" | head -1)
[[ ${schema:-0} -ge 92 ]] || fail "Room schema must be >=92"
rg -q 'MIGRATION_91_92' "$catalog" || fail "91->92 migration not connected"
[[ -f data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations91To92.kt ]] || fail "91->92 migration file missing"
pass "Room migration connected"

sql=supabase/migrations/20260829230000_v385_customer_profile_v2.sql
[[ -f "$sql" ]] || fail "Supabase expand/backfill migration missing"
for col in age_years purchase_contact_name business_activity workplace_name shop_name workshop_name vehicle_models; do
  rg -q "$col" "$sql" || fail "server migration missing $col"
done
pass "Supabase expand/backfill migration present"

echo 'PARTY_385_STATIC_GATE=PASS'
