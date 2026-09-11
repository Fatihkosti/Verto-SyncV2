#!/usr/bin/env bash
set -euo pipefail
fail(){ echo "FAIL: $1" >&2; exit 1; }
pass(){ echo "PASS: $1"; }

entity=data/database/src/main/kotlin/com/verto/app/data/local/entity/PartyNormalizedEntities.kt
rg -q 'primaryKeys = \["organization_id", "party_id"\]' "$entity" || fail "profile composite tenant key missing"
[[ $(rg -c '@ColumnInfo\(name = "organization_id"\) val organizationId: String' "$entity") -ge 2 ]] || fail "customer/supplier profile organization_id missing"
pass "customer/supplier profiles are organization scoped"

catalog=data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt
rg -q 'ROOM_SCHEMA_VERSION: Int = 93' "$catalog" || fail "Room schema 93 missing"
rg -q 'MIGRATION_92_93' "$catalog" || fail "92->93 migration not connected"
[[ -f data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations92To93.kt ]] || fail "92->93 migration file missing"
pass "Room 92->93 migration connected"

dao=data/database/src/main/kotlin/com/verto/app/data/local/dao/ClientDao.kt
for fn in getAllClientsForOrganization observeClientRoleProjections getClientById searchClients getAllClientsSyncForOrganization getAllClientsWithBalance searchClientsWithBalance getAllClientsWithBalancePaged searchClientsWithBalancePaged getDirtyClientsSync; do
  rg -q "$fn" "$dao" || fail "missing scoped DAO function $fn"
done
! rg -q 'abstract fun getAllClients\(\): Flow<List<ClientEntity>>' "$dao" || fail "global getAllClients remains"
! rg -q 'abstract suspend fun getAllClientsSync\(\): List<ClientEntity>' "$dao" || fail "global getAllClientsSync remains"
! rg -q 'abstract fun getTotalClientsCount\(\)' "$dao" || fail "global client count remains"
! rg -q 'abstract suspend fun searchClientsByPrefix\(' "$dao" || fail "global prefix search remains"
pass "unscoped directory DAO entry points removed"

repo=feature/party/src/main/kotlin/com/verto/app/data/repository/ClientRepository.kt
rg -q 'sessionReader.organizationId' "$repo" || fail "repository organization source missing"
rg -q 'getAllClientsForOrganization|clientDao::getAllClientsForOrganization' "$repo" || fail "repository list not tenant scoped"
rg -q 'getAllClientsSyncForOrganization\(organizationId\)' "$repo" || fail "repository sync list not tenant scoped"
pass "party directory reads are tenant scoped"

sync=data/network/src/main/kotlin/com/verto/app/data/sync/SyncClients.kt
rg -q 'getDirtyClientsSync\(orgId\)' "$sync" || fail "legacy fallback push not scoped"
rg -q 'getAllClientsSyncForOrganization\(orgId\)' "$sync" || fail "legacy fallback pull not scoped"
participant=feature/party/src/main/kotlin/com/verto/app/feature/party/data/sync/ClientSyncParticipant.kt
rg -q 'partyV2OwnsTransport' "$participant" || fail "V2 transport ownership gate missing"
rg -q '!partyV2OwnsTransport' "$participant" || fail "legacy client transport is not gated"
pass "legacy identity transport is disabled under Party V2 authority"

recovery=data/sync/src/main/kotlin/com/verto/app/data/sync/recovery/UnifiedSyncRecoveryRegistry.kt
rg -q '"PARTY_IDENTITY".*REPLAY_FROM_UNIFIED_OUTBOX.*"sync_outbox"' "$recovery" || fail "party identity recovery owner mismatch"
rg -q '"CUSTOMER_PROFILE".*REPLAY_FROM_UNIFIED_OUTBOX.*"sync_outbox"' "$recovery" || fail "customer profile recovery owner mismatch"
rg -q '"SUPPLIER_PROFILE".*REPLAY_FROM_UNIFIED_OUTBOX.*"sync_outbox"' "$recovery" || fail "supplier profile recovery owner mismatch"
rg -q 'customer_profiles WHERE customer_profiles.organization_id=\?' "$recovery" || fail "customer recovery prune not tenant scoped"
rg -q 'supplier_profiles WHERE supplier_profiles.organization_id=\?' "$recovery" || fail "supplier recovery prune not tenant scoped"
pass "recovery follows V2 unified outbox and tenant scope"

roleDao=data/database/src/main/kotlin/com/verto/app/data/local/dao/PartyRoleDao.kt
for fn in observeCustomerProfile getCustomerProfileSync observeSupplierProfile getSupplierProfileSync deleteCustomerProfileFromRemote deleteSupplierProfileFromRemote; do
  line=$(rg -n "$fn" "$roleDao" | head -1 | cut -d: -f1)
  [[ -n "$line" ]] || fail "missing role DAO function $fn"
done
rg -q 'WHERE organization_id=:organizationId AND party_id=:partyId' "$roleDao" || fail "profile DAO queries not organization scoped"
pass "profile read/delete DAO is tenant scoped"

optimal=data/database/src/main/kotlin/com/verto/app/data/local/dao/OptimalCompanyReadDao.kt
rg -q 'INNER JOIN customer_profiles cp' "$optimal" || fail "Optimal company reader still bypasses Party V2 profile"
rg -q 'cp.organization_id = :organizationId' "$optimal" || fail "Optimal company profile not tenant scoped"
rg -q 'invoice.organization_id = :organizationId' "$optimal" || fail "Optimal company invoice count not tenant scoped"
! rg -q 'c.clientType' "$optimal" || fail "Optimal company reader still depends on legacy clientType"
optimalInvoices=data/database/src/main/kotlin/com/verto/app/data/local/dao/OptimalCompanyInvoiceReadDao.kt
rg -q 'profile.vehicle_models' "$optimalInvoices" || fail "Optimal invoice reader still misses Party V2 vehicle profile"
! rg -q 'company.carType|other_link' "$optimalInvoices" || fail "Optimal invoice reader retains legacy/cross-tenant exclusion"
[[ $(rg -c 'invoice.organization_id = link.organization_id' "$optimalInvoices") -ge 4 ]] || fail "Optimal invoice projections not fully tenant scoped"
pass "Optimal customer projections use Party V2 and organization scope"

echo 'PARTY_386_STATIC_GATE=PASS'
