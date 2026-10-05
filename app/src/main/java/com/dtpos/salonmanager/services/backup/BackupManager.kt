package com.dtpos.salonmanager.services.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.dtpos.salonmanager.BuildConfig
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.data.database.SalonDatabase
import com.dtpos.salonmanager.data.repository.SettingKeys
import com.dtpos.salonmanager.data.repository.SettingsRepository
import com.dtpos.salonmanager.services.branding.LogoStore
import com.dtpos.salonmanager.services.branding.ServiceImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupManifest(
    val format: Int,
    val appVersion: String,
    val databaseVersion: Int,
    val createdAt: Long,
    val businessName: String?,
    val databaseSha256: String,
)

data class LocalBackup(val file: File, val createdAt: Long, val sizeBytes: Long)

sealed interface BackupInspection {
    /** The file is password protected; call [BackupManager.prepareRestore] with a password. */
    data object NeedsPassword : BackupInspection
    data class Ready(val prepared: PreparedRestore) : BackupInspection
    data class Invalid(val reason: BackupError) : BackupInspection
}

enum class BackupError {
    NOT_A_BACKUP,
    WRONG_PASSWORD,
    CORRUPT,
    NEWER_APP_VERSION,
    READ_FAILED,
    WRITE_FAILED,
    RESTORE_FAILED,
}

/** A validated backup waiting for the owner's confirmation. */
class PreparedRestore internal constructor(
    val manifest: BackupManifest,
    internal val databaseFile: File,
    internal val logoBytes: ByteArray?,
    /** Service menu photos: relative path ("menu/<name>.jpg") to bytes. */
    internal val menuImages: Map<String, ByteArray> = emptyMap(),
)

/**
 * Local backup & restore. Backup files ("*.salonbak") are zip archives containing the SQLite
 * database, the salon logo and a manifest with a SHA-256 checksum, optionally encrypted
 * with a password (AES-256-GCM, see [BackupCrypto]). Nothing ever leaves the phone unless the
 * owner saves or shares the file.
 */
class BackupManager(
    private val context: Context,
    private val database: () -> SalonDatabase,
    private val closeDatabase: () -> Unit,
    private val settings: SettingsRepository,
    private val logoStore: LogoStore,
) {
    private val mutex = Mutex()
    private val backupDir: File get() = File(context.filesDir, "backups").apply { mkdirs() }
    private val restoreDir: File get() = File(context.cacheDir, "restore").apply { mkdirs() }

    // ---- Create ----------------------------------------------------------------------------

    suspend fun createBackupBytes(password: CharArray?): ByteArray = withContext(Dispatchers.IO) {
        mutex.withLock {
            val db = database()
            // Flush the write-ahead log into the main file so the copy is complete and consistent.
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            val dbFile = context.getDatabasePath(SalonDatabase.NAME)
            val dbBytes = dbFile.readBytes()
            val walFile = File(dbFile.path + "-wal")
            val walBytes = if (walFile.isFile && walFile.length() > 0) walFile.readBytes() else null
            val businessName = try {
                db.businessDao().get(1L)?.name
            } catch (e: Exception) {
                null
            }
            val manifest = BackupManifest(
                format = FORMAT_VERSION,
                appVersion = BuildConfig.VERSION_NAME,
                databaseVersion = SalonDatabase.VERSION,
                createdAt = System.currentTimeMillis(),
                businessName = businessName,
                databaseSha256 = sha256(dbBytes),
            )
            val zipBytes = ByteArrayOutputStream().use { buffer ->
                ZipOutputStream(buffer).use { zip ->
                    zip.put(ENTRY_MANIFEST, manifest.toJson().toByteArray(Charsets.UTF_8))
                    zip.put(ENTRY_DB, dbBytes)
                    walBytes?.let { zip.put(ENTRY_WAL, it) }
                    logoStore.resolve(LogoStore.RELATIVE_PATH)?.let { zip.put(ENTRY_LOGO, it.readBytes()) }
                    ServiceImageStore(context).all().forEach { (path, file) -> zip.put(path, file.readBytes()) }
                }
                buffer.toByteArray()
            }
            if (password != null && password.isNotEmpty()) BackupCrypto.encrypt(zipBytes, password) else zipBytes
        }
    }

    /** Writes a backup to a document the owner picked (Storage Access Framework). */
    suspend fun exportTo(uri: Uri, password: CharArray?): BackupError? = try {
        val bytes = createBackupBytes(password)
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: throw IOException("no stream")
        }
        settings.putLong(SettingKeys.BACKUP_LAST_MANUAL, System.currentTimeMillis())
        null
    } catch (e: Exception) {
        BackupError.WRITE_FAILED
    }

    /** Saves a backup inside app storage (automatic daily backups and safety copies). */
    suspend fun createLocalBackup(tag: String): File? = try {
        val bytes = createBackupBytes(password = null)
        withContext(Dispatchers.IO) {
            val file = File(backupDir, "SalonBackup_${DateTimeUtils.fileStamp()}_$tag.$EXTENSION")
            file.writeBytes(bytes)
            prune()
            file
        }
    } catch (e: Exception) {
        null
    }

    fun localBackups(): List<LocalBackup> =
        (backupDir.listFiles { f -> f.isFile && f.name.endsWith(".$EXTENSION") } ?: emptyArray())
            .sortedByDescending { it.lastModified() }
            .map { LocalBackup(it, it.lastModified(), it.length()) }

    /** Runs at most once a day on app start when enabled (default on). */
    suspend fun runAutoBackupIfDue() {
        if (!settings.getBoolean(SettingKeys.BACKUP_AUTO_ENABLED, default = true)) return
        val last = settings.getLong(SettingKeys.BACKUP_LAST_AUTO)
        if (System.currentTimeMillis() - last < AUTO_INTERVAL_MS) return
        if (createLocalBackup("auto") != null) {
            settings.putLong(SettingKeys.BACKUP_LAST_AUTO, System.currentTimeMillis())
        }
    }

    private fun prune() {
        localBackups().drop(MAX_LOCAL_BACKUPS).forEach { it.file.delete() }
    }

    // ---- Restore ---------------------------------------------------------------------------

    suspend fun readUri(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_BACKUP_BYTES) return@use null
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Decrypts (if needed) and fully validates a backup before anything is replaced. */
    suspend fun prepareRestore(data: ByteArray, password: CharArray?): BackupInspection = withContext(Dispatchers.IO) {
        val zipBytes = if (BackupCrypto.isEncrypted(data)) {
            if (password == null || password.isEmpty()) return@withContext BackupInspection.NeedsPassword
            try {
                BackupCrypto.decrypt(data, password)
            } catch (e: BackupCryptoException) {
                return@withContext BackupInspection.Invalid(
                    if (e.reason == BackupCryptoException.Reason.WRONG_PASSWORD) BackupError.WRONG_PASSWORD else BackupError.CORRUPT,
                )
            }
        } else {
            data
        }

        val entries = try {
            unzip(zipBytes)
        } catch (e: Exception) {
            return@withContext BackupInspection.Invalid(BackupError.NOT_A_BACKUP)
        }
        val manifest = entries[ENTRY_MANIFEST]?.let { parseManifest(it) }
            ?: return@withContext BackupInspection.Invalid(BackupError.NOT_A_BACKUP)
        val dbBytes = entries[ENTRY_DB] ?: return@withContext BackupInspection.Invalid(BackupError.NOT_A_BACKUP)
        if (manifest.format > FORMAT_VERSION || manifest.databaseVersion > SalonDatabase.VERSION) {
            return@withContext BackupInspection.Invalid(BackupError.NEWER_APP_VERSION)
        }
        if (!dbBytes.startsWith(SQLITE_HEADER) || sha256(dbBytes) != manifest.databaseSha256) {
            return@withContext BackupInspection.Invalid(BackupError.CORRUPT)
        }

        // Materialise and check the database in a scratch location.
        restoreDir.listFiles()?.forEach { it.delete() }
        val candidate = File(restoreDir, "candidate.db")
        candidate.writeBytes(dbBytes)
        entries[ENTRY_WAL]?.let { File(candidate.path + "-wal").writeBytes(it) }
        val valid = try {
            SQLiteDatabase.openDatabase(candidate.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                val integrity = db.rawQuery("PRAGMA integrity_check", null).use { c -> if (c.moveToFirst()) c.getString(0) else null }
                val version = db.version
                val hasTables = db.rawQuery(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('sales','customers','businesses')",
                    null,
                ).use { c -> c.moveToFirst() && c.getInt(0) == 3 }
                // Merge the WAL so the candidate becomes a single self-contained file.
                db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
                integrity == "ok" && hasTables && version in 1..SalonDatabase.VERSION
            }
        } catch (e: Exception) {
            false
        }
        File(candidate.path + "-wal").delete()
        File(candidate.path + "-shm").delete()
        if (!valid) {
            candidate.delete()
            return@withContext BackupInspection.Invalid(BackupError.CORRUPT)
        }
        BackupInspection.Ready(
            PreparedRestore(manifest, candidate, entries[ENTRY_LOGO], entries.filterKeys { ServiceImageStore.isValidPath(it) }),
        )
    }

    /**
     * Replaces the current data with [prepared]. A safety backup of the current data is written
     * first and the old files are kept until the new database is in place, so a failure rolls
     * back automatically. The app must restart afterwards (see AppRestarter).
     */
    suspend fun applyRestore(prepared: PreparedRestore): BackupError? = withContext(Dispatchers.IO) {
        createLocalBackup("before-restore") ?: return@withContext BackupError.WRITE_FAILED
        mutex.withLock {
            val dbFile = context.getDatabasePath(SalonDatabase.NAME)
            val companions = listOf("", "-wal", "-shm", "-journal").map { File(dbFile.path + it) }
            val parked = companions.map { File(it.path + ".pre-restore") }
            try {
                closeDatabase()
                parked.forEach { it.delete() }
                companions.forEachIndexed { i, f -> if (f.exists() && !f.renameTo(parked[i])) throw IOException("rename failed") }
                prepared.databaseFile.copyTo(dbFile, overwrite = true)
                prepared.logoBytes?.let { bytes ->
                    val logo = File(context.filesDir, LogoStore.RELATIVE_PATH)
                    logo.parentFile?.mkdirs()
                    logo.writeBytes(bytes)
                }
                prepared.menuImages.forEach { (path, bytes) ->
                    if (ServiceImageStore.isValidPath(path)) {
                        File(context.filesDir, path).apply { parentFile?.mkdirs() }.writeBytes(bytes)
                    }
                }
                parked.forEach { it.delete() }
                prepared.databaseFile.delete()
                null
            } catch (e: Exception) {
                // Roll back to the previous database files.
                companions.forEach { it.delete() }
                parked.forEachIndexed { i, f -> if (f.exists()) f.renameTo(companions[i]) }
                BackupError.RESTORE_FAILED
            }
        }
    }

    // ---- Helpers ---------------------------------------------------------------------------

    private fun ZipOutputStream.put(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val result = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                // Only accept the known flat entry names (prevents zip path traversal).
                if (!entry.isDirectory && (entry.name in KNOWN_ENTRIES || ServiceImageStore.isValidPath(entry.name))) {
                    result[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        if (result.isEmpty()) throw IOException("empty archive")
        return result
    }

    private fun BackupManifest.toJson(): String = JSONObject()
        .put("format", format)
        .put("appVersion", appVersion)
        .put("databaseVersion", databaseVersion)
        .put("createdAt", createdAt)
        .put("businessName", businessName ?: JSONObject.NULL)
        .put("databaseSha256", databaseSha256)
        .toString(2)

    private fun parseManifest(bytes: ByteArray): BackupManifest? = try {
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        BackupManifest(
            format = json.getInt("format"),
            appVersion = json.optString("appVersion"),
            databaseVersion = json.getInt("databaseVersion"),
            createdAt = json.getLong("createdAt"),
            businessName = if (json.isNull("businessName")) null else json.optString("businessName"),
            databaseSha256 = json.getString("databaseSha256"),
        )
    } catch (e: Exception) {
        null
    }

    private fun ByteArray.startsWith(prefix: ByteArray) = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    companion object {
        const val EXTENSION = "salonbak"
        const val MIME_TYPE = "application/octet-stream"
        private const val FORMAT_VERSION = 1
        private const val ENTRY_MANIFEST = "manifest.json"
        private const val ENTRY_DB = "database/salon.db"
        private const val ENTRY_WAL = "database/salon.db-wal"
        private const val ENTRY_LOGO = "branding/logo.png"
        private val KNOWN_ENTRIES = setOf(ENTRY_MANIFEST, ENTRY_DB, ENTRY_WAL, ENTRY_LOGO)
        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        private const val MAX_LOCAL_BACKUPS = 10
        private const val AUTO_INTERVAL_MS = 24L * 60 * 60 * 1000
        private const val MAX_BACKUP_BYTES = 512L * 1024 * 1024

        fun suggestedFileName(): String = "SalonBackup_${DateTimeUtils.formatIso(DateTimeUtils.today())}.$EXTENSION"

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
