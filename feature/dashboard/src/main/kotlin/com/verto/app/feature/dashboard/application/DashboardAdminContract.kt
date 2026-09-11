package com.verto.app.feature.dashboard.application

import kotlinx.coroutines.flow.StateFlow

data class CommissionEligibilityItem(val invoiceId:String="",val clientId:String="",val commission:Double=0.0,val invoiceNumber:Int=0,val totalAmount:Double=0.0,val invoiceStatus:String="",val createdAt:String?=null,val eligibility:String="")
data class MarketerStatsItem(val clientId:String="",val fullName:String="",val phone:String="",val accountType:String="",val workshopName:String?=null,val joinedAt:String?=null,val lastSeenAt:String?=null,val invoicesCount:Int=0,val purchasesTotal:Double=0.0,val commissionTotal:Double=0.0,val balance:Double=0.0,val pendingWithdrawalsCount:Int=0,val pendingWithdrawalsAmount:Double=0.0,val completedWithdrawalsAmount:Double=0.0,val activeWeeks:Int=0,val streakWeeks:Int=0,val lastMessageBody:String?=null,val lastMessageAt:String?=null)
interface DashboardAdminGateway {
 val role: StateFlow<String?>
 suspend fun getMarketerStats(): Result<List<MarketerStatsItem>>
 suspend fun getCommissionEligibility(): Result<List<CommissionEligibilityItem>>
 suspend fun sendInactivityReminder(clientId:String,clientName:String): Result<Unit>
 suspend fun createNewConversation(clientId:String,subject:String): Result<String>
 suspend fun sendAdminReminder(clientId:String,message:String,navRoute:String): Result<Unit>
}
fun List<CommissionEligibilityItem>.withdrawableByClient():Map<String,Double> = asSequence().filter{it.eligibility=="WITHDRAWABLE"}.groupBy{it.clientId}.mapValues{(_,rows)->rows.sumOf{it.commission}}
