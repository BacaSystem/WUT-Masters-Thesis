package thesis.wut.application.captionlab.providers

import android.graphics.Bitmap

interface CaptioningProvider {
    val id: String
    suspend fun caption(bitmap: Bitmap): CaptionResult
}

data class CaptionResult(
    val text: String,
    val extra: Map<String, Any?> = emptyMap()
)