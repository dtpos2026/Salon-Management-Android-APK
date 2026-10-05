package com.dtpos.salonmanager.services.account

import android.content.Context
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.data.database.SalonDatabase
import com.dtpos.salonmanager.data.database.model.DailyStatRow
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Shares the salon's daily totals with the DT Super Admin, who bills salons by their sales:
 * per day the sales amount, number of customers (bills) and services, and how much was cash /
 * online / udhaar. Only these numbers leave the phone: no customer names, phone numbers,
 * services or receipts. Firestore keeps the writes while offline and sends them when online.
 */
class SalesSync(
    private val app: Context,
    private val database: () -> SalonDatabase,
    private val businessId: Long,
    private val accountManager: AccountManager,
    private val scope: CoroutineScope,
    private val versionName: String,
) {
    private val sent = HashMap<String, DayNumbers>()
    private var sentFor: String? = null
    @Volatile private var started = false

    private data class DayNumbers(val sales: Long, val customers: Int, val services: Int, val cash: Long, val credit: Long)

    private val configured: Boolean
        get() = try { FirebaseApp.getApps(app).isNotEmpty() } catch (e: Exception) { false }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun start() {
        if (started || !configured) return
        started = true
        val today = flow {
            while (true) {
                emit(DateTimeUtils.today())
                delay(60_000)
            }
        }.distinctUntilChanged()
        val uid = accountManager.state.map { (it as? AccessState.Allowed)?.account?.uid }.distinctUntilChanged()
        scope.launch {
            combine(uid, today) { u, d -> u to d }
                .flatMapLatest { (u, d) ->
                    if (u == null) {
                        emptyFlow()
                    } else {
                        database().saleDao()
                            .observeDailyStats(businessId, d.minusDays(WINDOW_DAYS).toEpochDay(), d.toEpochDay())
                            .map { Triple(u, d, it) }
                    }
                }
                .debounce(4_000)
                .collect { (u, d, rows) ->
                    try {
                        upload(u, d, rows)
                    } catch (e: Exception) {
                        // Best effort: the next change tries again. Salon work is never blocked.
                    }
                }
        }
    }

    private suspend fun upload(uid: String, today: LocalDate, rows: List<DailyStatRow>) {
        if (sentFor != uid) {
            sent.clear()
            sentFor = uid
        }
        val byDay = rows.associate { LocalDate.ofEpochDay(it.day) to DayNumbers(it.totalMinor, it.count, it.serviceCount, it.cashMinor, it.creditMinor) }
        val firestore = FirebaseFirestore.getInstance()
        val stats = firestore.collection("stats").document(uid)
        val batch = firestore.batch()
        // Days that changed, plus days that were sent before and are now empty (all voided).
        (byDay.keys + sent.keys.map { LocalDate.parse(it) }).distinct().forEach { day ->
            val key = KEY.format(day)
            val numbers = byDay[day] ?: DayNumbers(0, 0, 0, 0, 0)
            if (sent[key] == numbers) return@forEach
            batch.set(
                stats.collection("days").document(key),
                mapOf(
                    "date" to key,
                    "salesMinor" to numbers.sales,
                    "customers" to numbers.customers.toLong(),
                    "services" to numbers.services.toLong(),
                    "cashMinor" to numbers.cash,
                    "onlineMinor" to (numbers.sales - numbers.cash - numbers.credit).coerceAtLeast(0),
                    "creditMinor" to numbers.credit,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            sent[key] = numbers
        }
        val todayNumbers = byDay[today]
        val yesterdayNumbers = byDay[today.minusDays(1)]
        val month = byDay.filterKeys { it.year == today.year && it.month == today.month }.values
        val profile = try { database().businessDao().get(businessId) } catch (e: Exception) { null }
        batch.set(
            stats,
            mapOf(
                "uid" to uid,
                "salonName" to profile?.name?.take(120),
                "currency" to profile?.currencyCode?.take(10),
                "day" to KEY.format(today),
                "todaySalesMinor" to (todayNumbers?.sales ?: 0L),
                "todayCustomers" to (todayNumbers?.customers ?: 0).toLong(),
                "yesterdaySalesMinor" to (yesterdayNumbers?.sales ?: 0L),
                "yesterdayCustomers" to (yesterdayNumbers?.customers ?: 0).toLong(),
                "monthKey" to MONTH_KEY.format(today),
                "monthSalesMinor" to month.sumOf { it.sales },
                "monthCustomers" to month.sumOf { it.customers }.toLong(),
                "appVersion" to versionName.take(40),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        )
        // Not awaited: offline, Firestore queues the batch and sends it later.
        batch.commit()
    }

    companion object {
        /** Days of history kept in sync (covers the whole current month). */
        const val WINDOW_DAYS = 35L
        private val KEY: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        private val MONTH_KEY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
    }
}
