#!/usr/bin/env bash
set -euo pipefail
fail(){ echo "FAIL: $1" >&2; exit 1; }
pass(){ echo "PASS: $1"; }

sync=data/network/src/main/kotlin/com/verto/app/data/sync/SyncClients.kt
dtos=data/network/src/main/kotlin/com/verto/app/data/remote/dto/PartyNormalizedDtos.kt
role_dao=data/database/src/main/kotlin/com/verto/app/data/local/dao/PartyRoleDao.kt
client_dao=data/database/src/main/kotlin/com/verto/app/data/local/dao/ClientDao.kt

for f in "$sync" "$dtos" "$role_dao" "$client_dao"; do [[ -f "$f" ]] || fail "missing $f"; done

grep -q 'postgrest\["party_roles"\]' "$sync" || fail "party_roles pull missing"
grep -q 'postgrest\["customer_profiles"\]' "$sync" || fail "customer_profiles pull missing"
grep -q 'postgrest\["supplier_profiles"\]' "$sync" || fail "supplier_profiles pull missing"
pass "legacy Party pull hydrates normalized directory"

! awk '/suspend fun SyncRuntime.pullClients/{f=1} f{print}' "$sync" | grep -q 'if (remote.*isEmpty()).*return' || fail "empty client delta must not skip normalized Party refresh"
grep -q 'db.withTransaction' "$sync" || fail "Party remote apply must be atomic"
grep -q 'setLastPulledAt.*KEY_LAST_PULLED_CLIENTS' "$sync" || fail "client cursor advancement missing"
pass "client cursor advances only after normalized refresh path"

grep -q 'existing?.dirty == true' "$sync" || fail "local dirty Party protection missing"
grep -q 'hasActivePartyMutation("ROLE", partyId)' "$sync" || fail "pending role mutation protection missing"
grep -q 'getCustomerProfileSync' "$role_dao" || fail "customer profile sync read missing"
pass "pending local Party edits remain protected"

count=$(grep -c 'AND pr.deleted_at IS NULL' "$client_dao")
[[ "$count" -ge 3 ]] || fail "active role readers must exclude tombstones"
pass "customer/supplier list excludes deleted roles"

grep -q '@SerialName("server_revision")' "$dtos" || fail "server revision mapping missing"
grep -q '@SerialName("server_updated_at")' "$dtos" || fail "server timestamp mapping missing"
pass "server normalized schema mapped explicitly"

echo 'PARTY_DIRECTORY_SYNC_V373_STATIC_GATE=PASS'
