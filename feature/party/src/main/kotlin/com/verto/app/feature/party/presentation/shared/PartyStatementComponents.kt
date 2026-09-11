package com.verto.app.feature.party.presentation.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.verto.app.ui.theme.BgCard
import com.verto.app.ui.theme.BorderColor
import com.verto.app.ui.theme.SuccessColor
import com.verto.app.ui.theme.ErrorColor
import com.verto.app.ui.theme.TextMuted
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.TextSecondary
import com.verto.app.utils.WhatsAppUtils
import kotlin.math.abs

/** Shared Party statement header. Domain meaning stays in Party; Core remains feature-agnostic. */
@Composable
internal fun PartyStatementHeader(accentColor: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = PartyDimensions.dp10, topEnd = PartyDimensions.dp10))
            .background(accentColor.copy(0.15f))
            .padding(horizontal = PartyDimensions.dp8, vertical = PartyDimensions.dp6),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_8de3f4a96430), color = accentColor, fontSize = PartyTextScale.sp11, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
        Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_6ca9ee8ebffd), color = accentColor, fontSize = PartyTextScale.sp11, fontWeight = FontWeight.Bold, modifier = Modifier.weight(3f))
        Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_75001defcb35), color = ErrorColor, fontSize = PartyTextScale.sp11, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f), textAlign = TextAlign.End)
        Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_8d7a1a5199a0), color = SuccessColor, fontSize = PartyTextScale.sp11, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f), textAlign = TextAlign.End)
        Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_f96a754ed8d1), color = TextPrimary, fontSize = PartyTextScale.sp11, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f), textAlign = TextAlign.End)
    }
}

/** Shared Party statement row used by clients and suppliers with only data/date formatting supplied by owners. */
@Composable
internal fun PartyStatementRowItem(
    dateText: String,
    description: String,
    debit: Double,
    credit: Double,
    balance: Double,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(BgCard)
            .padding(horizontal = PartyDimensions.dp8, vertical = PartyDimensions.dp6),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(dateText, color = TextMuted, fontSize = PartyTextScale.sp10, modifier = Modifier.weight(2f))
        Text(description, color = TextSecondary, fontSize = PartyTextScale.sp11, modifier = Modifier.weight(3f))
        Text(
            if (debit > 0) WhatsAppUtils.formatAmount(debit) else androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_f7cc82ae5d5b),
            color = if (debit > 0) ErrorColor else TextMuted,
            fontSize = PartyTextScale.sp11,
            modifier = Modifier.weight(2f),
            textAlign = TextAlign.End,
        )
        Text(
            if (credit > 0) WhatsAppUtils.formatAmount(credit) else androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_4579312f1ac7),
            color = if (credit > 0) SuccessColor else TextMuted,
            fontSize = PartyTextScale.sp11,
            modifier = Modifier.weight(2f),
            textAlign = TextAlign.End,
        )
        Text(
            WhatsAppUtils.formatAmount(abs(balance)),
            color = if (balance > 0) ErrorColor else SuccessColor,
            fontSize = PartyTextScale.sp11,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(2f),
            textAlign = TextAlign.End,
        )
    }
    HorizontalDivider(color = BorderColor.copy(0.3f))
}
