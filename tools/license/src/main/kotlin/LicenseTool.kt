import com.dtpos.salonmanager.domain.license.LicenseCodec
import com.dtpos.salonmanager.domain.license.LicensePayload
import com.dtpos.salonmanager.domain.license.LicensePlan
import com.dtpos.salonmanager.domain.license.LicenseVerification
import java.io.File
import java.time.LocalDate
import kotlin.system.exitProcess

/**
 * Salon Manager licence tool (vendor side, never shipped to customers).
 *
 *   keygen                                   create a signing key pair in ./out
 *   issue --id LIC-0001 --business "Name" --plan YEAR_1 [--device ID] [--issued 2026-10-01]
 *         [--expires 2027-09-30] [--private out/license-private.key]
 *   verify --key SLN1... [--public BASE64 | --public-file out/license-public.txt]
 */
fun main(args: Array<String>) {
    val command = args.firstOrNull() ?: usage()
    val options = parseOptions(args.drop(1))
    when (command) {
        "keygen" -> keygen()
        "issue" -> issue(options)
        "verify" -> verify(options)
        else -> usage()
    }
}

private fun keygen() {
    val out = File("out").apply { mkdirs() }
    val privateFile = File(out, "license-private.key")
    if (privateFile.exists()) {
        System.err.println("Refusing to overwrite ${privateFile.path}. Move it away first - it signs every licence you issued.")
        exitProcess(2)
    }
    val pair = LicenseCodec.generateKeyPair()
    privateFile.writeText(LicenseCodec.privateKeyToBase64(pair.private))
    val publicKey = LicenseCodec.publicKeyToBase64(pair.public)
    File(out, "license-public.txt").writeText(publicKey)
    println("Private key : ${privateFile.path}  (KEEP SECRET, back it up, never commit it)")
    println("Public key  : ${File(out, "license-public.txt").path}")
    println()
    println("Add to gradle.properties (or pass with -P) when building customer APKs:")
    println("salon.enforceLicense=true")
    println("salon.licensePublicKey=$publicKey")
}

private fun issue(o: Map<String, String>) {
    val privateKey = File(o["private"] ?: "out/license-private.key").takeIf { it.exists() }?.readText()
        ?.let(LicenseCodec::privateKeyFromBase64) ?: fail("Private key not found. Run keygen first or pass --private.")
    val plan = o["plan"]?.let { runCatching { LicensePlan.valueOf(it) }.getOrNull() }
        ?: fail("--plan must be one of ${LicensePlan.entries.joinToString()}")
    val issued = o["issued"]?.let(LocalDate::parse) ?: LocalDate.now()
    val expires = o["expires"]?.let(LocalDate::parse)
        ?: plan.defaultExpiry(issued)?.minusDays(1)
        ?: if (plan == LicensePlan.LIFETIME) null else fail("--expires is required for plan $plan")
    val payload = LicensePayload(
        licenseId = o["id"] ?: fail("--id is required"),
        businessName = o["business"] ?: fail("--business is required"),
        plan = plan,
        issuedOn = issued,
        expiresOn = expires,
        deviceId = o["device"] ?: LicensePayload.ANY_DEVICE,
    )
    val key = LicenseCodec.sign(payload, privateKey)
    println("Licence ${payload.licenseId} for ${payload.businessName}: plan $plan, valid until ${expires ?: "forever"}, device ${payload.deviceId}")
    println()
    println(key)
}

private fun verify(o: Map<String, String>) {
    val publicText = o["public"] ?: o["public-file"]?.let { File(it).readText() } ?: File("out/license-public.txt").takeIf { it.exists() }?.readText()
    val result = LicenseCodec.verify(o["key"] ?: fail("--key is required"), publicText?.let(LicenseCodec::publicKeyFromBase64))
    when (result) {
        is LicenseVerification.Valid -> println("VALID: ${result.payload}")
        is LicenseVerification.Invalid -> fail("INVALID: ${result.reason}")
    }
}

private fun parseOptions(args: List<String>): Map<String, String> {
    val map = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        val name = args[i].removePrefix("--")
        map[name] = args.getOrNull(i + 1) ?: fail("Missing value for --$name")
        i += 2
    }
    return map
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(1)
}

private fun usage(): Nothing = fail(
    """
    Usage:
      keygen
      issue --id LIC-0001 --business "Salon name" --plan MONTH_1|MONTH_3|MONTH_6|YEAR_1|CUSTOM|LIFETIME
            [--device INSTALLATION-ID] [--issued yyyy-mm-dd] [--expires yyyy-mm-dd] [--private file]
      verify --key SLN1... [--public BASE64 | --public-file file]
    """.trimIndent(),
)
