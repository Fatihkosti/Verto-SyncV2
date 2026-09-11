package com.verto.app.feature.organization.presentation.team

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.res.stringResource

import com.verto.feature.organization.R

import com.verto.app.ui.components.VertoOutlinedTextField
import com.verto.app.ui.components.VertoOutlinedButton
import com.verto.app.ui.components.VertoButton

import com.verto.app.feature.organization.presentation.OrganizationDimensions
import com.verto.app.feature.organization.presentation.OrganizationTextScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.verto.app.ui.components.VertoCard
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoTopAppBar
import androidx.compose.foundation.layout.PaddingValues
import com.verto.app.ui.theme.VertoSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateInviteScreen(
    onBack: () -> Unit = {},
    vm: OrganizationTeamViewModel
) {
    val teamUiState by vm.uiState.collectAsStateWithLifecycle()
    val uiState = teamUiState.createInvite
    val clipboard  = LocalClipboardManager.current
    val resourceContext = LocalContext.current
    val sections   = remember(resourceContext) { buildPermissionSections(resourceContext::getString) }
    val expandedSections = remember { mutableStateMapOf<String, Boolean>() }
    var showDatePicker by remember { mutableStateOf(false) }
    val jobTitles = remember { listOf("مبيعات", "مشتريات", "محاسب", "مخزن") }

    // إعادة تهيئة عند الدخول
    LaunchedEffect(Unit) { vm.onEvent(OrganizationTeamEvent.ResetCreateInvite) }

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            VertoTopAppBar(
                title  = { Text(androidx.compose.ui.res.stringResource(R.string.ds_8da173f0cf53), color = TextPrimary, fontWeight = FontWeight.Bold) },
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

            // ── الكود الناتج (يظهر بعد التوليد) ──────────────
            if (uiState.generatedCode != null) {
                item {
                    GeneratedCodeCard(
                        code = checkNotNull(uiState.generatedCode),
                        employeeName = uiState.employeeName,
                        onCopy = {
                            clipboard.setText(AnnotatedString(checkNotNull(uiState.generatedCode)))
                        },
                        onNewCode = { vm.onEvent(OrganizationTeamEvent.ResetCreateInvite) }
                    )
                }
            }

            item {
                EmployeeDetailsCard(
                    name = uiState.employeeName,
                    jobTitle = uiState.jobTitle,
                    actualJoinDate = uiState.actualJoinDate,
                    jobTitles = jobTitles,
                    enabled = uiState.generatedCode == null,
                    onNameChange = vm::updateInviteEmployeeName,
                    onJobTitleChange = vm::updateInviteJobTitle,
                    onDateClick = { showDatePicker = true }
                )
            }

            item {
                Text(androidx.compose.ui.res.stringResource(R.string.ds_401c37828f9d), color = TextPrimary, fontSize = OrganizationTextScale.sp17, fontWeight = FontWeight.Bold)
            }

            // ── أقسام الصلاحيات ───────────────────────────────
            items(sections, key = { it.key }) { section ->
                val isExpanded = expandedSections[section.key] ?: false
                PermissionSectionCard(
                    section             = section,
                    permissions         = uiState.permissions,
                    isExpanded          = isExpanded,
                    onToggleExpand      = {
                        expandedSections[section.key] = !(expandedSections[section.key] ?: false)
                    },
                    onPermissionsChange = vm::updateInvitePermissions,
                    enabled             = uiState.generatedCode == null
                )
            }

            // ── زر التوليد ────────────────────────────────────
            if (uiState.generatedCode == null) {
                item {
                    Spacer(Modifier.height(OrganizationDimensions.dp4))
                    VertoButton(
                        onClick  = { vm.onEvent(OrganizationTeamEvent.GenerateInviteCode) },
                        enabled  = !uiState.isGenerating,
                        modifier = Modifier.fillMaxWidth().height(OrganizationDimensions.dp52),
                        colors   = ButtonDefaults.buttonColors(containerColor = AccentMain),
                        shape    = RoundedCornerShape(OrganizationDimensions.dp12)
                    ) {
                        if (uiState.isGenerating) {
                            CircularProgressIndicator(
                                color = TextOnAccent,
                                modifier = Modifier.size(OrganizationDimensions.dp20),
                                strokeWidth = OrganizationDimensions.dp2
                            )
                            Spacer(Modifier.width(OrganizationDimensions.dp8))
                        } else {
                            Icon(Icons.Filled.Key, null, modifier = Modifier.size(OrganizationDimensions.dp20))
                            Spacer(Modifier.width(OrganizationDimensions.dp8))
                        }
                        Text(
                            if (uiState.isGenerating) stringResource(R.string.legacy_ui_84f7e0956030) else stringResource(R.string.legacy_ui_25e3c9737ca2),
                            fontWeight = FontWeight.Bold,
                            fontSize = OrganizationTextScale.sp15
                        )
                    }

                    uiState.error?.let { err ->
                        Spacer(Modifier.height(OrganizationDimensions.dp8))
                        Text(err, color = ErrorColor, fontSize = OrganizationTextScale.sp12, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            // مسافة في الأسفل
            item { Spacer(Modifier.height(OrganizationDimensions.dp24)) }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        vm.updateInviteActualJoinDate(formatInviteDate(it))
                    }
                    showDatePicker = false
                }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel)) }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmployeeDetailsCard(
    name: String,
    jobTitle: String,
    actualJoinDate: String,
    jobTitles: List<String>,
    enabled: Boolean,
    onNameChange: (String) -> Unit,
    onJobTitleChange: (String) -> Unit,
    onDateClick: () -> Unit
) {
    var jobExpanded by remember { mutableStateOf(false) }

    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
        shape = RoundedCornerShape(OrganizationDimensions.dp14),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(OrganizationDimensions.dp16),
            verticalArrangement = Arrangement.spacedBy(OrganizationDimensions.dp12)
        ) {
            Text(androidx.compose.ui.res.stringResource(R.string.ds_942623106f7d), color = TextPrimary, fontSize = OrganizationTextScale.sp17, fontWeight = FontWeight.Bold)

            VertoOutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                enabled = enabled,
                label = { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(OrganizationDimensions.dp12),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentMain,
                    unfocusedBorderColor = BorderColor,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    disabledTextColor = TextSecondary
                )
            )

            ExposedDropdownMenuBox(
                expanded = jobExpanded,
                onExpandedChange = { if (enabled) jobExpanded = !jobExpanded }
            ) {
                VertoOutlinedTextField(
                    value = jobTitle,
                    onValueChange = {},
                    readOnly = true,
                    enabled = enabled,
                    label = { Text(androidx.compose.ui.res.stringResource(R.string.ds_6951dce35650)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = jobExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(OrganizationDimensions.dp12),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentMain,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        disabledTextColor = TextSecondary
                    )
                )
                ExposedDropdownMenu(
                    expanded = jobExpanded,
                    onDismissRequest = { jobExpanded = false }
                ) {
                    jobTitles.forEach { title ->
                        DropdownMenuItem(
                            text = { Text(title) },
                            onClick = {
                                onJobTitleChange(title)
                                jobExpanded = false
                            }
                        )
                    }
                }
            }

            VertoOutlinedTextField(
                value = actualJoinDate,
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                label = { Text(androidx.compose.ui.res.stringResource(R.string.ds_4c16231c900a)) },
                trailingIcon = {
                    VertoIconButton(onClick = onDateClick, enabled = enabled) {
                        Icon(Icons.Filled.CalendarMonth, null, tint = TextSecondary)
                    }
                },
                modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onDateClick() },
                shape = RoundedCornerShape(OrganizationDimensions.dp12),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentMain,
                    unfocusedBorderColor = BorderColor,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    disabledTextColor = TextSecondary
                )
            )
        }
    }
}

// ── بطاقة الكود الناتج ────────────────────────────────────────
@Composable
private fun GeneratedCodeCard(
    code: String,
    employeeName: String,
    onCopy: () -> Unit,
    onNewCode: () -> Unit
) {
    var copied by remember { mutableStateOf(false) }

    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(OrganizationDimensions.dp16),
        colors   = CardDefaults.cardColors(containerColor = AccentMain.copy(alpha = 0.08f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(OrganizationDimensions.dp20),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OrganizationDimensions.dp16)
        ) {
            Icon(Icons.Filled.CheckCircle, null, tint = SuccessColor, modifier = Modifier.size(OrganizationDimensions.dp36))

            Text(
                androidx.compose.ui.res.stringResource(R.string.ds_27b688a70b70, employeeName),
                color = TextPrimary,
                fontSize = OrganizationTextScale.sp15,
                fontWeight = FontWeight.Bold
            )

            // عرض الكود
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCard, RoundedCornerShape(OrganizationDimensions.dp12))
                    .border(OrganizationDimensions.dp1, AccentMain.copy(alpha = 0.3f), RoundedCornerShape(OrganizationDimensions.dp12))
                    .padding(vertical = OrganizationDimensions.dp16),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    code,
                    color      = AccentMain,
                    fontSize   = OrganizationTextScale.sp28,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = OrganizationTextScale.sp4
                )
            }

            Text(
                androidx.compose.ui.res.stringResource(R.string.ds_002f7b7310f1),
                color = TextSecondary,
                fontSize = OrganizationTextScale.sp12
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OrganizationDimensions.dp10)
            ) {
                // زر نسخ
                VertoButton(
                    onClick = {
                        onCopy()
                        copied = true
                    },
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = if (copied) SuccessColor else AccentMain
                    ),
                    shape = RoundedCornerShape(OrganizationDimensions.dp10)
                ) {
                    Icon(
                        if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        null, modifier = Modifier.size(OrganizationDimensions.dp16)
                    )
                    Spacer(Modifier.width(OrganizationDimensions.dp6))
                    Text(if (copied) stringResource(R.string.legacy_ui_559a1f710780) else stringResource(R.string.legacy_ui_b26f5e506bed))
                }

                // زر كود جديد
                VertoOutlinedButton(
                    onClick = onNewCode,
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(OrganizationDimensions.dp10)
                ) {
                    Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(OrganizationDimensions.dp16))
                    Spacer(Modifier.width(OrganizationDimensions.dp6))
                    Text(androidx.compose.ui.res.stringResource(R.string.ds_c6e12b82d972))
                }
            }
        }
    }
}

private fun formatInviteDate(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(epochMs))
