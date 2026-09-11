package com.verto.app.feature.integration.optimal.data

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.core.session.model.*
import com.verto.app.feature.integration.optimal.domain.model.*
import com.verto.app.feature.integration.optimal.domain.repository.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class GuardedOptimalRegistrationRepositoryTest {
    @Test fun `blank client id never reaches remote`() = runTest { val f=Fixture(); val r=f.repo.issueCompanyJoinCode(" "); assertSame(OptimalRegistrationResult.CompanyUnavailable,r); assertEquals(0,f.remote.calls) }
    @Test fun `linked company is rejected before code issue`() = runTest { val f=Fixture(link=OptimalCompanyLinkStatus.LINKED); assertSame(OptimalRegistrationResult.CompanyAlreadyLinked,f.repo.issueCompanyJoinCode("client")); assertEquals(0,f.remote.calls) }
    @Test fun `mismatched remote tenant is rejected`() = runTest { val f=Fixture(); f.remote.value=f.remote.value.copy(organizationId="other"); assertSame(OptimalRegistrationResult.RemoteResponseRejected,f.repo.issueCompanyJoinCode("client")) }
    @Test fun `valid future code succeeds`() = runTest { val f=Fixture(); val r=f.repo.issueCompanyJoinCode(" client "); assertTrue(r is OptimalRegistrationResult.Success); assertEquals("12345678",(r as OptimalRegistrationResult.Success).code.code); assertEquals(2,f.guard.calls) }
    @Test fun `permission denial at first guard stops repository`() = runTest { val f=Fixture(); f.guard.decisions=ArrayDeque(listOf(OptimalAccessDecision.PermissionDenied)); assertSame(OptimalRegistrationResult.PermissionDenied,f.repo.issueCompanyJoinCode("client")); assertEquals(0,f.remote.calls) }
    private class Fixture(link:OptimalCompanyLinkStatus=OptimalCompanyLinkStatus.UNLINKED) { val guard=FakeGuard(); val remote=FakeRemote(); val session=FakeSession(); val company=OptimalCompany("org","client","Acme","COMPANY",link,null,null,0); val companies=object:OptimalCompanyRepository{override fun observeCompanies(organizationId:String,searchTerm:String,linkFilter:OptimalCompanyLinkFilter)=flowOf(listOf(company))}; val repo=GuardedOptimalRegistrationRepository(guard,remote,session,companies,OptimalClock{1_000L}) }
    private class FakeGuard:OptimalOperationGuard { var calls=0; var decisions=ArrayDeque<OptimalAccessDecision>(); override suspend fun check(operation:OptimalOperation,layer:OptimalGuardLayer,details:String):OptimalAccessDecision { calls++; return if(decisions.isEmpty()) OptimalAccessDecision.Granted else decisions.removeFirst() } }
    private class FakeRemote:OptimalRegistrationRemoteSource { var calls=0; var value=OptimalRegistrationRemoteCode("org","client","Acme","12345678","2030-01-01T00:00:00Z"); override suspend fun issueCompanyJoinCode(clientId:String):OptimalRegistrationRemoteCode { calls++; return value } }
    private class FakeSession:SessionReader { override val userId=flowOf("u"); override val userName=flowOf("U"); override val userPhone=flowOf(""); override val role=flowOf("admin"); override val permissionsJson=flowOf(""); override val organizationId=flowOf("org"); override val currentUser=flowOf(CurrentUser("u","U","")); override val currentOrganization=flowOf(CurrentOrganization("org")); override suspend fun snapshot()=SessionState(CurrentUser("u","U",""),CurrentOrganization("org"),"admin","") }
}
