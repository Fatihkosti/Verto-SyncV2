from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def text(path):
    return (ROOT / path).read_text(encoding='utf-8')

checks = []
def check(name, ok):
    checks.append((name, bool(ok)))

catalog = text('data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt')
entity = text('data/database/src/main/kotlin/com/verto/app/data/local/entity/UnifiedSyncProducerV307Entities.kt')
dao = text('data/database/src/main/kotlin/com/verto/app/data/local/dao/UnifiedSyncProducerV307Dao.kt')
org_repo = text('data/operations/src/main/kotlin/com/verto/app/data/repository/Orgsettingsrepository.kt')
org_participant = text('app/src/main/kotlin/com/verto/app/feature/organization/bridge/OrganizationSyncParticipant.kt')
applier = text('data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedSyncChangeApplier.kt')
edu_repo = text('feature/dashboard/src/main/kotlin/com/verto/app/feature/dashboard/data/education/RoomEducationalContentRepository.kt')
auth_remote = text('data/network/src/main/kotlin/com/verto/app/data/remote/AuthAccountRemoteSource.kt')
profile_gateway = text('app/src/main/kotlin/com/verto/app/feature/profile/bridge/ProfileGatewayAdapter.kt')
home_vm = text('app/src/main/kotlin/com/verto/app/ui/screens/home/HomeViewModel.kt')

check('room_version_86', 'ROOM_SCHEMA_VERSION: Int = 86' in catalog and 'MIGRATION_85_86' in catalog)
check('org_dirty_column', 'is_dirty' in entity and 'is_dirty = 1' in dao)
check('org_local_save_dirty', 'isDirty = true' in org_repo)
check('org_push_only_dirty', 'getDirtyOrganizationSettings' in org_repo and 'settingsPushMutex.withLock' in org_repo)
check('org_pull_never_overwrites_dirty', 'if (dao.getDirtyOrganizationSettings(organizationId) != null) return' in org_repo)
check('org_participant_push_then_pull', 'pushPendingToSupabase()' in org_participant and 'orgSettingsRepository.syncFromSupabase()' in org_participant)
check('org_pull_no_push_before_read', 'pushToSupabase(orgSettingsRepository.orgSettings.first())' not in org_participant)
check('remote_org_apply_clean', 'isDirty = false' in applier)
check('education_target_codec_matches_parser', 'joinToString("|")' in edu_repo and '${it.type.name}\\u001F${it.value.trim()}' in edu_repo and "element.content.split('|')" in applier and "encoded.split('\\u001f'" in applier)
check('profile_fetch_refreshes_session_name', 'sessionWriter.setUserName(profile.name.trim())' in auth_remote)
check('profile_name_write_is_server_first', 'postgrest["app_users"].update' in auth_remote and 'authRepository.updateMyName(name).getOrThrow()' in profile_gateway)
check('home_no_ownername_shadow_write', 'setOwnerName' not in home_vm and 'AppPreferencesAccess' not in home_vm)

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(f"{'PASS' if ok else 'FAIL'} {name}")
print(f"TOTAL {len(checks)-len(failed)}/{len(checks)}")
raise SystemExit(1 if failed else 0)
