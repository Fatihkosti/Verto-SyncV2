#!/usr/bin/env python3
"""Read-only local prerequisite check. Does not launch/download Gradle or access a network."""
from datetime import datetime, timezone
from pathlib import Path
import json
import os
import shutil
import subprocess

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[4]
commands = {
    'jvm': 'bash gradlew --offline --no-daemon :data:sync:testDebugUnitTest --tests com.verto.app.data.sync.pull.FinancialMaterializationContractV2Test',
    'room': 'bash gradlew --offline --no-daemon :data:sync:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.verto.app.data.sync.pull.FinancialMaterializerV2InstrumentedTest',
    'compile': 'bash gradlew --offline --no-daemon :app:compileDebugKotlin',
}
gradle_home = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle')))
distributions = sorted(str(p) for p in (gradle_home / 'wrapper/dists').glob('gradle-8.9-bin/*/gradle-8.9/bin/gradle'))
sdk_candidates = [Path(v) for key in ('ANDROID_HOME', 'ANDROID_SDK_ROOT') if (v := os.environ.get(key))]
sdk_candidates += [Path.home() / 'Android/Sdk', Path('/opt/android-sdk'), Path('/opt/android-sdk-linux'), Path('/usr/local/lib/android/sdk')]
sdks = sorted({str(p) for p in sdk_candidates if p.is_dir()})
versions = {}
for tool in ('java', 'kotlinc'):
    if executable := shutil.which(tool):
        result = subprocess.run([executable, '-version'], text=True, capture_output=True, timeout=15)
        versions[tool] = {'executable': executable, 'exit_code': result.returncode, 'version': (result.stdout + result.stderr).strip()}
blockers = []
if not distributions:
    blockers.append('Gradle 8.9 distribution is not cached at the wrapper distribution location')
if not sdks:
    blockers.append('Android SDK not found in environment or standard local locations')
if not shutil.which('adb'):
    blockers.append('adb is unavailable; no connected-device check or instrumentation could start')
report = {
    'checkpoint': 'B09-V02', 'captured_at_utc': datetime.now(timezone.utc).isoformat(),
    'status': 'BLOCKED' if blockers else 'PREREQUISITES_FOUND_NOT_A_TEST_RESULT',
    'tools': versions, 'gradle_distributions': distributions, 'sdk_paths': sdks,
    'adb': shutil.which('adb'), 'emulator': shutil.which('emulator'), 'blockers': blockers,
    'network_attempted': False, 'gradle_wrapper_launched': False,
    'reason_not_launched': 'Uncached wrapper downloads its distribution before --offline applies; no network workaround was attempted',
    'required_commands': commands,
    'actual_jvm': 'NOT_RUN', 'actual_room': 'NOT_RUN', 'app_ksp_hilt_compile': 'NOT_RUN',
    'G-B09': 'BLOCKED', 'owner_gate_waiver': 'NOT_RECORDED',
}
(HERE / 'runtime-environment.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report, indent=2))
raise SystemExit(2 if blockers else 0)
