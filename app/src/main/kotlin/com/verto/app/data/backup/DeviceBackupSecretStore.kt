package com.verto.app.data.backup

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the automatic backup secret encrypted by an Android Keystore key.
 * A legacy plaintext preference is migrated once and deleted immediately.
 */
internal class DeviceBackupSecretStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getOrCreate(): String {
        val wrapped = preferences.getString(WRAPPED_SECRET_KEY, null)
        val iv = preferences.getString(WRAPPED_IV_KEY, null)
        if (!wrapped.isNullOrBlank() && !iv.isNullOrBlank()) {
            return decrypt(wrapped, iv)
        }

        val legacy = preferences.getString(LEGACY_SECRET_KEY, null)
        val secret = legacy?.takeIf { it.isNotBlank() } ?: randomSecret()
        val encrypted = encrypt(secret)
        check(
            preferences.edit()
                .putString(WRAPPED_SECRET_KEY, encrypted.first)
                .putString(WRAPPED_IV_KEY, encrypted.second)
                .remove(LEGACY_SECRET_KEY)
                .commit()
        ) { "Unable to persist protected backup secret" }
        return secret
    }

    private fun encrypt(secret: String): Pair<String, String> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(ciphertext, Base64.NO_WRAP) to
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
    }

    private fun decrypt(ciphertext: String, iv: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_BITS, Base64.decode(iv, Base64.NO_WRAP))
        )
        return cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            generateKey()
        }
    }

    private fun randomSecret(): String = ByteArray(32)
        .also(SecureRandom()::nextBytes)
        .let { Base64.encodeToString(it, Base64.NO_WRAP) }

    private companion object {
        const val PREFS_NAME = "verto_backup_crypto"
        const val LEGACY_SECRET_KEY = "device_backup_secret_v1"
        const val WRAPPED_SECRET_KEY = "device_backup_secret_wrapped_v2"
        const val WRAPPED_IV_KEY = "device_backup_secret_iv_v2"
        const val KEY_ALIAS = "verto_backup_master_key_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
