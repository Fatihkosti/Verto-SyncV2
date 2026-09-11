package com.verto.app.feature.shipment.presentation.logisticsv2

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.verto.app.feature.shipment.domain.model.LogisticsLegTransportMode
import com.verto.app.ui.theme.VertoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LogisticsV242UiQualityTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun transportChoice_isOneSelectableTalkBackTarget_withMinimumTouchHeight() {
        compose.setContent {
            VertoTheme {
                Row(Modifier.width(320.dp)) {
                    V237TransportChoice(
                        label = "بري",
                        icon = Icons.Outlined.LocalShipping,
                        mode = LogisticsLegTransportMode.ROAD,
                        selectedMode = LogisticsLegTransportMode.ROAD,
                        selectionConfirmed = true,
                        enabled = true,
                        modifier = Modifier.weight(1f),
                        onClick = {},
                    )
                }
            }
        }

        val node = compose.onNodeWithText("بري")
            .assertIsSelected()
            .assertHasClickAction()
            .fetchSemanticsNode()

        assertTrue("Transport choice must remain at least a 48px touch target", node.boundsInRoot.height >= 48f)
    }

    @Test
    fun logisticsScreen_remainsScrollableAt320dp_withFontScale2_andForcesRtl() {
        compose.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = baseDensity.density,
                    fontScale = 2f,
                ),
            ) {
                VertoTheme {
                    Box(Modifier.width(320.dp).height(640.dp)) {
                        LogisticsV2Screen(
                            title = "تفاصيل الشحنة",
                            subtitle = "S-242",
                            onBack = {},
                        ) {
                            Text(
                                if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
                                    "rtl-ok"
                                } else {
                                    "rtl-fail"
                                },
                            )
                            repeat(12) { index ->
                                LogisticsSection("قسم ${index + 1}") {
                                    Text("محتوى طويل لاختبار الخط الكبير")
                                }
                            }
                            Text("آخر عنصر")
                        }
                    }
                }
            }
        }

        compose.onNodeWithText("تفاصيل الشحنة").assertIsDisplayed()
        compose.onNodeWithText("rtl-ok").assertIsDisplayed()
        compose.onNodeWithText("آخر عنصر").performScrollTo().assertIsDisplayed()
    }
}
