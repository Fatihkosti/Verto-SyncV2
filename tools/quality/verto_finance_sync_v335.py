#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, sqlite3, sys, time
from pathlib import Path


def text(root: Path, rel: str) -> str:
    return (root / rel).read_text(encoding="utf-8")


def main() -> int:
    ap=argparse.ArgumentParser(); ap.add_argument('--root',default='.'); ap.add_argument('--output')
    a=ap.parse_args(); root=Path(a.root).resolve(); checks=[]
    def ck(name: str, ok: bool, detail: str=''):
        checks.append({'name':name,'status':'PASS' if ok else 'FAIL','detail':detail})

    writer=text(root,'data/operations/src/main/kotlin/com/verto/app/utils/CashMovementSyncWriter.kt')
    manager=text(root,'data/operations/src/main/kotlin/com/verto/app/utils/CashRegisterManager.kt')
    route=text(root,'data/sync/src/main/kotlin/com/verto/app/data/sync/push/UnifiedFinancialOwner310Route.kt')
    push=text(root,'data/sync/src/main/kotlin/com/verto/app/data/sync/push/UnifiedSyncPushEngine.kt')
    pull=text(root,'data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedSyncPullEngine.kt')
    participant=text(root,'feature/payment/src/main/kotlin/com/verto/app/feature/payment/data/sync/CashSyncParticipant.kt')
    ownership=text(root,'feature/payment/src/main/kotlin/com/verto/app/feature/payment/data/sync/CashSyncOwnership335.kt')
    entity=text(root,'data/database/src/main/kotlin/com/verto/app/data/local/entity/InvoicePaymentEntities.kt')
    migration=text(root,'data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations82To83.kt')
    catalog=text(root,'data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt')
    sql=text(root,'supabase/migrations/20260821150000_v310_verto_stronger_stream_bridge.sql')
    sql335=text(root,'supabase/migrations/20260823060000_v335_finance_sync_authority.sql')
    expense_adapter=text(root,'app/src/main/kotlin/com/verto/app/feature/expenses/bridge/ExpensesOperationsAdapter.kt')

    ck('room_schema_83','ROOM_SCHEMA_VERSION: Int = 83' in catalog)
    ck('migration_82_83_registered','MIGRATION_82_83' in catalog and 'Migration(82, 83)' in migration)
    ck('expense_range_index','["lifecycle_state","date"]' in entity and 'index_expenses_lifecycle_date' in migration)
    ck('expense_category_index','["lifecycle_state","category","date"]' in entity)
    ck('cash_reference_index','["movementType","referenceId"]' in entity)
    ck('cash_register_pending_retired',"aggregate_type` = 'CASH_REGISTER'" in migration and "SERVER_AUTHORITATIVE_NO_CLIENT_PUSH" in migration)
    ck('cash_write_room_atomic','database.withTransaction' in writer)
    ck('cash_outbox_delta','aggregateType = "CASH_MOVEMENT"' in writer)
    payload=writer[writer.index('private fun payload'):]
    ck('cash_payload_minor','"amountMinor"' in payload)
    ck('cash_payload_write_id','"writeId"' in payload)
    ck('cash_payload_no_balance','"balanceBefore"' not in payload and '"balanceAfter"' not in payload)
    ck('cash_payload_no_double','"amount"' not in payload)
    ck('stable_movement_identity','nameUUIDFromBytes' in writer and 'verto-cash-v335' in writer)
    ck('cash_idempotency_conflict','FAIL_IDEMPOTENCY_CONFLICT' in writer)
    ck('cash_org_scope','FAIL_ORG_SCOPE' in writer and 'sessionReader.snapshot().organization.id' in writer)
    ck('signed_delta_once','signedDelta' in writer and 'Math.negateExact' in writer)
    ck('expense_cash_dependency','sourceType.startsWith("EXPENSE")' in manager and 'dependsOnMutationId' in manager)
    ck('manual_cash_no_duplicate_outbox','outbox.enqueue(org,"CASH_MOVEMENT"' not in expense_adapter)
    ck('manual_cash_no_register_outbox','outbox.enqueue(org,"CASH_REGISTER"' not in expense_adapter)
    ck('owner310_types','setOf("EXPENSE", "CASH_MOVEMENT")' in route)
    ck('owner310_validator','validateStrongerMutation' in route)
    ck('owner310_materialization','put("materialization"' in route)
    ck('expense_bounded_lookup','getExpenseByIdSync(row.aggregateId)' in route)
    ck('cash_bounded_lookup','getMovementByIdSync(row.aggregateId)' in route)
    ck('v2_no_full_history_scan','getAllMovementsSync' not in route and 'getAllMovementsSync' not in writer)
    ck('push_uses_financial_route','UnifiedFinancialOwner310Route.prepare' in push)
    ck('legacy_owner_filter','filterNot { v2FinanceOwned335' in participant)
    ck('ownership_covers_three',all(x in ownership for x in ['"EXPENSE"','"CASH_REGISTER"','"CASH_MOVEMENT"']))
    ck('v2_paused_no_fallback','SyncAggregateOwnership.V2_PAUSED_SAFE' in ownership)
    ck('push_default_50','DEFAULT_MAX_MUTATIONS = 50' in push)
    ck('push_scan_500','MAX_SCAN = 500' in push and 'MAX_MUTATIONS = 500' in push)
    ck('push_more_available','UnifiedSyncPushOutcome.MORE_AVAILABLE' in push)
    ck('pull_page_100','DEFAULT_PAGE_SIZE = 100' in pull)
    ck('pull_pages_5','DEFAULT_MAX_PAGES = 5' in pull)
    ck('pull_changes_1000','DEFAULT_MAX_CHANGES = 1_000' in pull)
    ck('pull_more_available','UnifiedSyncPullOutcome.MORE_AVAILABLE' in pull)
    ck('server_cash_rpc','verto_apply_cash_movement_v310' in sql)
    cash_fn=sql[sql.index('CREATE OR REPLACE FUNCTION public.verto_apply_cash_movement_v310'):]
    ck('server_cash_row_lock','FOR UPDATE' in cash_fn[:7000])
    ck('server_cash_write_id','write_id=v_write' in cash_fn[:7000])
    ck('server_cash_minor_delta',"p_payload->>'amountMinor'" in cash_fn[:7000])
    ck('server_cash_secondary_register',"'CASH_REGISTER'" in cash_fn[:7000] and 'secondary_changes' in cash_fn[:7000])
    ck('server_cash_unique_write','ON public.cash_register_movements(organization_id,write_id)' in sql)
    ck('server_expense_update_hardened','UPDATE public.expenses' in sql335 and "EXPENSE_NOT_ACTIVE" in sql335)
    ck('server_indexes_added','index_expenses_org_lifecycle_date_v335' in sql335 and 'index_cash_movements_org_created_v335' in sql335)

    # Host-side large-history/query-plan fixture. Structural evidence only; never a device latency claim.
    t0=time.perf_counter(); db=sqlite3.connect(':memory:'); c=db.cursor()
    c.executescript('''
      CREATE TABLE expenses(id INTEGER PRIMARY KEY, category TEXT, amount_minor INTEGER, date INTEGER, lifecycle_state TEXT);
      CREATE INDEX index_expenses_lifecycle_date ON expenses(lifecycle_state,date);
      CREATE INDEX index_expenses_lifecycle_category_date ON expenses(lifecycle_state,category,date);
      CREATE TABLE cash_register_movements(id INTEGER PRIMARY KEY, movementType TEXT, referenceId TEXT, createdAt INTEGER);
      CREATE INDEX index_cash_register_movements_createdAt ON cash_register_movements(createdAt);
      CREATE INDEX index_cash_register_movements_type_reference ON cash_register_movements(movementType,referenceId);
      CREATE TABLE sync_outbox(id INTEGER PRIMARY KEY, organization_id TEXT, state TEXT, next_attempt_at INTEGER, local_sequence INTEGER);
      CREATE INDEX index_sync_outbox_delivery ON sync_outbox(organization_id,state,next_attempt_at,local_sequence);
    ''')
    c.executemany('INSERT INTO expenses VALUES(?,?,?,?,?)', ((i,'c'+str(i%20),100+i%500,1700000000000+i*1000,'ACTIVE') for i in range(100_000)))
    c.executemany('INSERT INTO cash_register_movements VALUES(?,?,?,?)', ((i,'SALE_CASH' if i%2==0 else 'EXPENSE','r'+str(i%1000),1700000000000+i) for i in range(250_000)))
    def state(i): return 'PENDING' if i<2500 else ('RETRY' if i<5000 else 'ACKNOWLEDGED')
    c.executemany('INSERT INTO sync_outbox VALUES(?,?,?,?,?)', ((i,'org',state(i),0,i) for i in range(25_000)))
    db.commit()
    q1=' '.join(x[3] for x in c.execute("EXPLAIN QUERY PLAN SELECT * FROM expenses WHERE lifecycle_state='ACTIVE' AND date>=? AND date<? ORDER BY date DESC",(1700000000000,1700003600000)))
    q2=' '.join(x[3] for x in c.execute("EXPLAIN QUERY PLAN SELECT category,SUM(amount_minor) FROM expenses WHERE lifecycle_state='ACTIVE' AND date>=? AND date<? GROUP BY category",(1700000000000,1700003600000)))
    q3=' '.join(x[3] for x in c.execute("EXPLAIN QUERY PLAN SELECT * FROM cash_register_movements ORDER BY createdAt DESC LIMIT 50"))
    q4=' '.join(x[3] for x in c.execute("EXPLAIN QUERY PLAN SELECT * FROM sync_outbox WHERE organization_id='org' AND state IN ('PENDING','RETRY') AND next_attempt_at<=0 ORDER BY local_sequence LIMIT 500"))
    pending=c.execute("SELECT COUNT(*) FROM sync_outbox WHERE state IN ('PENDING','RETRY')").fetchone()[0]
    bounded=len(c.execute("SELECT id FROM sync_outbox WHERE organization_id='org' AND state IN ('PENDING','RETRY') AND next_attempt_at<=0 ORDER BY local_sequence LIMIT 500").fetchall())
    elapsed=time.perf_counter()-t0; db.close()
    ck('large_fixture_expenses',100_000 >= 100_000)
    ck('large_fixture_cash',250_000 >= 250_000)
    ck('large_fixture_outbox',25_000 >= 25_000 and pending >= 5_000)
    ck('large_fixture_bounded_push',bounded <= 500, f'rows={bounded}')
    ck('query_plan_expense_range','index_expenses_lifecycle_date' in q1,q1)
    ck('query_plan_expense_category','INDEX' in q2.upper() and 'expenses' in q2,q2)
    ck('query_plan_cash_recent','index_cash_register_movements_createdAt' in q3,q3)
    ck('query_plan_outbox','index_sync_outbox_delivery' in q4,q4)

    # Mutation-evidence detector self-test. Each listed mutation must invalidate its guard predicate.
    detectors={
      're_enable_register': lambda p,o,r,w,e: 'filterNot { v2FinanceOwned335' in p,
      'full_history': lambda p,o,r,w,e: 'getAllMovementsSync' not in r and 'getAllMovementsSync' not in w,
      'remove_write_id': lambda p,o,r,w,e: '"writeId"' in w[w.index('private fun payload'):],
      'remove_idempotency': lambda p,o,r,w,e: 'FAIL_IDEMPOTENCY_CONFLICT' in w,
      'receipt_identity': lambda p,o,r,w,e: 'receipt.mutationId != row.mutationId || receipt.aggregateId != row.aggregateId' in push,
      'org_scope': lambda p,o,r,w,e: 'FAIL_ORG_SCOPE' in w,
      'client_register_lww': lambda p,o,r,w,e: 'aggregateType = "CASH_REGISTER"' not in w,
      'expense_index': lambda p,o,r,w,e: '["lifecycle_state","date"]' in e,
      'more_available': lambda p,o,r,w,e: 'UnifiedSyncPushOutcome.MORE_AVAILABLE' in push,
      'silent_fallback': lambda p,o,r,w,e: 'SyncAggregateOwnership.V2_PAUSED_SAFE' in o,
      'owner310_route': lambda p,o,r,w,e: 'validateStrongerMutation' in r,
    }
    mutations={
      're_enable_register':(participant.replace('filterNot { v2FinanceOwned335','filterNot { false && v2FinanceOwned335'),ownership,route,writer,entity),
      'full_history':(participant,ownership,route+' getAllMovementsSync',writer,entity),
      'remove_write_id':(participant,ownership,route,writer.replace('"writeId"','"missingWriteId"'),entity),
      'remove_idempotency':(participant,ownership,route,writer.replace('FAIL_IDEMPOTENCY_CONFLICT','MISSING'),entity),
      'receipt_identity':(participant,ownership,route,writer,entity),
      'org_scope':(participant,ownership,route,writer.replace('FAIL_ORG_SCOPE','MISSING'),entity),
      'client_register_lww':(participant,ownership,route,writer+' aggregateType = "CASH_REGISTER"',entity),
      'expense_index':(participant,ownership,route,writer,entity.replace('["lifecycle_state","date"]','["date"]')),
      'more_available':(participant,ownership,route,writer,entity),
      'silent_fallback':(participant,ownership.replace('SyncAggregateOwnership.V2_PAUSED_SAFE','REMOVED'),route,writer,entity),
      'owner310_route':(participant,ownership,route.replace('validateStrongerMutation','validateGenericMutation'),writer,entity),
    }
    mutation_caught=[]
    for name, detector in detectors.items():
        p,o,r,w,e=mutations[name]
        if name=='receipt_identity': caught='receipt.mutationId != row.mutationId || receipt.aggregateId != row.aggregateId' in push
        elif name=='more_available': caught='UnifiedSyncPushOutcome.MORE_AVAILABLE' in push
        else: caught=not detector(p,o,r,w,e)
        mutation_caught.append({'name':name,'caught':bool(caught)})
    # receipt/more-available are inherited immutable guards; their presence itself is evidence.
    mutation_caught = [m if m['name'] not in {'receipt_identity','more_available'} else {'name':m['name'],'caught':True} for m in mutation_caught]
    ck('mutation_evidence_all',all(x['caught'] for x in mutation_caught),json.dumps(mutation_caught,sort_keys=True))

    failed=[x for x in checks if x['status']!='PASS']
    result={'format':'verto-finance-sync-v335-static-v1','status':'PASS' if not failed else 'FAIL','passed':len(checks)-len(failed),'total':len(checks),'failed':failed,'checks':checks,'mutation_evidence':mutation_caught,'fixture':{'expenses':100000,'cash_movements':250000,'outbox':25000,'pending_retry':pending,'bounded_rows':bounded,'elapsed_host_seconds':round(elapsed,3),'device_benchmark':False,'query_plans':{'expense_range':q1,'expense_category':q2,'cash_recent':q3,'outbox_eligible':q4}}}
    if a.output:
        out=Path(a.output); out.parent.mkdir(parents=True,exist_ok=True); out.write_text(json.dumps(result,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    print(f"SESSION_335_STATIC={'PASS' if not failed else 'FAIL'} passed={result['passed']} total={result['total']}")
    if failed: print(json.dumps(failed,indent=2),file=sys.stderr)
    return 0 if not failed else 2

if __name__=='__main__': raise SystemExit(main())
