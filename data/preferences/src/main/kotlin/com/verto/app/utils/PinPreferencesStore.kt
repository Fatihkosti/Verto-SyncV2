package com.verto.app.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal class PinPreferencesStore(private val context: Context) {
    suspend fun setPin(pin: String): String {
        val pinSalt  = generateSalt()
        val pinHash  = pbkdf2Hash(pin, pinSalt)
        val code     = generateRecoveryCode()
        val codeSalt = generateSalt()
        val codeHash = pbkdf2Hash(code, codeSalt)
        context.dataStore.edit { prefs ->
            prefs[KEY_PIN_HASH]           = pinHash
            prefs[KEY_PIN_SALT]           = pinSalt.toHex()
            prefs[KEY_RECOVERY_CODE]      = codeHash
            prefs[KEY_RECOVERY_CODE_SALT] = codeSalt.toHex()
            prefs[KEY_PIN_ENABLED]        = true
        }
        return code
    }

    suspend fun validatePin(pin: String): Boolean {
        val prefs   = context.dataStore.data.first()
        val stored  = prefs[KEY_PIN_HASH] ?: return false
        val saltHex = prefs[KEY_PIN_SALT]
        return if (saltHex.isNullOrEmpty()) {
            // قديم: SHA-256 بدون salt — للتوافق مع الإصدارات السابقة
            sha256Hash(pin) == stored
        } else {
            pbkdf2Hash(pin, saltHex.fromHex()) == stored
        }
    }

    suspend fun disablePin() {
        context.dataStore.edit { prefs ->
            prefs[KEY_PIN_ENABLED] = false
            prefs.remove(KEY_PIN_HASH)
            prefs.remove(KEY_PIN_SALT)
            prefs.remove(KEY_RECOVERY_CODE)
            prefs.remove(KEY_RECOVERY_CODE_SALT)
        }
    }

    suspend fun resetWithRecoveryCode(code: String, newPin: String): Boolean {
        val prefs        = context.dataStore.data.first()
        val storedHash   = prefs[KEY_RECOVERY_CODE] ?: return false
        val saltHex      = prefs[KEY_RECOVERY_CODE_SALT]
        val valid = if (saltHex.isNullOrEmpty()) {
            // قديم: مخزون بنص صريح
            storedHash == code && code.isNotBlank()
        } else {
            pbkdf2Hash(code, saltHex.fromHex()) == storedHash
        }
        if (!valid) return false
        setPin(newPin)
        return true
    }

    // ── Private ────────────────────────────────────────────────────────────────

    private fun pbkdf2Hash(input: String, salt: ByteArray): String {
        val spec    = PBEKeySpec(input.toCharArray(), salt, 10_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded.toHex()
    }

    private fun sha256Hash(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.toHex()
    }

    private fun generateSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun generateRecoveryCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..8).map { chars.random() }.joinToString("")
    }
}
