package com.verto.app.feature.invoice.presentation.invoice

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import com.verto.app.feature.invoice.presentation.InvoiceDimensions
import com.verto.app.feature.invoice.presentation.InvoiceTextScale
import com.verto.app.ui.theme.InfoColor
import com.verto.app.ui.theme.InfoContainer
import com.verto.app.ui.theme.ErrorColor
import com.verto.app.ui.theme.ErrorContainer
import com.verto.app.ui.theme.WarningColor
import com.verto.app.ui.theme.WarningContainer
import com.verto.app.ui.theme.VertoMotion
import com.verto.app.utils.DateUtils

/** Invoice-owned copy of the pre-migration app badge; behavior and geometry are unchanged. */
@Composable
internal fun TimerBadge(dueDate: Long, modifier: Modifier = Modifier) {
    val (label, isOverdue, isUrgent) = DateUtils.dueDateLabel(dueDate)

    val bgColor = when {
        isOverdue -> ErrorContainer
        isUrgent -> WarningContainer
        else -> InfoContainer
    }
    val txtColor = when {
        isOverdue -> ErrorColor
        isUrgent -> WarningColor
        else -> InfoColor
    }
    val icon = when {
        isOverdue -> "🔴"
        isUrgent -> "🟠"
        else -> "🔵"
    }

    val alpha by if (isOverdue) {
        val transition = rememberInfiniteTransition(label = androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_8439fc486bfe))
        transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(VertoMotion.attentionPulse), RepeatMode.Reverse),
            label = androidx.compose.ui.res.stringResource(com.verto.feature.invoice.R.string.invoice_ds_387d313d27a2),
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    Surface(
        shape = RoundedCornerShape(InvoiceDimensions.dp20),
        color = bgColor,
        border = BorderStroke(InvoiceDimensions.dp1, txtColor.copy(alpha = 0.3f)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = InvoiceDimensions.dp10, vertical = InvoiceDimensions.dp4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(InvoiceDimensions.dp4),
        ) {
            Text(icon, fontSize = InvoiceTextScale.sp10, modifier = Modifier.alpha(alpha))
            Text(
                text = label,
                color = txtColor,
                fontSize = InvoiceTextScale.sp11,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
