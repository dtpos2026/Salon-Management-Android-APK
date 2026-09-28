package com.dtpos.salonmanager.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.dtpos.salonmanager.domain.model.Gender
import com.dtpos.salonmanager.domain.model.SalaryType
import com.dtpos.salonmanager.domain.model.StaffRole

@Entity(
    tableName = "customers",
    indices = [
        Index(value = ["businessId", "name"]),
        // SQLite allows many NULLs in a unique index, so customers without a phone are fine.
        Index(value = ["businessId", "phoneNormalized"], unique = true),
        Index(value = ["businessId", "createdAt"]),
    ],
    foreignKeys = [ForeignKey(entity = BusinessEntity::class, parentColumns = ["id"], childColumns = ["businessId"], onDelete = ForeignKey.CASCADE)],
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long,
    val name: String,
    val phone: String? = null,
    val phoneNormalized: String? = null,
    val gender: Gender = Gender.UNSPECIFIED,
    /** Epoch day, optional. */
    val dateOfBirth: Long? = null,
    val address: String? = null,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "services",
    indices = [
        Index(value = ["businessId", "name"], unique = true),
        Index(value = ["businessId", "isActive", "category"]),
    ],
    foreignKeys = [ForeignKey(entity = BusinessEntity::class, parentColumns = ["id"], childColumns = ["businessId"], onDelete = ForeignKey.CASCADE)],
)
data class ServiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long,
    val name: String,
    val category: String,
    val priceMinor: Long,
    val durationMinutes: Int = 0,
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "staff",
    indices = [Index(value = ["businessId", "isActive", "name"])],
    foreignKeys = [ForeignKey(entity = BusinessEntity::class, parentColumns = ["id"], childColumns = ["businessId"], onDelete = ForeignKey.CASCADE)],
)
data class StaffEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: Long,
    val name: String,
    val phone: String? = null,
    val role: StaffRole = StaffRole.BARBER,
    val salaryType: SalaryType = SalaryType.COMMISSION,
    /** Basis points: 3000 = 30%. */
    val commissionBps: Int = 0,
    /** Monthly fixed salary in minor units. */
    val fixedSalaryMinor: Long = 0,
    val isActive: Boolean = true,
    /** Epoch day. */
    val joinedOn: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
