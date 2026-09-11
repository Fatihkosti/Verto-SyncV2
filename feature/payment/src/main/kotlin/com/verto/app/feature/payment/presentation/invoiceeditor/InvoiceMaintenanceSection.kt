package com.verto.app.feature.payment.presentation.invoiceeditor

import com.verto.app.ui.components.VertoOutlinedTextField
import com.verto.app.ui.components.VertoOutlinedButton

import com.verto.app.feature.payment.application.model.*
import com.verto.app.feature.payment.presentation.PaymentDimensions
import com.verto.app.feature.payment.presentation.PaymentTextScale

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verto.app.feature.payment.application.model.ClientItem
import com.verto.app.ui.theme.AccentDim
import com.verto.app.ui.theme.AccentLight
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BgSurface
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.SuccessContainer
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import java.util.UUID

@Composable
internal fun InvoiceMaintenanceFormItem(
    form: InvoiceEditorFormState,
    allClients: List<ClientItem>,
    viewModel: InvoiceEditorViewModel,
    isInternational: Boolean,
    modifier: Modifier = Modifier,
) {
    val suggestions by viewModel.maintenanceVehicleSuggestions.collectAsStateWithLifecycle()
    val activeOrganizationId by viewModel.activeOrganizationId.collectAsStateWithLifecycle()
    val selectedClient = allClients.firstOrNull { it.id == form.selectedClientId }
    val enabled = form.isSale && !isInternational &&
        selectedClient?.customerSegment.isCompanyCustomerSegment()

    LaunchedEffect(activeOrganizationId, form.selectedClientId, enabled) {
        form.maintenance = form.maintenance.bindClient(
            organizationId = activeOrganizationId,
            clientId = form.selectedClientId,
            hasCompanyType = enabled,
        )
    }
    LaunchedEffect(
        form.maintenance.isEnabled,
        form.maintenance.ownerClientId,
        form.maintenance.vehicleQuery,
    ) {
        viewModel.updateMaintenanceVehicleSearch(
            clientId = form.maintenance.ownerClientId,
            searchTerm = form.maintenance.vehicleQuery,
            enabled = form.maintenance.isEnabled,
        )
    }

    if (form.maintenance.isEnabled) {
        InvoiceMaintenanceSection(
            state = form.maintenance,
            suggestions = suggestions.filter { it.clientId == form.selectedClientId },
            onToggle = { form.maintenance = form.maintenance.toggleExpanded() },
            onVehicleQueryChange = { form.maintenance = form.maintenance.updateVehicleQuery(it) },
            onSuggestionSelected = { form.maintenance = form.maintenance.selectSuggestion(it) },
            onPlateNumberChange = { form.maintenance = form.maintenance.updatePlateNumber(it) },
            onDriverOrDelegateChange = { form.maintenance = form.maintenance.updateDriverOrDelegate(it) },
            onNotesChange = { form.maintenance = form.maintenance.updateNotes(it) },
            onImagesAdded = { form.maintenance = form.maintenance.addImages(it) },
            onImageRemoved = { form.maintenance = form.maintenance.removeImage(it) },
            modifier = modifier,
        )
    }
}

@Composable
internal fun InvoiceMaintenanceSection(
    state: InvoiceMaintenanceFormState,
    suggestions: List<VehicleSuggestion>,
    onToggle: () -> Unit,
    onVehicleQueryChange: (String) -> Unit,
    onSuggestionSelected: (VehicleSuggestion) -> Unit,
    onPlateNumberChange: (String) -> Unit,
    onDriverOrDelegateChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onImagesAdded: (List<CompanyMaintenanceImageData>) -> Unit,
    onImageRemoved: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isEnabled) return

    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        val remaining = (InvoiceMaintenanceFormState.MaxImages - state.images.size).coerceAtLeast(0)
        val selected = uris.take(remaining).mapIndexed { index, uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            CompanyMaintenanceImageData(
                imageId = UUID.randomUUID().toString(),
                localUri = uri.toString(),
                mimeType = context.contentResolver.getType(uri).orEmpty().ifBlank { "image/*" },
                byteSize = context.contentResolver.openAssetFileDescriptor(uri, "r")
                    ?.use { descriptor -> descriptor.length.takeIf { it >= 0L } ?: 0L }
                    ?: 0L,
                sortOrder = state.images.size + index,
            )
        }
        onImagesAdded(selected)
    }

    Surface(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        color = BgCard,
        shape = RoundedCornerShape(PaymentDimensions.dp14),
        border = BorderStroke(PaymentDimensions.dp1, AccentPrimary.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = PaymentDimensions.dp14, vertical = PaymentDimensions.dp13),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(PaymentDimensions.dp9),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.DirectionsCar,
                        contentDescription = null,
                        tint = AccentPrimary,
                        modifier = Modifier.size(PaymentDimensions.dp20),
                    )
                    Column {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_805be7445a76),
                            color = TextPrimary,
                            fontSize = PaymentTextScale.sp14,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_0afc564d1d18),
                            color = TextMuted,
                            fontSize = PaymentTextScale.sp10,
                        )
                    }
                }
                Icon(
                    imageVector = if (state.isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (state.isExpanded) androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_v298_67162a6abd83) else androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_open),
                    tint = TextSecondary,
                )
            }

            if (state.isExpanded) {
                HorizontalDivider(color = BorderColor)
                Column(
                    modifier = Modifier.padding(PaymentDimensions.dp14),
                    verticalArrangement = Arrangement.spacedBy(PaymentDimensions.dp10),
                ) {
                    MaintenanceTextField(
                        value = state.vehicleQuery,
                        onValueChange = onVehicleQueryChange,
                        label = androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_2fa0b20ec00e),
                        placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_371b6485c111),
                        leadingIcon = {
                            Icon(Icons.Filled.DirectionsCar, null, tint = TextMuted)
                        },
                    )

                    if (suggestions.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = PaymentDimensions.dp2),
                        ) {
                            suggestions.take(5).forEachIndexed { index, vehicle ->
                                VehicleSuggestionRow(
                                    vehicle = vehicle,
                                    selected = state.selectedOfficialVehicle?.remoteVehicleId == vehicle.remoteVehicleId,
                                    onClick = { onSuggestionSelected(vehicle) },
                                )
                                if (index < suggestions.take(5).lastIndex) {
                                    HorizontalDivider(color = BorderColor.copy(alpha = 0.6f))
                                }
                            }
                        }
                    } else if (state.vehicleQuery.isNotBlank()) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_996b82b34e59),
                            color = TextMuted,
                            fontSize = PaymentTextScale.sp11,
                        )
                    }

                    state.selectedOfficialVehicle?.let {
                        Surface(
                            color = SuccessContainer,
                            shape = RoundedCornerShape(PaymentDimensions.dp8),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = PaymentDimensions.dp10, vertical = PaymentDimensions.dp7),
                                horizontalArrangement = Arrangement.spacedBy(PaymentDimensions.dp6),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Link, null, tint = SuccessColor, modifier = Modifier.size(PaymentDimensions.dp14))
                                Text(androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_b0dccf38e5fa), color = SuccessColor, fontSize = PaymentTextScale.sp11)
                            }
                        }
                    }

                    MaintenanceTextField(
                        value = state.plateNumber,
                        onValueChange = onPlateNumberChange,
                        label = androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_d4862b1b04ee),
                        placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_fb4eb0bbfb7f),
                        leadingIcon = { Icon(Icons.Filled.Tag, null, tint = TextMuted) },
                    )
                    MaintenanceTextField(
                        value = state.driverOrDelegate,
                        onValueChange = onDriverOrDelegateChange,
                        label = androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_9d1fff301aff),
                        placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_7a168061c36f),
                        leadingIcon = { Icon(Icons.Filled.Badge, null, tint = TextMuted) },
                    )
                    MaintenanceTextField(
                        value = state.notes,
                        onValueChange = onNotesChange,
                        label = androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_d20f71350085),
                        placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_539b58dcff4e),
                        singleLine = false,
                        minLines = 3,
                    )

                    VertoOutlinedButton(
                        onClick = { imagePicker.launch(arrayOf("image/*")) },
                        enabled = state.images.size < InvoiceMaintenanceFormState.MaxImages,
                        shape = RoundedCornerShape(PaymentDimensions.dp10),
                        border = BorderStroke(PaymentDimensions.dp1, AccentPrimary.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.AddPhotoAlternate, null, tint = AccentLight, modifier = Modifier.size(PaymentDimensions.dp18))
                        Spacer(Modifier.size(PaymentDimensions.dp7))
                        Text(
                            androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_4224c509b704, state.images.size, InvoiceMaintenanceFormState.MaxImages),
                            color = AccentLight,
                            fontSize = PaymentTextScale.sp12,
                        )
                    }

                    state.images.forEach { image ->
                        MaintenanceImageRow(image = image, onRemove = { onImageRemoved(image.imageId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleSuggestionRow(
    vehicle: VehicleSuggestion,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = if (selected) AccentDim else BgSurface,
        shape = RoundedCornerShape(PaymentDimensions.dp9),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PaymentDimensions.dp11, vertical = PaymentDimensions.dp9),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    vehicle.displayLabel(),
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = PaymentTextScale.sp12,
                )
                if (vehicle.vehicleType.isNotBlank() && vehicle.vehicleType != vehicle.displayLabel()) {
                    Text(vehicle.vehicleType, color = TextMuted, fontSize = PaymentTextScale.sp10)
                }
            }
            if (vehicle.plateNumber.isNotBlank()) {
                Text(vehicle.plateNumber, color = AccentLight, fontSize = PaymentTextScale.sp11, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MaintenanceImageRow(
    image: CompanyMaintenanceImageData,
    onRemove: () -> Unit,
) {
    Surface(color = BgSurface, shape = RoundedCornerShape(PaymentDimensions.dp9)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = PaymentDimensions.dp10, vertical = PaymentDimensions.dp8),
            horizontalArrangement = Arrangement.spacedBy(PaymentDimensions.dp8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Image, null, tint = AccentPrimary, modifier = Modifier.size(PaymentDimensions.dp18))
            Text(
                text = Uri.parse(image.localUri).lastPathSegment ?: androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_v298_054d2bb4d679),
                color = TextSecondary,
                fontSize = PaymentTextScale.sp11,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.DeleteOutline,
                contentDescription = androidx.compose.ui.res.stringResource(com.verto.feature.payment.R.string.payment_ds_42186a14c45a),
                tint = TextMuted,
                modifier = Modifier.size(PaymentDimensions.dp19).clickable(onClick = onRemove),
            )
        }
    }
}

@Composable
private fun MaintenanceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    leadingIcon: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    VertoOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = TextMuted, fontSize = PaymentTextScale.sp12) },
        placeholder = { Text(placeholder, color = TextMuted, fontSize = PaymentTextScale.sp11) },
        leadingIcon = leadingIcon,
        singleLine = singleLine,
        minLines = minLines,
        shape = RoundedCornerShape(PaymentDimensions.dp10),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentPrimary,
            unfocusedBorderColor = BorderColor,
            focusedContainerColor = BgSurface,
            unfocusedContainerColor = BgSurface,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
