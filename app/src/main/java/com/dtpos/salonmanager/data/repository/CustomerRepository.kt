package com.dtpos.salonmanager.data.repository

import com.dtpos.salonmanager.core.validation.Validators
import com.dtpos.salonmanager.data.database.SalonDatabase
import com.dtpos.salonmanager.data.database.entities.CustomerEntity
import com.dtpos.salonmanager.data.database.model.CustomerListRow
import com.dtpos.salonmanager.data.database.model.CustomerStatsRow
import com.dtpos.salonmanager.data.database.model.NamedTotalRow
import com.dtpos.salonmanager.data.database.model.VisitRow
import com.dtpos.salonmanager.domain.model.Gender
import kotlinx.coroutines.flow.Flow

/** Validated customer fields coming from the UI. */
data class CustomerInput(
    val name: String,
    val phone: String?,
    val gender: Gender,
    val dateOfBirthEpochDay: Long?,
    val address: String?,
    val notes: String?,
)

class CustomerRepository(
    private val db: SalonDatabase,
    private val businessId: Long,
) {
    private val dao = db.customerDao()

    fun search(query: String, limit: Int = DEFAULT_LIMIT): Flow<List<CustomerListRow>> {
        val digits = query.filter { it.isDigit() }
        val phonePattern = if (digits.length >= 2) "%$digits%" else null
        return dao.search(businessId, likePattern(query), phonePattern, limit)
    }

    fun observe(id: Long): Flow<CustomerEntity?> = dao.observe(id)

    fun observeStats(id: Long): Flow<CustomerStatsRow> = dao.observeStats(id)

    fun observeFavoriteServices(id: Long): Flow<List<NamedTotalRow>> = dao.observeFavoriteServices(id, 3)

    fun observeVisits(id: Long, limit: Int = 200): Flow<List<VisitRow>> = dao.observeVisits(id, limit)

    fun observeCount(): Flow<Int> = dao.observeCount(businessId)

    suspend fun get(id: Long): CustomerEntity? = dao.get(id)

    /** Inserts ([id] == null) or updates a customer. Phone numbers must be unique per salon. */
    suspend fun save(input: CustomerInput, id: Long? = null): DataResult<Long> = safeWrite {
        val normalized = Validators.normalizePhone(input.phone)
        if (normalized != null) {
            val existing = dao.findByPhone(businessId, normalized)
            if (existing != null && existing.id != id) return@safeWrite DataResult.Failure(DataError.DUPLICATE_PHONE)
        }
        val now = System.currentTimeMillis()
        if (id == null) {
            val newId = dao.insert(
                CustomerEntity(
                    businessId = businessId,
                    name = input.name,
                    phone = input.phone,
                    phoneNormalized = normalized,
                    gender = input.gender,
                    dateOfBirth = input.dateOfBirthEpochDay,
                    address = input.address,
                    notes = input.notes,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            DataResult.Success(newId)
        } else {
            val current = dao.get(id) ?: return@safeWrite DataResult.Failure(DataError.NOT_FOUND)
            dao.update(
                current.copy(
                    name = input.name,
                    phone = input.phone,
                    phoneNormalized = normalized,
                    gender = input.gender,
                    dateOfBirth = input.dateOfBirthEpochDay,
                    address = input.address,
                    notes = input.notes,
                    updatedAt = now,
                ),
            )
            DataResult.Success(id)
        }
    }

    /** Deletes the customer; past receipts keep the name snapshot and become "walk-in" linked. */
    suspend fun delete(id: Long): DataResult<Unit> = safeWrite {
        val current = dao.get(id) ?: return@safeWrite DataResult.Failure(DataError.NOT_FOUND)
        dao.delete(current)
        DataResult.Success(Unit)
    }

    suspend fun getAll(): List<CustomerEntity> = dao.getAll(businessId)

    companion object {
        const val DEFAULT_LIMIT = 100
    }
}
