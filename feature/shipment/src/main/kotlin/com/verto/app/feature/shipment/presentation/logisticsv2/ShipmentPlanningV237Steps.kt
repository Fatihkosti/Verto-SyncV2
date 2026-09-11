package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.ui.components.VertoOutlinedButton

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DirectionsBoat
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.verto.app.feature.shipment.domain.model.LogisticsLegTransportMode
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsRouteTransportPlanKind
import com.verto.app.ui.components.VertoFormCard
import com.verto.app.ui.components.VertoInlineStatus
import com.verto.app.ui.components.VertoStatusTone
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.app.ui.theme.VertoStroke
import kotlin.math.abs
import com.verto.app.ui.components.VertoIconButton

/** v237 §5.3 — trip type is explicit and separate from route construction. */
@Composable
internal fun ShipmentV237TripType(
    workspace: LogisticsRouteWorkspaceSnapshot,
    validationAttempt: Int,
    canManage: Boolean,
    onWorkspaceChange: ((LogisticsRouteWorkspaceSnapshot) -> LogisticsRouteWorkspaceSnapshot) -> Unit,
) {
    val validation = workspace.v237TripValidation()
    Column(verticalArrangement = Arrangement.spacedBy(VertoSpacing.md)) {
        Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_4c69f6c2650d), style = MaterialTheme.typography.headlineSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
        V237TripKindCard(
            title = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_6b34031537e4),
            description = "نوع نقل واحد لكل المسار",
            selected = workspace.tripTypeSelected && workspace.routeTransportPlanKind == LogisticsRouteTransportPlanKind.UNIFIED,
            enabled = canManage,
            onClick = { onWorkspaceChange { it.withV237TripKind(LogisticsRouteTransportPlanKind.UNIFIED) } },
        )
        V237TripKindCard(
            title = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_a25d61e7f2dc),
            description = "يمكن تغيير نوع النقل بين المحطات",
            selected = workspace.tripTypeSelected && workspace.routeTransportPlanKind == LogisticsRouteTransportPlanKind.MIXED,
            enabled = canManage,
            onClick = { onWorkspaceChange { it.withV237TripKind(LogisticsRouteTransportPlanKind.MIXED) } },
        )
        if (validationAttempt > 0 && validation.tripTypeError != null) {
            VertoInlineStatus(validation.tripTypeError, VertoStatusTone.Error)
        }
        if (workspace.tripTypeSelected && workspace.routeTransportPlanKind == LogisticsRouteTransportPlanKind.UNIFIED) {
            VertoFormCard {
                Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_6da975bbd210), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs),
                ) {
                    V237TransportChoice("بري", Icons.Outlined.LocalShipping, LogisticsLegTransportMode.ROAD, workspace.unifiedTransportMode, workspace.unifiedTransportModeSelected, canManage, Modifier.weight(1f)) {
                        onWorkspaceChange { it.withV237UnifiedMode(LogisticsLegTransportMode.ROAD) }
                    }
                    V237TransportChoice("بحري", Icons.Outlined.DirectionsBoat, LogisticsLegTransportMode.SEA, workspace.unifiedTransportMode, workspace.unifiedTransportModeSelected, canManage, Modifier.weight(1f)) {
                        onWorkspaceChange { it.withV237UnifiedMode(LogisticsLegTransportMode.SEA) }
                    }
                    V237TransportChoice("جوي", Icons.Outlined.Flight, LogisticsLegTransportMode.AIR, workspace.unifiedTransportMode, workspace.unifiedTransportModeSelected, canManage, Modifier.weight(1f)) {
                        onWorkspaceChange { it.withV237UnifiedMode(LogisticsLegTransportMode.AIR) }
                    }
                }
                if (validationAttempt > 0 && validation.unifiedModeError != null) {
                    Text(validation.unifiedModeError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else if (workspace.tripTypeSelected) {
            VertoInlineStatus(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_15280af43914), VertoStatusTone.Info)
        }
    }
}

@Composable
private fun V237TripKindCard(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                this.selected = selected
                stateDescription = if (selected) "محدد" else "غير محدد"
            }
            .clickable(enabled = enabled, onClick = onClick),
        color = BgCard,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(VertoStroke.thin, if (selected) AccentPrimary else BorderColor),
    ) {
        Row(
            modifier = Modifier.padding(VertoSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VertoSpacing.sm),
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(VertoSpacing.xxs)) {
                Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(description, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Outlined.Route, contentDescription = null, tint = if (selected) AccentPrimary else TextMuted)
        }
    }
}

@Composable
internal fun V237TransportChoice(
    label: String,
    icon: ImageVector,
    mode: LogisticsLegTransportMode,
    selectedMode: LogisticsLegTransportMode,
    selectionConfirmed: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val selected = selectionConfirmed && mode == selectedMode
    Surface(
        modifier = modifier
            .heightIn(min = LogisticsV2Tokens.transportChoiceMinHeight)
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                this.selected = selected
                stateDescription = if (selected) "محدد" else "غير محدد"
            }
            .clickable(enabled = enabled, onClick = onClick),
        color = BgCard,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(VertoStroke.thin, if (selected) AccentPrimary else BorderColor),
    ) {
        Column(
            modifier = Modifier.padding(VertoSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VertoSpacing.xs),
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) AccentPrimary else TextMuted, modifier = Modifier.size(LogisticsV2Tokens.transportChoiceIconSize))
            Text(label, style = MaterialTheme.typography.labelLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
        }
    }
}

/** v237 §5.4 — start/destination are immutable; only intermediate stations can be changed. */
@Composable
internal fun ShipmentV237RouteBuilder(
    draft: LogisticsPlanningDraft,
    workspace: LogisticsRouteWorkspaceSnapshot,
    canManage: Boolean,
    validationAttempt: Int,
    onWorkspaceChange: ((LogisticsRouteWorkspaceSnapshot) -> LogisticsRouteWorkspaceSnapshot) -> Unit,
    onEditStation: (String) -> Unit,
    onAddStation: () -> Unit,
    onCustomsRequested: () -> Unit,
) {
    val ordered = workspace.milestones.sortedBy { it.order }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(VertoSpacing.md)) {
        Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_6dbb0644cff4), style = MaterialTheme.typography.headlineSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
        VertoFormCard {
            ordered.forEachIndexed { index, milestone ->
                V237RouteRow(
                    milestone = milestone,
                    index = index,
                    lastIndex = ordered.lastIndex,
                    enabled = canManage,
                    onEdit = { onEditStation(milestone.id) },
                    onMoveUp = { onWorkspaceChange { it.withV237ReorderedIntermediate(draft, milestone.id, -1) } },
                    onMoveDown = { onWorkspaceChange { it.withV237ReorderedIntermediate(draft, milestone.id, 1) } },
                    onDelete = { pendingDeleteId = milestone.id },
                )
                if (index != ordered.lastIndex) {
                    Box(
                        Modifier.padding(start = LogisticsV2Tokens.routeConnectorStartInset).width(VertoStroke.progress).height(LogisticsV2Tokens.routeConnectorHeight).background(BorderColor),
                    )
                }
            }
            VertoOutlinedButton(
                onClick = onAddStation,
                enabled = canManage,
                modifier = Modifier.fillMaxWidth().heightIn(min = VertoSize.minTouchTarget),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(VertoSpacing.xs))
                Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_a833a0e172c8))
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().clickable(enabled = canManage, onClick = onCustomsRequested),
            color = BgCard,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(VertoStroke.thin, BorderColor),
        ) {
            Row(Modifier.padding(VertoSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_5b328473c1b6), modifier = Modifier.weight(1f), color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_next), color = AccentPrimary, style = MaterialTheme.typography.labelLarge)
            }
        }
        if (validationAttempt > 0) {
            workspace.v237UnconfirmedImmediateDuplicateId()?.let {
                VertoInlineStatus(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_01fc2358fa74), VertoStatusTone.Warning)
            }
            workspace.v237FirstIncompleteLeg()?.let {
                VertoInlineStatus(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_81505bdb02dc), VertoStatusTone.Error)
            }
        }
    }

    val deleteId = pendingDeleteId
    if (deleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_512f2bd5111f)) },
            text = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_5390fb830b4b)) },
            confirmButton = {
                TextButton(onClick = {
                    onWorkspaceChange { it.withV237RemovedIntermediate(draft, deleteId) }
                    pendingDeleteId = null
                }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_delete)) }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel)) } },
        )
    }
}

@Composable
private fun V237RouteRow(
    milestone: LogisticsMilestone,
    index: Int,
    lastIndex: Int,
    enabled: Boolean,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    val isIntermediate = milestone.type != LogisticsMilestoneType.ORIGIN && milestone.type != LogisticsMilestoneType.DESTINATION
    val dragThresholdPx = with(LocalDensity.current) { VertoSize.minTouchTarget.toPx() }
    var accumulatedDrag by remember(milestone.id) { mutableFloatStateOf(0f) }
    val label = when (milestone.type) {
        LogisticsMilestoneType.ORIGIN -> "نقطة البداية"
        LogisticsMilestoneType.DESTINATION -> "نقطة الوصول"
        else -> "محطة $index"
    }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = VertoSize.minTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VertoSpacing.xs),
    ) {
        Surface(shape = CircleShape, color = if (isIntermediate) BgCard else AccentPrimary, border = BorderStroke(VertoStroke.thin, AccentPrimary)) {
            Box(Modifier.size(LogisticsV2Tokens.routeNodeSize), contentAlignment = Alignment.Center) {
                Text((index + 1).toString(), color = if (isIntermediate) AccentPrimary else androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextMuted)
            Text(milestone.location.ifBlank { androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_v298_7764d85c4b04) }, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
        }
        if (isIntermediate) {
            Icon(
                Icons.Outlined.DragHandle,
                contentDescription = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_d3090c013c42),
                tint = TextMuted,
                modifier = Modifier
                    .size(VertoSize.minTouchTarget)
                    .pointerInput(milestone.id, index, lastIndex, enabled) {
                        if (!enabled) return@pointerInput
                        detectDragGesturesAfterLongPress(
                            onDragStart = { accumulatedDrag = 0f },
                            onDragCancel = { accumulatedDrag = 0f },
                            onDragEnd = { accumulatedDrag = 0f },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                accumulatedDrag += dragAmount.y
                                when {
                                    accumulatedDrag < -dragThresholdPx && index > 1 -> {
                                        onMoveUp()
                                        accumulatedDrag = 0f
                                    }
                                    accumulatedDrag > dragThresholdPx && index < lastIndex - 1 -> {
                                        onMoveDown()
                                        accumulatedDrag = 0f
                                    }
                                    abs(accumulatedDrag) > dragThresholdPx * 2 -> accumulatedDrag = 0f
                                }
                            },
                        )
                    },
            )
            VertoIconButton(onClick = onEdit, enabled = enabled) { Icon(Icons.Outlined.Edit, contentDescription = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_1288e07cf491)) }
            Box {
                var menu by remember { mutableStateOf(false) }
                VertoIconButton(onClick = { menu = true }, enabled = enabled) { Icon(Icons.Outlined.MoreVert, contentDescription = androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_650af3955cfb)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (index > 1) {
                        DropdownMenuItem(
                            text = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_8cf065da5561)) },
                            leadingIcon = { Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = null) },
                            onClick = { menu = false; onMoveUp() },
                        )
                    }
                    if (index < lastIndex - 1) {
                        DropdownMenuItem(
                            text = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_bc87057601c5)) },
                            leadingIcon = { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null) },
                            onClick = { menu = false; onMoveDown() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(androidx.compose.ui.res.stringResource(com.verto.app.feature.shipment.R.string.shipment_ds_0a260bafebc2)) },
                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}
