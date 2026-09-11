package com.verto.app.data.remote

import com.verto.app.core.security.TenantIsolationPolicy
import com.verto.app.data.model.EmployeePermissions
import com.verto.app.data.model.EmployeeWithPermissions
import com.verto.app.data.remote.dto.AppUserDto
import com.verto.app.data.remote.dto.EmployeePermissionsPayloadDto
import com.verto.app.data.remote.dto.EmployeePermissionsRowDto
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal class OrganizationAccessRemoteSource(
    private val accountRemoteSource: AuthAccountRemoteSource
) {
    private val client by lazy { VertoSupabase.client }

suspend fun getOrgEmployees(): Result<List<EmployeeWithPermissions>> =
    withContext(Dispatchers.IO) {
        runCatching {
            val profile = accountRemoteSource.getMyProfile() ?: error("المستخدم غير مسجل")
            TenantIsolationPolicy.requireAdmin(profile.role)
            val orgId = TenantIsolationPolicy.requireTenantId(profile.organizationId)

            val users = client.postgrest["app_users"]
                .select {
                    filter {
                        eq("organization_id", orgId)
                        neq("id", profile.id)
                    }
                }
                .decodeList<AppUserDto>()
                .onEach { TenantIsolationPolicy.requireSameTenant(orgId, it.organizationId) }

            val allPerms = client.postgrest["employee_permissions"]
                .select { filter { eq("org_id", orgId) } }
                .decodeList<EmployeePermissionsRowDto>()
                .onEach { TenantIsolationPolicy.requireSameTenant(orgId, it.orgId) }
                .associateBy { it.userId }

            users.map { user ->
                val permsDto = allPerms[user.id]
                EmployeeWithPermissions(
                    userId = user.id,
                    name = user.name,
                    joinedAt = user.createdAt ?: "",
                    isActive = user.isActive,
                    permissions = permsDto?.toDomain()
                        ?: EmployeePermissions.defaultEmployee(user.id, orgId)
                )
            }
        }
    }

suspend fun getEmployeePermissions(userId: String): Result<EmployeePermissions> =
    withContext(Dispatchers.IO) {
        runCatching {
            val targetUserId = TenantIsolationPolicy.requireIdentifier(userId, "employeeId")
            val profile = accountRemoteSource.getMyProfile() ?: error("المستخدم غير مسجل")
            TenantIsolationPolicy.requireAdmin(profile.role)
            val orgId = TenantIsolationPolicy.requireTenantId(profile.organizationId)

            val employee = client.postgrest["app_users"]
                .select {
                    filter {
                        eq("id", targetUserId)
                        eq("organization_id", orgId)
                    }
                }
                .decodeList<AppUserDto>()
                .singleOrNull()
                ?: error("الموظف غير موجود في هذه المؤسسة")
            TenantIsolationPolicy.requireSameTenant(orgId, employee.organizationId)

            val rows = client.postgrest["employee_permissions"]
                .select {
                    filter {
                        eq("user_id", targetUserId)
                        eq("org_id", orgId)
                    }
                }
                .decodeList<EmployeePermissionsRowDto>()
            if (rows.size > 1) error("بيانات صلاحيات الموظف غير متسقة")
            rows.firstOrNull()?.also {
                TenantIsolationPolicy.requireSameTenant(orgId, it.orgId)
            }?.toDomain() ?: EmployeePermissions.defaultEmployee(targetUserId, orgId)
        }
    }

/**
 * صلاحيات المستخدم **الحالي** نفسه (لا تتطلب دور admin — تعمل للموظف).
 * تُستخدم لكاش الصلاحيات محلياً وفرضها (الجلسة 3). المدير يُعامَل كصلاحيات كاملة في الطبقة الأعلى.
 * عند غياب صف صلاحيات → الافتراضي «عرض فقط» (`defaultEmployee`). RLS تضمن أن الموظف يرى صفّه فقط.
 */
suspend fun fetchMyPermissions(): Result<EmployeePermissions> =
    withContext(Dispatchers.IO) {
        runCatching {
            val profile = accountRemoteSource.getMyProfile() ?: error("المستخدم غير مسجل")
            val userId = TenantIsolationPolicy.requireIdentifier(profile.id, "userId")
            val orgId = TenantIsolationPolicy.requireTenantId(profile.organizationId)
            val rows = client.postgrest["employee_permissions"]
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("org_id", orgId)
                    }
                }
                .decodeList<EmployeePermissionsRowDto>()
            if (rows.size > 1) error("بيانات صلاحيات المستخدم غير متسقة")
            rows.firstOrNull()?.also {
                TenantIsolationPolicy.requireSameTenant(orgId, it.orgId)
            }?.toDomain() ?: EmployeePermissions.defaultEmployee(userId, orgId)
        }
    }

suspend fun saveEmployeePermissions(permissions: EmployeePermissions): Result<Unit> =
    withContext(Dispatchers.IO) {
        runCatching {
            val targetUserId = TenantIsolationPolicy.requireIdentifier(permissions.userId, "employeeId")
            val profile = accountRemoteSource.getMyProfile() ?: error("المستخدم غير مسجل")
            TenantIsolationPolicy.requireAdmin(profile.role)
            val orgId = TenantIsolationPolicy.requireTenantId(profile.organizationId)
            if (permissions.orgId.isNotBlank()) {
                TenantIsolationPolicy.requireSameTenant(orgId, permissions.orgId)
            }

            val employee = client.postgrest["app_users"]
                .select {
                    filter {
                        eq("id", targetUserId)
                        eq("organization_id", orgId)
                    }
                }
                .decodeList<AppUserDto>()
                .singleOrNull()
                ?: error("الموظف غير موجود في هذه المؤسسة")
            TenantIsolationPolicy.requireSameTenant(orgId, employee.organizationId)

            val safePermissions = permissions
                .copy(userId = targetUserId, orgId = orgId)
                .withoutRestrictedManagementOptimalPermissions()
            client.postgrest["employee_permissions"]
                .upsert(safePermissions.toRowDto()) {
                    onConflict = "user_id,org_id"
                }
            Unit
        }
    }

suspend fun removeEmployee(userId: String): Result<Unit> =
    withContext(Dispatchers.IO) {
        runCatching {
            val targetUserId = TenantIsolationPolicy.requireIdentifier(userId, "employeeId")
            val profile = accountRemoteSource.getMyProfile() ?: error("المستخدم غير مسجل")
            TenantIsolationPolicy.requireAdmin(profile.role)
            TenantIsolationPolicy.requireDifferentActor(profile.id, targetUserId)
            val orgId = TenantIsolationPolicy.requireTenantId(profile.organizationId)

            val employee = client.postgrest["app_users"]
                .select {
                    filter {
                        eq("id", targetUserId)
                        eq("organization_id", orgId)
                    }
                }
                .decodeList<AppUserDto>()
                .singleOrNull()
                ?: error("الموظف غير موجود في هذه المؤسسة")
            TenantIsolationPolicy.requireSameTenant(orgId, employee.organizationId)

            client.postgrest["employee_permissions"].delete {
                filter {
                    eq("user_id", targetUserId)
                    eq("org_id", orgId)
                }
            }
            client.postgrest["app_users"].update(UserActiveUpdate(isActive = false)) {
                filter {
                    eq("id", targetUserId)
                    eq("organization_id", orgId)
                }
            }
            Unit
        }
    }

suspend fun deactivateMember(memberUserId: String, reason: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        val profile = accountRemoteSource.getMyProfile() ?: error("المستخدم غير مسجل")
        TenantIsolationPolicy.requireAdmin(profile.role)
        TenantIsolationPolicy.requireTenantId(profile.organizationId)
        val targetUserId = TenantIsolationPolicy.requireIdentifier(memberUserId, "memberUserId")
        TenantIsolationPolicy.requireDifferentActor(profile.id, targetUserId)
        client.postgrest.rpc(
            "deactivate_org_member_admin",
            DeactivateMemberRequest(memberUid = targetUserId, reason = reason.trim().take(500))
        )
        Unit
    }
}
}

private fun EmployeePermissionsRowDto.toDomain() = permissions.toDomain(userId, orgId)

private fun EmployeePermissionsPayloadDto.toDomain(userId: String, orgId: String) = EmployeePermissions(
    userId = userId, orgId = orgId,
    salesView = salesView, salesCreate = salesCreate, salesEdit = salesEdit,
    salesDelete = salesDelete, salesExport = salesExport,
    purchasesView = purchasesView, purchasesCreate = purchasesCreate, purchasesEdit = purchasesEdit,
    purchasesDelete = purchasesDelete, purchasesExport = purchasesExport,
    clientsView = clientsView, clientsEdit = clientsEdit,
    clientsDelete = clientsDelete, clientsAddPayment = clientsAddPayment,
    suppliersAddPayment = suppliersAddPayment,
    inventoryView = inventoryView, inventoryEdit = inventoryEdit, inventoryPrice = inventoryPrice,
    inventoryImport = inventoryImport, inventoryExport = inventoryExport,
    reportsSummary = reportsSummary, reportsDetails = reportsDetails, reportsFull = reportsFull, reportsExport = reportsExport,
    expensesView = expensesView, expensesCreate = expensesCreate, expensesDelete = expensesDelete, cashAdjust = cashAdjust,
    paymentsReverse = paymentsReverse,
    commissionManage = commissionManage, marketingDashboards = marketingDashboards,
    shipmentsView = shipmentsView, shipmentsManage = shipmentsManage, shipmentsConfirm = shipmentsConfirm,
    settingsPassword = settingsPassword, settingsOrgData = settingsOrgData, settingsReset = settingsReset,
    viewManagement = viewManagement,
    viewOptimal = viewOptimal,
    viewOptimalCompanies = viewOptimalCompanies,
    issueOptimalCode = issueOptimalCode,
    viewOptimalMessages = viewOptimalMessages,
    sendOptimalMessages = sendOptimalMessages,
    viewOptimalInvoices = viewOptimalInvoices,
    viewOptimalMaintenance = viewOptimalMaintenance,
    viewOptimalSyncIssues = viewOptimalSyncIssues,
    retryOptimalSync = retryOptimalSync
)

private fun EmployeePermissions.toRowDto() = EmployeePermissionsRowDto(
    userId = userId,
    orgId = orgId,
    permissions = toPayloadDto()
)

private fun EmployeePermissions.toPayloadDto() = EmployeePermissionsPayloadDto(
    salesView = salesView, salesCreate = salesCreate, salesEdit = salesEdit,
    salesDelete = salesDelete, salesExport = salesExport,
    purchasesView = purchasesView, purchasesCreate = purchasesCreate, purchasesEdit = purchasesEdit,
    purchasesDelete = purchasesDelete, purchasesExport = purchasesExport,
    clientsView = clientsView, clientsEdit = clientsEdit,
    clientsDelete = clientsDelete, clientsAddPayment = clientsAddPayment,
    suppliersAddPayment = suppliersAddPayment,
    inventoryView = inventoryView, inventoryEdit = inventoryEdit, inventoryPrice = inventoryPrice,
    inventoryImport = inventoryImport, inventoryExport = inventoryExport,
    reportsSummary = reportsSummary, reportsDetails = reportsDetails, reportsFull = reportsFull, reportsExport = reportsExport,
    expensesView = expensesView, expensesCreate = expensesCreate, expensesDelete = expensesDelete, cashAdjust = cashAdjust,
    paymentsReverse = paymentsReverse,
    commissionManage = commissionManage, marketingDashboards = marketingDashboards,
    shipmentsView = shipmentsView, shipmentsManage = shipmentsManage, shipmentsConfirm = shipmentsConfirm,
    settingsPassword = settingsPassword, settingsOrgData = settingsOrgData, settingsReset = settingsReset,
    viewManagement = viewManagement,
    viewOptimal = viewOptimal,
    viewOptimalCompanies = viewOptimalCompanies,
    issueOptimalCode = issueOptimalCode,
    viewOptimalMessages = viewOptimalMessages,
    sendOptimalMessages = sendOptimalMessages,
    viewOptimalInvoices = viewOptimalInvoices,
    viewOptimalMaintenance = viewOptimalMaintenance,
    viewOptimalSyncIssues = viewOptimalSyncIssues,
    retryOptimalSync = retryOptimalSync
)

@Serializable
private data class DeactivateMemberRequest(
    @SerialName("p_member_uid") val memberUid: String,
    @SerialName("p_reason") val reason: String
)

@Serializable
private data class UserActiveUpdate(
    @SerialName("is_active") val isActive: Boolean
)
