package com.verto.app.feature.payment.application

import com.verto.app.feature.payment.application.model.PaymentCreditDecision
import com.verto.app.feature.payment.application.model.PaymentCustomerDecision

enum class CreditSaveAuthorization {
    ALLOW,
    ALLOW_MANAGER_OVERRIDE,
    BLOCK_CASH_ONLY,
    BLOCK_MANAGER_APPROVAL_REQUIRED,
}

/**
 * Pure workflow policy. CASH_ONLY cannot be overridden. Incomplete evidence can never auto-approve
 * credit, even if an upstream mapping accidentally reports ALLOW_CREDIT.
 */
object CustomerCreditWorkflowPolicy {
    fun evaluate(decision: PaymentCustomerDecision, isAdmin: Boolean): CreditSaveAuthorization {
        if (decision.decision == PaymentCreditDecision.CASH_ONLY) return CreditSaveAuthorization.BLOCK_CASH_ONLY
        if (!decision.dataComplete || decision.decision == PaymentCreditDecision.REQUIRES_APPROVAL) {
            return if (isAdmin) {
                CreditSaveAuthorization.ALLOW_MANAGER_OVERRIDE
            } else {
                CreditSaveAuthorization.BLOCK_MANAGER_APPROVAL_REQUIRED
            }
        }
        return CreditSaveAuthorization.ALLOW
    }
}
