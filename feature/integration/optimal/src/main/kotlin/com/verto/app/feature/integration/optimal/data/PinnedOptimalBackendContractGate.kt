package com.verto.app.feature.integration.optimal.data

import com.verto.app.feature.integration.optimal.domain.model.OptimalBackendContractGate
import com.verto.app.feature.integration.optimal.domain.model.OptimalBackendProtectionStatus
import com.verto.app.feature.integration.optimal.domain.model.OptimalRemoteContract
import javax.inject.Inject

/** يسمح فقط بعقد التسجيل المثبت محليًا؛ ويعيد المصدر البعيد التحقق من جاهزية v69 قبل الكتابة. */
class PinnedOptimalBackendContractGate @Inject constructor() : OptimalBackendContractGate {
    override fun status(contract: OptimalRemoteContract): OptimalBackendProtectionStatus = when (contract) {
        OptimalRemoteContract.ISSUE_COMPANY_JOIN_CODE -> OptimalBackendProtectionStatus.VERIFIED
        OptimalRemoteContract.SEND_MESSAGE -> OptimalBackendProtectionStatus.BLOCKED
        OptimalRemoteContract.ARCHIVE_CONVERSATION -> OptimalBackendProtectionStatus.BLOCKED
        OptimalRemoteContract.READ_VEHICLES -> OptimalBackendProtectionStatus.BLOCKED
        OptimalRemoteContract.UPSERT_INVOICE -> OptimalBackendProtectionStatus.BLOCKED
        OptimalRemoteContract.UPSERT_PAYMENT -> OptimalBackendProtectionStatus.BLOCKED
        OptimalRemoteContract.REVERSE_PAYMENT -> OptimalBackendProtectionStatus.BLOCKED
        OptimalRemoteContract.VOID_INVOICE -> OptimalBackendProtectionStatus.BLOCKED
        OptimalRemoteContract.UPSERT_MAINTENANCE -> OptimalBackendProtectionStatus.BLOCKED
    }
}
