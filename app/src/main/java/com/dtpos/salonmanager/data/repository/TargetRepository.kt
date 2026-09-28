package com.dtpos.salonmanager.data.repository

import com.dtpos.salonmanager.data.database.SalonDatabase
import com.dtpos.salonmanager.data.database.entities.BudgetEntity
import com.dtpos.salonmanager.data.database.entities.TargetEntity
import com.dtpos.salonmanager.domain.model.BudgetGroup
import com.dtpos.salonmanager.domain.model.TargetPeriod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TargetRepository(
    private val db: SalonDatabase,
    private val businessId: Long,
) {
    private val dao = db.targetDao()

    fun observeTargets(): Flow<Map<TargetPeriod, Long>> =
        dao.observeAll(businessId).map { rows -> rows.associate { it.period to it.amountMinor } }

    suspend fun setTarget(period: TargetPeriod, amountMinor: Long): DataResult<Unit> = safeWrite {
        if (amountMinor < 0) return@safeWrite DataResult.Failure(DataError.INVALID)
        val existing = dao.get(businessId, period)
        dao.upsert(
            TargetEntity(
                id = existing?.id ?: 0,
                businessId = businessId,
                period = period,
                amountMinor = amountMinor,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        DataResult.Success(Unit)
    }

    fun observeBudgets(): Flow<Map<BudgetGroup, Long>> =
        dao.observeBudgets(businessId).map { rows -> rows.associate { it.budgetGroup to it.amountMinor } }

    suspend fun setBudget(group: BudgetGroup, amountMinor: Long): DataResult<Unit> = safeWrite {
        if (amountMinor < 0) return@safeWrite DataResult.Failure(DataError.INVALID)
        val existing = dao.getBudgets(businessId).firstOrNull { it.budgetGroup == group }
        dao.upsertBudget(
            BudgetEntity(
                id = existing?.id ?: 0,
                businessId = businessId,
                budgetGroup = group,
                amountMinor = amountMinor,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        DataResult.Success(Unit)
    }
}
