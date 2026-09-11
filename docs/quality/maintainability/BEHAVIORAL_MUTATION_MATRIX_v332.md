# Behavioral Mutation Matrix v332

Status: PASS

| ID | Risk | Detecting test | Result |
| --- | --- | --- | --- |
| M1 | invoice validation bypass | InvoiceWriteCoordinatorTest.validation failure blocks persistence side effects | DETECTED |
| M2 | invoice authorization bypass | InvoiceWriteCoordinatorTest.authorization rejection blocks persistence and post commit effects | DETECTED |
| M3 | post-commit before persistence | InvoiceWriteCoordinatorTest.create path preserves persistence then post commit ordering | DETECTED |
| M4 | inventory side effect skipped | LogisticsV242EndToEndIntegrationTest.accepted invoice quantity reaches inventory | DETECTED |
| M5 | payment side effect skipped | PaymentCoordinatorsTest.sale payment is persisted and enters cash | DETECTED |
| M6 | sync wake before persistence | SyncManagerTest.request sync persists generation before wake | DETECTED |
| M7 | sync continuation dropped | SyncManagerTest.continuation scheduling is routed through scheduler seam | DETECTED |
| M8 | invalid logistics transition accepted | LogisticsPoliciesTest.shipment lifecycle allows only declared transitions | DETECTED |

Summary: 8/8 detected; undetected = 0.
