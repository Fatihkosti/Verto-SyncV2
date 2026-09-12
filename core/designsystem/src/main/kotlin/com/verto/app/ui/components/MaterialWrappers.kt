package com.verto.app.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SheetState
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRow
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.verto.app.ui.theme.VertoSize

/** Material3 ownership wrappers used by Session 299 migrations. */

@Composable
fun VertoIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.sizeIn(minWidth = VertoSize.minTouchTarget, minHeight = VertoSize.minTouchTarget),
        enabled = enabled,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VertoTopAppBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors? = null,
) {
    if (colors == null) {
        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
        )
    } else {
        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = colors,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VertoBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = shape,
        containerColor = containerColor,
        dragHandle = dragHandle,
        content = content,
    )
}

@Composable
fun VertoTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    containerColor: Color,
    contentColor: Color,
    indicator: (@Composable (tabPositions: List<TabPosition>) -> Unit)? = null,
    tabs: @Composable () -> Unit,
) {
    if (indicator == null) {
        TabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = modifier,
            containerColor = containerColor,
            contentColor = contentColor,
            tabs = tabs,
        )
    } else {
        TabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = modifier,
            containerColor = containerColor,
            contentColor = contentColor,
            indicator = indicator,
            tabs = tabs,
        )
    }
}

@Composable
fun VertoScrollableTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    containerColor: Color,
    contentColor: Color,
    edgePadding: Dp,
    indicator: (@Composable (tabPositions: List<TabPosition>) -> Unit)? = null,
    tabs: @Composable () -> Unit,
) {
    if (indicator == null) {
        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = modifier,
            containerColor = containerColor,
            contentColor = contentColor,
            edgePadding = edgePadding,
            tabs = tabs,
        )
    } else {
        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = modifier,
            containerColor = containerColor,
            contentColor = contentColor,
            edgePadding = edgePadding,
            indicator = indicator,
            tabs = tabs,
        )
    }
}
