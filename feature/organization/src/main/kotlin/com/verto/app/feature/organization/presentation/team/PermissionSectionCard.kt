package com.verto.app.feature.organization.presentation.team

import androidx.compose.ui.res.stringResource

import com.verto.app.feature.organization.presentation.OrganizationDimensions
import com.verto.app.feature.organization.presentation.OrganizationTextScale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.verto.app.feature.organization.domain.model.EmployeePermissions
import com.verto.app.ui.theme.AccentMain
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BgDeep
import com.verto.app.ui.theme.WarningColor
import com.verto.app.ui.theme.TextOnAccent
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.ui.components.VertoCard
import androidx.compose.foundation.layout.PaddingValues
import com.verto.app.ui.theme.VertoSpacing
import com.verto.feature.organization.R

/**
 * بطاقة قسم الصلاحيات المشتركة بين شاشة الصلاحيات وشاشة الدعوة.
 *
 * المفتاح الرئيسي ثلاثي الدلالة:
 *  - كل مفاتيح القسم مفعّلة  → Track أخضر (AccentMain)
 *  - بعضها مفعّل             → Track برتقالي (WarningColor) للإشارة للحالة الوسيطة
 *  - لا شيء مفعّل            → Track رمادي (BgDeep)
 */
@Composable
internal fun PermissionSectionCard(
    section: PermissionSection,
    permissions: EmployeePermissions,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onPermissionsChange: (EmployeePermissions) -> Unit,
    enabled: Boolean = true
) {
    val subValues   = section.subPermissions.map { it.getValue(permissions) }
    val allEnabled  = subValues.all { it }
    val noneEnabled = subValues.none { it }
    val isPartial   = !allEnabled && !noneEnabled

    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(OrganizationDimensions.dp14),
        colors    = CardDefaults.cardColors(containerColor = BgCard),
        elevation = CardDefaults.cardElevation(defaultElevation = OrganizationDimensions.dp0)
    ) {
        Column {
            // ── رأس القسم ──────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled, onClick = onToggleExpand)
                    .padding(horizontal = OrganizationDimensions.dp16, vertical = OrganizationDimensions.dp14),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = allEnabled,
                    enabled = enabled,
                    onCheckedChange = { turnOn ->
                        if (enabled) {
                            var updated = permissions
                            section.subPermissions.forEach { sub ->
                                updated = sub.setValue(updated, turnOn)
                            }
                            onPermissionsChange(updated)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor   = TextOnAccent,
                        checkedTrackColor   = AccentMain,
                        uncheckedThumbColor = if (isPartial) WarningColor else TextSecondary,
                        uncheckedTrackColor = if (isPartial) WarningColor.copy(alpha = 0.35f) else BgDeep
                    ),
                    modifier = Modifier.switchScale(0.85f)
                )

                Spacer(Modifier.width(OrganizationDimensions.dp10))

                Text(
                    section.label,
                    color      = TextPrimary,
                    fontSize   = OrganizationTextScale.sp15,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.weight(1f)
                )

                // عدّاد الممنوح/الكل
                Text(
                    stringResource(R.string.legacy_ui_fd5b50ab3eca, subValues.count { it }, subValues.size),
                    color    = if (isPartial) WarningColor else TextSecondary,
                    fontSize = OrganizationTextScale.sp12
                )

                Spacer(Modifier.width(OrganizationDimensions.dp8))

                Icon(
                    if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint     = TextSecondary,
                    modifier = Modifier.size(OrganizationDimensions.dp20)
                )
            }

            // ── المفاتيح الفرعية ───────────────────────────────
            AnimatedVisibility(
                visible = isExpanded,
                enter   = expandVertically() + fadeIn(),
                exit    = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgDeep.copy(alpha = 0.4f))
                ) {
                    HorizontalDivider(color = BgDeep, thickness = OrganizationDimensions.dp1)
                    section.subPermissions.forEach { sub ->
                        val checked = sub.getValue(permissions)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = enabled) {
                                    if (enabled) onPermissionsChange(sub.setValue(permissions, !checked))
                                }
                                .padding(start = OrganizationDimensions.dp48, end = OrganizationDimensions.dp16, top = OrganizationDimensions.dp10, bottom = OrganizationDimensions.dp10),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                sub.label,
                                color    = if (checked) TextPrimary else TextSecondary,
                                fontSize = OrganizationTextScale.sp13,
                                modifier = Modifier.weight(1f)
                            )
                            Checkbox(
                                checked  = checked,
                                enabled  = enabled,
                                onCheckedChange = { v ->
                                    if (enabled) onPermissionsChange(sub.setValue(permissions, v))
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor   = AccentMain,
                                    checkmarkColor = TextOnAccent,
                                    uncheckedColor = TextSecondary
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

/** تحجيم Switch — بديل عن Modifier.scale لتجنب استيراد خارجي. */
private fun Modifier.switchScale(scale: Float): Modifier =
    this.size(width = (52 * scale).dp, height = (32 * scale).dp)
