package com.verto.app.data.repository

import com.verto.app.utils.FeatureFlags
import com.verto.app.utils.PreferencesManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class SubscriptionTier(
    val displayName: String,
    val maxDevices: Int,
    val hasAdvancedReports: Boolean,
    val hasMultiCurrency: Boolean,
    val hasEncryptedBackup: Boolean,
    val hasPrioritySync: Boolean
) {
    FREE(
        displayName       = "مجاني",
        maxDevices        = 1,
        hasAdvancedReports = false,
        hasMultiCurrency  = false,
        hasEncryptedBackup = false,
        hasPrioritySync   = false
    ),
    PRO(
        displayName       = "احترافي",
        maxDevices        = 3,
        hasAdvancedReports = true,
        hasMultiCurrency  = false,
        hasEncryptedBackup = true,
        hasPrioritySync   = true
    ),
    ENTERPRISE(
        displayName       = "مؤسسي",
        maxDevices        = Int.MAX_VALUE,
        hasAdvancedReports = true,
        hasMultiCurrency  = true,
        hasEncryptedBackup = true,
        hasPrioritySync   = true
    );
}

/**
 * يُدير مستوى الاشتراك للمؤسسة.
 *
 * للتوسعة بـ Google Play Billing:
 * 1. أضف `com.android.billingclient:billing-ktx:7.x.x`
 * 2. نفِّذ BillingClient في `init()` واجلب subscription products
 * 3. استبدل القراءة من PreferencesManager بالقراءة من BillingClient
 */
class SubscriptionManager(
    private val prefs: PreferencesManager
) {

    val currentTier: Flow<SubscriptionTier> = prefs.subscriptionTier.map { tierName ->
        runCatching { SubscriptionTier.valueOf(tierName) }.getOrDefault(SubscriptionTier.FREE)
    }

    fun isFeatureAllowed(feature: SubscriptionFeature, tier: SubscriptionTier): Boolean {
        if (!FeatureFlags.isSubscriptionTieringEnabled) return true
        return when (feature) {
            SubscriptionFeature.ADVANCED_REPORTS   -> tier.hasAdvancedReports
            SubscriptionFeature.MULTI_CURRENCY     -> tier.hasMultiCurrency
            SubscriptionFeature.ENCRYPTED_BACKUP   -> tier.hasEncryptedBackup
            SubscriptionFeature.PRIORITY_SYNC      -> tier.hasPrioritySync
            SubscriptionFeature.MULTI_DEVICE       -> tier.maxDevices > 1
        }
    }

    suspend fun setTier(tier: SubscriptionTier) {
        prefs.setSubscriptionTier(tier.name)
    }
}

enum class SubscriptionFeature {
    ADVANCED_REPORTS,
    MULTI_CURRENCY,
    ENCRYPTED_BACKUP,
    PRIORITY_SYNC,
    MULTI_DEVICE
}
