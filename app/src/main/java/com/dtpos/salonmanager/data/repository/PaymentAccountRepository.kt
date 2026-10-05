package com.dtpos.salonmanager.data.repository

import com.dtpos.salonmanager.data.database.SalonDatabase
import com.dtpos.salonmanager.data.database.entities.PaymentAccountEntity
import com.dtpos.salonmanager.domain.model.AccountKind
import com.dtpos.salonmanager.domain.model.PaymentMethod
import kotlinx.coroutines.flow.Flow

/** JazzCash / EasyPaisa / bank / card accounts the salon receives money in. */
class PaymentAccountRepository(db: SalonDatabase, private val businessId: Long) {
    private val dao = db.paymentAccountDao()

    fun observeAll(): Flow<List<PaymentAccountEntity>> = dao.observeAll(businessId)

    fun observeActive(): Flow<List<PaymentAccountEntity>> = dao.observeActive(businessId)

    suspend fun get(id: Long): PaymentAccountEntity? = dao.get(id)

    suspend fun save(id: Long?, name: String, kind: AccountKind, title: String?, number: String?): DataResult<Long> = safeWrite {
        val clean = name.trim().take(40)
        if (clean.isEmpty()) return@safeWrite DataResult.Failure(DataError.INVALID)
        val existing = id?.let { dao.get(it) }
        if (existing != null) {
            dao.update(existing.copy(name = clean, kind = kind, accountTitle = title?.trim()?.take(60)?.ifEmpty { null }, accountNumber = number?.trim()?.take(40)?.ifEmpty { null }))
            DataResult.Success(existing.id)
        } else {
            DataResult.Success(
                dao.insert(
                    PaymentAccountEntity(
                        businessId = businessId,
                        name = clean,
                        kind = kind,
                        accountTitle = title?.trim()?.take(60)?.ifEmpty { null },
                        accountNumber = number?.trim()?.take(40)?.ifEmpty { null },
                        createdAt = System.currentTimeMillis(),
                    ),
                ),
            )
        }
    }

    suspend fun setActive(id: Long, active: Boolean): DataResult<Unit> = safeWrite {
        val account = dao.get(id) ?: return@safeWrite DataResult.Failure(DataError.NOT_FOUND)
        dao.update(account.copy(isActive = active))
        DataResult.Success(Unit)
    }

    /** An account used by sales is hidden instead of deleted, so old receipts keep their history. */
    suspend fun delete(id: Long): DataResult<Unit> = safeWrite {
        if (dao.usageCount(id) > 0) {
            dao.get(id)?.let { dao.update(it.copy(isActive = false)) }
        } else {
            dao.delete(id)
        }
        DataResult.Success(Unit)
    }

    companion object {
        /** The payment method a sale into this kind of account is reported under. */
        fun methodFor(kind: AccountKind): PaymentMethod = when (kind) {
            AccountKind.WALLET, AccountKind.BANK -> PaymentMethod.BANK
            AccountKind.CARD -> PaymentMethod.CARD
            AccountKind.OTHER -> PaymentMethod.OTHER
        }
    }
}
