package com.verto.app.data.repository

import com.verto.app.data.remote.AuthRepository
import com.verto.app.data.remote.VertoSupabase
import com.verto.app.data.remote.dto.AutodriveUserLookupDto
import com.verto.app.data.remote.dto.CommissionEligibilityDto
import com.verto.app.data.remote.dto.CommissionLedgerDto
import com.verto.app.data.remote.dto.CommissionPaymentDto
import com.verto.app.data.remote.dto.CommissionPaymentInvoiceDto
import com.verto.app.data.remote.dto.MarketerBalanceDto
import com.verto.app.data.remote.dto.MarketerStatsDto
import com.verto.app.data.remote.dto.RegisteredMarketerDto
import com.verto.app.data.remote.dto.toRemoteDecimal
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class CommissionRemoteSource(
    private val authRepository: AuthRepository
) {
    private val client by lazy { VertoSupabase.client }

suspend fun currentOrgId(): String? = withContext(Dispatchers.IO) {
    runCatching { authRepository.getMyProfile()?.organizationId }.getOrNull()
}

// ── العمولات: مصدر الحقيقة السيرفري (المرحلة 2.1) ───────────────────────────

/** يجلب تصنيف الأهلية لكل فاتورة عمولة من view commission_eligibility (السيرفر). */
suspend fun getCommissionEligibility(): Result<List<CommissionEligibilityDto>> =
    withContext(Dispatchers.IO) {
        runCatching {
            val profile = authRepository.getMyProfile() ?: error("غير مسجل")
            client.postgrest["commission_eligibility"]
                .select { filter { eq("org_id", profile.organizationId) } }
                .decodeList<CommissionEligibilityDto>()
        }
    }

/** أرصدة المسوّقين المحجوزة (marketer_balance) — تُستخدم في 2.2. */
suspend fun getMarketerBalances(): Result<List<MarketerBalanceDto>> =
    withContext(Dispatchers.IO) {
        runCatching {
            val profile = authRepository.getMyProfile() ?: error("غير مسجل")
            client.postgrest["marketer_balance"]
                .select { filter { eq("org_id", profile.organizationId) } }
                .decodeList<MarketerBalanceDto>()
        }
    }

/** سجل العمولات (commission_ledger) — يُستخدم في 2.3. */
suspend fun getCommissionLedger(): Result<List<CommissionLedgerDto>> =
    withContext(Dispatchers.IO) {
        runCatching {
            val profile = authRepository.getMyProfile() ?: error("غير مسجل")
            client.postgrest["commission_ledger"]
                .select { filter { eq("org_id", profile.organizationId) } }
                .decodeList<CommissionLedgerDto>()
        }
    }

/** إحصائيات المسوّقين للوحة المستخدمين (المرحلة 4.4) — RPC مُحصَّن admin سيرفرياً. */
suspend fun getMarketerStats(): Result<List<MarketerStatsDto>> =
    withContext(Dispatchers.IO) {
        runCatching {
            client.postgrest
                .rpc("get_marketer_stats")
                .decodeList<MarketerStatsDto>()
        }
    }

/**
 * يرسل تذكير انقطاع (`INACTIVITY`) لمسوّق منقطع — يُستخدم من زر التذكير في لوحة المستخدمين.
 * يحوّل `clientId` إلى `user_id` عبر [getMarketerUserId] ثم يُدرج إشعاراً.
 */
suspend fun sendInactivityReminder(
    clientId: String,
    orgId: String,
    marketerName: String
): Result<Unit> =
    withContext(Dispatchers.IO) {
        val userId = getMarketerUserId(clientId)
            ?: return@withContext Result.failure(IllegalStateException("لا يوجد حساب AutoDrive لهذا المسوّق"))
        insertNotification(
            userId   = userId,
            clientId = clientId,
            orgId    = orgId,
            type     = "INACTIVITY",
            title    = "اشتقنا لك 👋",
            body     = "لم نرَك منذ فترة يا $marketerName — افتح التطبيق وتابع عمولاتك."
        )
    }

/**
 * تذكير إداري موجّه (`ADMIN_REMINDER`) لمسوّق محدّد — يُستخدم من زر «تذكير» وزر «إرسال التقرير»
 * في لوحة المستخدمين. بلا عنوان (نص الرسالة فقط)، مع [navRoute] لربط الإشعار بشاشة AutoDrive
 * (الافتراضي `home`). تمرير `nav_route` عبر `data` ليصل لـ FCM ثم لـ navController.
 */
suspend fun sendAdminReminder(
    clientId: String,
    orgId: String,
    message: String,
    navRoute: String
): Result<Unit> =
    withContext(Dispatchers.IO) {
        val userId = getMarketerUserId(clientId)
            ?: return@withContext Result.failure(IllegalStateException("لا يوجد حساب AutoDrive لهذا المسوّق"))
        runCatching {
            client.postgrest["notifications"]
                .insert(NotificationInsert(
                    userId   = userId,
                    clientId = clientId,
                    orgId    = orgId,
                    type     = "ADMIN_REMINDER",
                    title    = "",
                    body     = message,
                    data     = buildJsonObject { put("nav_route", navRoute) }
                ))
            Unit
        }
    }

suspend fun getPaidOutCommissionInvoiceIds(): Result<Set<String>> =
    withContext(Dispatchers.IO) {
        runCatching {
            val payments = getCommissionPaymentsForCurrentOrg()
            if (payments.isEmpty()) return@runCatching emptySet()
            val paymentIds = payments.map { it.id }.toSet()
            client.postgrest["commission_payment_invoices"]
                .select()
                .decodeList<CommissionPaymentInvoiceDto>()
                .filter { it.commissionPaymentId in paymentIds }
                .map { it.invoiceId }
                .filter { it.isNotBlank() }
                .toSet()
        }
    }

private suspend fun getCommissionPaymentsForCurrentOrg(): List<CommissionPaymentDto> {
    val profile = authRepository.getMyProfile() ?: error("غير مسجل")
    return client.postgrest["commission_payments"]
        .select { filter { eq("organization_id", profile.organizationId) } }
        .decodeList<CommissionPaymentDto>()
}

suspend fun creditMarketerBalance(
    clientId: String,
    orgId: String,
    amount: Double,
    referenceId: String,
    note: String
): Result<Unit> =
    withContext(Dispatchers.IO) {
        runCatching {
            client.postgrest.rpc(
                "credit_marketer_balance",
                CreditBalanceParams(
                    clientId    = clientId,
                    orgId       = orgId,
                    amount      = amount.toRemoteDecimal(),
                    referenceId = referenceId,
                    note        = note
                )
            )
            Unit
        }
    }

// صرف مبلغ حر بدون ربط بفواتير محددة — يستخدم RPC pay_out_free_amount
suspend fun payOutFreeAmount(
    clientId: String,
    amount: Double,
    bankName: String,
    txRef: String,
    clientRequestId: String
): Result<String> =
    withContext(Dispatchers.IO) {
        runFinancialCommand("pay_out_free_amount") {
            val result = client.postgrest.rpc(
                "pay_out_free_amount",
                PayOutFreeAmountParams(
                    clientId        = clientId,
                    amount          = amount.toRemoteDecimal(),
                    bankName        = bankName,
                    transactionRef  = txRef,
                    clientRequestId = clientRequestId
                )
            )
            result.data
        }
    }

// FIX-009: صرف عمولة عبر RPC ذرّية (يحلّ ISS-002, ISS-014)
suspend fun payOutCommission(
    invoiceIds: List<String>,
    bankName: String,
    txRef: String,
    clientRequestId: String
): Result<String> =
    withContext(Dispatchers.IO) {
        runFinancialCommand("pay_out_commission") {
            val result = client.postgrest.rpc(
                "pay_out_commission",
                PayOutCommissionParams(
                    invoiceIds        = invoiceIds,
                    bankName          = bankName,
                    transactionRef    = txRef,
                    clientRequestId   = clientRequestId
                )
            )
            result.data
        }
    }

// ── إشعارات ───────────────────────────────────────────────────────────────

suspend fun getMarketerUserId(clientId: String): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            client.postgrest["autodrive_users"]
                .select { filter { eq("client_id", clientId) } }
                .decodeSingleOrNull<AutodriveUserLookupDto>()
                ?.userId
        }.getOrNull()
    }

suspend fun insertNotification(
    userId: String,
    clientId: String,
    orgId: String,
    type: String,
    title: String,
    body: String
): Result<Unit> =
    withContext(Dispatchers.IO) {
        runCatching {
            client.postgrest["notifications"]
                .insert(NotificationInsert(
                    userId   = userId,
                    clientId = clientId,
                    orgId    = orgId,
                    type     = type,
                    title    = title,
                    body     = body
                ))
            Unit
        }
    }

suspend fun getRegisteredMarketers(): Result<List<RegisteredMarketerDto>> =
    withContext(Dispatchers.IO) {
        runCatching {
            client.postgrest["autodrive_users"]
                .select()
                .decodeList<RegisteredMarketerDto>()
        }
    }
}
