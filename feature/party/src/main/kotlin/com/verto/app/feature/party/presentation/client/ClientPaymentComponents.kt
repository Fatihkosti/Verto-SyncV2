package com.verto.app.feature.party.presentation.client

import com.verto.app.feature.party.presentation.shared.PartyDimensions
import com.verto.app.feature.party.presentation.shared.PartyTextScale

import com.verto.app.feature.party.domain.model.*
import com.verto.app.feature.party.application.model.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.ui.components.VertoTopBar
import com.verto.app.ui.theme.*
import com.verto.app.utils.WhatsAppUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@Composable
internal fun InvoiceSelectRow(
    summary   : PartyInvoiceSummary,
    isSelected: Boolean,
    onClick   : () -> Unit
) {
    val borderColor = if (isSelected) AccentPrimary else BorderColor
    val bgColor     = if (isSelected) AccentPrimary.copy(0.08f) else BgCard

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PartyDimensions.dp10))
            .background(bgColor)
            .border(PartyDimensions.dp1, borderColor, RoundedCornerShape(PartyDimensions.dp10))
            .clickable(onClick = onClick)
            .padding(PartyDimensions.dp12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PartyDimensions.dp10)
    ) {
        Checkbox(
            checked        = isSelected,
            onCheckedChange = { onClick() },
            colors = CheckboxDefaults.colors(
                checkedColor   = AccentPrimary,
                uncheckedColor = TextMuted
            )
        )
        Column(Modifier.weight(1f)) {
            Text(
                androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_a6c4b78fa899_2, String.format(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_a6c4b78fa899), summary.invoice.invoiceNumber)),
                color      = if (isSelected) AccentPrimary else TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize   = PartyTextScale.sp14
            )
            Text(
                summary.invoice.description,
                color    = TextMuted,
                fontSize = PartyTextScale.sp12,
                maxLines = 1
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_557f737dff23), color = TextMuted, fontSize = PartyTextScale.sp11)
            Text(
                WhatsAppUtils.formatAmount(summary.remaining) + androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_v298_93520f4d8132),
                color      = ErrorColor,
                fontWeight = FontWeight.Bold,
                fontSize   = PartyTextScale.sp13
            )
        }
    }
}

@Composable
internal fun PayPreviewRow(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, fontSize = PartyTextScale.sp12)
        Text(value, color = color, fontSize = PartyTextScale.sp12, fontWeight = FontWeight.Bold)
    }
}

internal fun fmtPay(v: Double) =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v)
