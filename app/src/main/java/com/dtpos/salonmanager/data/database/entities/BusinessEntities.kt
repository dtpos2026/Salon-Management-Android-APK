package com.dtpos.salonmanager.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.dtpos.salonmanager.domain.model.BudgetGroup
import com.dtpos.salonmanager.domain.model.TargetPeriod

/**
 * A salon / store. Version 1 runs a single business (id = 1), but every major table carries a
 * businessId so a future multi-salon edition needs no schema redesign.
 */
@Entity(tableName = "businesses")
data class BusinessEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    val logoPath: String? = null,
    val currencyCode: String = "PKR",
    val currencySymbol: String = "Rs.",
    val receiptPrefix: String = "SAL",
    val receiptHeaderNote: String? = null,
    val receiptFooter: String? = null,
    val showLogoOnReceipt: Boolean = true,
    val showStaffOnReceipt: Boolean = true,
    val isSetupComplete: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Per-business receipt counter. Incremented inside the sale transaction. */
@Entity(
    tableName = "receipt_sequences",
    foreignKeys = [ForeignKey(entity = BusinessEntity::class, parentColumns = ["id"], childColumns = ["businessId"], onDelete = ForeignKey.CASCADE)],
)
data class ReceiptSequenceEntity(
    @PrimaryKey val businessId: Long,
    val lastNumber: Long,
)

/** Sales targets: one row per business and period type. */
@Entity(
    tableName = "targets",
    indices = [Index(value = ["businessId", "period"], unique = true)],
    foreignKeys = [ForeignKey(entity = BusinessEntity::class, parentColumns = ["id"], childColumns = ["businessId"], onDelete = ForeignKey.CASCADE)],
)
data class TargetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long,
    val period: TargetPeriod,
    val amountMinor: Long,
    val updatedAt: Long,
)

/** Monthly budget per group (smart budget module). */
@Entity(
    tableName = "budgets",
    indices = [Index(value = ["businessId", "budgetGroup"], unique = true)],
    foreignKeys = [ForeignKey(entity = BusinessEntity::class, parentColumns = ["id"], childColumns = ["businessId"], onDelete = ForeignKey.CASCADE)],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long,
    val budgetGroup: BudgetGroup,
    val amountMinor: Long,
    val updatedAt: Long,
)

/** Installation-wide key/value settings (printer, security, preferences). */
@Entity(tableName = "app_settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long,
)

/** Licence record (single row, id = 1). */
@Entity(tableName = "license_info")
data class LicenseEntity(
    @PrimaryKey val id: Long = 1,
    val licenseKey: String?,
    val licenseId: String?,
    val businessName: String?,
    val plan: String?,
    val activatedAt: Long?,
    val expiresOn: String?,
    val status: String,
    val lastVerifiedAt: Long?,
    val updatedAt: Long,
)
