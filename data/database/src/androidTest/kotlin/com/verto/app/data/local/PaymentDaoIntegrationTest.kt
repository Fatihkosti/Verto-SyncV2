package com.verto.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.verto.app.data.local.entity.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaymentDaoIntegrationTest {
    private lateinit var db:AppDatabase
    @Before fun createDb(){ val context=ApplicationProvider.getApplicationContext<Context>(); db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).allowMainThreadQueries().build() }
    @After fun closeDb(){db.close()}
    @Test fun negativeReversalParticipatesInTotalPaidAndCanBeFound() = runTest {
        db.clientDao().insertClient(PartyIdentityEntity(id="c",name="Client",phone="249"))
        db.invoiceDao().insertInvoice(InvoiceEntity(id="i",invoiceNumber=10,clientId="c",type=InvoiceType.GOODS,description="sale",totalAmount=100.0,dueDate=0L,status=InvoiceStatus.CLOSED_CREDIT))
        val original=PaymentEntity(id="p",invoiceId="i",clientId="c",amount=40.0,paymentMethod=PaymentMethod.CASH)
        val reversal=PaymentEntity(id="r",invoiceId="i",clientId="c",amount=-40.0,paymentMethod=PaymentMethod.CASH,reversedPaymentId="p")
        db.paymentDao().insertPayment(original); db.paymentDao().insertPayment(reversal)
        assertEquals(0.0,db.paymentDao().getTotalPaidForInvoiceSync("i"),0.0)
        assertEquals("r",db.paymentDao().getReversalForPaymentSync("p")?.id)
    }
    @Test fun duplicatePrimaryKeyIsIgnored() = runTest {
        db.clientDao().insertClient(PartyIdentityEntity(id="c",name="Client",phone="249"))
        db.invoiceDao().insertInvoice(InvoiceEntity(id="i",invoiceNumber=10,clientId="c",type=InvoiceType.GOODS,description="sale",totalAmount=100.0,dueDate=0L))
        val p=PaymentEntity(id="p",invoiceId="i",clientId="c",amount=10.0,paymentMethod=PaymentMethod.CASH)
        assertTrue(db.paymentDao().insertPayment(p)>0); assertEquals(-1L,db.paymentDao().insertPayment(p))
    }
}
