package com.verto.app.feature.auth.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.ui.components.resolveVertoError

@Composable
fun JoinOrgScreen(
    onJoinSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            viewModel.consumeSuccess()
            onJoinSuccess()
        }
    }

    JoinOrgContent(
        state = JoinOrgContentState(
            inviteDetails = uiState.inviteDetails,
            error = uiState.error?.resolveVertoError()?.message,
            isLoading = uiState.isLoading,
        ),
        events = JoinOrgEvents(
            onLoadInviteDetails = viewModel::loadInviteDetails,
            onJoin = viewModel::joinOrg,
            onClearInviteDetails = viewModel::clearInviteDetails,
            onClearError = viewModel::clearError,
            onNavigateToLogin = onNavigateToLogin,
        ),
    )
}
