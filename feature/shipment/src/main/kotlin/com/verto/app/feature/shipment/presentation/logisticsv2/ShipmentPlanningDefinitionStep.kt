@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.verto.app.feature.shipment.presentation.logisticsv2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.verto.app.feature.shipment.R
import com.verto.app.feature.shipment.domain.model.LogisticsCountryNormalizer
import com.verto.app.ui.components.VertoCard
import com.verto.app.ui.components.VertoTextField
import com.verto.app.ui.theme.VertoSpacing

private data class DefinitionScrollTargets(
    val originCountry: BringIntoViewRequester,
    val originCity: BringIntoViewRequester,
    val destinationCountry: BringIntoViewRequester,
    val destinationCity: BringIntoViewRequester,
    val employee: BringIntoViewRequester,
)

private data class DefinitionFormUi(
    val state: LogisticsDraftUiState.Content,
    val validation: LogisticsShipmentDefinitionValidation,
    val showErrors: Boolean,
    val targets: DefinitionScrollTargets,
)

@Composable
internal fun ShipmentV230Basics(
    state: LogisticsDraftUiState.Content?,
    fallbackEmployees: List<LogisticsEmployeeOption>,
    viewModel: LogisticsV2ViewModel,
    validationAttempt: Int,
) {
    if (state == null) return LogisticsLoading()
    val validation = state.draft.validation
    val targets = remember {
        DefinitionScrollTargets(
            BringIntoViewRequester(), BringIntoViewRequester(), BringIntoViewRequester(),
            BringIntoViewRequester(), BringIntoViewRequester(),
        )
    }
    LaunchedEffect(validationAttempt) {
        if (validationAttempt <= 0) return@LaunchedEffect
        when {
            validation.shipmentNumberError != null -> Unit
            validation.originCountryError != null -> targets.originCountry.bringIntoView()
            validation.originCityError != null -> targets.originCity.bringIntoView()
            validation.destinationCountryError != null -> targets.destinationCountry.bringIntoView()
            validation.destinationCityError != null || validation.destinationMatchError != null -> targets.destinationCity.bringIntoView()
            validation.employeeError != null -> targets.employee.bringIntoView()
        }
    }
    ShipmentDefinitionCard(
        ui = DefinitionFormUi(state, validation, validationAttempt > 0, targets),
        employees = state.employees.ifEmpty { fallbackEmployees },
        viewModel = viewModel,
    )
}

@Composable
private fun ShipmentDefinitionCard(
    ui: DefinitionFormUi,
    employees: List<LogisticsEmployeeOption>,
    viewModel: LogisticsV2ViewModel,
) {
    val draft = ui.state.draft
    VertoCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(VertoSpacing.md)) {
            VertoTextField(
                value = "#${draft.shipmentNumber.removePrefix("#")}",
                onValueChange = {},
                label = stringResource(R.string.logistics_v230_shipment_number),
                readOnly = true,
                errorText = ui.validation.shipmentNumberError.takeIf { ui.showErrors },
                accessibilityDescription = "رقم الشحنة ${draft.shipmentNumber}",
            )
            ShipmentDefinitionLocationSection(ui, viewModel, destination = false)
            ShipmentDefinitionLocationSection(ui, viewModel, destination = true)
            if (employees.isEmpty()) {
                LogisticsEmpty(stringResource(R.string.logistics_v230_no_active_employee))
            } else {
                LogisticsEmployeePicker(
                    employees = employees,
                    selectedEmployeeId = draft.employeeId,
                    onSelect = { viewModel.updateDraftEmployee(it.id) },
                    isError = ui.showErrors && ui.validation.employeeError != null,
                    errorText = ui.validation.employeeError.takeIf { ui.showErrors },
                    modifier = Modifier.bringIntoViewRequester(ui.targets.employee),
                )
            }
            if (ui.state.saving) Text(stringResource(R.string.logistics_v230_saving_draft), style = MaterialTheme.typography.bodySmall)
            ui.state.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun ShipmentDefinitionLocationSection(
    ui: DefinitionFormUi,
    viewModel: LogisticsV2ViewModel,
    destination: Boolean,
) {
    val draft = ui.state.draft
    val focusManager = LocalFocusManager.current
    val countryValue = if (destination) draft.destinationCountryName else draft.originCountryName
    val cityValue = if (destination) draft.destinationCity else draft.originCity
    val countryError = if (destination) ui.validation.destinationCountryError else ui.validation.originCountryError
    val cityError = if (destination) ui.validation.destinationCityError ?: ui.validation.destinationMatchError else ui.validation.originCityError
    val countryTarget = if (destination) ui.targets.destinationCountry else ui.targets.originCountry
    val cityTarget = if (destination) ui.targets.destinationCity else ui.targets.originCity
    Text(
        stringResource(if (destination) R.string.logistics_v230_to else R.string.logistics_v230_from),
        style = MaterialTheme.typography.titleMedium,
    )
    LogisticsFreeCountryField(
        value = countryValue,
        suggestions = ui.state.countrySuggestions,
        onValueChange = { value ->
            if (destination) viewModel.updateDraftDestinationCountry(value) else viewModel.updateDraftOriginCountry(value)
        },
        errorText = countryError.takeIf { ui.showErrors },
        modifier = Modifier.bringIntoViewRequester(countryTarget),
    )
    VertoTextField(
        value = cityValue,
        onValueChange = { value ->
            if (destination) viewModel.updateDraftDestinationCity(value) else viewModel.updateDraftOriginCity(value)
        },
        label = stringResource(R.string.logistics_v230_city),
        placeholder = stringResource(R.string.logistics_v235_city_hint),
        errorText = cityError.takeIf { ui.showErrors },
        leadingIcon = { Icon(Icons.Outlined.LocationOn, contentDescription = null) },
        imeAction = if (destination) ImeAction.Done else ImeAction.Next,
        keyboardActions = if (destination) KeyboardActions(onDone = { focusManager.clearFocus() })
        else KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
        modifier = Modifier.bringIntoViewRequester(cityTarget),
    )
}

@Composable
private fun LogisticsFreeCountryField(
    value: String,
    suggestions: List<String>,
    onValueChange: (String) -> Unit,
    errorText: String?,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val query = LogisticsCountryNormalizer.searchKey(value)
    val matches = remember(query, suggestions) {
        if (query.isBlank()) emptyList() else suggestions.filter {
            LogisticsCountryNormalizer.searchKey(it).contains(query)
        }.take(5)
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
        VertoTextField(
            value = value,
            onValueChange = onValueChange,
            label = stringResource(R.string.logistics_v235_country_label),
            placeholder = stringResource(R.string.logistics_v235_country_hint),
            errorText = errorText,
            leadingIcon = { Icon(Icons.Outlined.Public, contentDescription = null) },
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
        )
        if (matches.isNotEmpty()) {
            VertoCard(modifier = Modifier.fillMaxWidth()) {
                matches.forEach { suggestion ->
                    androidx.compose.material3.TextButton(
                        onClick = { onValueChange(suggestion) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(suggestion, modifier = Modifier.fillMaxWidth().padding(vertical = VertoSpacing.xxs))
                    }
                }
            }
        }
    }
}

