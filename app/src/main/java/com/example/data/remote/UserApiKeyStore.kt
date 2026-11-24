package com.example.data.remote

import android.content.Context
import com.example.BuildConfig
import com.example.domain.IranGatewayPreset

data class UserApiKeysConfig(
    val elevenLabsApiKey: String = "",
    val geminiDirectApiKey: String = "",
    val openAiApiKey: String = "",
    val openAiBaseUrl: String = "https://api.openai.com/v1",
    val openAiModel: String = "gpt-4o-mini",
    val iranGatewayPresetId: String = IranGatewayPreset.AVALAI.id,
    val iranGatewayName: String = IranGatewayPreset.AVALAI.titleFa,
    val iranGatewayBaseUrl: String = IranGatewayPreset.AVALAI.baseUrl,
    val iranGatewayApiKey: String = "",
    val iranGatewayModel: String = IranGatewayPreset.AVALAI.defaultModel,
    val customProviderName: String = "",
    val customProviderBaseUrl: String = "",
    val customProviderApiKey: String = "",
    val customProviderModel: String = ""
) {
    fun hasAnyKey(): Boolean =
        elevenLabsApiKey.isNotBlank() ||
            geminiDirectApiKey.isNotBlank() ||
            openAiApiKey.isNotBlank() ||
            iranGatewayApiKey.isNotBlank() ||
            customProviderApiKey.isNotBlank()
}

object UserApiKeyStore {
    private const val ENCRYPTED_PREFS_NAME = "parham_voice_encrypted_api_keys"
    private const val LEGACY_PREFS_NAME = "parham_voice_user_api_keys"

    private const val KEY_ELEVENLABS = "elevenlabs_api_key"
    private const val KEY_GEMINI = "gemini_direct_api_key"
    private const val KEY_OPENAI = "openai_api_key"
    private const val KEY_OPENAI_URL = "openai_base_url"
    private const val KEY_OPENAI_MODEL = "openai_model"
    private const val KEY_IRAN_PRESET = "iran_gateway_preset"
    private const val KEY_IRAN_NAME = "iran_gateway_name"
    private const val KEY_IRAN_URL = "iran_gateway_url"
    private const val KEY_IRAN_API = "iran_gateway_key"
    private const val KEY_IRAN_MODEL = "iran_gateway_model"
    private const val KEY_CUSTOM_NAME = "custom_provider_name"
    private const val KEY_CUSTOM_URL = "custom_provider_url"
    private const val KEY_CUSTOM_API = "custom_provider_key"
    private const val KEY_CUSTOM_MODEL = "custom_provider_model"

    fun load(context: Context): UserApiKeysConfig {
        migrateLegacyPlaintextIfNeeded(context)
        val p = context.getSharedPreferences(ENCRYPTED_PREFS_NAME, Context.MODE_PRIVATE)
        val seed = context.packageName
        val presetId = (p.getString(KEY_IRAN_PRESET, "") ?: "").ifBlank { IranGatewayPreset.AVALAI.id }
        val preset = IranGatewayPreset.fromId(presetId)
        return UserApiKeysConfig(
            elevenLabsApiKey = EncryptedLocalKeystore.decrypt(p.getString(KEY_ELEVENLABS, ""), seed),
            geminiDirectApiKey = EncryptedLocalKeystore.decrypt(p.getString(KEY_GEMINI, ""), seed),
            openAiApiKey = EncryptedLocalKeystore.decrypt(p.getString(KEY_OPENAI, ""), seed),
            openAiBaseUrl = (p.getString(KEY_OPENAI_URL, "") ?: "").ifBlank { "https://api.openai.com/v1" },
            openAiModel = (p.getString(KEY_OPENAI_MODEL, "") ?: "").ifBlank { "gpt-4o-mini" },
            iranGatewayPresetId = preset.id,
            iranGatewayName = (p.getString(KEY_IRAN_NAME, "") ?: "").ifBlank { preset.titleFa },
            iranGatewayBaseUrl = (p.getString(KEY_IRAN_URL, "") ?: "").ifBlank { preset.baseUrl },
            iranGatewayApiKey = EncryptedLocalKeystore.decrypt(p.getString(KEY_IRAN_API, ""), seed),
            iranGatewayModel = (p.getString(KEY_IRAN_MODEL, "") ?: "").ifBlank { preset.defaultModel },
            customProviderName = p.getString(KEY_CUSTOM_NAME, "") ?: "",
            customProviderBaseUrl = p.getString(KEY_CUSTOM_URL, "") ?: "",
            customProviderApiKey = EncryptedLocalKeystore.decrypt(p.getString(KEY_CUSTOM_API, ""), seed),
            customProviderModel = p.getString(KEY_CUSTOM_MODEL, "") ?: ""
        )
    }

    fun save(context: Context, config: UserApiKeysConfig) {
        val p = context.getSharedPreferences(ENCRYPTED_PREFS_NAME, Context.MODE_PRIVATE)
        val seed = context.packageName
        val preset = IranGatewayPreset.fromId(config.iranGatewayPresetId)
        p.edit()
            .putString(KEY_ELEVENLABS, EncryptedLocalKeystore.encrypt(config.elevenLabsApiKey.trim(), seed))
            .putString(KEY_GEMINI, EncryptedLocalKeystore.encrypt(config.geminiDirectApiKey.trim(), seed))
            .putString(KEY_OPENAI, EncryptedLocalKeystore.encrypt(config.openAiApiKey.trim(), seed))
            .putString(KEY_OPENAI_URL, config.openAiBaseUrl.trim().ifBlank { "https://api.openai.com/v1" })
            .putString(KEY_OPENAI_MODEL, config.openAiModel.trim().ifBlank { "gpt-4o-mini" })
            .putString(KEY_IRAN_PRESET, preset.id)
            .putString(KEY_IRAN_NAME, config.iranGatewayName.trim().ifBlank { preset.titleFa })
            .putString(KEY_IRAN_URL, config.iranGatewayBaseUrl.trim().ifBlank { preset.baseUrl })
            .putString(KEY_IRAN_API, EncryptedLocalKeystore.encrypt(config.iranGatewayApiKey.trim(), seed))
            .putString(KEY_IRAN_MODEL, config.iranGatewayModel.trim().ifBlank { preset.defaultModel })
            .putString(KEY_CUSTOM_NAME, config.customProviderName.trim())
            .putString(KEY_CUSTOM_URL, config.customProviderBaseUrl.trim())
            .putString(KEY_CUSTOM_API, EncryptedLocalKeystore.encrypt(config.customProviderApiKey.trim(), seed))
            .putString(KEY_CUSTOM_MODEL, config.customProviderModel.trim())
            .commit()
        context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun clearAll(context: Context) {
        context.getSharedPreferences(ENCRYPTED_PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun storageSecuritySummaryFa(): String = EncryptedLocalKeystore.storageStatusLabelFa()

    fun resolveElevenLabsKey(context: Context): String {
        val userKey = load(context).elevenLabsApiKey.trim()
        if (userKey.isNotBlank()) return userKey
        val buildKey = BuildConfig.ELEVENLABS_API_KEY.trim()
        return if (buildKey.isNotBlank() && buildKey != "YOUR_ELEVENLABS_API_KEY") buildKey else ""
    }

    fun resolveOpenAiKey(context: Context): String {
        val userKey = load(context).openAiApiKey.trim()
        if (userKey.isNotBlank()) return userKey
        val buildKey = BuildConfig.OPENAI_API_KEY.trim()
        return if (buildKey.isNotBlank() && buildKey != "YOUR_OPENAI_API_KEY") buildKey else ""
    }

    fun resolveGeminiDirectKey(context: Context): String =
        load(context).geminiDirectApiKey.trim()

    fun resolveIranGatewayKey(context: Context): String =
        load(context).iranGatewayApiKey.trim()

    private fun migrateLegacyPlaintextIfNeeded(context: Context) {
        val legacy = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        if (legacy.all.isEmpty()) return

        val encrypted = context.getSharedPreferences(ENCRYPTED_PREFS_NAME, Context.MODE_PRIVATE)
        if (encrypted.all.isEmpty()) {
            val migrated = UserApiKeysConfig(
                elevenLabsApiKey = legacy.getString(KEY_ELEVENLABS, "") ?: "",
                geminiDirectApiKey = legacy.getString(KEY_GEMINI, "") ?: "",
                openAiApiKey = legacy.getString(KEY_OPENAI, "") ?: "",
                openAiBaseUrl = (legacy.getString(KEY_OPENAI_URL, "") ?: "").ifBlank { "https://api.openai.com/v1" },
                openAiModel = (legacy.getString(KEY_OPENAI_MODEL, "") ?: "").ifBlank { "gpt-4o-mini" },
                customProviderName = legacy.getString(KEY_CUSTOM_NAME, "") ?: "",
                customProviderBaseUrl = legacy.getString(KEY_CUSTOM_URL, "") ?: "",
                customProviderApiKey = legacy.getString(KEY_CUSTOM_API, "") ?: "",
                customProviderModel = legacy.getString(KEY_CUSTOM_MODEL, "") ?: ""
            )
            save(context, migrated)
        } else {
            legacy.edit().clear().apply()
        }
    }
}
