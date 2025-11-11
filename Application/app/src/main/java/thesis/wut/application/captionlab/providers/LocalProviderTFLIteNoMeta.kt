package thesis.wut.application.captionlab.providers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.SystemClock
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp

class LocalProviderTFLiteNoMeta(private val context: Context) : CaptioningProvider {
    override val id: String = "tflite_local_nometa"

    private val modelPath = "models/mobilenet_v2_1.0_224_quant.tflite"
    private val labelsPath = "models/labels.txt"

    // Wymiary wejścia modelu (mobilenet_v2_1.0_224)
    private val inputSize = 224
    private val inputChannels = 3

    private val interpreter: Interpreter by lazy {
        Interpreter(loadModelFile(modelPath), Interpreter.Options().apply {
            // Na emulatorze CPU, więc bez delegatów.
            setNumThreads(Runtime.getRuntime().availableProcessors().coerceAtMost(4))
        })
    }

    private val labels: List<String> by lazy { loadLabels(labelsPath) }

    override suspend fun caption(bitmap: Bitmap): CaptionResult {
        val t0 = SystemClock.elapsedRealtimeNanos()
        val inputBuffer = preprocess(bitmap, inputSize, inputSize)
        val tPre = SystemClock.elapsedRealtimeNanos()

        // Wyjście: 1 x NUM_CLASSES (dla mobilenet_v2 zwykle 1001)
        val outputTensor: Tensor = interpreter.getOutputTensor(0)
        val numClasses = outputTensor.shape()[1]
        // Bufor na wyjście quantized (UINT8) lub float — wykryj typ
        val isQuantized = outputTensor.dataType().toString().contains("UINT8", ignoreCase = true)
        val output: Any = if (isQuantized) {
            Array(1) { ByteArray(numClasses) }
        } else {
            Array(1) { FloatArray(numClasses) }
        }

        interpreter.run(inputBuffer, output)
        val tInfer = SystemClock.elapsedRealtimeNanos()

        // De-kodowanie wyników
        val scores: FloatArray = if (isQuantized) {
            // zczytaj skale i zeroPoint z tensora
            val qParams = outputTensor.quantizationParams()
            val scale = qParams.scale
            val zeroPoint = qParams.zeroPoint
            val raw = (output as Array<ByteArray>)[0]
            FloatArray(numClasses) { i -> (raw[i].toInt() and 0xFF - zeroPoint) * scale }
        } else {
            (output as Array<FloatArray>)[0]
        }

        val probs = softmax(scores)
        val topK = topK(probs, labels, k = 3)
        val sentence = if (topK.isNotEmpty()) {
            val parts = topK.joinToString { "${it.label} (${String.format("%.0f", it.prob * 100)}%)" }
            "Obraz prawdopodobnie przedstawia: $parts."
        } else {
            "Brak pewnej klasyfikacji."
        }

        val tPost = SystemClock.elapsedRealtimeNanos()
        val preMs = (tPre - t0) / 1e6
        val inferMs = (tInfer - tPre) / 1e6
        val postMs = (tPost - tInfer) / 1e6
        val e2eMs = (tPost - t0) / 1e6

        return CaptionResult(
            text = sentence,
            extra = mapOf(
                "input_size" to inputSize,
                "pre_ms" to preMs,
                "infer_ms" to inferMs,
                "post_ms" to postMs,
                "e2e_ms" to e2eMs
            )
        )
    }

    private fun loadModelFile(assetPath: String): MappedByteBuffer {
        context.assets.openFd(assetPath).use { afd ->
            FileInputStream(afd.fileDescriptor).channel.use { fc ->
                return fc.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.length)
            }
        }
    }

    private fun loadLabels(assetPath: String): List<String> {
        context.assets.open(assetPath).bufferedReader().useLines { lines ->
            return lines.filter { it.isNotBlank() }.toList()
        }
    }

    private fun preprocess(src: Bitmap, dstW: Int, dstH: Int): ByteBuffer {
        // Model quant mobilenet v2 oczekuje zwykle UINT8 w [0..255]
        val dst = resizeKeepAspect(src, dstW, dstH)
        val inputSizeBytes = 1 * dstW * dstH * inputChannels
        val buffer = ByteBuffer.allocateDirect(inputSizeBytes)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(dstW * dstH)
        dst.getPixels(pixels, 0, dstW, 0, 0, dstW, dstH)
        var idx = 0
        for (y in 0 until dstH) {
            for (x in 0 until dstW) {
                val p = pixels[idx++]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                // kolejność kanałów: mobilenet tflite zwykle NHWC RGB
                buffer.put(r.toByte())
                buffer.put(g.toByte())
                buffer.put(b.toByte())
            }
        }
        buffer.rewind()
        return buffer
    }

    private fun resizeKeepAspect(src: Bitmap, dstW: Int, dstH: Int): Bitmap {
        // proste skalowanie do dokładnych wymiarów (bez letterboxingu)
        return Bitmap.createScaledBitmap(src, dstW, dstH, true)
    }

    data class Top(val label: String, val prob: Float)

    private fun softmax(logits: FloatArray): FloatArray {
        // numerically stable softmax
        val max = logits.maxOrNull() ?: 0f
        var sum = 0.0
        val exps = DoubleArray(logits.size) { i ->
            val v = exp((logits[i] - max).toDouble())
            sum += v
            v
        }
        return FloatArray(logits.size) { i -> (exps[i] / sum).toFloat() }
    }

    private fun topK(probs: FloatArray, labels: List<String>, k: Int): List<Top> {
        val pairs = probs.indices.map { i ->
            val label = if (i in labels.indices) labels[i] else "class_$i"
            Top(label, probs[i])
        }
        return pairs.sortedByDescending { it.prob }.take(k)
    }
}