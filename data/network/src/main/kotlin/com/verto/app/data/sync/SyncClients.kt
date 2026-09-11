package com.verto.app.data.sync

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.AppDatabase.Companion.CASH_CLIENT_UUID
import com.verto.app.data.local.AppDatabase.Companion.CASH_SUPPLIER_UUID
import com.verto.app.data.local.entity.*
import java.util.UUID
import com.verto.app.data.remote.AuthRepository
import com.verto.app.data.remote.VertoSupabase
import com.verto.app.data.remote.dto.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import com.verto.app.utils.PreferencesManager
import com.verto.app.utils.SupabaseDateParser
import com.verto.app.utils.SearchTextNormalizer
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject


@kotlinx.serialization.Serializable
private data class UpsertPartyV2Request(
    @kotlinx.serialization.SerialName("p_party_id") val partyId: String,
    @kotlinx.serialization.SerialName("p_name") val name: String,
    @kotlinx.serialization.SerialName("p_phone") val phone: String,
    @kotlinx.serialization.SerialName("p_address") val address: String,
    @kotlinx.serialization.SerialName("p_workplace") val workplace: String,
    @kotlinx.serialization.SerialName("p_general_note") val generalNote: String,
    @kotlinx.serialization.SerialName("p_car_type") val carType: String,
    @kotlinx.serialization.SerialName("p_bank_account") val bankAccount: String,
    @kotlinx.serialization.SerialName("p_specialty") val specialty: String,
    @kotlinx.serialization.SerialName("p_secondary_phones") val secondaryPhones: String,
    @kotlinx.serialization.SerialName("p_created_at") val createdAt: String?,
    @kotlinx.serialization.SerialName("p_customer_role_status") val customerRoleStatus: String?,
    @kotlinx.serialization.SerialName("p_customer_segment") val customerSegment: String?,
    @kotlinx.serialization.SerialName("p_age_years") val ageYears: Int?,
    @kotlinx.serialization.SerialName("p_purchase_contact_name") val purchaseContactName: String?,
    @kotlinx.serialization.SerialName("p_business_activity") val businessActivity: String?,
    @kotlinx.serialization.SerialName("p_workplace_name") val workplaceName: String?,
    @kotlinx.serialization.SerialName("p_shop_name") val shopName: String?,
    @kotlinx.serialization.SerialName("p_workshop_name") val workshopName: String?,
    @kotlinx.serialization.SerialName("p_vehicle_models") val vehicleModels: String?,
    @kotlinx.serialization.SerialName("p_workshop_worker_count") val workshopWorkerCount: Int?,
    @kotlinx.serialization.SerialName("p_supplier_role_status") val supplierRoleStatus: String?,
    @kotlinx.serialization.SerialName("p_supplier_scope") val supplierScope: String?,
    @kotlinx.serialization.SerialName("p_supplier_country") val supplierCountry: String?,
    @kotlinx.serialization.SerialName("p_supplier_currency_code") val supplierCurrencyCode: String?,
    @kotlinx.serialization.SerialName("p_supplier_specialty") val supplierSpecialty: String?,
)

/**
 * Party V2 direct transport. Identity has no client type; CUSTOMER/SUPPLIER semantics are
 * transmitted only through party_roles + normalized profiles.
 */
suspend fun SyncRuntime.pushClients(orgId: String, userId: String) {
    val identities = db.clientDao().getDirtyClientsSync(orgId)
    if (identities.isEmpty()) return
    val roleDao = db.partyRoleDao()

    identities.distinctBy { it.id }.forEachIndexed { index, local ->
        val remoteId = when (local.id) {
            "cash_client_main", CASH_CLIENT_UUID      -> cashClientUuid(orgId)
            "cash_supplier_main", CASH_SUPPLIER_UUID -> cashSupplierUuid(orgId)
            else                                       -> local.id
        }
        val customerRole = roleDao.getRole(local.id, orgId, "CUSTOMER")
        val supplierRole = roleDao.getRole(local.id, orgId, "SUPPLIER")
        val customer = roleDao.getCustomerProfileSync(orgId, local.id)
        val supplier = roleDao.getSupplierProfileSync(orgId, local.id)
        check(customerRole != null || supplierRole != null) {
            "Party ${local.id} has no organization-scoped role"
        }
        try {
            supabase.postgrest.rpc(
                "verto_upsert_party_v2",
                UpsertPartyV2Request(
                    partyId = remoteId,
                    name = local.name,
                    phone = local.phone,
                    address = local.address,
                    workplace = local.workplace,
                    generalNote = local.generalNote,
                    carType = local.carType,
                    bankAccount = local.bankAccount,
                    specialty = local.specialty,
                    secondaryPhones = local.secondaryPhones,
                    createdAt = SupabaseDateParser.format(local.createdAt),
                    customerRoleStatus = customerRole?.status,
                    customerSegment = customer?.segment,
                    ageYears = customer?.ageYears,
                    purchaseContactName = customer?.purchaseContactName,
                    businessActivity = customer?.businessActivity,
                    workplaceName = customer?.workplaceName,
                    shopName = customer?.shopName,
                    workshopName = customer?.workshopName,
                    vehicleModels = customer?.vehicleModels,
                    workshopWorkerCount = customer?.workshopWorkerCount,
                    supplierRoleStatus = supplierRole?.status,
                    supplierScope = supplier?.scope,
                    supplierCountry = supplier?.country,
                    supplierCurrencyCode = supplier?.currencyCode,
                    supplierSpecialty = supplier?.specialty,
                )
            )
            db.withTransaction {
                db.clientDao().markClientsClean(listOf(local.id))
                roleDao.markRolesClean(orgId, local.id)
                roleDao.markCustomerProfileClean(orgId, local.id)
                roleDao.markSupplierProfileClean(orgId, local.id)
                roleDao.clearLegacyRoleOutbox(local.id)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            val detail = error.message?.trim().orEmpty().ifBlank { error::class.simpleName ?: "unknown" }
            throw IllegalStateException(
                "فشل رفع الطرف ${local.name.ifBlank { local.id }} (${index + 1}/${identities.size}): $detail",
                error,
            )
        }
    }
}

suspend fun SyncRuntime.pushClientDeletions(orgId: String) {
    val pendingIds = userPrefs.getPendingClientDeletions()
    if (pendingIds.isEmpty()) return

    val failed = mutableListOf<String>()
    pendingIds.forEach { id ->
        runCatching {
            // اجلب فواتير العميل من Supabase واحذفها كاملة (بنود + مدفوعات + فاتورة)
            // قبل حذف العميل، لأن FK من invoices.client_id يمنع حذفه.
            val remoteInvoiceIds = runCatching {
                supabase.postgrest["invoices"]
                    .select {
                        filter {
                            eq("client_id", localClientIdToUuid(id, orgId))
                            eq("organization_id", orgId)
                        }
                    }
                    .decodeList<InvoiceDto>()
                    .map { it.id }
            }.getOrDefault(emptyList())

            remoteInvoiceIds.forEach { invId ->
                runCatching { deleteInvoiceCascadeOnSupabase(invId, orgId) }
                    .onFailure {
                        android.util.Log.w("SyncManager", "Remote deletion failed")
                    }
            }

            // أزل أي مدفوعات مرتبطة مباشرة بالعميل (بدون فاتورة) لو وُجدت
            runCatching {
                supabase.postgrest["payments"].delete {
                    filter {
                        eq("client_id", localClientIdToUuid(id, orgId))
                        eq("organization_id", orgId)
                    }
                }
            }.onFailure { android.util.Log.w("SyncManager", "Remote deletion failed") }

            supabase.postgrest["clients"].delete {
                filter {
                    eq("id", localClientIdToUuid(id, orgId))
                    eq("organization_id", orgId)
                }
            }
        }.onFailure { e ->
            android.util.Log.e("SyncManager", "Remote deletion failed")
            failed += "$id (${e.message?.take(80)})"
        }
        if (isGoneFromSupabase("clients", localClientIdToUuid(id, orgId), orgId)) {
            userPrefs.removePendingClientDeletion(id)
        }
    }
    if (failed.isNotEmpty()) error("لم يُحذف من Supabase: ${failed.joinToString(", ")}")
}

suspend fun SyncRuntime.pullClients(orgId: String, alreadyDeletedIds: Set<String> = emptySet()) {
    val lastPulledAt = userPrefs.getLastPulledAt(com.verto.app.utils.KEY_LAST_PULLED_CLIENTS)
    val pullStartedAt = System.currentTimeMillis()

    // Fallback transport only: ClientSyncParticipant skips this path whenever Party V2 owns
    // PARTY_IDENTITY. If rollout falls back to legacy, every fetch remains organization-scoped and
    // hydrates the normalized projections needed by Party V2 readers.
    val remoteClients = supabase.postgrest["clients"].select {
        filter {
            eq("organization_id", orgId)
            if (lastPulledAt > 0L) gte("updated_at", SupabaseDateParser.format(lastPulledAt))
        }
    }.decodeList<ClientDto>()
    val remoteRoles = supabase.postgrest["party_roles"].select {
        filter { eq("organization_id", orgId) }
    }.decodeList<PartyRoleDto>()
    val remoteCustomerProfiles = supabase.postgrest["customer_profiles"].select {
        filter { eq("organization_id", orgId) }
    }.decodeList<CustomerProfileDto>()
    val remoteSupplierProfiles = supabase.postgrest["supplier_profiles"].select {
        filter { eq("organization_id", orgId) }
    }.decodeList<SupplierProfileDto>()

    check(remoteRoles.all { it.organizationId == orgId }) { "FAIL_ORG_SCOPE: party_roles" }
    check(remoteCustomerProfiles.all { it.organizationId == orgId }) { "FAIL_ORG_SCOPE: customer_profiles" }
    check(remoteSupplierProfiles.all { it.organizationId == orgId }) { "FAIL_ORG_SCOPE: supplier_profiles" }

    val pendingDeletions = userPrefs.getPendingClientDeletions() + alreadyDeletedIds
    val localById = db.clientDao().getAllClientsSyncForOrganization(orgId).associateBy { it.id }
    val existingIds = localById.keys

    // SYNC-001 «لا تمسح بفراغ»: لا تكتب الحقل إن كانت قيمة السيرفر فارغة
    // والمحلية غير فارغة — يحمي فترة الانتقال قبل أول رفع يملأ السيرفر.
    fun keepLocalIfRemoteBlank(remoteV: String, localV: String?): String =
        if (remoteV.isBlank() && !localV.isNullOrBlank()) localV else remoteV

    // FIX-01: عكس UUID عند الجلب — CASH_CLIENT_UUID → "cash_client_main"
    val entities = remoteClients.map { dto ->
        val localId = resolveClientId(dto.id, orgId)
        val local = localById[localId]
        PartyIdentityEntity(
            id = localId,
            name = dto.name,
            phone = dto.phone,
            address = dto.address,
            workplace = dto.workplace,
            generalNote = dto.generalNote,
            carType = keepLocalIfRemoteBlank(dto.carType, local?.carType),
            bankAccount = keepLocalIfRemoteBlank(dto.bankAccount, local?.bankAccount),
            specialty = keepLocalIfRemoteBlank(dto.specialty, local?.specialty),
            secondaryPhones = keepLocalIfRemoteBlank(dto.secondaryPhones, local?.secondaryPhones),
            createdAt = SupabaseDateParser.parse(dto.createdAt),
            createdBy = dto.createdBy ?: "",
            isDirty = false,
        )
    }

    val (existing, newOnes) = entities
        .filter { it.id !in pendingDeletions }
        .partition { it.id in existingIds }

    db.withTransaction {
        if (newOnes.isNotEmpty()) db.clientDao().insertClientsFromRemote(newOnes)

        existing.forEach { c ->
            val local = localById[c.id]
            if (SyncConflictPolicy.resolve(localDirty = local?.isDirty == true) == SyncConflictResolution.KEEP_LOCAL) {
                return@forEach
            }
            db.clientDao().updateClient(c.copy(isDirty = false))
        }

        applyNormalizedPartyDirectoryFromRemote(
            orgId = orgId,
            roles = remoteRoles,
            customerProfiles = remoteCustomerProfiles,
            supplierProfiles = remoteSupplierProfiles,
            pendingDeletions = pendingDeletions,
        )
    }

    // Advance only after all four Party projections commit successfully. This prevents a client
    // cursor from hiding an incomplete normalized Party refresh on the next retry.
    userPrefs.setLastPulledAt(com.verto.app.utils.KEY_LAST_PULLED_CLIENTS, pullStartedAt)
}

private suspend fun SyncRuntime.applyNormalizedPartyDirectoryFromRemote(
    orgId: String,
    roles: List<PartyRoleDto>,
    customerProfiles: List<CustomerProfileDto>,
    supplierProfiles: List<SupplierProfileDto>,
    pendingDeletions: Set<String>,
) {
    val roleDao = db.partyRoleDao()

    roles.forEach { dto ->
        val partyId = resolveClientId(dto.partyId, orgId)
        if (partyId in pendingDeletions) return@forEach
        val existing = roleDao.getRole(partyId, orgId, dto.role)

        // Local role commands are stronger than the legacy mirror until acknowledged.
        if (existing?.dirty == true || roleDao.hasActivePartyMutation("ROLE", partyId)) return@forEach

        if (parseNullableTimestamp(dto.deletedAt) != null) {
            roleDao.deleteRoleFromRemote(orgId, partyId, dto.role)
            return@forEach
        }

        val updatedAt = SupabaseDateParser.parse(dto.serverUpdatedAt)
        roleDao.upsertRoleFromRemote(
            PartyRoleEntity(
                id = dto.id,
                partyId = partyId,
                organizationId = orgId,
                role = dto.role,
                status = dto.status,
                createdAt = existing?.createdAt ?: updatedAt,
                updatedAt = updatedAt,
                archivedAt = parseNullableTimestamp(dto.archivedAt),
                archivedBy = dto.archivedBy,
                archiveReason = dto.archiveReason,
                syncRevision = dto.serverRevision,
                dirty = false,
                deletedAt = null,
            )
        )
    }

    customerProfiles.forEach { dto ->
        val partyId = resolveClientId(dto.partyId, orgId)
        if (partyId in pendingDeletions) return@forEach
        val existing = roleDao.getCustomerProfileSync(orgId, partyId)
        if (existing?.dirty == true) return@forEach

        if (parseNullableTimestamp(dto.deletedAt) != null) {
            roleDao.deleteCustomerProfileFromRemote(orgId, partyId)
            return@forEach
        }

        roleDao.saveCustomerProfile(
            CustomerProfileEntity(
                organizationId = orgId,
                partyId = partyId,
                segment = dto.segment,
                ageYears = dto.ageYears,
                purchaseContactName = dto.purchaseContactName,
                businessActivity = dto.businessActivity,
                workplaceName = dto.workplaceName,
                shopName = dto.shopName,
                workshopName = dto.workshopName,
                vehicleModels = dto.vehicleModels,
                workshopWorkerCount = dto.workshopWorkerCount,
                updatedAt = SupabaseDateParser.parse(dto.serverUpdatedAt),
                syncRevision = dto.serverRevision,
                dirty = false,
            )
        )
    }

    supplierProfiles.forEach { dto ->
        val partyId = resolveClientId(dto.partyId, orgId)
        if (partyId in pendingDeletions) return@forEach
        val existing = roleDao.getSupplierProfileSync(orgId, partyId)
        if (existing?.dirty == true) return@forEach

        if (parseNullableTimestamp(dto.deletedAt) != null) {
            roleDao.deleteSupplierProfileFromRemote(orgId, partyId)
            return@forEach
        }

        roleDao.saveSupplierProfile(
            SupplierProfileEntity(
                organizationId = orgId,
                partyId = partyId,
                scope = dto.scope,
                country = dto.country,
                currencyCode = dto.currencyCode,
                specialty = dto.specialty,
                updatedAt = SupabaseDateParser.parse(dto.serverUpdatedAt),
                syncRevision = dto.serverRevision,
                dirty = false,
            )
        )
    }
}

private fun parseNullableTimestamp(value: String?): Long? =
    value?.takeIf { it.isNotBlank() }
        ?.let(SupabaseDateParser::parse)
        ?.takeIf { it > 0L }

