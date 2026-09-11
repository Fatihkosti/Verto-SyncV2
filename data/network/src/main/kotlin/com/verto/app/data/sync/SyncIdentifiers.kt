package com.verto.app.data.sync

import com.verto.app.data.local.AppDatabase.Companion.CASH_CLIENT_UUID
import com.verto.app.data.local.AppDatabase.Companion.CASH_SUPPLIER_UUID
import java.util.UUID

/** كل مؤسسة لها UUID ثابت مستقل لعميلها النقدي. */
fun SyncRuntime.cashClientUuid(orgId: String): String =
    UUID.nameUUIDFromBytes("cash_client:$orgId".toByteArray()).toString()

fun SyncRuntime.cashSupplierUuid(orgId: String): String =
    UUID.nameUUIDFromBytes("cash_supplier:$orgId".toByteArray()).toString()

/** Pull: UUID البعيد إلى المعرّف المحلي المتوافق مع Room. */
fun SyncRuntime.resolveClientId(remoteId: String, orgId: String): String = when (remoteId) {
    CASH_CLIENT_UUID, cashClientUuid(orgId) -> "cash_client_main"
    CASH_SUPPLIER_UUID, cashSupplierUuid(orgId) -> "cash_supplier_main"
    else -> remoteId
}

/** Push: المعرّف المحلي إلى UUID صالح لعمود Supabase. */
fun SyncRuntime.localClientIdToUuid(localId: String, orgId: String): String = when (localId) {
    "cash_client_main", CASH_CLIENT_UUID -> cashClientUuid(orgId)
    "cash_supplier_main", CASH_SUPPLIER_UUID -> cashSupplierUuid(orgId)
    else -> localId
}
