package com.verto.app.data.sync.rollout

import com.verto.app.data.sync.UnifiedSyncAggregateRegistry
import com.verto.app.data.sync.UnifiedSyncFinancialSensitivity
import com.verto.app.utils.FeatureFlags

/** Session 314 rollout authority. A single immutable snapshot decides transport and ownership. */
enum class SyncRolloutWave(val wireValue: Int) {
    WAVE_0_SYNTHETIC(0),
    WAVE_1_TEST_ORG_LEGACY_AUTHORITY(1),
    WAVE_2_V2_PULL_SHADOW(2),
    WAVE_3_V2_NONFINANCIAL_WRITE(3),
    WAVE_4_V2_INVENTORY_FINANCIAL(4),
    WAVE_5_REALTIME_ACCELERATION(5),
    WAVE_6_DEFAULT_ON(6);

    companion object {
        fun fromWireValue(value: Int): SyncRolloutWave? = values().firstOrNull { it.wireValue == value }
    }
}

enum class SyncRolloutEnvironment { LOCAL, STAGING, PRODUCTION }

enum class SyncAggregateOwnership {
    LEGACY_AUTHORITATIVE,
    V2_SHADOW_READ,
    V2_AUTHORITATIVE,
    V2_PAUSED_SAFE,
    RETIRED_LEGACY,
}

data class SyncRolloutSnapshot(
    val environment: SyncRolloutEnvironment,
    val wave: SyncRolloutWave,
    val organizationId: String,
    val organizationEligible: Boolean,
    val masterV2Enabled: Boolean,
    val v2PullEnabled: Boolean,
    val v2PushEnabled: Boolean,
    val financialEnabled: Boolean,
    val inventoryEnabled: Boolean,
    val realtimeEnabled: Boolean,
    val legacyFallbackEnabled: Boolean,
    val invalidReasons: Set<String>,
) {
    val isValid: Boolean get() = invalidReasons.isEmpty()
    val anyV2AuthoritativeTransport: Boolean
        get() = isValid && organizationEligible && masterV2Enabled && v2PullEnabled && v2PushEnabled && wave.wireValue >= 3
}

data class SyncOwnershipDecision(
    val aggregateType: String,
    val ownership: SyncAggregateOwnership,
    val reason: String,
)

object SyncRolloutPolicy {
    fun snapshot(organizationId: String): SyncRolloutSnapshot {
        val environment = runCatching {
            SyncRolloutEnvironment.valueOf(FeatureFlags.syncRolloutEnvironment.trim().uppercase())
        }.getOrNull()
        val wave = SyncRolloutWave.fromWireValue(FeatureFlags.syncRolloutWave)
        val reasons = linkedSetOf<String>()
        if (environment == null) reasons += "INVALID_ENVIRONMENT"
        if (wave == null) reasons += "INVALID_WAVE"
        if (FeatureFlags.isV2FinancialSyncEnabled && (!FeatureFlags.isV2PushEnabled || !FeatureFlags.isV2PullEnabled)) {
            reasons += "FINANCIAL_REQUIRES_PULL_PUSH"
        }
        if (FeatureFlags.isV2InventorySyncEnabled && (!FeatureFlags.isV2PushEnabled || !FeatureFlags.isV2PullEnabled)) {
            reasons += "INVENTORY_REQUIRES_PULL_PUSH"
        }
        if (FeatureFlags.isRealtimeHintsEnabled && !FeatureFlags.isV2PullEnabled) {
            reasons += "REALTIME_REQUIRES_PULL"
        }
        if (!FeatureFlags.isLegacySyncFallbackEnabled && (wave?.wireValue ?: -1) < 6) {
            reasons += "LEGACY_OFF_REQUIRES_FINAL_GATE"
        }
        if ((FeatureFlags.isV2PullEnabled || FeatureFlags.isV2PushEnabled) && !FeatureFlags.isVersionedSyncEnabled) {
            reasons += "V2_SUBSWITCH_REQUIRES_MASTER"
        }
        if ((wave?.wireValue ?: 0) < 4 && (FeatureFlags.isV2FinancialSyncEnabled || FeatureFlags.isV2InventorySyncEnabled)) {
            reasons += "SENSITIVE_ENABLE_BEFORE_WAVE_4"
        }
        if ((wave?.wireValue ?: 0) < 5 && FeatureFlags.isRealtimeHintsEnabled) {
            reasons += "REALTIME_ENABLE_BEFORE_WAVE_5"
        }

        val denied = organizationId in FeatureFlags.syncRolloutOrganizationDenylist
        val allowlisted = organizationId in FeatureFlags.syncRolloutOrganizationAllowlist
        val eligible = !denied && when (wave) {
            SyncRolloutWave.WAVE_0_SYNTHETIC -> environment != SyncRolloutEnvironment.PRODUCTION && allowlisted
            SyncRolloutWave.WAVE_1_TEST_ORG_LEGACY_AUTHORITY,
            SyncRolloutWave.WAVE_2_V2_PULL_SHADOW,
            SyncRolloutWave.WAVE_3_V2_NONFINANCIAL_WRITE,
            SyncRolloutWave.WAVE_4_V2_INVENTORY_FINANCIAL,
            SyncRolloutWave.WAVE_5_REALTIME_ACCELERATION -> allowlisted
            SyncRolloutWave.WAVE_6_DEFAULT_ON -> true
            null -> false
        }

        return SyncRolloutSnapshot(
            environment = environment ?: SyncRolloutEnvironment.PRODUCTION,
            wave = wave ?: SyncRolloutWave.WAVE_0_SYNTHETIC,
            organizationId = organizationId,
            organizationEligible = eligible,
            masterV2Enabled = FeatureFlags.isVersionedSyncEnabled,
            v2PullEnabled = FeatureFlags.isV2PullEnabled,
            v2PushEnabled = FeatureFlags.isV2PushEnabled,
            financialEnabled = FeatureFlags.isV2FinancialSyncEnabled,
            inventoryEnabled = FeatureFlags.isV2InventorySyncEnabled,
            realtimeEnabled = FeatureFlags.isRealtimeHintsEnabled && FeatureFlags.isRealtimeSyncEnabled,
            legacyFallbackEnabled = FeatureFlags.isLegacySyncFallbackEnabled,
            invalidReasons = reasons,
        )
    }

    /**
     * Fail closed. Before any V2 commit invalid config stays Legacy; after a V2 commit it pauses V2
     * instead of replaying legacy dirty state over an authoritative V2 effect.
     */
    fun ownership(
        snapshot: SyncRolloutSnapshot,
        aggregateType: String,
        hasCommittedV2Write: Boolean = false,
        legacyRetirementProven: Boolean = false,
    ): SyncOwnershipDecision {
        val record = UnifiedSyncAggregateRegistry.requireById(aggregateType)
        if (!snapshot.isValid || !snapshot.organizationEligible || !snapshot.masterV2Enabled) {
            return if (hasCommittedV2Write) {
                SyncOwnershipDecision(aggregateType, SyncAggregateOwnership.V2_PAUSED_SAFE, "FAIL_CLOSED_AFTER_V2_COMMIT")
            } else {
                SyncOwnershipDecision(aggregateType, SyncAggregateOwnership.LEGACY_AUTHORITATIVE, "FAIL_CLOSED_PRE_V2")
            }
        }
        if (legacyRetirementProven && snapshot.wave == SyncRolloutWave.WAVE_6_DEFAULT_ON) {
            return SyncOwnershipDecision(aggregateType, SyncAggregateOwnership.RETIRED_LEGACY, "FINAL_RETIREMENT_GATE")
        }
        if (snapshot.wave == SyncRolloutWave.WAVE_2_V2_PULL_SHADOW) {
            return SyncOwnershipDecision(aggregateType, SyncAggregateOwnership.V2_SHADOW_READ, "SHADOW_NO_SIDE_EFFECTS")
        }
        if (snapshot.wave.wireValue < 3) {
            return SyncOwnershipDecision(aggregateType, SyncAggregateOwnership.LEGACY_AUTHORITATIVE, "PRE_WRITE_WAVE")
        }
        if (!snapshot.v2PullEnabled || !snapshot.v2PushEnabled) {
            return if (hasCommittedV2Write) {
                SyncOwnershipDecision(aggregateType, SyncAggregateOwnership.V2_PAUSED_SAFE, "V2_TRANSPORT_PAUSED")
            } else {
                SyncOwnershipDecision(aggregateType, SyncAggregateOwnership.LEGACY_AUTHORITATIVE, "V2_TRANSPORT_OFF")
            }
        }

        val sensitive = record.financialSensitivity
        val enabled = when (sensitive) {
            UnifiedSyncFinancialSensitivity.NONE -> true
            UnifiedSyncFinancialSensitivity.INVENTORY_LEDGER -> snapshot.wave.wireValue >= 4 && snapshot.inventoryEnabled
            UnifiedSyncFinancialSensitivity.FINANCIAL,
            UnifiedSyncFinancialSensitivity.LEDGER_AFFECTING -> snapshot.wave.wireValue >= 4 && snapshot.financialEnabled
        }
        return if (enabled) {
            SyncOwnershipDecision(aggregateType, SyncAggregateOwnership.V2_AUTHORITATIVE, "WAVE_${snapshot.wave.wireValue}_OWNERSHIP")
        } else if (hasCommittedV2Write) {
            SyncOwnershipDecision(aggregateType, SyncAggregateOwnership.V2_PAUSED_SAFE, "SENSITIVE_SLICE_PAUSED")
        } else {
            SyncOwnershipDecision(aggregateType, SyncAggregateOwnership.LEGACY_AUTHORITATIVE, "SENSITIVE_SLICE_NOT_YET_ENABLED")
        }
    }

    fun aggregateOwnerships(snapshot: SyncRolloutSnapshot): Map<String, SyncAggregateOwnership> =
        UnifiedSyncAggregateRegistry.all.associate { record ->
            record.id to ownership(snapshot, record.id).ownership
        }
}
