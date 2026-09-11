#!/usr/bin/env python3
import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "tools" / "technical_debt"))
import verify  # noqa: E402


class TechnicalDebtGovernanceTest(unittest.TestCase):
    def test_registry_integrity(self):
        self.assertEqual(verify.registry(ROOT)["status"], "PASS")
        self.assertEqual(verify.registry(ROOT)["critical_open_count"], 0)

    def test_monotonic_ratchet(self):
        self.assertEqual(verify.ratchet(ROOT)["status"], "PASS")
        self.assertEqual(verify.self_test()["status"], "PASS")

    def test_non_git_differential(self):
        result = verify.differential(ROOT)
        self.assertEqual(result["status"], "PASS")
        self.assertEqual(result["worsened_identities"], [])

    def test_registry_is_machine_readable(self):
        data = json.loads((ROOT / "docs/quality/technical-debt-registry.json").read_text())
        self.assertTrue(data["entries"])
        self.assertEqual(len({entry["id"] for entry in data["entries"]}), len(data["entries"]))


if __name__ == "__main__":
    unittest.main()
