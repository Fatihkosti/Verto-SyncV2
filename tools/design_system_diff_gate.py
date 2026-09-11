#!/usr/bin/env python3
"""Compatibility wrapper for the authoritative Design System scanner."""
from __future__ import annotations
import argparse, subprocess, sys
from pathlib import Path

def main() -> int:
    ap=argparse.ArgumentParser()
    ap.add_argument('--root',default='.')
    ap.add_argument('--gate',choices=['migration','final'],default='migration')
    ap.add_argument('--session',type=int,default=291)
    ap.add_argument('--baseline')
    args=ap.parse_args()
    root=Path(args.root).resolve()
    canonical=(root/'docs/design-system/BASELINE.json').resolve()
    if args.baseline and Path(args.baseline).resolve()!=canonical:
        print('DESIGN_SYSTEM_DIFF CONFIG_ERROR: --baseline is compatibility-only and must be docs/design-system/BASELINE.json',file=sys.stderr)
        return 64
    cmd=[sys.executable,str(root/'scripts/design-system-scan.py'),'--gate',args.gate,'--session',str(args.session),'--json']
    cp=subprocess.run(cmd,cwd=root,text=True,capture_output=True,check=False)
    sys.stdout.write(cp.stdout)
    sys.stderr.write(cp.stderr)
    return cp.returncode
if __name__=='__main__': raise SystemExit(main())
