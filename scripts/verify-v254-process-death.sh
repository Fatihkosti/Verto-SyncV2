#!/usr/bin/env bash
set -euo pipefail

# F254 device-only verification: persist in one instrumentation process, force-stop,
# then assert from a fresh instrumentation process. This is intentionally not Rotation/recreate.
TEST_COMPONENT="${TEST_COMPONENT:-com.verto.data.database.test/androidx.test.runner.AndroidJUnitRunner}"
TARGET_PACKAGE="${TARGET_PACKAGE:-com.verto.data.database}"
TEST_PACKAGE="${TEST_COMPONENT%%/*}"
CLASS="com.verto.app.data.local.InvoiceEditorDraftProcessDeathTest"

adb shell am instrument -w -e class "$CLASS#phase1_seedDraftBeforeRealProcessDeath" "$TEST_COMPONENT"
adb shell am force-stop "$TARGET_PACKAGE"
if [[ "$TEST_PACKAGE" != "$TARGET_PACKAGE" ]]; then
  adb shell am force-stop "$TEST_PACKAGE"
fi
adb shell am instrument -w -e class "$CLASS#phase2_assertDraftAfterRealProcessDeath" "$TEST_COMPONENT"
