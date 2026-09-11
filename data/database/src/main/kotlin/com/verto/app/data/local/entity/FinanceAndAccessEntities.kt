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
enum class BudgetPeriodType(val label: String) {
    DAILY("يومي"),
    WEEKLY("أسبوعي"),
    MONTHLY("شهري"),
    QUARTERLY("ربع سنوي"),
    YEARLY("سنوي")
}

@Serializable
enum class BudgetType(val label: String) {
    SALES_TARGET("مبيعات مستهدفة"),
    PROFIT_TARGET("أرباح مستهدفة"),
    EXPENSE_LIMIT("حد أقصى للمصروفات"),
    CATEGORY_TARGET("هدف لتصنيف معين")
}

// ─────────────────────────────────────────────────────
// RFM — تصنيفات تجزئة العملاء
// ─────────────────────────────────────────────────────
@Serializable
enum class RfmSegment(val label: String, val emoji: String) {
    CHAMPIONS("أبطال", "🏆"),
    LOYAL("عملاء مخلصون", "💎"),
    POTENTIAL_LOYALIST("مخلصون محتملون", "🌱"),
    NEW_CUSTOMERS("عملاء جدد", "🆕"),
    PROMISING("واعدون", "✨"),
    NEEDS_ATTENTION("يحتاجون انتباه", "⚠️"),
    AT_RISK("في خطر", "🚨"),
    CANT_LOSE("لا يمكن خسارتهم", "💔"),
    HIBERNATING("نائمون", "💤"),
    LOST("مفقودون", "❌")
}

// ─────────────────────────────────────────────────────
// COST ALLOCATION — طرق توزيع التكاليف على الأصناف
// ─────────────────────────────────────────────────────
@Serializable
enum class CostAllocationMethod(val label: String) {
    BY_QUANTITY("حسب الكمية"),
    BY_VALUE("حسب القيمة"),
    BY_WEIGHT("حسب الوزن"),
    EQUAL("بالتساوي على الأصناف"),
    MANUAL("يدوي")
}

@Serializable
enum class CostAllocationSource(val label: String) {
    SHIPMENT_COST("تكلفة شحنة"),
    OVERHEAD("تكلفة عامة"),
    CUSTOMS("جمارك"),
    OTHER("أخرى")
}

// ─────────────────────────────────────────────────────
// CASH RECONCILIATION — أنواع جلسات التسوية
// ─────────────────────────────────────────────────────
@Serializable
enum class ReconciliationStatus(val label: String) {
    OPEN("مفتوحة"),
    CLOSED("مقفولة"),
    DISPUTED("متنازع عليها")
}

// ─────────────────────────────────────────────────────
// CLIENT CREDIT — رصيد مقدَّم (الفائض من السداد الجماعي)
// Session 4: عند دفع أكثر من إجمالي الدين يُسجَّل الفائض كرصيد دائم.
//   amount موجب  = رصيد لصالحنا (دفعنا للمورد مقدَّماً / الطرف مدين لنا).
//   amount سالب  = رصيد علينا   (العميل دفع لنا زيادة / نحن مدينون له).
// صافي رصيد الطرف = SUM(amount) على clientId. الاستهلاك مستقبلاً يُسجَّل بصف بإشارة معاكسة.
// ─────────────────────────────────────────────────────
@Serializable
@Entity(tableName = "client_credits", indices = [Index("clientId")])
data class ClientCreditEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val clientId: String,
    /** Compatibility major-unit projection; financial source of truth is [amountMinor]. */
    val amount: Double,
    @ColumnInfo(name = "amount_minor", defaultValue = "0")
    val amountMinor: Long = com.verto.app.money.Money.fromLegacyDouble(amount).amountMinor,
    val note: String = "",
    // أصل العملية (آخر دفعة في السداد الجماعي) لتتبّع المصدر.
    val sourcePaymentId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val employeeId: String = "",
    val employeeName: String = "",
    val isDirty: Boolean = true   // SYNC: جاهز للمزامنة (الرفع الفعلي ضمن نطاق Session 9)
)

// ─────────────────────────────────────────────────────
// JOIN CODE — كود الربط مع AutoDrive
// ─────────────────────────────────────────────────────
@Entity(tableName = "join_codes")
data class JoinCodeEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val clientId: String,
    val clientName: String,
    val code: String,
    val employeeName: String = "",
    val jobTitle: String = "",
    val actualJoinDate: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 72 * 60 * 60 * 1000L,
    val used: Boolean = false,
    val usedAt: Long? = null,
    val usedByUserId: String? = null
)
