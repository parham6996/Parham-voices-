package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Streaming
import retrofit2.http.Body

@JsonClass(generateAdapter = true)
data class VoicesListResponse(
    @Json(name = "voices") val voices: List<RemoteVoiceDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class RemoteVoiceDto(
    @Json(name = "voice_id") val voiceId: String,
    @Json(name = "name") val name: String,
    @Json(name = "category") val category: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "labels") val labels: Map<String, String>? = null
)

@JsonClass(generateAdapter = true)
data class AddOrEditVoiceResponse(
    @Json(name = "voice_id") val voiceId: String,
    @Json(name = "requires_verification") val requiresVerification: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class EditVoiceStatusResponse(
    @Json(name = "status") val status: String? = null
)

@JsonClass(generateAdapter = true)
data class TtsRequestBody(
    @Json(name = "text") val text: String,
    @Json(name = "model_id") val modelId: String = "eleven_v3",
    @Json(name = "voice_settings") val voiceSettings: VoiceSettingsDto
)

@JsonClass(generateAdapter = true)
data class VoiceSettingsDto(
    @Json(name = "stability") val stability: Float,
    @Json(name = "similarity_boost") val similarityBoost: Float,
    @Json(name = "style") val style: Float,
    @Json(name = "use_speaker_boost") val useSpeakerBoost: Boolean = true
)

@JsonClass(generateAdapter = true)
data class ScribeSttResponse(
    @Json(name = "language_code") val languageCode: String? = null,
    @Json(name = "text") val text: String = ""
)

interface ElevenLabsApi {

    @GET("v1/voices")
    suspend fun getVoices(
        @Header("xi-api-key") apiKey: String
    ): Response<VoicesListResponse>

    @Multipart
    @POST("v1/speech-to-text")
    suspend fun speechToText(
        @Header("xi-api-key") apiKey: String,
        @Part("model_id") modelId: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<ScribeSttResponse>

    @Multipart
    @POST("v1/voices/add")
    suspend fun addVoice(
        @Header("xi-api-key") apiKey: String,
        @Part("name") name: RequestBody,
        @Part("description") description: RequestBody,
        @Part("labels") labels: RequestBody,
        @Part("remove_background_noise") removeBackgroundNoise: RequestBody,
        @Part files: List<MultipartBody.Part>
    ): Response<AddOrEditVoiceResponse>

    @Multipart
    @POST("v1/voices/{voice_id}/edit")
    suspend fun editVoice(
        @Header("xi-api-key") apiKey: String,
        @Path("voice_id") voiceId: String,
        @Part("name") name: RequestBody,
        @Part("description") description: RequestBody,
        @Part("labels") labels: RequestBody,
        @Part("remove_background_noise") removeBackgroundNoise: RequestBody,
        @Part files: List<MultipartBody.Part>
    ): Response<EditVoiceStatusResponse>

    @DELETE("v1/voices/{voice_id}")
    suspend fun deleteVoice(
        @Header("xi-api-key") apiKey: String,
        @Path("voice_id") voiceId: String
    ): Response<ResponseBody>

    @Streaming
    @POST("v1/text-to-speech/{voice_id}")
    suspend fun textToSpeech(
        @Header("xi-api-key") apiKey: String,
        @Path("voice_id") voiceId: String,
        @Body body: TtsRequestBody
    ): Response<ResponseBody>
}
