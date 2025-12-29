package thesis.wut.application.captionlab.providers

import android.graphics.Bitmap

interface CaptioningProvider {
    val id: String
    suspend fun caption(bitmap: Bitmap): CaptionResult
    
    /**
     * Cleanup resources used by this provider (optional)
     * Called between benchmark runs or when provider is no longer needed
     */
    fun cleanup() {}
}

data class CaptionResult(
    val text: String,
    val extra: Map<String, Any?> = emptyMap()
)