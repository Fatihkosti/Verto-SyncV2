package com.verto.app.feature.organization.presentation.team

import com.verto.feature.organization.R

import com.verto.app.ui.components.VertoOutlinedButton
import com.verto.app.ui.components.VertoButton

import com.verto.app.feature.organization.presentation.OrganizationDimensions
import com.verto.app.feature.organization.presentation.OrganizationTextScale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import java.util.Calendar
import java.util.TimeZone
import com.verto.app.ui.theme.AccentBlue
import com.verto.app.ui.theme.AccentMain
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BgCardAlt
import com.verto.app.ui.theme.BgDeep
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.GoldPrimary
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.WarningColor
import com.verto.app.ui.theme.ErrorColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextOnAccent
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.ui.components.VertoCard
import com.verto.app.ui.theme.VertoSpacing

@Composable
internal fun EmployeeHeaderCard(profile: EmployeeDetailProfile) {
    CardContainer {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrganizationDimensions.dp14)
        ) {
            Box(
                modifier = Modifier
                    .size(OrganizationDimensions.dp54)
                    .clip(CircleShape)
                    .background(AccentPrimary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Person, null, tint = AccentPrimary, modifier = Modifier.size(OrganizationDimensions.dp28))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OrganizationDimensions.dp5)) {
                Text(profile.name, color = TextPrimary, fontSize = OrganizationTextScale.sp20, fontWeight = FontWeight.Bold)
                InfoLine(Icons.Filled.Work, profile.jobTitle)
                InfoLine(Icons.Filled.CalendarMonth, "تاريخ الانضمام: ${profile.joinedAt}")
                InfoLine(Icons.Filled.Schedule, profile.presence, tint = SuccessColor)
                InfoLine(Icons.Filled.TaskAlt, "آخر معاملة: ${profile.lastTransaction}")
            }
        }
    }
}

@Composable
internal fun PerformanceCard(
    performance: SalesPerformance,
    loading: Boolean,
    error: String?,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
    onPreset: (Pair<Long, Long>) -> Unit
) {
    DetailSectionCard(
        title = androidx.compose.ui.res.stringResource(R.string.ds_819198e224b2),
        icon = Icons.Filled.Paid,
        accent = AccentBlue,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(OrganizationDimensions.dp8)) {
                DateFilterPill("من", performance.fromDate, onClick = onPickFrom)
                DateFilterPill("إلى", performance.toDate, onClick = onPickTo)
            }
        }
    ) {
        PeriodPresetRow(onPreset = onPreset)
        Spacer(Modifier.height(OrganizationDimensions.dp12))
        when {
            loading -> {
                Text(androidx.compose.ui.res.stringResource(R.string.ds_da6da8025c9d), color = TextMuted, fontSize = OrganizationTextScale.sp12)
            }
            error != null -> {
                Text(androidx.compose.ui.res.stringResource(R.string.ds_db60c2237150, error), color = TextMuted, fontSize = OrganizationTextScale.sp12)
            }
            else -> {
                MetricGrid(
                    listOf(
                        "عدد الفواتير" to performance.invoiceCount.toString(),
                        "فواتير كاش" to performance.cashInvoices.toString(),
                        "فواتير آجل" to performance.creditInvoices.toString(),
                        "إجمالي المبيعات" to performance.totalSales,
                        "إجمالي الأرباح" to performance.totalProfit,
                        "الديون" to performance.debts,
                        "التحصيل" to performance.collections,
                        "عملاء مضافون" to performance.addedClients.toString()
                    )
                )
            }
        }
    }
}

@Composable
internal fun AttendanceCard(attendance: AttendanceSummary) {
    DetailSectionCard("الحضور", Icons.Filled.CalendarMonth, SuccessColor) {
        MetricGrid(
            listOf(
                "أيام الحضور" to attendance.presentDays.toString(),
                "أيام الغياب" to attendance.absentDays.toString(),
                "الإجازات" to attendance.leaveDays.toString(),
                "نسبة الالتزام" to "${attendance.commitmentRate}%"
            )
        )
    }
}

@Composable
internal fun PermissionsSummaryCard(
    summary: PermissionsSummary,
    onManagePermissions: () -> Unit
) {
    DetailSectionCard("الصلاحيات", Icons.Filled.Tune, AccentPrimary) {
        PermissionList("ماذا يرى", Icons.Filled.Visibility, SuccessColor, summary.visibleItems)
        Spacer(Modifier.height(OrganizationDimensions.dp10))
        PermissionList("ماذا لا يرى", Icons.Filled.VisibilityOff, ErrorColor, summary.hiddenItems)
        Spacer(Modifier.height(OrganizationDimensions.dp14))
        VertoButton(
            onClick = onManagePermissions,
            colors = ButtonDefaults.buttonColors(containerColor = AccentMain, contentColor = TextOnAccent),
            shape = RoundedCornerShape(OrganizationDimensions.dp10),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Lock, null, modifier = Modifier.size(OrganizationDimensions.dp17))
            Spacer(Modifier.width(OrganizationDimensions.dp8))
            Text(androidx.compose.ui.res.stringResource(R.string.ds_e64f9e1c97c4), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun CommunicationCard(
    templates: List<NotificationTemplate>,
    onTemplateClick: (NotificationTemplate) -> Unit
) {
    DetailSectionCard("التواصل", Icons.Filled.Notifications, WarningColor) {
        templates.forEach { template ->
            VertoOutlinedButton(
                onClick = { onTemplateClick(template) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(OrganizationDimensions.dp10),
                border = BorderStroke(OrganizationDimensions.dp1, BorderColor),
                contentPadding = PaddingValues(horizontal = OrganizationDimensions.dp12, vertical = OrganizationDimensions.dp10)
            ) {
                Icon(Icons.Filled.Send, null, tint = WarningColor, modifier = Modifier.size(OrganizationDimensions.dp16))
                Spacer(Modifier.width(OrganizationDimensions.dp8))
                Text(template.title, color = TextPrimary, fontSize = OrganizationTextScale.sp13, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(OrganizationDimensions.dp8))
        }
        Text(androidx.compose.ui.res.stringResource(R.string.ds_583750c2489a), color = TextMuted, fontSize = OrganizationTextScale.sp11)
    }
}

@Composable
internal fun GrowthAndTasksCard(growth: GrowthAndTasks) {
    DetailSectionCard("التطور والمهام", Icons.Filled.School, GoldPrimary) {
        GrowthGroup("كورسات منجزة", growth.completedCourses, Icons.Filled.CheckCircle, SuccessColor)
        GrowthGroup("كورسات حالية", growth.currentCourses, Icons.Filled.School, AccentPrimary)
        GrowthGroup("مهام منجزة", growth.completedTasks, Icons.Filled.CheckCircle, SuccessColor)
        GrowthGroup("مهام حالية", growth.currentTasks, Icons.Filled.TaskAlt, WarningColor)
    }
}

@Composable
internal fun DetailSectionCard(
    title: String,
    icon: ImageVector,
    accent: Color,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    CardContainer {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(OrganizationDimensions.dp20))
            Spacer(Modifier.width(OrganizationDimensions.dp8))
            Text(title, color = TextPrimary, fontSize = OrganizationTextScale.sp16, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        Spacer(Modifier.height(OrganizationDimensions.dp14))
        Column(content = content)
    }
}

@Composable
internal fun CardContainer(content: @Composable ColumnScope.() -> Unit) {
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OrganizationDimensions.dp14),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        elevation = CardDefaults.cardElevation(defaultElevation = OrganizationDimensions.dp0)
    ) {
        Column(
            modifier = Modifier.padding(OrganizationDimensions.dp16),
            content = content
        )
    }
}

@Composable
internal fun MetricGrid(metrics: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(OrganizationDimensions.dp8)) {
        metrics.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(OrganizationDimensions.dp8), modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { (label, value) ->
                    MetricTile(label = label, value = value, modifier = Modifier.weight(1f))
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(OrganizationDimensions.dp12))
            .background(BgCardAlt)
            .border(OrganizationDimensions.dp1, BorderColor, RoundedCornerShape(OrganizationDimensions.dp12))
            .padding(horizontal = OrganizationDimensions.dp10, vertical = OrganizationDimensions.dp10),
        verticalArrangement = Arrangement.spacedBy(OrganizationDimensions.dp4)
    ) {
        Text(label, color = TextSecondary, fontSize = OrganizationTextScale.sp11, maxLines = 2)
        Text(value, color = TextPrimary, fontSize = OrganizationTextScale.sp14, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun PermissionList(
    title: String,
    icon: ImageVector,
    color: Color,
    items: List<String>
) {
    Text(title, color = TextPrimary, fontSize = OrganizationTextScale.sp13, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(OrganizationDimensions.dp6))
    items.forEach { item ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = OrganizationDimensions.dp3),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(OrganizationDimensions.dp15))
            Spacer(Modifier.width(OrganizationDimensions.dp8))
            Text(item, color = TextSecondary, fontSize = OrganizationTextScale.sp12)
        }
    }
}

@Composable
internal fun GrowthGroup(title: String, items: List<String>, icon: ImageVector, color: Color) {
    Text(title, color = TextPrimary, fontSize = OrganizationTextScale.sp13, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(OrganizationDimensions.dp6))
    items.forEach { item ->
        Row(
            modifier = Modifier.padding(bottom = OrganizationDimensions.dp6),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(OrganizationDimensions.dp15))
            Spacer(Modifier.width(OrganizationDimensions.dp8))
            Text(item, color = TextSecondary, fontSize = OrganizationTextScale.sp12)
        }
    }
    Spacer(Modifier.height(OrganizationDimensions.dp6))
}

@Composable
internal fun DateFilterPill(label: String, value: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(OrganizationDimensions.dp9),
        color = BgCardAlt,
        border = BorderStroke(OrganizationDimensions.dp1, BorderColor),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = OrganizationDimensions.dp8, vertical = OrganizationDimensions.dp5),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = TextMuted, fontSize = OrganizationTextScale.sp10)
            Spacer(Modifier.width(OrganizationDimensions.dp4))
            Text(value, color = TextPrimary, fontSize = OrganizationTextScale.sp10, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(OrganizationDimensions.dp4))
            Icon(
                Icons.Filled.CalendarMonth, null,
                tint = AccentBlue, modifier = Modifier.size(OrganizationDimensions.dp12)
            )
        }
    }
}

/** أزرار فترات سريعة فوق مؤشرات الأداء. تحسب النطاق وتمرّره لـ setPeriod. */
@Composable
internal fun PeriodPresetRow(onPreset: (Pair<Long, Long>) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(OrganizationDimensions.dp8)) {
        PeriodPresetChip("هذا الشهر") { onPreset(presetCurrentMonth()) }
        PeriodPresetChip("الشهر الماضي") { onPreset(presetLastMonth()) }
        PeriodPresetChip("آخر 7 أيام") { onPreset(presetLastDays(7)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PeriodPresetChip(label: String, onClick: () -> Unit) {
    FilterChip(
        selected = false,
        onClick = onClick,
        label = { Text(label, fontSize = OrganizationTextScale.sp11) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = BgCardAlt,
            labelColor = TextSecondary
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = false,
            borderColor = BorderColor
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PeriodDatePickerDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val picked = state.selectedDateMillis
                if (picked != null) onConfirm(utcToLocal(picked)) else onDismiss()
            }) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_confirm), color = AccentMain) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextSecondary) }
        }
    ) {
        DatePicker(state = state)
    }
}

// ── حساب الفترات (نطاق محلي [بداية اليوم, نهاية اليوم] يُطبَّق في الـ ViewModel) ──

/** DatePicker يُرجع منتصف ليل UTC لليوم المختار — نحوّله لنفس اليوم محلياً. */
internal fun utcToLocal(utcMillis: Long): Long =
    utcMillis + TimeZone.getDefault().getOffset(utcMillis)

internal fun presetCurrentMonth(): Pair<Long, Long> {
    val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
    return cal.timeInMillis to System.currentTimeMillis()
}

internal fun presetLastMonth(): Pair<Long, Long> {
    val start = Calendar.getInstance().apply {
        add(Calendar.MONTH, -1)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val end = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.DAY_OF_MONTH, -1)
    }
    return start.timeInMillis to end.timeInMillis
}

internal fun presetLastDays(days: Int): Pair<Long, Long> {
    val start = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -(days - 1)) }
    return start.timeInMillis to System.currentTimeMillis()
}

@Composable
internal fun InfoLine(icon: ImageVector, text: String, tint: Color = TextMuted) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(OrganizationDimensions.dp15))
        Spacer(Modifier.width(OrganizationDimensions.dp6))
        Text(text, color = TextSecondary, fontSize = OrganizationTextScale.sp12)
    }
}

@Composable
internal fun NotificationPreviewDialog(
    template: NotificationTemplate,
    onDismiss: () -> Unit,
    onSend: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = { Text(template.title, color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                template.body,
                color = TextSecondary,
                fontSize = OrganizationTextScale.sp13,
                lineHeight = OrganizationTextScale.sp21
            )
        },
        confirmButton = {
            VertoButton(
                onClick = onSend,
                colors = ButtonDefaults.buttonColors(containerColor = AccentMain)
            ) {
                Icon(Icons.Filled.Send, null, modifier = Modifier.size(OrganizationDimensions.dp16))
                Spacer(Modifier.width(OrganizationDimensions.dp6))
                Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(androidx.compose.ui.res.stringResource(com.verto.core.designsystem.R.string.verto_action_cancel), color = TextSecondary)
            }
        }
    )
}
