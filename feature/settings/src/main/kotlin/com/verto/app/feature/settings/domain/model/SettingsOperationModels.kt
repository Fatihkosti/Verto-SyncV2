package com.verto.app.feature.settings.domain.model

/** نطاق البيانات الذي يمكن للمدير حذفه من شاشة الإعدادات. */
enum class SettingsDataScope {
    SALES_INVOICES,
    PURCHASE_INVOICES,
    CLIENTS,
    INVENTORY,
    CASH_REGISTER,
    ALL
}

/** وجهة مشاركة النسخة الاحتياطية دون كشف تفاصيل Android للواجهة. */
enum class SettingsBackupTarget {
    LOCAL,
    WHATSAPP,
    TELEGRAM
}
