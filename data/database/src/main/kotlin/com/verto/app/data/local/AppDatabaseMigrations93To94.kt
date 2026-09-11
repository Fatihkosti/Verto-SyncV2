package com.verto.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Party V2 classification cutover.
 *
 * Android minSdk is 26, whose SQLite version cannot reliably DROP COLUMN. `clients` is also the
 * parent of multiple FK-constrained tables, so rebuilding it inside Room's migration transaction
 * can fail with FOREIGN KEY constraint errors. The safe cutover is therefore semantic:
 * - erase every legacy local classification token;
 * - retain only an inert compatibility column in Room;
 * - delete legacy role outbox entries.
 *
 * Runtime code has no accessor named clientTypes/clientType and all classification is Party V2.
 */
val MIGRATION_93_94 = object : Migration(93, 94) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE `clients` SET `clientType`='' WHERE `clientType`<>''")
        db.execSQL("DELETE FROM `party_sync_outbox` WHERE `aggregate_type`='ROLE'")
    }
}
