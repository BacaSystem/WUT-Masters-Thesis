package thesis.wut.application.captionlab.providers.local

import ai.onnxruntime.*
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import thesis.wut.application.captionlab.providers.CaptioningProvider
import thesis.wut.application.captionlab.providers.CaptionResult
import thesis.wut.application.captionlab.utils.gpt2.GPT2Tokenizer
import thesis.wut.application.captionlab.utils.onnx.ImagePreprocessor
import thesis.wut.application.captionlab.utils.onnx.OnnxTensorHelper
import java.io.File

class ViTGPT2Provider(private val context: Context) : CaptioningProvider {

    companion object {
        private const val TAG = "ViTGPT2Provider"
        private const val MODEL_PATH = "models/vit-gpt2"
        
        private const val IMAGE_SIZE = 224
        private const val HIDDEN_DIM = 768
        private const val MAX_LENGTH = 20
        private const val BOS_TOKEN_ID = 50256L
        private const val EOS_TOKEN_ID = 50256L
        
        private val IMAGE_MEAN = floatArrayOf(0.5f, 0.5f, 0.5f)
        private val IMAGE_STD = floatArrayOf(0.5f, 0.5f, 0.5f)
    }

    override val id = "vit_gpt2_onnx_local"

    private val ortEnv: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private val sessionOptions: OrtSession.SessionOptions by lazy {
        OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
        }
    }

    private val encoderModel: OrtSession by lazy { loadModel("$MODEL_PATH/encoder_model.onnx") }
    private val decoderModel: OrtSession by lazy { loadModel("$MODEL_PATH/decoder_model_simple.onnx") }
    
    private val tokenizer: GPT2Tokenizer by lazy {
        GPT2Tokenizer(context, MODEL_PATH, BOS_TOKEN_ID, EOS_TOKEN_ID)
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
        val generatedText = tokenizer.decode(generatedIds, skipSpecialTokens = true)
        metrics["post_ms"] = (SystemClock.elapsedRealtimeNanos() - postStart) / 1_000_000.0
        
        metrics["e2e_ms"] = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
        metrics["tokens_generated"] = generatedIds.size
        
        CaptionResult(generatedText, metrics)
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
        val hiddenStates = OnnxTensorHelper.extractFloatArray(result)
        
        tensor.close()
        result[0]?.close()
        
        return hiddenStates
    }

    private fun generateText(encoderHiddenStates: FloatArray): List<Long> {
        val generatedIds = mutableListOf<Long>()
        var currentIds = listOf(BOS_TOKEN_ID)
        
        for (step in 0 until MAX_LENGTH) {
            val nextToken = decodeStep(currentIds, encoderHiddenStates)
            if (nextToken == EOS_TOKEN_ID) break
            generatedIds.add(nextToken)
            currentIds = currentIds + nextToken
        }
        
        return generatedIds
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
        val nextToken = OnnxTensorHelper.argmax(logits).toLong()
        
        inputIdsTensor.close()
        encoderHiddenTensor.close()
        result[0]?.close()
        
        return nextToken
    }

    fun close() {
        runCatching {
            encoderModel.close()
            decoderModel.close()
        }
    }
}
