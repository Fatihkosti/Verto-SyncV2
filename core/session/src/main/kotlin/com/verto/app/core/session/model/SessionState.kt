package com.verto.app.core.session.model

/** لقطة موحدة لهوية الجلسة والوصول المخزن محليًا. */
data class SessionState(
    val user: CurrentUser = CurrentUser(),
    val organization: CurrentOrganization = CurrentOrganization(),
    val role: String = "",
    val permissionsJson: String = ""
)
