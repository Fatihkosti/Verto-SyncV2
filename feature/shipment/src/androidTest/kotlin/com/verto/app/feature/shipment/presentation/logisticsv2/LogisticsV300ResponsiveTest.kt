package com.verto.app.feature.shipment.presentation.logisticsv2

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentSource
import com.verto.app.ui.theme.VertoTheme
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LogisticsV300ResponsiveTest {
    @get:Rule
    val compose = createComposeRule()

    @Test fun selectedInvoiceCard_available320_stacks() = verifyCase(320.dp, 1.0f, expectedStack = true)
    @Test fun selectedInvoiceCard_available360_staysInline() = verifyCase(360.dp, 1.0f, expectedStack = false)
    @Test fun selectedInvoiceCard_available412_staysInline() = verifyCase(412.dp, 1.0f, expectedStack = false)
    @Test fun selectedInvoiceCard_available412_largeFont_stacks() = verifyCase(412.dp, 1.5f, expectedStack = true)

    private fun verifyCase(availableWidth: Dp, fontScale: Float, expectedStack: Boolean) {
        val source = LogisticsShipmentSource(
            id = "source-300",
            shipmentId = "shipment-300",
            invoiceId = "invoice-300",
            supplierId = "supplier-300",
            supplierNameSnapshot = "Supplier 300",
            invoiceNumberSnapshot = "INV-300",
            plannedPackageCount = 3,
            plannedWeightKg = BigDecimal("12.5"),
        )
        compose.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale = fontScale),
            ) {
                VertoTheme {
                    // SelectedInvoicePlanningCard adds 12dp horizontal padding on each side.
                    // This makes BoxWithConstraints receive exactly availableWidth.
                    Box(Modifier.width(availableWidth + 24.dp)) {
                        SelectedInvoicePlanningCard(
                            source = source,
                            canManage = true,
                            showValidation = false,
                            actions = InvoicePlanningCardActions({}, { _ -> }, { _ -> }, {}),
                        )
                    }
                }
            }
        }

        compose.onNodeWithText("INV-300", substring = true).assertIsDisplayed()
        val cartons = compose.onNodeWithText("3 كرتونة").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val weight = compose.onNodeWithText("12.5 كجم").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("الجاهزية المتوقعة").assertIsDisplayed().assertHasClickAction()

        val verticalOverlap = cartons.top < weight.bottom && weight.top < cartons.bottom
        assertEquals("Row/Column branch must match the pre-v300 responsive rule", !expectedStack, verticalOverlap)
        assertTrue("Both metrics must remain laid out", cartons.width > 0f && weight.width > 0f)
    }
}
