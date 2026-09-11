package com.verto.app.feature.organization.presentation

import androidx.compose.ui.res.stringResource

import com.verto.feature.organization.R

import com.verto.app.ui.components.VertoOutlinedButton

import com.verto.app.ui.components.DialogTextField
import com.verto.app.ui.components.SettingsCard
import com.verto.app.ui.components.SettingsSectionHeader
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.verto.app.ui.theme.*
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoTopAppBar

// ── OrgSettingsScreen ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizationSettingsScreen(
    onBack : () -> Unit = {},
    vm     : OrganizationSettingsViewModel
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val orgSettings = uiState.settings
    val saveResult = uiState.saveState
    val isSaving = saveResult is OrganizationSaveState.Loading

    // ── حالة الحقول المحلية — تُقرأ من orgSettings مباشرة ──────────────────────
    var shopName  by remember(orgSettings) { mutableStateOf(orgSettings.shopName) }
    var city      by remember(orgSettings) { mutableStateOf(orgSettings.city) }
    var address   by remember(orgSettings) { mutableStateOf(orgSettings.address) }
    var phone     by remember(orgSettings) { mutableStateOf(orgSettings.shopPhone) }
    var currency  by remember(orgSettings) { mutableStateOf(orgSettings.currency) }
    var logoUri   by remember(orgSettings) { mutableStateOf<Uri?>(
        orgSettings.logoUrl.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
    )}
    var signatureUri by remember(orgSettings) { mutableStateOf<Uri?>(
        orgSettings.signatureUrl.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
    )}

    // ── Pickers ────────────────────────────────────────────────────────────────
    val logoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { logoUri = it } }

    val signaturePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { signatureUri = it } }

    // ── نتيجة الحفظ ───────────────────────────────────────────────────────────
    var showSuccessBanner by remember { mutableStateOf(false) }

    LaunchedEffect(saveResult) {
        if (saveResult is OrganizationSaveState.Success) {
            showSuccessBanner = true
            kotlinx.coroutines.delay(2_000)
            showSuccessBanner = false
        }
    }

    // ── Scaffold ───────────────────────────────────────────────────────────────
    Scaffold(
        containerColor = BgDeep,
        topBar = {
            VertoTopAppBar(
                title  = { Text(androidx.compose.ui.res.stringResource(R.string.ds_32919de9ba0a), color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    VertoIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                    }
                },
                actions = {
                    // زر حفظ في AppBar
                    TextButton(
                        onClick  = {
                            vm.onEvent(
                                OrganizationSettingsEvent.Save(
                                    orgSettings.copy(
                                    shopName     = shopName.trim(),
                                    shopPhone    = phone.trim(),
                                    city         = city.trim(),
                                    address      = address.trim(),
                                    currency     = currency.trim(),
                                    logoUrl      = logoUri?.toString()      ?: "",
                                    signatureUrl = signatureUri?.toString() ?: ""
                                    )
                                )
                            )
                        },
                        enabled  = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(OrganizationDimensions.dp16),
                                color       = AccentPrimary,
                                strokeWidth = OrganizationDimensions.dp2
                            )
                        } else {
                            Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_save), color = AccentPrimary, fontWeight = FontWeight.Bold, fontSize = OrganizationTextScale.sp14)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OrganizationDimensions.dp16, vertical = OrganizationDimensions.dp8),
            verticalArrangement = Arrangement.spacedBy(OrganizationDimensions.dp16)
        ) {

            // ── بانر النجاح ───────────────────────────────────────────────────
            if (showSuccessBanner) {
                Surface(
                    color  = SuccessColor.copy(alpha = 0.15f),
                    shape  = RoundedCornerShape(OrganizationDimensions.dp10),
                    border = BorderStroke(OrganizationDimensions.dp1, SuccessColor.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(OrganizationDimensions.dp12),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, tint = SuccessColor, modifier = Modifier.size(OrganizationDimensions.dp16))
                        Spacer(Modifier.width(OrganizationDimensions.dp8))
                        Text(androidx.compose.ui.res.stringResource(R.string.ds_8d9c2b2664ee), color = SuccessColor, fontSize = OrganizationTextScale.sp13)
                    }
                }
            }

            // ── خطأ ───────────────────────────────────────────────────────────
            val saveError = (saveResult as? OrganizationSaveState.Error)?.message
            if (saveError != null) {
                Surface(
                    color  = ErrorColor.copy(alpha = 0.1f),
                    shape  = RoundedCornerShape(OrganizationDimensions.dp10),
                    border = BorderStroke(OrganizationDimensions.dp1, ErrorColor.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(OrganizationDimensions.dp12),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Error, null, tint = ErrorColor, modifier = Modifier.size(OrganizationDimensions.dp16))
                        Spacer(Modifier.width(OrganizationDimensions.dp8))
                        Text(saveError, color = ErrorColor, fontSize = OrganizationTextScale.sp13)
                    }
                }
            }

            // ── ١. بيانات المحل الأساسية ──────────────────────────────────────
            SettingsSectionHeader(title = androidx.compose.ui.res.stringResource(R.string.ds_b25718d00d28))
            SettingsCard {
                OrgTextField(
                    label         = androidx.compose.ui.res.stringResource(R.string.ds_59539fc909e2),
                    value         = shopName,
                    onValueChange = { shopName = it },
                    leadingIcon   = Icons.Filled.Store
                )
                OrgTextField(
                    label         = androidx.compose.ui.res.stringResource(R.string.ds_75124a0090df),
                    value         = city,
                    onValueChange = { city = it },
                    leadingIcon   = Icons.Filled.LocationCity
                )
                OrgTextField(
                    label         = androidx.compose.ui.res.stringResource(R.string.ds_baffa49c77ea),
                    value         = address,
                    onValueChange = { address = it },
                    leadingIcon   = Icons.Filled.Place
                )
                OrgTextField(
                    label         = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_phone_number),
                    value         = phone,
                    onValueChange = { phone = it },
                    leadingIcon   = Icons.Filled.Phone,
                    keyboardType  = KeyboardType.Phone
                )
            }

            // ── ٢. العملة ─────────────────────────────────────────────────────
            SettingsSectionHeader(title = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_currency))
            SettingsCard {
                OrgTextField(
                    label         = androidx.compose.ui.res.stringResource(R.string.ds_7cb3970d3acd),
                    value         = currency,
                    onValueChange = { currency = it },
                    leadingIcon   = Icons.Filled.Payments
                )
            }

            // ── ٣. الشعار ─────────────────────────────────────────────────────
            SettingsSectionHeader(title = androidx.compose.ui.res.stringResource(R.string.ds_4c25c236bd1c))
            SettingsCard {
                ImagePickerRow(
                    label       = androidx.compose.ui.res.stringResource(R.string.ds_d17dd3001f17),
                    hint        = "يظهر في رأس الفاتورة المطبوعة",
                    uri         = logoUri,
                    placeholder = Icons.Filled.Image,
                    onPick      = { logoPicker.launch("image/*") },
                    onClear     = { logoUri = null }
                )
            }

            // ── ٤. التوقيع ────────────────────────────────────────────────────
            SettingsSectionHeader(title = androidx.compose.ui.res.stringResource(R.string.ds_9830d6fd532b))
            SettingsCard {
                ImagePickerRow(
                    label       = androidx.compose.ui.res.stringResource(R.string.ds_d14b027c8063),
                    hint        = "يظهر في أسفل الفاتورة المطبوعة",
                    uri         = signatureUri,
                    placeholder = Icons.Filled.Draw,
                    onPick      = { signaturePicker.launch("image/*") },
                    onClear     = { signatureUri = null }
                )
            }

            // ── ملاحظة ────────────────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(OrganizationDimensions.dp8))
                    .background(AccentPrimary.copy(alpha = 0.07f))
                    .padding(OrganizationDimensions.dp12),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Filled.Info, null,
                    tint     = AccentPrimary,
                    modifier = Modifier.size(OrganizationDimensions.dp15).padding(top = OrganizationDimensions.dp1)
                )
                Spacer(Modifier.width(OrganizationDimensions.dp8))
                Text(
                    androidx.compose.ui.res.stringResource(R.string.ds_cb791d19dc7d),
                    color    = AccentPrimary,
                    fontSize = OrganizationTextScale.sp11,
                    lineHeight = OrganizationTextScale.sp16
                )
            }

            Spacer(Modifier.height(OrganizationDimensions.dp32))
        }
    }
}


// ── ImagePickerRow ─────────────────────────────────────────────────────────────

@Composable
private fun ImagePickerRow(
    label       : String,
    hint        : String,
    uri         : Uri?,
    placeholder : androidx.compose.ui.graphics.vector.ImageVector,
    onPick      : () -> Unit,
    onClear     : () -> Unit
) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(OrganizationDimensions.dp16),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Preview
        Box(
            Modifier
                .size(OrganizationDimensions.dp72)
                .clip(RoundedCornerShape(OrganizationDimensions.dp10))
                .background(BgDeep)
                .border(OrganizationDimensions.dp1, BorderColor, RoundedCornerShape(OrganizationDimensions.dp10))
                .clickable { onPick() },
            contentAlignment = Alignment.Center
        ) {
            if (uri != null) {
                AsyncImage(
                    model             = ImageRequest.Builder(context).data(uri).crossfade(true).build(),
                    contentDescription = label,
                    contentScale      = ContentScale.Crop,
                    modifier          = Modifier.fillMaxSize().clip(RoundedCornerShape(OrganizationDimensions.dp10))
                )
                // زر X لإزالة الصورة
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = OrganizationDimensions.dp4, y = (-4).dp)
                        .size(OrganizationDimensions.dp18)
                        .clip(CircleShape)
                        .background(ErrorColor)
                        .clickable { onClear() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, null, tint = OnDanger, modifier = Modifier.size(OrganizationDimensions.dp11))
                }
            } else {
                Icon(placeholder, null, tint = TextMuted, modifier = Modifier.size(OrganizationDimensions.dp28))
            }
        }

        Spacer(Modifier.width(OrganizationDimensions.dp14))

        Column(Modifier.weight(1f)) {
            Text(label, color = TextPrimary, fontSize = OrganizationTextScale.sp14, fontWeight = FontWeight.Medium)
            Text(hint, color = TextSecondary, fontSize = OrganizationTextScale.sp11, lineHeight = OrganizationTextScale.sp15)
            Spacer(Modifier.height(OrganizationDimensions.dp6))
            VertoOutlinedButton(
                onClick      = onPick,
                shape        = RoundedCornerShape(OrganizationDimensions.dp8),
                border       = BorderStroke(OrganizationDimensions.dp1, AccentPrimary.copy(alpha = 0.6f)),
                colors       = ButtonDefaults.outlinedButtonColors(contentColor = AccentPrimary),
                contentPadding = PaddingValues(horizontal = OrganizationDimensions.dp12, vertical = OrganizationDimensions.dp4)
            ) {
                Icon(Icons.Filled.Upload, null, modifier = Modifier.size(OrganizationDimensions.dp14))
                Spacer(Modifier.width(OrganizationDimensions.dp6))
                Text(if (uri != null) stringResource(R.string.legacy_ui_65de8039dcae) else stringResource(R.string.legacy_ui_f0b5c8ab3dcb), fontSize = OrganizationTextScale.sp12)
            }
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

@Composable
private fun OrgTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    DialogTextField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        keyboardType = keyboardType,
        modifier = Modifier.padding(
            horizontal = OrganizationDimensions.dp12,
            vertical = OrganizationDimensions.dp6,
        ),
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(OrganizationDimensions.dp18),
            )
        },
    )
}
