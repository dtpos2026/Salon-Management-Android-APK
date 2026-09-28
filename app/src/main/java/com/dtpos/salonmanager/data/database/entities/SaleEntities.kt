package com.dtpos.salonmanager.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.dtpos.salonmanager.domain.model.DiscountType
import com.dtpos.salonmanager.domain.model.PaymentMethod
import com.dtpos.salonmanager.domain.model.SaleStatus

/**
 * A completed sale (the receipt). Customer name/phone are snapshotted so old receipts stay
 * correct even if the customer is edited or deleted later.
 */
@Entity(
    tableName = "sales",
    indices = [
        Index(value = ["businessId", "receiptNumber"], unique = true),
        Index(value = ["businessId", "businessDate"]),
        Index(value = ["customerId"]),
        Index(value = ["businessId", "createdAt"]),
    ],
    foreignKeys = [
        ForeignKey(entity = BusinessEntity::class, parentColumns = ["id"], childColumns = ["businessId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CustomerEntity::class, parentColumns = ["id"], childColumns = ["customerId"], onDelete = ForeignKey.SET_NULL),
    ],
)
data class SaleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long,
    val receiptNumber: String,
    val receiptSequence: Long,
    val customerId: Long?,
    val customerName: String?,
    val customerPhone: String?,
    /** Sum of unit price x quantity, before discounts. */
    val subtotalMinor: Long,
    val itemDiscountMinor: Long,
    val saleDiscountType: DiscountType,
    /** Amount (minor) or percent (basis points) as entered. */
    val saleDiscountValue: Long,
    val saleDiscountMinor: Long,
    val totalMinor: Long,
    val paymentMethod: PaymentMethod,
    val amountTenderedMinor: Long?,
    val changeMinor: Long,
    val serviceCount: Int,
    val note: String?,
    val status: SaleStatus,
    /** Business day (epoch day) the sale belongs to. */
    val businessDate: Long,
    val createdAt: Long,
    val voidedAt: Long? = null,
    val voidReason: String? = null,
)

@Entity(
    tableName = "sale_items",
    indices = [Index(value = ["saleId"]), Index(value = ["serviceId"]), Index(value = ["staffId"])],
    foreignKeys = [
        ForeignKey(entity = SaleEntity::class, parentColumns = ["id"], childColumns = ["saleId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ServiceEntity::class, parentColumns = ["id"], childColumns = ["serviceId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = StaffEntity::class, parentColumns = ["id"], childColumns = ["staffId"], onDelete = ForeignKey.SET_NULL),
    ],
)
data class SaleItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    val serviceId: Long?,
    val staffId: Long?,
    val serviceName: String,
    val staffName: String?,
    val unitPriceMinor: Long,
    val quantity: Int,
    val lineDiscountMinor: Long,
    /** Share of the sale-level discount allocated to this line. */
    val allocatedDiscountMinor: Long,
    /** Net revenue of the line after all discounts. */
    val netAmountMinor: Long,
    val commissionBps: Int,
    val commissionMinor: Long,
)

/** Customer visit record created automatically with every completed sale. */
@Entity(
    tableName = "visits",
    indices = [
        Index(value = ["saleId"], unique = true),
        Index(value = ["customerId", "visitAt"]),
        Index(value = ["businessId", "visitDate"]),
    ],
    foreignKeys = [
        ForeignKey(entity = BusinessEntity::class, parentColumns = ["id"], childColumns = ["businessId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SaleEntity::class, parentColumns = ["id"], childColumns = ["saleId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CustomerEntity::class, parentColumns = ["id"], childColumns = ["customerId"], onDelete = ForeignKey.SET_NULL),
    ],
)
data class VisitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long,
    val saleId: Long,
    val customerId: Long?,
    val visitDate: Long,
    val visitAt: Long,
    val servicesSummary: String,
    val staffSummary: String?,
    val totalMinor: Long,
)
