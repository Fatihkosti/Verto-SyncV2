#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
checks = []

def add(name, ok, detail=""):
    checks.append((name, bool(ok), detail))

def text(rel):
    p = ROOT / rel
    return p.read_text(encoding="utf-8") if p.exists() else ""

contract = text("core/common/src/main/kotlin/com/verto/app/core/error/UserErrorPresentation.kt")
ui = text("core/designsystem/src/main/kotlin/com/verto/app/ui/components/VertoErrorPresentation.kt")
strings = text("core/common/src/main/res/values/strings.xml")
policy_tests = text("core/common/src/test/kotlin/com/verto/app/core/error/ErrorPresentationPolicyTest.kt")
search_vm = text("app/src/main/kotlin/com/verto/app/ui/screens/home/search/HomeSearchViewModel.kt")
search_state = text("app/src/main/kotlin/com/verto/app/ui/screens/home/search/HomeSearchUiState.kt")
search_ui = text("app/src/main/kotlin/com/verto/app/ui/screens/home/search/HomeSearchSections.kt")

add("presentation_contract_exists", "data class UserErrorPresentation" in contract)
add("four_surfaces", all(x in contract for x in ["FIELD", "INLINE", "BANNER", "FULL_SCREEN"]))
add("recovery_actions", all(x in contract for x in ["RETRY", "LOGIN", "EDIT", "NONE"]))
add("context_policy", all(x in contract for x in ["FIELD", "FORM", "TRANSIENT_ACTION", "SCREEN_LOAD"]))
add("retry_advice_drives_recovery", "failure.retryAdvice" in contract)
add("automatic_backoff_no_manual_retry", "RetryAdvice.AUTOMATIC_BACKOFF" in contract and "ErrorRecoveryAction.NONE" in contract)
add("targeted_validation_field", "targetFor(failure).isNullOrBlank()" in contract and "ErrorSurface.FIELD" in contract)
add("designsystem_renderer_exists", "fun VertoUserError" in ui)
add("designsystem_resolves_resources", "stringResource(CommonR.string.common_error_" in ui)
add("full_screen_renderer", "ErrorSurface.FULL_SCREEN -> VertoEmptyState" in ui)
add("banner_renderer", "ErrorSurface.BANNER -> VertoStatusBanner" in ui)
add("inline_renderer", "VertoInlineStatus" in ui)
add("localized_copy_consolidated", all(k in strings for k in [
    "common_error_network_unavailable", "common_error_session_expired",
    "common_error_permission_denied", "common_error_conflict",
    "common_error_server_unavailable", "common_error_unexpected",
]))
add("policy_tests_present", policy_tests.count("@Test") >= 6)
add("home_search_typed_error", "ErrorPresentationPolicy.from" in search_vm and "UserErrorPresentation" in search_state)
add("home_search_no_generic_catch_copy", 'catch (_: Exception)' not in search_vm and 'تعذّر البحث الآن' not in search_vm)
add("home_search_shared_renderer", "VertoUserError" in search_ui)
add("home_search_retry_action", "retrySearch" in search_vm and "onRetry = onRetry" in search_ui)

proc = subprocess.run(
    [sys.executable, str(ROOT / "tools/quality/user_error_boundary_gate.py")],
    cwd=ROOT,
    capture_output=True,
    text=True,
)
add("anti_regression_gate", proc.returncode == 0, proc.stdout.strip() or proc.stderr.strip())

failed = [c for c in checks if not c[1]]
for name, ok, detail in checks:
    suffix = f": {detail}" if detail else ""
    print(f"{'PASS' if ok else 'FAIL'} {name}{suffix}")
print(f"\nSESSION_363_STATIC={len(checks)-len(failed)}/{len(checks)} {'PASS' if not failed else 'FAIL'}")
raise SystemExit(1 if failed else 0)
