package com.verto.app.feature.party.presentation.client

import com.verto.app.feature.party.presentation.shared.PartyDimensions
import com.verto.app.feature.party.presentation.shared.PartyTextScale

import com.verto.app.feature.party.domain.model.*
import com.verto.app.feature.party.application.model.*

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.ui.components.VertoTopBar
import com.verto.app.ui.components.WhatsAppIcon
import com.verto.app.feature.party.presentation.shared.DashboardInvoiceRow
import com.verto.app.ui.components.InfoChip
import com.verto.app.ui.components.KpiMini
import com.verto.app.ui.theme.*
import com.verto.app.utils.DateUtils
import com.verto.app.utils.WhatsAppUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun ClientContactCard(
    client: PartyClient,
) {
    val secondaryPhones = client.secondaryPhones.toSecondaryPhoneList()
    val hasPrimaryPhone = client.phone.isNotBlank()

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PartyDimensions.dp16))
            .background(BgCard)
            .border(PartyDimensions.dp1, AccentPrimary.copy(0.2f), RoundedCornerShape(PartyDimensions.dp16))
            .padding(PartyDimensions.dp16),
        verticalArrangement = Arrangement.spacedBy(PartyDimensions.dp10)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(PartyDimensions.dp52)
                    .clip(CircleShape)
                    .background(AccentPrimary.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    client.name.take(1),
                    color = AccentPrimary,
                    fontSize = PartyTextScale.sp22,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(Modifier.width(PartyDimensions.dp12))
            Column(Modifier.weight(1f)) {
                Text(client.name, color = TextPrimary, fontSize = PartyTextScale.sp16, fontWeight = FontWeight.Bold)
                if (hasPrimaryPhone) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PartyDimensions.dp4)
                    ) {
                        Icon(WhatsAppIcon, null,
                            tint = WhatsAppBrand, modifier = Modifier.size(PartyDimensions.dp12))
                        Text(client.phone, color = TextMuted, fontSize = PartyTextScale.sp13)
                    }
                }
                Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_8898da70bb4c), color = AccentPrimary, fontSize = PartyTextScale.sp11)
            }
        }

        if (secondaryPhones.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(PartyDimensions.dp4)) {
                secondaryPhones.forEach { phone ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PartyDimensions.dp6)
                    ) {
                        Icon(Icons.Filled.Phone, null,
                            tint = TextMuted, modifier = Modifier.size(PartyDimensions.dp13))
                        Text(phone, color = TextSecondary, fontSize = PartyTextScale.sp13)
                    }
                }
            }
        }
    }
}


@Composable
internal fun CustomerProfileCard(profile: CustomerProfile) {
    val ageLabel = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_profile_age)
    val workplaceLabel = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_profile_workplace)
    val vehiclesLabel = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_profile_vehicles)
    val purchaseContactLabel = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_profile_purchase_contact)
    val businessActivityLabel = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_profile_business_activity)
    val workshopNameLabel = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_profile_workshop_name)
    val workerCountLabel = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_profile_worker_count)
    val shopNameLabel = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_profile_shop_name)

    val rows = mutableListOf<Pair<String, String>>()
    when (profile.segment) {
        CustomerSegment.INDIVIDUAL -> {
            profile.ageYears?.let { rows += ageLabel to it.toString() }
            profile.workplaceName.takeIf(String::isNotBlank)?.let { rows += workplaceLabel to it }
            if (profile.vehicleModels.isNotEmpty()) rows += vehiclesLabel to profile.vehicleModels.joinToString("، ")
        }
        CustomerSegment.COMPANY -> {
            profile.purchaseContactName.takeIf(String::isNotBlank)?.let { rows += purchaseContactLabel to it }
            profile.businessActivity.takeIf(String::isNotBlank)?.let { rows += businessActivityLabel to it }
            if (profile.vehicleModels.isNotEmpty()) rows += vehiclesLabel to profile.vehicleModels.joinToString("، ")
        }
        CustomerSegment.WORKSHOP_OWNER -> {
            profile.workshopName.takeIf(String::isNotBlank)?.let { rows += workshopNameLabel to it }
            profile.businessActivity.takeIf(String::isNotBlank)?.let { rows += businessActivityLabel to it }
            profile.workshopWorkerCount?.let { rows += workerCountLabel to it.toString() }
        }
        CustomerSegment.MARKETER -> {
            profile.businessActivity.takeIf(String::isNotBlank)?.let { rows += businessActivityLabel to it }
        }
        CustomerSegment.TRADER -> {
            profile.shopName.takeIf(String::isNotBlank)?.let { rows += shopNameLabel to it }
            profile.businessActivity.takeIf(String::isNotBlank)?.let { rows += businessActivityLabel to it }
        }
        CustomerSegment.DISTRIBUTOR -> {
            profile.businessActivity.takeIf(String::isNotBlank)?.let { rows += businessActivityLabel to it }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PartyDimensions.dp16))
            .background(BgCard)
            .border(PartyDimensions.dp1, BorderColor.copy(alpha = 0.45f), RoundedCornerShape(PartyDimensions.dp16))
            .padding(PartyDimensions.dp16),
        verticalArrangement = Arrangement.spacedBy(PartyDimensions.dp10),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Badge, null, tint = AccentPrimary, modifier = Modifier.size(PartyDimensions.dp18))
            Spacer(Modifier.width(PartyDimensions.dp8))
            Text(
                androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_profile_title),
                color = TextPrimary,
                fontSize = PartyTextScale.sp14,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(profile.segment.customerSegmentLabel(), color = AccentPrimary, fontSize = PartyTextScale.sp12, fontWeight = FontWeight.Bold)
        }
        rows.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PartyDimensions.dp8)) {
                Text(label, color = TextMuted, fontSize = PartyTextScale.sp12, modifier = Modifier.widthIn(min = PartyDimensions.dp80))
                Text(value, color = TextSecondary, fontSize = PartyTextScale.sp13, modifier = Modifier.weight(1f))
            }
        }
    }
}
