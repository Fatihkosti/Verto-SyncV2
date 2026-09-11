package com.verto.app.feature.shipment.presentation.logisticsv2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.verto.app.feature.shipment.domain.model.LogisticsPartner
import com.verto.app.feature.shipment.domain.model.LogisticsPartnerRole
import com.verto.app.ui.components.VertoSecondaryButton
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import java.util.Locale

internal data class LogisticsPickerOption(
    val id: String,
    val label: String,
)

internal data class LogisticsCountryOption(
    val code: String,
    val name: String,
)

internal fun logisticsCountryOptions(locale: Locale = Locale.getDefault()): List<LogisticsCountryOption> =
    Locale.getISOCountries()
        .mapNotNull { code ->
            val name = Locale("", code).getDisplayCountry(locale).trim()
            name.takeIf { it.isNotBlank() }?.let { LogisticsCountryOption(code = code, name = it) }
        }
        .sortedBy { it.name }

@Composable
internal fun LogisticsCountryPicker(
    countryCode: String,
    countryName: String,
    onSelect: (LogisticsCountryOption) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorText: String? = null,
) {
    val countries = remember { logisticsCountryOptions() }
    val selectedName = countries.firstOrNull { it.code.equals(countryCode, ignoreCase = true) }?.name
        ?: countryName.takeIf { it.isNotBlank() }
    LogisticsSearchPicker(
        label = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_country),
        selectedLabel = selectedName,
        options = countries.map { LogisticsPickerOption(it.code, it.name) },
        onSelect = { selected ->
            countries.firstOrNull { it.code == selected.id }?.let(onSelect)
        },
        modifier = modifier,
        isError = isError,
        errorText = errorText,
        emptyMessage = "لا توجد دولة مطابقة",
    )
}

@Composable
internal fun LogisticsEmployeePicker(
    employees: List<LogisticsEmployeeOption>,
    selectedEmployeeId: String,
    onSelect: (LogisticsEmployeeOption) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorText: String? = null,
) {
    LogisticsSearchPicker(
        label = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_90a321619232),
        selectedLabel = employees.firstOrNull { it.id == selectedEmployeeId }?.name,
        options = employees.map { LogisticsPickerOption(it.id, it.name) },
        onSelect = { selected -> employees.firstOrNull { it.id == selected.id }?.let(onSelect) },
        modifier = modifier,
        isError = isError,
        errorText = errorText,
        emptyMessage = "لا يوجد موظف مطابق",
    )
}

@Composable
internal fun LogisticsCarrierPicker(
    carriers: List<LogisticsPartner>,
    selectedPartnerId: String,
    onSelect: (LogisticsPartner) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorText: String? = null,
    allowedRoles: Set<LogisticsPartnerRole> = setOf(LogisticsPartnerRole.CARRIER, LogisticsPartnerRole.FREIGHT_FORWARDER),
    label: String = "الناقل / وسيط الشحن",
) {
    val eligible = remember(carriers, allowedRoles) {
        carriers.filter { it.role in allowedRoles }
    }
    LogisticsSearchPicker(
        label = label,
        selectedLabel = eligible.firstOrNull { it.id == selectedPartnerId }?.name,
        options = eligible.map { LogisticsPickerOption(it.id, it.name) },
        onSelect = { selected -> eligible.firstOrNull { it.id == selected.id }?.let(onSelect) },
        modifier = modifier,
        isError = isError,
        errorText = errorText,
        emptyMessage = "لا توجد جهة نقل مطابقة",
    )
}

@Composable
internal fun LogisticsSearchPicker(
    label: String,
    selectedLabel: String?,
    options: List<LogisticsPickerOption>,
    onSelect: (LogisticsPickerOption) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorText: String? = null,
    emptyMessage: String = "لا توجد نتيجة مطابقة",
) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, options) {
        val needle = query.trim()
        if (needle.isBlank()) options else options.filter { it.label.contains(needle, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(VertoSpacing.xxs)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        VertoSecondaryButton(
            text = selectedLabel?.takeIf { it.isNotBlank() } ?: androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_799fb5e38493, label),
            onClick = { open = true },
            modifier = modifier.fillMaxWidth(),
        )
        if (isError && !errorText.isNullOrBlank()) {
            Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false; query = "" },
            title = { Text(label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(VertoSpacing.sm)) {
                    LogisticsTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_search),
                        imeAction = ImeAction.Done,
                    )
                    if (filtered.isEmpty()) {
                        Text(emptyMessage)
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = LogisticsV2Tokens.pickerListMaxHeight)) {
                            items(filtered, key = { it.id }) { option ->
                                TextButton(
                                    onClick = {
                                        onSelect(option)
                                        open = false
                                        query = ""
                                    },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = VertoSize.minTouchTarget),
                                ) {
                                    Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false; query = "" }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_close)) }
            },
        )
    }
}
