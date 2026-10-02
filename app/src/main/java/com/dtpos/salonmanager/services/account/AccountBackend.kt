package com.dtpos.salonmanager.services.account

import android.content.Context
import com.dtpos.salonmanager.core.util.PhoneNumbers
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import java.util.Locale

/** Why an email/password action failed. */
enum class AuthError {
    INVALID_EMAIL, WRONG_CREDENTIALS, EMAIL_IN_USE, WEAK_PASSWORD, USER_DISABLED,
    TOO_MANY_ATTEMPTS, NETWORK, PROVIDER_DISABLED, NOT_CONFIGURED, FAILED,
}

class AuthException(val error: AuthError, cause: Throwable? = null) : Exception(error.name, cause)

/** Online side of the account system. Firebase in the app; a fake in unit tests. */
interface AccountBackend {
    val isConfigured: Boolean

    fun currentUser(): SignedInUser?

    /** Throws [AuthException]. */
    suspend fun signInWithEmail(email: String, password: String): SignedInUser

    /** Creates the login (email + password) and signs in. Throws [AuthException]. */
    suspend fun createLogin(email: String, password: String): SignedInUser

    /** Throws [AuthException]. */
    suspend fun sendPasswordReset(email: String)

    fun signOut()

    /** Reads accounts/{uid} from the server (never from the offline cache). Null = no account. */
    suspend fun fetchAccount(uid: String): CloudAccount?

    /** Server updates of accounts/{uid}; [onChange] receives null when the document is deleted. */
    fun listenAccount(uid: String, onChange: (CloudAccount?) -> Unit): AutoCloseable

    suspend fun register(user: SignedInUser, registration: AccountRegistration, appVersion: String, deviceId: String, deviceModel: String)

    /** Asks the admin to approve this phone for the account. */
    suspend fun requestDevice(uid: String, deviceId: String, deviceModel: String)

    suspend fun heartbeat(uid: String, appVersion: String, deviceModel: String)

    suspend fun fetchBranding(): Branding?

    suspend fun fetchAppConfig(): RemoteAppConfig?
}

class FirebaseAccountBackend(private val context: Context) : AccountBackend {

    override val isConfigured: Boolean by lazy {
        try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private fun accountDoc(uid: String) = db.collection(ACCOUNTS).document(uid)

    override fun currentUser(): SignedInUser? = if (!isConfigured) null else auth.currentUser?.toSignedIn()

    private fun FirebaseUser.toSignedIn() = SignedInUser(uid, email, displayName, photoUrl?.toString())

    override suspend fun signInWithEmail(email: String, password: String): SignedInUser = authCall {
        auth.signInWithEmailAndPassword(email.trim(), password).await().user?.toSignedIn() ?: error("No user")
    }

    override suspend fun createLogin(email: String, password: String): SignedInUser = authCall {
        auth.createUserWithEmailAndPassword(email.trim(), password).await().user?.toSignedIn() ?: error("No user")
    }

    override suspend fun sendPasswordReset(email: String) = authCall {
        auth.sendPasswordResetEmail(email.trim()).await()
        Unit
    }

    private inline fun <T> authCall(block: () -> T): T {
        if (!isConfigured) throw AuthException(AuthError.NOT_CONFIGURED)
        return try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AuthException(classify(e), e)
        }
    }

    private fun classify(e: Exception): AuthError = when (e) {
        is FirebaseAuthWeakPasswordException -> AuthError.WEAK_PASSWORD
        is FirebaseAuthUserCollisionException -> AuthError.EMAIL_IN_USE
        is FirebaseAuthInvalidUserException ->
            if (e.errorCode == "ERROR_USER_DISABLED") AuthError.USER_DISABLED else AuthError.WRONG_CREDENTIALS
        is FirebaseAuthInvalidCredentialsException ->
            if (e.errorCode == "ERROR_INVALID_EMAIL") AuthError.INVALID_EMAIL else AuthError.WRONG_CREDENTIALS
        is FirebaseTooManyRequestsException -> AuthError.TOO_MANY_ATTEMPTS
        is FirebaseNetworkException -> AuthError.NETWORK
        is FirebaseAuthException -> when (e.errorCode) {
            "ERROR_OPERATION_NOT_ALLOWED" -> AuthError.PROVIDER_DISABLED
            "ERROR_TOO_MANY_REQUESTS" -> AuthError.TOO_MANY_ATTEMPTS
            "ERROR_NETWORK_REQUEST_FAILED" -> AuthError.NETWORK
            else -> AuthError.FAILED
        }
        else -> AuthError.FAILED
    }

    override fun signOut() {
        if (isConfigured) auth.signOut()
    }

    override suspend fun fetchAccount(uid: String): CloudAccount? {
        val snapshot = accountDoc(uid).get(Source.SERVER).await()
        return snapshot.toAccount()
    }

    override fun listenAccount(uid: String, onChange: (CloudAccount?) -> Unit): AutoCloseable {
        val registration: ListenerRegistration = accountDoc(uid).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            // Only server data counts as verified; cached snapshots arrive first when offline.
            if (snapshot.metadata.isFromCache) return@addSnapshotListener
            onChange(snapshot.toAccount())
        }
        return AutoCloseable { registration.remove() }
    }

    override suspend fun register(user: SignedInUser, registration: AccountRegistration, appVersion: String, deviceId: String, deviceModel: String) {
        val data = hashMapOf<String, Any?>(
            "uid" to user.uid,
            "email" to user.email,
            "displayName" to user.displayName,
            "photoUrl" to user.photoUrl,
            "salonName" to registration.salonName.trim(),
            "ownerName" to registration.ownerName.trim(),
            "phone" to registration.phone.trim(),
            "city" to registration.city.trim(),
            "address" to registration.address.trim(),
            "salonNameLower" to registration.salonName.trim().lowercase(Locale.ROOT),
            "ownerNameLower" to registration.ownerName.trim().lowercase(Locale.ROOT),
            "phoneDigits" to (PhoneNumbers.toWhatsApp(registration.phone) ?: PhoneNumbers.digits(registration.phone)),
            "status" to AccountStatus.PENDING.name,
            "platform" to "android",
            "appVersion" to appVersion,
            "deviceId" to deviceId,
            "deviceModel" to deviceModel.take(120),
            "createdAt" to FieldValue.serverTimestamp(),
            "lastSeenAt" to FieldValue.serverTimestamp(),
        )
        accountDoc(user.uid).set(data).await()
    }

    override suspend fun requestDevice(uid: String, deviceId: String, deviceModel: String) {
        accountDoc(uid).update(
            mapOf(
                "pendingDeviceId" to deviceId,
                "pendingDeviceModel" to deviceModel.take(120),
                "pendingDeviceAt" to FieldValue.serverTimestamp(),
                "lastSeenAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override suspend fun heartbeat(uid: String, appVersion: String, deviceModel: String) {
        accountDoc(uid).update(
            mapOf(
                "lastSeenAt" to FieldValue.serverTimestamp(),
                "appVersion" to appVersion,
                "deviceModel" to deviceModel.take(120),
            ),
        ).await()
    }

    override suspend fun fetchBranding(): Branding? =
        db.collection(CONFIG).document("branding").get(Source.SERVER).await().plainData()?.let(AccountParser::branding)

    override suspend fun fetchAppConfig(): RemoteAppConfig? =
        db.collection(CONFIG).document("app").get(Source.SERVER).await().plainData()?.let(AccountParser::appConfig)

    private fun DocumentSnapshot.toAccount(): CloudAccount? = plainData()?.let { AccountParser.account(id, it) }

    /** Document data with Firestore timestamps converted to epoch millis. */
    private fun DocumentSnapshot.plainData(): Map<String, Any?>? {
        if (!exists()) return null
        return data?.mapValues { (_, value) -> if (value is Timestamp) value.toDate().time else value }
    }

    private companion object {
        const val ACCOUNTS = "accounts"
        const val CONFIG = "config"
    }
}
