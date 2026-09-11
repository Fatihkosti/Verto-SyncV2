package com.verto.app.feature.party.application.model

import com.verto.app.feature.party.domain.model.CustomerSegment

import java.util.UUID

enum class ClientType(val label: String) {
    INDIVIDUAL("عميل"),
    COMPANY("شركة/مؤسسة"),
    INSTITUTION("مؤسسة"),
    WORKSHOP_OWNER("صاحب ورشة"),
    MARKETER("مسوق"),
    TRADER("تاجر"),
    DISTRIBUTOR("موزع"),
    COMPETITOR("منافس"),
    SUPPLIER("مورد"),
    GLOBAL_SUPPLIER("مورد عالمي"),
    WHOLESALE_TRADER("تاجر جملة"),
    CAR_OWNER("صاحب سيارة"),
    MECHANIC("ميكانيكي"),
    SHOP_OWNER("صاحب محل"),
    OTHER("أخرى")
}

data class BankAccount(
    val bankName: String = "",
    val accountNumber: String = ""
)

data class ClientReminderViewData(
    val id: String = UUID.randomUUID().toString(),
    val clientId: String,
    val note: String,
    val reminderAt: Long,
    val isDone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

typealias ClientReminderItem = ClientReminderViewData

data class PartyPermissionsViewData(
    val salesCreate: Boolean = false,
    val clientsEdit: Boolean = false,
    val clientsDelete: Boolean = false,
    val clientsAddPayment: Boolean = false,
    val suppliersAddPayment: Boolean = false
)

data class PartyPaymentTarget(
    val invoiceId: String,
    val invoiceNumber: Int,
    val remaining: Double
)

sealed class PartyPaymentAllocationResult {
    data class Success(val applied: Double, val surplusCredit: Double) : PartyPaymentAllocationResult()
    data class Error(val message: String) : PartyPaymentAllocationResult()
}


fun CustomerSegment?.toClientType(): ClientType =
    this?.name?.let { name -> runCatching { ClientType.valueOf(name) }.getOrNull() } ?: ClientType.OTHER

fun CustomerSegment?.customerSegmentLabel(): String = toClientType().label

fun String.toClientTypeSet(): Set<ClientType> =
    split(',').mapNotNull { runCatching { ClientType.valueOf(it.trim()) }.getOrNull() }
        .toSet().ifEmpty { setOf(ClientType.INDIVIDUAL) }

fun Set<ClientType>.toStorageString(): String = joinToString(",") { it.name }
fun String.containsClientType(type: ClientType): Boolean = toClientTypeSet().contains(type)
fun String.clientTypesLabel(): String = toClientTypeSet().joinToString(" + ") { it.label }
fun String.isSupplier(): Boolean =
    containsClientType(ClientType.SUPPLIER) || containsClientType(ClientType.GLOBAL_SUPPLIER)

fun String.toBankAccountList(): List<BankAccount> =
    if (isBlank()) emptyList() else split("||").map(String::trim).filter(String::isNotBlank).map { entry ->
        val parts = entry.split("::")
        BankAccount(parts.getOrElse(0) { "" }.trim(), parts.getOrElse(1) { "" }.trim())
    }

fun List<BankAccount>.toBankAccountsString(): String =
    joinToString("||") { "${it.bankName}::${it.accountNumber}" }
fun String.toSecondaryPhoneList(): List<String> =
    if (isBlank()) emptyList() else split("||").map(String::trim).filter(String::isNotBlank)
fun List<String>.toSecondaryPhonesString(): String = joinToString("||")
