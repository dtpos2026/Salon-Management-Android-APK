package com.dtpos.salonmanager.data.repository

/** Outcome of a write operation that can fail for a business reason (not a crash). */
sealed interface DataResult<out T> {
    data class Success<T>(val data: T) : DataResult<T>
    data class Failure(val error: DataError) : DataResult<Nothing>
}

enum class DataError {
    DUPLICATE_NAME,
    DUPLICATE_PHONE,
    NOT_FOUND,
    IN_USE,
    INVALID,
    READ_ONLY,
    ALREADY_CLOSED,
    STORAGE,
}

/** Builds a LIKE pattern that matches [query] literally anywhere in the column. */
fun likePattern(query: String): String {
    val escaped = query.trim()
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
    return "%$escaped%"
}

/** Runs a database write, converting SQLite failures into [DataError.STORAGE] instead of crashing. */
internal suspend fun <T> safeWrite(block: suspend () -> DataResult<T>): DataResult<T> = try {
    block()
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: android.database.sqlite.SQLiteConstraintException) {
    DataResult.Failure(DataError.INVALID)
} catch (e: android.database.SQLException) {
    DataResult.Failure(DataError.STORAGE)
} catch (e: IllegalStateException) {
    DataResult.Failure(DataError.STORAGE)
}
