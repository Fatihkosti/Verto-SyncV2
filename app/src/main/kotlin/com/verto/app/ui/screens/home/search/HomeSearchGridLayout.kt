package com.verto.app.ui.screens.home.search

import com.verto.feature.dashboard.api.HomeAction
import com.verto.feature.dashboard.api.HomeSearchResult

internal data class SearchGridPlacement(
    val column: Int,
    val row: Int,
    val columnSpan: Int,
    val rowSpan: Int,
)

/**
 * First-fit packing for a phone-only three-column search grid.
 * The selected result occupies a real 2x2 block; every other result occupies one cell.
 */
internal fun calculateSearchGridPlacements(
    itemCount: Int,
    expandedIndex: Int?,
    columnCount: Int = SEARCH_GRID_COLUMNS,
): List<SearchGridPlacement> {
    require(itemCount >= 0) { "itemCount must not be negative" }
    require(columnCount >= 2) { "columnCount must be at least two" }

    val validExpandedIndex = expandedIndex?.takeIf { it in 0 until itemCount }
    val occupiedRows = mutableListOf<BooleanArray>()

    fun ensureRows(requiredRows: Int) {
        while (occupiedRows.size < requiredRows) {
            occupiedRows += BooleanArray(columnCount)
        }
    }

    fun canPlace(row: Int, column: Int, rowSpan: Int, columnSpan: Int): Boolean {
        if (column < 0 || column + columnSpan > columnCount) return false
        ensureRows(row + rowSpan)
        for (candidateRow in row until row + rowSpan) {
            for (candidateColumn in column until column + columnSpan) {
                if (occupiedRows[candidateRow][candidateColumn]) return false
            }
        }
        return true
    }

    fun markOccupied(placement: SearchGridPlacement) {
        ensureRows(placement.row + placement.rowSpan)
        for (row in placement.row until placement.row + placement.rowSpan) {
            for (column in placement.column until placement.column + placement.columnSpan) {
                occupiedRows[row][column] = true
            }
        }
    }

    return buildList(itemCount) {
        repeat(itemCount) { index ->
            val span = if (index == validExpandedIndex) EXPANDED_SEARCH_GRID_SPAN else 1
            var row = 0
            var placement: SearchGridPlacement? = null
            while (placement == null) {
                for (column in 0..columnCount - span) {
                    if (canPlace(row, column, span, span)) {
                        placement = SearchGridPlacement(
                            column = column,
                            row = row,
                            columnSpan = span,
                            rowSpan = span,
                        )
                        break
                    }
                }
                if (placement == null) row += 1
            }
            markOccupied(placement)
            add(placement)
        }
    }
}

internal fun nextExpandedResultKey(
    currentKey: String?,
    selectedKey: String,
): String? = if (currentKey == selectedKey) null else selectedKey

internal const val SEARCH_GRID_COLUMNS: Int = 3
internal const val EXPANDED_SEARCH_GRID_SPAN: Int = 2

internal fun HomeSearchResult.expandedActions(openLabel: String): List<HomeAction> {
    val openAction = HomeAction(
        id = "open_result",
        label = openLabel,
        destination = destination,
        requiredPermission = requiredPermission,
    )
    return buildList {
        add(openAction)
        actions
            .asSequence()
            .filterNot { action -> action.destination == destination }
            .distinctBy { action -> action.id }
            .take(MAX_EXPANDED_ACTIONS - 1)
            .forEach(::add)
    }
}

internal const val MAX_EXPANDED_ACTIONS: Int = 3
