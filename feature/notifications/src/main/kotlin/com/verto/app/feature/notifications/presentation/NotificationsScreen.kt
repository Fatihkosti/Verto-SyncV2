package com.verto.app.feature.notifications.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.verto.feature.notifications.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.feature.notifications.domain.model.AppNotification
import com.verto.app.feature.notifications.domain.model.AppNotificationAudience
import com.verto.app.ui.components.SettingsCard
import com.verto.app.ui.components.SettingsDivider
import com.verto.app.ui.components.SettingsSectionHeader
import com.verto.app.ui.theme.*
import com.verto.app.utils.DateUtils
import com.verto.app.ui.components.VertoIconButton
import com.verto.app.ui.components.VertoTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit = {},
    onNotificationClick: (AppNotification) -> Unit = {},
    notificationsVm: NotificationCenterViewModel = hiltViewModel()
) {
    val state by notificationsVm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionRefresh by remember { mutableIntStateOf(0) }
    val notificationsPermissionGranted = remember(permissionRefresh) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionRefresh += 1
    }

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            VertoTopAppBar(
                title = { Text(androidx.compose.ui.res.stringResource(R.string.ds_0eb7129edb51), color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = { VertoIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = NotificationsDimensions.dp16, vertical = NotificationsDimensions.dp8),
            verticalArrangement = Arrangement.spacedBy(NotificationsDimensions.dp8)
        ) {
            if (!notificationsPermissionGranted) {
                SettingsCard {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = NotificationsDimensions.dp16, vertical = NotificationsDimensions.dp12),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "فعّل إشعارات الهاتف لاستلام التنبيهات خارج Verto",
                            color = TextSecondary,
                            fontSize = NotificationsTextScale.sp11,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                            Text("تفعيل", color = AccentPrimary, fontSize = NotificationsTextScale.sp12)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SettingsSectionHeader("آخر الإشعارات")
                if (state.notifications.any { !it.isRead }) {
                    TextButton(onClick = { notificationsVm.onEvent(NotificationCenterEvent.MarkAllAsRead) }) {
                        Text(androidx.compose.ui.res.stringResource(R.string.ds_9e8667304b3d), color = AccentPrimary, fontSize = NotificationsTextScale.sp12)
                    }
                }
            }
            SettingsCard(modifier = Modifier.weight(1f)) {
                when {
                    state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(NotificationsDimensions.dp22), color = AccentPrimary, strokeWidth = NotificationsDimensions.dp2)
                    }
                    state.error != null && state.notifications.isEmpty() -> Text(state.error.orEmpty(), color = ErrorColor, fontSize = NotificationsTextScale.sp12, modifier = Modifier.padding(NotificationsDimensions.dp16))
                    state.notifications.isEmpty() -> Text(androidx.compose.ui.res.stringResource(R.string.ds_00b8a255285f), color = TextMuted, fontSize = NotificationsTextScale.sp12, modifier = Modifier.padding(NotificationsDimensions.dp16))
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(state.notifications, key = { _, item -> item.id }) { index, notification ->
                            NotificationRow(notification) {
                                notificationsVm.onEvent(NotificationCenterEvent.MarkAsRead(notification.id))
                                onNotificationClick(notification)
                            }
                            if (index < state.notifications.lastIndex) SettingsDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(notification: AppNotification, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = NotificationsDimensions.dp16, vertical = NotificationsDimensions.dp12)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Icon(
                when (notification.audience) {
                    AppNotificationAudience.DIRECT_EMPLOYEE -> Icons.Filled.Person
                    AppNotificationAudience.ALL_EMPLOYEES -> Icons.Filled.Groups
                    AppNotificationAudience.MANAGER_ONLY -> Icons.Filled.AdminPanelSettings
                }, null, tint = if (notification.isRead) TextMuted else AccentPrimary, modifier = Modifier.size(NotificationsDimensions.dp20)
            )
            Spacer(Modifier.width(NotificationsDimensions.dp12))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NotificationsDimensions.dp3)) {
                Text(notification.title, color = TextPrimary, fontSize = NotificationsTextScale.sp13, fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.Bold)
                Text(notification.body, color = TextSecondary, fontSize = NotificationsTextScale.sp11, lineHeight = NotificationsTextScale.sp15)
                Text(DateUtils.formatTimeAgo(notification.createdAt), color = TextMuted, fontSize = NotificationsTextScale.sp10)
            }
        }
    }
}
