package com.verto.app.ui.screens.home.search

import com.verto.app.R

import com.verto.app.ui.components.VertoOutlinedTextField
import com.verto.app.ui.components.VertoUserError

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
import com.verto.app.ui.components.VertoIconButton

@Composable
internal fun SearchHeader(
    state: HomeSearchUiState,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onFilterSelect: (HomeSearchFilter) -> Unit,
    onBack: () -> Unit,
    onSearchIme: () -> Unit,
) {
    Surface(color = BgDeep, shadowElevation = HomeSearchDimensions.dp3) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HomeSearchDimensions.dp16, vertical = HomeSearchDimensions.dp10),
            verticalArrangement = Arrangement.spacedBy(HomeSearchDimensions.dp10),
        ) {
            SearchHeaderTitle(onBack = onBack)
            SearchQueryField(
                query = state.query,
                focusRequester = focusRequester,
                onQueryChange = onQueryChange,
                onClearQuery = onClearQuery,
                onSearchIme = onSearchIme,
            )
            SearchFilterRow(
                selectedFilter = state.selectedFilter,
                onFilterSelect = onFilterSelect,
            )
            HorizontalDivider(color = BorderColor)
        }
    }
}

@Composable
private fun SearchHeaderTitle(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VertoIconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = androidx.compose.ui.res.stringResource(R.string.ds_dbf3d91b7eaa),
                tint = TextPrimary,
            )
        }
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.ds_257f4de5065e),
            color = TextPrimary,
            fontWeight = FontWeight.Black,
            fontSize = HomeSearchTextScale.sp20,
        )
    }
}

@Composable
private fun SearchQueryField(
    query: String,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSearchIme: () -> Unit,
) {
    val queryFieldDescription = androidx.compose.ui.res.stringResource(R.string.ds_5e01369fe16b)
    VertoOutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .semantics { contentDescription = queryFieldDescription },
        placeholder = { Text(androidx.compose.ui.res.stringResource(R.string.ds_e7beff0aaa34), color = TextMuted) },
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null, tint = AccentPrimary)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                VertoIconButton(onClick = onClearQuery) {
                    Icon(Icons.Filled.Close, contentDescription = androidx.compose.ui.res.stringResource(R.string.ds_2d8ed6101d6f), tint = TextMuted)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(HomeSearchDimensions.dp18),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearchIme() }),
    )
}

@Composable
private fun SearchFilterRow(
    selectedFilter: HomeSearchFilter,
    onFilterSelect: (HomeSearchFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(HomeSearchDimensions.dp8),
    ) {
        HomeSearchFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selectedFilter,
                onClick = { onFilterSelect(filter) },
                label = { Text(filter.label) },
            )
        }
    }
}

@Composable
internal fun SearchBody(
    state: HomeSearchUiState,
    onResultToggle: (HomeSearchResult) -> Unit,
    onResultAction: (HomeSearchResult, HomeAction) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.error != null -> VertoUserError(
            error = state.error,
            onRetry = onRetry,
            modifier = modifier,
        )

        state.isWaitingForQuery -> SearchMessage(
            icon = Icons.Filled.ManageSearch,
            title = androidx.compose.ui.res.stringResource(R.string.ds_1d45abcb326a),
            message = androidx.compose.ui.res.stringResource(R.string.ds_46af496dcc8c),
            modifier = modifier,
        )

        state.needsMoreCharacters -> SearchMessage(
            icon = Icons.Filled.Search,
            title = androidx.compose.ui.res.stringResource(R.string.ds_1357e64f068e),
            message = androidx.compose.ui.res.stringResource(R.string.ds_78f2c1b26622),
            modifier = modifier,
        )

        state.showEmptyState -> SearchMessage(
            icon = Icons.Filled.ManageSearch,
            title = androidx.compose.ui.res.stringResource(R.string.ds_02c3f5c4f837),
            message = androidx.compose.ui.res.stringResource(R.string.ds_c26d24cef5a2),
            modifier = modifier,
        )

        else -> Box(modifier = modifier) {
            SearchResultsGrid(
                results = state.results,
                expandedResultKey = state.expandedResultKey,
                onResultToggle = onResultToggle,
                onActionClick = onResultAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = HomeSearchDimensions.dp12, vertical = HomeSearchDimensions.dp12),
            )
        }
    }
}
