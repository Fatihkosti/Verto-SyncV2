#!/usr/bin/env python3
from pathlib import Path
import json, sys

root = Path(__file__).resolve().parents[1]
checks = []

def check(name, ok, detail=''):
    checks.append({'name': name, 'status': 'PASS' if ok else 'FAIL', 'detail': detail})

api = (root/'feature/dashboard/api/src/main/kotlin/com/verto/feature/dashboard/api/TeamObservationContracts.kt').read_text()
capture = (root/'app/src/main/kotlin/com/verto/app/ui/screens/home/HomeIdeaCapture.kt').read_text()
content = (root/'app/src/main/kotlin/com/verto/app/ui/screens/home/HomeScreenContent.kt').read_text()
education = (root/'app/src/main/kotlin/com/verto/app/ui/screens/home/HomeEducationalContent.kt').read_text()
repo = (root/'feature/dashboard/src/main/kotlin/com/verto/app/feature/dashboard/data/observation/RoomTeamObservationRepository.kt').read_text()
manager = (root/'feature/dashboard/src/main/kotlin/com/verto/app/feature/dashboard/presentation/observation/TeamObservationsScreen.kt').read_text()
catalog = (root/'data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt').read_text()
migration = (root/'data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations87To88.kt').read_text()
test = (root/'data/database/src/androidTest/kotlin/com/verto/app/data/local/TeamObservationCategoryMigration359Test.kt').read_text()
sql = (root/'docs/sql/v359_team_observation_categories.sql').read_text()

for token in ('IDEA', 'MARKET_INFO', 'COMPLAINT'):
    check(f'category_{token.lower()}', token in api and token in capture and token in manager)
check('five_minute_rotation', '5L * 60L * 1000L' in capture)
check('no_placeholder', 'placeholder =' not in capture)
check('prominent_submit', 'enabled = !isSubmitting' in capture)
check('category_persisted_room_sync', 'category = category.name' in repo and 'val category: String' in repo and 'category = category' in repo)
check('education_summary_not_repeated', 'text = content.title' in education and 'text = content.fullContent' in education)
positions = [content.find('item(key = "pending")'), content.find('item(key = "idea_capture")'), content.find('education:${content.id}'), content.find('item(key = "activity")')]
check('home_order', all(p >= 0 for p in positions) and positions == sorted(positions), str(positions))
check('room_87_88', 'ROOM_SCHEMA_VERSION: Int = 88' in catalog and 'MIGRATION_87_88' in catalog and 'Migration(87, 88)' in migration)
check('room_migration_test', 'MIGRATION_87_88' in test and 'runMigrationsAndValidate(name, 88' in test)
check('server_sql_artifact', 'add column if not exists category' in sql.lower() and 'MARKET_INFO' in sql)

status = 'PASS' if all(c['status']=='PASS' for c in checks) else 'FAIL'
out = {'format':'verto-session-359-static-gate-v1','status':status,'checks':checks}
out_path = root/'docs/architecture/verification/SESSION_359_STATIC_GATE.json'
out_path.write_text(json.dumps(out, ensure_ascii=False, indent=2)+'\n')
print(json.dumps(out, ensure_ascii=False, indent=2))
sys.exit(0 if status=='PASS' else 1)
