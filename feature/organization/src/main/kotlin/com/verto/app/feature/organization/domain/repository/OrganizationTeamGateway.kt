package com.verto.app.feature.organization.domain.repository

import com.verto.app.feature.organization.domain.model.CreateOrganizationInviteRequest
import com.verto.app.feature.organization.domain.model.EmployeePermissions
import com.verto.app.feature.organization.domain.model.OrganizationEmployee

/** عقد إدارة فريق المؤسسة دون كشف تفاصيل التخزين أو الاتصال. */
interface OrganizationTeamGateway {
    suspend fun getEmployees(): Result<List<OrganizationEmployee>>
    suspend fun getEmployeePermissions(employeeId: String): Result<EmployeePermissions>
    suspend fun saveEmployeePermissions(permissions: EmployeePermissions): Result<Unit>
    suspend fun removeEmployee(employeeId: String): Result<Unit>
    suspend fun generateInviteCode(request: CreateOrganizationInviteRequest): Result<String>
}
