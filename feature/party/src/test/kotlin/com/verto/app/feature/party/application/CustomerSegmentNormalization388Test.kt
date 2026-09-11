package com.verto.app.feature.party.application

import com.verto.app.feature.party.domain.model.CustomerSegment
import com.verto.app.feature.party.domain.model.customerSegmentFromStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomerSegmentNormalization388Test {
    @Test fun `approved customer taxonomy contains exactly six segments`() {
        assertEquals(
            listOf(
                CustomerSegment.INDIVIDUAL,
                CustomerSegment.COMPANY,
                CustomerSegment.WORKSHOP_OWNER,
                CustomerSegment.MARKETER,
                CustomerSegment.TRADER,
                CustomerSegment.DISTRIBUTOR,
            ),
            CustomerSegment.entries,
        )
    }

    @Test fun `legacy customer classifications normalize without restoring legacy enum values`() {
        assertEquals(CustomerSegment.INDIVIDUAL, customerSegmentFromStorage("OTHER"))
        assertEquals(CustomerSegment.INDIVIDUAL, customerSegmentFromStorage("CAR_OWNER"))
        assertEquals(CustomerSegment.COMPANY, customerSegmentFromStorage("INSTITUTION"))
        assertEquals(CustomerSegment.WORKSHOP_OWNER, customerSegmentFromStorage("MECHANIC"))
        assertEquals(CustomerSegment.TRADER, customerSegmentFromStorage("SHOP_OWNER"))
        assertEquals(CustomerSegment.TRADER, customerSegmentFromStorage("COMPETITOR"))
        assertEquals(CustomerSegment.DISTRIBUTOR, customerSegmentFromStorage("WHOLESALE_TRADER"))
        assertNull(customerSegmentFromStorage("ALIEN"))
    }
}
