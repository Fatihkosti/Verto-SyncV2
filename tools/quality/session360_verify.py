#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
checks = []

def text(rel):
    return (ROOT / rel).read_text()

def check(name, ok):
    checks.append((name, bool(ok)))
    print(('PASS' if ok else 'FAIL'), name)

observe = text('feature/dashboard/src/main/kotlin/com/verto/app/feature/dashboard/application/activityevent/ObserveActivityEventsUseCase.kt')
feed = text('app/src/main/kotlin/com/verto/app/ui/screens/home/HomeActivityFeed.kt')
tokens = text('app/src/main/kotlin/com/verto/app/ui/screens/home/HomeDesignTokens.kt')
content = text('app/src/main/kotlin/com/verto/app/ui/screens/home/HomeScreenContent.kt')
test = text('feature/dashboard/src/test/kotlin/com/verto/app/feature/dashboard/application/activityevent/ActivityEventHardening339Test.kt')

check('activity window remains seven days', 'ACTIVITY_WINDOW_DAYS: Long = 7L' in observe)
check('activity output capped at thirty', 'MAX_HOME_ACTIVITY_EVENTS: Int = 30' in observe and '.take(ObserveActivityEventsUseCase.MAX_HOME_ACTIVITY_EVENTS)' in observe)
check('activity remains newest first', 'compareByDescending<ActivityEventContribution> { contribution -> contribution.event.occurredAtEpochMillis }' in observe)
check('five rows maximum before inner scroll', 'activityMaxVisibleRows = 5' in tokens and 'events.size.coerceIn(1, HomeDesignTokens.activityMaxVisibleRows)' in feed)
check('activity rows meet 48dp touch target', 'activityRowMinHeight = 48.dp' in tokens)
check('activity feed remains internally scrollable', 'LazyColumn(' in feed and 'state = listState' in feed)
check('short feeds shrink instead of reserving five rows', 'val visibleRowCount = events.size.coerceIn(1, HomeDesignTokens.activityMaxVisibleRows)' in feed)
check('fab overlap protected at bottom', 'activityBottomPadding' in tokens and 'Spacer(Modifier.height(HomeDesignTokens.activityBottomPadding))' in content)
check('home order preserved', content.index('item(key = "pending")') < content.index('item(key = "idea_capture")') < content.index('item(key = "education:${content.id}")') < content.index('item(key = "activity")'))
check('thirty event regression test present', 'assertEquals(30, ObserveActivityEventsUseCase.MAX_HOME_ACTIVITY_EVENTS)' in test)

failed = [name for name, ok in checks if not ok]
print(f'RESULT: {len(checks)-len(failed)}/{len(checks)} PASS')
if failed:
    print('FAILED:', ', '.join(failed))
    sys.exit(1)
