package com.dtpos.salonmanager.data.repository

import com.dtpos.salonmanager.data.database.dao.SettingsDao
import com.dtpos.salonmanager.data.database.entities.SettingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Keys for installation-wide settings stored in the app_settings table. */
object SettingKeys {
    const val INSTALLATION_ID = "install.id"
    const val FIRST_RUN_DATE = "install.firstRunDate"

    const val PRINTER_ADDRESS = "printer.address"
    const val PRINTER_NAME = "printer.name"
    const val PRINTER_PAPER = "printer.paper"
    const val PRINTER_MODE = "printer.mode"
    const val PRINTER_LOGO = "printer.logo"
    const val PRINTER_AUTO = "printer.autoPrint"
    const val PRINTER_COPIES = "printer.copies"
    const val PRINTER_FEED = "printer.feedLines"
    const val PRINTER_CUT = "printer.cut"

    const val SEC_LOCK_TYPE = "security.lockType"
    const val SEC_SECRET_HASH = "security.secretHash"
    const val SEC_RECOVERY_HASH = "security.recoveryHash"
    const val SEC_BIOMETRIC = "security.biometric"
    const val SEC_LOCK_ON_START = "security.lockOnStart"
    const val SEC_PROTECT_REPORTS = "security.protectReports"
    const val SEC_PROTECT_EXPENSES = "security.protectExpenses"
    const val SEC_PROTECT_SETTINGS = "security.protectSettings"
    const val SEC_FAILED_ATTEMPTS = "security.failedAttempts"
    const val SEC_LOCKOUT_UNTIL = "security.lockoutUntil"

    const val LICENSE_LAST_SEEN = "license.lastSeenDate"

    const val BACKUP_LAST_MANUAL = "backup.lastManualAt"
    const val BACKUP_LAST_AUTO = "backup.lastAutoAt"
    const val BACKUP_AUTO_ENABLED = "backup.autoEnabled"

    const val DEMO_DATA = "app.demoData"
}

class SettingsRepository(private val dao: SettingsDao) {

    suspend fun getString(key: String): String? = dao.get(key)

    fun observeString(key: String): Flow<String?> = dao.observe(key).distinctUntilChanged()

    suspend fun putString(key: String, value: String?) {
        if (value == null) dao.delete(key) else dao.put(SettingEntity(key, value, System.currentTimeMillis()))
    }

    suspend fun getBoolean(key: String, default: Boolean = false): Boolean =
        dao.get(key)?.toBooleanStrictOrNull() ?: default

    fun observeBoolean(key: String, default: Boolean = false): Flow<Boolean> =
        observeString(key).map { it?.toBooleanStrictOrNull() ?: default }

    suspend fun putBoolean(key: String, value: Boolean) = putString(key, value.toString())

    suspend fun getLong(key: String, default: Long = 0L): Long = dao.get(key)?.toLongOrNull() ?: default

    fun observeLong(key: String, default: Long = 0L): Flow<Long> = observeString(key).map { it?.toLongOrNull() ?: default }

    suspend fun putLong(key: String, value: Long) = putString(key, value.toString())

    suspend fun getInt(key: String, default: Int = 0): Int = dao.get(key)?.toIntOrNull() ?: default

    suspend fun putInt(key: String, value: Int) = putString(key, value.toString())

    fun observeAll(): Flow<Map<String, String>> = dao.observeAll().map { list -> list.associate { it.key to it.value } }
}
