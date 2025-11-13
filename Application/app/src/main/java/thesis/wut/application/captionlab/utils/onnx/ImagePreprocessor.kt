package thesis.wut.application.captionlab.utils.onnx

import android.graphics.Bitmap
import androidx.core.graphics.scale

internal object ImagePreprocessor {
    
    fun preprocess(
        bitmap: Bitmap, 
        targetSize: Int,
        mean: FloatArray = floatArrayOf(0.485f, 0.456f, 0.406f),
        std: FloatArray = floatArrayOf(0.229f, 0.224f, 0.225f)
    ): FloatArray {
        val resized = bitmap.scale(targetSize, targetSize)
        val floatArray = FloatArray(1 * 3 * targetSize * targetSize)
        val pixels = IntArray(targetSize * targetSize)
        
        resized.getPixels(pixels, 0, targetSize, 0, 0, targetSize, targetSize)
        
        var idx = 0
        
        // Channel 0 (R)
        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            floatArray[idx++] = (r - mean[0]) / std[0]
        }
        
        // Channel 1 (G)
        for (pixel in pixels) {
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            floatArray[idx++] = (g - mean[1]) / std[1]
        }
        
        // Channel 2 (B)
        for (pixel in pixels) {
            val b = (pixel and 0xFF) / 255.0f
            floatArray[idx++] = (b - mean[2]) / std[2]
        }
        
        return floatArray
    }
}

