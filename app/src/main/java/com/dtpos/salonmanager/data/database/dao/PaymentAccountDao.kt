package com.dtpos.salonmanager.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.dtpos.salonmanager.data.database.entities.PaymentAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentAccountDao {
    @Insert
    suspend fun insert(account: PaymentAccountEntity): Long

    @Update
    suspend fun update(account: PaymentAccountEntity)

    @Query("SELECT * FROM payment_accounts WHERE id = :id")
    suspend fun get(id: Long): PaymentAccountEntity?

    @Query("SELECT * FROM payment_accounts WHERE businessId = :businessId ORDER BY isActive DESC, sortOrder, name")
    fun observeAll(businessId: Long): Flow<List<PaymentAccountEntity>>

    @Query("SELECT * FROM payment_accounts WHERE businessId = :businessId AND isActive = 1 ORDER BY sortOrder, name")
    fun observeActive(businessId: Long): Flow<List<PaymentAccountEntity>>

    @Query("SELECT COUNT(*) FROM sales WHERE paymentAccountId = :id")
    suspend fun usageCount(id: Long): Int

    @Query("DELETE FROM payment_accounts WHERE id = :id")
    suspend fun delete(id: Long)
}
