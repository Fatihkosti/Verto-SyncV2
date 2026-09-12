package com.verto.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.verto.app.ui.theme.VertoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VertoAccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    private var density = 1f

    private fun setVertoContent(content: @Composable () -> Unit) {
        compose.setContent {
            density = LocalDensity.current.density
            VertoTheme(content = content)
        }
    }

    private fun SemanticsNodeInteraction.assertMinimum48DpTarget(label: String) {
        val bounds = fetchSemanticsNode().boundsInRoot
        val widthDp = bounds.width / density
        val heightDp = bounds.height / density
        assertTrue("$label width must be >= 48dp but was ${widthDp}dp", widthDp >= 48f - 0.5f)
        assertTrue("$label height must be >= 48dp but was ${heightDp}dp", heightDp >= 48f - 0.5f)
    }

    @Test
    fun canonicalButtons_andIconButton_meetLogical48DpTarget() {
        setVertoContent {
            Column {
                VertoButton(onClick = {}) { Text("زر أساسي") }
                VertoOutlinedButton(onClick = {}) { Text("زر محاط") }
                VertoIconButton(onClick = {}) {
                    Icon(Icons.Default.Info, contentDescription = "معلومات")
                }
            }
        }

        compose.onNodeWithText("زر أساسي").assertHasClickAction().assertMinimum48DpTarget("VertoButton")
        compose.onNodeWithText("زر محاط").assertHasClickAction().assertMinimum48DpTarget("VertoOutlinedButton")
        compose.onNodeWithContentDescription("معلومات").assertHasClickAction().assertMinimum48DpTarget("VertoIconButton")
    }

    @Test
    fun clickableCard_settingsRow_andFormButtons_meetLogical48DpTarget() {
        setVertoContent {
            Column(Modifier.width(320.dp)) {
                VertoCard(onClick = {}) { Text("بطاقة تفاعلية") }
                SettingsNavRow(
                    title = "الإعدادات",
                    subtitle = "وصف",
                    icon = Icons.Default.Settings,
                    onClick = {},
                )
                VertoPrimaryButton(text = "حفظ", onClick = {})
                VertoSecondaryButton(text = "عودة", onClick = {})
            }
        }

        compose.onNodeWithText("بطاقة تفاعلية").assertHasClickAction().assertMinimum48DpTarget("VertoCard(onClick)")
        compose.onNodeWithText("الإعدادات").assertHasClickAction().assertMinimum48DpTarget("SettingsNavRow")
        compose.onNodeWithText("حفظ").assertHasClickAction().assertMinimum48DpTarget("VertoPrimaryButton")
        compose.onNodeWithText("عودة").assertHasClickAction().assertMinimum48DpTarget("VertoSecondaryButton")
    }

    @Test
    fun statusAndEmptyActions_meetLogical48DpTarget() {
        setVertoContent {
            Column(Modifier.width(320.dp)) {
                VertoStatusBanner(
                    title = "تنبيه",
                    message = "رسالة حالة واضحة",
                    tone = VertoStatusTone.Warning,
                    actionLabel = "معالجة",
                    onAction = {},
                )
                VertoEmptyState(
                    title = "لا توجد بيانات",
                    message = "أضف سجلاً للمتابعة",
                    actionLabel = "إضافة",
                    onAction = {},
                )
            }
        }

        compose.onNodeWithText("معالجة").assertHasClickAction().assertMinimum48DpTarget("VertoStatusBanner action")
        compose.onNodeWithText("إضافة").assertHasClickAction().assertMinimum48DpTarget("VertoEmptyState action")
    }

    @Test
    fun topBarBack_hasLabel_andLogical48DpTarget() {
        setVertoContent {
            VertoTopBar(
                title = "العنوان",
                onBack = {},
                backContentDescription = "رجوع الاختبار",
            )
        }

        compose.onNodeWithContentDescription("رجوع الاختبار")
            .assertHasClickAction()
            .assertMinimum48DpTarget("VertoTopBar back")
    }

    @Test
    fun confirmationDialog_buttons_meetLogical48DpTarget() {
        setVertoContent {
            VertoConfirmationDialog(
                title = "تأكيد العملية",
                message = "هل تريد المتابعة؟",
                confirmLabel = "نعم، متابعة",
                dismissLabel = "إلغاء الاختبار",
                onConfirm = {},
                onDismiss = {},
            )
        }

        compose.onNodeWithText("نعم، متابعة").assertHasClickAction().assertMinimum48DpTarget("Confirmation confirm")
        compose.onNodeWithText("إلغاء الاختبار").assertHasClickAction().assertMinimum48DpTarget("Confirmation dismiss")
    }

    @Test
    fun dateRangeDialog_navigationButtons_meetLogical48DpTarget() {
        setVertoContent {
            DateRangePickerDialog(
                initialFrom = null,
                initialTo = null,
                onConfirm = { _, _ -> },
                onDismiss = {},
            )
        }

        compose.onNodeWithText("التالي").assertHasClickAction().assertMinimum48DpTarget("DateRange next")
        compose.onNodeWithText("إلغاء").assertHasClickAction().assertMinimum48DpTarget("DateRange cancel")
    }

    @Test
    fun passwordVisibilityAction_isLabeled_andAtLeast48Dp() {
        setVertoContent {
            VertoPasswordField(
                value = "secret",
                onValueChange = {},
                label = "كلمة المرور",
                showPasswordDescription = "إظهار السر",
                hidePasswordDescription = "إخفاء السر",
            )
        }

        compose.onNodeWithContentDescription("إظهار السر")
            .assertHasClickAction()
            .assertMinimum48DpTarget("Password visibility")
    }

    @Test
    fun statusBanner_exposesPoliteLiveRegion_andSeparateAction() {
        setVertoContent {
            VertoStatusBanner(
                title = "خطأ",
                message = "تعذر الحفظ",
                tone = VertoStatusTone.Error,
                actionLabel = "إعادة المحاولة",
                onAction = {},
            )
        }

        val banner = compose.onNodeWithContentDescription("خطأ. تعذر الحفظ").fetchSemanticsNode()
        assertTrue(
            "Status banner must expose live-region semantics",
            banner.config.contains(SemanticsProperties.LiveRegion),
        )
        compose.onNodeWithText("إعادة المحاولة").assertHasClickAction()
    }

    @Test
    fun loadingState_exposesProgressSemantics_andReadableMessage() {
        setVertoContent {
            VertoLoadingState(message = "جارٍ تجهيز البيانات")
        }

        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).fetchSemanticsNode()
        compose.onNodeWithText("جارٍ تجهيز البيانات").fetchSemanticsNode()
    }

    @Test
    fun tabRows_delegateTruthfulSelectedState_toRealTabs() {
        setVertoContent {
            Column(Modifier.fillMaxWidth()) {
                val container = MaterialTheme.colorScheme.surface
                val content = MaterialTheme.colorScheme.onSurface
                VertoTabRow(
                    selectedTabIndex = 0,
                    containerColor = container,
                    contentColor = content,
                ) {
                    Tab(selected = true, onClick = {}, text = { Text("الأول") })
                    Tab(selected = false, onClick = {}, text = { Text("الثاني") })
                }
                VertoScrollableTabRow(
                    selectedTabIndex = 1,
                    containerColor = container,
                    contentColor = content,
                    edgePadding = 0.dp,
                ) {
                    repeat(5) { index ->
                        Tab(
                            selected = index == 1,
                            onClick = {},
                            text = { Text("تبويب ${index + 1}") },
                        )
                    }
                }
            }
        }

        compose.onNodeWithText("الأول").assertIsSelected().assertHasClickAction()
        compose.onNodeWithText("الثاني").assertIsNotSelected().assertHasClickAction()
        compose.onNodeWithText("تبويب 2").assertIsSelected().assertHasClickAction()
        compose.onNodeWithText("تبويب 5").assertIsNotSelected().assertHasClickAction()
    }

    @Test
    fun disabledStates_remainTruthful() {
        setVertoContent {
            Column {
                VertoButton(onClick = {}, enabled = false) { Text("زر معطل") }
                VertoPrimaryButton(text = "حفظ معطل", onClick = {}, enabled = false)
                VertoSecondaryButton(text = "رجوع معطل", onClick = {}, enabled = false)
            }
        }

        compose.onNodeWithText("زر معطل").assertIsNotEnabled()
        compose.onNodeWithText("حفظ معطل").assertIsNotEnabled()
        compose.onNodeWithText("رجوع معطل").assertIsNotEnabled()
    }
}
