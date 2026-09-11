package com.verto.app.feature.integration.optimal.application

import javax.inject.Inject

class OptimalSyncErrorHumanizer @Inject constructor() {
    fun humanize(rawReason: String?): String = when (rawReason?.trim()?.uppercase()) {
        "NETWORK_UNAVAILABLE", "CONNECTION_FAILED", "TIMEOUT", "REMOTE_RETRYABLE_FAILURE" ->
            "تعذّر الاتصال بالخادم. تحقق من الإنترنت ثم أعد المحاولة."
        "UNAUTHORIZED" ->
            "انتهت صلاحية الجلسة. سجّل الدخول مرة أخرى."
        "PERMISSION_DENIED" ->
            "لا تملك صلاحية مزامنة هذه العملية. راجع صلاحيات الحساب."
        "LOCAL_STORAGE_MISSING" ->
            "الملف المحلي المطلوب غير موجود. أعد إرفاقه ثم حاول مجددًا."
        "LOCAL_STORAGE_READ", "LOCAL_STORAGE_WRITE", "LOCAL_STORAGE_CORRUPT",
        "LOCAL_STORAGE_UNKNOWN", "DEVICE_IO" ->
            "تعذّر الوصول إلى بيانات الجهاز المطلوبة للمزامنة."
        "CONFLICT", "REMOTE_VERSION_CONFLICT" ->
            "يوجد تعارض في نسخة البيانات ويحتاج مراجعة قبل المتابعة."
        "REMOTE_OPERATION_REJECTED", "REMOTE_OPERATION_BLOCKED" ->
            "رفض الخادم هذه العملية. راجع البيانات أو الصلاحيات قبل إعادة المحاولة."
        "SERVER_ERROR" ->
            "الخدمة غير متاحة حاليًا. أعد المحاولة لاحقًا."
        else -> "تعذّرت مزامنة العملية. أعد المحاولة لاحقًا."
    }
}
