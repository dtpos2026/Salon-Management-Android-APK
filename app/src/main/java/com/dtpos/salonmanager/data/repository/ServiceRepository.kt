package com.dtpos.salonmanager.data.repository

import com.dtpos.salonmanager.data.database.DefaultData
import com.dtpos.salonmanager.data.database.SalonDatabase
import com.dtpos.salonmanager.data.database.entities.ServiceEntity
import kotlinx.coroutines.flow.Flow

data class ServiceInput(
    val name: String,
    val category: String,
    val priceMinor: Long,
    val durationMinutes: Int,
    val isActive: Boolean,
)

class ServiceRepository(
    private val db: SalonDatabase,
    private val businessId: Long,
) {
    private val dao = db.serviceDao()

    fun observeAll(query: String = ""): Flow<List<ServiceEntity>> = dao.observeAll(businessId, likePattern(query))

    fun observeActive(): Flow<List<ServiceEntity>> = dao.observeActive(businessId)

    fun observeCategories(): Flow<List<String>> = dao.observeCategories(businessId)

    suspend fun get(id: Long): ServiceEntity? = dao.get(id)

    suspend fun save(input: ServiceInput, id: Long? = null): DataResult<Long> = safeWrite {
        val duplicate = dao.findByName(businessId, input.name)
        if (duplicate != null && duplicate.id != id) return@safeWrite DataResult.Failure(DataError.DUPLICATE_NAME)
        val now = System.currentTimeMillis()
        if (id == null) {
            DataResult.Success(
                dao.insert(
                    ServiceEntity(
                        businessId = businessId,
                        name = input.name,
                        category = input.category,
                        priceMinor = input.priceMinor,
                        durationMinutes = input.durationMinutes,
                        isActive = input.isActive,
                        sortOrder = dao.count(businessId),
                        createdAt = now,
                        updatedAt = now,
                    ),
                ),
            )
        } else {
            val current = dao.get(id) ?: return@safeWrite DataResult.Failure(DataError.NOT_FOUND)
            dao.update(
                current.copy(
                    name = input.name,
                    category = input.category,
                    priceMinor = input.priceMinor,
                    durationMinutes = input.durationMinutes,
                    isActive = input.isActive,
                    updatedAt = now,
                ),
            )
            DataResult.Success(id)
        }
    }

    suspend fun setActive(id: Long, active: Boolean): DataResult<Unit> = safeWrite {
        val current = dao.get(id) ?: return@safeWrite DataResult.Failure(DataError.NOT_FOUND)
        dao.update(current.copy(isActive = active, updatedAt = System.currentTimeMillis()))
        DataResult.Success(Unit)
    }

    /** Deleting is safe for history: sale items keep the service name and price snapshot. */
    suspend fun delete(id: Long): DataResult<Unit> = safeWrite {
        val current = dao.get(id) ?: return@safeWrite DataResult.Failure(DataError.NOT_FOUND)
        dao.delete(current)
        DataResult.Success(Unit)
    }

    suspend fun isUsedInSales(id: Long): Boolean = dao.countUsage(id) > 0

    suspend fun addDefaultServices() {
        dao.insertAllIgnoringDuplicates(DefaultData.serviceEntities(businessId, System.currentTimeMillis()))
    }
}
