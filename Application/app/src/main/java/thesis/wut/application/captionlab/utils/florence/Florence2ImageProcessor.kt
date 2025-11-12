package thesis.wut.application.captionlab.utils.florence

import android.graphics.Bitmap
import android.graphics.Matrix
import java.nio.FloatBuffer

/**
 * Image processor for Florence-2 model
 * 
 * Converts Bitmap to normalized pixel values in NCHW format [1, 3, H, W]
 * with ImageNet normalization (mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
 * 
 * @param targetSize Target image size (Florence-2 uses 768x768)
 */
class Florence2ImageProcessor(private val targetSize: Int = 768) {
    
    companion object {
        // ImageNet normalization constants
        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
    }
    
    /**
     * Preprocess bitmap to FloatBuffer in NCHW format
     * 
     * Steps:
     * 1. Resize to targetSize x targetSize
     * 2. Convert to RGB (if needed)
     * 3. Normalize to [0, 1]
     * 4. Apply ImageNet normalization
     * 5. Convert to NCHW format: [Channel, Height, Width]
     * 
     * @param bitmap Input image
     * @return FloatBuffer with shape [1, 3, targetSize, targetSize]
     */
    fun preprocess(bitmap: Bitmap): FloatBuffer {
        // Resize bitmap
        val resizedBitmap = resizeBitmap(bitmap, targetSize, targetSize)
        
        // Allocate buffer for NCHW format: [1, 3, H, W]
        val bufferSize = 3 * targetSize * targetSize
        val buffer = FloatBuffer.allocate(bufferSize)
        
        // Extract pixels
        val pixels = IntArray(targetSize * targetSize)
        resizedBitmap.getPixels(pixels, 0, targetSize, 0, 0, targetSize, targetSize)
        
        // Convert to NCHW format with normalization
        // Channel order: R, G, B
        for (c in 0..2) {
            val mean = MEAN[c]
            val std = STD[c]
            
            for (h in 0 until targetSize) {
                for (w in 0 until targetSize) {
                    val pixelIdx = h * targetSize + w
                    val pixel = pixels[pixelIdx]
                    
                    // Extract channel value (0-255)
                    val channelValue = when (c) {
                        0 -> (pixel shr 16) and 0xFF // Red
                        1 -> (pixel shr 8) and 0xFF  // Green
                        2 -> pixel and 0xFF          // Blue
                        else -> 0
                    }
                    
                    // Normalize: (value / 255.0 - mean) / std
                    val normalized = ((channelValue / 255.0f) - mean) / std
                    
                    buffer.put(normalized)
                }
            }
        }
        
        buffer.rewind()
        
        // Clean up
        if (resizedBitmap != bitmap) {
            resizedBitmap.recycle()
        }
        
        return buffer
    }
    
    /**
     * Resize bitmap to target dimensions
     * 
     * @param bitmap Input bitmap
     * @param width Target width
     * @param height Target height
     * @return Resized bitmap
     */
    private fun resizeBitmap(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        if (bitmap.width == width && bitmap.height == height) {
            return bitmap
        }
        
        val scaleWidth = width.toFloat() / bitmap.width
        val scaleHeight = height.toFloat() / bitmap.height
        
        val matrix = Matrix()
        matrix.postScale(scaleWidth, scaleHeight)
        
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
