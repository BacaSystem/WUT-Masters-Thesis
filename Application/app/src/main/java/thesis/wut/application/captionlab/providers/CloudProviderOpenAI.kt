package thesis.wut.application.captionlab.providers

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

class CloudProviderOpenAI(private val apiKeyProvider: () -> String?) : CaptioningProvider {
    override val id: String = "openai_cloud"
    private val client = OkHttpClient()
    private val moshi = Moshi.Builder().build()

    override suspend fun caption(bitmap: Bitmap): CaptionResult {
        val key = apiKeyProvider() ?: return CaptionResult("No API key")


        // 1) JPEG → base64 data URL
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        val dataUrl = "data:image/jpeg;base64,$b64"

        // 2) Zbuduj payload Chat Completions (vision)
        val payload = ChatRequest(
            model = "gpt-4o-mini",
            messages = listOf(
                ChatMessage(
                    role = "user",
                    content = listOf(
                        ContentItem(type = "text", text = "Generate short description of this image."),
                        ContentItem(type = "image_url", image_url = ImageUrl(url = dataUrl))
                    )
                )
            ),
            temperature = 0.2
        )


        val adapter = moshi.adapter(ChatRequest::class.java)
        val json = adapter.toJson(payload)

        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $key")
//            .addHeader("OpenAI-Organization", "org-XF9f7DZwlmmQDiIBTu1KWgm0")
//            .addHeader("OpenAI-Project", "proj_Z72x1NgDPPZWQHNqAfEY0xYW")
            .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        Log.d("OPENAI", "req = $req")
        Log.d("OPENAI", "json = ${json.toRequestBody("application/json; charset=utf-8".toMediaType())}")



        client.newCall(req).execute().use { resp ->
            Log.d("OPENAI", "Response code: ${resp.code}")
            if (!resp.isSuccessful) {
                val errorBody = resp.body?.string() ?: "No error body"
                Log.e("OPENAI", "Error response: $errorBody")
                return CaptionResult("OpenAI Error: ${resp.code}\n${resp.message}\n$errorBody")
            }
            val bodyStr = resp.body.string()
            val out = moshi.adapter(ChatResponse::class.java).fromJson(bodyStr)
            val text = out?.choices?.firstOrNull()?.message?.content ?: "(no content)"
            return CaptionResult(text = text, extra = mapOf("http_status" to resp.code))
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
        val choices: List<Choice>
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
}