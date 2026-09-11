package com.verto.app.data.local.entity

import com.verto.app.core.audit.domain.AuditAction
import com.verto.app.core.audit.domain.AuditTable
import com.verto.app.utils.SearchTextNormalizer

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
@Entity(
    tableName = "clients",
    indices = [
        Index(value = ["nameSearch"], name = "index_clients_name_search"),
        Index(value = ["phoneSearch"], name = "index_clients_phone_search")
    ]
)
data class PartyIdentityEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String,
    @ColumnInfo(defaultValue = "''")
    val nameSearch: String = SearchTextNormalizer.text(name),
    @ColumnInfo(defaultValue = "''")
    val phoneSearch: String = SearchTextNormalizer.phone(phone),
    val address: String = "",
    val workplace: String = "",
    val generalNote: String = "",
    /**
     * Local Room compatibility tombstone only. Party/customer/supplier classification must never
     * read or write this value; the authoritative model is Party V2 (party_roles + profiles).
     * Kept physically on Android because minSdk 26 SQLite cannot safely DROP a parent-table column
     * while many child tables reference clients through foreign keys. Migration 93→94 blanks it.
     */
    @Deprecated("Party V2 owns classification; do not use legacy clientType storage")
    @kotlinx.serialization.SerialName("clientTypes")
    @ColumnInfo(name = "clientType")
    val legacyClientTypeTombstone: String = "",
    // ── حقول جديدة ───────────────────────────────────
    val carType: String = "",           // نوع السيارة (للعملاء)
    val bankAccount: String = "",       // حسابات بنكية متعددة مفصولة بـ ||
    val specialty: String = "",         // التخصص (للموردين)
    val secondaryPhones: String = "",   // أرقام هواتف إضافية مفصولة بـ ||
    val createdAt: Long = System.currentTimeMillis(),
    // معرّف الموظف الذي أنشأ العميل (لأداء الموظف) — يُملأ عند الإنشاء ويُسحب من السيرفر.
    val createdBy: String = "",
    // SYNC-012: علم التغيّر — true للصفوف المحلية غير المرفوعة، false بعد رفع/سحب ناجح.
    // Push يرفع المتسخ فقط (يمنع إعادة رفع/سحب كل شيء كل دورة).
    val isDirty: Boolean = true
)

// ─────────────────────────────────────────────────────
// INVOICE
// ─────────────────────────────────────────────────────


fun PartyIdentityEntity.withSearchKeys(): PartyIdentityEntity = copy(
    nameSearch = SearchTextNormalizer.text(name),
    phoneSearch = SearchTextNormalizer.phone(phone)
)
