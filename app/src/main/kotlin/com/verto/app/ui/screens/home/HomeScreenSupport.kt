package com.verto.app.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.app.R
import com.verto.app.ui.components.UiMessage
import com.verto.app.ui.components.VertoPrimaryButton
import com.verto.app.ui.components.VertoSecondaryButton
import com.verto.app.ui.components.VertoTextField
import com.verto.app.ui.components.VertoStatusBanner
import com.verto.app.ui.components.VertoStatusTone
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.BgDeep
import com.verto.app.ui.theme.TextOnAccent
import com.verto.app.ui.theme.TextPrimary
import com.verto.app.ui.theme.VertoElevation
import com.verto.app.ui.theme.VertoRadius
import com.verto.app.ui.theme.VertoSize
import com.verto.app.ui.theme.VertoSpacing
import com.verto.feature.dashboard.api.HomeDestination
import java.util.Calendar
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

@Composable
internal fun InternationalPurchaseDialog(
    currencyName: String,
    exchangeRate: String,
    onCurrencyNameChange: (String) -> Unit,
    onExchangeRateChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var currencyError by rememberSaveable { mutableStateOf(false) }
    var rateError by rememberSaveable { mutableStateOf(false) }

    fun submit() {
        currencyError = currencyName.isBlank()
        rateError = exchangeRate.toDoubleOrNull()?.let { it <= 0.0 } != false
        if (!currencyError && !rateError) onConfirm()
    }

    HomeDialogSurface(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(VertoSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(VertoSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.home_international_purchase_title),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Black,
                modifier = Modifier.semantics { heading() },
            )
            VertoTextField(
                value = currencyName,
                onValueChange = {
                    onCurrencyNameChange(it)
                    currencyError = false
                },
                label = stringResource(R.string.home_international_currency_label),
                isRequired = true,
                errorText = stringResource(R.string.home_international_currency_required)
                    .takeIf { currencyError },
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Next,
            )
            VertoTextField(
                value = exchangeRate,
                onValueChange = {
                    onExchangeRateChange(it.filter { char -> char.isDigit() || char == '.' })
                    rateError = false
                },
                label = stringResource(R.string.home_international_rate_label),
                isRequired = true,
                errorText = stringResource(R.string.home_international_rate_invalid)
                    .takeIf { rateError },
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VertoSpacing.sm),
            ) {
                VertoPrimaryButton(
                    text = stringResource(R.string.home_continue),
                    onClick = ::submit,
                    modifier = Modifier.weight(1f),
                )
                VertoSecondaryButton(
                    text = stringResource(R.string.home_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
