package com.verto.app.feature.organization.presentation.team

import com.verto.feature.organization.R

import com.verto.app.feature.organization.presentation.OrganizationDimensions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import java.util.Calendar
import java.util.TimeZone
import com.verto.app.ui.theme.AccentBlue
import com.verto.app.ui.theme.AccentMain
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BgCardAlt
import com.verto.app.ui.theme.BgDeep
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.GoldPrimary
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.WarningColor
import com.verto.app.ui.theme.ErrorColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextOnAccent
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeDetailScreen(
    userId: String,
    onBack: () -> Unit,
    onManagePermissions: () -> Unit,
    teamVm: OrganizationTeamViewModel,
    viewModel: EmployeeDetailViewModel = hiltViewModel()
) {
    val teamUiState by teamVm.uiState.collectAsStateWithLifecycle()
    val employeesState = teamUiState.employees
    val detailState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTemplate by remember { mutableStateOf<NotificationTemplate?>(null) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (employeesState.employees.isEmpty()) teamVm.onEvent(OrganizationTeamEvent.LoadEmployees)
    }

    val employee = employeesState.employees.firstOrNull { it.userId == userId }
    LaunchedEffect(userId, employee) {
        viewModel.onEvent(EmployeeDetailEvent.Load(userId, employee))
    }

    selectedTemplate?.let { template ->
        NotificationPreviewDialog(
            template = template,
            onDismiss = { selectedTemplate = null },
            onSend = { selectedTemplate = null }
        )
    }

    if (showFromPicker) {
        PeriodDatePickerDialog(
            initialMillis = detailState.periodFrom,
            onDismiss = { showFromPicker = false },
            onConfirm = { picked ->
                showFromPicker = false
                viewModel.onEvent(EmployeeDetailEvent.PeriodChanged(picked, detailState.periodTo))
            }
        )
    }
    if (showToPicker) {
        PeriodDatePickerDialog(
            initialMillis = detailState.periodTo,
            onDismiss = { showToPicker = false },
            onConfirm = { picked ->
                showToPicker = false
                viewModel.onEvent(EmployeeDetailEvent.PeriodChanged(detailState.periodFrom, picked))
            }
        )
    }

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            VertoTopAppBar(
                title = { Text(androidx.compose.ui.res.stringResource(R.string.ds_5e9b46c91615), color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    VertoIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = OrganizationDimensions.dp16, vertical = OrganizationDimensions.dp12),
            verticalArrangement = Arrangement.spacedBy(OrganizationDimensions.dp12)
        ) {
            item { EmployeeHeaderCard(detailState.profile) }
            item {
                PerformanceCard(
                    performance = detailState.performance,
                    loading = detailState.performanceLoading,
                    error = detailState.performanceError,
                    onPickFrom = { showFromPicker = true },
                    onPickTo = { showToPicker = true },
                    onPreset = { preset -> viewModel.setPeriod(preset.first, preset.second) }
                )
            }
            item { AttendanceCard(detailState.attendance) }
            item {
                PermissionsSummaryCard(
                    summary = detailState.permissionsSummary,
                    onManagePermissions = onManagePermissions
                )
            }
            item {
                CommunicationCard(
                    templates = detailState.communicationTemplates,
                    onTemplateClick = { selectedTemplate = it }
                )
            }
            item { GrowthAndTasksCard(detailState.growth) }
            item { Spacer(Modifier.height(OrganizationDimensions.dp16)) }
        }
    }
}
