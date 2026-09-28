package com.dtpos.salonmanager.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dtpos.salonmanager.data.database.entities.CashSessionEntity
import com.dtpos.salonmanager.data.database.entities.CashTransactionEntity
import com.dtpos.salonmanager.data.database.model.CashTypeTotalRow
import kotlinx.coroutines.flow.Flow

@Dao
interface CashDao {
    @Query("SELECT * FROM cash_sessions WHERE businessId = :businessId AND sessionDate = :day")
    suspend fun getSession(businessId: Long, day: Long): CashSessionEntity?

    @Query("SELECT * FROM cash_sessions WHERE businessId = :businessId AND sessionDate = :day")
    fun observeSession(businessId: Long, day: Long): Flow<CashSessionEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: CashSessionEntity): Long

    @Update
    suspend fun updateSession(session: CashSessionEntity)

    @Query("SELECT * FROM cash_sessions WHERE businessId = :businessId ORDER BY sessionDate DESC LIMIT :limit")
    fun observeRecentSessions(businessId: Long, limit: Int): Flow<List<CashSessionEntity>>

    @Query(
        """
        SELECT * FROM cash_sessions
        WHERE businessId = :businessId AND sessionDate < :day AND actualClosingMinor IS NOT NULL
        ORDER BY sessionDate DESC LIMIT 1
        """,
    )
    suspend fun lastClosedBefore(businessId: Long, day: Long): CashSessionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(tx: CashTransactionEntity): Long

    @Query("DELETE FROM cash_transactions WHERE id = :id AND referenceType IS NULL")
    suspend fun deleteManualTransaction(id: Long): Int

    @Query("DELETE FROM cash_transactions WHERE referenceType = :referenceType AND referenceId = :referenceId")
    suspend fun deleteByReference(referenceType: String, referenceId: Long): Int

    @Query(
        """
        SELECT type, COALESCE(SUM(amountMinor), 0) AS totalMinor FROM cash_transactions
        WHERE businessId = :businessId AND txDate = :day
        GROUP BY type
        """,
    )
    fun observeTotalsByType(businessId: Long, day: Long): Flow<List<CashTypeTotalRow>>

    @Query(
        """
        SELECT type, COALESCE(SUM(amountMinor), 0) AS totalMinor FROM cash_transactions
        WHERE businessId = :businessId AND txDate = :day
        GROUP BY type
        """,
    )
    suspend fun totalsByType(businessId: Long, day: Long): List<CashTypeTotalRow>

    @Query("SELECT * FROM cash_transactions WHERE businessId = :businessId AND txDate = :day ORDER BY createdAt DESC")
    fun observeTransactions(businessId: Long, day: Long): Flow<List<CashTransactionEntity>>
}
