package com.verto.app.feature.auth.presentation.splash

import com.verto.feature.auth.R
import com.verto.app.feature.auth.presentation.AuthTextScale

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import com.verto.app.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    var startAnimation by remember { mutableStateOf(false) }

    val strokeWidth by animateFloatAsState(
        targetValue = if (startAnimation) 0f else 6f,
        animationSpec = tween(durationMillis = VertoMotion.long, easing = FastOutSlowInEasing),
        label = androidx.compose.ui.res.stringResource(R.string.ds_de2a1ca2bcc6)
    )
    val fillAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = VertoMotion.long, easing = FastOutSlowInEasing),
        label = androidx.compose.ui.res.stringResource(R.string.ds_9ecb01a0191a)
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(2000)

        // قرار الجلسة والتحقق من حالة المستخدم خلف SplashSessionGateway.
        when (viewModel.decide()) {
            SplashViewModel.Decision.HOME  -> onNavigateToHome()
            SplashViewModel.Decision.LOGIN -> onNavigateToLogin()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(BgDeep),
        contentAlignment = Alignment.Center
    ) {
        if (strokeWidth > 0f) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.ds_2d2bb6a420ab),
                fontSize = AuthTextScale.sp72,
                fontWeight = FontWeight.Black,
                color = AccentPrimary.copy(alpha = 0.8f),
                style = TextStyle.Default.copy(drawStyle = Stroke(width = strokeWidth))
            )
        }
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.ds_2d2bb6a420ab),
            fontSize = AuthTextScale.sp72,
            fontWeight = FontWeight.Black,
            style = TextStyle(
                brush = Brush.linearGradient(listOf(AccentPrimary, AccentBlue)),
                alpha = fillAlpha
            )
        )
    }
}
