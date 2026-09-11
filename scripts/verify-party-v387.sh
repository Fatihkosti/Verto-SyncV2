#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
fail(){ echo "FAIL: $*" >&2; exit 1; }
pass(){ echo "PASS: $*"; }

rg -q 'ROOM_SCHEMA_VERSION: Int = 94' data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt || fail 'Room schema is not 94'
rg -q 'MIGRATION_93_94' data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt || fail '93->94 not registered'
rg -q 'UPDATE `clients` SET `clientType`=' data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations93To94.kt || fail 'legacy tombstone not cleared'
! rg -q 'db\.execSQL\(\"(DROP TABLE|ALTER TABLE.*clients.*RENAME|ALTER TABLE.*clients.*DROP)' data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations93To94.kt || fail 'unsafe clients rebuild/drop in 93->94'
pass 'Room 94 semantic cutover is FK-safe'

rg -q 'data class PartyIdentityEntity' data/database/src/main/kotlin/com/verto/app/data/local/entity/PartyEntities.kt || fail 'PartyIdentityEntity missing'
! rg -q '\bdata class ClientEntity\b' app data feature core --glob '*.kt' --glob '!**/build/**' || fail 'ClientEntity still exists'
rg -q 'legacyClientTypeTombstone' data/database/src/main/kotlin/com/verto/app/data/local/entity/PartyEntities.kt || fail 'compat tombstone missing'
pass 'ClientEntity removed; inert Android compatibility tombstone only'

runtime_refs="$(rg -n '\.clientTypes\b|\bclientTypes\s*=|\.clientType\b|\bclientType\s*=' app data feature core --glob '*.kt' --glob '!**/build/**' --glob '!**/AppDatabaseMigrations*.kt' --glob '!**/BackupManager.kt' || true)"
[[ -z "$runtime_refs" ]] || { echo "$runtime_refs"; fail 'runtime legacy classification reference remains'; }
pass 'No runtime clientType/clientTypes authority remains'

rg -q 'verto_upsert_party_v2' data/network/src/main/kotlin/com/verto/app/data/sync/SyncClients.kt || fail 'Party V2 sync RPC missing'
! rg -q 'p_client_types|client_types' data/network/src/main/kotlin/com/verto/app/data/sync/SyncClients.kt || fail 'SyncClients still sends legacy type'
rg -q 'customer_profiles' data/database/src/main/kotlin/com/verto/app/data/local/dao/OptimalCompanyReadDao.kt || fail 'Optimal local read not Party V2'
pass 'Sync and Optimal local reads use Party V2'

rg -q 'val version: Int = 6' app/src/main/kotlin/com/verto/app/data/backup/BackupManager.kt || fail 'backup version 6 missing'
rg -q 'partyRoles: List<PartyRoleEntity>' app/src/main/kotlin/com/verto/app/data/backup/BackupManager.kt || fail 'party roles not backed up'
rg -q 'customerProfiles: List<CustomerProfileEntity>' app/src/main/kotlin/com/verto/app/data/backup/BackupManager.kt || fail 'customer profiles not backed up'
rg -q 'supplierProfiles: List<SupplierProfileEntity>' app/src/main/kotlin/com/verto/app/data/backup/BackupManager.kt || fail 'supplier profiles not backed up'
rg -q 'computeLegacyBackupChecksum' app/src/main/kotlin/com/verto/app/data/backup/BackupManager.kt || fail 'legacy checksum compatibility missing'
pass 'Backup v6 preserves Party V2 and v4/v5 checksum compatibility'

expected=(
  20260829214700_v387_party_v2_optimal_autodrive_cutover.sql
  20260829214816_v387_party_v2_external_party_backfill.sql
  20260829214843_v387_party_v2_marketer_backfill_and_autodrive_v2.sql
  20260829220246_v387_party_v2_sync_rpc.sql
  20260829220307_v387_legacy_upsert_bridge_to_party_v2.sql
  20260829220331_v387_max_and_autodrive_party_v2_cutover.sql
  20260829220343_v387_drop_legacy_client_types_column.sql
)
for f in "${expected[@]}"; do [[ -f "supabase/migrations/$f" ]] || fail "missing migration $f"; done
rg -q 'alter table public.clients drop column if exists client_types' supabase/migrations/20260829220343_v387_drop_legacy_client_types_column.sql || fail 'server column drop migration missing'
pass 'Local Supabase migration history contains the full remote v387 cutover'

python3 scripts/verify-party-v387-migration.py
[[ -x scripts/verify-party-v386-migration.py ]] && python3 scripts/verify-party-v386-migration.py
pass 'Party V2 static gate complete'
