package com.verto.app.feature.party.presentation.addclient

import com.verto.app.feature.party.presentation.shared.PartyDimensions
import com.verto.app.feature.party.presentation.shared.PartyTextScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.party.application.ClientTypeCatalog
import com.verto.app.feature.party.domain.model.*
import com.verto.app.feature.party.application.model.*
import androidx.lifecycle.ViewModel
import com.verto.app.core.session.domain.SessionReader
import androidx.lifecycle.viewModelScope
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import com.verto.app.ui.components.*
import com.verto.app.ui.theme.*
import com.verto.app.utils.ErrorHumanizer
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.verto.app.ui.components.VertoIconButton
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditClientScreen(
    clientId: String?,
    isSupplier: Boolean = false,
    onBack: () -> Unit,
    vm: AddEditClientViewModel = hiltViewModel()
) {
    val form = remember { AddEditClientFormState() }
    val saved  by vm.saved.collectAsStateWithLifecycle()
    val error  by vm.error.collectAsStateWithLifecycle()
    val isEdit = clientId != null
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(clientId) {
        clientId?.let { id ->
            vm.loadClient(id) { client, supplierProfile, customerProfile ->
                form.load(client, isSupplier, supplierProfile, customerProfile)
            }
        }
    }
    LaunchedEffect(saved) { if (saved) onBack() }
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearError()
        }
    }
    Scaffold(
        containerColor = BgDeep,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            VertoTopBar(
                title = when {
                    isEdit && isSupplier -> androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_2d7e9a5b2b8b)
                    isEdit               -> androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_5674108a7757)
                    isSupplier           -> androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_777a9eca72b9)
                    else                 -> androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_608fde11871b)
                },
                onBack = onBack
            )
            Column(
                Modifier.padding(horizontal = PartyDimensions.dp20),
                verticalArrangement = Arrangement.spacedBy(PartyDimensions.dp18)
            ) {
                VertoTextField(
                    value = form.name, onValueChange = { form.name = it; form.nameError = false },
                    label = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_name),
                    placeholder = if (isSupplier) "" else androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_098be7325bd3),
                    isRequired = true, isError = form.nameError,
                    leadingIcon = { Icon(Icons.Filled.Person, null, tint = TextMuted) }
                )
                if (isSupplier) {
                    val supplierOptions = listOf(ClientType.SUPPLIER, ClientType.GLOBAL_SUPPLIER, ClientType.DISTRIBUTOR, ClientType.COMPETITOR)
                    ExposedDropdownMenuBox(
                        expanded = form.supplierTypeExpanded,
                        onExpandedChange = { form.supplierTypeExpanded = it }
                    ) {
                        VertoOutlinedTextField(
                            value = form.selectedSupplierType.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_category), color = TextSecondary, fontSize = PartyTextScale.sp13) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(form.supplierTypeExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = BorderColor,
                                focusedBorderColor   = AccentPrimary,
                                unfocusedTextColor   = TextPrimary,
                                focusedTextColor     = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = form.supplierTypeExpanded,
                            onDismissRequest = { form.supplierTypeExpanded = false }
                        ) {
                            supplierOptions.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.label, color = TextPrimary) },
                                    onClick = { form.selectedSupplierType = type; form.supplierTypeExpanded = false }
                                )
                            }
                        }
                    }
                    if (form.selectedSupplierType == ClientType.GLOBAL_SUPPLIER) {
                        VertoTextField(
                            value = form.country, onValueChange = { form.country = it },
                            label = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_country),
                            leadingIcon = { Icon(Icons.Filled.Public, null, tint = TextMuted) }
                        )
                        VertoTextField(
                            value = form.currency, onValueChange = { form.currency = it },
                            label = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_currency),
                            leadingIcon = { Icon(Icons.Filled.Payments, null, tint = TextMuted) }
                        )
                    }
                    VertoTextField(
                        value = form.primaryPhone, onValueChange = { form.primaryPhone = it },
                        label = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_phone_number),
                        keyboardType = KeyboardType.Phone,
                        leadingIcon = { Icon(Icons.Filled.Phone, null, tint = SuccessColor) }
                    )
                    VertoTextField(
                        value = form.address, onValueChange = { form.address = it },
                        label = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_ddb9dbdc6483),
                        leadingIcon = { Icon(Icons.Filled.LocationOn, null, tint = TextMuted) }
                    )
                    BankAccountsSection(form.bankAccounts) { form.bankAccounts = it }
                    VertoTextField(
                        value = form.workplaceField, onValueChange = { form.workplaceField = it },
                        label = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_5fdb177bdd36),
                        leadingIcon = { Icon(Icons.Filled.Business, null, tint = TextMuted) }
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = form.typeExpanded,
                        onExpandedChange = { form.typeExpanded = it }
                    ) {
                        VertoOutlinedTextField(
                            value = form.selectedType.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_category), color = TextSecondary, fontSize = PartyTextScale.sp13) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(form.typeExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = BorderColor,
                                focusedBorderColor   = AccentPrimary,
                                unfocusedTextColor   = TextPrimary,
                                focusedTextColor     = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = form.typeExpanded,
                            onDismissRequest = { form.typeExpanded = false }
                        ) {
                            ClientTypeCatalog.customerTypes.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.label, color = TextPrimary) },
                                    onClick = {
                                        if (type != form.selectedType) {
                                            form.selectedType   = type
                                            form.primaryPhone   = ""; form.address        = ""
                                            form.workplaceField = ""; form.specialtyField = ""
                                            form.carType        = ""; form.extraCars      = listOf()
                                            form.cars           = listOf(); form.carCountInput = ""
                                            form.workerCount    = ""; form.bankAccounts   = listOf()
                                        }
                                        form.typeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    when (form.selectedType) {
                        ClientType.INDIVIDUAL -> {
                            VertoTextField(
                                value = form.primaryPhone, onValueChange = { form.primaryPhone = it },
                                label = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_phone_number), placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_da39a3ee5e6b),
                                keyboardType = KeyboardType.Phone,
                                leadingIcon = { Icon(Icons.Filled.Phone, null, tint = SuccessColor) }
                            )
                            VertoTextField(
                                value = form.address, onValueChange = { form.address = it },
                                label = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_ddb9dbdc6483),
                                leadingIcon = { Icon(Icons.Filled.LocationOn, null, tint = TextMuted) }
                            )
                            VertoTextField(
                                value = form.workplaceField, onValueChange = { form.workplaceField = it },
                                label = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_60d37c38e2d8), placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_da39a3ee5e6b),
                                leadingIcon = { Icon(Icons.Filled.Work, null, tint = TextMuted) }
                            )
                            VertoTextField(
                                value = form.specialtyField, onValueChange = { form.specialtyField = it },
                                label = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_1d8ec12f58be), placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_b858cb282617),
                                keyboardType = KeyboardType.Number,
                                leadingIcon = { Icon(Icons.Filled.Person, null, tint = TextMuted) }
                            )
                            VertoTextField(
                                value = form.carType, onValueChange = { form.carType = it },
                                label = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_9f21bd2067a1), placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_da39a3ee5e6b),
                                leadingIcon = { Icon(Icons.Filled.DirectionsCar, null, tint = TextMuted) }
                            )
                            form.extraCars.forEachIndexed { index, car ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(PartyDimensions.dp8)
                                ) {
                                    VertoTextField(
                                        value = car,
                                        onValueChange = { v ->
                                            form.extraCars = form.extraCars.toMutableList().also { it[index] = v }
                                        },
                                        label = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_ce2a1c5ac6a2, index + 2),
                                        placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_4e2390f638f0),
                                        leadingIcon = { Icon(Icons.Filled.DirectionsCar, null, tint = TextMuted) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    VertoIconButton(
                                        onClick = {
                                            form.extraCars = form.extraCars.toMutableList().also { it.removeAt(index) }
                                        },
                                        modifier = Modifier.size(PartyDimensions.dp48)
                                    ) {
                                        Icon(Icons.Filled.RemoveCircleOutline, null,
                                            tint = ErrorColor, modifier = Modifier.size(PartyDimensions.dp20))
                                    }
                                }
                            }
                            TextButton(
                                onClick = { form.extraCars = form.extraCars + "" },
                                contentPadding = PaddingValues(horizontal = PartyDimensions.dp0, vertical = PartyDimensions.dp4)
                            ) {
                                Icon(Icons.Filled.AddCircleOutline, null,
                                    tint = AccentPrimary, modifier = Modifier.size(PartyDimensions.dp16))
                                Spacer(Modifier.width(PartyDimensions.dp4))
                                Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_feba07c05ccb), color = AccentPrimary, fontSize = PartyTextScale.sp13)
                            }
                        }
                        ClientType.COMPANY -> {
                            VertoTextField(
                                value = form.address, onValueChange = { form.address = it },
                                label = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_ddb9dbdc6483), placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_2e528f9cb3df),
                                leadingIcon = { Icon(Icons.Filled.LocationOn, null, tint = TextMuted) }
                            )
                            VertoTextField(
                                value = form.specialtyField, onValueChange = { form.specialtyField = it },
                                label = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_9f1e2bf55b66), placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_e19b16bdb71a),
                                leadingIcon = { Icon(Icons.Filled.Person, null, tint = TextMuted) }
                            )
                            VertoTextField(
                                value = form.primaryPhone, onValueChange = { form.primaryPhone = it },
                                label = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_phone_number), placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_da39a3ee5e6b),
                                keyboardType = KeyboardType.Phone,
                                leadingIcon = { Icon(Icons.Filled.Phone, null, tint = SuccessColor) }
                            )
                            VertoTextField(
                                value = form.workplaceField, onValueChange = { form.workplaceField = it },
                                label = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_5fdb177bdd36), placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_da39a3ee5e6b),
                                leadingIcon = { Icon(Icons.Filled.Business, null, tint = TextMuted) }
                            )
                            VertoTextField(
                                value = form.carCountInput,
                                onValueChange = { input ->
                                    form.carCountInput = input
                                    val count = input.toIntOrNull()?.coerceIn(0, 20) ?: 0
                                    form.cars = when {
                                        count > form.cars.size -> form.cars + List(count - form.cars.size) { "" }
                                        count < form.cars.size -> form.cars.take(count)
                                        else -> form.cars
                                    }
                                },
                                label = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_97edc79b4788), placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_da39a3ee5e6b),
                                keyboardType = KeyboardType.Number,
                                leadingIcon = { Icon(Icons.Filled.DirectionsCar, null, tint = TextMuted) }
                            )
                            form.cars.forEachIndexed { index, car ->
                                VertoTextField(
                                    value = car,
                                    onValueChange = { v ->
                                        form.cars = form.cars.toMutableList().also { it[index] = v }
                                    },
                                    label = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_28e9f3017715, index + 1),
                                    placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_da39a3ee5e6b),
                                    leadingIcon = { Icon(Icons.Filled.DirectionsCar, null, tint = TextMuted) }
                                )
                            }
                        }
                        ClientType.WORKSHOP_OWNER -> {
                            VertoTextField(
                                value = form.workplaceField, onValueChange = { form.workplaceField = it },
                                label = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_bd525c68ae0e), placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_bd525c68ae0e),
                                leadingIcon = { Icon(Icons.Filled.Build, null, tint = TextMuted) }
                            )
                            VertoTextField(
                                value = form.address, onValueChange = { form.address = it },
                                label = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_ddb9dbdc6483), placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_2e528f9cb3df),
                                leadingIcon = { Icon(Icons.Filled.LocationOn, null, tint = TextMuted) }
                            )
                            VertoTextField(
                                value = form.primaryPhone, onValueChange = { form.primaryPhone = it },
                                label = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_phone_number), placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_da39a3ee5e6b),
                                keyboardType = KeyboardType.Phone,
                                leadingIcon = { Icon(Icons.Filled.Phone, null, tint = SuccessColor) }
                            )
                            VertoTextField(
                                value = form.specialtyField, onValueChange = { form.specialtyField = it },
                                label = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_5fdb177bdd36), placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_da39a3ee5e6b),
                                leadingIcon = { Icon(Icons.Filled.Business, null, tint = TextMuted) }
                            )
                            VertoTextField(
                                value = form.workerCount, onValueChange = { form.workerCount = it },
                                label = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_d5ae5ad86a68), placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_da39a3ee5e6b),
                                keyboardType = KeyboardType.Number,
                                leadingIcon = { Icon(Icons.Filled.Group, null, tint = TextMuted) }
                            )
                            BankAccountsSection(form.bankAccounts) { form.bankAccounts = it }
                        }
                        ClientType.MARKETER -> {
                            VertoTextField(
                                value = form.primaryPhone, onValueChange = { form.primaryPhone = it },
                                label = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_phone_number),
                                keyboardType = KeyboardType.Phone,
                                leadingIcon = { Icon(Icons.Filled.Phone, null, tint = SuccessColor) }
                            )
                            VertoTextField(
                                value = form.address, onValueChange = { form.address = it },
                                label = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_ddb9dbdc6483),
                                leadingIcon = { Icon(Icons.Filled.LocationOn, null, tint = TextMuted) }
                            )
                            BankAccountsSection(form.bankAccounts) { form.bankAccounts = it }
                        }
                        ClientType.TRADER -> {
                            VertoTextField(
                                value = form.workplaceField, onValueChange = { form.workplaceField = it },
                                label = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_59539fc909e2), placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_dc59f41a9afb),
                                leadingIcon = { Icon(Icons.Filled.Store, null, tint = TextMuted) }
                            )
                            VertoTextField(
                                value = form.address, onValueChange = { form.address = it },
                                label = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_ddb9dbdc6483), placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_2e528f9cb3df),
                                leadingIcon = { Icon(Icons.Filled.LocationOn, null, tint = TextMuted) }
                            )
                            VertoTextField(
                                value = form.primaryPhone, onValueChange = { form.primaryPhone = it },
                                label = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_phone_number), placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_da39a3ee5e6b),
                                keyboardType = KeyboardType.Phone,
                                leadingIcon = { Icon(Icons.Filled.Phone, null, tint = SuccessColor) }
                            )
                            VertoTextField(
                                value = form.specialtyField, onValueChange = { form.specialtyField = it },
                                label = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_5fdb177bdd36), placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_d7f3b1601dcd),
                                leadingIcon = { Icon(Icons.Filled.Business, null, tint = TextMuted) }
                            )
                        }
                        ClientType.DISTRIBUTOR -> {
                            VertoTextField(
                                value = form.address, onValueChange = { form.address = it },
                                label = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_ddb9dbdc6483), placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_2e528f9cb3df),
                                leadingIcon = { Icon(Icons.Filled.LocationOn, null, tint = TextMuted) }
                            )
                            VertoTextField(
                                value = form.primaryPhone, onValueChange = { form.primaryPhone = it },
                                label = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_phone_number), placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_da39a3ee5e6b),
                                keyboardType = KeyboardType.Phone,
                                leadingIcon = { Icon(Icons.Filled.Phone, null, tint = SuccessColor) }
                            )
                            VertoTextField(
                                value = form.workplaceField, onValueChange = { form.workplaceField = it },
                                label = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_5fdb177bdd36), placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_c79fa29eab43),
                                leadingIcon = { Icon(Icons.Filled.Business, null, tint = TextMuted) }
                            )
                        }
                        ClientType.COMPETITOR -> {
                            VertoTextField(
                                value = form.workplaceField, onValueChange = { form.workplaceField = it },
                                label = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_59539fc909e2), placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_dc59f41a9afb),
                                leadingIcon = { Icon(Icons.Filled.Store, null, tint = TextMuted) }
                            )
                            VertoTextField(
                                value = form.primaryPhone, onValueChange = { form.primaryPhone = it },
                                label = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_phone_number), placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_da39a3ee5e6b),
                                keyboardType = KeyboardType.Phone,
                                leadingIcon = { Icon(Icons.Filled.Phone, null, tint = SuccessColor) }
                            )
                            VertoTextField(
                                value = form.address, onValueChange = { form.address = it },
                                label = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_ddb9dbdc6483), placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_2e528f9cb3df),
                                leadingIcon = { Icon(Icons.Filled.LocationOn, null, tint = TextMuted) }
                            )
                            VertoTextField(
                                value = form.specialtyField, onValueChange = { form.specialtyField = it },
                                label = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_5fdb177bdd36), placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_d7f3b1601dcd),
                                leadingIcon = { Icon(Icons.Filled.Business, null, tint = TextMuted) }
                            )
                        }
                        else -> {
                            VertoTextField(
                                value = form.primaryPhone, onValueChange = { form.primaryPhone = it },
                                label = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_phone_number), placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_da39a3ee5e6b),
                                keyboardType = KeyboardType.Phone,
                                leadingIcon = { Icon(Icons.Filled.Phone, null, tint = TextMuted) }
                            )
                            VertoTextField(
                                value = form.address, onValueChange = { form.address = it },
                                label = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_ddb9dbdc6483), placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_2e528f9cb3df),
                                leadingIcon = { Icon(Icons.Filled.LocationOn, null, tint = TextMuted) }
                            )
                        }
                    }
                }
                VertoTextField(
                    value = form.note, onValueChange = { form.note = it },
                    label = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_a762bd14a446), placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_da39a3ee5e6b),
                    singleLine = false,
                    leadingIcon = { Icon(Icons.Filled.Note, null, tint = TextMuted) }
                )

                Spacer(Modifier.height(PartyDimensions.dp8))

                VertoPrimaryButton(
                    text = if (isEdit) androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_efa26154d532_3) else if (isSupplier) androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_efa26154d532_2) else androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_efa26154d532),
                    onClick = {
                        form.nameError = form.name.isBlank()
                        if (!form.nameError) {
                            if (isSupplier) {
                                val supplierScope = when (form.selectedSupplierType) {
                                    ClientType.GLOBAL_SUPPLIER -> SupplierScope.INTERNATIONAL
                                    ClientType.COMPETITOR -> SupplierScope.UNKNOWN
                                    else -> SupplierScope.LOCAL
                                }
                                val supplierCustomerSegment = when (form.selectedSupplierType) {
                                    ClientType.COMPETITOR -> CustomerSegment.TRADER
                                    ClientType.DISTRIBUTOR -> CustomerSegment.DISTRIBUTOR
                                    else -> null
                                }
                                vm.saveClient(
                                    id              = clientId,
                                    name            = form.name,
                                    phone           = form.primaryPhone,
                                    address         = form.address,
                                    note            = form.note,
                                    bankAccount     = form.bankAccounts.filter { it.accountNumber.isNotBlank() }.toBankAccountsString(),
                                    supplierScope   = supplierScope,
                                    supplierCustomerSegment = supplierCustomerSegment,
                                    supplierWorkplace = form.workplaceField,
                                    supplierSpecialty = form.specialtyField,
                                    supplierCountry = if (form.selectedSupplierType == ClientType.GLOBAL_SUPPLIER) form.country else "",
                                    supplierCurrencyCode = if (form.selectedSupplierType == ClientType.GLOBAL_SUPPLIER) form.currency else "",
                                )
                            } else {
                                val segment = when (form.selectedType) {
                                    ClientType.INDIVIDUAL -> CustomerSegment.INDIVIDUAL
                                    ClientType.COMPANY -> CustomerSegment.COMPANY
                                    ClientType.WORKSHOP_OWNER -> CustomerSegment.WORKSHOP_OWNER
                                    ClientType.MARKETER -> CustomerSegment.MARKETER
                                    ClientType.TRADER -> CustomerSegment.TRADER
                                    ClientType.DISTRIBUTOR -> CustomerSegment.DISTRIBUTOR
                                    else -> error("Unsupported customer type: ${form.selectedType}")
                                }
                                val vehicleModels = when (form.selectedType) {
                                    ClientType.INDIVIDUAL -> (listOf(form.carType) + form.extraCars)
                                        .map(String::trim).filter(String::isNotBlank)
                                    ClientType.COMPANY ->
                                        form.cars.map(String::trim).filter(String::isNotBlank)
                                    else -> emptyList()
                                }
                                val profileDraft = CustomerProfileDraft(
                                    segment = segment,
                                    ageYears = form.specialtyField.trim().toIntOrNull()
                                        ?.takeIf { form.selectedType == ClientType.INDIVIDUAL && it in 1..120 },
                                    purchaseContactName = form.specialtyField.takeIf {
                                        form.selectedType == ClientType.COMPANY
                                    }.orEmpty(),
                                    businessActivity = form.specialtyField.takeIf {
                                        form.selectedType in setOf(ClientType.WORKSHOP_OWNER, ClientType.TRADER)
                                    }.orEmpty().ifBlank {
                                        form.workplaceField.takeIf { form.selectedType == ClientType.DISTRIBUTOR || form.selectedType == ClientType.COMPANY }.orEmpty()
                                    },
                                    workplaceName = form.workplaceField.takeIf { form.selectedType == ClientType.INDIVIDUAL }.orEmpty(),
                                    shopName = form.workplaceField.takeIf {
                                        form.selectedType == ClientType.TRADER
                                    }.orEmpty(),
                                    workshopName = form.workplaceField.takeIf { form.selectedType == ClientType.WORKSHOP_OWNER }.orEmpty(),
                                    vehicleModels = vehicleModels,
                                    workshopWorkerCount = form.workerCount.trim().toIntOrNull()
                                        ?.takeIf { form.selectedType == ClientType.WORKSHOP_OWNER && it >= 0 },
                                )
                                vm.saveClient(
                                    id          = clientId,
                                    name        = form.name,
                                    phone       = form.primaryPhone,
                                    address     = form.address,
                                    note        = form.note,
                                    bankAccount = form.bankAccounts.filter { it.accountNumber.isNotBlank() }.toBankAccountsString(),
                                    customerProfileDraft = profileDraft,
                                )
                            }
                        }
                    }
                )
                Spacer(Modifier.height(PartyDimensions.dp32))
            }
        }
    }
}
