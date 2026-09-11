package com.verto.app.feature.commission.bridge
import com.verto.app.data.remote.RoleProvider
import com.verto.app.data.remote.dto.CommissionEligibilityDto
import com.verto.app.data.remote.dto.toRemoteDouble
import com.verto.app.data.repository.WithdrawalRepository
import com.verto.app.feature.party.domain.repository.PartyDirectoryGateway
import com.verto.app.feature.commission.application.*
import com.verto.app.utils.DateUtils
import com.verto.app.utils.MoneyMath
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow
@Singleton
class CommissionReportGatewayAdapter @Inject constructor(private val withdrawalRepository: WithdrawalRepository, private val partyDirectory: PartyDirectoryGateway, private val roleProvider: RoleProvider): CommissionReportGateway{
 override val role: StateFlow<String?> = roleProvider.role
 override suspend fun sendAdminReminder(clientId: String, message: String, navRoute: String): Result<Unit> { val orgId = withdrawalRepository.currentOrgId() ?: return Result.failure(IllegalStateException("تعذّر الوصول لبيانات المؤسسة")); return withdrawalRepository.sendAdminReminder(clientId, orgId, message, navRoute) }
 override suspend fun buildMarketerCommissionReport(
     clientId: String,
     from: Long?,
     to: Long?,
 ): Result<MarketerCommissionReportItem> = runCatching {
     val name = partyDirectory.getClientByIdSync(clientId)?.name ?: "المسوّق"
     val rows = withdrawalRepository.getCommissionEligibility().getOrThrow()
         .filter { it.clientId == clientId }
         .filter {
             from == null || to == null ||
                 (parse(it.createdAt)?.let { time -> time in from..to } ?: true)
         }
         .sortedByDescending { parse(it.createdAt) ?: 0L }
     val reportRows = rows.map { row ->
         MarketerCommissionRowItem(
             invoiceNumber = row.invoiceNumber,
             dateLabel = parse(row.createdAt)?.let(DateUtils::formatDate) ?: "—",
             invoiceTotal = row.totalAmount.toRemoteDouble(),
             commission = row.commission.toRemoteDouble(),
             statusLabel = when (row.eligibility) {
                 "WITHDRAWABLE" -> "قابلة للسحب"
                 "PAID" -> "مدفوعة"
                 "PENDING" -> "قيد الانتظار"
                 else -> row.eligibility
             },
         )
     }
     fun sum(predicate: (CommissionEligibilityDto) -> Boolean): Double =
         with(MoneyMath) {
             rows.filter(predicate).map { it.commission.toRemoteDouble() }.moneySum()
         }
     MarketerCommissionReportItem(
         marketerName = name,
         periodLabel = if (from != null && to != null) {
             "${DateUtils.formatDate(from)} — ${DateUtils.formatDate(to)}"
         } else {
             "كل الفترات"
         },
         rows = reportRows,
         totalCommission = sum { true },
         withdrawableTotal = sum { it.eligibility == "WITHDRAWABLE" },
         paidTotal = sum { it.eligibility == "PAID" },
         pendingTotal = sum { it.eligibility == "PENDING" },
     )
 }
 private fun parse(iso:String?):Long?=iso?.let{runCatching{SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.US).apply{timeZone=TimeZone.getTimeZone("UTC")}.parse(it.take(19))?.time}.getOrNull()}
}
