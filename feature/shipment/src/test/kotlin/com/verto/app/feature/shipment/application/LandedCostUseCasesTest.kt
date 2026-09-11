package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class LandedCostUseCasesTest {
    @Test fun `shipment landed cost uses accepted quantity only`() {
        val aggregate=baseAggregate().copy(
            lines=listOf(line("l1", BigDecimal("10")), line("l2", BigDecimal("20"))),
            costs=listOf(cost("c",BigDecimal("30"))),
            receivingBatches=listOf(batch(receiving("l1",accepted=2,received=5), receiving("l2",accepted=1,received=1)))
        )
        val allocations=CalculateShipmentLandedCostUseCase()(aggregate)
        assertEquals(BigDecimal("15"),allocations.first{it.shipmentLineId=="l1"}.amount)
        assertEquals(BigDecimal("15"),allocations.first{it.shipmentLineId=="l2"}.amount)
    }
    @Test fun `estimated costs are excluded`() {
        val aggregate=baseAggregate().copy(costs=listOf(cost("e",BigDecimal("99"),LogisticsCostStatus.ESTIMATED)))
        assertTrue(CalculateShipmentLandedCostUseCase()(aggregate).isEmpty())
    }
    @Test(expected=IllegalArgumentException::class) fun `actual cost without accepted basis is rejected`() {
        CalculateShipmentLandedCostUseCase()(baseAggregate().copy(costs=listOf(cost("c",BigDecimal.ONE))))
    }
    @Test fun `recovery cost is allocated and landed unit price includes allocation`() {
        val lines=listOf(recoveryLine("r","l1",2,BigDecimal("10")), recoveryLine("r","l2",1,BigDecimal("20")))
        val valued=CalculateRecoveryLandedCostUseCase()("r",lines,listOf(cost("c",BigDecimal("20"),recoveryId="r")))
        assertEquals(BigDecimal("10"),valued.first{it.shipmentLineId=="l1"}.economics.allocatedRecoveryCost)
        assertEquals(BigDecimal("15.00000000"),CalculateRecoveryLandedCostUseCase().landedUnitPrice(valued.first{it.shipmentLineId=="l1"}))
    }
    @Test(expected=IllegalArgumentException::class) fun `recovery rejects duplicate shipment line`() {
        val x=recoveryLine("r","l1",1,BigDecimal.ONE); CalculateRecoveryLandedCostUseCase()("r",listOf(x,x.copy(id="x2")),emptyList())
    }
    private fun baseAggregate()=LogisticsShipmentAggregate(LogisticsShipment("s","org","S","A","B",createdAt=1))
    private fun line(id:String,price:BigDecimal)=LogisticsShipmentLine(id,"s","inv","ii-$id","item-$id","Item",5,price)
    private fun receiving(lineId:String,accepted:Int,received:Int)=LogisticsReceivingLine("rec-$lineId","s",lineId,5,received,accepted,received-accepted,0,0)
    private fun batch(vararg lines:LogisticsReceivingLine)=LogisticsReceivingBatch("b","org","s","req",1,"u","U",lines.toList())
    private fun cost(id:String,amount:BigDecimal,status:LogisticsCostStatus=LogisticsCostStatus.ACTUAL,recoveryId:String?=null)=LogisticsCost(
        id=id, organizationId="org", shipmentId="s", type=LogisticsCostType.FREIGHT,
        amount=amount, currency="SDG", exchangeRateSnapshot=BigDecimal.ONE,
        baseCurrencyAmount=amount, status=status, recoveryId=recoveryId,
    )
    private fun recoveryLine(recoveryId:String,lineId:String,qty:Int,price:BigDecimal)=LogisticsRecoveryLine("org","rl-$lineId",recoveryId,"short-$lineId",lineId,qty,LogisticsRecoveryEconomics(price))
}
