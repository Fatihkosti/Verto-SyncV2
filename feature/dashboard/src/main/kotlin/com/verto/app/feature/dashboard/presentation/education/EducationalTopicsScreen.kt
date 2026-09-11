package com.verto.app.feature.dashboard.presentation.education

import androidx.compose.ui.res.stringResource

import com.verto.feature.dashboard.R

import com.verto.app.ui.components.VertoOutlinedTextField
import com.verto.app.feature.dashboard.presentation.DashboardDimensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.feature.dashboard.api.EducationalAudienceOption
import com.verto.feature.dashboard.api.EducationalContent
import com.verto.feature.dashboard.api.EducationalContentCategory
import com.verto.feature.dashboard.api.EducationalTargetType
import com.verto.app.ui.components.VertoCard
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoTopAppBar
import androidx.compose.foundation.layout.PaddingValues
import com.verto.app.ui.theme.VertoSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EducationalTopicsRoute(
    onBack: () -> Unit,
) {
    val vm: EducationalTopicsViewModel = hiltViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.legacy_ui_f72ef6676c0e)
    val deletedMessage = stringResource(R.string.legacy_ui_5f981ab92ee2)
    val deleteErrorMessage = stringResource(R.string.legacy_ui_915fec531227)
    val activeChangeErrorMessage = stringResource(R.string.legacy_ui_8e424ec9da81)

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHost.showSnackbar(it)
            vm.clearMessage()
        }
    }

    state.editor?.let { editor ->
        EducationalTopicEditorDialog(
            editor = editor,
            roles = state.roles,
            users = state.users,
            onDismiss = vm::closeEditor,
            onTitleChange = vm::updateTitle,
            onSummaryChange = vm::updateSummary,
            onContentChange = vm::updateFullContent,
            onCategoryChange = vm::updateCategory,
            onActiveChange = vm::updateActive,
            onEveryoneChange = vm::updateEveryone,
            onToggleRole = vm::toggleRole,
            onToggleUser = vm::toggleUser,
            onSave = { vm.save(savedMessage) },
        )
    }

    if (state.deletingTopicId != null) {
        AlertDialog(
            onDismissRequest = vm::cancelDelete,
            title = { Text(androidx.compose.ui.res.stringResource(R.string.ds_c492f97ccfeb)) },
            text = { Text(androidx.compose.ui.res.stringResource(R.string.ds_ab2d471ac6ae)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.confirmDelete(
                            deletedMessage = deletedMessage,
                            deleteErrorMessage = deleteErrorMessage,
                        )
                    },
                ) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = vm::cancelDelete) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel)) }
            },
        )
    }

    Scaffold(
        topBar = {
            VertoTopAppBar(
                title = { Text(androidx.compose.ui.res.stringResource(R.string.ds_5591e7e4991d), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    VertoIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_navigate_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            if (state.isAdmin) {
                FloatingActionButton(
                    onClick = vm::startCreate,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = androidx.compose.ui.res.stringResource(R.string.ds_21aaad4505ef))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        when {
            !state.isAdmin -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(DashboardDimensions.dp24),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(androidx.compose.ui.res.stringResource(R.string.ds_11f680bb0eb5))
            }

            state.topics.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(DashboardDimensions.dp24),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(androidx.compose.ui.res.stringResource(R.string.ds_9628f1d90ccd))
                Spacer(Modifier.height(DashboardDimensions.dp8))
                Text(
                    androidx.compose.ui.res.stringResource(R.string.ds_d2fa0db645b9),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(DashboardDimensions.dp16),
                verticalArrangement = Arrangement.spacedBy(DashboardDimensions.dp12),
            ) {
                items(state.topics, key = EducationalContent::id) { topic ->
                    EducationalTopicCard(
                        topic = topic,
                        onEdit = { vm.startEdit(topic) },
                        onDelete = { vm.requestDelete(topic.id) },
                        onActiveChange = { active ->
                            vm.setActive(topic, active, activeChangeErrorMessage)
                        },
                    )
                }
                item { Spacer(Modifier.height(DashboardDimensions.dp72)) }
            }
        }
    }
}

@Composable
private fun EducationalTopicCard(
    topic: EducationalContent,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onActiveChange: (Boolean) -> Unit,
) {
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(DashboardDimensions.dp16),
            verticalArrangement = Arrangement.spacedBy(DashboardDimensions.dp8),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        topic.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (topic.isActive) stringResource(R.string.legacy_ui_906952c584a3) else stringResource(R.string.legacy_ui_53d71ebbfa2a),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (topic.isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = topic.isActive, onCheckedChange = onActiveChange)
            }
            Text(
                topic.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                audienceSummary(topic),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                VertoIconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_edit))
                }
                VertoIconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_delete))
                }
            }
        }
    }
}

private fun audienceSummary(topic: EducationalContent): String {
    if (topic.targets.any { it.type == EducationalTargetType.ALL }) return "الجمهور: الجميع"
    val roles = topic.targets.count { it.type == EducationalTargetType.ROLE }
    val users = topic.targets.count { it.type == EducationalTargetType.USER }
    return buildList {
        if (roles > 0) add("$roles دور")
        if (users > 0) add("$users موظف")
    }.joinToString("، ").let { "الجمهور: $it" }
}

@Composable
private fun EducationalTopicEditorDialog(
    editor: EducationalTopicEditorState,
    roles: List<EducationalAudienceOption>,
    users: List<EducationalAudienceOption>,
    onDismiss: () -> Unit,
    onTitleChange: (String) -> Unit,
    onSummaryChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onCategoryChange: (EducationalContentCategory) -> Unit,
    onActiveChange: (Boolean) -> Unit,
    onEveryoneChange: (Boolean) -> Unit,
    onToggleRole: (String) -> Unit,
    onToggleUser: (String) -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!editor.isSaving) onDismiss() },
        title = { Text(if (editor.topicId == null) stringResource(R.string.legacy_ui_fabcb7d23962) else stringResource(R.string.legacy_ui_aa97c6d8ed4d)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(DashboardDimensions.dp12),
            ) {
                VertoOutlinedTextField(
                    value = editor.title,
                    onValueChange = onTitleChange,
                    label = { Text(androidx.compose.ui.res.stringResource(R.string.ds_baffa49c77ea)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                )
                VertoOutlinedTextField(
                    value = editor.summary,
                    onValueChange = onSummaryChange,
                    label = { Text(androidx.compose.ui.res.stringResource(R.string.ds_b8db01ca3908)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                )
                VertoOutlinedTextField(
                    value = editor.fullContent,
                    onValueChange = onContentChange,
                    label = { Text(androidx.compose.ui.res.stringResource(R.string.ds_f64ad45c4ee4)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    maxLines = 10,
                )
                Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_category), fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(DashboardDimensions.dp6)) {
                    EducationalContentCategory.entries.chunked(2).forEach { rowCategories ->
                        Row(horizontalArrangement = Arrangement.spacedBy(DashboardDimensions.dp8)) {
                            rowCategories.forEach { category ->
                                FilterChip(
                                    selected = editor.category == category,
                                    onClick = { onCategoryChange(category) },
                                    label = { Text(category.label()) },
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(androidx.compose.ui.res.stringResource(R.string.ds_1b388b1d5c9d), modifier = Modifier.weight(1f))
                    Switch(checked = editor.isActive, onCheckedChange = onActiveChange)
                }
                HorizontalDivider()
                Text(androidx.compose.ui.res.stringResource(R.string.ds_31e5b64d196a), fontWeight = FontWeight.SemiBold)
                AudienceCheckbox(
                    label = androidx.compose.ui.res.stringResource(R.string.ds_a87fc68492c1),
                    checked = editor.everyone,
                    onCheckedChange = onEveryoneChange,
                )
                if (roles.isNotEmpty()) {
                    Text(androidx.compose.ui.res.stringResource(R.string.ds_459613725333), style = MaterialTheme.typography.labelLarge)
                    roles.forEach { option ->
                        AudienceCheckbox(
                            label = option.label,
                            checked = option.id in editor.roleIds,
                            onCheckedChange = { onToggleRole(option.id) },
                        )
                    }
                }
                if (users.isNotEmpty()) {
                    Text(androidx.compose.ui.res.stringResource(R.string.ds_55f61c6aea62), style = MaterialTheme.typography.labelLarge)
                    users.forEach { option ->
                        AudienceCheckbox(
                            label = option.label,
                            checked = option.id in editor.userIds,
                            onCheckedChange = { onToggleUser(option.id) },
                        )
                    }
                }
                editor.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = onSave, enabled = !editor.isSaving) {
                Text(if (editor.isSaving) stringResource(R.string.legacy_ui_3bbe16c24462) else stringResource(R.string.legacy_ui_a8da1ce7ba20))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !editor.isSaving) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel)) }
        },
    )
}

@Composable
private fun AudienceCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}

private fun EducationalContentCategory.label(): String = when (this) {
    EducationalContentCategory.INVENTORY -> "المخزون"
    EducationalContentCategory.SALES -> "المبيعات"
    EducationalContentCategory.PAYMENTS -> "المدفوعات"
    EducationalContentCategory.CUSTOMERS -> "العملاء"
    EducationalContentCategory.OPERATIONS -> "العمليات"
}
