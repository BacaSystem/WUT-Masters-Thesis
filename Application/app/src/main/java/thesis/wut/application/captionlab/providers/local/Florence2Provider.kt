package thesis.wut.application.captionlab.providers.local

import ai.onnxruntime.*
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import thesis.wut.application.captionlab.providers.CaptioningProvider
import thesis.wut.application.captionlab.providers.CaptionResult
import thesis.wut.application.captionlab.utils.florence.Florence2Tokenizer
import thesis.wut.application.captionlab.utils.onnx.ImagePreprocessor
import thesis.wut.application.captionlab.utils.onnx.OnnxTensorHelper
import thesis.wut.application.captionlab.utils.florence.config.ModelConfig

class Florence2Provider(private val context: Context) : CaptioningProvider {

    companion object {
        private const val TAG = "Florence2ONNX"
        private const val MODEL_PATH = "models/florence2-base"
    }

    override val id = "florence2_onnx_local"

    private val config: ModelConfig by lazy { 
        ModelConfig.load(context, MODEL_PATH)
    }

    private val ortEnv: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private val sessionOptions: OrtSession.SessionOptions by lazy {
        OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
        }
    }

    private val visionEncoder: OrtSession by lazy { loadModel("$MODEL_PATH/vision_encoder.onnx") }
    private val embedTokens: OrtSession by lazy { loadModel("$MODEL_PATH/embed_tokens.onnx") }
    private val encoderModel: OrtSession by lazy { loadModel("$MODEL_PATH/encoder_model.onnx") }
    private val decoderModel: OrtSession by lazy { loadModel("$MODEL_PATH/decoder_model.onnx") }
    
    private val tokenizer: Florence2Tokenizer by lazy {
        Florence2Tokenizer(
            context = context,
            tokenizerPath = "$MODEL_PATH/tokenizer.json",
            bosTokenId = config.bosTokenId,
            eosTokenId = config.eosTokenId,
            unkTokenId = 3L
        )
    }

    override suspend fun caption(bitmap: Bitmap): CaptionResult {
        try {
            val start = SystemClock.elapsedRealtimeNanos()
            val metrics = mutableMapOf<String, Any?>()

            val preStart = SystemClock.elapsedRealtimeNanos()
            val pixelValues = ImagePreprocessor.preprocess(
                bitmap = bitmap,
                targetSize = config.imageSize,
                mean = config.imageMean,
                std = config.imageStd
            )
            metrics["pre_ms"] = (SystemClock.elapsedRealtimeNanos() - preStart) / 1_000_000.0

            val visionStart = SystemClock.elapsedRealtimeNanos()
            val imageFeatures = encodeImage(pixelValues)
            metrics["vision_enc_ms"] =
                (SystemClock.elapsedRealtimeNanos() - visionStart) / 1_000_000.0

            val promptStart = SystemClock.elapsedRealtimeNanos()
            val promptIds = tokenizer.encode("Describe image")
            val promptEmbeds = embedTokens(promptIds)
            metrics["prompt_ms"] = (SystemClock.elapsedRealtimeNanos() - promptStart) / 1_000_000.0

            val textEncStart = SystemClock.elapsedRealtimeNanos()
            val inputsEmbeds = OnnxTensorHelper.concatenate(imageFeatures, promptEmbeds)
            val seqLen = config.imageSeqLength + promptIds.size
            val attentionMask = LongArray(seqLen) { 1L }
            val encoderHiddenStates = encodeText(inputsEmbeds, attentionMask)
            metrics["text_enc_ms"] =
                (SystemClock.elapsedRealtimeNanos() - textEncStart) / 1_000_000.0

            val decStart = SystemClock.elapsedRealtimeNanos()
            val generatedIds =
                generateText(encoderHiddenStates, attentionMask, maxTokens = config.maxLength * 5)
            metrics["dec_ms"] = (SystemClock.elapsedRealtimeNanos() - decStart) / 1_000_000.0

            val postStart = SystemClock.elapsedRealtimeNanos()
            val generatedText = tokenizer.decode(generatedIds, skipSpecialTokens = true)
            metrics["post_ms"] = (SystemClock.elapsedRealtimeNanos() - postStart) / 1_000_000.0

            metrics["e2e_ms"] = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
            metrics["tokens_generated"] = generatedIds.size


            Log.d("Inference result ", "Caption: $generatedText")

            return CaptionResult(
                text = generatedText,
                extra = metrics
            )

        } catch (error: Exception) {
            Log.e(TAG, "Inference failed", error)

            return CaptionResult(
                text = "Error: ${error.message}",
                extra = mapOf("error" to error.toString())
            )
        }
    }

    private fun loadModel(path: String): OrtSession {
        val pathHash = path.hashCode().toString().replace("-", "n")
        val fileName = path.substringAfterLast('/')
        val cachedFileName = "${pathHash}_${fileName}"
        val outFile = java.io.File(context.cacheDir, cachedFileName)

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
                Log.d(TAG, "Model copied to: ${outFile.absolutePath}")
            } else {
                Log.d(TAG, "Using cached model file: ${outFile.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract model asset: $path", e)
            throw e
        }

        return ortEnv.createSession(outFile.absolutePath, sessionOptions)
    }

    private fun encodeImage(pixelValues: FloatArray): FloatArray {
        val shape = longArrayOf(1, 3, config.imageSize.toLong(), config.imageSize.toLong())
        val tensor = OnnxTensorHelper.createFloatTensor(ortEnv, pixelValues, shape)
        
        val result = visionEncoder.run(mapOf("pixel_values" to tensor))
        val features = OnnxTensorHelper.extractFloatArray(result)
        
        tensor.close()
        result[0]?.close()
        
        return features
    }

    private fun embedTokens(inputIds: List<Long>): FloatArray {
        val shape = longArrayOf(1, inputIds.size.toLong())
        val tensor = OnnxTensorHelper.createLongTensor(ortEnv, inputIds.toLongArray(), shape)
        
        val result = embedTokens.run(mapOf("input_ids" to tensor))
        val embeds = OnnxTensorHelper.extractFloatArray(result)
        
        tensor.close()
        result[0]?.close()
        
        return embeds
    }

    private fun encodeText(inputsEmbeds: FloatArray, attentionMask: LongArray): FloatArray {
        val seqLen = attentionMask.size
        
        val embedsShape = longArrayOf(1, seqLen.toLong(), config.hiddenDim.toLong())
        val embedsTensor = OnnxTensorHelper.createFloatTensor(ortEnv, inputsEmbeds, embedsShape)
        
        val maskShape = longArrayOf(1, seqLen.toLong())
        val maskTensor = OnnxTensorHelper.createLongTensor(ortEnv, attentionMask, maskShape)
        
        val result = encoderModel.run(mapOf(
            "inputs_embeds" to embedsTensor,
            "attention_mask" to maskTensor
        ))
        
        val hiddenStates = OnnxTensorHelper.extractFloatArray(result)
        
        embedsTensor.close()
        maskTensor.close()
        result[0]?.close()
        
        return hiddenStates
    }

    private fun generateText(
        encoderHiddenStates: FloatArray,
        encoderAttentionMask: LongArray,
        maxTokens: Int = 100
    ): List<Long> {
        val generatedIds = mutableListOf<Long>()
        val bosTokenId = tokenizer.getBosTokenId()
        val eosTokenId = tokenizer.getEosTokenId()
        
        var currentIds = listOf(bosTokenId)
        
        for (step in 0 until maxTokens) {
            val nextToken = decodeStep(currentIds, encoderHiddenStates, encoderAttentionMask)
            
            if (nextToken == eosTokenId) {
                break
            }
            
            generatedIds.add(nextToken)
            currentIds = listOf(bosTokenId) + generatedIds
        }
        
        return generatedIds
    }

    private fun decodeStep(
        currentIds: List<Long>,
        encoderHiddenStates: FloatArray,
        encoderAttentionMask: LongArray
    ): Long {
        val decoderEmbeds = embedTokens(currentIds)
        val decoderSeqLen = currentIds.size
        val encoderSeqLen = encoderAttentionMask.size
        
        val embedsShape = longArrayOf(1, decoderSeqLen.toLong(), config.hiddenDim.toLong())
        val embedsTensor = OnnxTensorHelper.createFloatTensor(ortEnv, decoderEmbeds, embedsShape)
        
        val encoderHiddenShape = longArrayOf(1, encoderSeqLen.toLong(), config.hiddenDim.toLong())
        val encoderHiddenTensor = OnnxTensorHelper.createFloatTensor(ortEnv, encoderHiddenStates, encoderHiddenShape)
        
        val encoderMaskShape = longArrayOf(1, encoderSeqLen.toLong())
        val encoderMaskTensor = OnnxTensorHelper.createLongTensor(ortEnv, encoderAttentionMask, encoderMaskShape)
        
        val result = decoderModel.run(mapOf(
            "inputs_embeds" to embedsTensor,
            "encoder_hidden_states" to encoderHiddenTensor,
            "encoder_attention_mask" to encoderMaskTensor
        ))
        
        val logits = OnnxTensorHelper.getLastPositionLogits(result, decoderSeqLen - 1)
        val nextToken = OnnxTensorHelper.argmax(logits).toLong()
        
        embedsTensor.close()
        encoderHiddenTensor.close()
        encoderMaskTensor.close()
        result[0]?.close()
        
        return nextToken
    }

    fun close() {
        runCatching {
            visionEncoder.close()
            embedTokens.close()
            encoderModel.close()
            decoderModel.close()
        }
    }
}
