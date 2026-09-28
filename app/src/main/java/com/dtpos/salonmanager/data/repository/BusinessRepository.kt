package com.dtpos.salonmanager.data.repository

import androidx.room.withTransaction
import com.dtpos.salonmanager.core.util.CurrencyConfig
import com.dtpos.salonmanager.data.database.DefaultData
import com.dtpos.salonmanager.data.database.SalonDatabase
import com.dtpos.salonmanager.data.database.entities.BusinessEntity
import com.dtpos.salonmanager.data.database.entities.ReceiptSequenceEntity
import com.dtpos.salonmanager.domain.calc.ReceiptNumbering
import com.dtpos.salonmanager.domain.model.BusinessProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class BusinessRepository(
    private val db: SalonDatabase,
    private val businessId: Long,
) {
    private val dao = db.businessDao()

    val profile: Flow<BusinessProfile?> = dao.observe(businessId).map { it?.toProfile() }.distinctUntilChanged()

    suspend fun getProfile(): BusinessProfile? = dao.get(businessId)?.toProfile()

    /** Creates the business row, receipt counter and default expense categories on first launch. */
    suspend fun ensureInitialized() {
        db.withTransaction {
            if (dao.get(businessId) == null) {
                val now = System.currentTimeMillis()
                dao.insert(
                    BusinessEntity(
                        id = businessId,
                        name = "",
                        receiptPrefix = ReceiptNumbering.DEFAULT_PREFIX,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
            if (dao.getSequence(businessId) == null) {
                dao.upsertSequence(ReceiptSequenceEntity(businessId, 0))
            }
            db.expenseDao().insertCategoriesIgnoringDuplicates(
                DefaultData.expenseCategoryEntities(businessId, System.currentTimeMillis()),
            )
        }
    }

    suspend fun completeSetup(
        name: String,
        phone: String?,
        address: String?,
        currency: CurrencyConfig,
        addDefaultServices: Boolean,
    ) {
        db.withTransaction {
            val current = dao.get(businessId) ?: error("business missing")
            val now = System.currentTimeMillis()
            dao.update(
                current.copy(
                    name = name,
                    phone = phone,
                    address = address,
                    currencyCode = currency.code,
                    currencySymbol = currency.symbol,
                    isSetupComplete = true,
                    updatedAt = now,
                ),
            )
            if (addDefaultServices) {
                db.serviceDao().insertAllIgnoringDuplicates(DefaultData.serviceEntities(businessId, now))
            }
        }
    }

    suspend fun updateProfile(name: String, phone: String?, address: String?) = edit {
        it.copy(name = name, phone = phone, address = address)
    }

    suspend fun updateCurrency(currency: CurrencyConfig) = edit {
        it.copy(currencyCode = currency.code, currencySymbol = currency.symbol)
    }

    suspend fun updateReceiptSettings(
        prefix: String,
        headerNote: String?,
        footer: String?,
        showLogo: Boolean,
        showStaff: Boolean,
    ) = edit {
        it.copy(
            receiptPrefix = prefix,
            receiptHeaderNote = headerNote,
            receiptFooter = footer,
            showLogoOnReceipt = showLogo,
            showStaffOnReceipt = showStaff,
        )
    }

    suspend fun setLogoPath(path: String?) = edit { it.copy(logoPath = path) }

    /** Preview of the next receipt number (the real one is assigned inside the sale transaction). */
    suspend fun nextReceiptNumberPreview(prefix: String? = null): String {
        val business = dao.get(businessId)
        val last = maxOf(dao.getSequence(businessId)?.lastNumber ?: 0L, db.saleDao().maxReceiptSequence(businessId) ?: 0L)
        return ReceiptNumbering.format(prefix ?: business?.receiptPrefix ?: ReceiptNumbering.DEFAULT_PREFIX, last + 1)
    }

    private suspend fun edit(transform: (BusinessEntity) -> BusinessEntity) {
        val current = dao.get(businessId) ?: return
        dao.update(transform(current).copy(updatedAt = System.currentTimeMillis()))
    }
}

internal fun BusinessEntity.toProfile() = BusinessProfile(
    id = id,
    name = name,
    phone = phone,
    address = address,
    logoPath = logoPath,
    currency = CurrencyConfig(currencyCode, currencySymbol),
    receiptPrefix = receiptPrefix,
    receiptHeaderNote = receiptHeaderNote,
    receiptFooter = receiptFooter,
    showLogoOnReceipt = showLogoOnReceipt,
    showStaffOnReceipt = showStaffOnReceipt,
    isSetupComplete = isSetupComplete,
)
