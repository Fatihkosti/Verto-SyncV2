package com.verto.app.feature.party.presentation.addclient

import com.verto.app.feature.party.presentation.shared.PartyDimensions
import com.verto.app.feature.party.presentation.shared.PartyTextScale

import com.verto.app.feature.party.domain.model.*
import com.verto.app.feature.party.application.model.*

import androidx.lifecycle.ViewModel
import com.verto.app.core.session.domain.SessionReader
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import com.verto.app.ui.components.*
import com.verto.app.ui.theme.*
import com.verto.app.utils.ErrorHumanizer
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.verto.app.ui.components.VertoIconButton

@Composable
internal fun BankAccountsSection(
    bankAccounts: List<BankAccount>,
    onChanged: (List<BankAccount>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(PartyDimensions.dp8)) {
        Text(
            androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_364769e1f926),
            color = TextSecondary, fontSize = PartyTextScale.sp13, fontWeight = FontWeight.SemiBold
        )
        bankAccounts.forEachIndexed { index, account ->
            Column(verticalArrangement = Arrangement.spacedBy(PartyDimensions.dp6)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PartyDimensions.dp8)
                ) {
                    VertoTextField(
                        value = account.bankName,
                        onValueChange = { v ->
                            onChanged(bankAccounts.toMutableList().also { it[index] = it[index].copy(bankName = v) })
                        },
                        label = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_9b1524057781, index + 1),
                        placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_35805e4f14c6),
                        leadingIcon = { Icon(Icons.Filled.AccountBalance, null, tint = TextMuted) },
                        modifier = Modifier.weight(1f)
                    )
                    VertoIconButton(
                        onClick = {
                            onChanged(bankAccounts.toMutableList().also { it.removeAt(index) })
                        },
                        modifier = Modifier.size(PartyDimensions.dp48)
                    ) {
                        Icon(Icons.Filled.RemoveCircleOutline, null,
                            tint = ErrorColor, modifier = Modifier.size(PartyDimensions.dp20))
                    }
                }
                VertoTextField(
                    value = account.accountNumber,
                    onValueChange = { v ->
                        onChanged(bankAccounts.toMutableList().also { it[index] = it[index].copy(accountNumber = v) })
                    },
                    label = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_d249c7b6df49),
                    placeholder = androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_7f93875e0d32),
                    keyboardType = KeyboardType.Number,
                    leadingIcon = { Icon(Icons.Filled.Numbers, null, tint = TextMuted) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        TextButton(
            onClick = { onChanged(bankAccounts + BankAccount()) },
            contentPadding = PaddingValues(horizontal = PartyDimensions.dp0, vertical = PartyDimensions.dp4)
        ) {
            Icon(Icons.Filled.AddCircleOutline, null, tint = AccentBlue, modifier = Modifier.size(PartyDimensions.dp16))
            Spacer(Modifier.width(PartyDimensions.dp4))
            Text(androidx.compose.ui.res.stringResource(com.verto.feature.party.R.string.party_ds_3627fabf3151), color = AccentBlue, fontSize = PartyTextScale.sp13)
        }
    }
}
