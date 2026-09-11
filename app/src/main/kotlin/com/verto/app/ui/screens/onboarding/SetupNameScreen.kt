package com.verto.app.ui.screens.onboarding

import com.verto.app.R

import com.verto.app.ui.components.VertoButton

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.verto.app.ui.components.VertoTextField
import com.verto.app.ui.theme.*

@Composable
fun SetupNameScreen(onNameSaved: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Scaffold(containerColor = BgDeep) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(OnboardingDimensions.dp24),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(androidx.compose.ui.res.stringResource(R.string.ds_34660abcfc4c), color = TextPrimary, fontSize = OnboardingTextScale.sp28, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(OnboardingDimensions.dp12))
            Text(androidx.compose.ui.res.stringResource(R.string.ds_43ddabfebc2b), color = TextSecondary, fontSize = OnboardingTextScale.sp15, textAlign = androidx.compose.ui.text.style.TextAlign.Center)

            Spacer(modifier = Modifier.height(OnboardingDimensions.dp40))

            VertoTextField(
                value = name,
                onValueChange = { name = it; isError = false },
                label = androidx.compose.ui.res.stringResource(R.string.ds_321d00ab93f6),
                placeholder = androidx.compose.ui.res.stringResource(R.string.ds_bae376f36075),
                isError = isError,
                isRequired = true
            )

            if (isError) {
                Text(androidx.compose.ui.res.stringResource(R.string.ds_bebea75c8a1e), color = ErrorColor, fontSize = OnboardingTextScale.sp12, modifier = Modifier.align(Alignment.Start).padding(start = OnboardingDimensions.dp8, top = OnboardingDimensions.dp4))
            }

            Spacer(modifier = Modifier.height(OnboardingDimensions.dp32))

            VertoButton(
                onClick = {
                    if (name.trim().isEmpty()) {
                        isError = true
                    } else {
                        onNameSaved(name.trim())
                    }
                },
                modifier = Modifier.fillMaxWidth().height(OnboardingDimensions.dp56),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                shape = RoundedCornerShape(OnboardingDimensions.dp16)
            ) {
                Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_follow_up), color = TextPrimary, fontSize = OnboardingTextScale.sp16, fontWeight = FontWeight.Bold)
            }
        }
    }
}
