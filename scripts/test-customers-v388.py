#!/usr/bin/env python3
from pathlib import Path
import re, subprocess, sys

ROOT = Path(__file__).resolve().parents[1]
PASS=[]; FAIL=[]

def check(name, cond, detail=''):
    (PASS if cond else FAIL).append((name, detail))
    print(('PASS' if cond else 'FAIL') + ': ' + name + (f' — {detail}' if detail else ''))

def text(path): return (ROOT/path).read_text(encoding='utf-8')

models = text('feature/party/src/main/kotlin/com/verto/app/feature/party/domain/model/PartyIdentityModels.kt')
entity = text('data/database/src/main/kotlin/com/verto/app/data/local/entity/PartyNormalizedEntities.kt')
party_entity = text('data/database/src/main/kotlin/com/verto/app/data/local/entity/PartyEntities.kt')
vm = text('feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/addclient/AddEditClientViewModel.kt')
dao = text('data/database/src/main/kotlin/com/verto/app/data/local/dao/ClientDao.kt')
repo = text('feature/party/src/main/kotlin/com/verto/app/data/repository/ClientRepository.kt')
sync = text('data/network/src/main/kotlin/com/verto/app/data/sync/SyncClients.kt')
list_screen = text('feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientsListScreen.kt')
dash_vm = text('feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientDashboardViewModel.kt')
dash_screen = text('feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientDashboardScreen.kt')
backup = text('app/src/main/kotlin/com/verto/app/data/backup/BackupManager.kt')
optimal = text('data/database/src/main/kotlin/com/verto/app/data/local/dao/OptimalCompanyReadDao.kt')

# Party V2 semantic profile
for field in ['ageYears','purchaseContactName','businessActivity','workplaceName','shopName','workshopName','vehicleModels','workshopWorkerCount']:
    check(f'CustomerProfile explicit field {field}', re.search(rf'\b{field}\b', models) is not None)
check('CustomerProfile no opaque vehicleInformation field', 'vehicleInformation' not in models)
check('Customer profile Room key is organization + party', 'primaryKeys = ["organization_id", "party_id"]' in entity)
check('Party identity has only inert clientType tombstone', 'legacyClientTypeTombstone' in party_entity and 'Party V2 owns classification' in party_entity)

# Add/edit write path: customer semantics stay out of identity compatibility fields
check('Customer save clears workplace identity', 'workplace       = if (isSupplierRole) supplierWorkplace.trim() else ""' in vm)
check('Customer save clears carType identity', 'carType         = ""' in vm)
check('Customer save clears secondaryPhones identity', 'secondaryPhones = ""' in vm)
check('Customer save writes explicit CustomerProfile', 'CustomerProfile(' in vm and 'customerProfileDraft' in vm)

# Runtime classification authority
runtime = subprocess.run([
    'rg','-n',r'\.clientTypes\b|\bclientTypes\s*=|\.clientType\b|\bclientType\s*=',
    'app','data','feature','core','--glob','*.kt','--glob','!**/build/**',
    '--glob','!**/AppDatabaseMigrations*.kt','--glob','!**/BackupManager.kt'
], cwd=ROOT, text=True, capture_output=True)
check('No runtime clientType/clientTypes authority', runtime.returncode == 1, runtime.stdout.strip())

# Tenant scope guards in customer-facing reads
customer_methods = [
    'getAllClientsForOrganization','observeClientRoleProjections','observeClientRoleProjection',
    'getClientRoleProjectionSync','getClientRoleProjectionsSync','getClientById','searchClients',
    'getAllClientsWithBalancePaged','searchClientsWithBalancePaged','getAllClientsWithBalance',
    'searchClientsWithBalance','countInvoicesForClient','getInvoiceIdsForClient','getClientByIdSync'
]
for method in customer_methods:
    m = re.search(rf'abstract\s+(?:suspend\s+)?fun\s+{method}\s*\(([^)]*)\)', dao)
    check(f'{method} requires organizationId', bool(m and 'organizationId' in m.group(1)), m.group(1).strip() if m else 'method missing')
check('Financial customer queries scope invoices to organization', dao.count('organization_id=:organizationId') + dao.count('organization_id = :organizationId') >= 12)
check('Repository resolves trusted organization for reads/writes', 'trustedOrganizationId()' in repo and 'FAIL_ORG_SCOPE' in repo)

# Migrations and sync
for script in ['scripts/verify-party-v386-migration.py','scripts/verify-party-v387-migration.py','scripts/verify-party-v388-migration.py']:
    r = subprocess.run([sys.executable, script], cwd=ROOT, text=True, capture_output=True)
    check(Path(script).name + ' executes', r.returncode == 0, (r.stdout+r.stderr).strip())
check('Party sync uses verto_upsert_party_v2', 'verto_upsert_party_v2' in sync)
check('Party sync sends no client_types', 'client_types' not in sync and 'p_client_types' not in sync)
check('Optimal local company reader uses Party V2 profile', 'customer_profiles' in optimal and 'segment' in optimal)

# Backup/restore protection
check('Backup schema version 6', 'val version: Int = 6' in backup)
for token in ['partyRoles: List<PartyRoleEntity>', 'customerProfiles: List<CustomerProfileEntity>', 'supplierProfiles: List<SupplierProfileEntity>']:
    check('Backup contains ' + token.split(':')[0], token in backup)
check('Legacy backup checksum compatibility retained', 'computeLegacyBackupChecksum' in backup)

# Legacy screen removal
client_screen_exists = (ROOT/'feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientScreen.kt').exists()
check('Legacy ClientScreen removed', not client_screen_exists)

# v388 closeout acceptance contract.
enum_match = re.search(r'enum class CustomerSegment\s*\{([^}]*)\}', models, re.S)
segments = []
if enum_match:
    segments = [x.strip() for x in enum_match.group(1).replace('\n',' ').split(',') if x.strip()]
expected = ['INDIVIDUAL','COMPANY','WORKSHOP_OWNER','MARKETER','TRADER','DISTRIBUTOR']
check('Approved six customer groups only', segments == expected, 'current=' + ','.join(segments))
check('Customer list delete action archives instead of permanent delete', 'deleteClientPermanently' not in list_screen, 'normal customer removal must archive the CUSTOMER role')
profile_consumed = 'observeCustomerProfile' in dash_vm or any(k in dash_screen for k in ['purchaseContactName','businessActivity','workshopName','vehicleModels','workplaceName','shopName'])
check('Customer dashboard consumes CustomerProfile V2', profile_consumed, 'dashboard must render CustomerProfile V2')

check('Room schema is 95 and migration 94->95 is wired',
      'ROOM_SCHEMA_VERSION: Int = 95' in text('data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt')
      and 'MIGRATION_94_95' in text('data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt'))
role_dao = text('data/database/src/main/kotlin/com/verto/app/data/local/dao/PartyRoleDao.kt')
check('Competitor is derived from active CUSTOMER plus SUPPLIER roles',
      'observeIsCompetitor' in role_dao and "role='CUSTOMER'" in role_dao and "role='SUPPLIER'" in role_dao)
server_migration = text('supabase/migrations/20260830050000_v388_customer_closeout.sql')
check('Server migration enforces six customer segments',
      'customer_profiles_segment_v388_check' in server_migration
      and all(x in server_migration for x in expected)
      and "segment='COMPETITOR'" not in server_migration)
check('Max server contract uses role-derived competitor',
      'verto_max_join_code_candidates_v1' in server_migration
      and "pr.role='CUSTOMER'" in server_migration
      and "prs.role='SUPPLIER'" in server_migration)
check('Customer archive preserves other Party roles',
      'roleCommands.setStatus(id, organizationId, role' in repo
      and 'deleteClientById(id)' not in repo[repo.find('override suspend fun archiveRole'):repo.find('override suspend fun deleteClientPermanently')])

print(f'CUSTOMERS_V388_PASS={len(PASS)}')
print(f'CUSTOMERS_V388_FAIL={len(FAIL)}')
if FAIL:
    print('CUSTOMERS_V388_GATE=FAIL')
    sys.exit(1)
print('CUSTOMERS_V388_GATE=PASS')
