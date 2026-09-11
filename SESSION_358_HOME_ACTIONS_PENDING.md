# SESSION 358 — Home Quick Actions & Pending Actions

## Source of Truth
- Input: `Verto-v357-invoice-flow-repaired.zip`
- Output: `Verto-v358-home-actions-pending.zip`

## Implemented
- Home quick actions fixed to: فاتورة بيع، فاتورة شراء، فاتورة شراء دولية، إضافة عميل، إضافة مورد، إضافة صنف، إضافة مصروف، كشف أسعار.
- Removed Home quick-action organizer and persisted-order execution path.
- Removed obsolete Home payment quick-action contribution.
- Removed obsolete quick-stock Home action implementation and its now-unused dedicated files.
- Home pending section shows one actionable card and rotates every 5 minutes.
- `عرض المزيد` opens three tabs: عملاء، موردون، مخزون.
- Customer and supplier events remain individual records/cards.
- Inventory is grouped into exactly three condition events when present: نافد تمامًا، أوشك على النفاد، راكد.
- Opening an inventory condition exposes all matching items through the pending-action details model.
- Non-customer/supplier/inventory pending domains are not shown in this new tabbed Home surface.

## Verification
- `strings.xml`: XML parse PASS.
- Session-358 source invariants: PASS.
- Legacy Home organizer/payment-provider/quick-stock production references: absent.
- Added inventory aggregation regression test.
- Gradle compile attempt: NOT RUN to compilation phase. Wrapper requires Gradle 8.9 distribution, which is not cached locally; offline environment cannot download `services.gradle.org` (`UnknownHostException`).

## Scope
No changes were made to the idea-capture, educational-content, or recent-activity redesign planned for later contracts.
