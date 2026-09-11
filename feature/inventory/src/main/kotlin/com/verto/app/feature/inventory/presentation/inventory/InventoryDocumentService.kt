package com.verto.app.feature.inventory.presentation.inventory

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.verto.app.core.audit.domain.AuditTable
import com.verto.app.core.audit.domain.WriteAuditPort
import com.verto.app.core.export.domain.DocumentSharePort
import com.verto.app.feature.inventory.application.InventoryApplicationService
import com.verto.app.feature.inventory.application.model.InventoryItemView
import com.verto.app.pdf.InventoryExportType
import com.verto.app.pdf.generateInventoryPdf
import com.verto.app.pdf.generateSlowMovingInventoryPdf
import com.verto.app.utils.InvoiceFont
import com.verto.app.utils.InvoiceTemplate
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException

internal class InventoryDocumentService(
    private val repo: InventoryApplicationService,
    private val auditLogger: WriteAuditPort,
    private val documentSharePort: DocumentSharePort
) {
    fun exportCsv(context: Context, items: List<InventoryItemView>): Result<Uri> = runCatching {
        val fileName = "verto_inventory_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}.csv"
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = java.io.File(dir, fileName)
        val csv = buildString {
            appendLine("الاسم,سعر الشراء,سعر البيع,الكمية,الحد الأدنى,الرقم التسلسلي,الموقع,ملاحظة")
            items.forEach { item ->
                appendLine("\"${item.name}\",${item.buyPrice},${item.sellPrice},${item.quantity},${item.minQuantity},\"${item.partNumber}\",\"${item.location}\",\"${item.note}\"")
            }
        }
        file.writeText(csv, Charsets.UTF_8)
        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    suspend fun importCsv(context: Context, uri: Uri): String = try {
        val stream = context.contentResolver.openInputStream(uri) ?: return "تعذّر فتح الملف"
        val lines = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readLines().drop(1) }
        val existing = repo.getAllItemsSync().associateBy { it.name }
        val now = System.currentTimeMillis()
        var importedCount = 0
        var errorCount = 0
        lines.forEach { line ->
            if (line.isBlank()) return@forEach
            val parts = parseCsvLine(line)
            if (parts.size < 5) return@forEach
            val name = parts[0].trim()
            if (name.isBlank()) return@forEach
            val buyPrice = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
            val sellPrice = parts.getOrNull(2)?.toDoubleOrNull() ?: 0.0
            val qty = parts.getOrNull(3)?.toIntOrNull() ?: 0
            val minQty = parts.getOrNull(4)?.toIntOrNull() ?: 5
            val partNum = parts.getOrNull(5)?.trim() ?: ""
            val location = parts.getOrNull(6)?.trim() ?: ""
            val note = parts.getOrNull(7)?.trim() ?: ""
            val existingItem = existing[name]
            if (existingItem != null) {
                val updated = existingItem.copy(
                    buyPrice = buyPrice,
                    sellPrice = sellPrice,
                    minQuantity = minQty,
                    partNumber = partNum,
                    location = location,
                    note = note,
                    updatedAt = now
                )
                repo.saveItem(updated)
                repo.syncUnitItemPrice(updated)
                if (existingItem.quantity != qty) {
                    val adjustResult = repo.adjustStock(existingItem.id, qty, "استيراد CSV")
                    if (adjustResult.isFailure) {
                        errorCount++
                        return@forEach
                    }
                    auditLogger.logUpdate(
                        table = AuditTable.INVENTORY,
                        recordId = existingItem.id,
                        summary = "استيراد CSV: ${existingItem.name} — ${existingItem.quantity} → $qty",
                        oldValue = "{\"quantity\":${existingItem.quantity}}",
                        newValue = "{\"quantity\":$qty}"
                    )
                }
            } else {
                repo.saveItem(
                    InventoryItemView(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        buyPrice = buyPrice,
                        sellPrice = sellPrice,
                        quantity = qty,
                        minQuantity = minQty,
                        partNumber = partNum,
                        location = location,
                        note = note,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
            importedCount++
        }
        buildString {
            append("تم استيراد $importedCount صنف بنجاح")
            if (errorCount > 0) append(" ($errorCount تجاهلها بسبب خطأ)")
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        "تعذّر استيراد الملف — تأكد أن الملف سليم وبنفس صيغة التصدير"
    }

    suspend fun exportSlowMovingPdf(
        context: Context,
        days: Int,
        template: InvoiceTemplate,
        font: InvoiceFont,
        fontSize: Int
    ): String? {
        return try {
        val sinceTimestamp = System.currentTimeMillis() - days.toLong() * 24 * 60 * 60 * 1000
        val slowItems = repo.getSlowMovingItemsSync(sinceTimestamp)
        if (slowItems.isEmpty()) return "لا يوجد مخزون راكد خلال $days يوم"
        val file = generateSlowMovingInventoryPdf(context, slowItems, days, template, font, fontSize)
        documentSharePort.sharePdf(file)
        null
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        "تعذّر إنشاء ملف PDF، حاول مرة أخرى"
        }
    }

    suspend fun exportPdf(
        context: Context,
        items: List<InventoryItemView>,
        exportType: InventoryExportType,
        categoryFilter: String,
        categoriesByItem: Map<String, List<String>>,
        template: InvoiceTemplate,
        font: InvoiceFont,
        fontSize: Int
    ): String? {
        return try {
        val filteredItems = if (categoryFilter == "الكل") items else items.filter {
            categoriesByItem[it.id]?.contains(categoryFilter) == true
        }
        if (filteredItems.isEmpty()) return "لا توجد أصناف في تصنيف: $categoryFilter"
        val file = generateInventoryPdf(context, filteredItems, exportType, categoryFilter, template, font, fontSize)
        documentSharePort.sharePdf(file)
        null
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        "تعذّر إنشاء ملف PDF، حاول مرة أخرى"
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var inQuote = false
        val current = StringBuilder()
        line.forEach { ch ->
            when {
                ch == '"' -> inQuote = !inQuote
                ch == ',' && !inQuote -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        result.add(current.toString())
        return result
    }
}
