package thesis.wut.application.captionlab.providers.cloud

import android.graphics.Bitmap
import android.os.SystemClock
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import thesis.wut.application.captionlab.providers.CaptionResult
import thesis.wut.application.captionlab.providers.CaptioningProvider
import java.io.ByteArrayOutputStream

class AzureVisionProvider(private val apiKeyProvider: () -> String?) : CaptioningProvider {
    
    companion object {
        private const val ENDPOINT = "https://imagecaptionmgr.cognitiveservices.azure.com"
        private const val API_VERSION = "2024-02-01"
    }
    
    override val id: String = "azure_vision_cloud"
    private val client = OkHttpClient()
    private val moshi = Moshi.Builder().build()

    override suspend fun caption(bitmap: Bitmap): CaptionResult {
        val key = apiKeyProvider() ?: return CaptionResult("No API key")

        val t0 = SystemClock.elapsedRealtimeNanos()
        
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
        val imageBytes = baos.toByteArray()
        val t1 = SystemClock.elapsedRealtimeNanos()

        val url = "$ENDPOINT/computervision/imageanalysis:analyze" +
                "?api-version=$API_VERSION" +
                "&features=caption" +
                "&gender-neutral-caption=true"

        val req = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/octet-stream")
            .addHeader("Ocp-Apim-Subscription-Key", key)
            .post(imageBytes.toRequestBody("application/octet-stream".toMediaType()))
            .build()

        val t2 = SystemClock.elapsedRealtimeNanos()
        
        client.newCall(req).execute().use { resp ->
            val t3 = SystemClock.elapsedRealtimeNanos()
            
            if (!resp.isSuccessful) {
                val errorBody = resp.body?.string() ?: "No error body"
                return CaptionResult("Azure Vision Error: ${resp.code}\n${resp.message}\n$errorBody")
            }
            
            val bodyStr = resp.body?.string() ?: ""
            val result = moshi.adapter(AzureVisionResponse::class.java).fromJson(bodyStr)
            val t4 = SystemClock.elapsedRealtimeNanos()
            
            val caption = result?.captionResult?.text ?: "(no caption)"
            val confidence = result?.captionResult?.confidence ?: 0.0
            val modelVersion = result?.modelVersion ?: "2024-02-01"
            
            return CaptionResult(
                text = caption,
                extra = mapOf(
                    "pre_ms" to (t1 - t0) / 1_000_000.0,
                    "http_ms" to (t3 - t2) / 1_000_000.0,
                    "post_ms" to (t4 - t3) / 1_000_000.0,
                    "e2e_ms" to (t4 - t0) / 1_000_000.0,
                    "model" to "Azure Computer Vision $modelVersion",
                    "confidence" to confidence,
                    "http_status" to resp.code,
                    "total_tokens" to caption.split(" ").size // Approximate token count
                )
            )
        }
    }

    @JsonClass(generateAdapter = true)
    data class AzureVisionResponse(
        val captionResult: CaptionData?,
        val modelVersion: String?
    )

    @JsonClass(generateAdapter = true)
    data class CaptionData(
        val text: String,
        val confidence: Double
    )
}
