package com.verto.app.utils
import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class PreferencesManager(private val context: Context) {

    private val syncStore = SyncPreferencesStore(context)
    private val pinStore = PinPreferencesStore(context)

    // ── Flows: بيانات المستخدم والمحل ─────────────────────────────────────────

    val shopName:      Flow<String> = context.dataStore.data.map { it[KEY_SHOP_NAME]      ?: "" }
    val shopPhone:     Flow<String> = context.dataStore.data.map { it[KEY_SHOP_PHONE]     ?: "" }
    val orgAddress:    Flow<String> = context.dataStore.data.map { it[KEY_ORG_ADDRESS]    ?: "" }
    val userId:        Flow<String> = context.dataStore.data.map { it[KEY_USER_ID]        ?: "" }
    val userName:      Flow<String> = context.dataStore.data.map { it[KEY_USER_NAME]      ?: "" }
    val userPhone:     Flow<String> = context.dataStore.data.map { it[KEY_USER_PHONE]     ?: "" }
    val ownerName:     Flow<String> = context.dataStore.data.map { it[KEY_OWNER_NAME]     ?: "" }
    val expenseTarget: Flow<Double> = context.dataStore.data.map { it[KEY_EXPENSE_TARGET] ?: 0.0 }
    val passwordRecoveryPending: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_PASSWORD_RECOVERY_PENDING] ?: false
    }
    /** دور المستخدم المخزَّن محلياً (admin | accountant | sales | warehouse) لعرض الواجهة دون اتصال. */
    val userRole:      Flow<String> = context.dataStore.data.map { it[KEY_USER_ROLE]      ?: "" }
    /** صلاحيات الموظف الحالي مُسلسَلة JSON — كاش محلي للفرض دون اتصال (الجلسة 3). فارغ = غير محمَّل. */
    val userPermissionsJson: Flow<String> = context.dataStore.data.map { it[KEY_USER_PERMISSIONS] ?: "" }
    /** معرّف المؤسسة المرتبط بآخر جلسة ناجحة. */
    val lastOrgId: Flow<String> = context.dataStore.data.map { it[KEY_LAST_ORG_ID] ?: "" }
    /** آخر قسم مفتوح في القائمة الجانبية؛ فارغ يعني أن جميع الأقسام مغلقة. */
    val lastDrawerOpenSection: Flow<String> = context.dataStore.data.map {
        it[KEY_DRAWER_LAST_OPEN_SECTION] ?: ""
    }

    // ── Flows: PIN ─────────────────────────────────────────────────────────────

    val isPinEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_PIN_ENABLED]   ?: false }
    val recoveryCode: Flow<String>  = context.dataStore.data.map { it[KEY_RECOVERY_CODE] ?: "" }

    // ── Flows: المظهر والثيمات ─────────────────────────────────────────────────

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map {
        runCatching { ThemeMode.valueOf(it[KEY_THEME] ?: "") }.getOrDefault(ThemeMode.AUTO)
    }
    val selectedTheme: Flow<String>      = context.dataStore.data.map { it[KEY_SELECTED_THEME] ?: "default" }
    val appFontSize:   Flow<AppFontSize> = context.dataStore.data.map {
        runCatching { AppFontSize.valueOf(it[KEY_APP_FONT_SIZE] ?: "") }.getOrDefault(AppFontSize.MEDIUM)
    }

    // ── Flows: الفواتير — تفضيلات الجهاز ──────────────────────────────────────

    /** محفوظ للتوافق مع InvoiceScreen الحالية */
    val invoiceStyle: Flow<InvoiceStyle> = context.dataStore.data.map {
        runCatching { InvoiceStyle.valueOf(it[KEY_INVOICE_STYLE] ?: "") }.getOrDefault(InvoiceStyle.FULL)
    }

    /** شكل الفاتورة الجديد — يُخزَّن محلياً لكل موظف */
    val invoiceTemplate: Flow<InvoiceTemplate> = context.dataStore.data.map {
        runCatching { InvoiceTemplate.valueOf(it[KEY_INVOICE_TEMPLATE] ?: "") }.getOrDefault(InvoiceTemplate.CLASSIC)
    }

    /** خط الفاتورة — يُخزَّن محلياً لكل موظف */
    val invoiceFont: Flow<InvoiceFont> = context.dataStore.data.map {
        runCatching { InvoiceFont.valueOf(it[KEY_INVOICE_FONT] ?: "") }.getOrDefault(InvoiceFont.CAIRO)
    }

    val invoiceFontSize: Flow<Int>         = context.dataStore.data.map { it[KEY_INVOICE_FONT_SIZE] ?: 14 }
    val invoiceColumns:  Flow<Set<String>> = context.dataStore.data.map { it[KEY_INVOICE_COLUMNS]   ?: emptySet() }

    // ── Flows: كشف أسعار ──────────────────────────────────────────────────────────
    val priceListFont:     Flow<InvoiceFont> = context.dataStore.data.map {
        runCatching { InvoiceFont.valueOf(it[KEY_PRICELIST_FONT] ?: "") }.getOrDefault(InvoiceFont.CAIRO)
    }
    val priceListFontSize: Flow<Int> = context.dataStore.data.map { it[KEY_PRICELIST_FONT_SIZE] ?: 14 }

    // ── Flows: كشف حساب ───────────────────────────────────────────────────────────

    val statementTemplate: Flow<InvoiceTemplate> = context.dataStore.data.map {
        runCatching { InvoiceTemplate.valueOf(it[KEY_STATEMENT_TEMPLATE] ?: "") }.getOrDefault(InvoiceTemplate.CLASSIC)
    }
    val statementFont:     Flow<InvoiceFont> = context.dataStore.data.map {
        runCatching { InvoiceFont.valueOf(it[KEY_STATEMENT_FONT] ?: "") }.getOrDefault(InvoiceFont.CAIRO)
    }
    val statementFontSize: Flow<Int> = context.dataStore.data.map { it[KEY_STATEMENT_FONT_SIZE] ?: 14 }

    // ── Flows: المخزون ────────────────────────────────────────────────────────────

    val inventoryTemplate: Flow<InvoiceTemplate> = context.dataStore.data.map {
        runCatching { InvoiceTemplate.valueOf(it[KEY_INVENTORY_TEMPLATE] ?: "") }.getOrDefault(InvoiceTemplate.CLASSIC)
    }
    val inventoryFont:     Flow<InvoiceFont> = context.dataStore.data.map {
        runCatching { InvoiceFont.valueOf(it[KEY_INVENTORY_FONT] ?: "") }.getOrDefault(InvoiceFont.CAIRO)
    }
    val inventoryFontSize: Flow<Int> = context.dataStore.data.map { it[KEY_INVENTORY_FONT_SIZE] ?: 14 }

    // ── Flows: التقارير ───────────────────────────────────────────────────────────

    val reportsTemplate: Flow<InvoiceTemplate> = context.dataStore.data.map {
        runCatching { InvoiceTemplate.valueOf(it[KEY_REPORTS_TEMPLATE] ?: "") }.getOrDefault(InvoiceTemplate.CLASSIC)
    }
    val reportsFont:     Flow<InvoiceFont> = context.dataStore.data.map {
        runCatching { InvoiceFont.valueOf(it[KEY_REPORTS_FONT] ?: "") }.getOrDefault(InvoiceFont.CAIRO)
    }
    val reportsFontSize: Flow<Int> = context.dataStore.data.map { it[KEY_REPORTS_FONT_SIZE] ?: 14 }

    // ── Flows: الإشعارات ───────────────────────────────────────────────────────

    val notifClients:       Flow<Boolean> = context.dataStore.data.map { it[KEY_NOTIF_CLIENTS]        ?: true  }
    val notifInventory:     Flow<Boolean> = context.dataStore.data.map { it[KEY_NOTIF_INVENTORY]      ?: true  }
    val notifInvoices:      Flow<Boolean> = context.dataStore.data.map { it[KEY_NOTIF_INVOICES]       ?: true  }
    val allowNegativeStock: Flow<Boolean> = context.dataStore.data.map { it[KEY_ALLOW_NEGATIVE_STOCK] ?: false }
    /** سياسة الصندوق السالب (الجلسة 8). الافتراضي true (غير كاسر)؛ اضبطه false للمنع الصارم بعد إدخال رصيد افتتاحي. */
    val allowCashOverdraft: Flow<Boolean> = context.dataStore.data.map { it[KEY_ALLOW_CASH_OVERDRAFT] ?: true }

    // ── Flows: كاش بيانات المؤسسة ──────────────────────────────────────────────

    val orgShopName:      Flow<String> = context.dataStore.data.map { it[KEY_ORG_SHOP_NAME]      ?: "" }
    val orgShopPhone:     Flow<String> = context.dataStore.data.map { it[KEY_ORG_SHOP_PHONE]     ?: "" }
    val orgCity:          Flow<String> = context.dataStore.data.map { it[KEY_ORG_CITY]           ?: "" }
    val orgAddress2:      Flow<String> = context.dataStore.data.map { it[KEY_ORG_ADDRESS2]       ?: "" }
    val orgCurrency:      Flow<String> = context.dataStore.data.map { it[KEY_ORG_CURRENCY]       ?: "" }
    val orgInvoiceFooter: Flow<String> = context.dataStore.data.map { it[KEY_ORG_INVOICE_FOOTER] ?: "" }
    val orgTaxNumber:     Flow<String> = context.dataStore.data.map { it[KEY_ORG_TAX_NUMBER]     ?: "" }
    val orgLogoUrl:       Flow<String> = context.dataStore.data.map { it[KEY_ORG_LOGO_URL]       ?: "" }
    val orgSignatureUrl:  Flow<String> = context.dataStore.data.map { it[KEY_ORG_SIGNATURE_URL]  ?: "" }

    // ── Setters: بيانات المستخدم والمحل ───────────────────────────────────────

    suspend fun setShopName(v: String)      { context.dataStore.edit { it[KEY_SHOP_NAME]      = v } }
    suspend fun setShopPhone(v: String)     { context.dataStore.edit { it[KEY_SHOP_PHONE]     = v } }
    suspend fun setOrgAddress(v: String)    { context.dataStore.edit { it[KEY_ORG_ADDRESS]    = v } }
    suspend fun setUserId(v: String)        { context.dataStore.edit { it[KEY_USER_ID]        = v } }
    suspend fun setUserName(v: String)      { context.dataStore.edit { it[KEY_USER_NAME]      = v } }
    suspend fun setUserPhone(v: String)     { context.dataStore.edit { it[KEY_USER_PHONE]     = v } }
    suspend fun setOwnerName(v: String)     { context.dataStore.edit { it[KEY_OWNER_NAME]     = v } }
    suspend fun setExpenseTarget(v: Double) { context.dataStore.edit { it[KEY_EXPENSE_TARGET] = v } }

    suspend fun markPasswordRecoveryVerified(email: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PASSWORD_RECOVERY_PENDING] = true
            prefs[KEY_PASSWORD_RECOVERY_EMAIL] = email.trim().lowercase()
        }
    }

    suspend fun isPasswordRecoveryPending(): Boolean =
        context.dataStore.data.first()[KEY_PASSWORD_RECOVERY_PENDING] ?: false

    suspend fun passwordRecoveryEmail(): String =
        context.dataStore.data.first()[KEY_PASSWORD_RECOVERY_EMAIL].orEmpty()

    suspend fun clearPasswordRecoveryState() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_PASSWORD_RECOVERY_PENDING)
            prefs.remove(KEY_PASSWORD_RECOVERY_EMAIL)
        }
    }
    suspend fun setUserRole(v: String)      { context.dataStore.edit { it[KEY_USER_ROLE]      = v } }
    suspend fun setUserPermissionsJson(v: String) { context.dataStore.edit { it[KEY_USER_PERMISSIONS] = v } }
    suspend fun setLastDrawerOpenSection(v: String) {
        context.dataStore.edit { it[KEY_DRAWER_LAST_OPEN_SECTION] = v }
    }

    // ── Setters: المظهر والثيمات ───────────────────────────────────────────────

    suspend fun setThemeMode(v: ThemeMode)     { context.dataStore.edit { it[KEY_THEME]          = v.name } }
    suspend fun setSelectedTheme(v: String)    { context.dataStore.edit { it[KEY_SELECTED_THEME] = v } }
    suspend fun setAppFontSize(v: AppFontSize) { context.dataStore.edit { it[KEY_APP_FONT_SIZE]  = v.name } }

    val subscriptionTier: Flow<String> = context.dataStore.data.map { it[KEY_SUBSCRIPTION_TIER] ?: "FREE" }
    suspend fun setSubscriptionTier(tier: String) { context.dataStore.edit { it[KEY_SUBSCRIPTION_TIER] = tier } }

    // ── Setters: الفواتير ──────────────────────────────────────────────────────

    suspend fun setInvoiceStyle(v: InvoiceStyle)      { context.dataStore.edit { it[KEY_INVOICE_STYLE]    = v.name } }
    suspend fun setInvoiceTemplate(v: InvoiceTemplate){ context.dataStore.edit { it[KEY_INVOICE_TEMPLATE] = v.name } }
    suspend fun setInvoiceFont(v: InvoiceFont)        { context.dataStore.edit { it[KEY_INVOICE_FONT]     = v.name } }
    suspend fun setInvoiceFontSize(v: Int)            { context.dataStore.edit { it[KEY_INVOICE_FONT_SIZE]= v } }
    suspend fun setInvoiceColumns(v: Set<String>)      { context.dataStore.edit { it[KEY_INVOICE_COLUMNS]     = v } }

    // ── Setters: كشف أسعار ────────────────────────────────────────────────────────

    suspend fun setPriceListFont(v: InvoiceFont)                { context.dataStore.edit { it[KEY_PRICELIST_FONT]       = v.name } }
    suspend fun setPriceListFontSize(v: Int)                    { context.dataStore.edit { it[KEY_PRICELIST_FONT_SIZE]  = v      } }

    // ── Setters: كشف حساب ─────────────────────────────────────────────────────────

    suspend fun setStatementTemplate(v: InvoiceTemplate) { context.dataStore.edit { it[KEY_STATEMENT_TEMPLATE]  = v.name } }
    suspend fun setStatementFont(v: InvoiceFont)         { context.dataStore.edit { it[KEY_STATEMENT_FONT]       = v.name } }
    suspend fun setStatementFontSize(v: Int)             { context.dataStore.edit { it[KEY_STATEMENT_FONT_SIZE]  = v      } }

    // ── Setters: المخزون ──────────────────────────────────────────────────────────

    suspend fun setInventoryTemplate(v: InvoiceTemplate) { context.dataStore.edit { it[KEY_INVENTORY_TEMPLATE]  = v.name } }
    suspend fun setInventoryFont(v: InvoiceFont)         { context.dataStore.edit { it[KEY_INVENTORY_FONT]       = v.name } }
    suspend fun setInventoryFontSize(v: Int)             { context.dataStore.edit { it[KEY_INVENTORY_FONT_SIZE]  = v      } }

    // ── Setters: التقارير ─────────────────────────────────────────────────────────

    suspend fun setReportsTemplate(v: InvoiceTemplate) { context.dataStore.edit { it[KEY_REPORTS_TEMPLATE]  = v.name } }
    suspend fun setReportsFont(v: InvoiceFont)         { context.dataStore.edit { it[KEY_REPORTS_FONT]       = v.name } }
    suspend fun setReportsFontSize(v: Int)             { context.dataStore.edit { it[KEY_REPORTS_FONT_SIZE]  = v      } }

    // ── Setters: الإشعارات ─────────────────────────────────────────────────────

    suspend fun setNotifClients(v: Boolean)       { context.dataStore.edit { it[KEY_NOTIF_CLIENTS]        = v } }
    suspend fun setNotifInventory(v: Boolean)     { context.dataStore.edit { it[KEY_NOTIF_INVENTORY]      = v } }
    suspend fun setNotifInvoices(v: Boolean)      { context.dataStore.edit { it[KEY_NOTIF_INVOICES]       = v } }
    suspend fun setAllowNegativeStock(v: Boolean) { context.dataStore.edit { it[KEY_ALLOW_NEGATIVE_STOCK] = v } }
    suspend fun setAllowCashOverdraft(v: Boolean) { context.dataStore.edit { it[KEY_ALLOW_CASH_OVERDRAFT] = v } }

    // ── Setters: كاش بيانات المؤسسة ────────────────────────────────────────────

    suspend fun cacheOrgSettings(
        shopName      : String,
        shopPhone     : String,
        city          : String,
        address       : String,
        currency      : String,
        invoiceFooter : String,
        taxNumber     : String,
        logoUrl       : String,
        signatureUrl  : String
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ORG_SHOP_NAME]      = shopName
            prefs[KEY_ORG_SHOP_PHONE]     = shopPhone
            prefs[KEY_ORG_CITY]           = city
            prefs[KEY_ORG_ADDRESS2]       = address
            prefs[KEY_ORG_CURRENCY]       = currency
            prefs[KEY_ORG_INVOICE_FOOTER] = invoiceFooter
            prefs[KEY_ORG_TAX_NUMBER]     = taxNumber
            prefs[KEY_ORG_LOGO_URL]       = logoUrl
            prefs[KEY_ORG_SIGNATURE_URL]  = signatureUrl
        }
    }

    // ── Pending Deletions — مزامنة ─────────────────────────────────────────────

    // ── مزامنة وحذف مؤجل ────────────────────────────────────────────────────
    suspend fun getPendingInvoiceDeletions() = syncStore.getPendingInvoiceDeletions()
    suspend fun addPendingInvoiceDeletion(id: String) = syncStore.addPendingInvoiceDeletion(id)
    suspend fun removePendingInvoiceDeletion(id: String) = syncStore.removePendingInvoiceDeletion(id)
    suspend fun clearPendingInvoiceDeletions() = syncStore.clearPendingInvoiceDeletions()
    suspend fun getPendingClientDeletions() = syncStore.getPendingClientDeletions()
    suspend fun addPendingClientDeletion(id: String) = syncStore.addPendingClientDeletion(id)
    suspend fun removePendingClientDeletion(id: String) = syncStore.removePendingClientDeletion(id)
    suspend fun getPendingInventoryDeletions() = syncStore.getPendingInventoryDeletions()
    suspend fun addPendingInventoryDeletion(id: String) = syncStore.addPendingInventoryDeletion(id)
    suspend fun removePendingInventoryDeletion(id: String) = syncStore.removePendingInventoryDeletion(id)
    suspend fun getPendingExpenseDeletions() = syncStore.getPendingExpenseDeletions()
    suspend fun addPendingExpenseDeletion(id: String) = syncStore.addPendingExpenseDeletion(id)
    suspend fun removePendingExpenseDeletion(id: String) = syncStore.removePendingExpenseDeletion(id)
    suspend fun getPendingCategoryDeletions() = syncStore.getPendingCategoryDeletions()
    suspend fun addPendingCategoryDeletion(id: String) = syncStore.addPendingCategoryDeletion(id)
    suspend fun removePendingCategoryDeletion(id: String) = syncStore.removePendingCategoryDeletion(id)
    suspend fun getPendingCommissionDeletions() = syncStore.getPendingCommissionDeletions()
    suspend fun addPendingCommissionDeletion(id: String) = syncStore.addPendingCommissionDeletion(id)
    suspend fun removePendingCommissionDeletion(id: String) = syncStore.removePendingCommissionDeletion(id)
    suspend fun getPendingUnitDeletions() = syncStore.getPendingUnitDeletions()
    suspend fun addPendingUnitDeletion(id: String) = syncStore.addPendingUnitDeletion(id)
    suspend fun removePendingUnitDeletion(id: String) = syncStore.removePendingUnitDeletion(id)
    suspend fun getPendingBudgetDeletions() = syncStore.getPendingBudgetDeletions()
    suspend fun addPendingBudgetDeletion(id: String) = syncStore.addPendingBudgetDeletion(id)
    suspend fun removePendingBudgetDeletion(id: String) = syncStore.removePendingBudgetDeletion(id)
    suspend fun getPendingReconciliationDeletions() = syncStore.getPendingReconciliationDeletions()
    suspend fun addPendingReconciliationDeletion(id: String) = syncStore.addPendingReconciliationDeletion(id)
    suspend fun removePendingReconciliationDeletion(id: String) = syncStore.removePendingReconciliationDeletion(id)
    suspend fun getLastPulledAt(key: Preferences.Key<Long>) = syncStore.getLastPulledAt(key)
    suspend fun setLastPulledAt(key: Preferences.Key<Long>, timestamp: Long) = syncStore.setLastPulledAt(key, timestamp)
    suspend fun getSyncV2Cursor(orgId: String) = syncStore.getSyncV2Cursor(orgId)
    suspend fun setSyncV2Cursor(orgId: String, revision: Long) = syncStore.setSyncV2Cursor(orgId, revision)
    suspend fun beginOrResumeSyncRun(orgId: String, startingRevision: Long) =
        syncStore.beginOrResumeSyncRun(orgId, startingRevision)
    suspend fun markSyncOperationCompleted(orgId: String, runId: String, operationKey: String) =
        syncStore.markSyncOperationCompleted(orgId, runId, operationKey)
    suspend fun completeSyncRun(orgId: String, runId: String) =
        syncStore.completeSyncRun(orgId, runId)
    suspend fun getPersistedSyncReport(orgId: String, userId: String) =
        syncStore.getPersistedSyncReport(orgId, userId)
    suspend fun setPersistedSyncReport(orgId: String, userId: String, reportJson: String) =
        syncStore.setPersistedSyncReport(orgId, userId, reportJson)
    suspend fun getLastOrgId() = syncStore.getLastOrgId()
    suspend fun setLastOrgId(orgId: String) = syncStore.setLastOrgId(orgId)
    suspend fun getSessionEpoch() = syncStore.getSessionEpoch()
    suspend fun activateNextSessionEpoch() = syncStore.activateNextSessionEpoch()
    suspend fun clearSessionData() = syncStore.clearSessionData()
    suspend fun migrateFromSharedPreferences() = syncStore.migrateFromSharedPreferences()

    // ── PIN ──────────────────────────────────────────────────────────────────
    suspend fun setPin(pin: String) = pinStore.setPin(pin)
    suspend fun validatePin(pin: String) = pinStore.validatePin(pin)
    suspend fun disablePin() = pinStore.disablePin()
    suspend fun resetWithRecoveryCode(code: String, newPin: String) = pinStore.resetWithRecoveryCode(code, newPin)
}
