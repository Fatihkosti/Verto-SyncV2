package com.verto.app.utils

import com.verto.app.core.error.AppFailure
import com.verto.app.core.error.ErrorClassifier
import com.verto.app.core.error.ErrorPresentationPolicy
import com.verto.app.core.error.LocalStorageKind
import com.verto.app.core.error.UserErrorMessageKey
import java.util.concurrent.CancellationException

/**
 * Compatibility mapper for legacy call sites.
 *
 * Classification is delegated to core:common. This class never guesses from exception text.
 * New feature code should keep structured failures until the presentation layer.
 */
object ErrorHumanizer {

    private const val GENERIC = "حدث خطأ غير متوقع، حاول مرة أخرى"

    fun humanize(t: Throwable?, action: String = ""): String {
        val prefix = if (action.isBlank()) "" else "تعذّر $action — "
        if (t == null) return prefix + GENERIC

        val failure = try {
            ErrorClassifier.classify(t)
        } catch (cancellation: CancellationException) {
            throw cancellation
        }

        val message = when (failure) {
            is AppFailure.NetworkUnavailable ->
                "تعذّر الوصول إلى الشبكة أو الخدمة، تحقق من الاتصال ثم أعد المحاولة"
            is AppFailure.ConnectionFailed ->
                "تعذّر الوصول إلى الخدمة حاليًا، حاول مرة أخرى"
            is AppFailure.Timeout ->
                "استغرقت العملية وقتًا أطول من المتوقع، حاول مرة أخرى"
            is AppFailure.Unauthorized ->
                "انتهت صلاحية الجلسة، سجّل الدخول مرة أخرى"
            is AppFailure.PermissionDenied ->
                "ليس لديك صلاحية لتنفيذ هذا الإجراء"
            is AppFailure.Validation ->
                if (failure.target.isNullOrBlank()) {
                    "تعذّر قبول البيانات الحالية، راجع المعلومات المتاحة وحاول مرة أخرى"
                } else {
                    "راجع قيمة الحقل المحدد وصحّحها ثم حاول مرة أخرى"
                }
            is AppFailure.BusinessRule -> when (failure.code) {
                "SYNC_PENDING_SESSION_END" -> "توجد بيانات محلية لم تُؤكَّد مزامنتها بعد. افتح حالة المزامنة وعالج المعلّق قبل تسجيل الخروج"
                "SYNC_PENDING_ORG_SWITCH" -> "لا يمكن تبديل المؤسسة قبل معالجة بيانات المزامنة المعلّقة للمؤسسة الحالية"
                else -> "تعذّر إكمال الطلب بالبيانات الحالية"
            }
            is AppFailure.NotFound ->
                "البيانات المطلوبة غير موجودة أو لم تعد متاحة"
            is AppFailure.Conflict -> when (ErrorPresentationPolicy.messageKeyFor(failure)) {
                UserErrorMessageKey.CONFLICT_DUPLICATE -> "يوجد سجل بهذه البيانات بالفعل"
                UserErrorMessageKey.CONFLICT_RELATED_RECORD -> "لا يمكن إكمال العملية لأن السجل مرتبط ببيانات أخرى"
                UserErrorMessageKey.CONFLICT_VERSION -> "تغيّرت البيانات منذ فتحها، حدّثها ثم حاول مرة أخرى"
                else -> "تعذّر إكمال العملية بسبب تعارض في البيانات، حدّث البيانات وحاول مرة أخرى"
            }
            is AppFailure.RateLimited ->
                "تم تنفيذ محاولات كثيرة، حاول مرة أخرى لاحقًا"
            is AppFailure.Server ->
                "الخدمة غير متاحة حاليًا، حاول مرة أخرى"
            is AppFailure.RemoteRejected ->
                "رفضت الخدمة الطلب، راجع البيانات وحاول مرة أخرى"
            is AppFailure.LocalStorage -> when (failure.kind) {
                LocalStorageKind.FULL -> "مساحة التخزين على الجهاز غير كافية، وفر مساحة ثم حاول مرة أخرى"
                LocalStorageKind.CORRUPT -> "تعذّر قراءة قاعدة البيانات المحلية بصورة سليمة"
                LocalStorageKind.CONSTRAINT -> "تعذّر حفظ البيانات بسبب تعارض في البيانات المحلية"
                else -> "تعذّر الوصول إلى بيانات الجهاز"
            }
            is AppFailure.Unknown -> {
                runCatching { CrashReporter.recordException(t, action) }
                GENERIC
            }
        }

        return if (prefix.isBlank()) message else prefix + message
    }

    /** Legacy compatibility helper. Generic IOException is intentionally NOT a network error. */
    fun isNetworkError(t: Throwable): Boolean =
        try {
            ErrorClassifier.isConnectivityFailure(t)
        } catch (_: CancellationException) {
            false
        }
}
