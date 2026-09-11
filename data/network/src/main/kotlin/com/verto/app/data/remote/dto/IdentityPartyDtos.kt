package com.verto.app.data.remote.dto

import com.verto.app.utils.BigDecimalSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class OrganizationDto(
    val id: String = "",
    val name: String = "",
    @SerialName("owner_id") val ownerId: String = "",
    @SerialName("created_at") val createdAt: String? = null
)

// ── المستخدم ──────────────────────────────────────────────────
@Serializable
data class AppUserDto(
    val id: String = "",
    @SerialName("organization_id") val organizationId: String = "",
    val name: String = "",
    val role: String = "sales",          // admin | accountant | sales | warehouse
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("job_title") val jobTitle: String = "",
    @SerialName("actual_join_date") val actualJoinDate: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

// ── العميل ────────────────────────────────────────────────────
@Serializable
data class ClientDto(
    val id: String = "",
    @SerialName("organization_id") val organizationId: String = "",
    @SerialName("created_by") val createdBy: String? = null,
    val name: String = "",
    val phone: String = "",
    val address: String = "",
    val workplace: String = "",
    @SerialName("general_note") val generalNote: String = "",
    // SYNC-001: حقول كانت تُمسح محلياً ولا تُرفع — الآن موجودة على السيرفر
    @SerialName("car_type") val carType: String = "",
    @SerialName("bank_account") val bankAccount: String = "",
    val specialty: String = "",
    @SerialName("secondary_phones") val secondaryPhones: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

// ── الفاتورة ──────────────────────────────────────────────────
