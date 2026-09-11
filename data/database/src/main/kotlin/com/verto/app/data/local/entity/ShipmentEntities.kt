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

// ─────────────────────────────────────────────────────
// COMMISSION PAYMENT — سجل مدفوعات العمولات
// ─────────────────────────────────────────────────────
@Entity(tableName = "commission_payments")
data class CommissionPaymentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val clientId: String,
    val clientName: String,
    val invoiceIds: String = "",
    val totalAmount: Double,
    val bankName: String = "",
    val transactionRef: String = "",
    val paidAt: Long = System.currentTimeMillis()
)

// ─────────────────────────────────────────────────────
// BUDGET — أنواع الأهداف
// ─────────────────────────────────────────────────────
