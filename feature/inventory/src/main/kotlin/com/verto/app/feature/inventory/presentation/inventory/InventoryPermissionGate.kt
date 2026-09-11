package com.verto.app.feature.inventory.presentation.inventory

import com.verto.app.core.audit.domain.WriteAuditPort
import com.verto.app.core.audit.domain.logPermissionDenied
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.feature.inventory.application.InventoryPermissionService
import com.verto.app.feature.inventory.application.model.InventoryPermission

internal class InventoryPermissionGate(
    private val auditLogger: WriteAuditPort,
    private val sessionReader: SessionReader,
    private val permissionProvider: InventoryPermissionService,
    private val resolveString: (Int) -> String,
    private val onDenied: (String) -> Unit
) {
    suspend fun canEdit(action: String, details: String = ""): Boolean = requirePermission(
        allowed = permissionProvider.canNow(InventoryPermission.EDIT),
        action = "inventory_edit:$action",
        details = details,
        message = resolveString(com.verto.feature.inventory.R.string.inventory_v298_permission_edit_denied)
    )

    suspend fun canPrice(action: String, details: String = ""): Boolean = requirePermission(
        allowed = permissionProvider.canNow(InventoryPermission.PRICE),
        action = "inventory_price:$action",
        details = details,
        message = resolveString(com.verto.feature.inventory.R.string.inventory_v298_permission_price_denied)
    )

    suspend fun canImport(details: String = ""): Boolean = requirePermission(
        allowed = permissionProvider.canNow(InventoryPermission.IMPORT),
        action = "inventory_import",
        details = details,
        message = resolveString(com.verto.feature.inventory.R.string.inventory_v298_permission_import_denied)
    )

    suspend fun canExport(action: String, details: String = ""): Boolean = requirePermission(
        allowed = permissionProvider.canNow(InventoryPermission.EXPORT),
        action = "inventory_export:$action",
        details = details,
        message = resolveString(com.verto.feature.inventory.R.string.inventory_v298_permission_export_denied)
    )

    fun canExportNow(): Boolean = permissionProvider.can(InventoryPermission.EXPORT)

    suspend fun recordDeniedExport(action: String, details: String, message: String) {
        runCatching { auditLogger.logPermissionDenied("inventory_export:$action", details, sessionReader) }
        onDenied(message)
    }

    private suspend fun requirePermission(
        allowed: Boolean,
        action: String,
        details: String,
        message: String
    ): Boolean {
        if (allowed) return true
        runCatching { auditLogger.logPermissionDenied(action, details, sessionReader) }
        onDenied(message)
        return false
    }
}
