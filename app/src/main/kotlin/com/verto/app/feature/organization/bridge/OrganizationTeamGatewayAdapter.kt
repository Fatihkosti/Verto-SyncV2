package com.verto.app.feature.organization.bridge
import com.verto.app.data.model.EmployeePermissions as DataEmployeePermissions
import com.verto.app.data.model.EmployeeWithPermissions as DataEmployeeWithPermissions
import com.verto.app.data.remote.AuthRepository
import com.verto.app.feature.organization.domain.model.CreateOrganizationInviteRequest
import com.verto.app.feature.organization.domain.model.EmployeePermissions
import com.verto.app.feature.organization.domain.model.OrganizationEmployee
import com.verto.app.feature.organization.domain.repository.OrganizationTeamGateway
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrganizationTeamGatewayAdapter @Inject constructor(
    private val authRepository: AuthRepository
) : OrganizationTeamGateway {

    override suspend fun getEmployees(): Result<List<OrganizationEmployee>> =
        authRepository.getOrgEmployees().map { employees -> employees.map(DataEmployeeWithPermissions::toDomain) }

    override suspend fun getEmployeePermissions(employeeId: String): Result<EmployeePermissions> =
        authRepository.getEmployeePermissions(employeeId).map(DataEmployeePermissions::toDomain)

    override suspend fun saveEmployeePermissions(permissions: EmployeePermissions): Result<Unit> =
        authRepository.saveEmployeePermissions(permissions.toData())

    override suspend fun removeEmployee(employeeId: String): Result<Unit> =
        authRepository.removeEmployee(employeeId)

    override suspend fun generateInviteCode(request: CreateOrganizationInviteRequest): Result<String> =
        authRepository.generateInviteCode(
            employeeName = request.employeeName,
            jobTitle = request.jobTitle,
            actualJoinDate = request.actualJoinDate,
            permissions = request.permissions.toData()
        )
}

private fun DataEmployeeWithPermissions.toDomain(): OrganizationEmployee = OrganizationEmployee(
    userId = userId,
    name = name,
    email = email,
    joinedAt = joinedAt,
    isActive = isActive,
    permissions = permissions.toDomain()
)

private fun DataEmployeePermissions.toDomain(): EmployeePermissions = EmployeePermissions(
    userId = userId,
    orgId = orgId,
    salesView = salesView,
    salesCreate = salesCreate,
    salesEdit = salesEdit,
    salesDelete = salesDelete,
    salesExport = salesExport,
    purchasesView = purchasesView,
    purchasesCreate = purchasesCreate,
    purchasesEdit = purchasesEdit,
    purchasesDelete = purchasesDelete,
    purchasesExport = purchasesExport,
    clientsView = clientsView,
    clientsEdit = clientsEdit,
    clientsDelete = clientsDelete,
    clientsAddPayment = clientsAddPayment,
    suppliersAddPayment = suppliersAddPayment,
    inventoryView = inventoryView,
    inventoryEdit = inventoryEdit,
    inventoryPrice = inventoryPrice,
    inventoryImport = inventoryImport,
    inventoryExport = inventoryExport,
    reportsSummary = reportsSummary,
    reportsDetails = reportsDetails,
    reportsFull = reportsFull,
    reportsExport = reportsExport,
    expensesView = expensesView,
    expensesCreate = expensesCreate,
    expensesDelete = expensesDelete,
    cashAdjust = cashAdjust,
    paymentsReverse = paymentsReverse,
    commissionManage = commissionManage,
    marketingDashboards = marketingDashboards,
    shipmentsView = shipmentsView,
    shipmentsManage = shipmentsManage,
    shipmentsConfirm = shipmentsConfirm,
    settingsPassword = settingsPassword,
    settingsOrgData = settingsOrgData,
    settingsReset = settingsReset,
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

private fun EmployeePermissions.toData(): DataEmployeePermissions = DataEmployeePermissions(
    userId = userId,
    orgId = orgId,
    salesView = salesView,
    salesCreate = salesCreate,
    salesEdit = salesEdit,
    salesDelete = salesDelete,
    salesExport = salesExport,
    purchasesView = purchasesView,
    purchasesCreate = purchasesCreate,
    purchasesEdit = purchasesEdit,
    purchasesDelete = purchasesDelete,
    purchasesExport = purchasesExport,
    clientsView = clientsView,
    clientsEdit = clientsEdit,
    clientsDelete = clientsDelete,
    clientsAddPayment = clientsAddPayment,
    suppliersAddPayment = suppliersAddPayment,
    inventoryView = inventoryView,
    inventoryEdit = inventoryEdit,
    inventoryPrice = inventoryPrice,
    inventoryImport = inventoryImport,
    inventoryExport = inventoryExport,
    reportsSummary = reportsSummary,
    reportsDetails = reportsDetails,
    reportsFull = reportsFull,
    reportsExport = reportsExport,
    expensesView = expensesView,
    expensesCreate = expensesCreate,
    expensesDelete = expensesDelete,
    cashAdjust = cashAdjust,
    paymentsReverse = paymentsReverse,
    commissionManage = commissionManage,
    marketingDashboards = marketingDashboards,
    shipmentsView = shipmentsView,
    shipmentsManage = shipmentsManage,
    shipmentsConfirm = shipmentsConfirm,
    settingsPassword = settingsPassword,
    settingsOrgData = settingsOrgData,
    settingsReset = settingsReset,
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
