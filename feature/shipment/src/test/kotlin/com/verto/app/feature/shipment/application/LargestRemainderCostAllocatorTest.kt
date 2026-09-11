package com.verto.app.feature.shipment.application

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class LargestRemainderCostAllocatorTest {
    @Test fun `allocations always sum to exact integral cost`() { val a=LargestRemainderCostAllocator.allocate(BigDecimal("10"),listOf(LargestRemainderCostAllocator.Basis("b",BigDecimal("1")),LargestRemainderCostAllocator.Basis("a",BigDecimal("1")),LargestRemainderCostAllocator.Basis("c",BigDecimal("1")))); assertEquals(BigDecimal("10"),a.fold(BigDecimal.ZERO){x,y->x+y.amount}); assertEquals(BigDecimal("4"),a.first{it.shipmentLineId=="a"}.amount) }
    @Test fun `decimal bases keep exact proportional weights`() { val a=LargestRemainderCostAllocator.allocate(BigDecimal("3"),listOf(LargestRemainderCostAllocator.Basis("a",BigDecimal("0.10")),LargestRemainderCostAllocator.Basis("b",BigDecimal("0.20")))); assertEquals(BigDecimal("1"),a.first{it.shipmentLineId=="a"}.amount); assertEquals(BigDecimal("2"),a.first{it.shipmentLineId=="b"}.amount) }
    @Test(expected=IllegalArgumentException::class) fun `fractional total cost is rejected`() { LargestRemainderCostAllocator.allocate(BigDecimal("1.5"), listOf(LargestRemainderCostAllocator.Basis("a",BigDecimal.ONE))) }
    @Test(expected=IllegalArgumentException::class) fun `duplicate line ids are rejected`() { LargestRemainderCostAllocator.allocate(BigDecimal.ONE,listOf(LargestRemainderCostAllocator.Basis("a",BigDecimal.ONE),LargestRemainderCostAllocator.Basis("a",BigDecimal.ONE))) }
    @Test fun `zero cost with no bases is valid`() { assertTrue(LargestRemainderCostAllocator.allocate(BigDecimal.ZERO,emptyList()).isEmpty()) }
}
