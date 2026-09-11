#!/usr/bin/env python3
"""Session 334 source-extracted behavioral mutation verifier.

Each case starts from the exact production source text, proves the clean extracted
financial seam with a compiled Kotlin harness, applies exactly one production
mutation, then requires the same harness to fail. DETECTED is never inferred from
symbol/string presence alone.
"""
from __future__ import annotations

import hashlib
import json
from concurrent.futures import ThreadPoolExecutor
import shutil
import subprocess
import tempfile
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
KOTLINC = shutil.which("kotlinc")
JAVA = shutil.which("java")

REPO = Path("data/operations/src/main/kotlin/com/verto/app/data/repository/ExpenseRepository.kt")
APP = Path("app/src/main/kotlin/com/verto/app/feature/expenses/bridge/ExpensesOperationsAdapter.kt")
VM = Path("feature/expenses/src/main/kotlin/com/verto/app/ui/screens/expenses/ExpensesViewModel.kt")

RUN_SUSPEND = r'''
import kotlin.coroutines.*
fun runSuspend(block:suspend()->Unit) {
    var outcome:Result<Unit>?=null
    block.startCoroutine(object:Continuation<Unit>{
        override val context:CoroutineContext=EmptyCoroutineContext
        override fun resumeWith(result:Result<Unit>){outcome=result}
    })
    check(outcome != null) { "unexpected suspension" }
    outcome!!.getOrThrow()
}
'''

@dataclass(frozen=True)
class Case:
    id: str
    risk: str
    path: Path
    anchor: str
    replacement: str
    extract_start: str
    harness: str
    prelude: str = RUN_SUSPEND

CASES = (
    Case(
        "F1", "Double diff replaces exact minor diff", REPO,
        "val diff=Math.subtractExact(newMinor,oldMinor)",
        "val diff=((newMinor/100.0-oldMinor/100.0)*100.0).toLong()",
        'internal const val EXPENSE_UPDATE_REFUND_SOURCE',
        r'''fun main(){runSuspend{var cash=0L;applyExpenseAmountDelta(1000L,1010L,{cash+=it},{_,_->});check(cash==10L){"exact delta lost: $cash"}}}''',
    ),
    Case(
        "F2", "Expense insert skips cash effect", REPO,
        '):T { val result=persist(); cash(); outbox(); return result }',
        '):T { val result=persist(); outbox(); return result }',
        'internal const val EXPENSE_UPDATE_REFUND_SOURCE',
        r'''fun main(){runSuspend{val calls=mutableListOf<String>();insertExpenseEffects({calls+="expense";1L},{calls+="cash"},{calls+="outbox"});check(calls==listOf("expense","cash","outbox")){calls}}}''',
    ),
    Case(
        "F3", "Void skips refund", REPO,
        'refund(current); val voided=toVoided(current); commitVoid(voided); return true',
        'val voided=toVoided(current); commitVoid(voided); return true',
        'internal const val EXPENSE_UPDATE_REFUND_SOURCE',
        r'''fun main(){runSuspend{var refunds=0;var state="ACTIVE";val changed=voidExpenseEffects(state,{it=="VOID"},{refunds++},{"VOID"},{state=it});check(changed);check(refunds==1){"refunds=$refunds"};check(state=="VOID")}}''',
    ),
    Case(
        "F4", "Update refund misclassified as void refund", REPO,
        'internal const val EXPENSE_UPDATE_REFUND_SOURCE = "EXPENSE_UPDATE_REFUND"',
        'internal const val EXPENSE_UPDATE_REFUND_SOURCE = "EXPENSE_VOID_REFUND"',
        'internal const val EXPENSE_UPDATE_REFUND_SOURCE',
        r'''fun main(){runSuspend{var source="";applyExpenseAmountDelta(1010L,1000L,{}, {_,s->source=s});check(source=="EXPENSE_UPDATE_REFUND"){source};check(EXPENSE_UPDATE_REFUND_SOURCE!=EXPENSE_VOID_REFUND_SOURCE)}}''',
    ),
    Case(
        "F5", "Linked expense outer transaction removed", APP,
        '):T = transaction { val result=writeExpense(); linkedEffects(); result }',
        '):T { val result=writeExpense(); linkedEffects(); return result }',
        'internal suspend fun <T> runLinkedExpenseAtomic',
        r'''fun main(){runSuspend{var expense=0;var landed=0;suspend fun <T> tx(block:suspend()->T):T{val e=expense;val l=landed;return try{block()}catch(t:Throwable){expense=e;landed=l;throw t}};val ok=runLinkedExpenseAtomic({b->tx(b)},{expense++;"e"},{landed+=3});check(ok=="e"&&expense==1&&landed==3);expense=0;landed=0;try{runLinkedExpenseAtomic({b->tx(b)},{expense++;"e"},{landed++;landed++;error("middle")});error("failure expected")}catch(_:IllegalStateException){};check(expense==0&&landed==0){"partial commit expense=$expense landed=$landed"}}}''',
    ),
    Case(
        "F6", "Landed-cost apply failure ignored", APP,
        'check(apply(command)) { "FAIL_LINKED_EXPENSE_INVENTORY_TARGET_MISSING:$targetId" }',
        'apply(command)',
        'internal suspend fun <T> runLinkedExpenseAtomic',
        r'''fun main(){runSuspend{var failed=false;try{requireLandedCostApply("cmd","missing"){false}}catch(_:IllegalStateException){failed=true};check(failed){"false apply was accepted"}}}''',
    ),
    Case(
        "F7", "Cash-adjust permission denial returns success", APP,
        '):T { authorize(); return transaction(block) }',
        '):T { return transaction(block) }',
        'internal suspend fun <T> runLinkedExpenseAtomic',
        r'''fun main(){runSuspend{var entered=false;var denied=false;try{runAuthorizedCashAdjustment({error("denied")},{b->entered=true;b()}){"ok"}}catch(_:IllegalStateException){denied=true};check(denied&&!entered){"permission fail-open"}}}''',
    ),
    Case(
        "F8", "31-day month approximation restored", VM,
        'val start=calendar.timeInMillis; calendar.add(Calendar.MONTH,1); return start to calendar.timeInMillis',
        'val start=calendar.timeInMillis; calendar.timeInMillis=start+31L*86_400_000L; return start to calendar.timeInMillis',
        'internal fun monthRangeFor',
        r'''fun main(){
fun checkMonth(y:Int,m:Int,days:Int){val z=java.util.TimeZone.getTimeZone("UTC");val c=Calendar.getInstance(z).apply{clear();set(y,m,15,12,0,0)};val (s,e)=monthRangeFor(c.timeInMillis,z);check(e-s==days.toLong()*86_400_000L){"bad span y=$y m=$m span=${e-s}"};val expected=Calendar.getInstance(z).apply{clear();set(y,m+1,1,0,0,0)}.timeInMillis;check(e==expected){"next-month boundary mismatch"};check(e-1 in s until e);check(e !in s until e)}
checkMonth(2023,Calendar.FEBRUARY,28);checkMonth(2024,Calendar.FEBRUARY,29);checkMonth(2024,Calendar.APRIL,30);checkMonth(2024,Calendar.JANUARY,31)
}''',
        prelude='import java.util.Calendar\n',
    ),
)

def extracted_source(text: str, marker: str) -> str:
    pos = text.find(marker)
    if pos < 0:
        raise ValueError(f"extract marker missing: {marker}")
    return text[pos:]

def compile_run(source: str, prelude: str, harness: str) -> dict:
    if not KOTLINC or not JAVA:
        return {"compiled": False, "ran": False, "exit_code": 127, "output": "kotlinc/java unavailable"}
    with tempfile.TemporaryDirectory(prefix="verto334-finance-") as td:
        td = Path(td)
        src = td / "Check.kt"
        jar = td / "check.jar"
        src.write_text(prelude + "\n" + source + "\n" + harness + "\n", encoding="utf-8")
        cp = subprocess.run([KOTLINC, str(src), "-include-runtime", "-d", str(jar)], text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=30)
        if cp.returncode != 0:
            return {"compiled": False, "ran": False, "exit_code": cp.returncode, "output": cp.stdout[-4000:]}
        rp = subprocess.run([JAVA, "-jar", str(jar)], text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=15)
        return {"compiled": True, "ran": True, "exit_code": rp.returncode, "output": rp.stdout[-4000:]}

def execute(case: Case) -> dict:
    path = ROOT / case.path
    original = path.read_text(encoding="utf-8")
    count = original.count(case.anchor)
    row = {"id":case.id,"risk":case.risk,"source":str(case.path),"anchor_count":count,"mutation_applied":False,"detected":False}
    if count != 1:
        row.update(status="FAIL", reason=f"mutation anchor count={count}")
        return row
    clean_source = extracted_source(original, case.extract_start)
    clean = compile_run(clean_source, case.prelude, case.harness)
    row["clean"] = clean
    if clean.get("exit_code") != 0:
        row.update(status="FAIL", reason="clean detecting harness did not pass")
        return row
    mutated_text = original.replace(case.anchor, case.replacement, 1)
    row["mutation_applied"] = mutated_text != original
    mutated_source = extracted_source(mutated_text, case.extract_start)
    mutated = compile_run(mutated_source, case.prelude, case.harness)
    row["mutated"] = mutated
    row["detected"] = bool(row["mutation_applied"] and mutated.get("ran") and mutated.get("exit_code") != 0)
    row["status"] = "DETECTED" if row["detected"] else "NOT_DETECTED"
    return row

def allocator_check() -> dict:
    text = (ROOT / APP).read_text(encoding="utf-8")
    start = text.find("internal data class CostTarget")
    end = text.find("private fun stableId", start)
    if start < 0 or end < 0:
        return {"status":"FAIL", "reason":"allocator extraction markers missing"}
    source = text[start:end]
    harness = r'''
fun main(){
    val targets=listOf(
        CostTarget("a",2,100L),
        CostTarget("b",3,200L),
        CostTarget("c",5,50L),
    )
    val first=allocateMinor(101L,targets)
    check(first.sum()==101L){"minor conservation failed: $first"}
    first.indices.forEach{check(first[it] % targets[it].quantity == 0L){"non exact per-unit allocation: $first"}}
    val second=allocateMinor(101L,targets)
    check(first.contentEquals(second)){"allocation is non-deterministic"}
}
'''
    run = compile_run(source, "", harness)
    return {"status":"PASS" if run.get("exit_code")==0 else "FAIL", "run":run}

def main() -> int:
    with ThreadPoolExecutor(max_workers=4) as pool:
        rows = list(pool.map(execute, CASES))
    allocator = allocator_check()
    result = {
        "format":"verto-finance-behavioral-mutations-v334",
        "session":334,
        "source_hashes": {str(p):hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in (REPO,APP,VM)},
        "required":len(rows),
        "detected":sum(1 for r in rows if r.get("detected")),
        "mutations":rows,
        "allocator_check":allocator,
    }
    result["status"] = "PASS" if result["detected"] == result["required"] and allocator.get("status")=="PASS" else "FAIL"
    print(json.dumps(result,indent=2,sort_keys=True))
    return 0 if result["status"] == "PASS" else 2

if __name__ == "__main__":
    raise SystemExit(main())
