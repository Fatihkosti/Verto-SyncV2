#!/usr/bin/env python3
from __future__ import annotations

import re
import sqlite3
import tempfile
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def migration_sql() -> list[str]:
    source = text("data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations70To71.kt")
    triples = [textwrap.dedent(x).strip() for x in re.findall(
        r'db\.execSQL\(\s*"""(.*?)"""\.trimIndent\(\)\s*\)', source, re.S
    )]
    singles = [bytes(x, "utf-8").decode("unicode_escape") for x in re.findall(
        r'db\.execSQL\("((?:[^"\\]|\\.)*)"\)', source
    )]
    require(len(triples) == 3, f"expected 3 CREATE TABLE blocks, found {len(triples)}")
    require(len(singles) >= 5, f"expected draft indexes, found {len(singles)}")
    return triples + singles


def static_contracts() -> None:
    catalog = text("data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt")
    app_db = text("data/database/src/main/kotlin/com/verto/app/data/local/AppDatabase.kt")
    entities = text("data/database/src/main/kotlin/com/verto/app/data/local/entity/InvoiceDraftEntities.kt")
    dao = text("data/database/src/main/kotlin/com/verto/app/data/local/dao/InvoiceDraftDao.kt")
    port = text("feature/payment/src/main/kotlin/com/verto/app/feature/payment/application/port/PaymentDebtWorkflowPort.kt")
    bridge = text("app/src/main/kotlin/com/verto/app/feature/payment/bridge/PaymentPresentationBridge.kt")
    vm = text("feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/InvoiceEditorViewModel.kt")
    screen = text("feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/InvoiceEditorScreen.kt")
    form = text("feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/InvoiceEditorFormState.kt")
    item = text("feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/InvoiceEditorItemComposer.kt")
    draft_ui = text("feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/InvoiceEditorDraftUi.kt")
    device_test = text("data/database/src/androidTest/kotlin/com/verto/app/data/local/InvoiceEditorDraftProcessDeathTest.kt")
    process_script = text("scripts/verify-v254-process-death.sh")

    version = re.search(r"ROOM_SCHEMA_VERSION:\s*Int\s*=\s*(\d+)", catalog)
    require(version is not None and int(version.group(1)) >= 71, "Room schema must be >=71")
    require("MIGRATION_70_71" in catalog, "70->71 migration not registered")
    for token in ("InvoiceEditorDraftEntity::class", "InvoiceEditorDraftLineEntity::class", "InvoiceEditorDraftMaintenanceImageEntity::class", "invoiceDraftDao()"):
        require(token in app_db, f"AppDatabase missing {token}")
    for table in ("invoice_editor_drafts", "invoice_editor_draft_lines", "invoice_editor_draft_maintenance_images"):
        require(table in entities, f"entity table missing: {table}")
    require("@Transaction" in dao and "replaceDraft" in dao and "deleteDraft" in dao, "atomic draft replace/delete contract missing")
    require("loadInvoiceDraft" in port and "saveInvoiceDraft" in port and "deleteInvoiceDraft" in port, "draft workflow port incomplete")
    require("InvoiceEditorDraftData.toEntity" in bridge and "toPaymentDraft" in bridge, "draft bridge mapping incomplete")

    require("ACTIVE_INVOICE_WRITE_ID" in vm and "SavedStateHandle" in vm, "stable save identity must use SavedStateHandle")
    require("restored?.writeId" in vm and "draft.withPersistence(stableWriteId" in vm, "write id must survive draft/process recreation")
    require("if (invoiceSaveInFlight) return@launch" in vm, "ViewModel duplicate-save gate missing")
    require("InvoiceSaveUiKind.SAVING" in vm and "InvoiceSaveUiKind.CONFLICT" in vm and "InvoiceSaveUiKind.OFFLINE" in vm, "explicit save states incomplete")
    save_section = vm[vm.index("fun saveOrUpdateInvoice"):]
    save_call = save_section.index("debtWorkflow.saveInvoice(")
    draft_delete = save_section.index("debtWorkflow.deleteInvoiceDraft(")
    require(draft_delete > save_call, "draft must only be cleared after invoice save returns successfully")

    require("snapshotFlow" in screen and "vm.onDraftChanged" in screen, "UI->ViewModel draft event flow missing")
    require("enabled = !state.isSaving" in draft_ui and "if (!isSaving && validate" in screen, "save button duplicate-click protection missing")
    require("BackHandler" in draft_ui and "تغييرات غير محفوظة" in draft_ui, "unsaved-changes exit warning missing")
    require("rememberSaveable" in screen, "simple UI state should use rememberSaveable")
    require("InvoiceEditorFormState(clientId" in screen and "remember(clientId" in screen, "complex form must not be rememberSaveable/Bundle-backed")
    require("form.restoreDraft" in screen and "draftLoadComplete" in screen, "draft restoration/loading gate missing")
    require("Functional" in draft_ui and "×" in draft_ui and "exchangeRate" in draft_ui, "international FX equation not always surfaced")
    require("سعر الشراء أساسي" in item and "سعر البيع اختياري" in item, "local purchase buy/sell separation hint missing")
    require("toDraftData" in form and "restoreDraft" in form and "invoiceItems" in form, "form draft snapshot/restore incomplete")

    require("phase1_seedDraftBeforeRealProcessDeath" in device_test and "phase2_assertDraftAfterRealProcessDeath" in device_test,
            "two-phase device process-death test missing")
    require("am force-stop" in process_script, "device process-death verifier must use a real force-stop, not Rotation")


def sqlite_contracts() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        db_path = Path(tmp) / "v254.db"
        conn = sqlite3.connect(db_path)
        conn.execute("PRAGMA foreign_keys=ON")
        for statement in migration_sql():
            conn.execute(statement)

        columns = [row[1] for row in conn.execute("PRAGMA table_info(invoice_editor_drafts)")]
        required = {
            "draft_key", "organization_id", "is_international", "is_sale", "payment_mode",
            "selected_client_id", "notes", "transaction_currency_code", "exchange_rate", "write_id",
            "draft_item_name", "maintenance_record_id", "updated_at",
        }
        require(required.issubset(columns), f"draft header columns missing: {required - set(columns)}")

        values: dict[str, object] = {name: "" for name in columns}
        values.update({
            "draft_key": "org-1:new:purchase:international:none",
            "organization_id": "org-1",
            "route_client_id": "supplier-1",
            "is_international": 1,
            "is_sale": 0,
            "payment_mode": "CREDIT",
            "selected_client_id": "supplier-1",
            "selected_date_millis": 1786000000000,
            "due_days": "14",
            "notes": "keep-after-process-death",
            "paid_amount": "25",
            "transaction_currency_code": "USD",
            "exchange_rate": "2500",
            "write_id": "write-1",
            "draft_item_name": "pending-line",
            "draft_item_quantity": "3",
            "draft_item_buy_price": "100",
            "maintenance_enabled": 1,
            "maintenance_expanded": 1,
            "maintenance_created_at": 1786000000000,
            "maintenance_vehicle_updated_at": None,
            "updated_at": 1786000000100,
        })
        integer_cols = {
            "is_international", "is_sale", "maintenance_enabled", "maintenance_expanded",
            "maintenance_created_at", "updated_at",
        }
        for name in integer_cols:
            values[name] = int(values.get(name) or 0)
        for nullable in (
            "existing_invoice_id", "selected_date_millis", "maintenance_vehicle_org_id",
            "maintenance_vehicle_client_id", "maintenance_vehicle_remote_id", "maintenance_vehicle_name",
            "maintenance_vehicle_type", "maintenance_vehicle_plate", "maintenance_vehicle_updated_at",
        ):
            if nullable not in values or values[nullable] == "":
                values[nullable] = None

        placeholders = ",".join("?" for _ in columns)
        conn.execute(
            f"INSERT INTO invoice_editor_drafts ({','.join(columns)}) VALUES ({placeholders})",
            [values[c] for c in columns],
        )
        conn.execute(
            "INSERT INTO invoice_editor_draft_lines (id,draft_key,sort_order,name,quantity,sell_price,buy_price,item_category,inventory_item_id) VALUES (?,?,?,?,?,?,?,?,?)",
            ("l1", values["draft_key"], 0, "Brake pad", "2", "150", "100", "parts", "inv-1"),
        )
        conn.execute(
            "INSERT INTO invoice_editor_draft_lines (id,draft_key,sort_order,name,quantity,sell_price,buy_price,item_category,inventory_item_id) VALUES (?,?,?,?,?,?,?,?,?)",
            ("l2", values["draft_key"], 1, "Filter", "4", "80", "50", "parts", "inv-2"),
        )
        conn.execute(
            "INSERT INTO invoice_editor_draft_maintenance_images (image_id,draft_key,local_uri,mime_type,byte_size,sort_order) VALUES (?,?,?,?,?,?)",
            ("i1", values["draft_key"], "content://draft/i1", "image/jpeg", 512, 0),
        )
        conn.commit()
        conn.close()

        # Close/reopen models process recreation at persistence level; the device script supplies real force-stop coverage.
        conn = sqlite3.connect(db_path)
        conn.execute("PRAGMA foreign_keys=ON")
        row = conn.execute("SELECT notes, write_id, draft_item_name FROM invoice_editor_drafts WHERE draft_key=?", (values["draft_key"],)).fetchone()
        require(row == ("keep-after-process-death", "write-1", "pending-line"), "draft header did not survive reopen")
        line_names = [r[0] for r in conn.execute("SELECT name FROM invoice_editor_draft_lines WHERE draft_key=? ORDER BY sort_order", (values["draft_key"],))]
        require(line_names == ["Brake pad", "Filter"], "draft line order/content lost across reopen")
        require(conn.execute("SELECT count(*) FROM invoice_editor_draft_maintenance_images WHERE draft_key=?", (values["draft_key"],)).fetchone()[0] == 1,
                "draft maintenance attachment metadata lost")

        conn.execute("DELETE FROM invoice_editor_drafts WHERE draft_key=?", (values["draft_key"],))
        require(conn.execute("SELECT count(*) FROM invoice_editor_draft_lines").fetchone()[0] == 0, "draft line cascade delete missing")
        require(conn.execute("SELECT count(*) FROM invoice_editor_draft_maintenance_images").fetchone()[0] == 0, "draft image cascade delete missing")
        conn.close()


def main() -> None:
    static_contracts()
    sqlite_contracts()
    print("PASS v254 invoice draft UX verifier")


if __name__ == "__main__":
    main()
