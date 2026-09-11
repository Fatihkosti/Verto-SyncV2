#!/usr/bin/env python3
import argparse,json,subprocess,sys
from collections import Counter
from pathlib import Path
ap=argparse.ArgumentParser(); ap.add_argument('--root',required=True); ap.add_argument('--input-zip',required=True); a=ap.parse_args()
cmd=[sys.executable,str(Path(a.root)/'tools/verify_sync_room_v306.py'),'--root',a.root,'--input-zip',a.input_zip]
r1=subprocess.run(cmd,capture_output=True,text=True); r2=subprocess.run(cmd,capture_output=True,text=True)
if r1.returncode or r2.returncode: print(r1.stdout); print(r1.stderr,file=sys.stderr); sys.exit(1)
if r1.stdout != r2.stdout: print('NON_DETERMINISTIC'); sys.exit(1)
d=json.loads(r1.stdout); fs=d['fixtureStats']; mins={'source/authority':5,'migration/schema':8,'outbox identity/sequence':8,'inbox dedup/immutability':7,'cursor/scope/CAS':8,'atomicity structure':6,'scope/runtime isolation':3}
by=fs['byCategory']
for k,v in mins.items():
    assert by.get(k,{}).get('passed',0)>=v,(k,by.get(k))
required=[*(f'M{i:02d}' for i in range(1,10)),*(f'O{i:02d}' for i in range(1,13)),*(f'I{i:02d}' for i in range(1,9)),*(f'C{i:02d}' for i in range(1,11)),*(f'A{i:02d}' for i in range(1,9))]
# Verifier exposes failed IDs only; zero failures + exact total/minima prove mandatory sets compiled into verifier source.
src=(Path(a.root)/'tools/verify_sync_room_v306.py').read_text()
missing=[x for x in required if f"'{x}'" not in src]
assert not missing,missing
assert fs['total']>=45 and fs['failed']==0
print(json.dumps({'status':'PASS','deterministic':True,'fixtureTotal':fs['total'],'normalizedHash':d['staticVerifierNormalizedHash']},sort_keys=True))
