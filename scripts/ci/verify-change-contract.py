#!/usr/bin/env python3
"""Fail-closed scope verifier for a Verto session Change Contract."""
from __future__ import annotations
import argparse, fnmatch, hashlib, json
from pathlib import Path
from typing import Any

EXCLUDED_DIRS={'.git','.gradle','.idea','.kotlin','build','out','__pycache__'}
EXCLUDED_SUFFIXES={'.zip','.apk','.aab','.class','.pyc'}
REQUIRED_TOP={'session','architecture','verification'}
REQUIRED_SESSION={'id','input_sha256','baseline_snapshot','scope','allowed_modules','allowed_files','forbidden_files'}
REQUIRED_ARCH={'invariants','allowed_new_dependencies','forbidden_dependencies'}
REQUIRED_VER={'required_tests','required_gates'}

def load(p:Path)->Any: return json.loads(p.read_text(encoding='utf-8'))
def rel(p:Path,r:Path)->str: return p.resolve().relative_to(r.resolve()).as_posix()
def excluded(p:Path,r:Path)->bool:
    rp=p.resolve().relative_to(r.resolve())
    return any(x in EXCLUDED_DIRS for x in rp.parts) or p.suffix.lower() in EXCLUDED_SUFFIXES

def snapshot(root:Path)->dict[str,Any]:
    rows=[]
    for p in sorted(root.rglob('*')):
        if not p.is_file() or excluded(p,root): continue
        b=p.read_bytes(); rows.append({'path':rel(p,root),'sha256':hashlib.sha256(b).hexdigest(),'size':len(b)})
    return {'format':'verto-architecture-snapshot-v1','root':'.','files':rows}

def match(path:str,patterns:list[str])->bool:
    for pattern in patterns:
        if pattern.endswith('/**'):
            prefix=pattern[:-3].rstrip('/')
            if path==prefix or path.startswith(prefix+'/'): return True
        if fnmatch.fnmatchcase(path,pattern): return True
    return False

def schema_failures(c:dict[str,Any])->list[dict[str,Any]]:
    f=[]
    for k in REQUIRED_TOP:
        if k not in c: f.append({'code':'FAIL_CHANGE_CONTRACT_MISSING','field':k})
    s=c.get('session',{})
    for k in REQUIRED_SESSION:
        if k not in s: f.append({'code':'FAIL_CHANGE_CONTRACT_MISSING','field':f'session.{k}'})
    a=c.get('architecture',{})
    for k in REQUIRED_ARCH:
        if k not in a: f.append({'code':'FAIL_CHANGE_CONTRACT_MISSING','field':f'architecture.{k}'})
    v=c.get('verification',{})
    for k in REQUIRED_VER:
        if k not in v: f.append({'code':'FAIL_CHANGE_CONTRACT_MISSING','field':f'verification.{k}'})
    if not isinstance(s.get('allowed_files'),list) or not s.get('allowed_files'): f.append({'code':'FAIL_CHANGE_CONTRACT_MISSING','field':'session.allowed_files'})
    return f

def verify(root:Path,contract_path:Path)->dict[str,Any]:
    try: c=load(contract_path)
    except Exception as e: return {'status':'FAIL','failures':[{'code':'FAIL_CHANGE_CONTRACT_MISSING','error':f'{type(e).__name__}: {e}'}]}
    failures=schema_failures(c)
    s=c.get('session',{}); baseline_ref=s.get('baseline_snapshot',''); bp=Path(baseline_ref)
    if not bp.is_absolute(): bp=root/bp
    if not bp.exists():
        failures.append({'code':'FAIL_CHANGE_CONTRACT_MISSING','field':'session.baseline_snapshot','path':baseline_ref})
        return {'status':'FAIL','failures':failures}
    before=load(bp); after=snapshot(root)
    b={x['path']:x for x in before.get('files',[])}; a={x['path']:x for x in after['files']}
    added=sorted(set(a)-set(b)); deleted=sorted(set(b)-set(a)); modified=sorted(p for p in set(a)&set(b) if a[p]['sha256']!=b[p]['sha256'] or a[p]['size']!=b[p]['size'])
    changed=added+modified+deleted; allow=s.get('allowed_files',[]); forbidden=s.get('forbidden_files',[])
    outside=sorted(p for p in changed if not match(p,allow)); forbidden_changed=sorted(p for p in changed if match(p,forbidden))
    if outside or forbidden_changed:
        failures.append({'code':'FAIL_CHANGE_CONTRACT_SCOPE','rule_id':'VARCH-014','outside_allowlist':outside,'forbidden_changed':forbidden_changed})
    return {
        'format':'verto-change-contract-verification-v1','status':'PASS' if not failures else 'FAIL','session_id':s.get('id'),
        'input_sha256':s.get('input_sha256'),'baseline_snapshot':baseline_ref,
        'changes':{'added':added,'modified':modified,'deleted':deleted,'outside_allowlist':outside,'forbidden_changed':forbidden_changed},
        'failures':failures,
    }

def main()->int:
    ap=argparse.ArgumentParser(); ap.add_argument('--root',default='.'); ap.add_argument('--contract',default='docs/architecture/contracts/sessions/session-320.json'); ap.add_argument('--output'); args=ap.parse_args()
    root=Path(args.root).resolve(); cp=Path(args.contract); cp=cp if cp.is_absolute() else root/cp
    r=verify(root,cp)
    if args.output:
        out=Path(args.output); out.parent.mkdir(parents=True,exist_ok=True); out.write_text(json.dumps(r,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    print(json.dumps({'CHANGE_CONTRACT_GATE':r['status'],'failures':r.get('failures',[])},sort_keys=True)); return 0 if r['status']=='PASS' else 2
if __name__=='__main__': raise SystemExit(main())
