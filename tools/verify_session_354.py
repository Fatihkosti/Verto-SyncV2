from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
checks=[]
def text(rel): return (ROOT/rel).read_text(encoding='utf-8')
def check(name, cond):
    checks.append((name,bool(cond)))

migration=text('data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations86To87.kt')
catalog=text('data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt')
home=text('app/src/main/kotlin/com/verto/app/ui/screens/home/HomeScreenContent.kt')
pending=text('app/src/main/kotlin/com/verto/app/ui/screens/home/HomePendingActions.kt')
idea=text('app/src/main/kotlin/com/verto/app/ui/screens/home/HomeIdeaCapture.kt')
edu=text('app/src/main/kotlin/com/verto/app/ui/screens/home/HomeEducationalContent.kt')
header=text('app/src/main/kotlin/com/verto/app/ui/screens/home/HomeHeader.kt')
drawer=text('app/src/main/kotlin/com/verto/app/ui/navigation/DrawerNavigationViewModel.kt')
repo=text('feature/dashboard/src/main/kotlin/com/verto/app/feature/dashboard/data/observation/RoomTeamObservationRepository.kt')
manager=text('feature/dashboard/src/main/kotlin/com/verto/app/feature/dashboard/presentation/observation/TeamObservationsScreen.kt')
sql=text('docs/sql/v354_team_observations.sql')

check('Room schema 87 + migration 86→87', 'ROOM_SCHEMA_VERSION: Int = 87' in catalog and 'Migration(86, 87)' in migration)
check('Team observation entity/DAO/repository exist', all((ROOT/p).exists() for p in [
 'data/database/src/main/kotlin/com/verto/app/data/local/entity/TeamObservationEntity.kt',
 'data/database/src/main/kotlin/com/verto/app/data/local/dao/TeamObservationDao.kt',
 'feature/dashboard/src/main/kotlin/com/verto/app/feature/dashboard/data/observation/RoomTeamObservationRepository.kt']))
check('Free-text capture with no category/AI', 'VertoOutlinedTextField' in idea and 'category' not in idea.lower() and 'classify' not in idea.lower() and 'classification' not in idea.lower())
check('Idea capture appended after existing activity', home.find('key = "activity"') < home.find('key = "idea_capture"'))
check('Education is last content block', home.find('key = "idea_capture"') < home.find('education:${content.id}'))
check('Education summary is visible heading', 'text = content.summary' in edu and 'TajawalFontFamily' in edu)
check('No education title label on Home', 'home_education_title' not in edu and 'content.title' not in edu)
check('Pending Home shows only first event', 'pendingActions.firstOrNull()' in pending and 'pendingActions.size > 1' in pending)
check('Legacy Home pager production references removed', not any('HorizontalPager' in p.read_text(encoding='utf-8') for p in (ROOT/'app/src/main/kotlin/com/verto/app/ui/screens/home').glob('*.kt')))
check('Username orange only in Home header treatment', 'withStyle(SpanStyle(color = AccentPrimary))' in header)
check('Drawer branch comes from organization city', 'branchLabel = org.city.trim()' in drawer)
check('Manager gets separate cards + status/importance controls', 'items(observations' in manager and 'setImportant' in text('feature/dashboard/src/main/kotlin/com/verto/app/feature/dashboard/presentation/observation/TeamObservationsViewModel.kt') and 'setStatus' in text('feature/dashboard/src/main/kotlin/com/verto/app/feature/dashboard/presentation/observation/TeamObservationsViewModel.kt'))
check('Raw text survives repository path', 'text = normalized' in repo and 'text = text' in repo)
check('Supabase RLS tenant + manager status/importance policy', 'enable row level security' in sql and "public.verto_current_role() = 'admin'" in sql and "status = 'NEW'" in sql and 'is_important = false' in sql)
check('No delete grant for observations', 'grant select, insert, update' in sql and 'grant delete' not in sql.lower())

for xml in ['app/src/main/res/values/strings.xml','feature/dashboard/src/main/res/values/strings.xml']:
    ET.parse(ROOT/xml)
check('Modified string resources are well-formed XML', True)

failed=[n for n,ok in checks if not ok]
for n,ok in checks: print(('PASS' if ok else 'FAIL') + ' | ' + n)
print(f'RESULT: {len(checks)-len(failed)}/{len(checks)} PASS')
if failed: raise SystemExit(1)
