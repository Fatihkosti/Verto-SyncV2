#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import datetime as dt
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
POLICY_PATH = ROOT / 'config/design-system/material-usage-policy.json'
ZERO_TARGETS_PATH = ROOT / 'config/design-system/final-zero-targets.json'
BASELINE_PATH = ROOT / 'docs/design-system/BASELINE.json'
HARDCODED_MANIFEST_PATH = ROOT / 'docs/design-system/CURRENT-HARDCODED-MANIFEST.json'
V226_PATH = ROOT / 'config/design-system/v226-enforcement-exceptions.json'
V291_PATH = ROOT / 'config/design-system/v291-exceptions.json'

LEGACY_FOCUSED_HARDCODED_PATTERNS = {
    'HARDCODED_TEXT_LITERAL': re.compile(r'\bText\s*\(\s*(?:text\s*=\s*)?"(?:\\.|[^"\\])*"'),
    'HARDCODED_CONTENT_DESCRIPTION': re.compile(r'\bcontentDescription\s*=\s*"(?:\\.|[^"\\])*"'),
    'HARDCODED_SEMANTIC_LABEL': re.compile(
        r'\b(?:label|placeholder|title|message|confirmLabel|supportingText|errorText|successText)\s*=\s*"(?:\\.|[^"\\])*"'
    ),
}
LEGACY_RAW_PATTERNS = {
    'RAW_MATERIAL_BUTTON': re.compile(r'(?<![A-Za-z0-9_])Button\s*\('),
    'RAW_MATERIAL_OUTLINED_BUTTON': re.compile(r'(?<![A-Za-z0-9_])OutlinedButton\s*\('),
    'RAW_MATERIAL_OUTLINED_TEXT_FIELD': re.compile(r'(?<![A-Za-z0-9_])OutlinedTextField\s*\('),
    'RAW_MATERIAL_TEXT_FIELD': re.compile(r'(?<![A-Za-z0-9_])TextField\s*\('),
}

FEATURE_TOKEN_FILES = {
    'app/src/main/kotlin/com/verto/app/ui/screens/home/HomeDesignTokens.kt',
    'feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/presentation/inventory/InventoryDesignTokens.kt',
    'feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/presentation/InvoiceDesignTokens.kt',
    'feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/PaymentDesignTokens.kt',
    'feature/expenses/src/main/kotlin/com/verto/app/ui/screens/expenses/ExpensesDesignTokens.kt',
    'feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/shared/PartyDesignTokens.kt',
    'feature/settings/src/main/kotlin/com/verto/app/feature/settings/presentation/SettingsDesignTokens.kt',
    'feature/settings/src/main/kotlin/com/verto/app/feature/settings/presentation/SettingsPrintTokens.kt',
    'feature/organization/src/main/kotlin/com/verto/app/feature/organization/presentation/OrganizationDesignTokens.kt',
    'app/src/main/kotlin/com/verto/app/ui/screens/usersdashboard/UserAdminDesignTokens.kt',
    'feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/ReportsDesignTokens.kt',
    'feature/auth/src/main/kotlin/com/verto/app/feature/auth/presentation/AuthDesignTokens.kt',
    'feature/integration/optimal/src/main/kotlin/com/verto/app/feature/integration/optimal/presentation/OptimalDesignTokens.kt',
    'feature/commission/src/main/kotlin/com/verto/app/ui/screens/commission/CommissionDesignTokens.kt',
    'feature/dashboard/src/main/kotlin/com/verto/app/feature/dashboard/presentation/DashboardDesignTokens.kt',
    'feature/messages/src/main/kotlin/com/verto/app/ui/screens/messages/MessagesDesignTokens.kt',
    'feature/profile/src/main/kotlin/com/verto/app/feature/profile/presentation/ProfileDesignTokens.kt',
    'feature/notifications/src/main/kotlin/com/verto/app/feature/notifications/presentation/NotificationsDesignTokens.kt',
    'feature/management/src/main/kotlin/com/verto/app/feature/management/presentation/ManagementDesignTokens.kt',
    'feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsV2DesignTokens.kt',
    'app/src/main/kotlin/com/verto/app/ui/components/AppChromeDesignTokens.kt',
    'app/src/main/kotlin/com/verto/app/ui/screens/auditlog/AuditLogDesignTokens.kt',
    'app/src/main/kotlin/com/verto/app/ui/screens/home/search/HomeSearchDesignTokens.kt',
    'app/src/main/kotlin/com/verto/app/ui/screens/onboarding/OnboardingDesignTokens.kt',
}
CORE_ALLOWED_LITERAL_FILES = {
    'core/designsystem/src/main/kotlin/com/verto/app/ui/theme/DesignTokens.kt',
    'core/designsystem/src/main/kotlin/com/verto/app/ui/theme/ComponentTokens.kt',
    'core/designsystem/src/main/kotlin/com/verto/app/ui/theme/Type.kt',
    'core/designsystem/src/main/kotlin/com/verto/app/ui/theme/Color.kt',
    'core/designsystem/src/main/kotlin/com/verto/app/ui/theme/FoundationTokens.kt',
    'core/designsystem/src/main/kotlin/com/verto/app/ui/components/AppIcons.kt',
}
RAW_COLOR_ALLOWED = {
    'core/designsystem/src/main/kotlin/com/verto/app/ui/theme/Color.kt',
    'feature/settings/src/main/kotlin/com/verto/app/feature/settings/presentation/SettingsPrintTokens.kt',
}
GUARDED_PRIMITIVES = {
    'VertoCard','VertoTopBar','VertoEmptyState','VertoLoadingState','VertoStatusBanner','VertoTextField',
    'VertoPrimaryButton','VertoSecondaryButton','SettingsCard','SettingsSectionHeader','SettingsDivider',
    'SettingsNavRow','DialogTextField',
}
USER_NAMED = {
    'label','placeholder','title','message','supportingText','errorText','successText','stateDescription',
    'confirmLabel','dismissLabel','actionLabel','retryLabel','contentDescription','text',
}


def stable_json(obj: object, indent: int | None = None) -> str:
    return json.dumps(obj, ensure_ascii=False, sort_keys=True, separators=(',', ':') if indent is None else None, indent=indent)


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def kotlin_files():
    for p in sorted(ROOT.rglob('*.kt')):
        rel = p.relative_to(ROOT).as_posix()
        if '/src/main/kotlin/' in rel and '/build/' not in rel:
            yield p, rel


def resolve_module_owner(path: Path) -> str:
    cur = path.parent
    while True:
        if (cur / 'build.gradle.kts').is_file():
            return cur.relative_to(ROOT).as_posix() if cur != ROOT else '.'
        if cur == ROOT or ROOT not in cur.parents:
            return 'other'
        cur = cur.parent


def is_old_presentation(rel: str) -> bool:
    return '/presentation/' in rel or ('/ui/' in rel and '/ui/theme/' not in rel)


def is_old_enforced(rel: str) -> bool:
    return not rel.startswith('core/designsystem/') and rel not in FEATURE_TOKEN_FILES and is_old_presentation(rel)


def strip_comments(source: str) -> str:
    out=[]; i=0; n=len(source); block=0; in_str=False; raw=False; in_char=False
    while i<n:
        if block:
            if source.startswith('/*',i): block+=1; out.extend('  '); i+=2
            elif source.startswith('*/',i): block-=1; out.extend('  '); i+=2
            else:
                out.append('\n' if source[i]=='\n' else ' '); i+=1
            continue
        if raw:
            if source.startswith('"""',i): out.extend('"""'); i+=3; raw=False
            else: out.append(source[i]); i+=1
            continue
        if in_str:
            ch=source[i]; out.append(ch); i+=1
            if ch=='\\' and i<n: out.append(source[i]); i+=1
            elif ch=='"': in_str=False
            continue
        if in_char:
            ch=source[i]; out.append(ch); i+=1
            if ch=='\\' and i<n: out.append(source[i]); i+=1
            elif ch=="'": in_char=False
            continue
        if source.startswith('//',i):
            while i<n and source[i]!='\n': out.append(' '); i+=1
            continue
        if source.startswith('/*',i): block=1; out.extend('  '); i+=2; continue
        if source.startswith('"""',i): out.extend('"""'); i+=3; raw=True; continue
        if source[i]=='"': out.append('"'); i+=1; in_str=True; continue
        if source[i]=="'": out.append("'"); i+=1; in_char=True; continue
        out.append(source[i]); i+=1
    return ''.join(out)


STRING_TOKEN = re.compile(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"')


def line_for(text: str, offset: int) -> int:
    return text.count('\n',0,offset)+1


def legacy_counts_for_text(rel: str, text: str) -> dict[str,int]:
    if not is_old_enforced(rel):
        return {}
    counts={}
    for rule,pat in LEGACY_RAW_PATTERNS.items():
        c=len(pat.findall(text))
        if c: counts[rule]=c
    for rule,pat in LEGACY_FOCUSED_HARDCODED_PATTERNS.items():
        c=len(pat.findall(text))
        if c: counts[rule]=c
    return counts


def collect_legacy_counts() -> tuple[dict[str,dict[str,int]],int]:
    result={}; hard=0
    for p,rel in kotlin_files():
        text=p.read_text(encoding='utf-8',errors='replace')
        c=legacy_counts_for_text(rel,text)
        if c:
            result[rel]=dict(sorted(c.items()))
            hard += sum(v for k,v in c.items() if k.startswith('HARDCODED_'))
    return dict(sorted(result.items())), hard


def decode_literal(token: str) -> str:
    if token.startswith('"""'):
        return token[3:-3]
    try:
        return bytes(token[1:-1], 'utf-8').decode('unicode_escape')
    except Exception:
        return token[1:-1]


def lex_kotlin(source: str) -> list[dict]:
    """Tokenize Kotlin comments/strings/chars; nested interpolation strings are retained as child STRING tokens."""
    tokens=[]; n=len(source)
    def line_at(pos:int)->int: return source.count('\n',0,pos)+1
    def scan_code(i:int, stop: str|None=None)->int:
        depth=0
        while i<n:
            if stop and source[i]==stop and depth==0: return i
            if source.startswith('//',i):
                st=i; j=source.find('\n',i+2); j=n if j<0 else j
                tokens.append({'kind':'LINE_COMMENT','start':st,'end':j,'text':source[st:j],'line':line_at(st)}); i=j; continue
            if source.startswith('/*',i):
                st=i; d=1; i+=2
                while i<n and d:
                    if source.startswith('/*',i): d+=1; i+=2
                    elif source.startswith('*/',i): d-=1; i+=2
                    else: i+=1
                tokens.append({'kind':'BLOCK_COMMENT','start':st,'end':i,'text':source[st:i],'line':line_at(st)}); continue
            if source.startswith('"""',i): i=scan_string(i,True); continue
            if source[i]=='"': i=scan_string(i,False); continue
            if source[i]=="'":
                st=i; i+=1
                while i<n:
                    if source[i]=='\\': i+=2; continue
                    if source[i]=="'": i+=1; break
                    i+=1
                tokens.append({'kind':'CHAR','start':st,'end':i,'text':source[st:i],'line':line_at(st)}); continue
            if stop:
                if source[i]=='{': depth+=1
                elif source[i]=='}':
                    if depth==0: return i
                    depth-=1
            i+=1
        return i
    def scan_string(i:int, raw:bool)->int:
        st=i; i+=3 if raw else 1; nested=[]
        while i<n:
            if raw and source.startswith('"""',i):
                i+=3; tokens.append({'kind':'RAW_STRING','start':st,'end':i,'text':source[st:i],'line':line_at(st)}); tokens.extend(nested); return i
            if not raw and source[i]=='\\': i+=2; continue
            if not raw and source[i]=='"':
                i+=1; tokens.append({'kind':'STRING','start':st,'end':i,'text':source[st:i],'line':line_at(st)}); tokens.extend(nested); return i
            if source.startswith('${',i):
                body=i+2; before=len(tokens); close=scan_code(body,'}')
                nested.extend(tokens[before:]); del tokens[before:]
                i=close+1 if close<n else close; continue
            i+=1
        tokens.append({'kind':'RAW_STRING' if raw else 'STRING','start':st,'end':i,'text':source[st:i],'line':line_at(st)}); tokens.extend(nested); return i
    scan_code(0)
    tokens.sort(key=lambda x:(x['start'],-(x['end']-x['start']),x['kind']))
    return tokens


def _kotlin_code_mask(source:str,tokens:list[dict])->str:
    chars=list(source)
    for t in tokens:
        if t['kind'] not in {'LINE_COMMENT','BLOCK_COMMENT','STRING','RAW_STRING','CHAR'}: continue
        for i in range(t['start'],t['end']):
            if chars[i]!='\n': chars[i]=' '
    return ''.join(chars)


def _delimiter_pairs(mask:str)->dict[int,int]:
    opens={'(':')','[':']','{':'}'}; rev={v:k for k,v in opens.items()}; stack=[]; pairs={}
    for i,ch in enumerate(mask):
        if ch in opens: stack.append((ch,i))
        elif ch in rev and stack and stack[-1][0]==rev[ch]:
            _,op=stack.pop(); pairs[op]=i
    return pairs


def find_balanced_region(source_or_mask:str, open_offset:int)->tuple[int,int]|None:
    """Return an inclusive balanced region for (), [] or {}; strings/comments may already be masked."""
    tokens=lex_kotlin(source_or_mask)
    mask=_kotlin_code_mask(source_or_mask,tokens)
    pairs=_delimiter_pairs(mask)
    close=pairs.get(open_offset)
    return (open_offset,close) if close is not None else None


def split_top_level_arguments(mask:str, open_offset:int, close_offset:int, pairs:dict[int,int]|None=None)->list[tuple[int,int]]:
    pairs=pairs or _delimiter_pairs(mask)
    spans=[]; start=open_offset+1; i=start
    while i<close_offset:
        if mask[i] in '([{':
            end=pairs.get(i)
            if end is not None and end<close_offset: i=end+1; continue
        if mask[i]==',': spans.append((start,i)); start=i+1
        i+=1
    spans.append((start,close_offset))
    return spans


def _string_tokens_in(tokens:list[dict],start:int,end:int)->list[dict]:
    out=[]
    for t in tokens:
        if t['kind'] not in {'STRING','RAW_STRING'} or t['start']<start or t['end']>end: continue
        raw=t['text'][3:-3] if t['kind']=='RAW_STRING' else t['text'][1:-1]
        if raw.strip(): out.append(t)
    return out


def _parse_named_argument(mask:str,start:int,end:int)->tuple[str|None,int]:
    m=re.match(r'\s*([A-Za-z_]\w*)\s*=\s*',mask[start:end])
    return (m.group(1),start+m.end()) if m else (None,start)


def _core_composable_signatures()->tuple[dict[str,list[str|None]],list[tuple[Path,dict]]]:
    signatures={}; defaults=[]
    root=ROOT/'core/designsystem'
    if not root.is_dir(): return signatures,defaults
    for p in sorted(root.rglob('*.kt')):
        rel=p.relative_to(ROOT).as_posix()
        if '/src/main/kotlin/' not in rel or '/build/' in rel: continue
        source=p.read_text(encoding='utf-8',errors='replace'); tokens=lex_kotlin(source); mask=_kotlin_code_mask(source,tokens); pairs=_delimiter_pairs(mask)
        pattern=re.compile(r'@Composable\b[\s\S]{0,500}?\b(?:(public|internal|private)\s+)?fun\s+([A-Za-z_]\w*)\s*\(')
        for m in pattern.finditer(mask):
            if m.group(1)=='private': continue
            name=m.group(2); op=mask.find('(',m.start(),m.end()); close=pairs.get(op)
            if close is None: continue
            params=[]
            for a,b in split_top_level_arguments(mask,op,close,pairs):
                segment=mask[a:b]
                pm=re.search(r'\b([A-Za-z_]\w*)\s*:\s*([^=,]+)',segment)
                if not pm: params.append(None); continue
                param=pm.group(1); typ=pm.group(2).strip(); params.append(param)
                eq=segment.find('=')
                if eq>=0 and re.search(r'\bString\s*\??',typ):
                    for token in _string_tokens_in(tokens,a+eq+1,b): defaults.append((p,token))
            signatures.setdefault(name,params)
    return signatures,defaults


def find_full_hardcoded() -> list[dict]:
    signatures,defaults=_core_composable_signatures(); found={}
    priority={'COMPOSABLE_DEFAULT':4,'CONTENT_DESCRIPTION':3,'TEXT':2,'UI_PARAMETER':1}
    def add(rel:str,source_path:Path,token:dict,context:str,rule:str):
        key=(rel,token['start'],token['end']); owner=resolve_module_owner(source_path)
        row={'file':rel,'line':token['line'],'context':context,'literal':token['text'],'owner':owner,'rule':rule}
        prev=found.get(key)
        if prev is None or priority[rule]>priority[prev['rule']]: found[key]=row
    for p,token in defaults:
        add(p.relative_to(ROOT).as_posix(),p,token,'COMPOSABLE_DEFAULT','COMPOSABLE_DEFAULT')
    call_re=re.compile(r'\b([A-Za-z_]\w*(?:\s*\.\s*[A-Za-z_]\w*)*)\s*\(')
    for p,rel in kotlin_files():
        source=p.read_text(encoding='utf-8',errors='replace'); tokens=lex_kotlin(source); mask=_kotlin_code_mask(source,tokens); pairs=_delimiter_pairs(mask)
        for m in call_re.finditer(mask):
            callee=re.sub(r'\s+','',m.group(1)); simple=callee.split('.')[-1]; op=mask.find('(',m.start(),m.end()); close=pairs.get(op)
            if close is None: continue
            if re.search(r'\bfun\s*$',mask[max(0,m.start()-24):m.start()]): continue
            if callee.startswith('Log.') or simple in {'print','println'}: continue
            positional=0
            for a,b in split_top_level_arguments(mask,op,close,pairs):
                name,expr=_parse_named_argument(mask,a,b); relevant=None; context=None; rule=None
                if name:
                    if name in USER_NAMED and not (name=='label' and (simple.startswith('animate') or simple in {'updateTransition','rememberInfiniteTransition'})):
                        relevant=(expr,b); context=name
                        rule='CONTENT_DESCRIPTION' if name=='contentDescription' else ('TEXT' if name=='text' else 'UI_PARAMETER')
                else:
                    if simple=='Text' and positional==0:
                        relevant=(a,b); context='Text'; rule='TEXT'
                    elif simple=='Icon' and positional==1:
                        relevant=(a,b); context='Icon'; rule='CONTENT_DESCRIPTION'
                    elif simple in signatures and positional<len(signatures[simple]) and signatures[simple][positional] in USER_NAMED:
                        param=signatures[simple][positional]; relevant=(a,b); context=f'{simple}:{param}'
                        rule='CONTENT_DESCRIPTION' if param=='contentDescription' else ('TEXT' if param=='text' else 'UI_PARAMETER')
                    positional+=1
                if relevant:
                    for token in _string_tokens_in(tokens,*relevant): add(rel,p,token,context,rule)
    findings=list(found.values()); findings.sort(key=lambda x:(x['file'],x['line'],x['context'],x['literal'],x['rule']))
    return findings

def load_policy() -> dict:
    p=json.loads(POLICY_PATH.read_text(encoding='utf-8'))
    req={'component','classification','scope','owner','reason','replacement','migrationStatus','enforceFromSession'}
    if p.get('schemaVersion')!=290: raise ValueError('material policy schemaVersion must be 290')
    comps=p.get('components',[])
    if len(comps)!=25 or len({x.get('component') for x in comps})!=25: raise ValueError('material policy requires 25 unique components')
    for x in comps:
        if set(x)!=req: raise ValueError(f"material policy fields invalid for {x.get('component')}")
        if x['classification'] not in {'DIRECT_USE_ALLOWED','MUST_WRAP','FORBIDDEN'}: raise ValueError('bad classification')
        if not isinstance(x['enforceFromSession'],int): raise ValueError('bad enforceFromSession')
    return p


def material_scan(policy: dict, session: int) -> tuple[dict[str,int],list[dict],int]:
    must={x['component']:x for x in policy['components'] if x['classification']=='MUST_WRAP'}
    by=Counter(); active=[]; forbidden=0
    for p,rel in kotlin_files():
        text=strip_comments(p.read_text(encoding='utf-8',errors='replace'))
        if re.search(r'^\s*import\s+androidx\.compose\.material\.(?!icons\.)',text,re.M):
            forbidden += len(re.findall(r'^\s*import\s+androidx\.compose\.material\.(?!icons\.)',text,re.M))
        if rel.startswith('core/designsystem/'):
            continue
        for comp,entry in must.items():
            count=len(re.findall(rf'(?<![A-Za-z0-9_]){re.escape(comp)}\s*\(',text))
            if count:
                by[comp]+=count
                if session>=entry['enforceFromSession']:
                    active.append({'file':rel,'component':comp,'count':count,'enforceFromSession':entry['enforceFromSession']})
    return dict(sorted(by.items())),sorted(active,key=lambda x:(x['file'],x['component'])),forbidden


def metric_raw_literals() -> tuple[int,int,int,int]:
    dp=sp=color=motion=0
    approved_dp=FEATURE_TOKEN_FILES|CORE_ALLOWED_LITERAL_FILES
    for p,rel in kotlin_files():
        text=strip_comments(p.read_text(encoding='utf-8',errors='replace'))
        if rel not in approved_dp:
            dp += len(re.findall(r'(?<![A-Za-z0-9_])\d+(?:\.\d+)?\.dp\b',text))
            sp += len(re.findall(r'(?<![A-Za-z0-9_])\d+(?:\.\d+)?\.sp\b',text))
        if rel not in RAW_COLOR_ALLOWED:
            color += len(re.findall(r'Color\(\s*0x[0-9A-Fa-f]+',text))
        if '/ui/theme/' not in rel and 'Motion' not in Path(rel).name:
            motion += len(re.findall(r'\btween\s*\(\s*(?:durationMillis\s*=\s*)?\d+',text))
            # Explicit durationMillis outside tween calls in animation definitions.
            for m in re.finditer(r'\bdurationMillis\s*=\s*\d+',text):
                prefix=text[max(0,m.start()-80):m.start()]
                if 'tween' not in prefix:
                    motion += 1
    return dp,sp,color,motion


def core_boundary_count() -> int:
    c=0
    root=ROOT/'core/designsystem/src/main/kotlin'
    if not root.is_dir(): return 0
    bad=('com.verto.app.feature.','com.verto.app.data.','com.verto.app.domain.','com.verto.app.ui.screens.','com.verto.app.navigation.')
    for p in root.rglob('*.kt'):
        for line in p.read_text(encoding='utf-8',errors='replace').splitlines():
            if line.startswith('import ') and any(x in line for x in bad): c+=1
    return c


def ds_strings_metrics() -> tuple[int,int]:
    strings=ROOT/'core/designsystem/src/main/res/values/strings.xml'
    tree=ET.parse(strings)
    names=[x.attrib.get('name','') for x in tree.getroot().findall('string')]
    defs=sum(1 for n in names if n.startswith('ds_'))
    refs=0
    pat=re.compile(r'com\.verto\.core\.designsystem\.R\.string\.ds_[A-Za-z0-9_]+')
    for p,rel in kotlin_files():
        if rel.startswith('core/designsystem/'): continue
        refs += len(pat.findall(p.read_text(encoding='utf-8',errors='replace')))
    return defs,refs


def duplicate_guarded_count() -> int:
    decl=defaultdict(list)
    pat=re.compile(r'(?m)^\s*(?:internal\s+|private\s+|public\s+)?fun\s+([A-Za-z0-9_]+)\s*\(')
    for p,rel in kotlin_files():
        for name in pat.findall(strip_comments(p.read_text(encoding='utf-8',errors='replace'))):
            if name in GUARDED_PRIMITIVES: decl[name].append(rel)
    bad=0
    for name,paths in decl.items():
        core=[x for x in paths if x.startswith('core/designsystem/')]; non=[x for x in paths if not x.startswith('core/designsystem/')]
        if non or len(core)!=1: bad+=1
    return bad


def load_core_signatures(seed: str|None) -> list[dict]:
    if seed:
        rows=list(csv.DictReader(open(seed,encoding='utf-8-sig')))
        sig=[]
        for r in rows:
            file=r.get('file') or r.get('path') or ''
            lit=r.get('literal') or r.get('literalValue') or r.get('text') or ''
            if not file or not lit: continue
            # CSV literal may include Kotlin quotes; store value without one outer pair.
            if lit.startswith('"') and lit.endswith('"'): lit=lit[1:-1]
            sig.append({'file':file,'literalValue':lit})
        if len(sig)!=10: raise ValueError(f'core visible seed requires 10 rows, got {len(sig)}')
        return sorted(sig,key=lambda x:(x['file'],x['literalValue']))
    if HARDCODED_MANIFEST_PATH.is_file():
        p=json.loads(HARDCODED_MANIFEST_PATH.read_text(encoding='utf-8'))
        sig=p.get('legacyCoreVisibleSignatures',[])
        if len(sig)==10: return sig
    raise ValueError('core visible signatures unavailable')


def core_visible_debt(signatures:list[dict], findings:list[dict]) -> int:
    # Compatibility signatures are tracked by exact string-token presence in the original file.
    # Comments are stripped first, so moving text to comments cannot preserve debt.
    count=0
    for s in signatures:
        p=ROOT/s['file']
        if not p.is_file():
            continue
        clean=strip_comments(p.read_text(encoding='utf-8',errors='replace'))
        val=s['literalValue']
        if any(val in m.group(0) for m in STRING_TOKEN.finditer(clean)):
            count+=1
    return count


def active_ledger_validation(legacy_counts: dict[str,dict[str,int]]) -> tuple[int,int,list[str],list[dict]]:
    if not V291_PATH.is_file():
        return 0,0,[],[]
    failures=[]
    try: led=json.loads(V291_PATH.read_text(encoding='utf-8'))
    except Exception as e: return 0,0,[f'active ledger invalid JSON: {e}'],[]
    top_req={'schemaVersion','active','createdAt','legacySource','expiresAt','entries'}
    if set(led)!=top_req: failures.append('active ledger top-level fields invalid')
    if led.get('schemaVersion')!=291 or led.get('active') is not True: failures.append('active ledger schema/active invalid')
    if led.get('legacySource')!='v226' or led.get('expiresAt')!='2026-09-30': failures.append('active ledger provenance/expiry invalid')
    entries=led.get('entries',[])
    req={'file','rule','owner','reason','removalCondition','createdAt','expiresAt','allowedCount','currentCount','sourceSha256','legacySource','permanent'}
    keys=set(); permanent=expired=0; today=dt.date.today().isoformat()
    old_allowances={}
    if V226_PATH.is_file():
        v226=json.loads(V226_PATH.read_text(encoding='utf-8'))
        for row in v226.get('files',[]):
            for e in row.get('exceptions',[]): old_allowances[(row['file'],e['rule'])]=int(e['allowed_count'])
    for e in entries:
        if set(e)!=req: failures.append(f"active ledger fields invalid: {e.get('file')} {e.get('rule')}"); continue
        key=(e['file'],e['rule'])
        if key in keys: failures.append(f'duplicate active ledger key: {key}')
        keys.add(key)
        if key not in old_allowances: failures.append(f'active key absent from v226: {key}')
        elif e['allowedCount']>old_allowances[key]: failures.append(f'active allowance exceeds v226: {key} {e["allowedCount"]}>{old_allowances[key]}')
        if e['legacySource']!='v226': failures.append(f'legacySource invalid: {key}')
        if e['permanent'] is not False: permanent+=1; failures.append(f'permanent exception: {key}')
        if e['expiresAt']<=today: expired+=1; failures.append(f'expired exception: {key}')
        p=ROOT/e['file']
        if not p.is_file(): failures.append(f'active source missing: {key}'); continue
        current=legacy_counts.get(e['file'],{}).get(e['rule'],0)
        if current>e['allowedCount']: failures.append(f'active allowance exceeded: {key} {current}>{e["allowedCount"]}')
        if resolve_module_owner(p)!=e['owner']: failures.append(f'active owner mismatch: {key}')
        current_sha=sha256_file(p)
        if current_sha!=e['sourceSha256'] and current>=e['allowedCount']:
            failures.append(f'active source hash changed without debt reduction: {key}')
    return expired,permanent,failures,entries


def migrate_v226(created_at: str) -> tuple[dict,list[str]]:
    v226=json.loads(V226_PATH.read_text(encoding='utf-8'))
    entries=[]; resolved=[]; stale=[]; blocked=[]; unevaluable=[]
    for row in v226.get('files',[]):
        rel=row['file']; p=ROOT/rel
        for exc in row.get('exceptions',[]):
            rule=exc['rule']; allowed=int(exc['allowed_count'])
            if rule not in LEGACY_RAW_PATTERNS and rule not in LEGACY_FOCUSED_HARDCODED_PATTERNS:
                unevaluable.append({'file':rel,'rule':rule}); continue
            if not p.is_file(): stale.append({'file':rel,'rule':rule}); continue
            text=p.read_text(encoding='utf-8',errors='replace')
            current=legacy_counts_for_text(rel,text).get(rule,0)
            if current==0:
                resolved.append({'file':rel,'rule':rule,'legacyAllowed':allowed}); continue
            if current>allowed:
                blocked.append({'file':rel,'rule':rule,'current':current,'legacyAllowed':allowed}); continue
            entries.append({
                'file':rel,'rule':rule,'owner':resolve_module_owner(p),'reason':exc['reason'],
                'removalCondition':exc['removal_condition'],'createdAt':created_at,'expiresAt':'2026-09-30',
                'allowedCount':current,'currentCount':current,'sourceSha256':sha256_file(p),
                'legacySource':'v226','permanent':False,
            })
    entries.sort(key=lambda x:(x['file'],x['rule']))
    evidence={'schemaVersion':291,'createdAt':created_at,'activeEntryCount':len(entries),'resolvedCount':len(resolved),'staleCount':len(stale),'blockedCount':len(blocked),'unevaluableCount':len(unevaluable),'resolved':resolved,'stale':stale,'blocked':blocked,'unevaluable':unevaluable}
    failures=[]
    if unevaluable: failures.append(f'unevaluable legacy rules: {len(unevaluable)}')
    if blocked: failures.append(f'over-allowance legacy blockers: {len(blocked)}')
    if not failures:
        ledger={'schemaVersion':291,'active':True,'createdAt':created_at,'legacySource':'v226','expiresAt':'2026-09-30','entries':entries}
        V291_PATH.write_text(json.dumps(ledger,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    return evidence,failures


def build_snapshot(session:int, gate:str, signatures:list[dict]) -> tuple[dict,list[dict],dict[str,dict[str,int]],list[dict]]:
    policy=load_policy()
    legacy_counts,legacy_hard=collect_legacy_counts()
    findings=find_full_hardcoded()
    material_by,active_material,forbidden=material_scan(policy,session)
    dp,sp,color,motion=metric_raw_literals()
    corebound=core_boundary_count(); dsdefs,dsrefs=ds_strings_metrics(); dup=duplicate_guarded_count()
    expired,permanent,ledger_failures,entries=active_ledger_validation(legacy_counts)
    metrics={
        'legacyFocusedHardcodedCount':legacy_hard,
        'hardcodedUserFacingStringsFull':len(findings),
        'coreVisibleLiteralDebt':core_visible_debt(signatures,findings),
        'rawMaterialMustWrap':sum(material_by.values()),
        'forbiddenMaterial':forbidden,
        'rawDpOutsideApprovedTokenFiles':dp,
        'rawSpOutsideApprovedTokenFiles':sp,
        'rawColorOutsideApprovedTokenFiles':color,
        'rawMotionDurations':motion,
        'coreBoundaryViolations':corebound,
        'designSystemDomainStringCount':dsdefs,
        'externalDesignSystemDomainStringReferences':dsrefs,
        'expiredExceptions':expired,
        'permanentExceptions':permanent,
        'duplicateGuardedPrimitives':dup,
    }
    owner_counts=Counter(f['owner'] for f in findings)
    core_for_id={'session':session,'metrics':metrics,'debtByFileRule':legacy_counts,'hardcodedFindings':findings}
    sid=hashlib.sha256(stable_json(core_for_id).encode()).hexdigest()
    breakdowns={
        'debtByFileRule':legacy_counts,
        'materialByComponent':material_by,
        'materialActiveViolations':active_material,
        'ownerModuleCounts':dict(sorted(owner_counts.items())),
        'legacyExceptionRuleCount':sum(len(x.get('exceptions',[])) for x in json.loads(V226_PATH.read_text()).get('files',[])) if V226_PATH.is_file() else 0,
        'activeExceptionEntryCount':len(entries),
    }
    snap={'schemaVersion':290,'session':session,'gate':gate,'result':'PENDING','snapshotId':sid,'metrics':metrics,'breakdowns':breakdowns,'artifacts':{'hardcodedManifest':'docs/design-system/CURRENT-HARDCODED-MANIFEST.json','baseline':'docs/design-system/BASELINE.json','activeExceptionLedger':'config/design-system/v291-exceptions.json' if V291_PATH.is_file() else None},'failures':[]}
    return snap,findings,legacy_counts,ledger_failures


def write_manifest(snapshot:dict,findings:list[dict],signatures:list[dict],path:Path):
    payload={'schemaVersion':290,'source':'Verto-v289','snapshotId':snapshot['snapshotId'],'legacyCoreVisibleSignatures':signatures,'findingCount':len(findings),'findings':findings}
    path.write_text(json.dumps(payload,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')


def write_state(snapshot:dict,path:Path):
    lines=['# Current Design State','',f"Snapshot ID: `{snapshot['snapshotId']}`",'',f"Session: `{snapshot['session']}`  Gate: `{snapshot['gate']}`",'', '| Metric | Value |','|---|---:|']
    for k,v in snapshot['metrics'].items(): lines.append(f'| {k} | {v} |')
    lines += ['', 'Generated from `scripts/design-system-scan.py`; Baseline is migration/no-growth evidence, not final allowance.','']
    path.write_text('\n'.join(lines),encoding='utf-8')


def write_baseline(snapshot:dict):
    payload={'schemaVersion':290,'source':'Verto-v289','policy':'migration-no-growth-only','snapshotId':snapshot['snapshotId'],'metrics':snapshot['metrics'],'debtByFileRule':snapshot['breakdowns']['debtByFileRule'],'materialByComponent':snapshot['breakdowns']['materialByComponent']}
    BASELINE_PATH.write_text(json.dumps(payload,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')


def evaluate_gate(snapshot:dict,ledger_failures:list[str],gate:str,session:int) -> list[str]:
    failures=list(ledger_failures)
    if snapshot['metrics']['forbiddenMaterial']:
        failures.append(f"forbidden Material2 imports={snapshot['metrics']['forbiddenMaterial']}")
    if gate=='migration':
        if not BASELINE_PATH.is_file(): return failures+['migration baseline missing']
        b=json.loads(BASELINE_PATH.read_text(encoding='utf-8'))
        oldm=b.get('metrics',{})
        for k,v in snapshot['metrics'].items():
            if k in {'expiredExceptions','permanentExceptions'}: continue
            if k in oldm and v>oldm[k]: failures.append(f'metric grew: {k} {oldm[k]}->{v}')
        oldc=b.get('debtByFileRule',{})
        for file,rules in snapshot['breakdowns']['debtByFileRule'].items():
            for rule,v in rules.items():
                old=oldc.get(file,{}).get(rule,0)
                if v>old: failures.append(f'debt grew: {file} {rule} {old}->{v}')
        for x in snapshot['breakdowns']['materialActiveViolations']:
            # v226 legacy-exception-era clean rules were already zero; pending future components activate later.
            failures.append(f"active MUST_WRAP direct use: {x['file']} {x['component']} count={x['count']}")
    elif gate=='final':
        z=json.loads(ZERO_TARGETS_PATH.read_text(encoding='utf-8'))['targets']
        for k,target in z.items():
            actual=snapshot['metrics'].get(k)
            if actual!=target: failures.append(f'final target: {k} expected {target} actual {actual}')
        if V291_PATH.is_file():
            entries=json.loads(V291_PATH.read_text(encoding='utf-8')).get('entries',[])
            if entries: failures.append(f'final target: active temporary exceptions remain={len(entries)}')
    else:
        failures.append(f'unknown gate {gate}')
    return failures


def main() -> int:
    ap=argparse.ArgumentParser()
    ap.add_argument('--gate',choices=['migration','final'],default='migration')
    ap.add_argument('--session',type=int,default=290)
    ap.add_argument('--json',action='store_true')
    ap.add_argument('--check',action='store_true')
    ap.add_argument('--write-baseline',action='store_true')
    ap.add_argument('--write-hardcoded-manifest')
    ap.add_argument('--write-current-state')
    ap.add_argument('--seed-core-visible-manifest')
    ap.add_argument('--write-v291-exceptions',action='store_true')
    ap.add_argument('--created-at')
    args=ap.parse_args()
    try:
        signatures=load_core_signatures(args.seed_core_visible_manifest)
        # Migration command evaluates current source before snapshot and writes only when no blockers.
        migration_evidence=None; migration_failures=[]
        if args.write_v291_exceptions:
            created=args.created_at or dt.date.today().isoformat()
            migration_evidence,migration_failures=migrate_v226(created)
            if migration_failures:
                payload={'schemaVersion':291,'command':'write-v291-exceptions','result':'FAIL','migration':migration_evidence,'failures':migration_failures}
                print(json.dumps(payload,ensure_ascii=False,indent=2) if args.json else '\n'.join('FAIL '+x for x in migration_failures))
                return 1
        snap,findings,legacy_counts,ledger_failures=build_snapshot(args.session,args.gate,signatures)
        if args.write_hardcoded_manifest:
            write_manifest(snap,findings,signatures,ROOT/args.write_hardcoded_manifest)
        if args.write_current_state:
            write_state(snap,ROOT/args.write_current_state)
        if args.write_baseline:
            if not (args.gate=='migration' and args.session==290):
                print('write-baseline only allowed for migration/session290',file=sys.stderr); return 64
            write_baseline(snap)
        failures=evaluate_gate(snap,ledger_failures,args.gate,args.session)
        snap['failures']=failures
        snap['result']='PASS' if not failures else 'FAIL'
        if migration_evidence is not None:
            snap['migration']=migration_evidence
        if args.json:
            print(json.dumps(snap,ensure_ascii=False,indent=2)+'')
        else:
            print(f"DESIGN_SYSTEM_SCAN_RESULT {snap['result']} {len(failures)}")
            for f in failures: print('FAIL',f)
        return 0 if not failures else 1
    except Exception as exc:
        payload={'schemaVersion':290,'session':args.session,'gate':args.gate,'result':'CONFIG_ERROR','failures':[str(exc)]}
        if args.json: print(json.dumps(payload,ensure_ascii=False,indent=2))
        else: print('DESIGN_SYSTEM_SCAN_CONFIG_ERROR',exc,file=sys.stderr)
        return 2

if __name__=='__main__':
    raise SystemExit(main())
