package com.example.data.remote

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

enum class ApiProviderTarget(val titleFa: String) {
    ELEVENLABS("ElevenLabs"),
    IRAN_GATEWAY("درگاه ایران (بدون فیلترشکن)"),
    GEMINI("Google Gemini"),
    OPENAI("OpenAI / GPT"),
    CUSTOM("موتور دلخواه")
}

data class ApiTestResult(
    val provider: ApiProviderTarget,
    val success: Boolean,
    val message: String,
    val latencyMs: Long = 0L
)

object ApiConnectionTester {

    suspend fun testProvider(
        context: Context,
        target: ApiProviderTarget,
        config: UserApiKeysConfig
    ): ApiTestResult = withContext(Dispatchers.IO) {
        when (target) {
            ApiProviderTarget.ELEVENLABS -> testElevenLabs(context, config.elevenLabsApiKey.trim())
            ApiProviderTarget.IRAN_GATEWAY -> testOpenAiCompatible(
                context = context,
                target = ApiProviderTarget.IRAN_GATEWAY,
                providerTitle = config.iranGatewayName.trim().ifBlank { "درگاه ایران (AvalAI/Liara/GapGPT)" },
                baseUrl = config.iranGatewayBaseUrl.trim().ifBlank { "https://api.avalai.ir/v1" },
                apiKey = config.iranGatewayApiKey.trim(),
                model = config.iranGatewayModel.trim().ifBlank { "gpt-4o-mini" },
                bypassProxyForDirectIran = true
            )
            ApiProviderTarget.GEMINI -> testGemini(context, config.geminiDirectApiKey.trim())
            ApiProviderTarget.OPENAI -> testOpenAiCompatible(
                context = context,
                target = ApiProviderTarget.OPENAI,
                providerTitle = "OpenAI / GPT",
                baseUrl = config.openAiBaseUrl.trim().ifBlank { "https://api.openai.com/v1" },
                apiKey = config.openAiApiKey.trim(),
                model = config.openAiModel.trim().ifBlank { "gpt-4o-mini" },
                bypassProxyForDirectIran = false
            )
            ApiProviderTarget.CUSTOM -> testOpenAiCompatible(
                context = context,
                target = ApiProviderTarget.CUSTOM,
                providerTitle = config.customProviderName.trim().ifBlank { "موتور دلخواه" },
                baseUrl = config.customProviderBaseUrl.trim(),
                apiKey = config.customProviderApiKey.trim(),
                model = config.customProviderModel.trim(),
                bypassProxyForDirectIran = false
            )
        }
    }

    private fun testElevenLabs(context: Context, rawKey: String): ApiTestResult {
        val key = rawKey.ifBlank { UserApiKeyStore.resolveElevenLabsKey(context) }
        if (key.isBlank()) {
            return ApiTestResult(
                provider = ApiProviderTarget.ELEVENLABS,
                success = false,
                message = "کلید ElevenLabs وارد نشده است."
            )
        }

        val startMs = System.currentTimeMillis()
        return try {
            val client = ProxyConfigStore.buildOkHttpClient(context, readTimeoutSec = 15L)
            val subReq = Request.Builder()
                .url("https://api.elevenlabs.io/v1/user/subscription")
                .addHeader("xi-api-key", key)
                .get()
                .build()

            client.newCall(subReq).execute().use { resp ->
                val elapsed = System.currentTimeMillis() - startMs
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    val json = runCatching { JSONObject(body) }.getOrNull()
                    val tier = json?.optString("tier", "").orEmpty()
                    val count = json?.optInt("character_count", -1) ?: -1
                    val limit = json?.optInt("character_limit", -1) ?: -1
                    val detail = if (limit > 0 && count >= 0) {
                        val remaining = (limit - count).coerceAtLeast(0)
                        "پلن: ${tier.ifBlank { "فعال" }} • سهمیه باقی‌مانده: $remaining کاراکتر"
                    } else {
                        "احراز هویت موفق"
                    }
                    return ApiTestResult(
                        provider = ApiProviderTarget.ELEVENLABS,
                        success = true,
                        message = "اتصال ElevenLabs برقرار است ($detail)",
                        latencyMs = elapsed
                    )
                }

                if (resp.code == 401 && body.contains("missing_permissions", ignoreCase = true)) {
                    val modelsReq = Request.Builder()
                        .url("https://api.elevenlabs.io/v1/models")
                        .addHeader("xi-api-key", key)
                        .get()
                        .build()
                    client.newCall(modelsReq).execute().use { mResp ->
                        val mElapsed = System.currentTimeMillis() - startMs
                        if (mResp.isSuccessful) {
                            return ApiTestResult(
                                provider = ApiProviderTarget.ELEVENLABS,
                                success = true,
                                message = "کلید محدود ElevenLabs معتبر است (دسترسی مدل‌ها تایید شد)",
                                latencyMs = mElapsed
                            )
                        }
                    }
                }

                ApiTestResult(
                    provider = ApiProviderTarget.ELEVENLABS,
                    success = false,
                    message = formatHttpFailure("ElevenLabs", resp.code),
                    latencyMs = elapsed
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiTestResult(
                provider = ApiProviderTarget.ELEVENLABS,
                success = false,
                message = "خطای شبکه ElevenLabs: ${e.localizedMessage ?: "بررسی پروکسی/اینترنت"}"
            )
        }
    }

    private fun testGemini(context: Context, apiKey: String): ApiTestResult {
        if (apiKey.isBlank()) {
            return ApiTestResult(
                provider = ApiProviderTarget.GEMINI,
                success = false,
                message = "کلید شخصی Gemini وارد نشده است (اتصال پیش‌فرض داخلی از طریق Firebase App Check فعال است)."
            )
        }
        val startMs = System.currentTimeMillis()
        return try {
            val client = ProxyConfigStore.buildOkHttpClient(context, readTimeoutSec = 15L)
            val url = "https://generativelanguage.googleapis.com/v1beta/models".toHttpUrlOrNull()!!
                .newBuilder()
                .addQueryParameter("key", apiKey)
                .build()
            val req = Request.Builder().url(url).get().build()

            client.newCall(req).execute().use { resp ->
                val elapsed = System.currentTimeMillis() - startMs
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    val modelCount = runCatching { JSONObject(body).optJSONArray("models")?.length() ?: 0 }.getOrDefault(0)
                    ApiTestResult(
                        provider = ApiProviderTarget.GEMINI,
                        success = true,
                        message = "اتصال Gemini API تایید شد ($modelCount مدل در دسترس)",
                        latencyMs = elapsed
                    )
                } else {
                    ApiTestResult(
                        provider = ApiProviderTarget.GEMINI,
                        success = false,
                        message = formatHttpFailure("Gemini", resp.code),
                        latencyMs = elapsed
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val safeMsg = (e.localizedMessage ?: "بررسی پروکسی/اینترنت").replace(apiKey, "***")
            ApiTestResult(
                provider = ApiProviderTarget.GEMINI,
                success = false,
                message = "خطای اتصال Gemini: $safeMsg"
            )
        }
    }

    private fun testOpenAiCompatible(
        context: Context,
        target: ApiProviderTarget,
        providerTitle: String,
        baseUrl: String,
        apiKey: String,
        model: String,
        bypassProxyForDirectIran: Boolean
    ): ApiTestResult {
        if (apiKey.isBlank()) {
            return ApiTestResult(
                provider = target,
                success = false,
                message = "کلید $providerTitle وارد نشده است."
            )
        }
        val cleanBase = baseUrl.trim().trimEnd('/')
        val parsedUrl = cleanBase.toHttpUrlOrNull()
        if (parsedUrl == null || (parsedUrl.scheme != "https" && parsedUrl.scheme != "http")) {
            return ApiTestResult(
                provider = target,
                success = false,
                message = "آدرس Base URL برای $providerTitle معتبر نیست (باید با https:// شروع شود)."
            )
        }

        val startMs = System.currentTimeMillis()
        return try {
            val client = if (bypassProxyForDirectIran) {
                ProxyConfigStore.buildDirectOkHttpClient(readTimeoutSec = 15L)
            } else {
                ProxyConfigStore.buildOkHttpClient(context, readTimeoutSec = 15L)
            }
            val modelsReq = Request.Builder()
                .url("$cleanBase/models")
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()

            client.newCall(modelsReq).execute().use { resp ->
                val elapsed = System.currentTimeMillis() - startMs
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    val dataCount = runCatching { JSONObject(body).optJSONArray("data")?.length() ?: 0 }.getOrDefault(0)
                    val routeNote = if (bypassProxyForDirectIran) "مستقیم بدون فیلترشکن" else "آماده"
                    return ApiTestResult(
                        provider = target,
                        success = true,
                        message = "اتصال $providerTitle برقرار است ($routeNote${if (dataCount > 0) " • $dataCount مدل" else ""})",
                        latencyMs = elapsed
                    )
                }

                if ((resp.code == 404 || resp.code == 405) && model.isNotBlank()) {
                    val payload = JSONObject().apply {
                        put("model", model)
                        put("max_tokens", 4)
                        put("messages", JSONArray().apply {
                            put(JSONObject().put("role", "user").put("content", "hi"))
                        })
                    }
                    val chatReq = Request.Builder()
                        .url("$cleanBase/chat/completions")
                        .addHeader("Authorization", "Bearer $apiKey")
                        .addHeader("Content-Type", "application/json")
                        .post(payload.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    client.newCall(chatReq).execute().use { cResp ->
                        val cElapsed = System.currentTimeMillis() - startMs
                        if (cResp.isSuccessful) {
                            return ApiTestResult(
                                provider = target,
                                success = true,
                                message = "اتصال $providerTitle با مدل $model برقرار است",
                                latencyMs = cElapsed
                            )
                        }
                        return ApiTestResult(
                            provider = target,
                            success = false,
                            message = formatHttpFailure(providerTitle, cResp.code),
                            latencyMs = cElapsed
                        )
                    }
                }

                ApiTestResult(
                    provider = target,
                    success = false,
                    message = formatHttpFailure(providerTitle, resp.code),
                    latencyMs = elapsed
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val safeMsg = (e.localizedMessage ?: "بررسی اینترنت").replace(apiKey, "***")
            ApiTestResult(
                provider = target,
                success = false,
                message = "خطای اتصال $providerTitle: $safeMsg"
            )
        }
    }

    private fun formatHttpFailure(providerName: String, code: Int): String = when (code) {
        401 -> "کلید $providerName نامعتبر یا منقضی است (کد 401)"
        403 -> "دسترسی $providerName مجاز نیست یا تحریم/محدود است (کد 403)"
        404 -> "مسیر API در $providerName پیدا نشد (کد 404)"
        429 -> "سهمیه یا محدودیت نرخ درخواست $providerName پر شده است (کد 429)"
        else -> "پاسخ ناموفق از $providerName (کد $code)"
    }
}
