package com.dtpos.salonmanager.services.ai

import android.content.Context
import com.dtpos.salonmanager.core.util.Money
import com.dtpos.salonmanager.domain.insights.BusinessSnapshot
import com.google.firebase.FirebaseApp
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

enum class AiError { OFF_BY_ADMIN, LIMIT, NOT_ACTIVE, SIGNED_OUT, NETWORK, NOT_SET_UP, FAILED }

sealed interface AiResult {
    data class Answer(val text: String) : AiResult
    data class Failed(val error: AiError) : AiResult
}

/**
 * Business growth assistant. Only the salon's summary numbers (no customer names or phone numbers)
 * go to the DT Cloud Function, which asks Claude. Works only when the owner turns AI on in the app
 * and DT has it switched on in the Super Admin panel.
 */
class AiAssistant(private val app: Context) {

    val isConfigured: Boolean
        get() = try { FirebaseApp.getApps(app).isNotEmpty() } catch (e: Exception) { false }

    suspend fun ask(question: String, stats: Map<String, Any?>, lang: String): AiResult {
        if (!isConfigured) return AiResult.Failed(AiError.NOT_SET_UP)
        return try {
            val result = FirebaseFunctions.getInstance(REGION)
                .getHttpsCallable("businessAssistant")
                .apply { setTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
                .call(mapOf("question" to question.take(1500), "stats" to stats, "lang" to lang))
                .await()
            val answer = (result.getData() as? Map<*, *>)?.get("answer") as? String
            if (answer.isNullOrBlank()) AiResult.Failed(AiError.FAILED) else AiResult.Answer(answer.trim())
        } catch (e: FirebaseFunctionsException) {
            AiResult.Failed(
                when (e.code) {
                    FirebaseFunctionsException.Code.FAILED_PRECONDITION -> AiError.OFF_BY_ADMIN
                    FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> AiError.LIMIT
                    FirebaseFunctionsException.Code.PERMISSION_DENIED -> AiError.NOT_ACTIVE
                    FirebaseFunctionsException.Code.UNAUTHENTICATED -> AiError.SIGNED_OUT
                    FirebaseFunctionsException.Code.NOT_FOUND -> AiError.NOT_SET_UP
                    FirebaseFunctionsException.Code.UNAVAILABLE, FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> AiError.NETWORK
                    else -> AiError.FAILED
                },
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AiResult.Failed(AiError.NETWORK)
        }
    }

    companion object {
        const val REGION = "asia-south1"
        private const val TIMEOUT_SECONDS = 180L

        /** The numbers the assistant sees: totals in rupees, top lists by name, no personal data. */
        fun summary(snapshot: BusinessSnapshot, pendingDuesMinor: Long, customerCount: Int): Map<String, Any?> {
            fun rs(minor: Long) = minor / Money.MINOR_PER_UNIT
            return mapOf(
                "date" to snapshot.today.toString(),
                "monthToDate" to mapOf(
                    "sales" to rs(snapshot.monthToDateSalesMinor),
                    "salesCount" to snapshot.salesCountMonthToDate,
                    "businessExpenses" to rs(snapshot.monthToDateBusinessExpensesMinor),
                    "staffPayments" to rs(snapshot.monthToDateStaffPaymentsMinor),
                    "profit" to rs(snapshot.monthToDateProfitMinor),
                    "personalExpenses" to rs(snapshot.monthToDatePersonalExpensesMinor),
                ),
                "previousMonthSamePeriod" to mapOf(
                    "sales" to rs(snapshot.previousMonthSamePeriodSalesMinor),
                    "businessExpenses" to rs(snapshot.previousMonthSamePeriodBusinessExpensesMinor),
                ),
                "monthlyTarget" to rs(snapshot.monthlyTargetMinor),
                "topServices" to snapshot.topServices.map { mapOf("name" to it.name, "sales" to rs(it.amountMinor), "count" to it.count) },
                "topStaff" to snapshot.topStaff.map { mapOf("name" to it.name, "sales" to rs(it.amountMinor), "count" to it.count) },
                "averageSalesByWeekday" to snapshot.salesByWeekday.entries.associate { (day, stat) -> day.name to rs(stat.averageMinor) },
                "customersLast60Days" to snapshot.customersLast60Days,
                "returningCustomersLast60Days" to snapshot.returningCustomersLast60Days,
                "totalCustomers" to customerCount,
                "pendingUdhaar" to rs(pendingDuesMinor),
            )
        }
    }
}
