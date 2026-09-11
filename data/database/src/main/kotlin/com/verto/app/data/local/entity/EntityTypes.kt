package com.verto.app.data.local.entity

import com.verto.app.core.audit.domain.AuditAction
import com.verto.app.core.audit.domain.AuditTable

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

// ── أنواع الفواتير ───────────────────────────────────
// قيمة واحدة مقصودة — الفاتورة الخدمية أُلغيت ولم تُنفَّذ؛ الـ enum يُبقى لتوافق قاعدة البيانات

@Serializable
enum class InvoiceType(val label: String) {
    GOODS("فاتورة")
}

// ── تصنيف الفاتورة: مبيعات أم مشتريات ───────────────
@Serializable
enum class InvoiceCategory(val label: String) {
    SALE("بيع"),
    PURCHASE("مشتريات")
}

// ── حالة الفاتورة ────────────────────────────────────
@Serializable
enum class InvoiceStatus {
    CLOSED_CASH,
    CLOSED_CREDIT
}

@Serializable
enum class InvoiceLifecycleStatus {
    DRAFT,
    POSTED,
    VOID
}

// ── نوع البند داخل الفاتورة ──────────────────────────
// قيمة واحدة مقصودة — الأصناف الخدمية تُعرَّف في InventoryItemEntity.isService؛ الـ enum يُبقى لتوافق قاعدة البيانات
@Serializable
enum class ItemType(val label: String) {
    GOODS("صنف")
}

// ── المجموعات الرئيسية للعملاء ───────────────────────
enum class ClientGroup(val label: String) {
    RETAIL("بيع التجزئة"),
    MARKETING("التسويق"),
    WHOLESALE("الجملة"),
    SUPPLIER_GROUP("موردون")
}

// ── تصنيف العميل / المورد ────────────────────────────
enum class ClientType(val label: String) {
    // ── تجزئة ─────────────────────────────────────────
    INDIVIDUAL("عميل"),
    COMPANY("شركة"),
    INSTITUTION("مؤسسة"),
    // ── تسويق ─────────────────────────────────────────
    WORKSHOP_OWNER("صاحب ورشة"),
    MARKETER("مسوق"),
    // ── جملة ──────────────────────────────────────────
    TRADER("تاجر"),
    DISTRIBUTOR("موزع"),
    COMPETITOR("منافس"),
    // ── موردون ────────────────────────────────────────
    SUPPLIER("مورد"),
    GLOBAL_SUPPLIER("مورد عالمي"),
    WHOLESALE_TRADER("تاجر جملة"),
    // ── legacy (للتوافق مع بيانات قديمة) ─────────────
    CAR_OWNER("صاحب سيارة"),
    MECHANIC("ميكانيكي"),
    SHOP_OWNER("صاحب محل"),
    OTHER("أخرى")
}

val ClientType.group: ClientGroup get() = when (this) {
    ClientType.INDIVIDUAL, ClientType.COMPANY, ClientType.INSTITUTION,
    ClientType.CAR_OWNER, ClientType.MECHANIC, ClientType.SHOP_OWNER -> ClientGroup.RETAIL
    ClientType.WORKSHOP_OWNER, ClientType.MARKETER                   -> ClientGroup.MARKETING
    ClientType.TRADER, ClientType.DISTRIBUTOR, ClientType.COMPETITOR,
    ClientType.WHOLESALE_TRADER                                       -> ClientGroup.WHOLESALE
    ClientType.SUPPLIER, ClientType.GLOBAL_SUPPLIER, ClientType.OTHER -> ClientGroup.SUPPLIER_GROUP
}

// ── قوائم التصنيف حسب النوع ──────────────────────────
val CLIENT_TYPES = listOf(
    ClientType.INDIVIDUAL, ClientType.COMPANY, ClientType.INSTITUTION,
    ClientType.WORKSHOP_OWNER, ClientType.MARKETER,
    ClientType.TRADER, ClientType.DISTRIBUTOR, ClientType.COMPETITOR
)

val SUPPLIER_TYPES = listOf(
    ClientType.WHOLESALE_TRADER, ClientType.DISTRIBUTOR, ClientType.COMPETITOR, ClientType.OTHER
)

// ── مساعد multi-select ────────────────────────────────
fun String.toClientTypeSet(): Set<ClientType> =
    this.split(",").mapNotNull { name ->
        runCatching { ClientType.valueOf(name.trim()) }.getOrNull()
    }.toSet().ifEmpty { setOf(ClientType.INDIVIDUAL) }

fun Set<ClientType>.toStorageString(): String =
    this.joinToString(",") { it.name }

fun String.containsClientType(type: ClientType): Boolean =
    this.toClientTypeSet().contains(type)

fun String.clientTypesLabel(): String =
    this.toClientTypeSet().joinToString(" + ") { it.label }

// ── مساعد الحسابات البنكية (متعددة مفصولة بـ ||) ─────
// ── الحساب البنكي (اسم البنك + رقم الحساب) ──────────
data class BankAccount(
    val bankName: String = "",
    val accountNumber: String = ""
)

// صيغة التخزين: "اسم البنك::رقم الحساب||اسم البنك::رقم الحساب"
fun String.toBankAccountList(): List<BankAccount> =
    if (this.isBlank()) emptyList()
    else this.split("||").map { it.trim() }.filter { it.isNotBlank() }.map { entry ->
        val parts = entry.split("::")
        BankAccount(
            bankName      = parts.getOrElse(0) { "" }.trim(),
            accountNumber = parts.getOrElse(1) { "" }.trim()
        )
    }

fun List<BankAccount>.toBankAccountsString(): String =
    this.joinToString("||") { "${it.bankName}::${it.accountNumber}" }
// ── مساعد الهواتف الإضافية (مفصولة بـ ||) ────────────
fun String.toSecondaryPhoneList(): List<String> =
    if (this.isBlank()) emptyList()
    else this.split("||").map { it.trim() }.filter { it.isNotBlank() }

fun List<String>.toSecondaryPhonesString(): String = this.joinToString("||")

// ── طرق الدفع ────────────────────────────────────────
@Serializable
enum class PaymentMethod(val label: String) {
    CASH("كاش"),
    TRANSFER("تحويل"),
    CHECK("شيك")
}

// ── حالة العميل ──────────────────────────────────────
enum class ClientStatus { RED, GREEN, GREY }

// ── فئات المصروفات ───────────────────────────────────

// ── نوع حركة الصندوق ─────────────────────────────────
// ✅ الإصلاح — الثغرة #6:
// أُضيف PAYMENT_MADE ("سداد مورد") لتمييز المدفوعات للموردين
// عن المشتريات النقدية في التقارير المالية.
@Serializable
enum class CashMovementType(val label: String) {
    SALE_CASH("كاش"),
    PURCHASE_CASH("مشتريات كاش"),
    PAYMENT_RECEIVED("تحصيل دين"),
    PAYMENT_MADE("سداد مورد"),       // ✅ جديد — سداد دين للمورد
    EXPENSE("مصروف"),
    MANUAL_ADD("إضافة يدوية"),
    MANUAL_DEDUCT("خصم يدوي")
}

// ─────────────────────────────────────────────────────
// CLIENT
// ─────────────────────────────────────────────────────
