package com.redline.viewer.data.github

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

internal data class PkcePair(val verifier: String, val challenge: String)

internal fun generatePkce(): PkcePair {
    val random = SecureRandom()
    val bytes = ByteArray(64).also { random.nextBytes(it) }
    val verifier = base64UrlNoPad(bytes)
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(verifier.toByteArray(Charsets.US_ASCII))
    val challenge = base64UrlNoPad(digest)
    return PkcePair(verifier = verifier, challenge = challenge)
}

internal fun randomState(): String {
    val random = SecureRandom()
    val bytes = ByteArray(24).also { random.nextBytes(it) }
    return base64UrlNoPad(bytes)
}

private fun base64UrlNoPad(bytes: ByteArray): String =
    Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
