import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


class AuthCutoverContractTest(unittest.TestCase):
    def test_main_activity_always_starts_at_splash(self):
        text = read("app/src/main/kotlin/com/verto/app/MainActivity.kt")
        self.assertRegex(text, r'startDestination\s*=\s*"splash"')
        self.assertNotIn("authRepo.isLoggedIn()", text)

    def test_main_activity_has_no_password_recovery_deep_link_handler(self):
        text = read("app/src/main/kotlin/com/verto/app/MainActivity.kt")
        self.assertNotIn("handlePasswordResetDeepLink", text)
        self.assertNotIn("handleDeepLinks", text)
        self.assertNotIn("com.verto://reset-password", text)

    def test_manifest_has_no_legacy_reset_password_deep_link(self):
        text = read("app/src/main/AndroidManifest.xml")
        self.assertNotIn("reset-password", text)
        self.assertNotIn('android:scheme="com.verto"', text)

    def test_supabase_client_has_no_legacy_auth_deep_link_configuration(self):
        text = read("data/network/src/main/kotlin/com/verto/app/data/remote/SupabaseClient.kt")
        self.assertNotIn("handleDeepLinks", text)
        self.assertNotIn('scheme = "com.verto"', text)
        self.assertNotIn('host = "reset-password"', text)

    def test_recovery_request_is_otp_only_no_redirect_url(self):
        text = read("data/network/src/main/kotlin/com/verto/app/data/remote/AuthAccountRemoteSource.kt")
        reset_body = re.search(
            r"suspend fun resetPassword\(email: String\).*?\n}\n",
            text,
            flags=re.S,
        )
        self.assertIsNotNone(reset_body)
        body = reset_body.group(0)
        self.assertIn("resetPasswordForEmail", body)
        self.assertNotIn("redirectUrl", body)
        self.assertNotIn("redirectTo", body)

    def test_recovery_verification_uses_supabase_recovery_otp(self):
        text = read("data/network/src/main/kotlin/com/verto/app/data/remote/AuthAccountRemoteSource.kt")
        self.assertIn("verifyEmailOtp", text)
        self.assertIn("OtpType.Email.RECOVERY", text)

    def test_password_change_requires_verified_recovery_state_and_same_email(self):
        text = read("data/network/src/main/kotlin/com/verto/app/data/remote/AuthAccountRemoteSource.kt")
        self.assertIn("prefs.isPasswordRecoveryPending()", text)
        self.assertIn("prefs.passwordRecoveryEmail()", text)
        self.assertRegex(text, r"currentEmail\s*!=\s*expectedEmail")
        self.assertIn("RECOVERY_SESSION_REQUIRED", text)
        self.assertIn("client.auth.updateUser { password = newPassword }", text)
        self.assertIn("client.auth.signOut()", text)

    def test_recovery_session_is_denied_before_splash_server_admission(self):
        text = read(
            "feature/auth/src/main/kotlin/com/verto/app/feature/auth/presentation/splash/SplashViewModel.kt"
        )
        pending = text.index("hasPendingPasswordRecovery")
        server = text.index("fetchSessionAuthorizationStatus")
        self.assertLess(pending, server)
        self.assertIn("wipeAndSignOut", text[pending:server])

    def test_recovery_otp_length_is_eight(self):
        text = read(
            "feature/auth/src/main/kotlin/com/verto/app/feature/auth/presentation/RecoveryOtpPolicy.kt"
        )
        self.assertRegex(text, r"RECOVERY_OTP_LENGTH\s*=\s*8")

    def test_clipboard_extraction_accepts_only_complete_eight_digit_code(self):
        text = read(
            "feature/auth/src/main/kotlin/com/verto/app/feature/auth/presentation/RecoveryOtpPolicy.kt"
        )
        self.assertIn(r"(?<!\\d)", text)
        self.assertIn(r"(?!\\d)", text)
        self.assertIn("RECOVERY_OTP_LENGTH", text)

    def test_otp_screen_attempts_clipboard_fill_when_returning_to_app(self):
        text = read(
            "feature/auth/src/main/kotlin/com/verto/app/feature/auth/presentation/PasswordResetSentScreen.kt"
        )
        self.assertIn("LocalClipboardManager.current", text)
        self.assertIn("Lifecycle.Event.ON_RESUME", text)
        self.assertIn("fillOtpFromClipboard()", text)
        self.assertIn("extractRecoveryOtpFromClipboard", text)

    def test_otp_component_supports_full_code_paste_and_completion(self):
        text = read(
            "feature/auth/src/main/kotlin/com/verto/app/feature/auth/presentation/AuthComponents.kt"
        )
        self.assertIn("digits.take(RECOVERY_OTP_LENGTH)", text)
        self.assertIn("if (next.length == RECOVERY_OTP_LENGTH)", text)
        self.assertIn("onComplete(next)", text)

    def test_viewmodel_rejects_non_exact_otp_format(self):
        text = read(
            "feature/auth/src/main/kotlin/com/verto/app/feature/auth/presentation/AuthViewModel.kt"
        )
        self.assertIn("normalizedOtp.length != RECOVERY_OTP_LENGTH", text)
        self.assertIn("normalizedOtp != otp", text)
        self.assertIn("AuthErrorCodes.OTP_FORMAT", text)

    def test_auth_gateway_contains_only_canonical_recovery_methods(self):
        text = read(
            "feature/auth/src/main/kotlin/com/verto/app/feature/auth/domain/repository/AuthGateway.kt"
        )
        self.assertIn("requestPasswordReset", text)
        self.assertIn("verifyPasswordResetOtp", text)
        self.assertIn("setNewPasswordAfterVerifiedOtp", text)
        self.assertNotIn("sendPasswordResetLink", text)
        self.assertNotIn("handleRecoveryLink", text)


    def test_login_uses_email_password_and_requires_active_profile(self):
        text = read("data/network/src/main/kotlin/com/verto/app/data/remote/AuthAccountRemoteSource.kt")
        self.assertIn("client.auth.signInWith(Email)", text)
        self.assertIn("fetchActiveProfile().getOrThrow()", text)
        self.assertIn("AuthErrorCodes.PROFILE_MISSING", text)

    def test_registration_provisions_organization_through_server_rpc(self):
        text = read("data/network/src/main/kotlin/com/verto/app/data/remote/AuthAccountRemoteSource.kt")
        self.assertIn("client.auth.signUpWith(Email)", text)
        self.assertIn('"create_organization_with_admin"', text)
        self.assertIn("AuthErrorCodes.PROVISIONING_INCOMPLETE", text)

    def test_join_flow_validates_invite_then_calls_canonical_join_rpc(self):
        text = read("data/network/src/main/kotlin/com/verto/app/data/remote/InviteRemoteSource.kt")
        self.assertIn("getInviteCodeDetails(inviteCode).getOrThrow()", text)
        self.assertIn("client.auth.signUpWith(Email)", text)
        self.assertIn('"join_organization_with_code"', text)
        self.assertIn("if (row.used)", text)
        self.assertIn("SupabaseTimestamp.isExpired", text)

    def test_logged_in_password_change_reauthenticates_before_update(self):
        text = read("data/network/src/main/kotlin/com/verto/app/data/remote/AuthAccountRemoteSource.kt")
        start = text.index("suspend fun changePassword")
        end = text.index("suspend fun resetPassword")
        body = text[start:end]
        sign_in = body.index("client.auth.signInWith(Email)")
        update = body.index("client.auth.updateUser")
        self.assertLess(sign_in, update)
        self.assertIn("currentPassword", body)

    def test_logout_clears_recovery_state_and_revokes_push_token_before_signout(self):
        text = read("data/network/src/main/kotlin/com/verto/app/data/remote/AuthAccountRemoteSource.kt")
        start = text.index("suspend fun logout")
        body = text[start:]
        clear = body.index("prefs.clearPasswordRecoveryState()")
        push = body.index("pushTokens.deleteCurrentUserToken()")
        signout = body.index("client.auth.signOut()")
        self.assertLess(clear, signout)
        self.assertLess(push, signout)

    def test_no_legacy_reset_password_uri_in_production_sources(self):
        hits = []
        roots = [ROOT / "app/src/main", ROOT / "data/network/src/main", ROOT / "feature/auth/src/main"]
        for base in roots:
            for path in base.rglob("*"):
                if path.is_file() and path.suffix in {".kt", ".xml"}:
                    text = path.read_text(encoding="utf-8", errors="ignore")
                    if "com.verto://reset-password" in text:
                        hits.append(str(path.relative_to(ROOT)))
        self.assertEqual([], hits)


if __name__ == "__main__":
    unittest.main(verbosity=2)
