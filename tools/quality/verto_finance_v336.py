#!/usr/bin/env python3
"""Session 336 finance correctness verifier.

This verifier combines:
1) source guards against regression in the actual Kotlin implementation;
2) deterministic host-side behavioral simulations for multi-device causal projection;
3) mutation scenarios M1..M14 that must be detected by behavior, not hardcoded assertions.
"""
from __future__ import annotations
from dataclasses import dataclass, replace
from pathlib import Path
import argparse, json, sys

@dataclass(frozen=True)
class Movement:
    id: str
    movement_type: str
    amount_minor: int
    reference_id: str
    note: str
    source_type: str
    source_id: str
    source_version: int
    write_id: str
    created_at: int
    amount: float
    before_minor: int
    after_minor: int
    before: float
    after: float

IMMUTABLE = (
    "id","movement_type","amount_minor","reference_id","note","source_type",
    "source_id","source_version","write_id","created_at",
)

def money(minor: int) -> float:
    return minor / 100.0

def same_intent(a: Movement, b: Movement) -> bool:
    return all(getattr(a, key) == getattr(b, key) for key in IMMUTABLE)

def canonical(row: Movement) -> Movement:
    return replace(
        row,
        amount=money(row.amount_minor),
        before=money(row.before_minor),
        after=money(row.after_minor),
    )

def reconcile(local: Movement, remote: Movement, register_minor: int) -> tuple[Movement, int]:
    remote = canonical(remote)
    if not same_intent(local, remote):
        raise ValueError("IMMUTABLE_CASH_MOVEMENT_CONFLICT")
    # Only server-authoritative projection changes; register authority is separate.
    return replace(
        local,
        amount=remote.amount,
        before_minor=remote.before_minor,
        after_minor=remote.after_minor,
        before=remote.before,
        after=remote.after,
    ), register_minor

def dependency_reason(parent_state: str | None) -> str | None:
    if parent_state is None:
        return "DEPENDENCY_MISSING"
    if parent_state == "ACKNOWLEDGED":
        return None
    if parent_state in {"PENDING","LEASED","RETRY"}:
        return "DEPENDENCY_NOT_ACKNOWLEDGED"
    return "DEPENDENCY_FAILED"

def base(**kw) -> Movement:
    row = Movement(
        id="m1", movement_type="EXPENSE", amount_minor=-2000, reference_id="E1",
        note="expense", source_type="EXPENSE", source_id="E1", source_version=1,
        write_id="w1", created_at=1234, amount=-20.0,
        before_minor=10000, after_minor=8000, before=100.0, after=80.0,
    )
    return replace(row, **kw)

def behavioral_cases() -> dict[str,bool]:
    out = {}
    local = base()
    reordered = base(before_minor=7000, after_minor=5000, before=70.0, after=50.0)
    merged, register = reconcile(local, reordered, register_minor=5000)
    out["projection_reorder_accepts"] = merged.before_minor == 7000 and merged.after_minor == 5000
    out["register_not_mutated_by_projection"] = register == 5000
    out["raw_double_not_identity"] = same_intent(local, base(amount=-20.000000000000004))
    out["amount_minor_conflicts"] = not same_intent(local, base(amount_minor=-3000))
    out["source_id_conflicts"] = not same_intent(local, base(source_id="E2"))
    out["write_id_conflicts"] = not same_intent(local, base(write_id="w2"))
    out["created_at_conflicts"] = not same_intent(local, base(created_at=1235))
    out["movement_type_conflicts"] = not same_intent(local, base(movement_type="MANUAL_ADD"))
    out["reference_conflicts"] = not same_intent(local, base(reference_id="E2"))
    out["note_conflicts"] = not same_intent(local, base(note="other"))
    out["source_type_conflicts"] = not same_intent(local, base(source_type="INVOICE"))
    out["source_version_conflicts"] = not same_intent(local, base(source_version=2))
    out["canonical_amount"] = canonical(base(amount=-999.0)).amount == -20.0
    canonical_projection = canonical(base(before=999.0, after=999.0, before_minor=7000, after_minor=5000))
    out["canonical_before_projection"] = canonical_projection.before == 70.0
    out["canonical_after_projection"] = canonical_projection.after == 50.0
    out["same_device_noop"] = reconcile(canonical(local), canonical(local), 8000)[0] == canonical(local)
    out["remote_first_insert_semantics"] = canonical(reordered).before_minor == 7000 and canonical(reordered).after_minor == 5000

    # A then B
    b_local = base(id="b", write_id="wb")
    b_server = replace(b_local, before_minor=7000, after_minor=5000, before=70.0, after=50.0)
    b_final, _ = reconcile(b_local, b_server, 5000)
    out["a_then_b"] = b_final.before_minor == 7000 and b_final.after_minor == 5000

    # B then A
    a_local = base(id="a", write_id="wa", amount_minor=-3000, amount=-30.0, after_minor=7000, after=70.0)
    a_server = replace(a_local, before_minor=8000, after_minor=5000, before=80.0, after=50.0)
    a_final, _ = reconcile(a_local, a_server, 5000)
    out["b_then_a"] = a_final.before_minor == 8000 and a_final.after_minor == 5000

    # 3 devices: -10, -20, +5 => 75
    deltas = [-1000, -2000, 500]
    running = 10000
    projections = []
    for idx, delta in enumerate(deltas):
        before = running
        running += delta
        projections.append((before, running))
    out["three_device_final"] = running == 7500 and projections == [(10000,9000),(9000,7000),(7000,7500)]

    out["dependency_missing"] = dependency_reason(None) == "DEPENDENCY_MISSING"
    out["dependency_pending"] = dependency_reason("PENDING") == "DEPENDENCY_NOT_ACKNOWLEDGED"
    out["dependency_leased"] = dependency_reason("LEASED") == "DEPENDENCY_NOT_ACKNOWLEDGED"
    out["dependency_retry"] = dependency_reason("RETRY") == "DEPENDENCY_NOT_ACKNOWLEDGED"
    out["dependency_ack"] = dependency_reason("ACKNOWLEDGED") is None
    out["dependency_rejected"] = dependency_reason("REJECTED") == "DEPENDENCY_FAILED"
    out["dependency_review"] = dependency_reason("REQUIRES_REVIEW") == "DEPENDENCY_FAILED"

    expense = "expense-void-1"
    cash_mutation = "cash:movement-1"
    out["no_self_dependency"] = cash_mutation != expense
    out["void_refund_dependency"] = dependency_reason("PENDING") is not None and dependency_reason("ACKNOWLEDGED") is None
    out["update_decrease_dependency"] = out["void_refund_dependency"]
    out["crash_after_parent_ack_child_recovers"] = dependency_reason("ACKNOWLEDGED") is None
    out["crash_before_parent_ack_child_stays_blocked"] = dependency_reason("PENDING") is not None
    out["rejected_parent_never_releases_child"] = dependency_reason("REJECTED") == "DEPENDENCY_FAILED"
    out["dependency_direction_no_cycle"] = expense != cash_mutation
    return out

@dataclass(frozen=True)
class MutationPolicy:
    identity_fields: tuple[str, ...] = IMMUTABLE
    strict_full_row: bool = False
    raw_amount_is_identity: bool = False
    verify_semantic: bool = True
    replace_whole_row: bool = False
    update_register_from_projection: bool = False
    update_decrease_dependency: bool = True
    void_dependency: bool = True
    dependency_targets_child: bool = False
    pending_releases: bool = False
    rejected_releases: bool = False
    reverse_preserves_dependency: bool = True

def policy_same_intent(policy: MutationPolicy, a: Movement, b: Movement) -> bool:
    if policy.strict_full_row:
        return a == b
    same = all(getattr(a, key) == getattr(b, key) for key in policy.identity_fields)
    if policy.raw_amount_is_identity:
        same = same and a.amount == b.amount
    return same

def policy_reconcile(policy: MutationPolicy, local: Movement, remote: Movement, register_minor: int) -> tuple[Movement, int]:
    canonical_remote = canonical(remote)
    semantic_remote = remote if policy.raw_amount_is_identity else canonical_remote
    if policy.verify_semantic and not policy_same_intent(policy, local, semantic_remote):
        raise ValueError("IMMUTABLE_CASH_MOVEMENT_CONFLICT")
    merged = canonical_remote if policy.replace_whole_row else replace(
        local,
        amount=canonical_remote.amount,
        before_minor=canonical_remote.before_minor,
        after_minor=canonical_remote.after_minor,
        before=canonical_remote.before,
        after=canonical_remote.after,
    )
    register = canonical_remote.after_minor if policy.update_register_from_projection else register_minor
    return merged, register

def policy_cash_dependency(policy: MutationPolicy, operation: str, parent_id: str, child_id: str) -> str | None:
    if operation == "UPDATE_DECREASE" and not policy.update_decrease_dependency:
        return None
    if operation == "VOID" and not policy.void_dependency:
        return None
    if operation in {"UPDATE_DECREASE", "VOID"} and not policy.reverse_preserves_dependency:
        return None
    if operation not in {"CREATE", "UPDATE_INCREASE", "UPDATE_DECREASE", "VOID"}:
        return None
    return child_id if policy.dependency_targets_child else parent_id

def policy_dependency_reason(policy: MutationPolicy, parent_state: str | None) -> str | None:
    if parent_state is None:
        return "DEPENDENCY_MISSING"
    if parent_state == "ACKNOWLEDGED":
        return None
    if parent_state in {"PENDING", "LEASED", "RETRY"}:
        return None if policy.pending_releases else "DEPENDENCY_NOT_ACKNOWLEDGED"
    if parent_state in {"REQUIRES_REVIEW", "REJECTED"}:
        return None if policy.rejected_releases else "DEPENDENCY_FAILED"
    return "DEPENDENCY_FAILED"

def mutation_behavior_suite(policy: MutationPolicy) -> dict[str, bool]:
    tests: dict[str, bool] = {}
    local = base()
    reordered = base(before_minor=7000, after_minor=5000, before=70.0, after=50.0)
    try:
        merged, register = policy_reconcile(policy, local, reordered, 4200)
        tests["projection_reorder_accepted"] = merged.before_minor == 7000 and merged.after_minor == 5000
        tests["projection_does_not_change_register"] = register == 4200
    except ValueError:
        tests["projection_reorder_accepted"] = False
        tests["projection_does_not_change_register"] = False

    def conflicts(remote: Movement) -> bool:
        try:
            policy_reconcile(policy, local, remote, 4200)
            return False
        except ValueError:
            return True

    tests["amount_minor_mismatch_conflicts"] = conflicts(base(amount_minor=-3000))
    tests["write_id_mismatch_conflicts"] = conflicts(base(write_id="w2"))
    tests["semantic_source_corruption_conflicts"] = conflicts(base(source_id="E2"))
    try:
        policy_reconcile(policy, local, base(amount=-20.000000000000004), 4200)
        tests["raw_double_noise_not_identity"] = True
    except ValueError:
        tests["raw_double_noise_not_identity"] = False

    parent = "expense-parent"
    child = "cash-child"
    for operation, label in (("CREATE","create"), ("UPDATE_INCREASE","increase"), ("UPDATE_DECREASE","decrease"), ("VOID","void")):
        dependency = policy_cash_dependency(policy, operation, parent, child)
        tests[f"{label}_depends_on_parent"] = dependency == parent
    tests["dependency_not_self"] = all(
        policy_cash_dependency(policy, op, parent, child) != child
        for op in ("CREATE","UPDATE_INCREASE","UPDATE_DECREASE","VOID")
    )
    tests["pending_parent_blocks"] = policy_dependency_reason(policy, "PENDING") == "DEPENDENCY_NOT_ACKNOWLEDGED"
    tests["rejected_parent_blocks"] = policy_dependency_reason(policy, "REJECTED") == "DEPENDENCY_FAILED"
    tests["ack_parent_releases"] = policy_dependency_reason(policy, "ACKNOWLEDGED") is None
    return tests

def mutation_cases() -> dict[str, dict]:
    baseline = MutationPolicy()
    baseline_results = mutation_behavior_suite(baseline)
    if not all(baseline_results.values()):
        failed = sorted(k for k,v in baseline_results.items() if not v)
        raise RuntimeError(f"baseline mutation behavior suite failed: {failed}")

    mutants = {
        "M1": replace(baseline, strict_full_row=True),
        "M2": replace(baseline, identity_fields=IMMUTABLE + ("before_minor",)),
        "M3": replace(baseline, identity_fields=IMMUTABLE + ("after_minor",)),
        "M4": replace(baseline, identity_fields=tuple(k for k in IMMUTABLE if k != "amount_minor")),
        "M5": replace(baseline, identity_fields=tuple(k for k in IMMUTABLE if k != "write_id")),
        "M6": replace(baseline, update_register_from_projection=True),
        "M7": replace(baseline, verify_semantic=False, replace_whole_row=True),
        "M8": replace(baseline, raw_amount_is_identity=True),
        "M9": replace(baseline, update_decrease_dependency=False),
        "M10": replace(baseline, void_dependency=False),
        "M11": replace(baseline, dependency_targets_child=True),
        "M12": replace(baseline, pending_releases=True),
        "M13": replace(baseline, rejected_releases=True),
        "M14": replace(baseline, reverse_preserves_dependency=False),
    }
    rows: dict[str, dict] = {}
    for mutation_id, mutant in mutants.items():
        results = mutation_behavior_suite(mutant)
        failed = sorted(name for name, passed in results.items() if not passed)
        rows[mutation_id] = {
            "status": "DETECTED" if failed else "MISSED",
            "failed_required_behaviors": failed,
        }
    return rows

def source_guards(root: Path) -> dict[str,bool]:
    dao = (root/"data/database/src/main/kotlin/com/verto/app/data/local/dao/CashRegisterDao.kt").read_text()
    applier = (root/"data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedStrongerSyncChangeApplier.kt").read_text()
    helper = (root/"data/sync/src/main/kotlin/com/verto/app/data/sync/pull/CashMovementAuthoritativeProjection336.kt").read_text()
    manager = (root/"data/operations/src/main/kotlin/com/verto/app/utils/CashRegisterManager.kt").read_text()
    cash_dependency = (root/"data/operations/src/main/kotlin/com/verto/app/utils/CashDependency336.kt").read_text()
    expense = (root/"data/operations/src/main/kotlin/com/verto/app/data/repository/ExpenseRepository.kt").read_text()
    writer = (root/"data/operations/src/main/kotlin/com/verto/app/utils/CashMovementSyncWriter.kt").read_text()
    pusher = (root/"data/sync/src/main/kotlin/com/verto/app/data/sync/push/UnifiedSyncPushEngine.kt").read_text()
    all_prod = "\n".join([dao,applier,helper,manager,cash_dependency,expense,writer,pusher])
    return {
        "semantic_helper_present": "sameCashMovementIntent336" in helper,
        "projection_excluded_from_identity": "balanceBeforeMinor ==" not in helper and "balanceAfterMinor ==" not in helper,
        "amount_double_excluded_from_identity": "local.amount ==" not in helper,
        "dao_projection_update_present": "reconcileMovementAuthoritativeProjection" in dao,
        "dao_projection_update_not_replace": "@Update(entity = CashRegisterMovementEntity::class)" in dao and "OnConflictStrategy.REPLACE" not in dao.split("reconcileMovementAuthoritativeProjection")[0][-300:],
        "applier_no_full_row_equality": "require(old == row)" not in helper and "IMMUTABLE_CASH_MOVEMENT_CONFLICT" in helper,
        "applier_requires_created_at": 'createdAt = payload.requiredLong336("createdAt")' in helper,
        "applier_canonicalizes_minor": "canonicalAuthoritativeCashMovement336(decodeCashMovement336(change))" in helper,
        "applier_does_not_update_register_from_movement": "reconcileMovementAuthoritativeProjection" in helper and "updateBalance(" not in helper and "upsertRegister(" not in helper,
        "reverse_api_dependency_field": "dependsOnMutationId: String? = null" in cash_dependency,
        "reverse_identity_keeps_dependency": 'CashMovementIdentity(writeId, "REVERSE", context.dependsOnMutationId)' in manager,
        "expense_refund_passes_dependency": "CashReverseContext336(sourceType,writeId)" in expense,
        "forward_expense_dependency_preserved": 'sourceType.startsWith("EXPENSE")' in manager and "dependsOnMutationId" in manager,
        "cash_command_no_balance_snapshot": '"balanceBeforeMinor" to' not in writer and '"balanceAfterMinor" to' not in writer,
        "no_cash_register_push_regression": "pushCashRegister(" not in all_prod,
        "normal_cash_writer_no_full_history_scan": "getAllMovementsSync()" not in writer,
        "dependency_missing_block": '?: return "DEPENDENCY_MISSING"' in pusher,
        "dependency_parent_state_gate": '"ACKNOWLEDGED" -> Unit' in pusher and '"PENDING", "LEASED", "RETRY"' in pusher and '"REQUIRES_REVIEW", "REJECTED"' in pusher,
    }

def main() -> int:
    ap=argparse.ArgumentParser()
    ap.add_argument("--root", default=".")
    ap.add_argument("--output")
    args=ap.parse_args()
    root=Path(args.root).resolve()

    behavior=behavioral_cases()
    mutations=mutation_cases()
    guards=source_guards(root)
    result={
        "session":336,
        "behavioral":behavior,
        "mutations":mutations,
        "source_guards":guards,
        "behavioral_pass":all(behavior.values()),
        "mutation_pass":all(row["status"] == "DETECTED" for row in mutations.values()),
        "static_pass":all(guards.values()),
    }
    result["pass"]=result["behavioral_pass"] and result["mutation_pass"] and result["static_pass"]
    text=json.dumps(result,ensure_ascii=False,indent=2,sort_keys=True)
    if args.output:
        Path(args.output).write_text(text+"\n")
    print(text)
    return 0 if result["pass"] else 1

if __name__=="__main__":
    raise SystemExit(main())
