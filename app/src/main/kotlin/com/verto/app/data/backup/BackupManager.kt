package com.verto.app.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.sync.UnifiedOutboxWriter
import com.verto.app.data.local.entity.*
import com.verto.app.utils.PreferencesManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStreamReader
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.KeySpec
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

// ── هيكل النسخة الاحتياطية v6 ─────────────────────────
@Serializable
data class BackupData(
    val version: Int = 6,
    val exportedAt: Long = 0L,
    val checksum: String = "",
    val organizationId: String = "",
    val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
    val appVersionCode: Int = 1,
    // v1
    val clients: List<PartyIdentityEntity>                 = emptyList(),
    // v6 — Party V2 classification is backed up explicitly; legacy clientType is no longer authority.
    val partyRoles: List<PartyRoleEntity>                  = emptyList(),
    val customerProfiles: List<CustomerProfileEntity>      = emptyList(),
    val supplierProfiles: List<SupplierProfileEntity>      = emptyList(),
    val invoices: List<InvoiceEntity>                      = emptyList(),
    val invoiceItems: List<InvoiceItemEntity>              = emptyList(),
    val payments: List<PaymentEntity>                      = emptyList(),
    val expenses: List<ExpenseEntity>                      = emptyList(),
    val notes: List<NoteEntity>                            = emptyList(),
    // v2
    val clientReminders: List<ClientReminderEntity>        = emptyList(),
    // v3
    val inventoryItems: List<InventoryItemEntity>          = emptyList(),
    val inventoryMovements: List<InventoryMovementEntity>  = emptyList(),
    val expenseTarget: Double                              = 0.0,
    // v4
    val cashRegister: CashRegisterEntity?                  = null,
    val cashMovements: List<CashRegisterMovementEntity>    = emptyList(),
    val auditLogs: List<AuditLogEntity>                    = emptyList(),
    val inventoryUnits: List<InventoryUnitEntity>          = emptyList(),
    val itemCategories: List<ItemCategoryEntity>           = emptyList(),
    val categories: List<CategoryEntity>                   = emptyList(),
    // v5 — price-list templates reference live inventory; no copied prices/items.
    val priceListTemplates: List<PriceListTemplateEntity> = emptyList(),
    val priceListTemplateItems: List<PriceListTemplateItemEntity> = emptyList()
)

// ── نتيجة فحص الباكب قبل الاستيراد ───────────────────
data class BackupPreview(
    val version: Int,
    val exportedAt: Long,
    val clientCount: Int,
    val invoiceCount: Int,
    val paymentCount: Int,
    val expenseCount: Int,
    val inventoryItemCount: Int,
    val isEncrypted: Boolean
)

private val backupJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private const val ENCRYPTED_HEADER = "VERTO-ENCRYPTED-V1"
private const val PBKDF2_ITERATIONS = 100_000
private const val KEY_LENGTH_BITS = 256
private const val GCM_TAG_BITS = 128
private const val SALT_BYTES = 16
private const val IV_BYTES = 12
private const val BACKUP_SCHEMA_VERSION = 40
private const val MAX_BACKUP_ROWS_PER_TABLE = 250_000

// ── تشفير / فك تشفير ──────────────────────────────────
private object BackupCrypto {

    fun encrypt(data: ByteArray, password: String): ByteArray {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val iv   = ByteArray(IV_BYTES).also   { SecureRandom().nextBytes(it) }
        val key  = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        val encrypted = cipher.doFinal(data)
        // format: salt(16) + iv(12) + ciphertext
        return salt + iv + encrypted
    }

    fun decrypt(data: ByteArray, password: String): ByteArray {
        val salt      = data.copyOfRange(0, SALT_BYTES)
        val iv        = data.copyOfRange(SALT_BYTES, SALT_BYTES + IV_BYTES)
        val encrypted = data.copyOfRange(SALT_BYTES + IV_BYTES, data.size)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        return cipher.doFinal(encrypted)
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val bytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }
}

// ── حساب checksum ──────────────────────────────────────
@Serializable
private data class LegacyBackupChecksumDataV5(
    val version: Int,
    val exportedAt: Long,
    val checksum: String = "",
    val organizationId: String,
    val schemaVersion: Int,
    val appVersionCode: Int,
    val clients: List<PartyIdentityEntity>,
    val invoices: List<InvoiceEntity>,
    val invoiceItems: List<InvoiceItemEntity>,
    val payments: List<PaymentEntity>,
    val expenses: List<ExpenseEntity>,
    val notes: List<NoteEntity>,
    val clientReminders: List<ClientReminderEntity>,
    val inventoryItems: List<InventoryItemEntity>,
    val inventoryMovements: List<InventoryMovementEntity>,
    val expenseTarget: Double,
    val cashRegister: CashRegisterEntity?,
    val cashMovements: List<CashRegisterMovementEntity>,
    val auditLogs: List<AuditLogEntity>,
    val inventoryUnits: List<InventoryUnitEntity>,
    val itemCategories: List<ItemCategoryEntity>,
    val categories: List<CategoryEntity>,
    val priceListTemplates: List<PriceListTemplateEntity>,
    val priceListTemplateItems: List<PriceListTemplateItemEntity>,
)

private fun sha256Hex(json: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(json.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

private fun computeLegacyBackupChecksum(data: BackupData): String {
    val legacy = LegacyBackupChecksumDataV5(
        version = data.version,
        exportedAt = data.exportedAt,
        organizationId = data.organizationId,
        schemaVersion = data.schemaVersion,
        appVersionCode = data.appVersionCode,
        clients = data.clients,
        invoices = data.invoices,
        invoiceItems = data.invoiceItems,
        payments = data.payments,
        expenses = data.expenses,
        notes = data.notes,
        clientReminders = data.clientReminders,
        inventoryItems = data.inventoryItems,
        inventoryMovements = data.inventoryMovements,
        expenseTarget = data.expenseTarget,
        cashRegister = data.cashRegister,
        cashMovements = data.cashMovements,
        auditLogs = data.auditLogs,
        inventoryUnits = data.inventoryUnits,
        itemCategories = data.itemCategories,
        categories = data.categories,
        priceListTemplates = data.priceListTemplates,
        priceListTemplateItems = data.priceListTemplateItems,
    )
    return sha256Hex(backupJson.encodeToString(legacy))
}

internal fun computeBackupChecksum(data: BackupData): String {
    if (data.version in 4..5) return computeLegacyBackupChecksum(data)
    val withoutChecksum = data.copy(checksum = "")
    return sha256Hex(backupJson.encodeToString(withoutChecksum))
}

internal fun BackupData.withComputedChecksum(): BackupData =
    copy(checksum = computeBackupChecksum(this))

/** تحقق كامل قبل بدء معاملة الاستعادة، لذلك لا تُمس البيانات الحالية عند ملف تالف. */
internal fun validateBackupData(data: BackupData, currentOrgId: String) {
    require(data.version in 1..6) { "إصدار الباكب (${data.version}) غير مدعوم." }
    require(data.schemaVersion in 1..BACKUP_SCHEMA_VERSION) {
        "نسخة قاعدة بيانات الباكب (${data.schemaVersion}) غير مدعومة."
    }
    if (data.organizationId.isNotBlank() && currentOrgId.isNotBlank() && data.organizationId != currentOrgId) {
        error("هذه النسخة الاحتياطية تخص مؤسسة مختلفة.")
    }
    if (data.version >= 4) {
        require(data.checksum.isNotBlank()) { "الباكب v4 بلا checksum." }
        require(computeBackupChecksum(data) == data.checksum) {
            "الباكب تالف أو معدَّل (checksum غير مطابق)."
        }
    }

    fun requireUnique(label: String, ids: List<String>) {
        require(ids.none { it.isBlank() }) { "$label يحتوي معرّفًا فارغًا." }
        require(ids.size == ids.toSet().size) { "$label يحتوي معرّفات مكررة." }
        require(ids.size <= MAX_BACKUP_ROWS_PER_TABLE) { "$label يتجاوز حد الاستعادة الآمن." }
    }
    requireUnique("العملاء", data.clients.map { it.id })
    requireUnique("الفواتير", data.invoices.map { it.id })
    requireUnique("بنود الفواتير", data.invoiceItems.map { it.id })
    requireUnique("المدفوعات", data.payments.map { it.id })
    requireUnique("المصروفات", data.expenses.map { it.id })
    requireUnique("الملاحظات", data.notes.map { it.id })
    requireUnique("التنبيهات", data.clientReminders.map { it.id })
    requireUnique("أصناف المخزون", data.inventoryItems.map { it.id })
    requireUnique("قوالب كشف الأسعار", data.priceListTemplates.map { it.id })
    requireUnique("حركات المخزون", data.inventoryMovements.map { it.id })
    requireUnique("وحدات المخزون", data.inventoryUnits.map { it.id })
    requireUnique("تصنيفات الأصناف", data.itemCategories.map { it.id })
    requireUnique("التصنيفات", data.categories.map { it.id })
    requireUnique("حركات الصندوق", data.cashMovements.map { it.id })
    requireUnique("سجل التدقيق", data.auditLogs.map { it.id })

    val clientIds = data.clients.mapTo(mutableSetOf()) { it.id }
    if (data.version >= 6) {
        requireUnique("أدوار العملاء", data.partyRoles.map { "${it.organizationId}:${it.partyId}:${it.role}" })
        requireUnique("ملفات العملاء", data.customerProfiles.map { "${it.organizationId}:${it.partyId}" })
        requireUnique("ملفات الموردين", data.supplierProfiles.map { "${it.organizationId}:${it.partyId}" })
        require(data.partyRoles.all { it.partyId in clientIds }) { "دور Party يشير إلى عميل غير موجود." }
        require(data.customerProfiles.all { it.partyId in clientIds }) { "ملف عميل يشير إلى Party غير موجود." }
        require(data.supplierProfiles.all { it.partyId in clientIds }) { "ملف مورد يشير إلى Party غير موجود." }
        if (data.organizationId.isNotBlank()) {
            require(data.partyRoles.all { it.organizationId == data.organizationId }) { "دور Party يخص مؤسسة مختلفة." }
            require(data.customerProfiles.all { it.organizationId == data.organizationId }) { "ملف عميل يخص مؤسسة مختلفة." }
            require(data.supplierProfiles.all { it.organizationId == data.organizationId }) { "ملف مورد يخص مؤسسة مختلفة." }
        }
    }
    val invoiceIds = data.invoices.mapTo(mutableSetOf()) { it.id }
    val inventoryIds = data.inventoryItems.mapTo(mutableSetOf()) { it.id }
    require(data.invoices.all { it.clientId in clientIds }) { "فاتورة تشير إلى عميل غير موجود." }
    require(data.invoiceItems.all { it.invoiceId in invoiceIds }) { "بند يشير إلى فاتورة غير موجودة." }
    require(data.payments.all { it.invoiceId in invoiceIds && it.clientId in clientIds }) {
        "دفعة تشير إلى فاتورة أو عميل غير موجود."
    }
    require(data.notes.all { it.clientId in clientIds }) { "ملاحظة تشير إلى عميل غير موجود." }
    require(data.clientReminders.all { it.clientId in clientIds }) { "تنبيه يشير إلى عميل غير موجود." }
    require(data.inventoryMovements.all { it.itemId in inventoryIds }) { "حركة مخزون تشير إلى صنف غير موجود." }
    require(data.itemCategories.all { it.itemId in inventoryIds }) { "تصنيف يشير إلى صنف غير موجود." }
    val priceTemplateIds = data.priceListTemplates.mapTo(mutableSetOf()) { it.id }
    require(data.priceListTemplateItems.all { it.templateId in priceTemplateIds && it.inventoryItemId in inventoryIds }) {
        "قالب كشف أسعار يشير إلى قالب أو صنف غير موجود."
    }

    val numbers = buildList {
        add(data.expenseTarget)
        addAll(data.invoices.map { it.totalAmount })
        addAll(data.invoiceItems.flatMap { listOf(it.buyPrice, it.sellPrice, it.totalPrice, it.adjustedPurchasePrice) })
        addAll(data.payments.map { it.amount })
        addAll(data.expenses.map { it.amount })
        addAll(data.inventoryItems.flatMap { listOf(it.buyPrice, it.sellPrice, it.quantityPerUnit) })
        addAll(data.inventoryMovements.map { it.unitPrice })
        addAll(data.cashMovements.flatMap { listOf(it.amount, it.balanceBefore, it.balanceAfter) })
        data.cashRegister?.let { add(it.balance) }
    }
    require(numbers.all(Double::isFinite)) { "الباكب يحتوي قيمة مالية غير صالحة." }
    require(data.invoiceItems.all { it.quantity > 0 }) { "الباكب يحتوي كمية بند غير صالحة." }
}

class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val prefs: PreferencesManager,
    private val sessionReader: SessionReader,
    private val outbox: UnifiedOutboxWriter,
) {
    // ── تصدير ومشاركة ──────────────────────────────────────────────────────
    suspend fun exportAndShare(shareTarget: ShareTarget, password: String? = null) {
        val rawData = collectAllData()
        val data = rawData.withComputedChecksum()

        val json = backupJson.encodeToString(data)
        val encryptionPassword = password?.takeIf { it.isNotBlank() } ?: deviceBackupSecret()
        val encrypted = BackupCrypto.encrypt(json.toByteArray(Charsets.UTF_8), encryptionPassword)
        val fileContent = "$ENCRYPTED_HEADER\n${Base64.encodeToString(encrypted, Base64.NO_WRAP)}"

        val file = saveToFile(fileContent, "vrtbak")
        shareFile(file, shareTarget)
    }

    // ── فحص الباكب قبل الاستيراد (للعرض على المستخدم) ─────────────────────
    suspend fun previewBackup(uri: Uri, password: String? = null): Result<BackupPreview> = runCatching {
        val text = readText(uri)
        val data = parseBackup(text, password)
        BackupPreview(
            version           = data.version,
            exportedAt        = data.exportedAt,
            clientCount       = data.clients.size,
            invoiceCount      = data.invoices.size,
            paymentCount      = data.payments.size,
            expenseCount      = data.expenses.size,
            inventoryItemCount = data.inventoryItems.size,
            isEncrypted       = text.startsWith(ENCRYPTED_HEADER)
        )
    }

    // ── استيراد ─────────────────────────────────────────────────────────────
    suspend fun importFromUri(uri: Uri, password: String? = null): Result<String> = runCatching {
        val text = readText(uri)
        restoreBackupData(parseBackup(text, password))
    }

    /** نقطة اختبار داخلية تستخدم نفس مسار الإنتاج، بلا Uri أو FileProvider. */
    internal suspend fun restoreBackupData(data: BackupData): String {
        val currentOrgId = sessionReader.snapshot().organization.id.trim().also {
            require(it.isNotBlank()) { "FAIL_ORG_SCOPE" }
        }
        validateBackupData(data, currentOrgId)
        val restoreBatchId = "backup-restore:${data.checksum.ifBlank { computeBackupChecksum(data) }}"

        db.withTransaction {
            clearAllData()

            val restoredPartyRows = if (data.version >= 6) {
                RestoredPartyRows(data.partyRoles, data.customerProfiles, data.supplierProfiles)
            } else {
                legacyBackupPartyRows(data.clients, currentOrgId)
            }

            data.clients.forEach {
                db.clientDao().insertClient(
                    it.copy(legacyClientTypeTombstone = "", isDirty = true)
                )
            }
            restoredPartyRows.roles.forEach {
                db.partyRoleDao().upsertRoleFromRemote(it.copy(organizationId = currentOrgId, dirty = true))
            }
            restoredPartyRows.customers.forEach {
                db.partyRoleDao().saveCustomerProfile(it.copy(organizationId = currentOrgId, dirty = true))
            }
            restoredPartyRows.suppliers.forEach {
                db.partyRoleDao().saveSupplierProfile(it.copy(organizationId = currentOrgId, dirty = true))
            }

            data.invoices.forEach          { db.invoiceDao().insertInvoice(it.copy(isDirty = true)) }
            if (data.invoiceItems.isNotEmpty())
                db.invoiceDao().insertInvoiceItems(data.invoiceItems.map { it.copy(isDirty = true) })
            data.payments.forEach          { db.paymentDao().insertPayment(it.copy(isDirty = true)) }
            data.expenses.forEach          { db.expenseDao().insertExpense(it.copy(isDirty = true)) }
            data.notes.forEach             { db.noteDao().insertNote(it) }

            if (data.version >= 2) {
                data.clientReminders.forEach { db.clientReminderDao().insertReminder(it) }
            }
            if (data.version >= 3) {
                data.inventoryItems.forEach     { db.inventoryDao().insertItem(it.copy(isDirty = true)) }
                data.inventoryMovements.forEach { db.inventoryDao().insertMovement(it) }
            }
            if (data.version >= 4) {
                data.inventoryUnits.forEach { db.inventoryUnitDao().insertUnit(it) }
                data.itemCategories.forEach { db.itemCategoryDao().insertCategory(it) }
                data.categories.forEach     { db.categoryDao().insertCategory(it) }
                // سجل التدقيق append-only؛ IGNORE يمنع تكرار المعرف الموجود ولا يحذف التاريخ المحلي.
                data.auditLogs.forEach      { db.auditLogDao().insert(it) }
                data.cashRegister?.let      { db.cashRegisterDao().upsertRegister(it) }
                data.cashMovements.forEach  { db.cashRegisterDao().insertMovement(it) }
            }
            if (data.version >= 5) {
                data.priceListTemplates.forEach { db.priceListDao().upsertTemplate(it.copy(organizationId = currentOrgId)) }
                if (data.priceListTemplateItems.isNotEmpty()) {
                    db.priceListDao().upsertTemplateItems(data.priceListTemplateItems)
                }
            }

            // Session 307 IMPORT_TO_SYNC capture. Deterministic identities make a repeated restore idempotent.
            var commandOrder = 0
            suspend fun capture(aggregate: String, aggregateId: String, semanticId: String, snapshot: String) {
                outbox.enqueue(
                    organizationId = currentOrgId,
                    aggregateType = aggregate,
                    aggregateId = aggregateId,
                    operationType = "UPSERT",
                    mutationId = stableRestoreMutationId(restoreBatchId, aggregate, semanticId),
                    commandBatchId = restoreBatchId,
                    commandOrder = commandOrder++,
                    payload = mapOf("restoreBatch" to restoreBatchId, "snapshotJson" to snapshot),
                )
            }
            data.clients.forEach { capture("PARTY_IDENTITY", it.id, it.id, backupJson.encodeToString(it.copy(legacyClientTypeTombstone = ""))) }
            restoredPartyRows.customers.forEach {
                capture("CUSTOMER_PROFILE", it.partyId, it.partyId, backupJson.encodeToString(it.copy(organizationId = currentOrgId)))
            }
            restoredPartyRows.suppliers.forEach {
                capture("SUPPLIER_PROFILE", it.partyId, it.partyId, backupJson.encodeToString(it.copy(organizationId = currentOrgId)))
            }
            data.invoices.forEach { invoice ->
                val lines = data.invoiceItems.filter { it.invoiceId == invoice.id }
                capture(
                    "INVOICE", invoice.id, invoice.id,
                    backupJson.encodeToString(invoice) + "\n" + backupJson.encodeToString(lines),
                )
            }
            data.payments.forEach { capture("PAYMENT", it.id, it.id, backupJson.encodeToString(it)) }
            data.expenses.forEach { capture("EXPENSE", it.id, it.id, backupJson.encodeToString(it)) }
            data.notes.forEach { capture("NOTE", it.id, it.id, backupJson.encodeToString(it)) }
            data.clientReminders.forEach { capture("REMINDER", it.id, it.id, backupJson.encodeToString(it)) }
            data.inventoryItems.forEach { capture("INVENTORY_ITEM", it.id, it.id, backupJson.encodeToString(it)) }
            data.inventoryMovements.forEach { capture("INVENTORY_MOVEMENT", it.id, it.id, backupJson.encodeToString(it)) }
            data.inventoryUnits.forEach { capture("INVENTORY_UNIT", it.id, it.id, backupJson.encodeToString(it)) }
            data.categories.forEach { capture("CATEGORY", it.id, it.id, backupJson.encodeToString(it)) }
            data.itemCategories.groupBy { it.itemId }.forEach { (itemId, rows) ->
                capture("ITEM_CATEGORY", itemId, itemId, backupJson.encodeToString(rows.sortedBy { it.id }))
            }
            data.priceListTemplates.forEach { template ->
                val itemIds = data.priceListTemplateItems
                    .filter { it.templateId == template.id }
                    .sortedBy { it.sortOrder }
                    .map { it.inventoryItemId }
                outbox.enqueue(
                    organizationId = currentOrgId,
                    aggregateType = "PRICE_LIST",
                    aggregateId = template.id,
                    operationType = "UPSERT",
                    mutationId = stableRestoreMutationId(restoreBatchId, "PRICE_LIST", template.id),
                    commandBatchId = restoreBatchId,
                    commandOrder = commandOrder++,
                    payload = mapOf(
                        "kind" to "TEMPLATE",
                        "name" to template.name,
                        "isFavorite" to template.isFavorite,
                        "itemIds" to itemIds.joinToString(","),
                        "createdAt" to template.createdAt,
                        "updatedAt" to template.updatedAt,
                    ),
                )
            }
            data.cashRegister?.let { capture("CASH_REGISTER", it.id, it.id, backupJson.encodeToString(it)) }
            data.cashMovements.forEach { capture("CASH_MOVEMENT", it.id, it.id, backupJson.encodeToString(it)) }
        }

        if (data.version >= 3) prefs.setExpenseTarget(data.expenseTarget)
        return restoreSummary(data)
    }

    private data class RestoredPartyRows(
        val roles: List<PartyRoleEntity>,
        val customers: List<CustomerProfileEntity>,
        val suppliers: List<SupplierProfileEntity>,
    )

    /** v1-v5 backup compatibility only. Runtime classification never reads this tombstone. */
    @Suppress("DEPRECATION")
    private fun legacyBackupPartyRows(clients: List<PartyIdentityEntity>, organizationId: String): RestoredPartyRows {
        val roles = mutableListOf<PartyRoleEntity>()
        val customers = mutableListOf<CustomerProfileEntity>()
        val suppliers = mutableListOf<SupplierProfileEntity>()
        val now = System.currentTimeMillis()

        clients.forEach { client ->
            val tokens = client.legacyClientTypeTombstone
                .split(',')
                .map { it.trim().uppercase(Locale.ROOT) }
                .filter { it.isNotBlank() }
                .toSet()
            val customerSegment = listOf(
                "COMPETITOR", "COMPANY", "INSTITUTION", "WORKSHOP_OWNER", "MARKETER", "TRADER",
                "DISTRIBUTOR", "WHOLESALE_TRADER", "CAR_OWNER", "MECHANIC", "SHOP_OWNER", "OTHER", "INDIVIDUAL",
            ).firstOrNull(tokens::contains)
                ?: "INDIVIDUAL".takeIf { "SUPPLIER" !in tokens && "GLOBAL_SUPPLIER" !in tokens }
            val supplierScope = when {
                "GLOBAL_SUPPLIER" in tokens -> "INTERNATIONAL"
                "SUPPLIER" in tokens -> "LOCAL"
                "COMPETITOR" in tokens -> "UNKNOWN"
                else -> null
            }

            if (customerSegment != null) {
                roles += PartyRoleEntity(
                    id = "$organizationId:${client.id}:CUSTOMER",
                    partyId = client.id,
                    organizationId = organizationId,
                    role = "CUSTOMER",
                    status = "ACTIVE",
                    createdAt = client.createdAt,
                    updatedAt = now,
                    dirty = true,
                )
                customers += CustomerProfileEntity(
                    organizationId = organizationId,
                    partyId = client.id,
                    segment = customerSegment,
                    ageYears = client.specialty.trim().toIntOrNull()
                        ?.takeIf { customerSegment == "INDIVIDUAL" && it in 1..120 },
                    purchaseContactName = client.specialty.takeIf { customerSegment in setOf("COMPANY", "INSTITUTION") }.orEmpty(),
                    businessActivity = when (customerSegment) {
                        "COMPANY", "INSTITUTION", "DISTRIBUTOR" -> client.workplace
                        "WORKSHOP_OWNER", "TRADER", "COMPETITOR" -> client.specialty
                        else -> ""
                    },
                    workplaceName = client.workplace.takeIf { customerSegment == "INDIVIDUAL" }.orEmpty(),
                    shopName = client.workplace.takeIf { customerSegment in setOf("TRADER", "COMPETITOR") }.orEmpty(),
                    workshopName = client.workplace.takeIf { customerSegment == "WORKSHOP_OWNER" }.orEmpty(),
                    vehicleModels = client.carType,
                    workshopWorkerCount = client.secondaryPhones.trim().toIntOrNull()
                        ?.takeIf { customerSegment == "WORKSHOP_OWNER" && it >= 0 },
                    updatedAt = now,
                    dirty = true,
                )
            }
            if (supplierScope != null) {
                roles += PartyRoleEntity(
                    id = "$organizationId:${client.id}:SUPPLIER",
                    partyId = client.id,
                    organizationId = organizationId,
                    role = "SUPPLIER",
                    status = "ACTIVE",
                    createdAt = client.createdAt,
                    updatedAt = now,
                    dirty = true,
                )
                suppliers += SupplierProfileEntity(
                    organizationId = organizationId,
                    partyId = client.id,
                    scope = supplierScope,
                    country = client.carType,
                    currencyCode = client.secondaryPhones.trim().uppercase(Locale.ROOT)
                        .takeIf { it in setOf("SDG", "USD", "EUR", "SAR", "AED", "EGP", "CNY") }.orEmpty(),
                    specialty = client.specialty,
                    updatedAt = now,
                    dirty = true,
                )
            }
        }
        return RestoredPartyRows(roles, customers, suppliers)
    }

    private fun stableRestoreMutationId(batchId: String, aggregate: String, semanticId: String): String =
        UUID.nameUUIDFromBytes("v307|$batchId|$aggregate|$semanticId".toByteArray(Charsets.UTF_8)).toString()

    private fun restoreSummary(data: BackupData): String = buildString {
        append("✅ تم الاستيراد بنجاح\n")
        append("${data.clients.size} عميل، ${data.invoices.size} فاتورة\n")
        append("${data.expenses.size} مصروف، ${data.payments.size} دفعة")
        if (data.clientReminders.isNotEmpty()) append("\n${data.clientReminders.size} تنبيه")
        if (data.inventoryItems.isNotEmpty())  append("\n${data.inventoryItems.size} صنف مخزون")
        if (data.cashMovements.isNotEmpty())   append("\n${data.cashMovements.size} حركة صندوق")
        if (data.auditLogs.isNotEmpty())       append("\n${data.auditLogs.size} سجل تدقيق")
    }

    // ── جمع كل البيانات للتصدير ────────────────────────────────────────────
    internal suspend fun collectAllData(): BackupData = BackupData(
        exportedAt        = System.currentTimeMillis(),
        organizationId    = prefs.getLastOrgId(),
        schemaVersion     = BACKUP_SCHEMA_VERSION,
        appVersionCode    = 1,
        clients           = db.clientDao().getAllPartyIdentitiesForOrganizationSync(prefs.getLastOrgId())
            .map { it.copy(legacyClientTypeTombstone = "") },
        partyRoles        = db.partyRoleDao().getRolesForOrganizationSync(prefs.getLastOrgId()),
        customerProfiles  = db.partyRoleDao().getCustomerProfilesForOrganizationSync(prefs.getLastOrgId()),
        supplierProfiles  = db.partyRoleDao().getSupplierProfilesForOrganizationSync(prefs.getLastOrgId()),
        invoices          = db.invoiceDao().getAllInvoices().first(),
        invoiceItems      = db.invoiceDao().getAllInvoiceItemsSync(),
        payments          = db.paymentDao().getAllPaymentsSync(),
        expenses          = db.expenseDao().getAllExpensesSync(),
        notes             = db.noteDao().getAllNotesSync(),
        clientReminders   = db.clientReminderDao().getAllRemindersSync(),
        inventoryItems    = db.inventoryDao().getAllItemsIncludingArchivedSync(),
        inventoryMovements = db.inventoryDao().getAllMovementsSync(),
        expenseTarget     = prefs.expenseTarget.first(),
        cashRegister      = db.cashRegisterDao().getRegisterSync(),
        cashMovements     = db.cashRegisterDao().getAllMovementsSync(),
        auditLogs         = db.auditLogDao().getAllSync(),
        inventoryUnits    = db.inventoryUnitDao().getAllUnitsSync(),
        itemCategories    = db.itemCategoryDao().getAllItemCategoriesSync(),
        categories        = db.categoryDao().getAllCategoriesSync(),
        priceListTemplates = db.priceListDao().getTemplatesSync(prefs.getLastOrgId()),
        priceListTemplateItems = db.priceListDao().getTemplateItemsSync(prefs.getLastOrgId())
    )

    // ── حذف كل البيانات قبل الاستيراد ─────────────────────────────────────
    private suspend fun clearAllData() {
        // Party V2 profiles/roles use RESTRICT to clients; remove them first, then CASCADE handles
        // invoice/payment/note/reminder children of the identity.
        val organizationId = prefs.getLastOrgId()
        val partyIdentities = db.clientDao().getAllPartyIdentitiesForOrganizationSync(organizationId)
        db.partyRoleDao().deleteCustomerProfilesForOrganization(organizationId)
        db.partyRoleDao().deleteSupplierProfilesForOrganization(organizationId)
        db.partyRoleDao().deleteRolesForOrganization(organizationId)
        partyIdentities.forEach { db.clientDao().deleteClient(it) }
        db.expenseDao().getAllExpensesSync().forEach   { db.expenseDao().deleteExpense(it) }
        db.inventoryDao().clearInventoryForBackupRestore()
        // item_categories تُحذف بـ CASCADE عند حذف inventory_items
        db.inventoryUnitDao().getAllUnitsSync().forEach { db.inventoryUnitDao().deleteUnit(it.id) }
        db.categoryDao().getAllCategoriesSync().forEach { db.categoryDao().deleteCategory(it.id) }
        val priceListOrgId = sessionReader.snapshot().organization.id
        db.priceListDao().getTemplatesSync(priceListOrgId).forEach {
            db.priceListDao().deleteTemplate(it.id, priceListOrgId)
        }
        db.cashRegisterDao().resetRegister()
        db.cashRegisterDao().deleteAllMovements()
        // audit_log لا يُحذف (append-only بالتصميم) — يُضاف الجديد فوق القديم
    }

    // ── تحليل الباكب (مشفر أو عادي) ──────────────────────────────────────
    private fun parseBackup(text: String, password: String?): BackupData {
        return if (text.startsWith(ENCRYPTED_HEADER)) {
            val encryptionPassword = password?.takeIf { it.isNotBlank() } ?: deviceBackupSecret()
            val encoded = text.substringAfter("\n")
            val encrypted = Base64.decode(encoded, Base64.NO_WRAP)
            val decrypted = BackupCrypto.decrypt(encrypted, encryptionPassword)
            backupJson.decodeFromString(decrypted.toString(Charsets.UTF_8))
        } else {
            backupJson.decodeFromString(text)
        }
    }

    private fun deviceBackupSecret(): String =
        DeviceBackupSecretStore(context).getOrCreate()

    private fun readText(uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri)
            ?: error("لا يمكن فتح الملف")
        return InputStreamReader(stream).use { it.readText() }
    }

    private fun saveToFile(content: String, ext: String): File {
        val date = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())
        val file = File(context.cacheDir, "verto_backup_$date.$ext")
        file.writeText(content)
        return file
    }

    private fun shareFile(file: File, target: ShareTarget) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val base = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "نسخة احتياطية Verto")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val intent = when (target) {
            ShareTarget.WHATSAPP -> Intent(base).apply { setPackage("com.whatsapp") }
            ShareTarget.TELEGRAM -> Intent(base).apply { setPackage("org.telegram.messenger") }
            else -> Intent.createChooser(base, "حفظ النسخة الاحتياطية").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

enum class ShareTarget(val label: String) {
    LOCAL("محلي"), DRIVE("Google Drive"), WHATSAPP("واتساب"), TELEGRAM("تيليجرام")
}
