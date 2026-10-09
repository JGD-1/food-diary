package nl.guido.foodtracker.feature.sync.remote

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** The "proof key" for browser sign-in: only the app that started sign-in can finish it. */
object Pkce {
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    fun newVerifier(random: SecureRandom = SecureRandom()): String =
        ByteArray(48).also { random.nextBytes(it) }.let(encoder::encodeToString)

    fun challenge(verifier: String): String =
        encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
}
