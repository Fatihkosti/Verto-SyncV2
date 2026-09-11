package com.verto.app.feature.payment.application

import com.verto.app.feature.payment.application.model.PaymentCreditDecision
import com.verto.app.feature.payment.application.model.PaymentCustomerDecision
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomerCreditWorkflowPolicy347Test {
    private fun decision(value: PaymentCreditDecision) = PaymentCustomerDecision(
        decision = value,
        dataComplete = true,
        reasons = emptyList(),
        currentlyOverdueInvoiceCount = 0,
        maxCurrentDaysOverdue = 0,
    )

    @Test fun `cash only is never overridden by admin`() {
        assertEquals(
            CreditSaveAuthorization.BLOCK_CASH_ONLY,
            CustomerCreditWorkflowPolicy.evaluate(decision(PaymentCreditDecision.CASH_ONLY), isAdmin = true),
        )
    }

    @Test fun `approval decision blocks employee and allows manager`() {
        val value = decision(PaymentCreditDecision.REQUIRES_APPROVAL)
        assertEquals(CreditSaveAuthorization.BLOCK_MANAGER_APPROVAL_REQUIRED, CustomerCreditWorkflowPolicy.evaluate(value, false))
        assertEquals(CreditSaveAuthorization.ALLOW_MANAGER_OVERRIDE, CustomerCreditWorkflowPolicy.evaluate(value, true))
    }

    @Test fun `approved history allows credit`() {
        assertEquals(
            CreditSaveAuthorization.ALLOW,
            CustomerCreditWorkflowPolicy.evaluate(decision(PaymentCreditDecision.ALLOW_CREDIT), isAdmin = false),
        )
    }
}
