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

class OpenAIProvider(private val apiKeyProvider: () -> String?) : CaptioningProvider {
    
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

        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $key")
            .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val t2 = SystemClock.elapsedRealtimeNanos()
        
        client.newCall(req).execute().use { resp ->
            val t3 = SystemClock.elapsedRealtimeNanos()
            
            if (!resp.isSuccessful) {
                val errorBody = resp.body?.string() ?: "No error body"
                return CaptionResult("OpenAI Error: ${resp.code}\n${resp.message}\n$errorBody")
            }
            
            val bodyStr = resp.body.string()
            val out = moshi.adapter(ChatResponse::class.java).fromJson(bodyStr)
            val text = out?.choices?.firstOrNull()?.message?.content ?: "(no content)"
            val t4 = SystemClock.elapsedRealtimeNanos()
            
            val usage = out?.usage
            return CaptionResult(
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
                    "total_tokens" to usage?.total_tokens
                )
            )
        }
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