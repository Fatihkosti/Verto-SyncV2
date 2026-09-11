package com.verto.app.data.local.dao

import androidx.room.*
import com.verto.app.data.local.entity.CashMovementType
import com.verto.app.data.local.entity.CashRegisterEntity
import com.verto.app.data.local.entity.CashRegisterMovementEntity
import com.verto.app.money.Money
import kotlinx.coroutines.flow.Flow

// ─────────────────────────────────────────────────────────────────────────────
// تم تحويله من interface إلى abstract class لإتاحة دوال @Transaction مركّبة
// تضمن أن قراءة الرصيد + تحديثه + تسجيل الحركة تحدث في transaction واحدة
// لا يمكن فصلها، وبالتالي تُحلّ مشكلة Race Condition الحرجة #2
// ─────────────────────────────────────────────────────────────────────────────
@Dao
abstract class CashRegisterDao {

    // ── الصندوق الرئيسي ──────────────────────────────
    @Query("SELECT * FROM cash_register WHERE id = 'main' LIMIT 1")
    abstract fun getRegister(): Flow<CashRegisterEntity?>

    @Query("SELECT * FROM cash_register WHERE id = 'main' LIMIT 1")
    abstract suspend fun getRegisterSync(): CashRegisterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertRegister(register: CashRegisterEntity)

    @Query("UPDATE cash_register SET balance = :balance, balance_minor = :balanceMinor, updatedAt = :updatedAt WHERE id = 'main'")
    abstract suspend fun updateBalance(
        balance: Double,
        balanceMinor: Long = Money.fromLegacyDouble(balance).amountMinor,
        updatedAt: Long = System.currentTimeMillis(),
    ): Int

    // ── حركات الصندوق ────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertMovement(movement: CashRegisterMovementEntity): Long

    @Query("SELECT * FROM cash_register_movements ORDER BY createdAt DESC")
    abstract fun getAllMovements(): Flow<List<CashRegisterMovementEntity>>

    @Query("SELECT * FROM cash_register_movements ORDER BY createdAt ASC")
    abstract suspend fun getAllMovementsSync(): List<CashRegisterMovementEntity>

    @Query("SELECT * FROM cash_register_movements WHERE id = :id LIMIT 1")
    abstract suspend fun getMovementByIdSync(id: String): CashRegisterMovementEntity?

    @Query("SELECT COUNT(*) FROM cash_register_movements WHERE movementType = :type AND referenceId = :referenceId")
    abstract suspend fun countMovementsByTypeAndReference(type: CashMovementType, referenceId: String): Int

    @Query("SELECT * FROM cash_register_movements WHERE createdAt BETWEEN :from AND :to ORDER BY createdAt DESC")
    abstract fun getMovementsByRange(from: Long, to: Long): Flow<List<CashRegisterMovementEntity>>

    @Query("SELECT * FROM cash_register_movements ORDER BY createdAt DESC LIMIT :limit")
    abstract fun getRecentMovements(limit: Int = 50): Flow<List<CashRegisterMovementEntity>>

    @Query("DELETE FROM cash_register_movements WHERE id = :id")
    abstract suspend fun deleteMovement(id: String)

    // ─────────────────────────────────────────────────────────────────────────
    // ✅ الإصلاح الجوهري — الثغرة #2 + #3 (CashRegister):
    //
    // recordMovementAtomic: تُنفِّذ قراءة الرصيد + تحديثه + إدراج الحركة
    // داخل transaction واحدة غير قابلة للتجزئة.
    //
    // ✅ الإصلاح — الثغرة #3 (Double خام):
    // الرصيد الجديد يُحسب عبر MoneyMath.add (BigDecimal داخلياً)
    // بدلاً من current.balance + signedAmount (Double خام).
    // هذا يمنع تراكم أخطاء التقريب مع كل حركة صندوق.
    // ─────────────────────────────────────────────────────────────────────────
    /** Fixed-point cash write used by new financial flows. Double columns are compatibility projections only. */
    @Transaction
    open suspend fun recordMovementAtomicMinor(
        type: CashMovementType,
        amountMinor: Long,
        referenceId: String = "",
        note: String = "",
        sourceType: String = "",
        sourceId: String = referenceId,
        sourceVersion: Int = 1,
        writeId: String = "",
    ) {
        require(amountMinor > 0L) { "Cash movement amount must be positive" }
        val current = getRegisterSync()
            ?: CashRegisterEntity(balance = 0.0, balanceMinor = 0L).also { upsertRegister(it) }
        val beforeMinor = current.balanceMinor
        val signedMinor = when (type) {
            CashMovementType.SALE_CASH,
            CashMovementType.PAYMENT_RECEIVED,
            CashMovementType.MANUAL_ADD -> amountMinor
            CashMovementType.PURCHASE_CASH,
            CashMovementType.PAYMENT_MADE,
            CashMovementType.EXPENSE,
            CashMovementType.MANUAL_DEDUCT -> Math.negateExact(amountMinor)
        }
        val afterMinor = Math.addExact(beforeMinor, signedMinor)
        val before = Money.ofMinor(beforeMinor)
        val signed = Money.ofMinor(signedMinor)
        val after = Money.ofMinor(afterMinor)

        val affected = updateBalance(after.toLegacyDouble(), afterMinor)
        check(affected == 1) { "cash register update affected $affected rows; expected exactly one" }
        val rowId = insertMovement(
            CashRegisterMovementEntity(
                movementType = type,
                amount = signed.toLegacyDouble(),
                amountMinor = signedMinor,
                balanceBefore = before.toLegacyDouble(),
                balanceBeforeMinor = beforeMinor,
                balanceAfter = after.toLegacyDouble(),
                balanceAfterMinor = afterMinor,
                referenceId = referenceId,
                note = note,
                sourceType = sourceType,
                sourceId = sourceId,
                sourceVersion = sourceVersion,
                writeId = writeId,
            )
        )
        check(rowId != -1L) { "cash movement insert was ignored unexpectedly" }
    }

    @Transaction
    open suspend fun recordMovementAtomic(
        type: CashMovementType,
        amount: Double,           // موجب دائماً — الاتجاه يُحدد من النوع
        referenceId: String = "",
        note: String = "",
        sourceType: String = "",
        sourceId: String = referenceId,
        sourceVersion: Int = 1,
        writeId: String = "",
    ) {
        val amountMoney = Money.fromLegacyDouble(amount)
        require(amountMoney.isPositive()) { "Cash movement amount must be positive" }
        // The minor-unit column is the financial source of truth after schema 62.
        val current = getRegisterSync()
            ?: CashRegisterEntity(balance = 0.0, balanceMinor = 0L).also { upsertRegister(it) }
        val before = Money.ofMinor(current.balanceMinor)
        val signedAmount = when (type) {
            CashMovementType.SALE_CASH,
            CashMovementType.PAYMENT_RECEIVED,
            CashMovementType.MANUAL_ADD -> amountMoney
            CashMovementType.PURCHASE_CASH,
            CashMovementType.PAYMENT_MADE,
            CashMovementType.EXPENSE,
            CashMovementType.MANUAL_DEDUCT -> amountMoney.negate()
        }
        val after = before + signedAmount

        val affected = updateBalance(after.toLegacyDouble(), after.amountMinor)
        check(affected == 1) { "cash register update affected $affected rows; expected exactly one" }
        val rowId = insertMovement(
            CashRegisterMovementEntity(
                movementType = type,
                amount = signedAmount.toLegacyDouble(),
                amountMinor = signedAmount.amountMinor,
                balanceBefore = before.toLegacyDouble(),
                balanceBeforeMinor = before.amountMinor,
                balanceAfter = after.toLegacyDouble(),
                balanceAfterMinor = after.amountMinor,
                referenceId = referenceId,
                note = note,
                sourceType = sourceType,
                sourceId = sourceId,
                sourceVersion = sourceVersion,
                writeId = writeId,
            )
        )
        check(rowId != -1L) { "cash movement insert was ignored unexpectedly" }
    }

    // ── إعادة التعيين ─────────────────────────────────
    @Query("DELETE FROM cash_register_movements")
    abstract suspend fun deleteAllMovements()

    @Transaction
    open suspend fun resetRegister() { deleteAllMovements(); updateBalance(0.0, 0L) }
    @Update(entity = CashRegisterMovementEntity::class)
    abstract suspend fun reconcileMovementAuthoritativeProjection(projection: CashMovementProjectionUpdate336): Int
}
