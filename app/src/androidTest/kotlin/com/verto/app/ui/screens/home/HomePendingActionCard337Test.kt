package com.verto.app.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.verto.app.ui.theme.VertoTheme
import com.verto.feature.dashboard.api.HomeAction
import com.verto.feature.dashboard.api.HomeDestination
import com.verto.feature.dashboard.api.PendingAction
import com.verto.feature.dashboard.api.PendingActionPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomePendingActionCard337Test {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun primarySecondarySnoozeAndDismiss_areReachable() {
        var executed = ""
        var snoozed = 0
        var dismissed = 0
        compose.setContent {
            VertoTheme {
                HomePendingActionCard(
                    event = event(),
                    onOpen = {},
                    onAction = { executed = it.id },
                    onSnooze = { snoozed++ },
                    onDismiss = { dismissed++ },
                )
            }
        }

        compose.onNodeWithText("فتح").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithContentDescription("إجراءات إضافية").performClick()
        compose.onNodeWithText("اتصال").assertIsDisplayed().performClick()
        assertEquals("call", executed)

        compose.onNodeWithContentDescription("إجراءات إضافية").performClick()
        compose.onNodeWithText("تأجيل").assertIsDisplayed().performClick()
        assertEquals(1, snoozed)

        compose.onNodeWithContentDescription("إجراءات إضافية").performClick()
        compose.onNodeWithText("إغلاق").assertIsDisplayed().performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun primaryAndOverflowControls_haveAtLeast48DpLogicalTarget() {
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            VertoTheme {
                HomePendingActionCard(
                    event = event(),
                    onOpen = {},
                    onAction = {},
                    onSnooze = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("فتح").assertAtLeast48Dp(density, "primary")
        compose.onNodeWithContentDescription("إجراءات إضافية").assertAtLeast48Dp(density, "overflow")
    }

    @Test
    fun fontScale2Rtl_keepsTitleSummaryAndPrimaryActionReachable() {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                VertoTheme {
                    Box(Modifier.width(320.dp).height(800.dp).testTag("pending-root")) {
                        HomePendingActionCard(
                            event = event(
                                title = "عنوان حدث طويل لاختبار التكبير مئتين بالمئة",
                                summary = "ملخص طويل يجب ألا يختفي أو يتداخل مع الإجراء الأساسي في البطاقة",
                            ),
                            onOpen = {},
                            onAction = {},
                            onSnooze = {},
                            onDismiss = {},
                        )
                    }
                }
            }
        }

        compose.onNodeWithText("عنوان حدث طويل لاختبار التكبير مئتين بالمئة", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("ملخص طويل يجب ألا يختفي أو يتداخل مع الإجراء الأساسي في البطاقة", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("فتح", useUnmergedTree = true).assertIsDisplayed().assertInsideRoot()
    }

    private fun event(
        title: String = "حدث يحتاج إجراء",
        summary: String = "ملخص الحدث",
    ) = PendingAction(
        eventKey = "event-337",
        title = title,
        summary = summary,
        occurredAtEpochMillis = 1L,
        priority = PendingActionPriority.HIGH,
        destination = HomeDestination("details"),
        actions = listOf(
            HomeAction("open", "فتح", HomeDestination("details")),
            HomeAction("call", "اتصال", HomeDestination("call")),
        ),
    )

    private fun SemanticsNodeInteraction.assertAtLeast48Dp(density: Float, label: String) {
        val bounds = fetchSemanticsNode().boundsInRoot
        assertTrue("$label width < 48dp", bounds.width / density >= 47.5f)
        assertTrue("$label height < 48dp", bounds.height / density >= 47.5f)
    }

    private fun SemanticsNodeInteraction.assertInsideRoot() {
        val root = compose.onNodeWithTag("pending-root", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val bounds = fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.left >= root.left - 1f)
        assertTrue(bounds.right <= root.right + 1f)
        assertTrue(bounds.top >= root.top - 1f)
        assertTrue(bounds.bottom <= root.bottom + 1f)
    }
}
