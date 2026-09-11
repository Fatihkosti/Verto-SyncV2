package com.verto.app.feature.shipment.presentation.logisticsv2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.verto.app.feature.shipment.domain.model.LogisticsLegTransportMode
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsRouteTemplate
import com.verto.app.feature.shipment.domain.model.LogisticsRouteTransportPlanKind
import com.verto.app.ui.components.VertoPrimaryButton
import com.verto.app.ui.components.VertoSecondaryButton
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.feature.shipment.R

@Composable
internal fun ShipmentV230Plan(
    draft: LogisticsPlanningDraft,
    workspace: LogisticsRouteWorkspaceSnapshot,
    routeTemplates: List<LogisticsRouteTemplate>,
    canManage: Boolean,
    onWorkspaceChange: ((LogisticsRouteWorkspaceSnapshot) -> LogisticsRouteWorkspaceSnapshot) -> Unit,
    onHeaderLocationsChange: (String, String) -> Unit,
    onSaveTemplate: (LogisticsRouteTemplate) -> Unit,
) {
    var newStation by remember(draft.shipmentId) { mutableStateOf("") }
    val orderedMilestones = workspace.milestones.sortedBy { it.order }

    AccordionHeader(
        title = stringResource(R.string.logistics_v230_route_stations),
        subtitle = if (orderedMilestones.size >= 2) stringResource(R.string.logistics_v230_stations_summary, orderedMilestones.size) else stringResource(R.string.logistics_v230_incomplete),
        expanded = workspace.expandedSection == "ROUTE",
        onClick = { onWorkspaceChange { it.copy(expandedSection = "ROUTE") } },
    )
    if (workspace.expandedSection == "ROUTE") {
        LogisticsSection(stringResource(R.string.logistics_v230_shipping_type)) {
            Row(horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs), modifier = Modifier.fillMaxWidth()) {
                LogisticsRouteTransportPlanKind.entries.forEach { option ->
                    FilterChip(
                        selected = workspace.routeTransportPlanKind == option,
                        onClick = {
                            onWorkspaceChange { current ->
                                val mode = if (option == LogisticsRouteTransportPlanKind.UNIFIED) current.unifiedTransportMode
                                else LogisticsLegTransportMode.UNSPECIFIED
                                current.copy(
                                    routeTransportPlanKind = option,
                                    legs = current.legs.map { leg -> leg.asV230PlanningLeg(mode) },
                                )
                            }
                        },
                        label = { Text(if (option == LogisticsRouteTransportPlanKind.UNIFIED) stringResource(R.string.logistics_v230_unified) else stringResource(R.string.logistics_v230_mixed)) },
                    )
                }
            }
            if (workspace.routeTransportPlanKind == LogisticsRouteTransportPlanKind.UNIFIED) {
                Row(horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs), modifier = Modifier.fillMaxWidth()) {
                    listOf(LogisticsLegTransportMode.ROAD, LogisticsLegTransportMode.SEA, LogisticsLegTransportMode.AIR).forEach { mode ->
                        FilterChip(
                            selected = workspace.unifiedTransportMode == mode,
                            onClick = {
                                onWorkspaceChange { current ->
                                    current.copy(
                                        unifiedTransportMode = mode,
                                        legs = current.legs.map { it.asV230PlanningLeg(mode) },
                                    )
                                }
                            },
                            label = { Text(v230TransportModeLabel(mode)) },
                        )
                    }
                }
            }
        }

        LogisticsSection(stringResource(R.string.logistics_v230_route)) {
            LogisticsLabeledValue(stringResource(R.string.logistics_v230_departure), draft.sourceLocation.toLogisticsPlanningPlace().city)
            orderedMilestones.drop(1).dropLast(1).forEachIndexed { index, milestone ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs)) {
                    LogisticsLabeledValue(stringResource(R.string.logistics_v230_station_number, index + 1), milestone.location, modifier = Modifier.weight(1f))
                    VertoSecondaryButton(
                        text = stringResource(R.string.logistics_v230_remove),
                        onClick = { onWorkspaceChange { current -> current.withoutIntermediateMilestone(draft, milestone.id) } },
                    )
                }
            }
            LogisticsLabeledValue(stringResource(R.string.logistics_v230_arrival), draft.destinationLocation.toLogisticsPlanningPlace().city)
            LogisticsTextField(
                value = newStation,
                onValueChange = { newStation = it },
                label = stringResource(R.string.logistics_v230_add_station),
                imeAction = ImeAction.Done,
            )
            VertoSecondaryButton(
                text = stringResource(R.string.logistics_v230_add_station_action),
                onClick = {
                    val name = newStation.trim()
                    if (name.isNotBlank()) {
                        onWorkspaceChange { current -> current.withIntermediateMilestone(draft, name) }
                        newStation = ""
                    }
                },
                enabled = newStation.isNotBlank(),
            )
        }

        LogisticsSection(stringResource(R.string.logistics_v230_expected_durations)) {
            workspace.legs.sortedBy { it.sequence }.forEach { leg ->
                val from = orderedMilestones.getOrNull(leg.sequence)?.location.orEmpty().toLogisticsPlanningPlace().city
                val to = orderedMilestones.getOrNull(leg.sequence + 1)?.location.orEmpty().toLogisticsPlanningPlace().city
                LogisticsTextField(
                    value = leg.expectedTransitDays?.toString().orEmpty(),
                    onValueChange = { raw ->
                        val days = raw.filter(Char::isDigit).toIntOrNull()
                        onWorkspaceChange { current ->
                            current.copy(legs = current.legs.map { if (it.id == leg.id) it.copy(expectedTransitDays = days) else it })
                        }
                    },
                    label = stringResource(R.string.logistics_v230_leg_days, from, to),
                    keyboardType = KeyboardType.Number,
                )
            }
        }

        LogisticsSection(stringResource(R.string.logistics_v230_customs)) {
            val customs = orderedMilestones.singleOrNull { it.type == LogisticsMilestoneType.CUSTOMS }
            val intermediates = orderedMilestones.drop(1).dropLast(1)
            LogisticsSearchPicker(
                label = stringResource(R.string.logistics_v230_where_customs),
                selectedLabel = customs?.location ?: stringResource(R.string.logistics_v230_no_customs),
                options = listOf(LogisticsPickerOption("NONE", stringResource(R.string.logistics_v230_no_customs))) +
                    intermediates.map { LogisticsPickerOption(it.id, it.location) },
                onSelect = { selected ->
                    onWorkspaceChange { current -> current.withCustomsMilestone(selected.id.takeUnless { it == "NONE" }) }
                },
            )
            customs?.let { selected ->
                LogisticsTextField(
                    value = selected.expectedStayDays?.toString().orEmpty(),
                    onValueChange = { raw ->
                        val days = raw.filter(Char::isDigit).toIntOrNull()
                        onWorkspaceChange { current ->
                            current.copy(milestones = current.milestones.map { if (it.id == selected.id) it.copy(expectedStayDays = days) else it })
                        }
                    },
                    label = stringResource(R.string.logistics_v230_customs_days),
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                )
            }
        }

        LogisticsSection(stringResource(R.string.logistics_v230_route_templates)) {
            LogisticsSearchPicker(
                label = stringResource(R.string.logistics_v230_use_template),
                selectedLabel = null,
                options = routeTemplates.map { LogisticsPickerOption(it.id, it.name) },
                onSelect = { selected ->
                    routeTemplates.firstOrNull { it.id == selected.id }?.let { template ->
                        onHeaderLocationsChange(
                            logisticsPlanningPlace(template.originCountryCode.toPlanningCountryName(), template.originCity),
                            logisticsPlanningPlace(template.destinationCountryCode.toPlanningCountryName(), template.destinationCity),
                        )
                        onWorkspaceChange { current -> current.applyTemplate(draft, template) }
                    }
                },
                emptyMessage = stringResource(R.string.logistics_v230_no_templates),
            )
            LogisticsTextField(
                value = workspace.templateName,
                onValueChange = { value -> onWorkspaceChange { it.copy(templateName = value) } },
                label = stringResource(R.string.logistics_v230_template_name),
                imeAction = ImeAction.Done,
            )
            VertoPrimaryButton(
                text = stringResource(R.string.logistics_v230_save_template),
                onClick = {
                    onSaveTemplate(workspace.toRouteTemplate(draft))
                },
                enabled = canManage && workspace.templateName.isNotBlank() && draft.isV230RouteReady(workspace),
            )
        }
    }
}

@Composable
private fun AccordionHeader(title: String, subtitle: String, expanded: Boolean, onClick: () -> Unit) {
    LogisticsSection(title, trailing = if (expanded) stringResource(R.string.logistics_v230_opened) else subtitle) {
        VertoSecondaryButton(text = if (expanded) stringResource(R.string.logistics_v230_hide) else stringResource(R.string.logistics_v230_open), onClick = onClick)
    }
}

@Composable
internal fun ShipmentV230Review(
    shipmentNumber: String,
    draft: LogisticsPlanningDraft,
    workspace: LogisticsRouteWorkspaceSnapshot,
    onEditBasics: () -> Unit,
    onEditPlan: () -> Unit,
) {
    LogisticsSection(stringResource(R.string.logistics_v230_definition)) {
        LogisticsLabeledValue(stringResource(R.string.logistics_v230_shipment_number), shipmentNumber)
        LogisticsLabeledValue(
            stringResource(R.string.logistics_v230_from_to),
            "${draft.sourceLocation.toLogisticsPlanningPlace().city} → ${draft.destinationLocation.toLogisticsPlanningPlace().city}",
        )
        LogisticsLabeledValue(stringResource(R.string.logistics_v230_employee_short), draft.assigneeName)
        VertoSecondaryButton(stringResource(R.string.logistics_v230_edit_definition), onEditBasics)
    }
    LogisticsSection(stringResource(R.string.logistics_v230_sources)) {
        draft.sources.groupBy { it.supplierId }.values.forEach { sources ->
            LogisticsLabeledValue(
                sources.first().supplierNameSnapshot,
                sources.joinToString("، ") { "#${it.invoiceNumberSnapshot}" },
            )
        }
    }
    LogisticsSection(stringResource(R.string.logistics_v230_route)) {
        val ordered = workspace.milestones.sortedBy { it.order }
        LogisticsLabeledValue(
            stringResource(R.string.logistics_v230_route_stops),
            ordered.joinToString(" → ") { it.location.toLogisticsPlanningPlace().city },
        )
        LogisticsLabeledValue(
            stringResource(R.string.logistics_v230_shipping_type),
            if (workspace.routeTransportPlanKind == LogisticsRouteTransportPlanKind.MIXED) stringResource(R.string.logistics_v230_mixed)
            else stringResource(R.string.logistics_v230_shipping_type_unified, v230TransportModeLabel(workspace.unifiedTransportMode)),
        )
        LogisticsLabeledValue(
            stringResource(R.string.logistics_v230_customs),
            ordered.singleOrNull { it.type == LogisticsMilestoneType.CUSTOMS }?.location?.toLogisticsPlanningPlace()?.city
                ?: stringResource(R.string.logistics_v230_no_customs),
        )
        workspace.legs.sortedBy { it.sequence }.forEach { leg ->
            val from = ordered.getOrNull(leg.sequence)?.location.orEmpty().toLogisticsPlanningPlace().city
            val to = ordered.getOrNull(leg.sequence + 1)?.location.orEmpty().toLogisticsPlanningPlace().city
            LogisticsLabeledValue("$from → $to", stringResource(R.string.logistics_v230_leg_duration_days, leg.expectedTransitDays ?: 0))
        }
        ordered.singleOrNull { it.type == LogisticsMilestoneType.CUSTOMS }?.expectedStayDays?.let {
            LogisticsLabeledValue(stringResource(R.string.logistics_v230_customs_duration), stringResource(R.string.logistics_v230_leg_duration_days, it))
        }
        VertoSecondaryButton(stringResource(R.string.logistics_v230_edit_plan), onEditPlan)
    }
}


@Composable
private fun v230TransportModeLabel(mode: LogisticsLegTransportMode): String = when (mode) {
    LogisticsLegTransportMode.ROAD -> stringResource(R.string.logistics_v230_road)
    LogisticsLegTransportMode.SEA -> stringResource(R.string.logistics_v230_sea)
    LogisticsLegTransportMode.AIR -> stringResource(R.string.logistics_v230_air)
    LogisticsLegTransportMode.UNSPECIFIED -> stringResource(R.string.logistics_v230_at_execution)
}
