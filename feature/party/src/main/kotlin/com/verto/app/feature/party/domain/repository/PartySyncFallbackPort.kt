package com.verto.app.feature.party.domain.repository

/** يحافظ على مسار السحب القديم للتوافق دون إدخال DAOs المالية إلى مستودع الأطراف. */
interface PartySyncFallbackPort {
    suspend fun pullClientsInvoicesAndPayments(): Result<Unit>
}
