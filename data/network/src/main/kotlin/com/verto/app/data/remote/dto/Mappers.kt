package com.verto.app.data.remote.dto

import com.verto.app.data.local.entity.*
import com.verto.app.utils.SupabaseDateParser
import java.text.SimpleDateFormat
import java.util.Locale

private inline fun <reified T : Enum<T>> parseRequiredEnum(value: String, fieldName: String): T =
    runCatching { enumValueOf<T>(value) }
        .getOrElse { throw IllegalArgumentException("Unknown $fieldName contract value: $value") }

fun ClientDto.toEntity(): PartyIdentityEntity = PartyIdentityEntity(
    id          = this.id,
    name        = this.name,
    phone       = this.phone,
    address     = this.address,
    workplace   = this.workplace,
    generalNote = this.generalNote,
    createdAt   = SupabaseDateParser.parseOrNow(this.createdAt)
)

fun InvoiceDto.toEntity(): InvoiceEntity = InvoiceEntity(
    id                   = this.id,
    invoiceNumber        = this.invoiceNumber,
    clientId             = this.clientId,
    organizationId       = this.organizationId,
    supplierInvoiceReference = this.supplierInvoiceReference,
    supplierInvoiceReferenceNormalized = this.supplierInvoiceReferenceNormalized,
    type                 = parseRequiredEnum(this.type, "invoice.type"),
    category             = parseRequiredEnum(this.category, "invoice.category"),
    status               = parseRequiredEnum(this.status, "invoice.status"),
    description          = this.description,
    totalAmount          = this.totalAmount.toRemoteDouble(),
    dueDate              = runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(this.dueDate?.take(10) ?: "")?.time ?: 0L
    }.getOrDefault(0L),
    notifyDaysBefore     = this.notifyDaysBefore,
    notifyRepeatDays     = this.notifyRepeatDays,
    notificationsEnabled = this.notificationsEnabled,
    notes                = this.notes,
    isOwedToMe           = this.isOwedToMe,
    imageUri             = this.imageUrl,
    commission           = this.commission.toDouble(),
    shipmentId           = this.shipmentId,
    purchaseOrderId      = this.purchaseOrderId,
    purchaseScope        = parseRequiredEnum(this.purchaseScope, "invoice.purchase_scope"),
    lifecycleStatus      = parseRequiredEnum(this.lifecycleStatus, "invoice.lifecycle_status"),
    lifecycleVersion     = this.lifecycleVersion,
    postedAt             = this.postedAt,
    voidedAt             = this.voidedAt,
    voidReason           = this.voidReason,
    voidWriteId          = this.voidWriteId,
    createdBy            = this.createdBy.orEmpty(),
    voided               = this.voided || this.lifecycleStatus == "VOID",
    createdAt            = SupabaseDateParser.parseOrNow(this.createdAt)
)

fun PaymentDto.toEntity(): PaymentEntity = PaymentEntity(
    id            = this.id,
    invoiceId     = this.invoiceId,
    clientId      = this.clientId,
    amount        = this.amount.toRemoteDouble(),
    paymentMethod = parseRequiredEnum(this.paymentMethod, "payment.payment_method"),
    note          = this.note,
    paidAt        = SupabaseDateParser.parseOrNow(this.paidAt)
)
