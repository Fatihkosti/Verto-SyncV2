package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase


val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS shipments (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'PENDING',
                createdAt INTEGER NOT NULL,
                expectedArrivalDate INTEGER,
                actualArrivalDate INTEGER,
                notes TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS shipment_stops (
                id TEXT PRIMARY KEY NOT NULL,
                shipmentId TEXT NOT NULL,
                `order` INTEGER NOT NULL,
                locationName TEXT NOT NULL,
                arrivedAt INTEGER,
                isDestination INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(shipmentId) REFERENCES shipments(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_shipment_stops_shipmentId ON shipment_stops(shipmentId)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS shipment_costs (
                id TEXT PRIMARY KEY NOT NULL,
                shipmentId TEXT NOT NULL,
                type TEXT NOT NULL,
                amount REAL NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                FOREIGN KEY(shipmentId) REFERENCES shipments(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_shipment_costs_shipmentId ON shipment_costs(shipmentId)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS shipment_receipts (
                id TEXT PRIMARY KEY NOT NULL,
                shipmentId TEXT NOT NULL,
                isComplete INTEGER NOT NULL DEFAULT 1,
                damagedItems TEXT NOT NULL DEFAULT '',
                missingItems TEXT NOT NULL DEFAULT '',
                receivedAt INTEGER NOT NULL,
                receivedBy TEXT NOT NULL DEFAULT '',
                FOREIGN KEY(shipmentId) REFERENCES shipments(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_shipment_receipts_shipmentId ON shipment_receipts(shipmentId)")
        db.execSQL("ALTER TABLE invoices ADD COLUMN shipmentId TEXT")
        db.execSQL("ALTER TABLE invoice_items ADD COLUMN adjustedPurchasePrice REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE inventory_movements ADD COLUMN shipmentId TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE shipments ADD COLUMN shipmentNumber TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE shipments ADD COLUMN origin TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE shipments ADD COLUMN destination TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE shipment_stops ADD COLUMN legCost REAL NOT NULL DEFAULT 0.0")
    }
}

val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS commission_payments (
                id TEXT PRIMARY KEY NOT NULL,
                clientId TEXT NOT NULL,
                clientName TEXT NOT NULL,
                invoiceIds TEXT NOT NULL DEFAULT '',
                totalAmount REAL NOT NULL,
                bankName TEXT NOT NULL DEFAULT '',
                transactionRef TEXT NOT NULL DEFAULT '',
                paidAt INTEGER NOT NULL
            )
        """.trimIndent())
    }
}

// Migration 23 → 24: indexes للأداء
val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_createdAt ON invoices(createdAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_payments_paidAt ON payments(paidAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cash_register_movements_createdAt ON cash_register_movements(createdAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_log_createdAt ON audit_log(createdAt)")
    }
}

// Migration 24 → 25: إضافة جداول التقارير المتقدمة
val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {

        // 1) جدول الأهداف
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS budgets (
                id TEXT PRIMARY KEY NOT NULL,
                periodType TEXT NOT NULL DEFAULT 'MONTHLY',
                periodStart INTEGER NOT NULL,
                periodEnd INTEGER NOT NULL,
                budgetType TEXT NOT NULL DEFAULT 'SALES_TARGET',
                category TEXT NOT NULL DEFAULT '',
                targetAmount REAL NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                isActive INTEGER NOT NULL DEFAULT 1,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_budgets_periodStart ON budgets(periodStart)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_budgets_periodEnd ON budgets(periodEnd)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_budgets_budgetType ON budgets(budgetType)")

        // 2) كاش RFM للعملاء
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS client_rfm_cache (
                clientId TEXT PRIMARY KEY NOT NULL,
                recencyScore INTEGER NOT NULL DEFAULT 0,
                frequencyScore INTEGER NOT NULL DEFAULT 0,
                monetaryScore INTEGER NOT NULL DEFAULT 0,
                daysSinceLastPurchase INTEGER NOT NULL DEFAULT 0,
                totalInvoiceCount INTEGER NOT NULL DEFAULT 0,
                totalSpent REAL NOT NULL DEFAULT 0,
                avgInvoiceValue REAL NOT NULL DEFAULT 0,
                segment TEXT NOT NULL DEFAULT 'NEW_CUSTOMERS',
                totalProfit REAL NOT NULL DEFAULT 0,
                firstPurchaseAt INTEGER NOT NULL DEFAULT 0,
                lastPurchaseAt INTEGER NOT NULL DEFAULT 0,
                customerLifespanDays INTEGER NOT NULL DEFAULT 0,
                calculatedAt INTEGER NOT NULL,
                FOREIGN KEY (clientId) REFERENCES clients(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_client_rfm_cache_segment ON client_rfm_cache(segment)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_client_rfm_cache_calculatedAt ON client_rfm_cache(calculatedAt)")

        // 3) كاش التوقعات
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS forecast_cache (
                id TEXT PRIMARY KEY NOT NULL,
                forecastType TEXT NOT NULL,
                granularity TEXT NOT NULL,
                targetDate INTEGER NOT NULL,
                predictedValue REAL NOT NULL,
                lowerBound REAL NOT NULL DEFAULT 0,
                upperBound REAL NOT NULL DEFAULT 0,
                confidenceLevel REAL NOT NULL DEFAULT 0.8,
                method TEXT NOT NULL DEFAULT 'MOVING_AVERAGE',
                basedOnDataPoints INTEGER NOT NULL DEFAULT 0,
                computedAt INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_forecast_cache_targetDate ON forecast_cache(targetDate)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_forecast_cache_forecastType ON forecast_cache(forecastType)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_forecast_cache_granularity ON forecast_cache(granularity)")

        // 4) توزيع التكاليف
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS cost_allocations (
                id TEXT PRIMARY KEY NOT NULL,
                itemId TEXT NOT NULL,
                sourceType TEXT NOT NULL DEFAULT 'SHIPMENT_COST',
                sourceId TEXT NOT NULL DEFAULT '',
                allocatedAmount REAL NOT NULL,
                perUnitCost REAL NOT NULL DEFAULT 0,
                quantityAffected INTEGER NOT NULL DEFAULT 0,
                method TEXT NOT NULL DEFAULT 'BY_QUANTITY',
                note TEXT NOT NULL DEFAULT '',
                createdAt INTEGER NOT NULL,
                FOREIGN KEY (itemId) REFERENCES inventory_items(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cost_allocations_itemId ON cost_allocations(itemId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cost_allocations_sourceId ON cost_allocations(sourceId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cost_allocations_createdAt ON cost_allocations(createdAt)")

        // 5) جلسات تسوية الصندوق
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS cash_reconciliation_sessions (
                id TEXT PRIMARY KEY NOT NULL,
                employeeId TEXT NOT NULL DEFAULT '',
                employeeName TEXT NOT NULL DEFAULT '',
                openingBalance REAL NOT NULL DEFAULT 0,
                totalSales REAL NOT NULL DEFAULT 0,
                totalRefunds REAL NOT NULL DEFAULT 0,
                totalCashIn REAL NOT NULL DEFAULT 0,
                totalCashOut REAL NOT NULL DEFAULT 0,
                expectedBalance REAL NOT NULL DEFAULT 0,
                actualCountedBalance REAL NOT NULL DEFAULT 0,
                variance REAL NOT NULL DEFAULT 0,
                varianceReason TEXT NOT NULL DEFAULT '',
                status TEXT NOT NULL DEFAULT 'OPEN',
                startedAt INTEGER NOT NULL,
                endedAt INTEGER,
                notes TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cash_reconciliation_sessions_startedAt ON cash_reconciliation_sessions(startedAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cash_reconciliation_sessions_endedAt ON cash_reconciliation_sessions(endedAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cash_reconciliation_sessions_employeeId ON cash_reconciliation_sessions(employeeId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cash_reconciliation_sessions_status ON cash_reconciliation_sessions(status)")

        // 6) تفصيل الفئات النقدية
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS cash_denominations (
                id TEXT PRIMARY KEY NOT NULL,
                reconciliationId TEXT NOT NULL,
                denominationValue REAL NOT NULL,
                count INTEGER NOT NULL DEFAULT 0,
                subtotal REAL NOT NULL DEFAULT 0,
                isCoin INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY (reconciliationId) REFERENCES cash_reconciliation_sessions(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cash_denominations_reconciliationId ON cash_denominations(reconciliationId)")
    }
}

// Migration 25 → 26: إضافة جدول أكواد الربط مع AutoDrive
