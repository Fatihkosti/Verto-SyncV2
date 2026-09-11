package com.verto.app.utils

import com.verto.app.core.error.AppFailure
import com.verto.app.core.error.ErrorPresentationContext
import com.verto.app.core.error.ErrorPresentationPolicy
import com.verto.app.core.error.IncidentReference
import com.verto.app.core.error.OperationOutcome
import com.verto.app.core.error.UserErrorPresentation

/**
 * Failure boundary used by feature/application code that needs a user-visible error.
 * It creates one opaque incident reference, records a sanitized diagnostic event, and returns
 * the structured presentation while preserving the known operation outcome.
 */
object UserErrorFactory {
    fun from(
        throwable: Throwable,
        context: ErrorPresentationContext,
        operation: String,
        outcome: OperationOutcome = OperationOutcome.NOT_APPLIED,
    ): UserErrorPresentation {
        val incidentId = IncidentReference.create()
        CrashReporter.recordException(throwable, operation, incidentId)
        return ErrorPresentationPolicy.from(
            throwable = throwable,
            context = context,
            outcome = outcome,
            incidentId = incidentId,
        )
    }

    fun from(
        failure: AppFailure,
        context: ErrorPresentationContext,
        operation: String,
        outcome: OperationOutcome = OperationOutcome.NOT_APPLIED,
    ): UserErrorPresentation {
        val incidentId = IncidentReference.create()
        CrashReporter.log(
            "user_failure incident=$incidentId operation=$operation diagnostic=${failure.diagnosticCode} outcome=${outcome.name}"
        )
        return ErrorPresentationPolicy.from(
            failure = failure,
            context = context,
            outcome = outcome,
            incidentId = incidentId,
        )
    }
}
