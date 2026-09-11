package com.verto.app.ui.screens.onboarding

import com.verto.app.R

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.verto.app.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun ThankYouScreen(onNavigateHome: () -> Unit) {
    // الانتظار ثانية ونصف ثم الانتقال للرئيسية بسلاسة
    LaunchedEffect(Unit) {
        delay(1500)
        onNavigateHome()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(BgDeep),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.ds_c20dbb2b07fd),
                fontSize = OnboardingTextScale.sp64
            )
            Spacer(modifier = Modifier.height(OnboardingDimensions.dp16))
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.ds_81a416d0df4d),
                color = TextPrimary,
                fontSize = OnboardingTextScale.sp24,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(OnboardingDimensions.dp8))
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.ds_dfd309bc0912),
                color = TextSecondary,
                fontSize = OnboardingTextScale.sp15,
                textAlign = TextAlign.Center
            )
        }
    }
}