package com.dtpos.salonmanager.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dtpos.salonmanager.data.database.entities.BudgetEntity
import com.dtpos.salonmanager.data.database.entities.BusinessEntity
import com.dtpos.salonmanager.data.database.entities.LicenseEntity
import com.dtpos.salonmanager.data.database.entities.ReceiptSequenceEntity
import com.dtpos.salonmanager.data.database.entities.SettingEntity
import com.dtpos.salonmanager.data.database.entities.TargetEntity
import com.dtpos.salonmanager.domain.model.TargetPeriod
import kotlinx.coroutines.flow.Flow

@Dao
interface BusinessDao {
    @Query("SELECT * FROM businesses WHERE id = :id")
    fun observe(id: Long): Flow<BusinessEntity?>

    @Query("SELECT * FROM businesses WHERE id = :id")
    suspend fun get(id: Long): BusinessEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(business: BusinessEntity): Long

    @Update
    suspend fun update(business: BusinessEntity)

    @Query("SELECT * FROM receipt_sequences WHERE businessId = :businessId")
    suspend fun getSequence(businessId: Long): ReceiptSequenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSequence(sequence: ReceiptSequenceEntity)
}

@Dao
interface SettingsDao {
    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Query("SELECT * FROM app_settings")
    fun observeAll(): Flow<List<SettingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: SettingEntity)

    @Query("DELETE FROM app_settings WHERE `key` = :key")
    suspend fun delete(key: String)
}

@Dao
interface LicenseDao {
    @Query("SELECT * FROM license_info WHERE id = 1")
    suspend fun get(): LicenseEntity?

    @Query("SELECT * FROM license_info WHERE id = 1")
    fun observe(): Flow<LicenseEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(license: LicenseEntity)
}

@Dao
interface TargetDao {
    @Query("SELECT * FROM targets WHERE businessId = :businessId")
    fun observeAll(businessId: Long): Flow<List<TargetEntity>>

    @Query("SELECT * FROM targets WHERE businessId = :businessId AND period = :period")
    suspend fun get(businessId: Long, period: TargetPeriod): TargetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(target: TargetEntity)

    @Query("SELECT * FROM budgets WHERE businessId = :businessId")
    fun observeBudgets(businessId: Long): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE businessId = :businessId")
    suspend fun getBudgets(businessId: Long): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(budget: BudgetEntity)
}
