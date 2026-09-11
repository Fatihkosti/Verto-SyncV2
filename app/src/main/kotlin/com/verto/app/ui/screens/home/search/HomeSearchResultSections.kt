package com.verto.app.ui.screens.home.search

import com.verto.app.ui.components.VertoOutlinedButton
import com.verto.app.ui.components.VertoButton

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.ui.screens.home.adaptiveSearchGridColumnCount
import com.verto.app.ui.theme.AccentBlue
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BgDeep
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.GoldPrimary
import com.verto.app.ui.theme.GradientStart
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.WarningColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.feature.dashboard.api.HomeAction
import com.verto.feature.dashboard.api.HomeSearchKind
import com.verto.feature.dashboard.api.HomeSearchResult

@Composable
internal fun SearchResultsGrid(
    results: List<HomeSearchResult>,
    expandedResultKey: String?,
    onResultToggle: (HomeSearchResult) -> Unit,
    onActionClick: (HomeSearchResult, HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val columnCount = adaptiveSearchGridColumnCount(
            availableWidthDp = maxWidth.value.toInt(),
            fontScale = LocalDensity.current.fontScale,
        )
        SearchResultsGridLayout(
            results = results,
            expandedResultKey = expandedResultKey,
            onResultToggle = onResultToggle,
            onActionClick = onActionClick,
            columnCount = columnCount,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun SearchResultsGridLayout(
    results: List<HomeSearchResult>,
    expandedResultKey: String?,
    onResultToggle: (HomeSearchResult) -> Unit,
    onActionClick: (HomeSearchResult, HomeAction) -> Unit,
    columnCount: Int,
    modifier: Modifier = Modifier,
) {
    val expandedIndex = remember(results, expandedResultKey) {
        results.indexOfFirst { result -> result.stableSearchKey() == expandedResultKey }
            .takeIf { index -> index >= 0 }
    }
    val cellSpacing = HomeSearchDimensions.dp8

    Layout(
        modifier = modifier,
        content = {
            results.forEachIndexed { index, result ->
                key(result.stableSearchKey()) {
                    SearchResultCard(
                        result = result,
                        expanded = index == expandedIndex,
                        onToggle = { onResultToggle(result) },
                        onActionClick = { action -> onActionClick(result, action) },
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val spacingPx = cellSpacing.roundToPx()
        val availableWidth = constraints.maxWidth
        val cellWidth = (
            (availableWidth - spacingPx * (columnCount - 1)) / columnCount
            ).coerceAtLeast(1)
        val placements = calculateSearchGridPlacements(
            itemCount = measurables.size,
            expandedIndex = expandedIndex,
            columnCount = columnCount,
        )
        val placeables = measurables.mapIndexed { index, measurable ->
            val placement = placements[index]
            val width = cellWidth * placement.columnSpan + spacingPx * (placement.columnSpan - 1)
            val height = cellWidth * placement.rowSpan + spacingPx * (placement.rowSpan - 1)
            measurable.measure(Constraints.fixed(width = width, height = height))
        }
        val rowCount = placements.maxOfOrNull { placement -> placement.row + placement.rowSpan } ?: 0
        val contentHeight = if (rowCount == 0) {
            0
        } else {
            rowCount * cellWidth + (rowCount - 1) * spacingPx
        }

        layout(
            width = availableWidth,
            height = constraints.constrainHeight(contentHeight),
        ) {
            placeables.forEachIndexed { index, placeable ->
                val placement = placements[index]
                placeable.placeRelative(
                    x = placement.column * (cellWidth + spacingPx),
                    y = placement.row * (cellWidth + spacingPx),
                )
            }
        }
    }
}

@Composable
internal fun SearchResultCard(
    result: HomeSearchResult,
    expanded: Boolean,
    onToggle: () -> Unit,
    onActionClick: (HomeAction) -> Unit,
) {
    val visual = result.kind.visual()
    val accessibility = buildString {
        append(visual.label)
        append("، ")
        append(result.title)
        result.subtitle?.takeIf(String::isNotBlank)?.let {
            append("، ")
            append(it)
        }
        append(if (expanded) "، بطاقة ممددة" else "، اضغط لعرض التفاصيل")
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .semantics(mergeDescendants = !expanded) {
                contentDescription = accessibility
                role = Role.Button
            }
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(HomeSearchDimensions.dp18),
        color = BgCard,
        border = BorderStroke(HomeSearchDimensions.dp1, visual.color.copy(alpha = 0.45f)),
        tonalElevation = if (expanded) HomeSearchDimensions.dp4 else HomeSearchDimensions.dp1,
    ) {
        if (expanded) {
            ExpandedSearchResultContent(
                result = result,
                visual = visual,
                onActionClick = onActionClick,
            )
        } else {
            CompactSearchResultContent(result = result, visual = visual)
        }
    }
}

@Composable
internal fun CompactSearchResultContent(
    result: HomeSearchResult,
    visual: SearchKindVisual,
) {
    Column(
        modifier = Modifier.padding(HomeSearchDimensions.dp10),
        verticalArrangement = Arrangement.spacedBy(HomeSearchDimensions.dp6),
    ) {
        ResultTypeHeader(visual = visual)
        Text(
            text = result.title,
            color = TextPrimary,
            fontSize = HomeSearchTextScale.sp13,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        result.subtitle?.takeIf(String::isNotBlank)?.let { subtitle ->
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = HomeSearchTextScale.sp10,
                lineHeight = HomeSearchTextScale.sp14,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun ExpandedSearchResultContent(
    result: HomeSearchResult,
    visual: SearchKindVisual,
    onActionClick: (HomeAction) -> Unit,
) {
    val openLabel = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_open)
    val actions = remember(result, openLabel) { result.expandedActions(openLabel) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(HomeSearchDimensions.dp12),
        verticalArrangement = Arrangement.spacedBy(HomeSearchDimensions.dp8),
    ) {
        ResultTypeHeader(visual = visual)
        Text(
            text = result.title,
            color = TextPrimary,
            fontSize = HomeSearchTextScale.sp17,
            lineHeight = HomeSearchTextScale.sp21,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        result.subtitle?.takeIf(String::isNotBlank)?.let { subtitle ->
            Text(
                text = subtitle,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(HomeSearchDimensions.dp6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEachIndexed { index, action ->
                if (index == 0) {
                    VertoButton(
                        onClick = { onActionClick(action) },
                        modifier = Modifier.weight(1f),
                    ) {
                        ActionLabel(action.label)
                    }
                } else {
                    VertoOutlinedButton(
                        onClick = { onActionClick(action) },
                        modifier = Modifier.weight(1f),
                    ) {
                        ActionLabel(action.label)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ResultTypeHeader(visual: SearchKindVisual) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = visual.color.copy(alpha = 0.16f),
        ) {
            Icon(
                imageVector = visual.icon,
                contentDescription = null,
                tint = visual.color,
                modifier = Modifier
                    .padding(HomeSearchDimensions.dp6)
                    .size(HomeSearchDimensions.dp16),
            )
        }
        Text(
            text = visual.label,
            color = visual.color,
            fontSize = HomeSearchTextScale.sp10,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun ActionLabel(label: String) {
    Text(
        text = label,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        fontSize = HomeSearchTextScale.sp11,
    )
}

@Composable
internal fun SearchMessage(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.padding(HomeSearchDimensions.dp24), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HomeSearchDimensions.dp10),
        ) {
            Icon(icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(HomeSearchDimensions.dp44))
            Text(
                text = title,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

internal data class SearchKindVisual(
    val label: String,
    val icon: ImageVector,
    val color: Color,
)

@Composable
internal fun HomeSearchKind.visual(): SearchKindVisual = when (this) {
    HomeSearchKind.PARTY -> SearchKindVisual("عميل/مورد", Icons.Filled.People, AccentPrimary)
    HomeSearchKind.INVOICE -> SearchKindVisual("فاتورة", Icons.Filled.Description, SuccessColor)
    HomeSearchKind.INVENTORY_ITEM -> SearchKindVisual("صنف", Icons.Filled.Inventory2, WarningColor)
    HomeSearchKind.PAYMENT -> SearchKindVisual("دفعة", Icons.Filled.AccountBalanceWallet, GoldPrimary)
    HomeSearchKind.SCREEN -> SearchKindVisual("شاشة", Icons.Filled.ManageSearch, AccentBlue)
    HomeSearchKind.ACTION -> SearchKindVisual("إجراء", Icons.Filled.TouchApp, GradientStart)
    HomeSearchKind.OTHER -> SearchKindVisual("نتيجة", Icons.Filled.Search, TextMuted)
}
