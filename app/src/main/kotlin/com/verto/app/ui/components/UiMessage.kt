package com.verto.app.ui.components

/**
 * رسائل الـ UI الموحّدة — بديل عن String العشوائية.
 *
 * بدل:
 *   _message.value = "فشل التحديث: ..."    // خطأ
 *   _message.value = "تم التحديث بنجاح"    // نجاح
 *   if (message.startsWith("فشل")) ...       // هش جداً
 *
 * الآن:
 *   _uiMessage.value = UiMessage.Error("فشل التحديث: ...")
 *   _uiMessage.value = UiMessage.Success("تم التحديث بنجاح")
 *   is UiMessage.Error  → لون أحمر
 *   is UiMessage.Success → لون أخضر
 */
sealed class UiMessage {
    abstract val text: String

    data class Error(override val text: String)   : UiMessage()
    data class Success(override val text: String) : UiMessage()
    data class Info(override val text: String)    : UiMessage()
    data class Warning(override val text: String) : UiMessage()
}
