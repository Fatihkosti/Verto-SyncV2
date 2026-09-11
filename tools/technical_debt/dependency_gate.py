#!/usr/bin/env python3
"""Emit an explicit dependency judgment from the authoritative architecture scan."""
from __future__ import annotations
import argparse, json, subprocess, sys, tempfile
from pathlib import Path

def main() -> int:
    parser = argparse.ArgumentParser(); parser.add_argument("--root", type=Path, default=Path.cwd()); parser.add_argument("--output", type=Path)
    args = parser.parse_args(); root=args.root.resolve()
    with tempfile.TemporaryDirectory(prefix="verto-dependency-") as d:
        scan=Path(d)/"architecture.json"
        command=[sys.executable, str(root/"tools/architecture/verto_arch_guard.py"), "verify", "--root", str(root), "--output", str(scan)]
        completed=subprocess.run(command,cwd=root,text=True,capture_output=True,check=False)
        payload=json.loads(scan.read_text()) if scan.exists() else {"failures":[{"detail":completed.stdout+completed.stderr}]}
    details=payload.get("dependency_details", {})
    failures=details.get("failures", []) if isinstance(details,dict) else []
    result={"format":"verto-dependency-gate-v1","status":"PASS" if completed.returncode==0 and not failures else "FAIL","DEPENDENCY_GATE":"PASS" if completed.returncode==0 and not failures else "FAIL","scan":"tools/architecture/verto_arch_guard.py","failures":failures}
    print(json.dumps(result,indent=2,sort_keys=True))
    if args.output: args.output.parent.mkdir(parents=True,exist_ok=True); args.output.write_text(json.dumps(result,indent=2,sort_keys=True)+"\n")
    return 0 if result["status"]=="PASS" else 2
if __name__ == "__main__": raise SystemExit(main())
