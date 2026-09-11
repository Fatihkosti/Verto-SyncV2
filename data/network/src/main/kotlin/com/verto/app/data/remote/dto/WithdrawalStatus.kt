package com.verto.app.data.remote.dto

enum class WithdrawalStatus(
    val wireValue: String,
    val label: String
) {
    PENDING("PENDING", "معلق"),
    APPROVED("APPROVED", "موافق عليه"),
    REJECTED("REJECTED", "مرفوض"),
    COMPLETED("COMPLETED", "مكتمل"),
    UNKNOWN("UNKNOWN", "");

    companion object {
        fun fromWire(value: String): WithdrawalStatus =
            entries.firstOrNull { it.wireValue == value } ?: UNKNOWN
    }
}
