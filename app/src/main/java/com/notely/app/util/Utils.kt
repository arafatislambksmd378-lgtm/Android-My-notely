package com.notely.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeFmt {
    private val dayFmt = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
    private val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val relFmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

    fun greeting(): String {
        val h = Date().hours
        return when (h) {
            in 0..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    fun todayLine(): String = dayFmt.format(Date())
    fun savedLine(millis: Long): String = "Saved · ${timeFmt.format(Date(millis))}"
    fun relative(millis: Long): String {
        val diff = System.currentTimeMillis() - millis
        return when {
            diff < 60_000 -> "Just now"
            diff < 3_600_000 -> "${diff / 60_000} min ago"
            diff < 86_400_000 -> "${diff / 3_600_000} h ago"
            else -> relFmt.format(Date(millis))
        }
    }
}

/**
 * PIN handling.
 *
 * A plain SHA-256 of a 4–6 digit PIN is trivially reversible: an attacker with the
 * DataStore file can enumerate all 1e6 candidates in well under a second. We store
 * `pbkdf2$<iterations>$<saltB64>$<hashB64>` and verify with the platform PBKDF2
 * implementation. Legacy unsalted hashes are still accepted once and transparently
 * upgraded on the next successful unlock (see PreferencesRepository.verifyPin).
 */
object Security {
    private const val ITERATIONS = 150_000
    private const val KEY_BITS = 256
    private const val PREFIX = "pbkdf2"

    private fun pbkdf2(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        return javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
    }

    /** True when [stored] is one of the old unsalted SHA-256 hashes. */
    fun isLegacyHash(stored: String): Boolean =
        stored.isNotBlank() && !stored.startsWith("$PREFIX$") && stored.length == 64

    fun hashPin(pin: String): String {
        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val hash = pbkdf2(pin, salt, ITERATIONS)
        return listOf(
            PREFIX,
            ITERATIONS.toString(),
            android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP),
            android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP)
        ).joinToString("$")
    }

    fun verifyPin(pin: String, stored: String): Boolean {
        if (stored.isBlank()) return false
        if (isLegacyHash(stored)) {
            // Compare against the old scheme so existing installs keep working.
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val legacy = md.digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }
            return java.security.MessageDigest.isEqual(legacy.toByteArray(), stored.toByteArray())
        }
        val parts = stored.split("$")
        if (parts.size != 4) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = runCatching { android.util.Base64.decode(parts[2], android.util.Base64.NO_WRAP) }
            .getOrNull() ?: return false
        val expected = runCatching { android.util.Base64.decode(parts[3], android.util.Base64.NO_WRAP) }
            .getOrNull() ?: return false
        val actual = runCatching { pbkdf2(pin, salt, iterations) }.getOrNull() ?: return false
        return java.security.MessageDigest.isEqual(expected, actual)
    }
}
