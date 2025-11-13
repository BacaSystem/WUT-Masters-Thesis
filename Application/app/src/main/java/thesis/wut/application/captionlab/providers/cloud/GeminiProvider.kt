package thesis.wut.application.captionlab.providers.cloud

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Base64
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import thesis.wut.application.captionlab.providers.CaptionResult
import thesis.wut.application.captionlab.providers.CaptioningProvider
import java.io.ByteArrayOutputStream

class GeminiProvider(private val apiKeyProvider: () -> String?) : CaptioningProvider {
    
    companion object {
        private const val MODEL = "gemini-2.5-flash-lite"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    }
    
    override val id: String = "gemini_cloud"
    private val client = OkHttpClient()
    private val moshi = Moshi.Builder().build()

    override suspend fun caption(bitmap: Bitmap): CaptionResult {
        val key = apiKeyProvider() ?: return CaptionResult("No API key")

        val t0 = SystemClock.elapsedRealtimeNanos()
        
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

        val payload = GeminiRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = "Generate a short, descriptive caption for this image."),
                        Part(inline_data = InlineData(mime_type = "image/jpeg", data = b64))
                    )
                )
            ),
            generationConfig = GenerationConfig(
                temperature = 0.2,
                maxOutputTokens = 100
            )
        )

        val adapter = moshi.adapter(GeminiRequest::class.java)
        val json = adapter.toJson(payload)
        val t1 = SystemClock.elapsedRealtimeNanos()

        val url = "$BASE_URL/$MODEL:generateContent?key=$key"
        val req = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val t2 = SystemClock.elapsedRealtimeNanos()
        
        client.newCall(req).execute().use { resp ->
            val t3 = SystemClock.elapsedRealtimeNanos()
            
            if (!resp.isSuccessful) {
                val errorBody = resp.body?.string() ?: "No error body"
                return CaptionResult("Gemini Error: ${resp.code}\n${resp.message}\n$errorBody")
            }
            
            val bodyStr = resp.body?.string() ?: ""
            val result = moshi.adapter(GeminiResponse::class.java).fromJson(bodyStr)
            val t4 = SystemClock.elapsedRealtimeNanos()
            
            val text = result?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "(no content)"
            val usage = result?.usageMetadata
            
            return CaptionResult(
                text = text,
                extra = mapOf(
                    "pre_ms" to (t1 - t0) / 1_000_000.0,
                    "http_ms" to (t3 - t2) / 1_000_000.0,
                    "post_ms" to (t4 - t3) / 1_000_000.0,
                    "e2e_ms" to (t4 - t0) / 1_000_000.0,
                    "http_status" to resp.code,
                    "model" to MODEL,
                    "prompt_tokens" to usage?.promptTokenCount,
                    "completion_tokens" to usage?.candidatesTokenCount,
                    "total_tokens" to usage?.totalTokenCount
                )
            )
        }
    }

    @JsonClass(generateAdapter = true)
    data class GeminiRequest(
        val contents: List<Content>,
        val generationConfig: GenerationConfig? = null
    )

    @JsonClass(generateAdapter = true)
    data class Content(
        val parts: List<Part>
    )

    @JsonClass(generateAdapter = true)
    data class Part(
        val text: String? = null,
        val inline_data: InlineData? = null
    )

    @JsonClass(generateAdapter = true)
    data class InlineData(
        val mime_type: String,
        val data: String
    )

    @JsonClass(generateAdapter = true)
    data class GenerationConfig(
        val temperature: Double,
        val maxOutputTokens: Int
    )

    @JsonClass(generateAdapter = true)
    data class GeminiResponse(
        val candidates: List<Candidate>?,
        val usageMetadata: UsageMetadata?
    )

    @JsonClass(generateAdapter = true)
    data class Candidate(
        val content: ContentResponse,
        val finishReason: String?
    )

    @JsonClass(generateAdapter = true)
    data class ContentResponse(
        val parts: List<PartResponse>
    )

    @JsonClass(generateAdapter = true)
    data class PartResponse(
        val text: String
    )

    @JsonClass(generateAdapter = true)
    data class UsageMetadata(
        val promptTokenCount: Int,
        val candidatesTokenCount: Int,
        val totalTokenCount: Int
    )
}
