package com.verto.feature.dashboard.api

/** Stable permission keys consumed by business search providers. */
object HomePermissionKeys {
    const val CLIENTS_VIEW = "clients_view"
    const val CLIENTS_EDIT = "clients_edit"

    const val ADMIN = "role_admin"
    const val CLIENTS_ADD_PAYMENT = "clients_add_payment"
    const val SUPPLIERS_ADD_PAYMENT = "suppliers_add_payment"

    const val SALES_VIEW = "sales_view"
    const val SALES_CREATE = "sales_create"
    const val SALES_EDIT = "sales_edit"
    const val PURCHASES_VIEW = "purchases_view"
    const val PURCHASES_CREATE = "purchases_create"
    const val PURCHASES_EDIT = "purchases_edit"

    const val INVENTORY_VIEW = "inventory_view"
    const val INVENTORY_EDIT = "inventory_edit"
    const val INVENTORY_PRICE = "inventory_price"

    const val REPORTS_VIEW = "reports_summary"
    const val EXPENSES_VIEW = "expenses_view"
    const val EXPENSES_CREATE = "expenses_create"
    const val COMMISSION_MANAGE = "commission_manage"
    const val SHIPMENTS_VIEW = "shipments_view"
    const val MARKETING_DASHBOARDS = "marketing_dashboards"
    const val SETTINGS_PASSWORD = "settings_password"
    const val SETTINGS_ORG_DATA = "settings_org_data"

    const val VIEW_MANAGEMENT = "VIEW_MANAGEMENT"
    const val VIEW_OPTIMAL = "VIEW_OPTIMAL"
    const val VIEW_OPTIMAL_COMPANIES = "VIEW_OPTIMAL_COMPANIES"
    const val VIEW_OPTIMAL_MESSAGES = "VIEW_OPTIMAL_MESSAGES"
    const val VIEW_OPTIMAL_INVOICES = "VIEW_OPTIMAL_INVOICES"
    const val VIEW_OPTIMAL_MAINTENANCE = "VIEW_OPTIMAL_MAINTENANCE"
    const val ISSUE_OPTIMAL_CODE = "ISSUE_OPTIMAL_CODE"
    const val VIEW_OPTIMAL_SYNC_ISSUES = "VIEW_OPTIMAL_SYNC_ISSUES"

    const val PAYMENTS_REVERSE = "payments_reverse"
}

/** Navigation-safe identifiers; the app shell owns their route mapping. */
object HomeDestinationIds {
    const val PARTY_DETAILS = "party_details"
    const val PARTY_PAYMENT = "party_payment"
    const val INVOICE_DETAILS = "invoice_details"
    const val INVOICE_EDIT = "invoice_edit"
    const val INVOICE_PAYMENT = "invoice_payment"
    const val INVENTORY_ITEM_DETAILS = "inventory_item_details"
    const val INVENTORY_ITEM_EDIT = "inventory_item_edit"
    const val SHIPMENT_DETAILS = "shipment_details"
    const val SHIPMENT_STAGE_DOCUMENTS = "shipment_stage_documents"
    const val PRICE_LIST = "price_list"
    const val OPTIMAL_MAINTENANCE_DETAILS = "optimal_maintenance_details"
    const val PAYMENT_REVERSE = "payment_reverse"
    const val PARTY_CALL = "party_call"
    const val PARTY_WHATSAPP = "party_whatsapp"
    const val PENDING_ACTION_REMIND = "pending_action_remind"
}


/** Stable IDs owned by feature quick-action providers, never by Home UI. */
object QuickActionIds {
    const val SALES_INVOICE = "sales.invoice.create"
    const val PURCHASE = "purchases.invoice.create"
    const val INTERNATIONAL_PURCHASE = "purchases.invoice.international.create"
    const val ADD_CLIENT = "party.client.add"
    const val ADD_SUPPLIER = "party.supplier.add"
    const val ADD_INVENTORY_ITEM = "inventory.item.add"
    const val RECORD_EXPENSE = "expenses.record"
    const val PRICE_LIST = "inventory.price_list.open"

    // Retained as stable IDs for non-home callers and persisted historical state.
    const val RECORD_PAYMENT = "payment.record"
    const val QUICK_STOCK_COUNT = "inventory.stock.count"
}

/** App-shell-safe destinations. Concrete routes remain owned by the app module. */
object QuickActionDestinationIds {
    const val INVOICE_EDITOR = "quick_action.invoice_editor"
    const val INTERNATIONAL_PURCHASE_SETUP = "quick_action.international_purchase_setup"
    const val ADD_CLIENT = "quick_action.add_client"
    const val ADD_SUPPLIER = "quick_action.add_supplier"
    const val ADD_INVENTORY_ITEM = "quick_action.add_inventory_item"
    const val EXPENSE_ENTRY = "quick_action.expense_entry"
    const val PRICE_LIST = "quick_action.price_list"

    // Retained for non-home flows and persisted historical state.
    const val PAYMENT_ENTRY = "quick_action.payment_entry"
    const val STOCK_COUNT = "quick_action.stock_count"
}

/** Sparse positions leave space for future providers without renumbering existing actions. */
object QuickActionDefaultOrder {
    const val SALES_INVOICE = 100
    const val PURCHASE = 200
    const val INTERNATIONAL_PURCHASE = 300
    const val ADD_CLIENT = 400
    const val ADD_SUPPLIER = 500
    const val ADD_INVENTORY_ITEM = 600
    const val RECORD_EXPENSE = 700
    const val PRICE_LIST = 800

    // Historical/non-home positions; no longer contributed to Home quick actions.
    const val RECORD_PAYMENT = 900
    const val QUICK_STOCK_COUNT = 1000
}
