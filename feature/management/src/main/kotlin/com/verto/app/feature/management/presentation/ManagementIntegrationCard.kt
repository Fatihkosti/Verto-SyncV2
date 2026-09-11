package com.verto.app.feature.management.presentation

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BuildCircle
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.verto.app.feature.management.domain.model.ManagementIntegration
import com.verto.app.feature.management.domain.model.ManagementIntegrationBadge
import com.verto.app.feature.management.domain.model.ManagementIntegrationBadgeTone
import com.verto.app.feature.management.domain.model.ManagementIntegrationIcon
import com.verto.app.ui.components.VertoCard
import androidx.compose.foundation.layout.PaddingValues
import com.verto.app.ui.theme.VertoSpacing
import com.verto.feature.management.R

@Composable
fun ManagementIntegrationCard(
    integration: ManagementIntegration,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badges: List<ManagementIntegrationBadge> = emptyList(),
) {
    VertoCard(contentPadding = PaddingValues(VertoSpacing.none), 
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(ManagementDimensions.dp18),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = ManagementDimensions.dp2),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = ManagementDimensions.dp18, vertical = ManagementDimensions.dp20),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(ManagementDimensions.dp48),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = integration.icon.imageVector(),
                    contentDescription = null,
                    modifier = Modifier.size(ManagementDimensions.dp30),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(ManagementDimensions.dp14))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ManagementDimensions.dp6),
            ) {
                Text(
                    text = integration.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = integration.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
                val visibleBadges = badges.filter { it.count > 0 }
                if (visibleBadges.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ManagementDimensions.dp8),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        visibleBadges.forEach { badge ->
                            ManagementBadge(badge)
                        }
                    }
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun ManagementBadge(badge: ManagementIntegrationBadge) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ManagementDimensions.dp4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = badge.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
        )
        Badge(
            containerColor = when (badge.tone) {
                ManagementIntegrationBadgeTone.STANDARD -> MaterialTheme.colorScheme.primary
                ManagementIntegrationBadgeTone.ERROR -> MaterialTheme.colorScheme.error
            },
        ) {
            Text(if (badge.count > 99) stringResource(R.string.legacy_ui_eace596bb652) else badge.count.toString())
        }
    }
}

private fun ManagementIntegrationIcon.imageVector(): ImageVector = when (this) {
    ManagementIntegrationIcon.BENZINE -> Icons.Filled.LocalGasStation
    ManagementIntegrationIcon.OPTIMAL -> Icons.Filled.BuildCircle
    ManagementIntegrationIcon.GENERIC -> Icons.Filled.Widgets
}
