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
@Entity(tableName = "audit_log", indices = [Index("createdAt")])
data class AuditLogEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val action: AuditAction,
    val auditTable: AuditTable,
    val recordId: String,
    // وصف مقروء: "فاتورة #45 - محمد أحمد"
    val recordSummary: String = "",
    // JSON للقيم قبل وبعد التعديل
    val oldValue: String = "",
    val newValue: String = "",
    val employeeId: String = "",
    val employeeName: String = "",
    @ColumnInfo(defaultValue = "''")
    val sourceType: String = "",
    @ColumnInfo(defaultValue = "''")
    val sourceId: String = "",
    @ColumnInfo(defaultValue = "1")
    val sourceVersion: Int = 1,
    @ColumnInfo(defaultValue = "''")
    val writeId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    // يُصبح false بعد 24 ساعة (يُحدَّث بـ worker دوري)
    val canUndo: Boolean = true
)

// ─────────────────────────────────────────────────────
// NOTE
// ─────────────────────────────────────────────────────
@Serializable
@Entity(
    tableName = "notes",
    foreignKeys = [ForeignKey(
        entity = PartyIdentityEntity::class,
        parentColumns = ["id"],
        childColumns = ["clientId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("clientId")]
)
data class NoteEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val clientId: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)

// ─────────────────────────────────────────────────────
// CLIENT REMINDER
// ─────────────────────────────────────────────────────
@Serializable
@Entity(
    tableName = "client_reminders",
    foreignKeys = [ForeignKey(
        entity = PartyIdentityEntity::class,
        parentColumns = ["id"],
        childColumns = ["clientId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("clientId")]
)
data class ClientReminderEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val clientId: String,
    val note: String,
    val reminderAt: Long,
    val isDone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

// ─────────────────────────────────────────────────────
// SHIPMENT — الشحنة الرئيسية
// ─────────────────────────────────────────────────────
