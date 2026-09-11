package com.verto.app.application.presentationboundary


/**
 * Transitional names for structural services already injected into presentation.
 * They centralize the dependency surface so presentation no longer imports infrastructure.
 * Feature-owned application gateways replace these aliases incrementally.
 */
typealias ClientDirectoryAccess = com.verto.app.data.local.dao.ClientDao
typealias InventoryDataAccess = com.verto.app.data.local.dao.InventoryDao
typealias PriceListDataAccess = com.verto.app.data.local.dao.PriceListDao
typealias ReminderDataAccess = com.verto.app.data.local.dao.ClientReminderDao
typealias ClientCreditAccess = com.verto.app.data.local.dao.ClientCreditDao
typealias CashRegisterAccess = com.verto.app.data.local.dao.CashRegisterDao
typealias InvoiceDataAccess = com.verto.app.data.local.dao.InvoiceDao
typealias PaymentDataAccess = com.verto.app.data.local.dao.PaymentDao

typealias AppPreferencesAccess = com.verto.app.utils.PreferencesManager
typealias DataSyncAccess = com.verto.app.data.sync.SyncManager
typealias CashLedgerAccess = com.verto.app.utils.CashRegisterManager
