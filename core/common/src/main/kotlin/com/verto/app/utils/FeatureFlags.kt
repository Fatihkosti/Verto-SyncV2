package com.verto.app.utils

/**
 * Feature Flags — يتحكم في تفعيل/تعطيل الميزات بدون نشر.
 *
 * للتوسعة بـ Firebase RemoteConfig:
 * 1. أضف `com.google.firebase:firebase-config-ktx`
 * 2. في `init()` اجلب قيم RemoteConfig واستبدل الـ overrides
 *    مثال: remoteConfig.fetchAndActivate().await()
 *           isRealtimeSyncEnabled = remoteConfig.getBoolean("realtime_sync_enabled")
 */
object FeatureFlags {

    // ── تحكم يدوي (يُبدَّل بـ RemoteConfig في الإنتاج) ──────────────────

    /**
     * تزامن فوري عبر Supabase Realtime.
     * الجلسة 9 (fixbacklog): **معطَّل** في إطلاق المنصوري الأول (جهاز واحد) لتفادي تداخل
     * المزامنة أثناء الاستقرار — يُعتمَد على المزامنة اليدوية/الدورية (SyncWorker). يُفعَّل
     * بعد استقرار مسار الفواتير/المخزون، أو عبر RemoteConfig في الإنتاج.
     */
    var isRealtimeSyncEnabled: Boolean = false

    /** U10 protocol v2 remains opt-in until the Staging two-device convergence gate passes. */
    var isVersionedSyncEnabled: Boolean = false

    // Session 314 — independent sync rollout kill switches. Static/pre-cutover defaults are fail-safe.
    var isV2PullEnabled: Boolean = false
    var isV2PushEnabled: Boolean = false
    var isV2FinancialSyncEnabled: Boolean = false
    var isV2InventorySyncEnabled: Boolean = false
    var isRealtimeHintsEnabled: Boolean = false
    var isLegacySyncFallbackEnabled: Boolean = true

    /** 0..6, mapped by SyncRolloutPolicy; 0 keeps all production ownership on Legacy. */
    var syncRolloutWave: Int = 0

    /** Explicit rollout scoping. Empty allowlist means no organization is eligible before wave 6. */
    val syncRolloutOrganizationAllowlist: MutableSet<String> = linkedSetOf()
    val syncRolloutOrganizationDenylist: MutableSet<String> = linkedSetOf()

    /** LOCAL / STAGING / PRODUCTION. Invalid values fail closed in SyncRolloutPolicy. */
    var syncRolloutEnvironment: String = "PRODUCTION"

    /** تقارير الأرباح التفصيلية — متاح لكل المستخدمين حالياً */
    var isDetailedProfitReportEnabled: Boolean = true

    /** تصدير AuditLog لـ PDF — متاح لكل المستخدمين حالياً */
    var isAuditLogExportEnabled: Boolean = true

    /** دعم تعدد العملات في الفاتورة */
    var isMultiCurrencyEnabled: Boolean = false

    /** SaaS subscription gating */
    var isSubscriptionTieringEnabled: Boolean = false

    /** نسخة احتياطية مشفّرة */
    var isEncryptedBackupEnabled: Boolean = false

    /** U02 containment: financial payout/credit mutations stay disabled until the server ledger engine is atomic. */
    var isFinancialMutationsEnabled: Boolean = false

    /** U13 server-authoritative confirmation; enabled only after Staging concurrency passes. */
    var isAtomicShipmentConfirmationEnabled: Boolean = false

    // ── دوال القراءة السهلة ────────────────────────────────────────────

    fun isEnabled(flag: String): Boolean = when (flag) {
        "realtime_sync"            -> isRealtimeSyncEnabled
        "versioned_sync"           -> isVersionedSyncEnabled
        "v2_pull"                  -> isV2PullEnabled
        "v2_push"                  -> isV2PushEnabled
        "v2_financial"             -> isV2FinancialSyncEnabled
        "v2_inventory"             -> isV2InventorySyncEnabled
        "realtime_hints"           -> isRealtimeHintsEnabled
        "legacy_sync_fallback"     -> isLegacySyncFallbackEnabled
        "detailed_profit_report"   -> isDetailedProfitReportEnabled
        "audit_log_export"         -> isAuditLogExportEnabled
        "multi_currency"           -> isMultiCurrencyEnabled
        "subscription_tiering"     -> isSubscriptionTieringEnabled
        "encrypted_backup"         -> isEncryptedBackupEnabled
        "financial_mutations"      -> isFinancialMutationsEnabled
        "atomic_shipment_confirmation" -> isAtomicShipmentConfirmationEnabled
        else -> false
    }
}
