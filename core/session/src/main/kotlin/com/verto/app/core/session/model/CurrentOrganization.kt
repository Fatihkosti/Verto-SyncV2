package com.verto.app.core.session.model

/** المؤسسة المرتبطة بالجلسة الحالية. */
data class CurrentOrganization(
    val id: String = ""
) {
    val isKnown: Boolean get() = id.isNotBlank()
}
