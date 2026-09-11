package com.verto.app.feature.payment.application

import com.verto.app.feature.payment.application.model.PaymentCreditDecision
import com.verto.app.feature.payment.application.model.PaymentCustomerDecision
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomerCreditWorkflowPolicy348Test {
    private fun incompleteAllowDecision() = PaymentCustomerDecision(
        decision = PaymentCreditDecision.ALLOW_CREDIT,
        dataComplete = false,
        reasons = listOf("INCOMPLETE_FINANCIAL_HISTORY"),
        currentlyOverdueInvoiceCount = 0,
        maxCurrentDaysOverdue = 0,
    )

    @Test fun `incomplete evidence cannot auto approve employee credit`() {
        assertEquals(
            CreditSaveAuthorization.BLOCK_MANAGER_APPROVAL_REQUIRED,
            CustomerCreditWorkflowPolicy.evaluate(incompleteAllowDecision(), isAdmin = false),
        )
    }

    @Test fun `incomplete evidence may proceed only through manager override`() {
        assertEquals(
            CreditSaveAuthorization.ALLOW_MANAGER_OVERRIDE,
            CustomerCreditWorkflowPolicy.evaluate(incompleteAllowDecision(), isAdmin = true),
        )
    }
}
