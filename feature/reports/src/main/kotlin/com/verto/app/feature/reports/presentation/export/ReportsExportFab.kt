package com.verto.app.feature.reports.presentation.export

import com.verto.app.feature.reports.presentation.ReportsDimensions

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.verto.app.ui.theme.*

@Composable
fun ReportsExportFab(
    onExport: (ExportOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    FloatingActionButton(
        onClick = { showDialog = true },
        modifier = modifier,
        containerColor = AccentPrimary,
        contentColor = TextOnAccent
    ) {
        Icon(
            imageVector = Icons.Default.Share,
            contentDescription = androidx.compose.ui.res.stringResource(com.verto.feature.reports.R.string.reports_ds_62d85520f9af),
            modifier = Modifier.size(ReportsDimensions.dp22)
        )
    }

    if (showDialog) {
        UnifiedExportDialog(
            onOptionSelected = onExport,
            onDismiss = { showDialog = false }
        )
    }
}
