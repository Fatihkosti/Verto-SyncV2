package com.verto.app.utils

import androidx.datastore.preferences.core.*

// ── بيانات المستخدم والمحل ────────────────────────────────────────────
val KEY_SHOP_NAME      = stringPreferencesKey("shop_name")
val KEY_SHOP_PHONE     = stringPreferencesKey("shop_phone")
val KEY_ORG_ADDRESS    = stringPreferencesKey("org_address")
val KEY_USER_ID        = stringPreferencesKey("user_id")
val KEY_USER_NAME      = stringPreferencesKey("user_name")
val KEY_USER_PHONE     = stringPreferencesKey("user_phone")
val KEY_OWNER_NAME     = stringPreferencesKey("owner_name")
val KEY_EXPENSE_TARGET = doublePreferencesKey("expense_target")
val KEY_PASSWORD_RECOVERY_PENDING = booleanPreferencesKey("password_recovery_pending")
val KEY_PASSWORD_RECOVERY_EMAIL   = stringPreferencesKey("password_recovery_email")

// ── PIN ───────────────────────────────────────────────────────────────
val KEY_PIN_HASH           = stringPreferencesKey("pin_hash")
val KEY_PIN_SALT           = stringPreferencesKey("pin_salt")
val KEY_RECOVERY_CODE      = stringPreferencesKey("recovery_code")
val KEY_RECOVERY_CODE_SALT = stringPreferencesKey("recovery_code_salt")
val KEY_PIN_ENABLED        = booleanPreferencesKey("pin_enabled")

// ── المظهر والثيمات ───────────────────────────────────────────────────
val KEY_THEME              = stringPreferencesKey("theme")
val KEY_SELECTED_THEME     = stringPreferencesKey("selected_theme")
val KEY_APP_FONT_SIZE      = stringPreferencesKey("app_font_size")
val KEY_SUBSCRIPTION_TIER  = stringPreferencesKey("subscription_tier")
val KEY_USER_ROLE          = stringPreferencesKey("user_role")
val KEY_USER_PERMISSIONS   = stringPreferencesKey("user_permissions")
val KEY_DRAWER_LAST_OPEN_SECTION = stringPreferencesKey("drawer_last_open_section")

// ── الفواتير — تفضيلات الجهاز ─────────────────────────────────────────
val KEY_INVOICE_STYLE        = stringPreferencesKey("invoice_style")
val KEY_INVOICE_TEMPLATE     = stringPreferencesKey("invoice_template")
val KEY_INVOICE_FONT         = stringPreferencesKey("invoice_font")
val KEY_INVOICE_FONT_SIZE    = intPreferencesKey("invoice_font_size")
val KEY_INVOICE_COLUMNS      = stringSetPreferencesKey("invoice_columns")

// ── كشف أسعار — تفضيلات الطباعة ─────────────────────────────────────────
val KEY_PRICELIST_FONT      = stringPreferencesKey("pricelist_font")
val KEY_PRICELIST_FONT_SIZE = intPreferencesKey("pricelist_font_size")

// ── كشف حساب — تفضيلات الطباعة ──────────────────────────────────────────
val KEY_STATEMENT_TEMPLATE  = stringPreferencesKey("statement_template")
val KEY_STATEMENT_FONT      = stringPreferencesKey("statement_font")
val KEY_STATEMENT_FONT_SIZE = intPreferencesKey("statement_font_size")

// ── المخزون — تفضيلات الطباعة ────────────────────────────────────────────
val KEY_INVENTORY_TEMPLATE  = stringPreferencesKey("inventory_template")
val KEY_INVENTORY_FONT      = stringPreferencesKey("inventory_font")
val KEY_INVENTORY_FONT_SIZE = intPreferencesKey("inventory_font_size")

// ── التقارير — تفضيلات الطباعة ───────────────────────────────────────────
val KEY_REPORTS_TEMPLATE  = stringPreferencesKey("reports_template")
val KEY_REPORTS_FONT      = stringPreferencesKey("reports_font")
val KEY_REPORTS_FONT_SIZE = intPreferencesKey("reports_font_size")

// ── الإشعارات ──────────────────────────────────────────────────────────
val KEY_NOTIF_CLIENTS   = booleanPreferencesKey("notif_clients")
val KEY_NOTIF_INVENTORY = booleanPreferencesKey("notif_inventory")
val KEY_NOTIF_INVOICES  = booleanPreferencesKey("notif_invoices")

// ── كاش بيانات المؤسسة (مصدرها Supabase — يُخزن محلياً للعرض السريع) ──
val KEY_ORG_SHOP_NAME      = stringPreferencesKey("org_shop_name")
val KEY_ORG_SHOP_PHONE     = stringPreferencesKey("org_shop_phone")
val KEY_ORG_CITY           = stringPreferencesKey("org_city")
val KEY_ORG_ADDRESS2       = stringPreferencesKey("org_address2")
val KEY_ORG_CURRENCY       = stringPreferencesKey("org_currency")
val KEY_ORG_INVOICE_FOOTER = stringPreferencesKey("org_invoice_footer")
val KEY_ORG_TAX_NUMBER     = stringPreferencesKey("org_tax_number")
val KEY_ORG_LOGO_URL       = stringPreferencesKey("org_logo_url")
val KEY_ORG_SIGNATURE_URL  = stringPreferencesKey("org_signature_url")

// ── مزامنة — الحذف المؤجل ─────────────────────────────────────────────
val KEY_PENDING_INVOICE_DELETIONS   = stringSetPreferencesKey("pending_invoice_deletions")
val KEY_PENDING_CLIENT_DELETIONS    = stringSetPreferencesKey("pending_client_deletions")
val KEY_PENDING_INVENTORY_DELETIONS = stringSetPreferencesKey("pending_inventory_deletions")
val KEY_PENDING_EXPENSE_DELETIONS    = stringSetPreferencesKey("pending_expense_deletions")
val KEY_PENDING_CATEGORY_DELETIONS   = stringSetPreferencesKey("pending_category_deletions")
val KEY_PENDING_COMMISSION_DELETIONS = stringSetPreferencesKey("pending_commission_deletions")
val KEY_PENDING_UNIT_DELETIONS       = stringSetPreferencesKey("pending_unit_deletions")
val KEY_PENDING_BUDGET_DELETIONS     = stringSetPreferencesKey("pending_budget_deletions")
val KEY_PENDING_RECONCILIATION_DELETIONS = stringSetPreferencesKey("pending_reconciliation_deletions")
val KEY_SP_MIGRATED                 = booleanPreferencesKey("sp_migrated")

// ── المخزون — إعدادات المدير ──────────────────────────────────────────
val KEY_ALLOW_NEGATIVE_STOCK = booleanPreferencesKey("allow_negative_stock")
val KEY_ALLOW_CASH_OVERDRAFT = booleanPreferencesKey("allow_cash_overdraft")

// ── مزامنة تزايدية — آخر وقت سحب (epoch ms) ──────────────────────────
val KEY_LAST_PULLED_CLIENTS       = longPreferencesKey("lp_clients")
val KEY_LAST_PULLED_INVOICES      = longPreferencesKey("lp_invoices")
val KEY_LAST_PULLED_INVENTORY     = longPreferencesKey("lp_inventory")
val KEY_LAST_PULLED_PAYMENTS      = longPreferencesKey("lp_payments")
val KEY_LAST_PULLED_EXPENSES      = longPreferencesKey("lp_expenses")
val KEY_LAST_PULLED_CASH_MVTS     = longPreferencesKey("lp_cash_movements")
val KEY_LAST_PULLED_ORG_SETTINGS  = longPreferencesKey("lp_org_settings")
val KEY_LAST_PULLED_INV_MOVEMENTS = longPreferencesKey("lp_inv_movements")
val KEY_LAST_PULLED_COMMISSIONS   = longPreferencesKey("lp_commissions")
val KEY_LAST_PULLED_SHIPMENTS     = longPreferencesKey("lp_shipments")
val KEY_LAST_PULLED_CATEGORIES    = longPreferencesKey("lp_categories")
val KEY_LAST_PULLED_NOTES         = longPreferencesKey("lp_notes")
val KEY_LAST_PULLED_REMINDERS     = longPreferencesKey("lp_reminders")
val KEY_LAST_PULLED_BUDGETS       = longPreferencesKey("lp_budgets")

// Server-issued protocol v2 cursor. The org binding prevents cursor reuse after account switching.
val KEY_SYNC_V2_ORG_ID            = stringPreferencesKey("sync_v2_org_id")
val KEY_SYNC_V2_CURSOR            = longPreferencesKey("sync_v2_cursor")
// Persistent operation checkpoint for crash-safe sync replay.
val KEY_SYNC_RUN_ORG_ID           = stringPreferencesKey("sync_run_org_id")
val KEY_SYNC_RUN_ID               = stringPreferencesKey("sync_run_id")
val KEY_SYNC_RUN_START_REVISION   = longPreferencesKey("sync_run_start_revision")
val KEY_SYNC_RUN_COMPLETED_KEYS   = stringSetPreferencesKey("sync_run_completed_keys")
/** ملخصات آخر مزامنة، معزولة بمفتاح مجزأ للمؤسسة والمستخدم. */
val KEY_SYNC_REPORT_SUMMARIES     = stringSetPreferencesKey("sync_report_summaries_v124")

// ── هوية الجلسة — لكشف تبديل الحساب/المؤسسة ───────────────────────────
/** آخر معرّف مؤسسة سُجِّل الدخول به على هذا الجهاز (لمسح Room عند التبديل). */
val KEY_LAST_ORG_ID = stringPreferencesKey("last_org_id")
/** Session 311 opaque monotonic epoch; intentionally survives session-data clearing. */
val KEY_SYNC_SESSION_EPOCH = longPreferencesKey("sync_session_epoch_v311")
