package com.verto.app.feature.organization.presentation.team

import androidx.compose.ui.res.stringResource

import com.verto.feature.organization.R

import com.verto.app.ui.components.VertoOutlinedButton
import com.verto.app.ui.components.VertoButton

import com.verto.app.feature.organization.presentation.OrganizationDimensions
import com.verto.app.feature.organization.presentation.OrganizationTextScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.feature.organization.domain.model.OrganizationEmployee
import com.verto.app.ui.theme.*
import com.verto.app.ui.components.VertoCard
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoTopAppBar
import androidx.compose.foundation.layout.PaddingValues
import com.verto.app.ui.theme.VertoSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeesScreen(
    onBack: () -> Unit = {},
    onNavigateToEmployeeDetail: (userId: String) -> Unit = {},
    onNavigateToCreateInvite: () -> Unit = {},
    vm: OrganizationTeamViewModel
) {
    val teamUiState by vm.uiState.collectAsStateWithLifecycle()
    val uiState = teamUiState.employees

    LaunchedEffect(Unit) { vm.loadEmployees() }

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            VertoTopAppBar(
                title  = { Text(androidx.compose.ui.res.stringResource(R.string.ds_9bc331c7a422), color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    VertoIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick           = onNavigateToCreateInvite,
                containerColor    = AccentMain,
                contentColor      = TextOnAccent,
                icon              = { Icon(Icons.Filled.PersonAdd, null) },
                text              = { Text(androidx.compose.ui.res.stringResource(R.string.ds_8da173f0cf53)) }
            )
        }
    ) { padding ->

        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = AccentMain
                    )
                }

                uiState.error != null -> {
                    ErrorState(
                        message = checkNotNull(uiState.error),
                        onRetry = { vm.loadEmployees() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.employees.isEmpty() -> {
                    EmptyEmployeesState(
                        onCreateInvite = onNavigateToCreateInvite,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = OrganizationDimensions.dp16, end = OrganizationDimensions.dp16,
                            top = OrganizationDimensions.dp16, bottom = OrganizationDimensions.dp96 // مسافة للـ FAB
                        ),
                        verticalArrangement = Arrangement.spacedBy(OrganizationDimensions.dp10)
                    ) {
                        item {
                            Text(
                                androidx.compose.ui.res.stringResource(R.string.ds_84895944a04a, uiState.employees.size),
                                color = TextSecondary,
                                fontSize = OrganizationTextScale.sp13,
                                modifier = Modifier.padding(bottom = OrganizationDimensions.dp4)
                            )
                        }

                        items(uiState.employees, key = { it.userId }) { employee ->
                            EmployeeCard(
                                employee = employee,
                                onClick  = { onNavigateToEmployeeDetail(employee.userId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── بطاقة الموظف ──────────────────────────────────────────────
@Composable
private fun EmployeeCard(
    employee: OrganizationEmployee,
    onClick: () -> Unit
) {
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape  = RoundedCornerShape(OrganizationDimensions.dp14),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        elevation = CardDefaults.cardElevation(defaultElevation = OrganizationDimensions.dp0)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OrganizationDimensions.dp16, vertical = OrganizationDimensions.dp14),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrganizationDimensions.dp12)
        ) {
            // Avatar الأول من الاسم
            Box(
                modifier = Modifier
                    .size(OrganizationDimensions.dp44)
                    .clip(CircleShape)
                    .background(AccentMain.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = employee.name.firstOrNull()?.toString() ?: stringResource(R.string.legacy_ui_c662f8583007),
                    color = AccentMain,
                    fontSize = OrganizationTextScale.sp18,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = employee.name,
                    color = TextPrimary,
                    fontSize = OrganizationTextScale.sp15,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(OrganizationDimensions.dp2))
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.ds_d7e51a87a516, formatJoinDate(employee.joinedAt)),
                    color = TextSecondary,
                    fontSize = OrganizationTextScale.sp12
                )
            }

            // مؤشر الحالة
            if (!employee.isActive) {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.ds_a0b026caf37a),
                    color = ErrorColor,
                    fontSize = OrganizationTextScale.sp11,
                    modifier = Modifier
                        .background(ErrorColor.copy(alpha = 0.1f), RoundedCornerShape(OrganizationDimensions.dp6))
                        .padding(horizontal = OrganizationDimensions.dp8, vertical = OrganizationDimensions.dp3)
                )
            }

            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                null,
                tint = TextSecondary,
                modifier = Modifier.size(OrganizationDimensions.dp18)
            )
        }
    }
}

// ── حالة فارغة ────────────────────────────────────────────────
@Composable
private fun EmptyEmployeesState(
    onCreateInvite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(OrganizationDimensions.dp32),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OrganizationDimensions.dp16)
    ) {
        Icon(
            Icons.Filled.Group, null,
            tint = AccentDim,
            modifier = Modifier.size(OrganizationDimensions.dp72)
        )
        Text(
            androidx.compose.ui.res.stringResource(R.string.ds_4cf946e067d2),
            color = TextPrimary,
            fontSize = OrganizationTextScale.sp18,
            fontWeight = FontWeight.Bold
        )
        Text(
            androidx.compose.ui.res.stringResource(R.string.ds_dcb228c08334),
            color = TextSecondary,
            fontSize = OrganizationTextScale.sp13,
            textAlign = TextAlign.Center,
            lineHeight = OrganizationTextScale.sp20
        )
        VertoButton(
            onClick = onCreateInvite,
            colors = ButtonDefaults.buttonColors(containerColor = AccentMain)
        ) {
            Icon(Icons.Filled.PersonAdd, null, modifier = Modifier.size(OrganizationDimensions.dp18))
            Spacer(Modifier.width(OrganizationDimensions.dp8))
            Text(androidx.compose.ui.res.stringResource(R.string.ds_0c4b96d5041d))
        }
    }
}

// ── حالة الخطأ ────────────────────────────────────────────────
@Composable
private fun ErrorState(
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

// ── تنسيق التاريخ ─────────────────────────────────────────────
private fun formatJoinDate(isoDate: String): String {
    if (isoDate.isBlank()) return "—"
    return runCatching {
        val parts = isoDate.substringBefore("T").split("-")
        val months = listOf("", "يناير","فبراير","مارس","أبريل","مايو","يونيو",
            "يوليو","أغسطس","سبتمبر","أكتوبر","نوفمبر","ديسمبر")
        val d = parts[2].trimStart('0')
        val m = months[parts[1].toInt()]
        "$d $m"
    }.getOrDefault(isoDate.substringBefore("T"))
}
