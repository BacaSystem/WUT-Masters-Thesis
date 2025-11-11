package thesis.wut.application.captionlab.utils.onnx

import android.graphics.Bitmap
import java.nio.FloatBuffer
import kotlin.math.roundToInt


class ImagePreprocessor(
    private val inputW: Int = 384,
    private val inputH: Int = 384,
    private val mean: FloatArray = floatArrayOf(0.485f, 0.456f, 0.406f),
    private val std: FloatArray = floatArrayOf(0.229f, 0.224f, 0.225f)
) {
    /** Zwraca bufor w układzie NHWC (1, H, W, 3) lub NCHW w zależności od modelu. */
    fun toNCHW(bitmap: Bitmap): FloatBuffer {
        val scaled = Bitmap.createScaledBitmap(bitmap, inputW, inputH, true)
        val buf = FloatBuffer.allocate(1 * 3 * inputH * inputW)
        val pixels = IntArray(inputW * inputH)
        scaled.getPixels(pixels, 0, inputW, 0, 0, inputW, inputH)
        var ci = 0
// kanały osobno: [N=0,C=R][C=G][C=B]
// Indeks: c*H*W + y*W + x
        for (c in 0 until 3) {
            for (y in 0 until inputH) {
                for (x in 0 until inputW) {
                    val p = pixels[y * inputW + x]
                    val r = ((p shr 16) and 0xFF) / 255f
                    val g = ((p shr 8) and 0xFF) / 255f
                    val b = (p and 0xFF) / 255f
                    val v = when (c) {
                        0 -> (r - mean[0]) / std[0]
                        1 -> (g - mean[1]) / std[1]
                        else -> (b - mean[2]) / std[2]
                    }
                    buf.put(ci++, v)
                }
            }
        }
        buf.rewind()
        return buf
    }
}