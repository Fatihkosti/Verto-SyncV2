#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
errors = []
notes = []

def text(rel: str) -> str:
    path = root / rel
    if not path.exists():
        errors.append(f"missing:{rel}")
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")

components = text("feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsV2Components.kt")
planning = text("feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningScreen.kt")
trip = text("feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/ShipmentPlanningV237Steps.kt")
progress = text("feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsDraftProgressStore.kt")
detail = text("feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsShipmentDetailScreen.kt")
nav = text("app/src/main/kotlin/com/verto/app/ui/navigation/InventoryShipmentsNavGraph.kt")
tokens = text("core/designsystem/src/main/kotlin/com/verto/app/ui/theme/DesignTokens.kt")
shipment_build = text("feature/shipment/build.gradle.kts")
catalog = text("gradle/libs.versions.toml")

for token in ("VertoTopBar(", "VertoFormCard(", "VertoTextField(", "VertoPrimaryButton(", "VertoLoadingState("):
    if token not in components:
        errors.append(f"design-system-common-missing:{token}")
for forbidden in ("OutlinedTextField(", "OutlinedButton("):
    if forbidden in components:
        errors.append(f"parallel-common-component:{forbidden}")
if "VertoConfirmationDialog(" not in planning or "AlertDialog(" in planning:
    errors.append("planning-confirmations-not-consolidated")
if "role = Role.RadioButton" not in trip or "this.selected = selected" not in trip:
    errors.append("trip-choice-accessibility-semantics-missing")
if "RadioButton(selected = selected, onClick = null)" not in trip:
    errors.append("trip-choice-duplicate-talkback-action")
if "val minTouchTarget = 48.dp" not in tokens:
    errors.append("min-touch-target-not-48dp")
for durable in ("loadProgress()", "saveProgress(", "loadRouteWorkspace(", "saveRouteWorkspace(", "loadReceivingDraft(", "saveReceivingDraft("):
    if durable not in progress:
        errors.append(f"durability-contract-missing:{durable}")
if "openDocument" not in detail:
    errors.append("document-open-ui-wiring-missing")
if "LOGISTICS_V2_DETAIL_ROUTE" not in nav or "LOGISTICS_V2_PLAN_ROUTE" not in nav:
    errors.append("current-logistics-navigation-missing")
presentation_root = root / "feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation"
legacy_kt = [
    p for p in presentation_root.rglob("*.kt")
    if "logisticsv2" not in p.parts
]
if legacy_kt:
    errors.append("reachable-or-present-legacy-shipment-presentation:" + ",".join(str(p.relative_to(root)) for p in legacy_kt))
for required in (
    "feature/shipment/src/test/kotlin/com/verto/app/feature/shipment/application/LogisticsV242EndToEndIntegrationTest.kt",
    "feature/shipment/src/androidTest/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsV242UiQualityTest.kt",
):
    if not (root / required).exists():
        errors.append(f"missing-test:{required}")
if "androidx.ui.test.junit4" not in shipment_build:
    errors.append("compose-ui-test-dependency-not-wired")
if "androidx-ui-test-junit4" not in catalog:
    errors.append("compose-ui-test-catalog-entry-missing")

wrapper = root / "gradle/wrapper/gradle-wrapper.jar"
if not wrapper.exists():
    notes.append("gradle-wrapper.jar absent: full Gradle/instrumentation execution unavailable in this source package")

if errors:
    print("V242_QUALITY_FAIL")
    for item in errors:
        print("FAIL", item)
    for item in notes:
        print("NOTE", item)
    sys.exit(1)

print("V242_QUALITY_PASS")
for item in notes:
    print("NOTE", item)
