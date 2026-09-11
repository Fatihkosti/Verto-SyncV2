package com.verto.app.data.repository

import com.verto.app.utils.cashOutAllowedMinor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseAtomicity334Test {
    private data class Ledger(var expenseWrites:Int=0,var cashMinor:Long=0L,var outboxWrites:Int=0,var lifecycle:String="ACTIVE",val refunds:MutableList<String> = mutableListOf())

    private suspend fun <T> transaction(state:Ledger,block:suspend()->T):T {
        val snapshot=state.copy(refunds=state.refunds.toMutableList())
        return try { block() } catch(error:Throwable) {
            state.expenseWrites=snapshot.expenseWrites; state.cashMinor=snapshot.cashMinor; state.outboxWrites=snapshot.outboxWrites
            state.lifecycle=snapshot.lifecycle; state.refunds.clear(); state.refunds.addAll(snapshot.refunds); throw error
        }
    }

    @Test fun `insert expense commits expense cash and outbox together`()=runBlocking {
        val state=Ledger()
        val row=runAuthorizedExpenseWrite(authorize={},transaction={ block -> transaction(state,block) }) {
            insertExpenseEffects(
                persist={ state.expenseWrites++; 7L },
                cash={ state.cashMinor-=1010L },
                outbox={ state.outboxWrites++ },
            )
        }
        assertEquals(7L,row); assertEquals(1,state.expenseWrites); assertEquals(-1010L,state.cashMinor); assertEquals(1,state.outboxWrites)
    }

    @Test fun `cash failure rolls expense back`()=runBlocking {
        val state=Ledger()
        runCatching {
            runAuthorizedExpenseWrite(authorize={},transaction={ block -> transaction(state,block) }) {
                insertExpenseEffects(persist={state.expenseWrites++;1L},cash={error("cash failure")},outbox={state.outboxWrites++})
            }
        }
        assertEquals(Ledger(),state)
    }

    @Test fun `outbox failure rolls expense and cash back`()=runBlocking {
        val state=Ledger()
        runCatching {
            runAuthorizedExpenseWrite(authorize={},transaction={ block -> transaction(state,block) }) {
                insertExpenseEffects(persist={state.expenseWrites++;1L},cash={state.cashMinor-=1010L},outbox={error("outbox failure")})
            }
        }
        assertEquals(Ledger(),state)
    }

    @Test fun `update increase and decrease use exact minor delta and update refund identity`()=runBlocking {
        var cashOut=0L; var refund=0L; var source=""
        applyExpenseAmountDelta(1000L,1010L,{cashOut+=it},{amount,kind->refund+=amount;source=kind})
        assertEquals(10L,cashOut); assertEquals(0L,refund)
        applyExpenseAmountDelta(1010L,1000L,{cashOut+=it},{amount,kind->refund+=amount;source=kind})
        assertEquals(10L,refund); assertEquals(EXPENSE_UPDATE_REFUND_SOURCE,source)
    }

    @Test fun `void refunds once and update refund remains semantically distinct`()=runBlocking {
        val state=Ledger()
        applyExpenseAmountDelta(1010L,1000L,{}, {amount,source->assertEquals(10L,amount);state.refunds+=source})
        suspend fun voidOnce():Boolean = voidExpenseEffects(
            current=state.lifecycle, isVoided={it=="VOID"},
            refund={state.refunds+=EXPENSE_VOID_REFUND_SOURCE}, toVoided={"VOID"},
            commitVoid={state.lifecycle=it;state.outboxWrites++},
        )
        assertTrue(voidOnce()); assertFalse(voidOnce())
        assertEquals(listOf(EXPENSE_UPDATE_REFUND_SOURCE,EXPENSE_VOID_REFUND_SOURCE),state.refunds)
        assertEquals(1,state.outboxWrites)
    }

    @Test fun `permission denial prevents transaction and writes`()=runBlocking {
        val state=Ledger(); var transactionEntered=false
        val result=runCatching {
            runAuthorizedExpenseWrite(
                authorize={throw SecurityException("denied")},
                transaction={ block -> transactionEntered=true; transaction(state,block) },
            ) { insertExpenseEffects(persist={state.expenseWrites++;1L},cash={state.cashMinor--},outbox={state.outboxWrites++}) }
        }
        assertTrue(result.isFailure); assertFalse(transactionEntered); assertEquals(Ledger(),state)
    }

    @Test fun `overdraft decision is fixed point minor units`() {
        assertFalse(cashOutAllowedMinor(1009L,1010L,false))
        assertTrue(cashOutAllowedMinor(1010L,1010L,false))
        assertTrue(cashOutAllowedMinor(0L,1010L,true))
    }

    @Test(expected=IllegalArgumentException::class) fun `zero expense is rejected`() { requirePositiveExpenseMinor(0L) }
    @Test(expected=IllegalArgumentException::class) fun `negative expense is rejected`() { requirePositiveExpenseMinor(-1L) }
}
