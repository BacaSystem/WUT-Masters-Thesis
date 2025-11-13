package thesis.wut.application.captionlab.providers.local

import ai.onnxruntime.*
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import thesis.wut.application.captionlab.providers.CaptioningProvider
import thesis.wut.application.captionlab.providers.CaptionResult
import thesis.wut.application.captionlab.utils.blip.BlipTokenizer
import thesis.wut.application.captionlab.utils.onnx.ImagePreprocessor
import thesis.wut.application.captionlab.utils.onnx.OnnxTensorHelper
import java.io.File

class BlipProvider(private val context: Context) : CaptioningProvider {

    companion object {
        private const val TAG = "BlipProvider"
        private const val MODEL_PATH = "models/blip"
        
        private const val IMAGE_SIZE = 384
        private const val HIDDEN_DIM = 768
        private const val MAX_LENGTH = 20
        private const val CLS_TOKEN_ID = 30522L
        private const val SEP_TOKEN_ID = 102L
        
        private val IMAGE_MEAN = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
        private val IMAGE_STD = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)
    }

    override val id = "blip_onnx_local"

    private val ortEnv: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private val sessionOptions: OrtSession.SessionOptions by lazy {
        OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
        }
    }

    private val encoderModel: OrtSession by lazy { loadModel("$MODEL_PATH/encoder_model.onnx") }
    private val decoderModel: OrtSession by lazy { loadModel("$MODEL_PATH/decoder_model.onnx") }

    private val tokenizer: BlipTokenizer by lazy {
        BlipTokenizer(context, MODEL_PATH, CLS_TOKEN_ID, SEP_TOKEN_ID)
    }

    override suspend fun caption(bitmap: Bitmap): CaptionResult = runCatching {
        val start = SystemClock.elapsedRealtimeNanos()
        val metrics = mutableMapOf<String, Any?>()

        val preStart = SystemClock.elapsedRealtimeNanos()
        val pixelValues = ImagePreprocessor.preprocess(bitmap, IMAGE_SIZE, IMAGE_MEAN, IMAGE_STD)
        metrics["pre_ms"] = (SystemClock.elapsedRealtimeNanos() - preStart) / 1_000_000.0

        val encStart = SystemClock.elapsedRealtimeNanos()
        val encoderHiddenStates = encodeImage(pixelValues)
        metrics["enc_ms"] = (SystemClock.elapsedRealtimeNanos() - encStart) / 1_000_000.0

        val decStart = SystemClock.elapsedRealtimeNanos()
        val generatedIds = generateText(encoderHiddenStates)
        metrics["dec_ms"] = (SystemClock.elapsedRealtimeNanos() - decStart) / 1_000_000.0

        val postStart = SystemClock.elapsedRealtimeNanos()
        val text = tokenizer.decode(generatedIds, skipSpecialTokens = true)
        metrics["post_ms"] = (SystemClock.elapsedRealtimeNanos() - postStart) / 1_000_000.0

        metrics["e2e_ms"] = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
        metrics["tokens_generated"] = generatedIds.size

        CaptionResult(text, metrics)
    }.getOrElse { error ->
        Log.e(TAG, "Inference failed", error)
        CaptionResult("Error: ${error.message}", mapOf("error" to error.toString()))
    }

    private fun loadModel(path: String): OrtSession {
        val pathHash = path.hashCode().toString().replace("-", "n")
        val fileName = path.substringAfterLast('/')
        val cachedFileName = "${pathHash}_${fileName}"
        val outFile = File(context.cacheDir, cachedFileName)

        try {
            if (!outFile.exists() || outFile.length() == 0L) {
                context.assets.open(path).use { input ->
                    outFile.outputStream().use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var read = input.read(buffer)
                        while (read >= 0) {
                            output.write(buffer, 0, read)
                            read = input.read(buffer)
                        }
                        output.flush()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract model: $path", e)
            throw e
        }

        return ortEnv.createSession(outFile.absolutePath, sessionOptions)
    }

    private fun encodeImage(pixelValues: FloatArray): FloatArray {
        val shape = longArrayOf(1, 3, IMAGE_SIZE.toLong(), IMAGE_SIZE.toLong())
        val tensor = OnnxTensorHelper.createFloatTensor(ortEnv, pixelValues, shape)

        val result = encoderModel.run(mapOf("pixel_values" to tensor))
        val hidden = OnnxTensorHelper.extractFloatArray(result)

        tensor.close()
        result[0]?.close()
        result[1]?.close()

        return hidden
    }

    private fun generateText(encoderHiddenStates: FloatArray): List<Long> {
        val generated = mutableListOf<Long>()
        var currentIds = listOf(CLS_TOKEN_ID)

        for (step in 0 until MAX_LENGTH) {
            val next = decodeStep(currentIds, encoderHiddenStates)
            if (next == SEP_TOKEN_ID && generated.isNotEmpty()) break
            generated.add(next)
            currentIds = currentIds + next
        }

        return generated
    }

    private fun decodeStep(currentIds: List<Long>, encoderHiddenStates: FloatArray): Long {
        val seqLen = currentIds.size
        val inputIdsShape = longArrayOf(1, seqLen.toLong())
        val inputIdsTensor = OnnxTensorHelper.createLongTensor(ortEnv, currentIds.toLongArray(), inputIdsShape)

        val encoderSeqLen = encoderHiddenStates.size / HIDDEN_DIM
        val encoderHiddenShape = longArrayOf(1, encoderSeqLen.toLong(), HIDDEN_DIM.toLong())
        val encoderHiddenTensor = OnnxTensorHelper.createFloatTensor(ortEnv, encoderHiddenStates, encoderHiddenShape)

        val result = decoderModel.run(mapOf(
            "input_ids" to inputIdsTensor,
            "encoder_hidden_states" to encoderHiddenTensor
        ))

        val logits = OnnxTensorHelper.getLastPositionLogits(result, seqLen - 1)
        val next = OnnxTensorHelper.argmax(logits).toLong()

        inputIdsTensor.close()
        encoderHiddenTensor.close()
        result[0]?.close()

        return next
    }

    fun close() {
        runCatching {
            encoderModel.close()
            decoderModel.close()
        }
    }
}
