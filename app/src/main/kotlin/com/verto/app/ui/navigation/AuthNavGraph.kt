package com.verto.app.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.verto.app.feature.sync.presentation.SyncViewModel
import com.verto.app.feature.auth.presentation.JoinOrgScreen
import com.verto.app.feature.auth.presentation.LoginScreen
import com.verto.app.feature.auth.presentation.PasswordResetSentScreen
import com.verto.app.feature.auth.presentation.RegisterScreen
import com.verto.app.feature.auth.presentation.ResetPasswordScreen
import com.verto.app.feature.auth.presentation.splash.SplashScreen

private const val ARG_RESET_OTP_VERIFIED = "reset_otp_verified"

internal fun NavGraphBuilder.authGraph(navController: NavHostController, syncViewModel: SyncViewModel) {

    // ── Splash ─────────────────────────────────────────────
    composable("splash") {
        SplashScreen(
            onNavigateToHome = {
                syncViewModel.pull()
                navController.navigate(Screen.Home.route) { popUpTo("splash") { inclusive = true } }
            },
            onNavigateToLogin = {
                navController.navigate(Screen.Login.route) { popUpTo("splash") { inclusive = true } }
            }
        )
    }

    // ── Auth ───────────────────────────────────────────────
    composable(Screen.Login.route) {
        LoginScreen(
            onLoginSuccess = {
                syncViewModel.pull()
                navController.navigate(Screen.Home.route) { popUpTo(0) { inclusive = true } }
            },
            onNavigateToRegister = { navController.navigate(Screen.Register.route) },
            onNavigateToJoin     = { navController.navigate(Screen.JoinOrg.route) },
            onNavigateToPasswordReset = { email ->
                // البريد عبر savedStateHandle بدل المسار (تفادي تسريب PII).
                navController.currentBackStackEntry?.savedStateHandle
                    ?.set(Screen.PasswordResetSent.ARG_RESET_EMAIL, email)
                navController.navigate(Screen.PasswordResetSent.route)
            }
        )
    }

    composable(Screen.PasswordResetSent.route) {
        val email = navController.previousBackStackEntry?.savedStateHandle
            ?.get<String>(Screen.PasswordResetSent.ARG_RESET_EMAIL) ?: ""
        PasswordResetSentScreen(
            email = email,
            onBackToLogin = {
                navController.previousBackStackEntry?.savedStateHandle
                    ?.remove<String>(Screen.PasswordResetSent.ARG_RESET_EMAIL)
                navController.popBackStack()
            },
            onOtpVerified = { verifiedEmail, isVerified ->
                navController.currentBackStackEntry?.savedStateHandle?.apply {
                    set(Screen.PasswordResetSent.ARG_RESET_EMAIL, verifiedEmail)
                    set(ARG_RESET_OTP_VERIFIED, isVerified)
                }
                navController.navigate(Screen.ResetPassword.route)
            },
        )
    }

    composable(Screen.ResetPassword.route) {
        val resetState = navController.previousBackStackEntry?.savedStateHandle
        val email = resetState?.get<String>(Screen.PasswordResetSent.ARG_RESET_EMAIL).orEmpty()
        val isOtpVerified = resetState?.get<Boolean>(ARG_RESET_OTP_VERIFIED) == true
        ResetPasswordScreen(
            email = email,
            isOtpVerified = isOtpVerified,
            onPasswordReset = {
                resetState?.remove<String>(Screen.PasswordResetSent.ARG_RESET_EMAIL)
                resetState?.remove<Boolean>(ARG_RESET_OTP_VERIFIED)
                navController.navigate(Screen.Login.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        )
    }

    composable(Screen.Register.route) {
        RegisterScreen(
            onRegisterSuccess = {
                navController.navigate(Screen.Home.route) { popUpTo(0) { inclusive = true } }
            },
            onNavigateToLogin = {
                navController.navigate(Screen.Login.route) {
                    popUpTo(Screen.Register.route) { inclusive = true }
                }
            }
        )
    }

    composable(Screen.JoinOrg.route) {
        JoinOrgScreen(
            onJoinSuccess     = {
                syncViewModel.pull()
                navController.navigate(Screen.Home.route) { popUpTo(0) { inclusive = true } }
            },
            onNavigateToLogin = { navController.popBackStack() }
        )
    }
}
