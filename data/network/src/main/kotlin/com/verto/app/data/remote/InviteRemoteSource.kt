package com.verto.app.data.remote

import com.verto.app.core.error.AuthErrorCodes
import com.verto.app.core.error.BusinessRuleFailureException
import com.verto.app.core.security.TenantIsolationPolicy
import com.verto.app.core.session.domain.SessionWriter
import com.verto.app.data.model.EmployeePermissions
import com.verto.app.data.remote.dto.EmployeePermissionsDto
import com.verto.app.utils.PreferencesManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class InviteRemoteSource(
    private val prefs: PreferencesManager,
    private val sessionWriter: SessionWriter,
    private val accountRemoteSource: AuthAccountRemoteSource
) {
    private val client by lazy { VertoSupabase.client }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

suspend fun joinOrganization(
    inviteCode: String,
    email: String,
    password: String,
    memberPhone: String = ""
): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        val invite = getInviteCodeDetails(inviteCode).getOrThrow()

        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        val uid = client.auth.currentSessionOrNull()?.user?.id
            ?: client.auth.currentUserOrNull()?.id
            ?: throw BusinessRuleFailureException(AuthErrorCodes.PROVISIONING_INCOMPLETE)

        client.postgrest.rpc(
            "join_organization_with_code",
            JoinOrgRequest(
                inviteCode = inviteCode.trim().uppercase(),
                memberName = invite.employeeName,
                memberUid  = uid
            )
        )

        prefs.setOwnerName(invite.employeeName)
        sessionWriter.setUserId(uid)
        sessionWriter.setUserName(invite.employeeName)
        if (memberPhone.isNotBlank()) sessionWriter.setUserPhone(memberPhone)

        Unit
    }
}

suspend fun generateInviteCode(
    employeeName: String,
    jobTitle: String,
    actualJoinDate: String,
    permissions: EmployeePermissions = EmployeePermissions.defaultEmployee()
): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        val profile = accountRemoteSource.getMyProfile() ?: error("المستخدم غير مسجل")
        TenantIsolationPolicy.requireAdmin(profile.role)
        TenantIsolationPolicy.requireTenantId(profile.organizationId)
        val cleanName = employeeName.trim()
        val cleanJobTitle = jobTitle.trim()
        val cleanJoinDate = actualJoinDate.trim()
        if (cleanName.isBlank()) error("اسم الموظف مطلوب")
        if (cleanJobTitle.isBlank()) error("المسمى الوظيفي مطلوب")
        if (cleanJoinDate.isBlank()) error("تاريخ الانضمام مطلوب")
        val safePermissions = permissions.withoutRestrictedManagementOptimalPermissions()
        if (!safePermissions.hasAnyEnabledPermission()) error("حدد صلاحية واحدة على الأقل")

        val permissionsJson = json.encodeToString(safePermissions.toDto())

        client.postgrest.rpc(
            "admin_create_invite_code",
            AdminCreateInviteCodeRequest(
                employeeName = cleanName,
                jobTitle = cleanJobTitle,
                actualJoinDate = cleanJoinDate,
                permissions = permissionsJson,
                expiresInHours = 72
            )
        ).data.trim().trim('"').also { generatedCode ->
            if (generatedCode.isBlank()) {
                error("تعذّر إنشاء كود الدعوة، حاول مرة أخرى")
            }
        }
    }
}

suspend fun revokeInviteCode(inviteCode: String): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        val profile = accountRemoteSource.getMyProfile() ?: error("المستخدم غير مسجل")
        TenantIsolationPolicy.requireAdmin(profile.role)
        TenantIsolationPolicy.requireTenantId(profile.organizationId)
        val code = TenantIsolationPolicy.requireIdentifier(inviteCode.trim().uppercase(), "inviteCode")
        client.postgrest.rpc(
            "admin_revoke_invite_code",
            InviteCodeLookupRequest(inviteCode = code)
        )
        Unit
    }
}

suspend fun getInviteCodeDetails(inviteCode: String): Result<InviteCodeDetails> =
    withContext(Dispatchers.IO) {
        runCatching {
            val code = inviteCode.trim().uppercase()
            if (code.isBlank()) throw BusinessRuleFailureException(
                AuthErrorCodes.INVALID_INVITE,
                target = "invite",
            )

            val row = json.decodeFromString<InviteCodeDetailsRow>(
                client.postgrest.rpc(
                    "get_invite_code_details",
                    InviteCodeLookupRequest(inviteCode = code)
                ).data
            )

            if (row.used) throw BusinessRuleFailureException(
                AuthErrorCodes.INVITE_USED,
                target = "invite",
            )
            if (SupabaseTimestamp.isExpired(row.expiresAt)) {
                throw BusinessRuleFailureException(
                    AuthErrorCodes.INVALID_INVITE,
                    target = "invite",
                )
            }
            if (row.employeeName.isBlank()) {
                throw BusinessRuleFailureException(
                    AuthErrorCodes.INVALID_INVITE,
                    target = "invite",
                )
            }

            InviteCodeDetails(
                code = row.code,
                employeeName = row.employeeName,
                jobTitle = row.jobTitle,
                actualJoinDate = row.actualJoinDate.orEmpty()
            )
        }
    }
}

private object SupabaseTimestamp {
    private val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }

    fun plusHours(hours: Long): String =
        parser.format(java.util.Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(hours)))

    fun isExpired(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return runCatching {
            parser.parse(
                value.trim()
                    .replace(" ", "T")
                    .replace(Regex("\\.\\d+"), "")
                    .replace(Regex("\\+00:?00$"), "Z")
                    .replace(Regex("\\+00$"), "Z")
            )?.time ?: Long.MAX_VALUE
        }.getOrDefault(Long.MAX_VALUE) < System.currentTimeMillis()
    }
}

private fun EmployeePermissions.toDto() = EmployeePermissionsDto(
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

private fun EmployeePermissions.hasAnyEnabledPermission(): Boolean = listOf(
    salesView, salesCreate, salesEdit, salesDelete, salesExport,
    purchasesView, purchasesCreate, purchasesEdit, purchasesDelete, purchasesExport,
    clientsView, clientsEdit, clientsDelete, clientsAddPayment, suppliersAddPayment,
    inventoryView, inventoryEdit, inventoryPrice, inventoryImport, inventoryExport,
    reportsSummary, reportsDetails, reportsFull, reportsExport,
    expensesView, expensesCreate, expensesDelete, cashAdjust,
    paymentsReverse,
    commissionManage, marketingDashboards,
    shipmentsView, shipmentsManage, shipmentsConfirm,
    settingsPassword, settingsOrgData, settingsReset,
    viewManagement, viewOptimal, viewOptimalCompanies, issueOptimalCode,
    viewOptimalMessages, sendOptimalMessages, viewOptimalInvoices,
    viewOptimalMaintenance, viewOptimalSyncIssues, retryOptimalSync
).any { it }

@Serializable
data class JoinOrgRequest(
    @SerialName("invite_code") val inviteCode: String,
    @SerialName("member_name") val memberName: String,
    @SerialName("member_uid") val memberUid: String
)

@Serializable
data class InviteCodeLookupRequest(
    @SerialName("invite_code") val inviteCode: String
)

@Serializable
private data class AdminCreateInviteCodeRequest(
    @SerialName("p_employee_name") val employeeName: String?,
    @SerialName("p_job_title") val jobTitle: String?,
    @SerialName("p_actual_join_date") val actualJoinDate: String?,
    @SerialName("p_permissions") val permissions: String,
    @SerialName("p_expires_in_hours") val expiresInHours: Int = 72,
    @SerialName("p_code") val code: String? = null,
    @SerialName("p_marketer_client_id") val marketerClientId: String? = null,
)

data class InviteCodeDetails(
    val code: String,
    val employeeName: String,
    val jobTitle: String,
    val actualJoinDate: String
)

@Serializable
private data class InviteCodeDetailsRow(
    @SerialName("code") val code: String,
    @SerialName("used") val used: Boolean = false,
    @SerialName("employee_name") val employeeName: String = "",
    @SerialName("job_title") val jobTitle: String = "",
    @SerialName("actual_join_date") val actualJoinDate: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null
)
