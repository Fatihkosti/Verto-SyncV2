package com.verto.app.feature.auth.presentation

import androidx.compose.ui.unit.sp
import com.verto.app.ui.theme.VertoTypography

/**
 * Auth-owned compatibility geometry for the v136 long-tail migration.
 * Shared values alias Core tokens; specialized values stay with this UI owner to preserve layout.
 */

/** Exact type compatibility: central sizes reuse VertoTypography; in-between sizes stay feature-owned. */
internal object AuthTextScale {
    val sp72 = 72.sp
}
