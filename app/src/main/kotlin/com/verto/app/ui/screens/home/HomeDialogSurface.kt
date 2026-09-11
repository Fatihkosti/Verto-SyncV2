package com.verto.app.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.VertoElevation
import com.verto.app.ui.theme.VertoRadius
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoStroke

/** Shared home-dialog shell used by every dialog on the work-center screen. */
@Composable
internal fun HomeDialogSurface(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false,
    accessibilityLabel: String? = null,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val heightModifier = if (fillHeight) {
            Modifier.fillMaxHeight(HomeDesignTokens.dialogMaxHeightFraction)
        } else {
            Modifier
        }
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .then(heightModifier)
                .padding(horizontal = VertoSize.screenHorizontalPadding)
                .widthIn(max = HomeDesignTokens.dialogMaxWidth)
                .then(
                    if (accessibilityLabel == null) Modifier else Modifier.semantics {
                        contentDescription = accessibilityLabel
                    },
                ),
            color = BgCard,
            shape = RoundedCornerShape(VertoRadius.xl),
            tonalElevation = VertoElevation.floating,
            border = BorderStroke(VertoStroke.thin, BorderColor),
            content = content,
        )
    }
}
