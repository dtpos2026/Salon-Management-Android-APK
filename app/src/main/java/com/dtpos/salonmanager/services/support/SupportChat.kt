package com.dtpos.salonmanager.services.support

import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.tasks.await

/** Who wrote a support message. AI replies come from the DT assistant (Cloud Function). */
enum class SupportSender { USER, ADMIN, AI }

data class SupportMessage(val id: String, val from: SupportSender, val text: String, val atMillis: Long?)

/**
 * Support chat with the Super Admin, stored in Firestore at support/{uid}/messages. Only the
 * conversation goes online; salon data never does.
 */
class SupportChat(private val app: android.content.Context) {

    val isConfigured: Boolean
        get() = try { FirebaseApp.getApps(app).isNotEmpty() } catch (e: Exception) { false }

    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    fun messages(uid: String): Flow<List<SupportMessage>> {
        if (!isConfigured) return emptyFlow()
        return callbackFlow {
            val registration = db.collection("support").document(uid).collection("messages")
                .orderBy("at", Query.Direction.ASCENDING)
                .limitToLast(300)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    trySend(
                        snapshot.documents.map { d ->
                            SupportMessage(
                                id = d.id,
                                from = when (d.getString("from")) { "admin" -> SupportSender.ADMIN; "ai" -> SupportSender.AI; else -> SupportSender.USER },
                                text = d.getString("text").orEmpty(),
                                atMillis = (d.get("at") as? Timestamp)?.toDate()?.time,
                            )
                        },
                    )
                }
            awaitClose { registration.remove() }
        }
    }

    /** Sends a message and flags the thread as unread for the admin. */
    suspend fun send(uid: String, salonName: String?, email: String?, text: String, lang: String) {
        val clean = text.trim().take(MAX_LENGTH)
        require(clean.isNotEmpty())
        val thread = db.collection("support").document(uid)
        val batch = db.batch()
        batch.set(thread.collection("messages").document(), mapOf("from" to "user", "text" to clean, "at" to FieldValue.serverTimestamp()))
        batch.set(
            thread,
            mapOf(
                "uid" to uid,
                "salonName" to salonName?.take(120),
                "email" to email,
                "lastMessage" to clean.take(200),
                "lastFrom" to "user",
                "lastAt" to FieldValue.serverTimestamp(),
                "unreadForAdmin" to true,
                "unreadForUser" to false,
                "lang" to lang.take(10),
            ),
        )
        batch.commit().await()
    }

    /** Deletes this salon's chat messages (the thread itself stays so DT can still reply). */
    suspend fun clear(uid: String): Int {
        val messages = db.collection("support").document(uid).collection("messages").get().await().documents
        messages.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(db.collection("support").document(uid).collection("messages").document(it.id)) }
            batch.commit().await()
        }
        return messages.size
    }

    companion object {
        const val MAX_LENGTH = 2000
    }
}
