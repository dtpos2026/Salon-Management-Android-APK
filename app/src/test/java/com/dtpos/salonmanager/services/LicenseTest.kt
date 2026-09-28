package com.dtpos.salonmanager.services

import com.dtpos.salonmanager.domain.license.LicenseCodec
import com.dtpos.salonmanager.domain.license.LicenseEvaluator
import com.dtpos.salonmanager.domain.license.LicensePayload
import com.dtpos.salonmanager.domain.license.LicensePlan
import com.dtpos.salonmanager.domain.license.LicenseStatus
import com.dtpos.salonmanager.domain.license.LicenseVerification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LicenseTest {

    private val keys = LicenseCodec.generateKeyPair()
    private val payload = LicensePayload(
        licenseId = "LIC-2026-0001",
        businessName = "Royal Barber Shop",
        plan = LicensePlan.YEAR_1,
        issuedOn = LocalDate.of(2026, 9, 28),
        expiresOn = LocalDate.of(2027, 9, 27),
        deviceId = "abcd-1234",
        features = setOf("printer", "reports"),
    )

    @Test
    fun `signed key round trips and verifies`() {
        val key = LicenseCodec.sign(payload, keys.private)
        assertTrue(key.startsWith("SLN1."))
        val result = LicenseCodec.verify(key, keys.public)
        assertEquals(LicenseVerification.Valid(payload), result)
    }

    @Test
    fun `whitespace and line breaks in pasted keys are ignored`() {
        val key = LicenseCodec.sign(payload, keys.private)
        val pasted = key.chunked(20).joinToString("\n  ")
        assertTrue(LicenseCodec.verify(pasted, keys.public) is LicenseVerification.Valid)
    }

    @Test
    fun `tampered or foreign keys are rejected`() {
        val key = LicenseCodec.sign(payload, keys.private)
        val parts = key.split('.')
        val forgedPayload = LicenseCodec.encodePayload(payload.copy(expiresOn = LocalDate.of(2099, 1, 1)))
        val forged = listOf(parts[0], java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(forgedPayload), parts[2])
            .joinToString(".")
        assertEquals(
            LicenseVerification.Invalid(LicenseVerification.Reason.BAD_SIGNATURE),
            LicenseCodec.verify(forged, keys.public),
        )
        val otherKeys = LicenseCodec.generateKeyPair()
        assertFalse(LicenseCodec.verify(key, otherKeys.public) is LicenseVerification.Valid)
        assertEquals(LicenseVerification.Invalid(LicenseVerification.Reason.MALFORMED), LicenseCodec.verify("nonsense", keys.public))
        assertEquals(LicenseVerification.Invalid(LicenseVerification.Reason.NO_PUBLIC_KEY), LicenseCodec.verify(key, null))
    }

    @Test
    fun `keys survive base64 export`() {
        val pub = LicenseCodec.publicKeyFromBase64(LicenseCodec.publicKeyToBase64(keys.public))
        val priv = LicenseCodec.privateKeyFromBase64(LicenseCodec.privateKeyToBase64(keys.private))!!
        val key = LicenseCodec.sign(payload, priv)
        assertTrue(LicenseCodec.verify(key, pub) is LicenseVerification.Valid)
    }

    private fun evaluate(
        enforced: Boolean = true,
        today: LocalDate = LocalDate.of(2026, 10, 1),
        payload: LicensePayload? = this.payload,
        lastSeen: LocalDate? = null,
        installationId: String = "ABCD-1234",
    ) = LicenseEvaluator.evaluate(
        enforced = enforced,
        today = today,
        firstRunDate = LocalDate.of(2026, 9, 28),
        trialDays = 30,
        payload = payload,
        installationId = installationId,
        lastSeenDate = lastSeen,
    )

    @Test
    fun `license status evaluation`() {
        assertEquals(LicenseStatus.ACTIVE, evaluate().status)
        assertFalse(evaluate().isReadOnly)
        assertEquals(LicenseStatus.EXPIRING_SOON, evaluate(today = LocalDate.of(2027, 9, 25)).status)
        assertEquals(3L, evaluate(today = LocalDate.of(2027, 9, 25)).daysRemaining)
        val expired = evaluate(today = LocalDate.of(2027, 9, 28))
        assertEquals(LicenseStatus.EXPIRED, expired.status)
        assertTrue(expired.isReadOnly)
        assertEquals(LicenseStatus.INVALID, evaluate(installationId = "other").status)
        assertEquals(LicenseStatus.CLOCK_TAMPERED, evaluate(lastSeen = LocalDate.of(2026, 12, 1)).status)
    }

    @Test
    fun `trial and non enforced builds`() {
        val trial = evaluate(payload = null)
        assertEquals(LicenseStatus.TRIAL, trial.status)
        assertEquals(27L, trial.daysRemaining)
        val trialOver = evaluate(payload = null, today = LocalDate.of(2026, 10, 28))
        assertEquals(LicenseStatus.TRIAL_EXPIRED, trialOver.status)
        assertTrue(trialOver.isReadOnly)
        val owner = evaluate(enforced = false, payload = null, today = LocalDate.of(2030, 1, 1))
        assertEquals(LicenseStatus.NOT_ENFORCED, owner.status)
        assertFalse(owner.isReadOnly)
        // Lifetime key.
        assertEquals(LicenseStatus.ACTIVE, evaluate(payload = payload.copy(expiresOn = null, deviceId = "*")).status)
    }
}
