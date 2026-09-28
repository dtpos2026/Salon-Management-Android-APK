package com.dtpos.salonmanager.domain.license

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.LocalDate
import java.util.Base64

sealed interface LicenseVerification {
    data class Valid(val payload: LicensePayload) : LicenseVerification
    data class Invalid(val reason: Reason) : LicenseVerification

    enum class Reason { MALFORMED, UNSUPPORTED_VERSION, BAD_SIGNATURE, NO_PUBLIC_KEY }
}

/**
 * Offline licence keys: `SLN1.<base64url payload>.<base64url ECDSA P-256 signature>`.
 *
 * The vendor signs keys with a private key that never ships in the app (see tools/license).
 * The app only holds the public key, so keys cannot be forged, and no server is required.
 * The same payload format can later be served by an online licence API.
 */
object LicenseCodec {
    const val PREFIX = "SLN1"
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    private const val KEY_ALGORITHM = "EC"
    private const val CURVE = "secp256r1"

    private val urlEncoder = Base64.getUrlEncoder().withoutPadding()
    private val urlDecoder = Base64.getUrlDecoder()

    fun generateKeyPair(): KeyPair =
        KeyPairGenerator.getInstance(KEY_ALGORITHM).apply { initialize(ECGenParameterSpec(CURVE)) }.generateKeyPair()

    fun publicKeyToBase64(key: PublicKey): String = Base64.getEncoder().encodeToString(key.encoded)

    fun privateKeyToBase64(key: PrivateKey): String = Base64.getEncoder().encodeToString(key.encoded)

    fun publicKeyFromBase64(value: String): PublicKey? = try {
        KeyFactory.getInstance(KEY_ALGORITHM).generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(value.trim())))
    } catch (e: Exception) {
        null
    }

    fun privateKeyFromBase64(value: String): PrivateKey? = try {
        KeyFactory.getInstance(KEY_ALGORITHM).generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(value.trim())))
    } catch (e: Exception) {
        null
    }

    fun sign(payload: LicensePayload, privateKey: PrivateKey): String {
        val body = encodePayload(payload)
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initSign(privateKey)
            update(body)
            sign()
        }
        return "$PREFIX.${urlEncoder.encodeToString(body)}.${urlEncoder.encodeToString(signature)}"
    }

    fun verify(licenseKey: String, publicKey: PublicKey?): LicenseVerification {
        if (publicKey == null) return LicenseVerification.Invalid(LicenseVerification.Reason.NO_PUBLIC_KEY)
        val compact = licenseKey.filterNot { it.isWhitespace() }
        val parts = compact.split('.')
        if (parts.size != 3) return LicenseVerification.Invalid(LicenseVerification.Reason.MALFORMED)
        if (parts[0] != PREFIX) return LicenseVerification.Invalid(LicenseVerification.Reason.UNSUPPORTED_VERSION)
        return try {
            val body = urlDecoder.decode(parts[1])
            val signature = urlDecoder.decode(parts[2])
            val ok = Signature.getInstance(SIGNATURE_ALGORITHM).run {
                initVerify(publicKey)
                update(body)
                verify(signature)
            }
            if (!ok) return LicenseVerification.Invalid(LicenseVerification.Reason.BAD_SIGNATURE)
            val payload = decodePayload(body) ?: return LicenseVerification.Invalid(LicenseVerification.Reason.MALFORMED)
            LicenseVerification.Valid(payload)
        } catch (e: IllegalArgumentException) {
            LicenseVerification.Invalid(LicenseVerification.Reason.MALFORMED)
        } catch (e: java.security.SignatureException) {
            LicenseVerification.Invalid(LicenseVerification.Reason.BAD_SIGNATURE)
        }
    }

    /** Canonical, line-based payload so the signature is stable across platforms. */
    fun encodePayload(payload: LicensePayload): ByteArray {
        fun clean(value: String) = value.replace('\n', ' ').replace('\r', ' ').trim()
        val lines = listOf(
            "v=1",
            "lid=${clean(payload.licenseId)}",
            "biz=${clean(payload.businessName)}",
            "plan=${payload.plan.name}",
            "iat=${payload.issuedOn}",
            "exp=${payload.expiresOn?.toString() ?: ""}",
            "dev=${clean(payload.deviceId)}",
            "max=${payload.maxBusinesses}",
            "feat=${payload.features.sorted().joinToString(",") { clean(it) }}",
        )
        return lines.joinToString("\n").toByteArray(Charsets.UTF_8)
    }

    fun decodePayload(bytes: ByteArray): LicensePayload? = try {
        val map = bytes.toString(Charsets.UTF_8).lines()
            .filter { it.contains('=') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        if (map["v"] != "1") {
            null
        } else {
            LicensePayload(
                licenseId = map.getValue("lid"),
                businessName = map.getValue("biz"),
                plan = LicensePlan.valueOf(map.getValue("plan")),
                issuedOn = LocalDate.parse(map.getValue("iat")),
                expiresOn = map["exp"]?.takeIf { it.isNotBlank() }?.let(LocalDate::parse),
                deviceId = map["dev"]?.ifBlank { null } ?: LicensePayload.ANY_DEVICE,
                maxBusinesses = map["max"]?.toIntOrNull() ?: 1,
                features = map["feat"].orEmpty().split(',').filter { it.isNotBlank() }.toSet(),
            )
        }
    } catch (e: Exception) {
        null
    }
}
