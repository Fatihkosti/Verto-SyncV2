package com.verto.app.feature.integration.optimal.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.BuildCircle
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.verto.app.ui.components.VertoCard

@Composable
internal fun OptimalHomeSectionCard(
    section: OptimalHomeSection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    VertoCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(OptimalDimensions.dp46),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = section.icon.imageVector(),
                    contentDescription = null,
                    modifier = Modifier.size(OptimalDimensions.dp28),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(OptimalDimensions.dp12))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OptimalDimensions.dp3),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    section.badgeCount?.let { count ->
                        Spacer(Modifier.width(OptimalDimensions.dp8))
                        Badge {
                            Text(count.toString())
                        }
                    }
                }
                Text(
                    text = section.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            )
        }
    }
}

private fun OptimalHomeSectionIcon.imageVector(): ImageVector = when (this) {
    OptimalHomeSectionIcon.COMPANIES -> Icons.Filled.Apartment
    OptimalHomeSectionIcon.MESSAGES -> Icons.Filled.ChatBubble
    OptimalHomeSectionIcon.INVOICES -> Icons.Filled.ReceiptLong
    OptimalHomeSectionIcon.MAINTENANCE -> Icons.Filled.BuildCircle
    OptimalHomeSectionIcon.CODES -> Icons.Filled.Key
    OptimalHomeSectionIcon.SYNC_ISSUES -> Icons.Filled.SyncProblem
}
