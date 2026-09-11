package com.verto.app.ui.screens.settings

import com.verto.app.R

import com.verto.app.ui.components.VertoOutlinedTextField
import com.verto.app.ui.components.VertoOutlinedButton
import com.verto.app.ui.components.VertoButton
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.settings.presentation.BackupSection
import com.verto.app.feature.settings.presentation.BackupShareDialog

import com.verto.app.feature.settings.presentation.SettingsDimensions
import com.verto.app.feature.settings.presentation.SettingsTextScale
import com.verto.app.ui.components.SettingsCard
import com.verto.app.ui.components.SettingsDivider
import com.verto.app.ui.components.SettingsNavRow
import com.verto.app.ui.components.SettingsSectionHeader
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import com.verto.app.BuildConfig
import com.verto.app.feature.profile.presentation.ProfileEditDialog
import com.verto.app.feature.profile.presentation.ProfileOperationState
import com.verto.app.feature.profile.presentation.ProfileSettingsViewModel
import com.verto.app.feature.settings.domain.model.SettingsDataScope
import com.verto.app.feature.settings.presentation.main.SectionState
import com.verto.app.feature.settings.presentation.main.SettingsOperationsViewModel
import com.verto.app.ui.theme.*
import com.verto.app.utils.DateUtils
import com.verto.app.ui.components.VertoTopAppBar

// ── SettingsScreen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateThemes        : () -> Unit = {},
    onNavigateNotifications : () -> Unit = {},
    onNavigateInvoicePrint  : () -> Unit = {},
    onNavigateEmployees     : () -> Unit = {},
    onNavigateAuditLog      : () -> Unit = {},
    onNavigateOrgSettings   : () -> Unit = {},
    onNavigateManagement    : () -> Unit = {},
    onNavigateEducationalTopics : () -> Unit = {},
    onLogout                : () -> Unit = {},
    openProfileOnLaunch     : Boolean = false,
    vm: SettingsOperationsViewModel = hiltViewModel(),
    profileVm: ProfileSettingsViewModel = hiltViewModel()
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val isAdmin = uiState.isAdmin
    val canViewManagement = uiState.canViewManagement
    val lastSyncTime = uiState.lastSyncTime

    val profileUiState by profileVm.uiState.collectAsStateWithLifecycle()
    val profile = profileUiState.profile
    val canEditProfileName = profileUiState.canEditName
    val userName = profile.name
    val userPhone = profile.phone

    // ── حالة الـ Dialogs ───────────────────────────────────────────────────────
    var showProfileDialog        by rememberSaveable { mutableStateOf(openProfileOnLaunch) }
    var showLogoutConfirmDialog  by remember { mutableStateOf(false) }
    var showResetSection         by rememberSaveable { mutableStateOf(false) }
    var pendingReset             by remember { mutableStateOf<SettingsDataScope?>(null) }
    var resetConfirmText         by remember { mutableStateOf("") }
    var showBackupShareDialog    by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { vm.importBackup(it.toString()) } }

    // ── Dialog: الملف الشخصي (يشمل تغيير كلمة السر) ──────────────────────────
    if (showProfileDialog) {
        ProfileEditDialog(
            initialName  = userName,
            initialPhone = userPhone,
            canEditName   = canEditProfileName,
            isSavingProfile  = profileUiState.profileState is ProfileOperationState.Loading,
            isSavingPassword = profileUiState.passwordState is ProfileOperationState.Loading,
            passwordError    = (profileUiState.passwordState as? ProfileOperationState.Error)?.message,
            onSaveProfile = { name, phone ->
                profileVm.saveProfile(name, phone)
            },
            onSavePassword = { current, newPass ->
                profileVm.changePassword(current, newPass)
            },
            onDismiss = {
                showProfileDialog = false
                profileVm.clearPasswordState()
            }
        )
    }

    // ── Dialog: تأكيد تسجيل الخروج ────────────────────────────────────────────
    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            containerColor   = BgCard,
            title = {
                Text(androidx.compose.ui.res.stringResource(R.string.ds_8710d64ff1ad), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = SettingsTextScale.sp15)
            },
            text = {
                Text(androidx.compose.ui.res.stringResource(R.string.ds_598f1a5c1c02), color = TextSecondary, fontSize = SettingsTextScale.sp13)
            },
            confirmButton = {
                VertoButton(
                    onClick = { vm.logout(); onLogout() },
                    colors  = ButtonDefaults.buttonColors(containerColor = ErrorColor, contentColor = OnDanger),
                    shape   = RoundedCornerShape(SettingsDimensions.dp10)
                ) { Text(androidx.compose.ui.res.stringResource(R.string.ds_8710d64ff1ad), color = OnDanger) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmDialog = false }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextMuted)
                }
            }
        )
    }

    // ── Dialog: تأكيد مسح البيانات ────────────────────────────────────────────
    pendingReset?.let { scope ->
        AlertDialog(
            onDismissRequest = { pendingReset = null; resetConfirmText = "" },
            containerColor   = BgCard,
            title = {
                Text(androidx.compose.ui.res.stringResource(R.string.ds_243fc43e7380, scope.label),
                    color = ErrorColor, fontWeight = FontWeight.Bold, fontSize = SettingsTextScale.sp15)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp12)) {
                    Text(scope.warning, color = TextSecondary, fontSize = SettingsTextScale.sp13)
                    Text(androidx.compose.ui.res.stringResource(R.string.ds_2d4bfb5701d0), color = TextMuted, fontSize = SettingsTextScale.sp12)
                    VertoOutlinedTextField(
                        value         = resetConfirmText,
                        onValueChange = { resetConfirmText = it },
                        singleLine    = true,
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor      = ErrorColor,
                            unfocusedBorderColor    = BorderColor,
                            focusedContainerColor   = BgDeep,
                            unfocusedContainerColor = BgDeep,
                            focusedTextColor        = TextPrimary,
                            unfocusedTextColor      = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                VertoButton(
                    onClick = {
                        if (resetConfirmText.trim() == "تأكيد") {
                            vm.resetData(scope)
                            pendingReset = null
                            resetConfirmText = ""
                        }
                    },
                    enabled = resetConfirmText.trim() == "تأكيد",
                    colors  = ButtonDefaults.buttonColors(containerColor = ErrorColor, contentColor = OnDanger),
                    shape   = RoundedCornerShape(SettingsDimensions.dp10)
                ) { Text(androidx.compose.ui.res.stringResource(R.string.ds_05db58dea631), color = OnDanger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingReset = null; resetConfirmText = "" }) {
                    Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextMuted)
                }
            }
        )
    }

    // ── Scaffold ───────────────────────────────────────────────────────────────
    Scaffold(
        containerColor = BgDeep,
        topBar = {
            VertoTopAppBar(
                title  = { Text(androidx.compose.ui.res.stringResource(R.string.ds_90b6c869a171), color = TextPrimary, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SettingsDimensions.dp16, vertical = SettingsDimensions.dp8),
            verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp8)
        ) {

            // ── ١. الحساب ─────────────────────────────────────────────────────
            SettingsSectionHeader("الحساب")
            SettingsCard {
                SettingsNavRow(
                    title    = androidx.compose.ui.res.stringResource(R.string.ds_2f1419b78059),
                    subtitle = userName.ifBlank { "اضغط للتعديل" },
                    icon     = Icons.Filled.Person,
                    onClick  = { showProfileDialog = true }
                )
                // بيانات المؤسسة — للأدمن فقط
                if (isAdmin) {
                    SettingsDivider()
                    SettingsNavRow(
                        title    = androidx.compose.ui.res.stringResource(R.string.ds_32919de9ba0a),
                        subtitle = "الشعار، العملة، التوقيع، والمزيد",
                        icon     = Icons.Filled.Business,
                        onClick  = onNavigateOrgSettings
                    )
                }
            }

            // ── ٢. التخصيص ───────────────────────────────────────────────────
            SettingsSectionHeader("التخصيص")
            SettingsCard {
                SettingsNavRow(
                    title   = androidx.compose.ui.res.stringResource(R.string.ds_d8dfff3e6ded),
                    subtitle = "الوضع الداكن وحجم الخط",
                    icon    = Icons.Filled.Palette,
                    onClick = onNavigateThemes
                )
                SettingsDivider()
                SettingsNavRow(
                    title   = androidx.compose.ui.res.stringResource(R.string.ds_4218f4cd8cc9),
                    subtitle = "نمط الفاتورة وبيانات المنشأة",
                    icon    = Icons.Filled.Receipt,
                    onClick = onNavigateInvoicePrint
                )
                SettingsDivider()
                SettingsNavRow(
                    title   = androidx.compose.ui.res.stringResource(R.string.ds_0eb7129edb51),
                    subtitle = "تحكم في أنواع التنبيهات",
                    icon    = Icons.Filled.Notifications,
                    onClick = onNavigateNotifications
                )
            }

            // ── ٣. الإدارة ──────────────────────────────────────────────────
            if (isAdmin || canViewManagement) {
                SettingsSectionHeader("الإدارة")
                SettingsCard {
                    if (canViewManagement) {
                        SettingsNavRow(
                            title   = androidx.compose.ui.res.stringResource(R.string.ds_4e4171ec12a8),
                            subtitle = "Benzine وOptimal",
                            icon    = Icons.Filled.Hub,
                            onClick = onNavigateManagement
                        )
                    }
                    if (isAdmin) {
                        if (canViewManagement) SettingsDivider()
                        SettingsNavRow(
                            title   = androidx.compose.ui.res.stringResource(R.string.ds_9bc331c7a422),
                            subtitle = "الصلاحيات وأكواد الانضمام",
                            icon    = Icons.Filled.Group,
                            onClick = onNavigateEmployees
                        )
                        SettingsDivider()
                        SettingsNavRow(
                            title   = androidx.compose.ui.res.stringResource(R.string.ds_5591e7e4991d),
                            subtitle = "إضافة المواضيع وتحديد جمهورها",
                            icon    = Icons.Filled.School,
                            onClick = onNavigateEducationalTopics
                        )
                        SettingsDivider()
                        SettingsNavRow(
                            title   = androidx.compose.ui.res.stringResource(R.string.ds_e94c905bb082),
                            subtitle = "عرض جميع العمليات المسجلة",
                            icon    = Icons.Filled.History,
                            onClick = onNavigateAuditLog
                        )
                    }
                }
            }

            // ── ٤. المزامنة (Inline) ──────────────────────────────────────────
            SettingsSectionHeader("المزامنة")
            SyncSection(
                syncState    = uiState.syncState,
                lastSyncTime = lastSyncTime,
                onSyncNow    = { vm.syncNow() }
            )

            // ── ٥. النسخ الاحتياطي (أدمن فقط) ────────────────────────────────
            if (isAdmin) {
                SettingsSectionHeader("النسخ الاحتياطي")
                BackupSection(
                    backupState   = uiState.backupState,
                    backupMessage = uiState.backupMessage,
                    onExport      = { showBackupShareDialog = true },
                    onImport      = { importLauncher.launch("application/json") }
                )

                if (showBackupShareDialog) {
                    BackupShareDialog(
                        onDismiss = { showBackupShareDialog = false },
                        onSelect  = { target ->
                            showBackupShareDialog = false
                            vm.exportBackup(target)
                        }
                    )
                }
            }

            // ── ٦. عن التطبيق (Inline) ───────────────────────────────────────
            SettingsSectionHeader("عن التطبيق")
            SettingsCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(SettingsDimensions.dp16),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column {
                        Text(androidx.compose.ui.res.stringResource(R.string.ds_2d2bb6a420ab), color = TextPrimary, fontSize = SettingsTextScale.sp14, fontWeight = FontWeight.Bold)
                        Text(androidx.compose.ui.res.stringResource(R.string.ds_dc5e3693f8db), color = TextSecondary, fontSize = SettingsTextScale.sp12)
                    }
                    Text(
                        androidx.compose.ui.res.stringResource(R.string.ds_2682e0657f66, BuildConfig.VERSION_NAME),
                        color = AccentPrimary, fontSize = SettingsTextScale.sp12, fontWeight = FontWeight.Bold
                    )
                }
            }

            // ── ٦. إعادة تعيين البيانات (أدمن فقط) ────────────────────────────
            if (isAdmin) {
                SettingsSectionHeader("منطقة الخطر")
                SettingsCard {
                    SettingsNavRow(
                        title      = androidx.compose.ui.res.stringResource(R.string.ds_40533bce0e9b),
                        subtitle   = "حذف المبيعات أو المخزون أو الكل",
                        icon       = Icons.Filled.DeleteSweep,
                        iconTint   = ErrorColor,
                        titleColor = ErrorColor,
                        trailingIcon = if (showResetSection) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        onClick    = { showResetSection = !showResetSection }
                    )
                    AnimatedVisibility(visible = showResetSection) {
                        Column(
                            Modifier.padding(start = SettingsDimensions.dp16, end = SettingsDimensions.dp16, bottom = SettingsDimensions.dp12),
                            verticalArrangement = Arrangement.spacedBy(SettingsDimensions.dp6)
                        ) {
                            HorizontalDivider(
                                color    = ErrorColor.copy(alpha = 0.2f),
                                modifier = Modifier.padding(bottom = SettingsDimensions.dp4)
                            )
                            Text(
                                androidx.compose.ui.res.stringResource(R.string.ds_57867f746796),
                                color = WarningColor, fontSize = SettingsTextScale.sp11
                            )
                            SettingsDataScope.values().forEach { scope ->
                                val isAll = scope == SettingsDataScope.ALL
                                VertoOutlinedButton(
                                    onClick  = { pendingReset = scope; resetConfirmText = "" },
                                    enabled  = uiState.resetState !is SectionState.Loading,
                                    modifier = Modifier.fillMaxWidth(),
                                    border   = BorderStroke(SettingsDimensions.dp1, if (isAll) ErrorColor else ErrorColor.copy(0.4f)),
                                    shape    = RoundedCornerShape(SettingsDimensions.dp10),
                                    colors   = ButtonDefaults.outlinedButtonColors(
                                        contentColor = ErrorColor
                                    )
                                ) {
                                    Icon(
                                        if (isAll) Icons.Filled.DeleteForever else Icons.Filled.Delete,
                                        null, modifier = Modifier.size(SettingsDimensions.dp15)
                                    )
                                    Spacer(Modifier.width(SettingsDimensions.dp8))
                                    Text(
                                        androidx.compose.ui.res.stringResource(R.string.ds_34d8c8a1999e, scope.label),
                                        fontSize   = SettingsTextScale.sp13,
                                        fontWeight = if (isAll) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── ٧. تسجيل الخروج ───────────────────────────────────────────────
            Spacer(Modifier.height(SettingsDimensions.dp4))
            VertoOutlinedButton(
                onClick  = { showLogoutConfirmDialog = true },
                border   = BorderStroke(SettingsDimensions.dp1, ErrorColor),
                shape    = RoundedCornerShape(SettingsDimensions.dp12),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Logout, null, tint = ErrorColor, modifier = Modifier.size(SettingsDimensions.dp18))
                Spacer(Modifier.width(SettingsDimensions.dp8))
                Text(androidx.compose.ui.res.stringResource(R.string.ds_8710d64ff1ad), color = ErrorColor, fontSize = SettingsTextScale.sp14)
            }

            Spacer(Modifier.height(SettingsDimensions.dp24))
        }
    }
}

// ── SyncSection ────────────────────────────────────────────────────────────────

@Composable
private fun SyncSection(
    syncState    : SectionState,
    lastSyncTime : Long?,
    onSyncNow    : () -> Unit
) {
    SettingsCard {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(SettingsDimensions.dp16),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(androidx.compose.ui.res.stringResource(R.string.ds_94585d763f1a), color = TextPrimary, fontSize = SettingsTextScale.sp14, fontWeight = FontWeight.Bold)
                val subtitle = when (syncState) {
                    is SectionState.Loading -> "جارٍ المزامنة..."
                    is SectionState.Success -> "✓ تمت المزامنة"
                    is SectionState.Error   -> "✗ ${syncState.msg}"
                    else -> if (lastSyncTime != null)
                        "آخر مزامنة: ${DateUtils.formatTimeAgo(lastSyncTime)}"
                    else
                        "لم تتم المزامنة بعد"
                }
                val subtitleColor = when (syncState) {
                    is SectionState.Success -> SuccessColor
                    is SectionState.Error   -> ErrorColor
                    is SectionState.Loading -> AccentPrimary
                    else                    -> TextSecondary
                }
                Text(subtitle, color = subtitleColor, fontSize = SettingsTextScale.sp12)
            }
            Spacer(Modifier.width(SettingsDimensions.dp12))
            VertoButton(
                onClick  = onSyncNow,
                enabled  = syncState !is SectionState.Loading,
                colors   = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                shape    = RoundedCornerShape(SettingsDimensions.dp10),
                contentPadding = PaddingValues(horizontal = SettingsDimensions.dp16, vertical = SettingsDimensions.dp8)
            ) {
                if (syncState is SectionState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(SettingsDimensions.dp16),
                        color = TextPrimary, strokeWidth = SettingsDimensions.dp2
                    )
                } else {
                    Icon(Icons.Filled.Sync, null, modifier = Modifier.size(SettingsDimensions.dp16))
                    Spacer(Modifier.width(SettingsDimensions.dp4))
                    Text(androidx.compose.ui.res.stringResource(R.string.ds_5ad06a96052f), fontSize = SettingsTextScale.sp13)
                }
            }
        }
    }
}

private val SettingsDataScope.label: String
    get() = when (this) {
        SettingsDataScope.SALES_INVOICES -> "فواتير المبيعات"
        SettingsDataScope.PURCHASE_INVOICES -> "فواتير المشتريات"
        SettingsDataScope.CLIENTS -> "العملاء والموردين"
        SettingsDataScope.INVENTORY -> "المخزون"
        SettingsDataScope.CASH_REGISTER -> "الصندوق"
        SettingsDataScope.ALL -> "كل البيانات"
    }

private val SettingsDataScope.warning: String
    get() = when (this) {
        SettingsDataScope.SALES_INVOICES ->
            "سيتم حذف جميع فواتير المبيعات والمدفوعات المرتبطة بها نهائياً."
        SettingsDataScope.PURCHASE_INVOICES ->
            "سيتم حذف جميع فواتير المشتريات والمدفوعات المرتبطة بها نهائياً."
        SettingsDataScope.CLIENTS ->
            "سيتم حذف جميع العملاء والموردين وكل فواتيرهم ومدفوعاتهم نهائياً."
        SettingsDataScope.INVENTORY ->
            "سيتم حذف جميع أصناف المخزون وسجل الحركات نهائياً."
        SettingsDataScope.CASH_REGISTER ->
            "سيتم إعادة رصيد الصندوق إلى الصفر وحذف جميع الحركات نهائياً."
        SettingsDataScope.ALL ->
            "⚠️ سيتم حذف جميع الفواتير والعملاء والمخزون والصندوق نهائياً. لا يمكن التراجع."
    }
