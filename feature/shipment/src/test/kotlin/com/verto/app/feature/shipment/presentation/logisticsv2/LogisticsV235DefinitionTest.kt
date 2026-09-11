package com.verto.app.feature.shipment.presentation.logisticsv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogisticsV235DefinitionTest {
    @Test
    fun `definition requires country city and follow up employee`() {
        val validation = validateLogisticsShipmentDefinition(
            LogisticsShipmentHeaderDraft(shipmentId = "s1", shipmentNumber = "7805"),
        )

        assertFalse(validation.isValid)
        assertEquals("اكتب دولة الانطلاق", validation.originCountryError)
        assertEquals("اكتب مدينة الانطلاق", validation.originCityError)
        assertEquals("اكتب دولة الوصول", validation.destinationCountryError)
        assertEquals("اكتب مدينة الوصول", validation.destinationCityError)
        assertEquals("اختر مسؤول المتابعة", validation.employeeError)
    }

    @Test
    fun `definition rejects identical origin and destination`() {
        val validation = validateLogisticsShipmentDefinition(validDraft().copy(
            destination = LogisticsDefinitionLocationDraft("السودان", "بورتسودان"),
        ))

        assertFalse(validation.isValid)
        assertEquals("يجب أن تختلف نقطة الوصول عن نقطة الانطلاق", validation.destinationMatchError)
    }

    @Test
    fun `valid definition can continue and country is not part of legacy city display`() {
        val draft = validDraft()
        val validation = validateLogisticsShipmentDefinition(draft)

        assertTrue(validation.isValid)
        assertTrue(draft.canContinue)
        assertNull(validation.destinationMatchError)
        assertEquals("بورتسودان", draft.departureStation)
        assertEquals("أبوحمد", draft.finalArrivalStation)
    }

    @Test
    fun `missing generated shipment number has dedicated error`() {
        val validation = validateLogisticsShipmentDefinition(validDraft().copy(shipmentNumber = ""))
        assertEquals("تعذر إنشاء رقم الشحنة، حاول مجددًا", validation.shipmentNumberError)
    }

    private fun validDraft() = LogisticsShipmentHeaderDraft(
        shipmentId = "s1",
        shipmentNumber = "7805",
        origin = LogisticsDefinitionLocationDraft("السودان", "بورتسودان"),
        destination = LogisticsDefinitionLocationDraft("السودان", "أبوحمد"),
        employeeId = "e1",
        employeeName = "محمد",
    )
}
