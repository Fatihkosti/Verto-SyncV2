package com.verto.app.feature.party.presentation.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp

/** Party-owned contact action primitive shared by client/supplier surfaces. */
@Composable
internal fun PartyContactActionButton(
    icon: ImageVector,
    color: Color,
    size: Dp = PartyDimensions.dp40,
    cornerRadius: Dp = PartyDimensions.dp10,
    iconSize: Dp = PartyDimensions.dp20,
    backgroundAlpha: Float = 0.15f,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(PartyDimensions.dp48)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(cornerRadius))
                .background(color.copy(backgroundAlpha)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(iconSize))
        }
    }
}
