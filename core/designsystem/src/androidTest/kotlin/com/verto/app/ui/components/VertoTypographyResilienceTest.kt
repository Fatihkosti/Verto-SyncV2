package com.verto.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.verto.app.ui.theme.AccentPrimary
import com.verto.app.ui.theme.VertoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VertoTypographyResilienceTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setNarrowFontScale2(content: @Composable () -> Unit) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                VertoTheme {
                    Box(Modifier.width(320.dp).height(640.dp).testTag("v303-320-root")) {
                        content()
                    }
                }
            }
        }
    }

    private fun SemanticsNodeInteraction.assertInsideRoot(label: String) {
        val root = compose.onNodeWithTag("v303-320-root", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val bounds = fetchSemanticsNode().boundsInRoot
        assertTrue("$label must have positive bounds", bounds.width > 0f && bounds.height > 0f)
        assertTrue("$label must not overflow root horizontally: $bounds vs $root", bounds.left >= root.left - 1f && bounds.right <= root.right + 1f)
    }

    @Test
    fun coreTextControls_areReachableAt320dp_fontScale2_rtl() {
        setNarrowFontScale2 {
            Column(Modifier.fillMaxWidth()) {
                VertoPrimaryButton(text = "تنفيذ العملية المطلوبة الآن", onClick = {})
                VertoSecondaryButton(text = "العودة إلى الشاشة السابقة", onClick = {})
                VertoTextField(
                    value = "قيمة عربية طويلة 12345 ABC",
                    onValueChange = {},
                    label = "اسم الحقل الطويل للاختبار",
                    errorText = "رسالة خطأ عربية طويلة يجب أن تبقى قابلة للقراءة",
                )
                VertoInlineStatus(
                    message = "تنبيه طويل مع رقم 12345 وكلمة English لاختبار اتجاه النص",
                    tone = VertoStatusTone.Warning,
                )
            }
        }

        compose.onNodeWithText("تنفيذ العملية المطلوبة الآن").assertIsDisplayed().assertInsideRoot("Primary button")
        compose.onNodeWithText("العودة إلى الشاشة السابقة").assertIsDisplayed().assertInsideRoot("Secondary button")
        compose.onNodeWithText("اسم الحقل الطويل للاختبار").assertIsDisplayed().assertInsideRoot("Field label")
        compose.onNodeWithText("رسالة خطأ عربية طويلة يجب أن تبقى قابلة للقراءة").assertIsDisplayed().assertInsideRoot("Field error")
        compose.onNodeWithText("تنبيه طويل مع رقم 12345 وكلمة English لاختبار اتجاه النص").assertIsDisplayed().assertInsideRoot("Inline status")
    }

    @Test
    fun kpiAndInfoChip_hotspots_keepContentInsideNarrowRootAtFontScale2() {
        setNarrowFontScale2 {
            Column(Modifier.fillMaxWidth()) {
                KpiMini(
                    label = "إجمالي المبالغ المستحقة حتى اليوم",
                    value = "123,456,789.50 SDG",
                    color = AccentPrimary,
                    modifier = Modifier.fillMaxWidth(),
                )
                InfoChip(
                    icon = Icons.Default.Info,
                    label = "حالة المخزون التفصيلية جداً",
                    value = "123,456 قطعة / Warehouse A",
                    color = AccentPrimary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        compose.onNodeWithText("إجمالي المبالغ المستحقة حتى اليوم").assertIsDisplayed().assertInsideRoot("KpiMini label")
        compose.onNodeWithText("123,456,789.50 SDG").assertIsDisplayed().assertInsideRoot("KpiMini value")
        compose.onNodeWithText("حالة المخزون التفصيلية جداً").assertIsDisplayed().assertInsideRoot("InfoChip label")
        compose.onNodeWithText("123,456 قطعة / Warehouse A").assertIsDisplayed().assertInsideRoot("InfoChip value")
    }

    @Test
    fun statusEmptyAndSettingsRows_remainReachableAtFontScale2() {
        setNarrowFontScale2 {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                VertoStatusBanner(
                    title = "حالة تحتاج إلى انتباه",
                    message = "هذه رسالة طويلة لاختبار المرونة مع التكبير 200% واتجاه RTL",
                    tone = VertoStatusTone.Error,
                    actionLabel = "إعادة المحاولة",
                    onAction = {},
                )
                VertoEmptyState(
                    title = "لا توجد نتائج مطابقة",
                    message = "يمكنك تغيير معايير البحث أو إضافة سجل جديد من هنا",
                    actionLabel = "إضافة سجل جديد",
                    onAction = {},
                    variant = VertoEmptyStateVariant.Plain,
                )
                SettingsNavRow(
                    title = "إعدادات طويلة لاختبار الالتفاف",
                    subtitle = "وصف ثانوي طويل مع 123 English",
                    icon = Icons.Default.Settings,
                    onClick = {},
                )
            }
        }

        compose.onNodeWithText("حالة تحتاج إلى انتباه").performScrollTo().assertIsDisplayed().assertInsideRoot("Status title")
        compose.onNodeWithText("إعادة المحاولة").performScrollTo().assertIsDisplayed().assertInsideRoot("Status action")
        compose.onNodeWithText("لا توجد نتائج مطابقة").performScrollTo().assertIsDisplayed().assertInsideRoot("Empty title")
        compose.onNodeWithText("إضافة سجل جديد").performScrollTo().assertIsDisplayed().assertInsideRoot("Empty action")
        compose.onNodeWithText("إعدادات طويلة لاختبار الالتفاف").performScrollTo().assertIsDisplayed().assertInsideRoot("Settings row")
    }

    @Test
    fun authScaffold_lastActionRemainsReachableAt320dp_fontScale2() {
        setNarrowFontScale2 {
            VertoAuthScaffold {
                VertoFormSectionHeader(
                    title = "تسجيل الدخول إلى حساب Verto",
                    description = "وصف عربي طويل يضغط التخطيط عند تكبير الخط إلى 200%",
                )
                Spacer(Modifier.height(24.dp))
                repeat(5) { index ->
                    VertoTextField(
                        value = "قيمة ${index + 1}",
                        onValueChange = {},
                        label = "الحقل ${index + 1}",
                    )
                    Spacer(Modifier.height(16.dp))
                }
                VertoPrimaryButton(text = "آخر إجراء في النموذج", onClick = {})
            }
        }

        compose.onNodeWithText("تسجيل الدخول إلى حساب Verto").assertIsDisplayed()
        compose.onNodeWithText("آخر إجراء في النموذج").performScrollTo().assertIsDisplayed().assertInsideRoot("Auth last action")
    }

    @Test
    fun topBar_mixedArabicEnglishTitle_staysInside320dpAtFontScale2() {
        setNarrowFontScale2 {
            VertoTopBar(
                title = "تفاصيل الفاتورة INV-2026-123456 الطويلة",
                onBack = {},
                backContentDescription = "رجوع",
            )
        }

        compose.onNodeWithContentDescription("رجوع").assertIsDisplayed().assertInsideRoot("Top bar back")
        compose.onNodeWithText("تفاصيل الفاتورة INV-2026-123456 الطويلة").assertIsDisplayed().assertInsideRoot("Top bar title")
    }
}
