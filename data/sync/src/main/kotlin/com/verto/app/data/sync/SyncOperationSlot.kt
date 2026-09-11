package com.verto.app.data.sync

/**
 * المصدر المركزي لخانات ترتيب خطة المزامنة.
 *
 * يجب أن تكون قيمة [order] فريدة داخل [stage]. استخدام هذه الخانات بدل الأرقام السحرية
 * يجعل ترتيب الاعتماديات مرئيًا وقابلًا للتحقق آليًا.
 */
enum class SyncOperationSlot(
    val stage: SyncStage,
    val order: Int,
) {
    // PUSH: parents/owners before their dependent rows and late integrations last.
    PUSH_CLIENTS(SyncStage.PUSH, 10),
    PUSH_PURCHASE_CYCLE_PRE_INVOICES(SyncStage.PUSH, 12),
    PUSH_FINANCIAL_OUTBOX(SyncStage.PUSH, 15),
    PUSH_INVOICES(SyncStage.PUSH, 20),
    PUSH_INVOICE_ITEMS(SyncStage.PUSH, 30),
    PUSH_PURCHASE_CYCLE_POST_INVOICES(SyncStage.PUSH, 35),
    PUSH_INVENTORY_UNITS(SyncStage.PUSH, 40),
    PUSH_INVENTORY(SyncStage.PUSH, 50),
    PUSH_ITEM_CATEGORIES(SyncStage.PUSH, 60),
    PUSH_COST_ALLOCATIONS(SyncStage.PUSH, 70),
    PUSH_PAYMENTS(SyncStage.PUSH, 80),
    PUSH_CLIENT_CREDITS(SyncStage.PUSH, 90),
    PUSH_EXPENSES(SyncStage.PUSH, 100),
    PUSH_BUDGETS(SyncStage.PUSH, 110),
    PUSH_CASH_RECONCILIATION(SyncStage.PUSH, 120),
    PUSH_NOTES(SyncStage.PUSH, 130),
    PUSH_REMINDERS(SyncStage.PUSH, 140),
    PUSH_INVENTORY_CATEGORIES(SyncStage.PUSH, 160),
    // Deliberately follows inventory categories and precedes logistics/cash integrations.
    PUSH_ORGANIZATION_SETTINGS(SyncStage.PUSH, 170),
    PUSH_LOGISTICS_V2(SyncStage.PUSH, 185),
    PUSH_CASH_REGISTER(SyncStage.PUSH, 190),
    PUSH_OPTIMAL_OUTBOX(SyncStage.PUSH, 200),
    PUSH_EDUCATIONAL_CONTENT(SyncStage.PUSH, 210),
    PUSH_TEAM_OBSERVATIONS(SyncStage.PUSH, 215),

    // DELETE: destructive propagation happens only after all PUSH operations complete.
    DELETE_INVOICES(SyncStage.DELETE, 10),
    DELETE_CLIENTS(SyncStage.DELETE, 20),
    DELETE_EXPENSES(SyncStage.DELETE, 30),
    DELETE_INVENTORY_CATEGORIES(SyncStage.DELETE, 50),
    DELETE_EDUCATIONAL_CONTENT(SyncStage.DELETE, 80),

    // PULL: parent/reference data before dependent projections where required.
    PULL_CLIENTS(SyncStage.PULL, 10),
    PULL_PURCHASE_CYCLE_PRE_INVOICES(SyncStage.PULL, 12),
    PULL_FINANCIAL_INBOX(SyncStage.PULL, 15),
    PULL_INVOICES(SyncStage.PULL, 20),
    PULL_INVENTORY_UNITS(SyncStage.PULL, 30),
    PULL_INVENTORY(SyncStage.PULL, 40),
    PULL_ITEM_CATEGORIES(SyncStage.PULL, 50),
    PULL_COST_ALLOCATIONS(SyncStage.PULL, 60),
    PULL_INVOICE_ITEMS(SyncStage.PULL, 70),
    PULL_PURCHASE_CYCLE_POST_INVOICES(SyncStage.PULL, 75),
    PULL_PAYMENTS(SyncStage.PULL, 80),
    PULL_FINANCIAL_RECONCILIATION(SyncStage.PULL, 85),
    PULL_CLIENT_CREDITS(SyncStage.PULL, 90),
    PULL_EXPENSES(SyncStage.PULL, 100),
    PULL_NOTES(SyncStage.PULL, 110),
    PULL_REMINDERS(SyncStage.PULL, 120),
    PULL_BUDGETS(SyncStage.PULL, 130),
    PULL_CASH_RECONCILIATION(SyncStage.PULL, 140),
    PULL_INVENTORY_MOVEMENTS(SyncStage.PULL, 160),
    PULL_INVENTORY_COST_REVISIONS(SyncStage.PULL, 165),
    PULL_INVENTORY_CATEGORIES(SyncStage.PULL, 170),
    PULL_COMMISSIONS(SyncStage.PULL, 180),
    PULL_LOGISTICS_V2(SyncStage.PULL, 195),
    PULL_CASH_REGISTER(SyncStage.PULL, 200),
    PULL_NOTIFICATIONS(SyncStage.PULL, 210),
    PULL_ORGANIZATION_SETTINGS(SyncStage.PULL, 220),
    PULL_EDUCATIONAL_CONTENT(SyncStage.PULL, 230),
    PULL_TEAM_OBSERVATIONS(SyncStage.PULL, 235),
}
