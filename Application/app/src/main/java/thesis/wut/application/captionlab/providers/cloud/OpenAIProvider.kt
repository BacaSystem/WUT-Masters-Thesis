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
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

class OpenAIProvider(private val apiKeyProvider: () -> String?) : CaptioningProvider {
    
    companion object {
        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 500L
        private const val MAX_BACKOFF_MS = 30000L
        private const val JITTER_FACTOR = 0.1
        private const val REQUEST_TIMEOUT_MS = 60000L
    }
    
    override val id: String = "openai_cloud"
    private val client = OkHttpClient()
    private val moshi = Moshi.Builder().build()

    override suspend fun caption(bitmap: Bitmap): CaptionResult {
        val key = apiKeyProvider() ?: return CaptionResult("No API key")

        val t0 = SystemClock.elapsedRealtimeNanos()
        
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        val dataUrl = "data:image/jpeg;base64,$b64"

        val payload = ChatRequest(
            model = "gpt-4o-mini",
            messages = listOf(
                ChatMessage(
                    role = "user",
                    content = listOf(
                        ContentItem(type = "text", text = "Generate a short, descriptive caption for this image."),
                        ContentItem(type = "image_url", image_url = ImageUrl(url = dataUrl))
                    )
                )
            ),
            temperature = 0.2
        )

        val adapter = moshi.adapter(ChatRequest::class.java)
        val json = adapter.toJson(payload)
        val t1 = SystemClock.elapsedRealtimeNanos()

        return executeWithRetry(key, json, t0, t1)
    }

    private suspend fun executeWithRetry(
        key: String,
        json: String,
        t0: Long,
        t1: Long,
        retryCount: Int = 0
    ): CaptionResult {
        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $key")
            .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val t2 = SystemClock.elapsedRealtimeNanos()
        
        return try {
            client.newCall(req).execute().use { resp ->
                val t3 = SystemClock.elapsedRealtimeNanos()
                
                when {
                    resp.isSuccessful -> {
                        val bodyStr = resp.body.string()
                        val out = moshi.adapter(ChatResponse::class.java).fromJson(bodyStr)
                        val text = out?.choices?.firstOrNull()?.message?.content ?: "(no content)"
                        val t4 = SystemClock.elapsedRealtimeNanos()
                        
                        val usage = out?.usage
                        CaptionResult(
                            text = text,
                            extra = mapOf(
                                "pre_ms" to (t1 - t0) / 1_000_000.0,
                                "http_ms" to (t3 - t2) / 1_000_000.0,
                                "post_ms" to (t4 - t3) / 1_000_000.0,
                                "e2e_ms" to (t4 - t0) / 1_000_000.0,
                                "http_status" to resp.code,
                                "model" to "gpt-4o-mini",
                                "prompt_tokens" to usage?.prompt_tokens,
                                "completion_tokens" to usage?.completion_tokens,
                                "total_tokens" to usage?.total_tokens,
                                "retry_count" to retryCount
                            )
                        )
                    }
                    resp.code == 429 && retryCount < MAX_RETRIES -> {
                        // Rate limited - wait and retry
                        val retryAfter = resp.headers["Retry-After"]?.toLongOrNull() ?: calculateBackoff(retryCount)
                        val waitMs = minOf(retryAfter * 1000, MAX_BACKOFF_MS)
                        
                        resp.body?.close()
                        Thread.sleep(waitMs)
                        
                        executeWithRetry(key, json, t0, t1, retryCount + 1)
                    }
                    resp.code in 500..599 && retryCount < MAX_RETRIES -> {
                        // Server error - wait and retry
                        val waitMs = calculateBackoff(retryCount)
                        val errorBody = resp.body?.string() ?: "No error body"
                        
                        Thread.sleep(waitMs)
                        
                        executeWithRetry(key, json, t0, t1, retryCount + 1)
                    }
                    else -> {
                        // Other error
                        val errorBody = resp.body?.string() ?: "No error body"
                        CaptionResult("OpenAI Error: ${resp.code}\n${resp.message}\n$errorBody")
                    }
                }
            }
        } catch (e: Exception) {
            if (retryCount < MAX_RETRIES && e is java.net.SocketTimeoutException) {
                val waitMs = calculateBackoff(retryCount)
                Thread.sleep(waitMs)
                executeWithRetry(key, json, t0, t1, retryCount + 1)
            } else {
                CaptionResult("OpenAI Request Error: ${e.message}")
            }
        }
    }

    private fun calculateBackoff(retryCount: Int): Long {
        // Exponential backoff with jitter
        val exponentialBackoff = INITIAL_BACKOFF_MS * 2.0.pow(retryCount)
        val jitter = exponentialBackoff * JITTER_FACTOR * Random.nextDouble()
        val backoffMs = exponentialBackoff + jitter
        return min(backoffMs.toLong(), MAX_BACKOFF_MS)
    }

    @JsonClass(generateAdapter = true)
    data class ChatRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Double? = null
    )
    
    @JsonClass(generateAdapter = true)
    data class ChatMessage(
        val role: String,
        val content: List<ContentItem>
    )
    
    @JsonClass(generateAdapter = true)
    data class ContentItem(
        val type: String,
        val text: String? = null,
        val image_url: ImageUrl? = null
    )
    
    @JsonClass(generateAdapter = true)
    data class ImageUrl(
        val url: String
    )
    
    @JsonClass(generateAdapter = true)
    data class ChatResponse(
        val choices: List<Choice>,
        val usage: Usage?
    )
    
    @JsonClass(generateAdapter = true)
    data class Choice(
        val message: ChatMessageOut
    )
    
    @JsonClass(generateAdapter = true)
    data class ChatMessageOut(
        val role: String,
        val content: String
    )
    
    @JsonClass(generateAdapter = true)
    data class Usage(
        val prompt_tokens: Int,
        val completion_tokens: Int,
        val total_tokens: Int
    )
}