package com.verto.app.feature.shipment.domain.policy

import com.verto.app.feature.shipment.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class LogisticsPoliciesTest {
    @Test fun `shipment lifecycle allows only declared transitions`() { assertTrue(LogisticsLifecyclePolicy.canTransition(LogisticsShipmentState.DRAFT,LogisticsShipmentState.READY)); assertFalse(LogisticsLifecyclePolicy.canTransition(LogisticsShipmentState.DRAFT,LogisticsShipmentState.CLOSED)); assertTrue(LogisticsLifecyclePolicy.nextStates(LogisticsShipmentState.CLOSED).isEmpty()) }
    @Test(expected=IllegalArgumentException::class) fun `arrived leg cannot be superseded`() { LogisticsLifecyclePolicy.requireCanSupersede(LogisticsLegStatus.ARRIVED) }
    @Test fun `source defaults to supplier custody before shipment starts`() { val a=aggregate(); val p=LogisticsCustodyResolver.currentForSource(a,a.sources.single()); assertEquals(LogisticsCustodyHolderType.SUPPLIER,p.holderType); assertEquals("supplier",p.holderId) }
    @Test fun `shipment wide handoff applies after start`() { val base=aggregate(startedAt=5); val h=LogisticsCustodyHandoff("h","org","s",null,null,LogisticsCustodyHolderType.SUPPLIER,"supplier","Supplier",LogisticsCustodyHolderType.LOGISTICS_PARTNER,"carrier","Carrier",10,11,"r"); val p=LogisticsCustodyResolver.currentForSource(base.copy(custodyHandoffs=listOf(h)),base.sources.single()); assertEquals("carrier",p.holderId) }
    @Test(expected=IllegalArgumentException::class) fun `foreign source is rejected`() { val a=aggregate(); LogisticsCustodyResolver.currentForSource(a,a.sources.single().copy(shipmentId="other")) }
    private fun aggregate(startedAt:Long?=null):LogisticsShipmentAggregate { val shipment=LogisticsShipment("s","org","S1","A","B",createdAt=1,startedAt=startedAt); val source=LogisticsShipmentSource("src","s","inv","supplier","Supplier","I1"); return LogisticsShipmentAggregate(shipment,sources=listOf(source)) }
}
