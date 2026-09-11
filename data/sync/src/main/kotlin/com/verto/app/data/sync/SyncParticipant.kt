package com.verto.app.data.sync

import kotlinx.coroutines.CancellationException
/**
 * مرحلة تنفيذ عملية المزامنة.
 *
 * الترتيب العام ثابت: رفع البيانات، ثم نشر الحذف، ثم السحب.
 */
enum class SyncStage(val sequence: Int) {
    PUSH(0),
    DELETE(1),
    PULL(2)
}

/** يحدد هل فشل الخطوة يوقف المزامنة فورًا أم يُجمع مع الأخطاء غير الحرجة. */
enum class SyncFailureMode {
    ABORT,
    COLLECT
}

/** البيانات المشتركة التي تحتاجها Participants خلال دورة مزامنة واحدة. */
data class SyncRunContext(
    val organizationId: String,
    val userId: String,
    val deletions: SyncDeletionSnapshot
)

/** خطوة ذرية داخل خطة المزامنة. */
data class SyncOperation internal constructor(
    val stage: SyncStage,
    val order: Int,
    val label: String,
    val failureMode: SyncFailureMode,
    val execute: suspend () -> Unit,
    val participantKey: String = "",
    val idempotencyKey: String = ""
) {
    /** Public construction path: every feature must use a centrally declared semantic slot. */
    constructor(
        slot: SyncOperationSlot,
        label: String,
        failureMode: SyncFailureMode,
        execute: suspend () -> Unit,
        participantKey: String = "",
        idempotencyKey: String = "",
    ) : this(
        stage = slot.stage,
        order = slot.order,
        label = label,
        failureMode = failureMode,
        execute = execute,
        participantKey = participantKey,
        idempotencyKey = idempotencyKey,
    )
}

/**
 * مالك مزامنة مجال واحد.
 *
 * لا يعرف SyncManager تفاصيل push/pull؛ بل يجمع العمليات من Participants وينفذها بالترتيب.
 */
interface SyncParticipant {
    val key: String

    fun operations(context: SyncRunContext): List<SyncOperation>
}

/**
 * يتحقق من الخطة الفعلية وقت التشغيل كخط دفاع أخير حتى لو تجاوز Contributor الاختبارات.
 * الرسالة مقصودة لتكون صالحة مباشرة في ADB وتشير إلى كل العمليات المتعارضة.
 */
internal fun validateSyncOperationPlan(operations: List<SyncOperation>) {
    val conflicts = operations
        .groupBy { it.stage to it.order }
        .filterValues { it.size > 1 }

    require(conflicts.isEmpty()) {
        val details = conflicts.entries
            .sortedWith(compareBy({ it.key.first.sequence }, { it.key.second }))
            .joinToString(separator = "; ") { (slot, entries) ->
                val owners = entries
                    .sortedWith(compareBy<SyncOperation>({ it.participantKey }, { it.label }))
                    .joinToString(separator = ", ") { operation ->
                        "${operation.participantKey.ifBlank { "<unknown>" }}:${operation.label}"
                    }
                "stage=${slot.first} order=${slot.second} conflicts=[$owners]"
            }
        "Duplicate sync operation order: $details"
    }
}

/** ينفذ الخطة بترتيب ثابت ويجمع أخطاء الخطوات غير الحرجة دون ابتلاعها. */
internal suspend fun executeSyncOperations(
    operations: List<SyncOperation>,
    completedIdempotencyKeys: Set<String> = emptySet(),
    onOperationCompleted: suspend (SyncOperation) -> Unit = {},
    onTrace: (SyncOperationTrace) -> Unit = {},
    onCollectedFailure: (SyncOperation, Throwable) -> Unit = { _, _ -> }
): List<String> {
    validateSyncOperationPlan(operations)

    val duplicateKeys = operations.filter { it.idempotencyKey.isNotBlank() }
        .groupingBy { it.idempotencyKey }
        .eachCount()
        .filterValues { it > 1 }
    require(duplicateKeys.isEmpty()) {
        "Duplicate sync idempotency keys: $duplicateKeys"
    }

    val errors = mutableListOf<String>()
    operations
        .sortedWith(compareBy<SyncOperation>({ it.stage.sequence }, { it.order }))
        .forEach { operation ->
            if (operation.idempotencyKey.isNotBlank() && operation.idempotencyKey in completedIdempotencyKeys) {
                onTrace(operation.trace(SyncOperationOutcome.SKIPPED_CHECKPOINT))
                return@forEach
            }

            when (operation.failureMode) {
                SyncFailureMode.ABORT -> {
                    try {
                        operation.execute()
                        onOperationCompleted(operation)
                        onTrace(operation.trace(SyncOperationOutcome.SUCCEEDED))
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (failure: Exception) {
                        onTrace(operation.trace(SyncOperationOutcome.FAILED_ABORTED, failure))
                        throw failure
                    }
                }
                SyncFailureMode.COLLECT -> {
                    try {
                        operation.execute()
                        onOperationCompleted(operation)
                        onTrace(operation.trace(SyncOperationOutcome.SUCCEEDED))
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (failure: Exception) {
                        onCollectedFailure(operation, failure)
                        onTrace(operation.trace(SyncOperationOutcome.FAILED_COLLECTED, failure))
                        errors += "${operation.label}: ${failure.message}"
                    }
                }
            }
        }
    return errors
}

private fun SyncOperation.trace(
    outcome: SyncOperationOutcome,
    throwable: Throwable? = null
): SyncOperationTrace = SyncOperationTrace(
    participantKey = participantKey,
    label = label,
    idempotencyKey = idempotencyKey,
    outcome = outcome,
    failureType = throwable?.let { it::class.simpleName ?: "Throwable" }
)
