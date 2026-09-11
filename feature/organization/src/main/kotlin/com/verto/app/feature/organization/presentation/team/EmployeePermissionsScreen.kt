package com.verto.app.feature.organization.presentation.team

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.res.stringResource

import com.verto.feature.organization.R

import com.verto.app.ui.components.VertoOutlinedButton
import com.verto.app.ui.components.VertoButton

import com.verto.app.feature.organization.presentation.OrganizationDimensions
import com.verto.app.feature.organization.presentation.OrganizationTextScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.feature.organization.domain.model.EmployeePermissions
import com.verto.app.ui.theme.*
import kotlinx.coroutines.flow.collectLatest
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeePermissionsScreen(
    userId: String,
    onBack: () -> Unit = {},
    vm: OrganizationTeamViewModel
) {
    val teamUiState by vm.uiState.collectAsStateWithLifecycle()
    val uiState = teamUiState.employeePermissions
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(userId) { vm.loadEmployeePermissions(userId) }

    LaunchedEffect(vm) {
        vm.effects.collectLatest { effect ->
            if (effect is OrganizationTeamEffect.EmployeeRemoved && effect.userId == userId) {
                onBack()
            }
        }
    }

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            VertoTopAppBar(
                title = {
                    Column {
                        Text(
                            uiState.employeeName.ifBlank { stringResource(R.string.legacy_ui_300d6bdbe2ff) },
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = OrganizationTextScale.sp16
                        )
                        if (uiState.joinedAt.isNotBlank()) {
                            Text(
                                androidx.compose.ui.res.stringResource(R.string.ds_d7e51a87a516, uiState.joinedAt),
                                color = TextSecondary,
                                fontSize = OrganizationTextScale.sp11
                            )
                        }
                    }
                },
                navigationIcon = {
                    VertoIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                    }
                },
                actions = {
                    // زر حذف الموظف
                    VertoIconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Filled.PersonRemove, null, tint = ErrorColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep)
            )
        },
        bottomBar = {
            // زر الحفظ
            Surface(color = BgDeep, shadowElevation = OrganizationDimensions.dp8) {
                VertoButton(
                    onClick = { vm.saveEmployeePermissions(userId) },
                    enabled = !uiState.isSaving && uiState.permissions != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = OrganizationDimensions.dp16, vertical = OrganizationDimensions.dp12),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentMain)
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            color = TextOnAccent,
                            modifier = Modifier.size(OrganizationDimensions.dp18),
                            strokeWidth = OrganizationDimensions.dp2
                        )
                        Spacer(Modifier.width(OrganizationDimensions.dp8))
                    }
                    Text(
                        if (uiState.isSaving) stringResource(R.string.legacy_ui_0c7ccaf3ffe7) else stringResource(R.string.legacy_ui_d49e74ff0775),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { padding ->

        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = AccentMain
                    )
                }

                uiState.permissions == null -> {
                    PermissionErrorState(
                        message = uiState.error ?: stringResource(R.string.legacy_ui_deff75511735),
                        onRetry = { vm.loadEmployeePermissions(userId) },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    PermissionsContent(
                        permissions = checkNotNull(uiState.permissions),
                        onPermissionsChange = { vm.updatePermissionsLocally(it) },
                        savedSuccess = uiState.savedSuccess,
                        error = uiState.error
                    )
                }
            }
        }
    }

    // ── Dialog حذف الموظف ──────────────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor   = BgCard,
            icon = { Icon(Icons.Filled.Warning, null, tint = ErrorColor) },
            title = { Text(androidx.compose.ui.res.stringResource(R.string.ds_a541e096620c), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text  = {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.ds_1ae880b0ff93, uiState.employeeName) +
                    stringResource(R.string.legacy_ui_cfe5aad0ac8d),
                    color = TextSecondary,
                    lineHeight = OrganizationTextScale.sp20
                )
            },
            confirmButton = {
                VertoButton(
                    onClick = {
                        showDeleteDialog = false
                        vm.removeEmployee(userId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorColor, contentColor = OnDanger)
                ) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextSecondary)
                }
            }
        )
    }
}

// ── محتوى الصلاحيات ───────────────────────────────────────────
@Composable
private fun PermissionsContent(
    permissions: EmployeePermissions,
    onPermissionsChange: (EmployeePermissions) -> Unit,
    savedSuccess: Boolean,
    error: String?
) {
    val resourceContext = LocalContext.current
    val sections = remember(resourceContext) { buildPermissionSections(resourceContext::getString) }
    val expandedSections = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = OrganizationDimensions.dp16, vertical = OrganizationDimensions.dp12),
        verticalArrangement = Arrangement.spacedBy(OrganizationDimensions.dp10)
    ) {
        // رسالة الحفظ الناجح
        if (savedSuccess) {
            item {
                AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SuccessColor.copy(alpha = 0.12f), RoundedCornerShape(OrganizationDimensions.dp10))
                            .padding(horizontal = OrganizationDimensions.dp14, vertical = OrganizationDimensions.dp10),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(OrganizationDimensions.dp8)
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, tint = SuccessColor, modifier = Modifier.size(OrganizationDimensions.dp18))
                        Text(androidx.compose.ui.res.stringResource(R.string.ds_aedf9e63de35), color = SuccessColor, fontSize = OrganizationTextScale.sp13)
                    }
                }
            }
        }

        if (error != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ErrorColor.copy(alpha = 0.12f), RoundedCornerShape(OrganizationDimensions.dp10))
                        .padding(horizontal = OrganizationDimensions.dp14, vertical = OrganizationDimensions.dp10),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OrganizationDimensions.dp8)
                ) {
                    Icon(Icons.Filled.ErrorOutline, null, tint = ErrorColor, modifier = Modifier.size(OrganizationDimensions.dp18))
                    Text(error, color = ErrorColor, fontSize = OrganizationTextScale.sp13)
                }
            }
        }

        item {
            Text(
                androidx.compose.ui.res.stringResource(R.string.ds_68b53f81b403),
                color = TextSecondary,
                fontSize = OrganizationTextScale.sp12,
                modifier = Modifier.padding(bottom = OrganizationDimensions.dp4)
            )
        }

        items(sections, key = { it.key }) { section ->
            val isExpanded = expandedSections[section.key] ?: true
            PermissionSectionCard(
                section             = section,
                permissions         = permissions,
                isExpanded          = isExpanded,
                onToggleExpand      = { expandedSections[section.key] = !isExpanded },
                onPermissionsChange = onPermissionsChange
            )
        }
    }
}

@Composable
private fun PermissionErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(OrganizationDimensions.dp32),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OrganizationDimensions.dp12)
    ) {
        Icon(Icons.Filled.ErrorOutline, null, tint = ErrorColor, modifier = Modifier.size(OrganizationDimensions.dp48))
        Text(message, color = TextSecondary, textAlign = TextAlign.Center)
        VertoOutlinedButton(onClick = onRetry) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_retry)) }
    }
}

