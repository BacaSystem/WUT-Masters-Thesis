package thesis.wut.application.captionlab.providers.local

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import thesis.wut.application.captionlab.providers.CaptioningProvider
import thesis.wut.application.captionlab.providers.CaptionResult
import thesis.wut.application.captionlab.utils.florence.Florence2ImageProcessor
import thesis.wut.application.captionlab.utils.florence.Florence2Tokenizer
import java.nio.FloatBuffer

/**
 * Florence-2 ONNX provider for image captioning
 * 
 * Model: onnx-community/Florence-2-base-ft
 * Architecture: Encoder-Decoder transformer with vision encoder
 * 
 * Expected model files in assets/models/florence2/:
 * - vision_encoder.onnx (or encoder_model.onnx)
 * - decoder_model_merged.onnx
 * - tokenizer.json
 * 
 * @param appContext Android application context for asset loading
 */
class Florence2OnnxProvider(private val appContext: Context) : CaptioningProvider {
    
    override val id: String = "florence2_onnx_local"
    
    companion object {
        private const val TAG = "Florence2ONNX"
        
        // Model configuration
        private const val IMAGE_SIZE = 768 // Florence-2 uses 768x768
        private const val MAX_NEW_TOKENS = 100
        private const val NUM_BEAMS = 3
        
        // Special token IDs (standard for Florence-2)
        private const val PAD_TOKEN_ID = 0
        private const val BOS_TOKEN_ID = 1
        private const val EOS_TOKEN_ID = 2
        
        // Task prompts
        private const val TASK_CAPTION = "<CAPTION>"
        private const val TASK_DETAILED_CAPTION = "<DETAILED_CAPTION>"
        private const val TASK_MORE_DETAILED_CAPTION = "<MORE_DETAILED_CAPTION>"
    }
    
    // ONNX Runtime
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    
    // Store encoder result to close later
    private var encoderResult: OrtSession.Result? = null
    
    // Sessions - lazy loaded
    private val visionEncoderSession: OrtSession by lazy {
        val session = loadModelFromAssets("models/florence2/vision_encoder.onnx")
        
        Log.d(TAG, "Vision encoder outputs (${session.outputNames.size}):")
        session.outputNames.forEach { name ->
            Log.d(TAG, "  - $name")
        }
        
        session
    }
    
    private val embedTokensSession: OrtSession by lazy {
        loadModelFromAssets("models/florence2/embed_tokens.onnx")
    }
    
    private val decoderSession: OrtSession by lazy {
        val session = loadModelFromAssets("models/florence2/decoder_with_past_model.onnx")
        
        // Log expected inputs for debugging
        Log.d(TAG, "Decoder model inputs (${session.inputNames.size}):")
        session.inputNames.forEach { name ->
            Log.d(TAG, "  - $name")
        }
        
        Log.d(TAG, "Decoder model outputs (${session.outputNames.size}):")
        session.outputNames.forEach { name ->
            Log.d(TAG, "  - $name")
        }
        
        session
    }
    
    // Embedding model for converting token IDs to embeddings
    private val embedSession: OrtSession by lazy {
        loadModelFromAssets("models/florence2/embed_tokens.onnx")
    }
    
    // Processors
    private val imageProcessor = Florence2ImageProcessor(IMAGE_SIZE)
    private val tokenizer = Florence2Tokenizer(appContext)
    
    override suspend fun caption(bitmap: Bitmap): CaptionResult {
        val startTime = SystemClock.elapsedRealtimeNanos()
        
        try {
            // Phase 1: Preprocessing
            val preStart = SystemClock.elapsedRealtimeNanos()
            
            // Process image to pixel values [1, 3, 768, 768]
            val pixelValues = imageProcessor.preprocess(bitmap)
            
            // Prepare prompt
            val prompt = TASK_DETAILED_CAPTION
            val inputIds = tokenizer.encode(prompt)
            
            Log.d(TAG, "Prompt: '$prompt'")
            Log.d(TAG, "Input IDs (${inputIds.size}): ${inputIds.joinToString()}")
            
            val preEnd = SystemClock.elapsedRealtimeNanos()
            
            // Phase 2: Vision Encoder
            val encStart = SystemClock.elapsedRealtimeNanos()
            val encoderHiddenStates = runVisionEncoder(pixelValues)
            val encEnd = SystemClock.elapsedRealtimeNanos()
            
            // Debug: check encoder output
            val encData = encoderHiddenStates.value as Array<Array<FloatArray>>
            val encShape = (encoderHiddenStates.info as ai.onnxruntime.TensorInfo).shape
            val sampleValues = encData[0][0].take(5).map { String.format("%.3f", it) }
            Log.d(TAG, "Encoder output shape: ${encShape.contentToString()}")
            Log.d(TAG, "Encoder sample values: $sampleValues")
            val meanVal = encData[0][0].average()
            val maxVal = encData[0][0].maxOrNull() ?: 0f
            val minVal = encData[0][0].minOrNull() ?: 0f
            Log.d(TAG, "Encoder stats: mean=${"%.3f".format(meanVal)}, min=${"%.3f".format(minVal)}, max=${"%.3f".format(maxVal)}")
            
            // Phase 3: Decoder (autoregressive generation)
            // Note: runDecoderAutoregressive will handle padding internally
            val decStart = SystemClock.elapsedRealtimeNanos()
            val generatedIds = runDecoderAutoregressive(
                inputIds = inputIds,
                encoderHiddenStates = encoderHiddenStates,
                maxNewTokens = 50
            )
            val decEnd = SystemClock.elapsedRealtimeNanos()
            
            // Phase 4: Post-processing
            val postStart = SystemClock.elapsedRealtimeNanos()
            val decodedText = tokenizer.decode(generatedIds)
            val cleanedText = postProcessCaption(decodedText, prompt)
            val postEnd = SystemClock.elapsedRealtimeNanos()
            
            // Calculate metrics
            val preMs = (preEnd - preStart) / 1_000_000.0
            val encMs = (encEnd - encStart) / 1_000_000.0
            val decMs = (decEnd - decStart) / 1_000_000.0
            val postMs = (postEnd - postStart) / 1_000_000.0
            val e2eMs = (postEnd - startTime) / 1_000_000.0
            
            Log.i(TAG, "Caption generated: $cleanedText")
            Log.i(TAG, "Timing - Pre: ${preMs}ms, Enc: ${encMs}ms, Dec: ${decMs}ms, Post: ${postMs}ms, E2E: ${e2eMs}ms")
            
            return CaptionResult(
                text = cleanedText.ifBlank { "(empty caption)" },
                extra = mapOf(
                    "pre_ms" to preMs,
                    "enc_ms" to encMs,
                    "dec_ms" to decMs,
                    "post_ms" to postMs,
                    "e2e_ms" to e2eMs,
                    "tokens_generated" to generatedIds.size
                )
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during captioning", e)
            return CaptionResult(
                text = "Error: ${e.message}",
                extra = mapOf("error" to (e.message ?: "Unknown error"))
            )
        }
    }
    
    /**
     * Run vision encoder on preprocessed image
     * Returns: encoder hidden states (last_hidden_state)
     */
    private fun runVisionEncoder(pixelValues: FloatBuffer): OnnxTensor {
        val inputs = mapOf(
            "pixel_values" to OnnxTensor.createTensor(
                env,
                pixelValues,
                longArrayOf(1, 3, IMAGE_SIZE.toLong(), IMAGE_SIZE.toLong())
            )
        )
        
        // Close previous encoder result if exists
        encoderResult?.close()
        
        val outputs = visionEncoderSession.run(inputs)
        inputs.values.forEach { it.close() }
        
        // Store result for later cleanup
        encoderResult = outputs
        
        // Extract last_hidden_state (or first output)
        val encoderHiddenStates = outputs.get(0) as? OnnxTensor
            ?: throw RuntimeException("Encoder output is not an OnnxTensor")
        
        return encoderHiddenStates
    }
    
    /**
     * Run decoder autoregressively using 3-model pipeline:
     * 1. token_ids -> embed_tokens.onnx -> inputs_embeds
     * 2. inputs_embeds -> decoder_with_past_model.onnx -> logits + new_past_key_values
     * 3. logits -> next_token
     */
    private fun runDecoderAutoregressive(
        inputIds: IntArray,
        encoderHiddenStates: OnnxTensor,
        maxNewTokens: Int
    ): IntArray {
        val generatedTokens = mutableListOf<Int>()
        var currentInputIds = inputIds
        var shouldStop = false
        
        // Initialize past_key_values as null (first iteration)
        var pastKeyValues: Map<String, OnnxTensor>? = null
        
        // Create encoder attention mask (all ones, length = encoder seq length)
        val encoderSeqLen = (encoderHiddenStates.info as ai.onnxruntime.TensorInfo).shape[1].toInt()
        val encoderAttentionMask = createAttentionMask(encoderSeqLen)
        
        for (step in 0 until maxNewTokens) {
            if (shouldStop) break
            
            try {
                // Pad current input to 16 tokens (model requirement)
                val paddedInputIds = if (currentInputIds.size < 16) {
                    currentInputIds + IntArray(16 - currentInputIds.size) { 0 } // PAD = 0
                } else {
                    currentInputIds
                }
                
                // Step 1: Convert token IDs to embeddings using embed_tokens.onnx
                val inputsEmbeds = runEmbedTokens(paddedInputIds)
                
                // Step 2: Run decoder with past
                val (logits, newPastKeyValues) = runDecoderWithPast(
                    inputsEmbeds,
                    encoderHiddenStates,
                    encoderAttentionMask,
                    pastKeyValues
                )
                
                // Step 3: Get next token (greedy decoding)
                // Take logits only for the actual (non-padded) position
                // In first iteration: we have N real tokens, want logits for last real token (pos N-1)
                // In subsequent: we have 1 real token at position 0
                val logitsArray = logits.value as Array<Array<FloatArray>>
                val actualPosition = if (step == 0) {
                    // First iteration: last position of real (non-padded) tokens
                    currentInputIds.size - 1
                } else {
                    // Subsequent iterations: first position (the only real token)
                    0
                }
                val nextTokenLogits = logitsArray[0][actualPosition]
                
                // Apply repetition penalty to avoid loops
                // Penalize all previously generated tokens
                if (generatedTokens.isNotEmpty()) {
                    val penalty = 1.05f // Very low penalty to see more of model behavior
                    generatedTokens.forEach { prevToken ->
                        if (prevToken >= 0 && prevToken < nextTokenLogits.size) {
                            nextTokenLogits[prevToken] = nextTokenLogits[prevToken] / penalty
                        }
                    }
                }
                
                val nextTokenId = argmax(nextTokenLogits)
                
                // Debug: check decoder KV shape growth
                if (step == 0 || step == 1 || step == 2) {
                    val decoderKeyShape = (newPastKeyValues["past_key_values.0.decoder.key"]?.info as? ai.onnxruntime.TensorInfo)?.shape
                    val top5 = nextTokenLogits.withIndex()
                        .sortedByDescending { it.value }
                        .take(5)
                        .map { "${it.index}:${String.format("%.3f", it.value)}" }
                        .joinToString(", ")
                    Log.d(TAG, "Step $step: decoder KV shape = ${decoderKeyShape?.contentToString()}")
                    Log.d(TAG, "Step $step: top5 tokens = $top5")
                }
                
                Log.d(TAG, "Step $step: nextTokenId=$nextTokenId (pos=$actualPosition, inputSize=${currentInputIds.size})")
                
                // Clean up old decoder KV before replacing (encoder KV are reused)
                if (pastKeyValues != null && step > 0) {
                    // Close only decoder KV from previous iteration
                    for (i in 0 until 6) {
                        pastKeyValues["past_key_values.$i.decoder.key"]?.close()
                        pastKeyValues["past_key_values.$i.decoder.value"]?.close()
                    }
                }
                pastKeyValues = newPastKeyValues
                
                // Clean up current iteration tensors
                inputsEmbeds.close()
                logits.close()
                
                // Check for EOS
                if (nextTokenId == EOS_TOKEN_ID) {
                    Log.d(TAG, "EOS reached at step $step")
                    shouldStop = true
                    continue
                }
                
                // Add to generated tokens
                generatedTokens.add(nextTokenId)
                
                // Update input for next iteration (only the new token)
                currentInputIds = intArrayOf(nextTokenId)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error at decoder step $step", e)
                shouldStop = true
            }
        }
        
        // Clean up all KV cache tensors
        pastKeyValues?.values?.forEach { it.close() }
        encoderAttentionMask.close()
        encoderResult?.close()
        encoderResult = null
        
        return generatedTokens.toIntArray()
    }
    
    /**
     * Convert token IDs to embeddings using embed_tokens.onnx
     */
    private fun runEmbedTokens(tokenIds: IntArray): OnnxTensor {
        val inputShape = longArrayOf(1, tokenIds.size.toLong())
        val longIds = tokenIds.map { it.toLong() }.toLongArray()
        val inputBuffer = java.nio.LongBuffer.wrap(longIds)
        
        val inputTensor = OnnxTensor.createTensor(env, inputBuffer, inputShape)
        
        try {
            val inputs = mapOf("input_ids" to inputTensor)
            val result = embedTokensSession.run(inputs)
            
            // Return inputs_embeds tensor
            return result.get(0) as? OnnxTensor 
                ?: throw IllegalStateException("embed_tokens output is not OnnxTensor")
        } finally {
            inputTensor.close()
        }
    }
    
    /**
     * Run decoder_with_past_model.onnx
     * Returns: (logits, new_past_key_values)
     * 
     * Based on error, model expects these inputs:
     * - encoder_attention_mask
     * - inputs_embeds
     * - past_key_values.{0-5}.{encoder|decoder}.{key|value} (24 tensors)
     */
    private fun runDecoderWithPast(
        inputsEmbeds: OnnxTensor,
        encoderHiddenStates: OnnxTensor,
        encoderAttentionMask: OnnxTensor,
        pastKeyValues: Map<String, OnnxTensor>?
    ): Pair<OnnxTensor, Map<String, OnnxTensor>> {
        
        val inputs = mutableMapOf<String, OnnxTensor>()
        
        // Required inputs (verified from error message)
        inputs["inputs_embeds"] = inputsEmbeds
        inputs["encoder_attention_mask"] = encoderAttentionMask
        
        // past_key_values: always required (24 tensors for 6 layers)
        if (pastKeyValues != null && pastKeyValues.size == 24) {
            // Subsequent iterations: reuse cached past_key_values
            Log.d(TAG, "Reusing cached past_key_values (${pastKeyValues.size} tensors)")
            inputs.putAll(pastKeyValues)
        } else {
            // First iteration: initialize empty past_key_values
            // Encoder KV: compute from encoder_hidden_states (K = Q = encoder output projected)
            // Decoder KV: empty (no decoder tokens generated yet)
            
            Log.d(TAG, "First iteration - initializing past_key_values")
            
            val batchSize = 1L
            val numHeads = 12L
            val encoderSeqLen = (encoderHiddenStates.info as ai.onnxruntime.TensorInfo).shape[1]
            val decoderSeqLen = 0L
            val headDim = 64L
            
            // Extract encoder hidden states data
            val encoderData = encoderHiddenStates.value as Array<Array<FloatArray>>
            val hiddenDim = 768 // Florence-2 hidden dimension
            
            // Create encoder past_key_values by projecting encoder_hidden_states
            // Simple approach: reshape encoder_hidden_states [seq, 768] -> [seq, 12, 64] for K and V
            for (i in 0 until 6) {
                val encoderKVShape = longArrayOf(batchSize, numHeads, encoderSeqLen, headDim)
                val encoderKVSize = (batchSize * numHeads * encoderSeqLen * headDim).toInt()
                
                // Simple projection: split 768-dim hidden states across 12 heads
                val encoderKeyData = FloatArray(encoderKVSize)
                val encoderValueData = FloatArray(encoderKVSize)
                
                for (seqIdx in 0 until encoderSeqLen.toInt()) {
                    for (headIdx in 0 until numHeads.toInt()) {
                        for (dimIdx in 0 until headDim.toInt()) {
                            val hiddenIdx = headIdx * headDim.toInt() + dimIdx
                            val value = if (hiddenIdx < hiddenDim) {
                                encoderData[0][seqIdx][hiddenIdx]
                            } else {
                                0f
                            }
                            
                            val kvIdx = seqIdx * (numHeads * headDim).toInt() + 
                                       headIdx * headDim.toInt() + dimIdx
                            
                            encoderKeyData[kvIdx] = value
                            encoderValueData[kvIdx] = value
                        }
                    }
                }
                
                inputs["past_key_values.$i.encoder.key"] = OnnxTensor.createTensor(
                    env, java.nio.FloatBuffer.wrap(encoderKeyData), encoderKVShape
                )
                inputs["past_key_values.$i.encoder.value"] = OnnxTensor.createTensor(
                    env, java.nio.FloatBuffer.wrap(encoderValueData), encoderKVShape
                )
                
                // Decoder past_key_values: empty
                val decoderKVShape = longArrayOf(batchSize, numHeads, decoderSeqLen, headDim)
                val decoderKVData = FloatArray(0)
                
                inputs["past_key_values.$i.decoder.key"] = OnnxTensor.createTensor(
                    env, java.nio.FloatBuffer.wrap(decoderKVData), decoderKVShape
                )
                inputs["past_key_values.$i.decoder.value"] = OnnxTensor.createTensor(
                    env, java.nio.FloatBuffer.wrap(decoderKVData.clone()), decoderKVShape
                )
            }
        }
        
        Log.d(TAG, "Running decoder with ${inputs.size} inputs")
        
        try {
            val result = decoderSession.run(inputs)
            
            // Extract logits (first output)
            val logits = result.get(0) as? OnnxTensor
                ?: throw IllegalStateException("Decoder logits output is not OnnxTensor")
            
            // Extract new past_key_values
            // Model returns: logits + 12 present decoder KV (no encoder KV - they're constant)
            // Outputs: present.{0-5}.decoder.{key,value}
            val newPastKeyValues = mutableMapOf<String, OnnxTensor>()
            
            // Keep encoder KV from input (they don't change)
            for (i in 0 until 6) {
                newPastKeyValues["past_key_values.$i.encoder.key"] = 
                    inputs["past_key_values.$i.encoder.key"]!!
                newPastKeyValues["past_key_values.$i.encoder.value"] = 
                    inputs["past_key_values.$i.encoder.value"]!!
            }
            
            // Extract new decoder KV from outputs (outputs 1-12)
            for (i in 0 until 6) {
                val baseIdx = 1 + i * 2 // present.{i}.decoder.{key,value}
                
                newPastKeyValues["past_key_values.$i.decoder.key"] = result.get(baseIdx) as OnnxTensor
                newPastKeyValues["past_key_values.$i.decoder.value"] = result.get(baseIdx + 1) as OnnxTensor
            }
            
            return Pair(logits, newPastKeyValues)
            
        } finally {
            // Clean up temporary decoder KV tensors from first iteration only
            // Don't close encoder KV - we're reusing them in newPastKeyValues
            if (pastKeyValues == null || pastKeyValues.size != 24) {
                for (i in 0 until 6) {
                    inputs["past_key_values.$i.decoder.key"]?.close()
                    inputs["past_key_values.$i.decoder.value"]?.close()
                }
            }
        }
    }
    
    /**
     * Create attention mask (all ones)
     */
    private fun createAttentionMask(seqLen: Int): OnnxTensor {
        val mask = LongArray(seqLen) { 1L }
        val buffer = java.nio.LongBuffer.wrap(mask)
        return OnnxTensor.createTensor(
            env,
            buffer,
            longArrayOf(1, seqLen.toLong())
        )
    }
    
    /**
     * Find argmax of logits
     */
    private fun argmax(logits: FloatArray): Int {
        var maxIdx = 0
        var maxVal = Float.NEGATIVE_INFINITY
        
        logits.forEachIndexed { idx, value ->
            if (value > maxVal) {
                maxVal = value
                maxIdx = idx
            }
        }
        
        return maxIdx
    }
    
    /**
     * Post-process generated caption
     */
    private fun postProcessCaption(text: String, task: String): String {
        // Remove task prefix if present
        var cleaned = text.replace(task, "").trim()
        
        // Remove special tokens
        cleaned = cleaned
            .replace("<s>", "")
            .replace("</s>", "")
            .replace("<pad>", "")
            .trim()
        
        // Capitalize first letter
        if (cleaned.isNotEmpty()) {
            cleaned = cleaned[0].uppercaseChar() + cleaned.substring(1)
        }
        
        // Ensure ends with period
        if (cleaned.isNotEmpty() && !cleaned.endsWith(".") && !cleaned.endsWith("!") && !cleaned.endsWith("?")) {
            cleaned += "."
        }
        
        return cleaned
    }
    
    /**
     * Load ONNX model from assets
     * 
     * Strategy:
     * 1. Try to load directly from assets (if not compressed)
     * 2. If compressed, copy to cache directory and load from there
     * 3. Fallback: load into byte array (uses more memory)
     */
    private fun loadModelFromAssets(assetPath: String): OrtSession {
        try {
            Log.i(TAG, "Loading model: $assetPath")
            
            // First, try to copy to cache for better performance with large models
            val cachedFile = copyAssetToCache(assetPath)
            
            if (cachedFile != null && cachedFile.exists()) {
                Log.i(TAG, "Loading from cache: ${cachedFile.absolutePath}")
                val sessionOptions = OrtSession.SessionOptions()
                sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                
                val session = env.createSession(cachedFile.absolutePath, sessionOptions)
                Log.i(TAG, "Model session created successfully from cache")
                logSessionInfo(session)
                return session
            }
            
            // Fallback: Load into memory (for small models or if cache fails)
            Log.i(TAG, "Loading model into memory (fallback)")
            val inputStream = appContext.assets.open(assetPath)
            val modelBytes = inputStream.readBytes()
            inputStream.close()
            
            Log.i(TAG, "Model loaded into memory: ${modelBytes.size / (1024 * 1024)} MB")
            
            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            
            val session = env.createSession(modelBytes, sessionOptions)
            
            Log.i(TAG, "Model session created successfully from memory")
            logSessionInfo(session)
            
            return session
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: $assetPath", e)
            throw RuntimeException("Failed to load ONNX model: $assetPath", e)
        }
    }
    
    /**
     * Copy asset file to cache directory
     * This allows loading large models without holding them in memory
     */
    private fun copyAssetToCache(assetPath: String): java.io.File? {
        return try {
            val fileName = assetPath.substringAfterLast('/')
            val cacheFile = java.io.File(appContext.cacheDir, fileName)
            
            // Check if already cached
            if (cacheFile.exists()) {
                Log.d(TAG, "Model already cached: ${cacheFile.absolutePath}")
                return cacheFile
            }
            
            Log.i(TAG, "Copying model to cache: $fileName")
            
            val inputStream = appContext.assets.open(assetPath)
            val outputStream = java.io.FileOutputStream(cacheFile)
            
            inputStream.copyTo(outputStream)
            
            inputStream.close()
            outputStream.close()
            
            Log.i(TAG, "Model copied to cache: ${cacheFile.absolutePath} (${cacheFile.length() / (1024 * 1024)} MB)")
            
            cacheFile
        } catch (e: Exception) {
            Log.w(TAG, "Failed to copy to cache, will use memory fallback", e)
            null
        }
    }
    
    /**
     * Log session input/output information for debugging
     */
    private fun logSessionInfo(session: OrtSession) {
        Log.d(TAG, "=== Session Info ===")
        
        Log.d(TAG, "Inputs:")
        session.inputNames.forEach { name ->
            val info = session.inputInfo[name]
            Log.d(TAG, "  - $name: ${info?.info}")
        }
        
        Log.d(TAG, "Outputs:")
        session.outputNames.forEach { name ->
            val info = session.outputInfo[name]
            Log.d(TAG, "  - $name: ${info?.info}")
        }
    }
    
    /**
     * Clean up resources
     */
    fun close() {
        try {
            encoderResult?.close()
            visionEncoderSession.close()
            decoderSession.close()
            env.close()
            Log.i(TAG, "Resources cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing resources", e)
        }
    }
}
