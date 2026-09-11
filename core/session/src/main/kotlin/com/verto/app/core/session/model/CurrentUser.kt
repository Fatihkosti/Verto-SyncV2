package com.verto.app.core.session.model

/** هوية المستخدم الحالية كما تحتاجها بقية الميزات، دون معرفة DataStore أو عميل الشبكة. */
data class CurrentUser(
    val id: String = "",
    val name: String = "",
    val phone: String = ""
) {
    val isKnown: Boolean get() = id.isNotBlank()
}
