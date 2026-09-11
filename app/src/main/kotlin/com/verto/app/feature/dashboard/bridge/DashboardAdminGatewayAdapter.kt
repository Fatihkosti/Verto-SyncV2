package com.verto.app.feature.dashboard.bridge
import com.verto.app.data.remote.RoleProvider
import com.verto.app.data.remote.dto.CommissionEligibilityDto
import com.verto.app.data.remote.dto.MarketerStatsDto
import com.verto.app.data.remote.dto.toRemoteDouble
import com.verto.app.data.repository.WithdrawalRepository
import com.verto.app.feature.dashboard.application.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow
@Singleton
class DashboardAdminGatewayAdapter @Inject constructor(private val withdrawalRepository:WithdrawalRepository,private val roleProvider:RoleProvider):DashboardAdminGateway{
 override val role:StateFlow<String?> = roleProvider.role
 override suspend fun getMarketerStats():Result<List<MarketerStatsItem>> = withdrawalRepository.getMarketerStats().map{rows->rows.map(MarketerStatsDto::toItem)}
 override suspend fun getCommissionEligibility():Result<List<CommissionEligibilityItem>> = withdrawalRepository.getCommissionEligibility().map{rows->rows.map(CommissionEligibilityDto::toItem)}
 override suspend fun sendInactivityReminder(clientId:String,clientName:String):Result<Unit>{val orgId=withdrawalRepository.currentOrgId()?:return Result.failure(IllegalStateException("تعذّر الوصول لبيانات المؤسسة"));return withdrawalRepository.sendInactivityReminder(clientId,orgId,clientName)}
 override suspend fun createNewConversation(clientId:String,subject:String):Result<String> = withdrawalRepository.openConversation(clientId,subject).map{it.id}
 override suspend fun sendAdminReminder(clientId:String,message:String,navRoute:String):Result<Unit>{val orgId=withdrawalRepository.currentOrgId()?:return Result.failure(IllegalStateException("تعذّر الوصول لبيانات المؤسسة"));return withdrawalRepository.sendAdminReminder(clientId,orgId,message,navRoute)}
}
private fun CommissionEligibilityDto.toItem()=CommissionEligibilityItem(invoiceId,clientId,commission.toRemoteDouble(),invoiceNumber,totalAmount.toRemoteDouble(),invoiceStatus,createdAt,eligibility)
private fun MarketerStatsDto.toItem()=MarketerStatsItem(clientId,fullName,phone,accountType,workshopName,joinedAt,lastSeenAt,invoicesCount,purchasesTotal.toRemoteDouble(),commissionTotal.toRemoteDouble(),balance.toRemoteDouble(),pendingWithdrawalsCount,pendingWithdrawalsAmount.toRemoteDouble(),completedWithdrawalsAmount.toRemoteDouble(),activeWeeks,streakWeeks,lastMessageBody,lastMessageAt)
