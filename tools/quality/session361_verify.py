#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
checks = []

def add(name, ok, detail=""):
    checks.append((name, bool(ok), detail))

app_failure = ROOT / "core/common/src/main/kotlin/com/verto/app/core/error/AppFailure.kt"
classifier = ROOT / "core/common/src/main/kotlin/com/verto/app/core/error/ErrorClassifier.kt"
structured = ROOT / "core/common/src/main/kotlin/com/verto/app/core/error/StructuredFailures.kt"
humanizer = ROOT / "core/crash/src/main/kotlin/com/verto/app/utils/ErrorHumanizer.kt"
tests = ROOT / "core/common/src/test/kotlin/com/verto/app/core/error/ErrorClassifierTest.kt"

for p in (app_failure, classifier, structured, humanizer, tests):
    add(f"exists:{p.name}", p.exists())

app_text = app_failure.read_text() if app_failure.exists() else ""
classifier_text = classifier.read_text() if classifier.exists() else ""
humanizer_text = humanizer.read_text() if humanizer.exists() else ""
test_text = tests.read_text() if tests.exists() else ""

required_failures = [
    "NetworkUnavailable", "ConnectionFailed", "Timeout", "Unauthorized",
    "PermissionDenied", "Validation", "BusinessRule", "NotFound", "Conflict",
    "RateLimited", "Server", "RemoteRejected", "LocalStorage", "Unknown",
]
add("canonical_failure_taxonomy", all(name in app_text for name in required_failures))
add("structured_remote_contract", "RemoteFailureMetadata" in structured.read_text() and "RemoteFailureException" in structured.read_text())
add("cancellation_rethrown", "CancellationException" in classifier_text and "throw it" in classifier_text)
add("generic_io_not_network", "it is IOException" in classifier_text and "NetworkUnavailable" in classifier_text)
add("no_arabic_trust_heuristic", "hasArabic" not in humanizer_text)
add("no_throwable_message_mapping", not re.search(r"\b(message|t\.message|throwable\.message)\b.*hasArabic", humanizer_text))
add("humanizer_delegates_classifier", "ErrorClassifier.classify" in humanizer_text)
add("regression_tests_present", all(x in test_text for x in ["genericIOException_isUnknown_notNetworkOrStorage", "arabicText_doesNotMakeUnknownExceptionTrusted", "cancellation_isRethrown"]))

failed = [c for c in checks if not c[1]]
for name, ok, detail in checks:
    print(f"{'PASS' if ok else 'FAIL'} {name}{(': ' + detail) if detail else ''}")
print(f"\nSESSION_361_STATIC={len(checks)-len(failed)}/{len(checks)} PASS" if not failed else f"\nSESSION_361_STATIC={len(checks)-len(failed)}/{len(checks)} FAIL")
raise SystemExit(1 if failed else 0)
