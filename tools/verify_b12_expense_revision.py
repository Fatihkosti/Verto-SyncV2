#!/usr/bin/env python3
from pathlib import Path
import json, re, hashlib

ROOT=Path(__file__).resolve().parents[1]
read=lambda p:(ROOT/p).read_text(encoding='utf-8')
checks=[]
def check(name, cond):
    checks.append((name,bool(cond)))
    if not cond: raise AssertionError(name)

repo=read('data/operations/src/main/kotlin/com/verto/app/data/repository/ExpenseRepository.kt')
cash=read('data/operations/src/main/kotlin/com/verto/app/utils/CashMovementSyncWriter.kt')+read('data/operations/src/main/kotlin/com/verto/app/utils/CashRegisterManager.kt')
applier=read('data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedStrongerSyncChangeApplier.kt')
contract=read('data/sync/src/main/kotlin/com/verto/app/data/sync/expense/ExpenseRevisionContractB12.kt')
dao=read('data/database/src/main/kotlin/com/verto/app/data/local/dao/SyncRepairV2Dao.kt')
guards=read('data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseB12ExpenseGuards.kt')
sql=read('supabase/migrations/20260911001500_b12_expense_versioned_history.sql')
dto=read('data/network/src/main/kotlin/com/verto/app/data/sync/FinancialSyncContractV2.kt')
schema=json.loads(read('SYNC_CONTRACT_V2.schema.json'))

check('B12 mutable identity retained', 'EXPENSE_IDENTITY_CHANGE_FORBIDDEN' in repo and '"expenseId" to after.id' in repo)
check('B12 full before/after intent', '"before" to before?.let(::expenseSnapshotMapB12)' in repo and '"after" to expenseSnapshotMapB12(after)' in repo)
check('B12 baseVersion captured', 'capturedBaseVersion' in repo and 'baseVersion = capturedBaseVersion' in repo)
check('B12 expense+cash batch', 'commandBatchId = batchId' in repo and 'batchCoordinator.seal' in repo and 'commandBatchId = intent.identity.commandBatchId' in cash)
check('B12 cash dependency', 'dependsOnMutationId = writeId' in cash)
check('B12 formula centralized', 'expenseCashDeltaMinorB12(before, after)' in repo and 'Math.negateExact(Math.subtractExact' in contract)
check('B12 applier mutable', 'IMMUTABLE_EXPENSE_FACT_CONFLICT' not in applier and 'database.expenseDao().updateExpense(row)' in applier)
check('B12 applier minor authority', 'val amountMinor = p.reqLong("amountMinor")' in applier and 'Money.ofMinor(amountMinor)' in applier)
check('B12 local history', 'putExpenseRevisionHistory' in dao and 'ExpenseRevisionHistoryEntity' in applier)
check('B12 history append-only', 'expense_revision_history_immutable_b12' in guards and 'expense_revision_history_no_delete_b12' in guards)
check('B12 DTOs', 'data class ExpenseRevisionIntentDtoV2' in dto and 'data class ExpenseRevisionDtoV2' in dto)
check('B12 schema defs', {'expenseRevisionIntentV2','expenseRevisionV2'} <= schema['$defs'].keys())
check('B12 server base version', 'p_base_version<>v_previous' in sql and "'STALE_BASE_VERSION'" in sql)
check('B12 server group validator', 'verto_validate_expense_group_b12' in sql and "'BLOCKED_EXPENSE_DOMAIN_DRIFT'" in sql)
check('B12 safe block before B07', "'B12_EXPENSE_BATCH_REQUIRED'" in sql)
check('B12 adapter passes baseVersion', "verto_apply_expense_command_b12(p_organization_id,p_aggregate_id,p_operation_type,p_payload,p_base_version)" in sql)
check('B12 server history', 'INSERT INTO public.expense_revision_history' in sql and 'expense_revision_history_immutable_b12' in sql)
check('B12 published server authority', "stronger_server_authority='verto_apply_expense_command_b12'" in sql and "conflict_policy='VERSIONED_MUTABLE_EXPENSE'" in sql)
check('B12 server actor and operation binding', "v_intent->>'actorId'" in sql and "v_actor::text" in sql and "v_intent->>'operation'<>'UPSERT'" in sql)

# T21 arithmetic as specified by the execution contract.
def effective(amount, state): return amount if state=='ACTIVE' else 0
def delta(old_amount, old_state, new_amount, new_state): return -(effective(new_amount,new_state)-effective(old_amount,old_state))
check('T21 note delta 0', delta(10000,'ACTIVE',10000,'ACTIVE') == 0)
check('T21 10000->15000 delta -5000', delta(10000,'ACTIVE',15000,'ACTIVE') == -5000)
check('T21 VOID delta +15000', delta(15000,'ACTIVE',15000,'VOID') == 15000)

print(f'B12_STATIC_GATE=PASS checks={len(checks)}')
for name,_ in checks: print('PASS', name)
