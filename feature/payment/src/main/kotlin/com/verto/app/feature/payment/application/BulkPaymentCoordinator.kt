package com.verto.app.feature.payment.application

import com.verto.app.core.error.ClassifiedFailureException
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.feature.payment.domain.model.AdvanceCreditRecord
import com.verto.app.feature.payment.domain.model.BulkPaymentCommand
import com.verto.app.feature.payment.domain.model.BulkPaymentResult
import com.verto.app.feature.payment.domain.model.PaymentMethod
import com.verto.app.feature.payment.domain.model.PaymentOperationResult
import com.verto.app.feature.payment.domain.model.RecordPaymentCommand
import com.verto.app.feature.payment.domain.port.AdvanceCreditPort
import com.verto.app.feature.payment.domain.port.PaymentCashPort
import com.verto.app.feature.payment.domain.port.PaymentTransactionPort
import com.verto.app.money.ExchangeRate
import com.verto.app.money.Money
import com.verto.app.utils.ErrorHumanizer
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject

class BulkPaymentCoordinator @Inject constructor(
    private val transaction: PaymentTransactionPort,
    private val paymentRecorder: RecordPaymentCoordinator,
    private val cash: PaymentCashPort,
    private val credits: AdvanceCreditPort,
    private val sessionReader: SessionReader
) {
    private class BulkRejected(message: String, cause: Throwable? = null) : Exception(message, cause)

    suspend fun allocate(command: BulkPaymentCommand): BulkPaymentResult {
        val amount = runCatching { Money.fromLegacyDouble(command.amount) }.getOrNull()
            ?: return BulkPaymentResult.Error("المبلغ يجب أن يكون رقماً موجباً صالحاً")
        if (!amount.isPositive()) {
            return BulkPaymentResult.Error("المبلغ يجب أن يكون رقماً موجباً صالحاً")
        }
        val rate = runCatching { ExchangeRate.fromLegacyDouble(command.rate) }.getOrNull()
            ?: return BulkPaymentResult.Error("سعر الصرف غير صالح")

        val currentUser = sessionReader.currentUser.first()
        var surplusMinor = 0L
        var lastPaymentId: String? = null
        val outcome = runCatching {
            transaction.inTransaction {
                var remainingMinor = amount.amountMinor
                for (target in command.targets) {
                    if (remainingMinor == 0L) break
                    val targetRemaining = runCatching { Money.fromLegacyDouble(target.remaining) }
                        .getOrElse { throw BulkRejected("رصيد الفاتورة غير صالح", it) }
                    if (targetRemaining.isNegative()) throw BulkRejected("رصيد الفاتورة غير صالح")

                    val toPayMinor = minOf(remainingMinor, targetRemaining.amountMinor)
                    if (toPayMinor == 0L) continue
                    val toPay = Money.ofMinor(toPayMinor)
                    val functionalCash = rate.convert(toPay)
                    val paymentResult = paymentRecorder.record(
                        RecordPaymentCommand(
                            invoiceId = target.invoiceId,
                            clientId = command.clientId,
                            amount = toPay.toLegacyDouble(),
                            paymentMethod = PaymentMethod.CASH,
                            note = command.note,
                            remainingAmount = targetRemaining.toLegacyDouble(),
                            clientName = command.clientName,
                            invoiceNumber = target.invoiceNumber,
                            cashAmount = functionalCash.toLegacyDouble(),
                            paymentExchangeRate = command.rate,
                            exchangeRateSource = "BULK_PAYMENT",
                            requireSupplierPermission = command.requireSupplierPermission,
                            requestId = UUID.nameUUIDFromBytes(
                                "${command.requestId}:${target.invoiceId}".toByteArray()
                            ).toString()
                        )
                    )
                    if (paymentResult is PaymentOperationResult.Error) {
                        throw ClassifiedFailureException(
                            failure = paymentResult.failure,
                            cause = paymentResult.cause,
                        )
                    }
                    if (paymentResult is PaymentOperationResult.Success) lastPaymentId = paymentResult.paymentId
                    remainingMinor = Math.subtractExact(remainingMinor, toPayMinor)
                }

                if (remainingMinor > 0L) {
                    surplusMinor = remainingMinor
                    val surplus = Money.ofMinor(surplusMinor)
                    val creditId = UUID.nameUUIDFromBytes(
                        "${command.requestId}:advance-credit".toByteArray()
                    ).toString()
                    val surplusCash = rate.convert(surplus)
                    if (command.moneyIn) cash.onPaymentReceived(surplusCash.toLegacyDouble(), creditId)
                    else cash.onPaymentMade(surplusCash.toLegacyDouble(), creditId)

                    val signedAmount = if (command.moneyIn) surplus.negate() else surplus
                    val inserted = credits.insert(
                        AdvanceCreditRecord(
                            id = creditId,
                            clientId = command.clientId,
                            amount = signedAmount.toLegacyDouble(),
                            amountMinor = signedAmount.amountMinor,
                            note = "رصيد مقدَّم — ${command.note}",
                            createdAt = System.currentTimeMillis(),
                            employeeId = currentUser.id,
                            employeeName = currentUser.name,
                            sourcePaymentId = requireNotNull(lastPaymentId) {
                                "لا يمكن إنشاء رصيد مقدم بلا أصل دفع مثبت"
                            }
                        )
                    )
                    if (!inserted) throw BulkRejected("تعذّر تسجيل الرصيد المقدَّم")
                }
            }
        }
        outcome.exceptionOrNull()?.let {
            return BulkPaymentResult.Error(ErrorHumanizer.humanize(it, "تسجيل السداد"))
        }
        val surplus = Money.ofMinor(surplusMinor)
        return BulkPaymentResult.Success(
            applied = (amount - surplus).toLegacyDouble(),
            surplusCredit = surplus.toLegacyDouble()
        )
    }
}
