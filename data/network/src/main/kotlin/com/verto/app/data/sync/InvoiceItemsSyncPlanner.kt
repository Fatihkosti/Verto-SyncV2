package com.verto.app.data.sync

/**
 * منطق قرارات مزامنة بنود الفاتورة (الجلسة 5 من fixbacklog) — دالّة نقية قابلة للاختبار
 * مفصولة عن استدعاءات الشبكة في [SyncManager.pullInvoiceItems].
 *
 * القاعدة الحاكمة (منع فقدان بيانات): لا يُلمَس بند فاتورة محلية عليها تعديل غير مرفوع
 * (`isDirty`)، ولا يُحذف بند محلي غير مرفوع كـ«يتيم». الـ push يسبق الـ pull لكنه قد يفشل
 * (شبكة/RLS) أو يأتي pull من Realtime، فهذه الحرّاس شبكة أمان ضد فقدان التعديلات المحلية.
 */
internal object InvoiceItemsSyncPlanner {

    data class LocalItem(val id: String, val invoiceId: String, val isDirty: Boolean)

    /** فواتير لا تُلمَس بنودها: غير موجودة محلياً، أو عليها تعديل محلي غير مرفوع. */
    fun protectedInvoiceIds(
        existingInvoiceIds: Set<String>,
        dirtyInvoiceIds: Set<String>,
        candidateInvoiceId: String
    ): Boolean = candidateInvoiceId !in existingInvoiceIds || candidateInvoiceId in dirtyInvoiceIds

    /**
     * معرّفات البنود المحلية اليتيمة الواجب حذفها:
     *  - فاتورتها ضمن المسحوبة فعلياً (`pulledInvoiceIds`)،
     *  - والبند **غير** متسخ (`!isDirty`) — البند المحلي غير المرفوع محمي،
     *  - ولم يَعُد موجوداً على السيرفر لتلك الفاتورة.
     */
    fun staleItemIdsToDelete(
        localItems: List<LocalItem>,
        pulledInvoiceIds: Set<String>,
        remoteIdsByInvoice: Map<String, Set<String>>
    ): List<String> =
        localItems.filter {
            it.invoiceId in pulledInvoiceIds &&
                !it.isDirty &&
                it.id !in (remoteIdsByInvoice[it.invoiceId] ?: emptySet())
        }.map { it.id }
}
