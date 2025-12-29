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
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

class AzureVisionProvider(private val apiKeyProvider: () -> String?) : CaptioningProvider {
    
    companion object {
        private const val ENDPOINT = "https://imagecaptionmgr.cognitiveservices.azure.com"
        private const val API_VERSION = "2024-02-01"
        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 500L
        private const val MAX_BACKOFF_MS = 30000L
        private const val JITTER_FACTOR = 0.1
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

        return executeWithRetry(key, imageBytes, url, t0, t1)
    }

    private suspend fun executeWithRetry(
        key: String,
        imageBytes: ByteArray,
        url: String,
        t0: Long,
        t1: Long,
        retryCount: Int = 0
    ): CaptionResult {
        val req = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/octet-stream")
            .addHeader("Ocp-Apim-Subscription-Key", key)
            .post(imageBytes.toRequestBody("application/octet-stream".toMediaType()))
            .build()

        val t2 = SystemClock.elapsedRealtimeNanos()
        
        return try {
            client.newCall(req).execute().use { resp ->
                val t3 = SystemClock.elapsedRealtimeNanos()
                
                when {
                    resp.isSuccessful -> {
                        val bodyStr = resp.body?.string() ?: ""
                        val result = moshi.adapter(AzureVisionResponse::class.java).fromJson(bodyStr)
                        val t4 = SystemClock.elapsedRealtimeNanos()
                        
                        val caption = result?.captionResult?.text ?: "(no caption)"
                        val confidence = result?.captionResult?.confidence ?: 0.0
                        val modelVersion = result?.modelVersion ?: "2024-02-01"
                        
                        CaptionResult(
                            text = caption,
                            extra = mapOf(
                                "pre_ms" to (t1 - t0) / 1_000_000.0,
                                "http_ms" to (t3 - t2) / 1_000_000.0,
                                "post_ms" to (t4 - t3) / 1_000_000.0,
                                "e2e_ms" to (t4 - t0) / 1_000_000.0,
                                "model" to "Azure Computer Vision $modelVersion",
                                "confidence" to confidence,
                                "http_status" to resp.code,
                                "total_tokens" to caption.split(" ").size,
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
                        
                        executeWithRetry(key, imageBytes, url, t0, t1, retryCount + 1)
                    }
                    resp.code in 500..599 && retryCount < MAX_RETRIES -> {
                        // Server error - wait and retry
                        val waitMs = calculateBackoff(retryCount)
                        resp.body?.close()
                        
                        Thread.sleep(waitMs)
                        
                        executeWithRetry(key, imageBytes, url, t0, t1, retryCount + 1)
                    }
                    else -> {
                        val errorBody = resp.body?.string() ?: "No error body"
                        CaptionResult("Azure Vision Error: ${resp.code}\n${resp.message}\n$errorBody")
                    }
                }
            }
        } catch (e: Exception) {
            if (retryCount < MAX_RETRIES && e is java.net.SocketTimeoutException) {
                val waitMs = calculateBackoff(retryCount)
                Thread.sleep(waitMs)
                executeWithRetry(key, imageBytes, url, t0, t1, retryCount + 1)
            } else {
                CaptionResult("Azure Vision Request Error: ${e.message}")
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
