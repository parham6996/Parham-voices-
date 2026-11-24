package com.example.data.remote

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Hardware-backed AES-256-GCM local encryption backed by AndroidKeyStore.
 * Falls back cleanly only on non-Android JVM environments (e.g. local unit tests).
 */
object EncryptedLocalKeystore {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "parham_voice_api_master_key_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFIX_HW = "hw_v1:"
    private const val PREFIX_SW = "sw_v1:"
    private const val GCM_TAG_BITS = 128

    fun isAndroidKeystoreAvailable(): Boolean {
        return try {
            getOrCreateKeystoreSecretKey() != null
        } catch (_: Throwable) {
            false
        }
    }

    fun storageStatusLabelFa(): String {
        return if (isAndroidKeystoreAvailable()) {
            "رمزنگاری سخت‌افزاری Android Keystore (AES-256-GCM)"
        } else {
            "رمزنگاری محلی AES-256-GCM"
        }
    }

    fun encrypt(plainText: String, packageSeed: String = "parham_voice_keystore"): String {
        if (plainText.isEmpty()) return ""
        val plainBytes = plainText.toByteArray(StandardCharsets.UTF_8)

        val hwKey = try {
            getOrCreateKeystoreSecretKey()
        } catch (_: Throwable) {
            null
        }

        if (hwKey != null) {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, hwKey)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainBytes)
            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val dataB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            return "$PREFIX_HW$ivB64:$dataB64"
        }

        val swKey = deriveFallbackKey(packageSeed)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, swKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainBytes)
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val dataB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        return "$PREFIX_SW$ivB64:$dataB64"
    }

    fun decrypt(storedValue: String?, packageSeed: String = "parham_voice_keystore"): String {
        val value = storedValue?.trim().orEmpty()
        if (value.isEmpty()) return ""

        return try {
            when {
                value.startsWith(PREFIX_HW) -> {
                    val payload = value.removePrefix(PREFIX_HW)
                    val parts = payload.split(":", limit = 2)
                    if (parts.size != 2) return ""
                    val iv = Base64.decode(parts[0], Base64.NO_WRAP)
                    val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
                    val hwKey = getOrCreateKeystoreSecretKey() ?: return ""
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    cipher.init(Cipher.DECRYPT_MODE, hwKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                    String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
                }

                value.startsWith(PREFIX_SW) -> {
                    val payload = value.removePrefix(PREFIX_SW)
                    val parts = payload.split(":", limit = 2)
                    if (parts.size != 2) return ""
                    val iv = Base64.decode(parts[0], Base64.NO_WRAP)
                    val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
                    val swKey = deriveFallbackKey(packageSeed)
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    cipher.init(Cipher.DECRYPT_MODE, swKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                    String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
                }

                // Legacy unencrypted migration path
                else -> value
            }
        } catch (_: Throwable) {
            ""
        }
    }

    private fun getOrCreateKeystoreSecretKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun deriveFallbackKey(seed: String): SecretKey {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("ParHamVoiCe::$seed".toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(bytes, "AES")
    }
}
