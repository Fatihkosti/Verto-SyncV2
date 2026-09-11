package com.verto.app.data.repository

import com.verto.app.data.local.dao.ClientDao
import com.verto.app.data.local.entity.PartyIdentityEntity
import com.verto.app.data.model.PermissionDeniedException
import com.verto.app.data.remote.PermissionProvider
import com.verto.app.core.audit.domain.AuditAction
import com.verto.app.core.audit.domain.AuditTable
import com.verto.app.core.audit.domain.WriteAuditPort
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.sync.UnifiedOutboxWriter
import com.verto.app.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map as mapPaging
import kotlin.math.abs
import com.verto.app.feature.party.application.query.PartyPagingGateway
import com.verto.app.feature.party.data.toPartyIdentityEntity
import com.verto.app.feature.party.data.toPartyClient
import com.verto.app.feature.party.data.toPartyClientSummary
import com.verto.app.feature.party.domain.model.PartyClient
import com.verto.app.feature.party.domain.model.PartyClientSummary
import com.verto.app.feature.party.domain.repository.PartyDirectoryGateway
import com.verto.app.feature.party.domain.repository.PartySyncFallbackPort
import androidx.room.withTransaction
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.CustomerProfileEntity
import com.verto.app.data.local.entity.SupplierProfileEntity
import com.verto.app.feature.party.application.PartyRoleCommandPort
import com.verto.app.feature.party.domain.model.PartyRole
import com.verto.app.feature.party.domain.model.CustomerProfile
import com.verto.app.feature.party.domain.model.SupplierProfile
import com.verto.app.feature.party.domain.model.SupplierScope
import java.util.UUID

// ─────────────────────────────────────────────────────────────
// Repository
// ─────────────────────────────────────────────────────────────

class ClientRepository(
    private val clientDao: ClientDao,
    private val userPrefs: PreferencesManager,
    private val permissionProvider: PermissionProvider,
    private val auditLogger: WriteAuditPort,
    private val syncFallback: PartySyncFallbackPort,
    private val database: AppDatabase,
    private val roleCommands: PartyRoleCommandPort,
    private val sessionReader: SessionReader,
    private val outbox: UnifiedOutboxWriter,
) : PartyDirectoryGateway, PartyPagingGateway {

    // مسار توافق فقط؛ التنفيذ المالي انتقل إلى Adapter مستقل.
    suspend fun syncAllFromSupabase(): Result<Unit> =
        syncFallback.pullClientsInvoicesAndPayments()

    // ── قراءة — SQL يحسب الأرقام، Repository يحسب الحالة ─────
    //
    // بدل: combine(clients, invoices, payments) في الـ memory
    // الآن: استعلام واحد من قاعدة البيانات + map بسيط

    private val organizationIds: Flow<String> = sessionReader.organizationId
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctUntilChanged()

    override fun getAllClientSummaries(): Flow<List<PartyClientSummary>> =
        organizationIds.flatMapLatest(clientDao::getAllClientsWithBalance)
            .map { list ->
                list.map { it.toPartyClientSummary() }
                    .sortedWith(
                        compareBy<PartyClientSummary> { it.status.ordinal }
                            .thenByDescending { abs(it.remaining) }
                    )
            }

    override fun searchClientSummaries(query: String): Flow<List<PartyClientSummary>> =
        organizationIds.flatMapLatest { organizationId -> clientDao.searchClientsWithBalance(organizationId, query) }
            .map { list ->
                list.map { it.toPartyClientSummary() }
                    .sortedWith(
                        compareBy<PartyClientSummary> { it.status.ordinal }
                            .thenByDescending { abs(it.remaining) }
                    )
            }

    // ── عمليات فردية ──────────────────────────────────────────

    // ── Pagination ────────────────────────────────────────────────────
    private fun getAllClientsWithBalancePaged(organizationId: String, showSuppliers: Boolean, supplierScope: SupplierScope?) =
        clientDao.getAllClientsWithBalancePaged(organizationId, if (showSuppliers) 1 else 0, supplierScope?.name)

    private fun searchClientsWithBalancePaged(organizationId: String, query: String, showSuppliers: Boolean, supplierScope: SupplierScope?) =
        clientDao.searchClientsWithBalancePaged(organizationId, query, if (showSuppliers) 1 else 0, supplierScope?.name)

    override fun pagedClientSummaries(
        query: String,
        showSuppliers: Boolean,
        supplierScope: SupplierScope?,
    ): Flow<PagingData<PartyClientSummary>> = organizationIds.flatMapLatest { organizationId ->
        Pager(
            config = PagingConfig(pageSize = 30, prefetchDistance = 5, enablePlaceholders = false),
            pagingSourceFactory = {
                if (query.isBlank()) getAllClientsWithBalancePaged(organizationId, showSuppliers, supplierScope)
                else searchClientsWithBalancePaged(organizationId, query, showSuppliers, supplierScope)
            }
        ).flow
    }.map { pagingData -> pagingData.mapPaging { it.toPartyClientSummary() } }

    override fun getAllClients(): Flow<List<PartyClient>> =
        organizationIds.flatMapLatest(clientDao::observeClientRoleProjections)
            .map { rows -> rows.map { it.client.toPartyClient(it.customerSegment, it.supplierScope) } }

    override suspend fun getAllClientsSync(): List<PartyClient> {
        val organizationId = trustedOrganizationId()
        return clientDao.getClientRoleProjectionsSync(organizationId)
            .map { it.client.toPartyClient(it.customerSegment, it.supplierScope) }
    }

    fun searchClients(query: String): Flow<List<PartyIdentityEntity>> =
        organizationIds.flatMapLatest { organizationId -> clientDao.searchClients(organizationId, query) }

    override fun getClientById(id: String): Flow<PartyClient?> =
        organizationIds.flatMapLatest { organizationId -> clientDao.observeClientRoleProjection(organizationId, id) }
            .map { it?.let { row -> row.client.toPartyClient(row.customerSegment, row.supplierScope) } }

    override suspend fun getClientByIdSync(id: String): PartyClient? {
        val organizationId = trustedOrganizationId()
        return clientDao.getClientRoleProjectionSync(organizationId, id)
            ?.let { row -> row.client.toPartyClient(row.customerSegment, row.supplierScope) }
    }

    // SYNC-012: كل كتابة محلية تُعلّم الصف متسخاً ليُرفع في المزامنة التالية
    override suspend fun insertClient(client: PartyClient): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!permissionProvider.canNow { it.clientsEdit }) {
                runCatching { auditLogger.logPartyPermissionDenied("clients_edit", "insert clientId=${client.id}") }
                throw PermissionDeniedException("لا تملك صلاحية تعديل بيانات العملاء")
            }
            val organizationId = trustedOrganizationId()
            val actorId = userPrefs.userId.first()
            val batchId = UUID.randomUUID().toString()
            database.withTransaction {
                val local = client.copy(isDirty = true)
                clientDao.insertClient(local.toPartyIdentityEntity())
                outbox.enqueue(
                    organizationId = organizationId,
                    aggregateType = "PARTY_IDENTITY",
                    aggregateId = client.id,
                    operationType = "UPSERT",
                    payload = partyIdentityPayload(local),
                    commandBatchId = batchId,
                    commandOrder = 0,
                )
                writeNormalizedRolesAndProfiles(client, organizationId, actorId, batchId)
            }
            Unit
        }
    }

    override suspend fun updateClient(client: PartyClient): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!permissionProvider.canNow { it.clientsEdit }) {
                runCatching { auditLogger.logPartyPermissionDenied("clients_edit", "update clientId=${client.id}") }
                throw PermissionDeniedException("لا تملك صلاحية تعديل بيانات العملاء")
            }
            val organizationId = trustedOrganizationId()
            val actorId = userPrefs.userId.first()
            val batchId = UUID.randomUUID().toString()
            database.withTransaction {
                val local = client.copy(isDirty = true)
                clientDao.updateClient(local.toPartyIdentityEntity())
                outbox.enqueue(
                    organizationId = organizationId,
                    aggregateType = "PARTY_IDENTITY",
                    aggregateId = client.id,
                    operationType = "UPSERT",
                    payload = partyIdentityPayload(local),
                    commandBatchId = batchId,
                    commandOrder = 0,
                )
                writeNormalizedRolesAndProfiles(client, organizationId, actorId, batchId)
            }
        }
    }

    /** Session 344: explicit normalized role profiles are persisted atomically with identity/outbox. */
    override suspend fun insertParty(
        client: PartyClient,
        customerProfile: CustomerProfile?,
        supplierProfile: SupplierProfile?,
    ): Result<Unit> = persistParty(client, customerProfile, supplierProfile, insert = true)

    override suspend fun updateParty(
        client: PartyClient,
        customerProfile: CustomerProfile?,
        supplierProfile: SupplierProfile?,
    ): Result<Unit> = persistParty(client, customerProfile, supplierProfile, insert = false)

    private suspend fun persistParty(
        client: PartyClient,
        customerProfile: CustomerProfile?,
        supplierProfile: SupplierProfile?,
        insert: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(customerProfile != null || supplierProfile != null) { "Party must have at least one role profile" }
            require(customerProfile == null || customerProfile.partyId == client.id)
            require(supplierProfile == null || supplierProfile.partyId == client.id)
            if (!permissionProvider.canNow { it.clientsEdit }) {
                runCatching {
                    auditLogger.logPartyPermissionDenied(
                        "clients_edit",
                        "${if (insert) "insert" else "update"} clientId=${client.id}",
                    )
                }
                throw PermissionDeniedException("لا تملك صلاحية تعديل بيانات العملاء")
            }
            val organizationId = trustedOrganizationId()
            val actorId = userPrefs.userId.first()
            val batchId = UUID.randomUUID().toString()
            database.withTransaction {
                val local = client.copy(isDirty = true)
                if (insert) clientDao.insertClient(local.toPartyIdentityEntity()) else clientDao.updateClient(local.toPartyIdentityEntity())
                outbox.enqueue(
                    organizationId = organizationId,
                    aggregateType = "PARTY_IDENTITY",
                    aggregateId = client.id,
                    operationType = "UPSERT",
                    payload = partyIdentityPayload(local),
                    commandBatchId = batchId,
                    commandOrder = 0,
                )
                writeNormalizedRolesAndProfiles(
                    client = local,
                    organizationId = organizationId,
                    actorId = actorId,
                    batchId = batchId,
                    explicitCustomer = customerProfile,
                    explicitSupplier = supplierProfile,
                )
            }
        }
    }

    /**
     * حذف العميل + تسجيل كل فواتيره في pendingInvoiceDeletions قبل الحذف،
     * لأن Room CASCADE يحذفها محلياً لكن SyncManager يحتاج معرفاتها لحذفها من Supabase.
     * بدون هذا، فواتير العميل تبقى في Supabase وتمنع حذفه (FK) ثم تعود في الـ pull التالي.
     */
    /** Normal UI removal archives exactly one Party role and preserves any other active role. */
    override suspend fun archiveRole(id: String, role: PartyRole): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!permissionProvider.canNow { it.clientsDelete }) {
                runCatching { auditLogger.logPartyPermissionDenied("clients_delete", "archive ${role.name} partyId=$id") }
                throw PermissionDeniedException("لا تملك صلاحية أرشفة العملاء أو الموردين")
            }
            val organizationId = trustedOrganizationId()
            val projection = clientDao.getClientRoleProjectionSync(organizationId, id)
                ?: throw IllegalStateException("الطرف غير موجود في المؤسسة الحالية")
            val hasRole = when (role) {
                PartyRole.CUSTOMER -> projection.hasCustomerRole
                PartyRole.SUPPLIER -> projection.hasSupplierRole
            }
            if (!hasRole) return@runCatching Unit
            val actorId = userPrefs.userId.first()
            roleCommands.setStatus(id, organizationId, role, com.verto.app.feature.party.domain.model.RoleStatus.ARCHIVED, actorId, "USER_ARCHIVE")
            Unit
        }
    }

    /** يحذف العميل نهائياً — يرفض إذا كان لديه فواتير */
    override suspend fun deleteClientPermanently(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!permissionProvider.canNow { it.clientsDelete }) {
                runCatching { auditLogger.logPartyPermissionDenied("clients_delete", "clientId=$id") }
                throw PermissionDeniedException("لا تملك صلاحية حذف العملاء")
            }
            val organizationId = trustedOrganizationId()
            val count = clientDao.countInvoicesForClient(organizationId, id)
            if (count > 0)
                throw IllegalStateException("لا يمكن الحذف — يوجد $count فاتورة مرتبطة بهذا العميل. احذف الفواتير أولاً.")
            if (database.partyRoleDao().countRoles(id) > 0)
                throw IllegalStateException("لا يمكن الحذف النهائي — للطرف أدوار محفوظة أو مؤرشفة")
            val existing = clientDao.getClientByIdSync(organizationId, id) ?: return@runCatching Unit
            database.withTransaction {
                clientDao.deleteClientById(id)
                outbox.enqueue(
                    organizationId = organizationId,
                    aggregateType = "PARTY_IDENTITY",
                    aggregateId = id,
                    operationType = "DELETE",
                    payload = mapOf("deleted" to true, "name" to existing.name),
                )
            }
            // Legacy compatibility mirror only, after durable Room commit.
            runCatching { userPrefs.addPendingClientDeletion(id) }
            Unit
        }
    }

    private suspend fun WriteAuditPort.logPartyPermissionDenied(action: String, details: String) {
        log(
            action = AuditAction.UPDATE,
            table = AuditTable.CLIENT,
            recordId = "permission_denied:$action",
            summary = listOf("رفض صلاحية: $action", details).filter { it.isNotBlank() }.joinToString(" — "),
            employeeId = runCatching { userPrefs.userId.first() }.getOrDefault(""),
            employeeName = runCatching { userPrefs.userName.first() }.getOrDefault(""),
            canUndo = false
        )
    }

    private suspend fun writeNormalizedRolesAndProfiles(
        client: PartyClient,
        organizationId: String,
        actorId: String,
        batchId: String,
        explicitCustomer: CustomerProfile? = null,
        explicitSupplier: SupplierProfile? = null,
    ) {
        val roles = buildSet {
            if (explicitCustomer != null || client.customerSegment != null) add(PartyRole.CUSTOMER)
            if (explicitSupplier != null || client.supplierScope != null) add(PartyRole.SUPPLIER)
        }
        require(roles.isNotEmpty()) { "Party V2 requires an explicit customer segment or supplier scope" }
        roles.forEach { roleCommands.attach(client.id, organizationId, it, actorId) }

        val now = System.currentTimeMillis()
        var order = 1

        if (PartyRole.CUSTOMER in roles) {
            val source = explicitCustomer
            val segment = source?.segment ?: client.customerSegment ?: com.verto.app.feature.party.domain.model.CustomerSegment.INDIVIDUAL
            val legacyVehicles = when (segment) {
                com.verto.app.feature.party.domain.model.CustomerSegment.INDIVIDUAL ->
                    (listOf(client.carType) + client.secondaryPhones.split("||"))
                        .map(String::trim).filter(String::isNotBlank)
                com.verto.app.feature.party.domain.model.CustomerSegment.COMPANY ->
                    client.carType.split(",").map(String::trim).filter(String::isNotBlank)
                else -> emptyList()
            }
            val profile = CustomerProfileEntity(
                organizationId = organizationId,
                partyId = client.id,
                segment = segment.name,
                ageYears = source?.ageYears ?: client.specialty.trim().toIntOrNull()
                    ?.takeIf { segment == com.verto.app.feature.party.domain.model.CustomerSegment.INDIVIDUAL && it in 1..120 },
                purchaseContactName = source?.purchaseContactName
                    ?: client.specialty.takeIf { segment in setOf(com.verto.app.feature.party.domain.model.CustomerSegment.COMPANY) }.orEmpty(),
                businessActivity = source?.businessActivity ?: when (segment) {
                    com.verto.app.feature.party.domain.model.CustomerSegment.COMPANY,
                    com.verto.app.feature.party.domain.model.CustomerSegment.DISTRIBUTOR -> client.workplace
                    com.verto.app.feature.party.domain.model.CustomerSegment.WORKSHOP_OWNER,
                    com.verto.app.feature.party.domain.model.CustomerSegment.TRADER -> client.specialty
                    else -> ""
                },
                workplaceName = source?.workplaceName
                    ?: client.workplace.takeIf { segment == com.verto.app.feature.party.domain.model.CustomerSegment.INDIVIDUAL }.orEmpty(),
                shopName = source?.shopName
                    ?: client.workplace.takeIf { segment in setOf(com.verto.app.feature.party.domain.model.CustomerSegment.TRADER) }.orEmpty(),
                workshopName = source?.workshopName
                    ?: client.workplace.takeIf { segment == com.verto.app.feature.party.domain.model.CustomerSegment.WORKSHOP_OWNER }.orEmpty(),
                vehicleModels = (source?.vehicleModels ?: legacyVehicles).joinToString("||"),
                workshopWorkerCount = source?.workshopWorkerCount
                    ?: client.secondaryPhones.trim().toIntOrNull()
                        ?.takeIf { it >= 0 && segment == com.verto.app.feature.party.domain.model.CustomerSegment.WORKSHOP_OWNER },
                updatedAt = now,
            )
            database.partyRoleDao().saveCustomerProfile(profile)
            outbox.enqueue(
                organizationId = organizationId,
                aggregateType = "CUSTOMER_PROFILE",
                aggregateId = client.id,
                operationType = "UPSERT",
                payload = mapOf(
                    "partyId" to profile.partyId,
                    "segment" to profile.segment,
                    "ageYears" to profile.ageYears,
                    "purchaseContactName" to profile.purchaseContactName,
                    "businessActivity" to profile.businessActivity,
                    "workplaceName" to profile.workplaceName,
                    "shopName" to profile.shopName,
                    "workshopName" to profile.workshopName,
                    "vehicleModels" to profile.vehicleModels,
                    "workshopWorkerCount" to profile.workshopWorkerCount,
                    "updatedAt" to profile.updatedAt,
                ),
                commandBatchId = batchId,
                commandOrder = order++,
            )
        }

        if (PartyRole.SUPPLIER in roles) {
            val source = explicitSupplier
            val profile = SupplierProfileEntity(
                organizationId = organizationId,
                partyId = client.id,
                scope = source?.scope?.name ?: client.supplierScope?.name ?: "UNKNOWN",
                country = source?.country ?: client.carType,
                currencyCode = (source?.currencyCode ?: client.secondaryPhones)
                    .trim()
                    .uppercase()
                    .takeIf { it in setOf("SDG", "USD", "EUR", "SAR", "AED", "EGP", "CNY") }
                    ?: "",
                specialty = source?.specialty ?: client.specialty,
                updatedAt = now,
            )
            database.partyRoleDao().saveSupplierProfile(profile)
            outbox.enqueue(
                organizationId = organizationId,
                aggregateType = "SUPPLIER_PROFILE",
                aggregateId = client.id,
                operationType = "UPSERT",
                payload = mapOf(
                    "partyId" to profile.partyId,
                    "scope" to profile.scope,
                    "country" to profile.country,
                    "currencyCode" to profile.currencyCode,
                    "specialty" to profile.specialty,
                    "updatedAt" to profile.updatedAt,
                ),
                commandBatchId = batchId,
                commandOrder = order,
            )
        }
    }

    private fun partyIdentityPayload(client: PartyClient) = mapOf(
        "address" to client.address,
        "bankAccount" to client.bankAccount,
        "carType" to client.carType,
        "createdAt" to client.createdAt,
        "createdBy" to client.createdBy,
        "generalNote" to client.generalNote,
        "name" to client.name,
        "phone" to client.phone,
        "secondaryPhones" to client.secondaryPhones,
        "specialty" to client.specialty,
        "workplace" to client.workplace,
    )

    private suspend fun trustedOrganizationId(): String = sessionReader.snapshot().organization.id.trim().also {
        require(it.isNotBlank()) { "FAIL_ORG_SCOPE" }
    }

}
