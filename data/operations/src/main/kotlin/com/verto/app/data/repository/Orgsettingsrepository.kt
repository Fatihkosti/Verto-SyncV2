package com.verto.app.data.repository

import androidx.room.withTransaction
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.OrganizationSettingsLocalEntity
import com.verto.app.data.remote.AuthRepository
import com.verto.app.data.remote.OrganizationSettingsRemote
import com.verto.app.data.remote.dto.OrgSettingsDto
import com.verto.app.data.sync.UnifiedOutboxWriter
import com.verto.app.utils.PreferencesManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Session 307: Room is the synchronized settings source of truth; DataStore is compatibility/cache only. */
@Singleton
class OrgSettingsRepository @Inject constructor(
    private val prefs: PreferencesManager,
    private val authRepo: AuthRepository,
    private val remote: OrganizationSettingsRemote,
    private val database: AppDatabase,
    private val sessionReader: SessionReader,
    private val outbox: UnifiedOutboxWriter,
) {
    private val dao get() = database.unifiedSyncProducerV307Dao()
    private val settingsPushMutex = Mutex()

    val orgSettings: Flow<OrgSettings> = sessionReader.organizationId.flatMapLatest { rawOrgId ->
        val organizationId = rawOrgId.trim()
        if (organizationId.isBlank()) flowOf(OrgSettings())
        else dao.observeOrganizationSettings(organizationId)
            .onStart { hydrateLegacyCacheIfMissing(organizationId) }
            .map { it?.toDomain() ?: OrgSettings() }
    }

    suspend fun saveOrgSettings(settings: OrgSettings) {
        val organizationId = trustedOrganizationId()
        val now = System.currentTimeMillis()
        database.withTransaction {
            dao.upsertOrganizationSettings(settings.toEntity(organizationId, now, isDirty = true))
            outbox.enqueue(
                organizationId = organizationId,
                aggregateType = "ORGANIZATION_SETTINGS",
                aggregateId = organizationId,
                operationType = "UPSERT",
                payload = settings.payload(now),
            )
        }
        // Compatibility cache and legacy remote happen only after the durable local commit.
        cache(settings)
        runCatching { pushPendingToSupabase() }.onFailure { e ->
            android.util.Log.w("OrgSettingsRepository", "Organization settings upload failed: ${e::class.java.simpleName}")
        }
    }

    /**
     * LEGACY_RETRY: uploads only a locally edited row. A clean/stale device is never allowed to
     * overwrite the server merely because a sync cycle started. The conditional clean update
     * protects a newer local edit that may arrive while the network call is in flight.
     */
    suspend fun pushPendingToSupabase() = settingsPushMutex.withLock {
        val organizationId = trustedOrganizationId()
        val pending = dao.getDirtyOrganizationSettings(organizationId) ?: return@withLock
        val profile = authRepo.getMyProfile() ?: return@withLock
        require(profile.organizationId.trim() == organizationId) { "FAIL_ORG_SCOPE" }
        remote.upsert(pending.toDomain().toDto(organizationId))
        database.withTransaction {
            dao.markOrganizationSettingsClean(organizationId, pending.updatedAt)
        }
    }

    /** REMOTE_APPLY: updates Room + compatibility cache, never enqueues a producer mutation. */
    suspend fun syncFromSupabase() {
        val organizationId = trustedOrganizationId()
        // Never replace an unsent local edit. PUSH owns retry and will clear the dirty marker on success.
        if (dao.getDirtyOrganizationSettings(organizationId) != null) return
        val profile = authRepo.getMyProfile() ?: return
        require(profile.organizationId.trim() == organizationId) { "FAIL_ORG_SCOPE" }
        val dto = runCatching { remote.fetch(organizationId) }.getOrNull() ?: return
        val settings = dto.toDomain()
        database.withTransaction {
            dao.upsertOrganizationSettings(settings.toEntity(organizationId, System.currentTimeMillis(), isDirty = false))
        }
        cache(settings)
    }

    /** Legacy compatibility side effect used by explicit callers; it never mutates local dirty state. */
    suspend fun pushToSupabase(settings: OrgSettings) {
        val organizationId = trustedOrganizationId()
        val profile = authRepo.getMyProfile() ?: return
        require(profile.organizationId.trim() == organizationId) { "FAIL_ORG_SCOPE" }
        remote.upsert(settings.toDto(organizationId))
    }

    /** CACHE_HYDRATION: DataStore cannot be read by a Room migration, so hydrate once without enqueue. */
    private suspend fun hydrateLegacyCacheIfMissing(organizationId: String) {
        if (dao.getOrganizationSettings(organizationId) != null) return
        val cached = OrgSettings(
            shopName = prefs.orgShopName.first(),
            shopPhone = prefs.orgShopPhone.first(),
            city = prefs.orgCity.first(),
            address = prefs.orgAddress2.first(),
            currency = prefs.orgCurrency.first(),
            invoiceFooter = prefs.orgInvoiceFooter.first(),
            taxNumber = prefs.orgTaxNumber.first(),
            logoUrl = prefs.orgLogoUrl.first(),
            signatureUrl = prefs.orgSignatureUrl.first(),
        )
        database.withTransaction {
            if (dao.getOrganizationSettings(organizationId) == null) {
                dao.upsertOrganizationSettings(cached.toEntity(organizationId, System.currentTimeMillis(), isDirty = false))
            }
        }
    }

    private suspend fun cache(settings: OrgSettings) = prefs.cacheOrgSettings(
        shopName = settings.shopName,
        shopPhone = settings.shopPhone,
        city = settings.city,
        address = settings.address,
        currency = settings.currency,
        invoiceFooter = settings.invoiceFooter,
        taxNumber = settings.taxNumber,
        logoUrl = settings.logoUrl,
        signatureUrl = settings.signatureUrl,
    )

    private suspend fun trustedOrganizationId(): String = sessionReader.snapshot().organization.id.trim().also {
        require(it.isNotBlank()) { "FAIL_ORG_SCOPE" }
    }

    private fun OrganizationSettingsLocalEntity.toDomain() = OrgSettings(
        shopName, shopPhone, address, city, currency, invoiceFooter, taxNumber, logoUrl, signatureUrl,
    )

    private fun OrgSettings.toEntity(organizationId: String, updatedAt: Long, isDirty: Boolean) = OrganizationSettingsLocalEntity(
        organizationId = organizationId,
        shopName = shopName,
        shopPhone = shopPhone,
        city = city,
        address = address,
        currency = currency,
        invoiceFooter = invoiceFooter,
        taxNumber = taxNumber,
        logoUrl = logoUrl,
        signatureUrl = signatureUrl,
        updatedAt = updatedAt,
        isDirty = isDirty,
    )

    private fun OrgSettings.payload(updatedAt: Long) = mapOf(
        "address" to address,
        "city" to city,
        "currency" to currency,
        "invoiceFooter" to invoiceFooter,
        "logoUrl" to logoUrl,
        "shopName" to shopName,
        "shopPhone" to shopPhone,
        "signatureUrl" to signatureUrl,
        "taxNumber" to taxNumber,
        "updatedAt" to updatedAt,
    )

    private fun OrgSettings.toDto(organizationId: String) = OrgSettingsDto(
        organizationId = organizationId,
        shopName = shopName,
        shopPhone = shopPhone,
        city = city,
        address = address,
        currency = currency,
        invoiceFooter = invoiceFooter,
        taxNumber = taxNumber,
        logoUrl = logoUrl,
        signatureUrl = signatureUrl,
    )

    private fun OrgSettingsDto.toDomain() = OrgSettings(
        shopName = shopName,
        shopPhone = shopPhone,
        city = city,
        address = address,
        currency = currency,
        invoiceFooter = invoiceFooter,
        taxNumber = taxNumber,
        logoUrl = logoUrl,
        signatureUrl = signatureUrl,
    )
}
