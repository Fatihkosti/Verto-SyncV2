#!/usr/bin/env python3
from __future__ import annotations
import hashlib, re, sys, zipfile
from pathlib import Path

EXPECTED_BASELINE_SHA = "73b0da3842ebccec3b96e049bddfa3962d43224b45a502adcf696c256fd0add0"
ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
BASELINE_ZIP = Path(sys.argv[2]).resolve() if len(sys.argv) > 2 else None
checks: list[tuple[str, bool, str]] = []

def check(name: str, condition: bool, detail: str = "") -> None:
    checks.append((name, bool(condition), detail))

def text(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")

def sha(path: Path) -> str:
    h=hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda:f.read(1024*1024), b""): h.update(chunk)
    return h.hexdigest()

# Baseline identity
if BASELINE_ZIP and BASELINE_ZIP.exists():
    check("baseline SHA-256", sha(BASELINE_ZIP) == EXPECTED_BASELINE_SHA, sha(BASELINE_ZIP))
else:
    check("baseline SHA-256", False, "baseline zip missing")

header_policy = text("app/src/main/kotlin/com/verto/app/ui/screens/home/HomeHeaderPolicy.kt")
header_ui = text("app/src/main/kotlin/com/verto/app/ui/screens/home/HomeHeader.kt")
usecase = text("feature/dashboard/src/main/kotlin/com/verto/app/feature/dashboard/application/search/UnifiedHomeSearchUseCase.kt")
vm = text("app/src/main/kotlin/com/verto/app/ui/screens/home/search/HomeSearchViewModel.kt")
click = text("app/src/main/kotlin/com/verto/app/ui/screens/home/search/HomeSearchClickPolicy.kt")
contracts = text("feature/dashboard/api/src/main/kotlin/com/verto/feature/dashboard/api/HomeContracts.kt")
sections = text("app/src/main/kotlin/com/verto/app/ui/screens/home/search/HomeSearchSections.kt")

# Header structure/content
catalogs = {}
for name in ("morning","day","evening"):
    m = re.search(rf"val {name}: List<String> = listOf\((.*?)\n\s*\)", header_policy, re.S)
    vals = re.findall(r'"([^"\\]*(?:\\.[^"\\]*)*)"', m.group(1)) if m else []
    catalogs[name]=vals
    check(f"header {name} count=30", len(vals)==30, str(len(vals)))
    check(f"header {name} unique=30", len(set(vals))==30, str(len(set(vals))))
    check(f"header {name} <=55 chars", bool(vals) and max(map(len,vals))<=55, str(max(map(len,vals)) if vals else 0))
check("header cross-period exact phrases unique", len(set(sum(catalogs.values(), [])))==90)
check("header deterministic floorMod epoch day", "Math.floorMod(date.toEpochDay()" in header_policy and "30" in header_policy or "HomeHeaderMessages.morning.size" in header_policy)
check("header correct 04/12/16 period boundaries", all(token in header_policy for token in ["LocalTime.of(4, 0)", "LocalTime.NOON", "LocalTime.of(16, 0)"]))
check("header next boundary includes midnight", "LocalTime.MIDNIGHT" in header_policy and "nextHomeHeaderBoundary" in header_policy)
check("header lifecycle resume recompute", "repeatOnLifecycle(Lifecycle.State.STARTED)" in header_ui and "value = current" in header_ui)
check("header boundary-only scheduling", "nextHomeHeaderBoundary(current)" in header_ui and "delay(delayMillis)" in header_ui)
check("header no Random", "Random" not in header_policy + header_ui)
check("header no DB/network", not re.search(r"Room|Dao|Supabase|http|Retrofit|network", header_policy + header_ui, re.I))
check("header no minute polling", "60_000" not in header_ui and "delay(60000" not in header_ui.replace(" ",""))

# Search contract
check("search debounce=180ms", "DEBOUNCE_MILLIS: Long = 180L" in usecase)
check("search max total=24", "MAX_TOTAL_RESULTS: Int = 24" in usecase)
check("search providers parallel", "async { provider.searchSafely" in usecase and "awaitAll().flatten()" in usecase)
check("search cancellation propagates", usecase.count("CancellationException") >= 2 and vm.count("CancellationException") >= 2)
check("search provider failures isolated", "catch (error: Exception)" in usecase and "emptyList()" in usecase and "recordFailure" in usecase)
check("search diagnostics are PII-free shape", all(x in usecase for x in ["providerId", "queryLength", "resultCount", "lastDurationBucket", "lastFailureType"]) and "raw query" in usecase)
check("search result identity includes provider", '"${providerId}:${kind}:${key}"' in click and "val providerId: String" in contracts)
check("search click reruns canonical current query", "permissionContext.first()" in vm and "val canonicalResults = search(" in vm and "resolveCanonicalSearchDestination" in vm)
check("search click ignores UI destination", "result.destination" not in vm and "action.destination" not in vm)
check("search destination fail closed", "destinationResolver.resolve(destination) != null" in vm)
check("search UI no direct action destination navigation", "onOpenDestination(action.destination)" not in sections)
check("search merge permission defense", ".filter { result -> context.allows(result.requiredPermission) }" in usecase)
check("search no runCatching cancellation trap", "runCatching" not in usecase and "runCatching" not in vm)

# Provider pre-gates + tenant source context
provider_files = {
    "party":"feature/party/src/main/kotlin/com/verto/app/feature/party/application/search/PartyHomeSearchProvider.kt",
    "invoice":"feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/search/InvoiceHomeSearchProvider.kt",
    "payment":"feature/payment/src/main/kotlin/com/verto/app/feature/payment/application/search/PaymentHomeSearchProvider.kt",
    "inventory":"feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/application/search/InventoryHomeSearchProvider.kt",
}
for name, rel in provider_files.items():
    s=text(rel)
    check(f"{name} provider passes organizationId", "context.organizationId" in s and "source.search" in s)
    check(f"{name} provider verifies active session", "matchesActiveSession()" in s)
check("party permission pre-gate", "CLIENTS_VIEW" in text(provider_files["party"]).split("source.search",1)[0])
check("inventory permission pre-gate", "INVENTORY_VIEW" in text(provider_files["inventory"]).split("source.search",1)[0])
for name in ("invoice","payment"):
    s=text(provider_files[name])
    prefix=s.split("source.search",1)[0]
    check(f"{name} category permission pre-gate", "includeSales" in prefix and "includePurchases" in prefix and "if (!includeSales && !includePurchases) return emptyList()" in prefix)

# DAO tenant + bounded deterministic queries
dao_expect = {
 "party":("data/database/src/main/kotlin/com/verto/app/data/local/dao/ClientDao.kt", "searchClientsWithBalanceByPrefix", "organization_id = :organizationId"),
 "invoice":("data/database/src/main/kotlin/com/verto/app/data/local/dao/InvoiceReadDao.kt", "searchInvoiceCardsByNumberPrefix", "inv.organization_id = :organizationId"),
 "payment":("data/database/src/main/kotlin/com/verto/app/data/local/dao/PaymentDao.kt", "searchPaymentCards", "i.organization_id = :organizationId"),
 "inventory":("data/database/src/main/kotlin/com/verto/app/data/local/dao/InventoryCatalogReadDao.kt", "searchItemsByPrefix", "organization_id = :organizationId"),
}
for name,(rel,method,tenant) in dao_expect.items():
    s=text(rel)
    idx=s.find(method)
    window=s[max(0,idx-5000):idx+700]
    check(f"{name} DAO tenant scope", tenant in window)
    check(f"{name} DAO bounded ordered", "ORDER BY" in window and "LIMIT :limit" in window)

# Required tests
required_tests = [
 "app/src/test/kotlin/com/verto/app/ui/screens/home/HomeHeaderPolicy340Test.kt",
 "app/src/test/kotlin/com/verto/app/ui/screens/home/search/HomeSearchClickPolicy340Test.kt",
 "feature/dashboard/src/test/kotlin/com/verto/app/feature/dashboard/application/search/HomeSearchHardening340Test.kt",
 "feature/party/src/test/kotlin/com/verto/app/feature/party/application/search/PartyHomeSearchProvider340Test.kt",
 "feature/invoice/src/test/kotlin/com/verto/app/feature/invoice/application/search/InvoiceHomeSearchProvider340Test.kt",
 "feature/payment/src/test/kotlin/com/verto/app/feature/payment/application/search/PaymentHomeSearchProvider340Test.kt",
 "feature/inventory/src/test/kotlin/com/verto/app/feature/inventory/application/search/InventoryHomeSearchProvider340Test.kt",
]
check("required 340 tests authored", all((ROOT/p).exists() for p in required_tests))

# Schema freeze
migration_catalog=text("data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt")
check("Room schema remains 83", "ROOM_SCHEMA_VERSION: Int = 83" in migration_catalog)

# Baseline diff / parallel scope / protected files / visual signature
if BASELINE_ZIP and BASELINE_ZIP.exists():
    with zipfile.ZipFile(BASELINE_ZIP) as z:
        names=z.namelist(); top=names[0].split('/')[0]+'/'
        baseline={n[len(top):]:z.read(n) for n in names if n.startswith(top) and not n.endswith('/')}
    current={str(p.relative_to(ROOT)):p.read_bytes() for p in ROOT.rglob('*') if p.is_file()}
    modified={p for p in current.keys() & baseline.keys() if current[p]!=baseline[p]}
    added=set(current)-set(baseline); deleted=set(baseline)-set(current)
    changed=modified|added|deleted
    allowed_prefixes=(
        "app/src/main/kotlin/com/verto/app/ui/screens/home/HomeHeader",
        "app/src/main/kotlin/com/verto/app/ui/screens/home/HomeScreenContent.kt",
        "app/src/main/kotlin/com/verto/app/ui/screens/home/search/",
        "app/src/test/kotlin/com/verto/app/ui/screens/home/",
        "feature/dashboard/api/src/main/kotlin/com/verto/feature/dashboard/api/HomeContracts.kt",
        "feature/dashboard/src/main/kotlin/com/verto/app/feature/dashboard/application/search/",
        "feature/dashboard/src/test/kotlin/com/verto/app/feature/dashboard/application/search/",
        "feature/party/src/main/kotlin/com/verto/app/feature/party/application/search/",
        "feature/party/src/main/kotlin/com/verto/app/feature/party/data/search/",
        "feature/party/src/test/kotlin/com/verto/app/feature/party/application/search/",
        "feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/search/",
        "feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/data/search/",
        "feature/invoice/src/test/kotlin/com/verto/app/feature/invoice/application/search/",
        "feature/payment/src/main/kotlin/com/verto/app/feature/payment/application/search/",
        "feature/payment/src/main/kotlin/com/verto/app/feature/payment/data/search/",
        "feature/payment/src/test/kotlin/com/verto/app/feature/payment/application/search/",
        "feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/application/search/",
        "feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/data/search/",
        "feature/inventory/src/test/kotlin/com/verto/app/feature/inventory/application/search/",
        "data/database/src/main/kotlin/com/verto/app/data/local/dao/ClientDao.kt",
        "data/database/src/main/kotlin/com/verto/app/data/local/dao/InvoiceReadDao.kt",
        "data/database/src/main/kotlin/com/verto/app/data/local/dao/PaymentDao.kt",
        "data/database/src/main/kotlin/com/verto/app/data/local/dao/InventoryCatalogReadDao.kt",
        "tools/quality/session340_verify.py",
        "VERTO_SESSION_340_VERIFICATION.md",
    )
    unrelated=sorted(p for p in changed if not any(p.startswith(a) for a in allowed_prefixes))
    check("parallel scope unrelated=0", not unrelated, ", ".join(unrelated))
    check("no deleted files", not deleted, str(sorted(deleted)))
    protected_tokens=("HomePendingActions","HomeQuickActions","HomeActivityFeed","Drawer","Sync")
    protected=[p for p in changed if any(t in Path(p).name for t in protected_tokens) or p.startswith("server/")]
    check("protected files untouched", not protected, ", ".join(sorted(protected)))
    migrations=[p for p in changed if "Migration" in Path(p).name or "/migration" in p.lower()]
    check("no migration changes", not migrations, ", ".join(sorted(migrations)))
    server_sync=[p for p in changed if p.startswith("server/") or p.startswith("data/sync/")]
    check("no server/sync changes", not server_sync, ", ".join(sorted(server_sync)))

    visual_files=[
      "app/src/main/kotlin/com/verto/app/ui/screens/home/HomeHeader.kt",
      "app/src/main/kotlin/com/verto/app/ui/screens/home/search/HomeSearchScreen.kt",
      "app/src/main/kotlin/com/verto/app/ui/screens/home/search/HomeSearchSections.kt",
      "app/src/main/kotlin/com/verto/app/ui/screens/home/search/HomeSearchResultSections.kt",
    ]
    visual_rx=re.compile(r"Modifier\.|color\s*=|style\s*=|fontWeight\s*=|shape\s*=|border\s*=|shadowElevation|tonalElevation|Arrangement\.|Alignment\.|Spacer\(|LinearProgressIndicator|Scaffold\(")
    def visual_sig(data:bytes):
        return [ln.strip() for ln in data.decode('utf-8').splitlines() if visual_rx.search(ln)]
    visually_same=all(visual_sig(baseline[p])==visual_sig(current[p]) for p in visual_files)
    check("Search/Header visual signature unchanged", visually_same)

passed=sum(ok for _,ok,_ in checks)
for i,(name,ok,detail) in enumerate(checks,1):
    suffix=f" — {detail}" if detail and not ok else ""
    print(f"[{i:02d}] {'PASS' if ok else 'FAIL'} {name}{suffix}")
print(f"SESSION340_STATIC_VERIFICATION {passed}/{len(checks)} PASS")
sys.exit(0 if passed==len(checks) else 1)
