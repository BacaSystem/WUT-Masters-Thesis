package thesis.wut.application.captionlab.metrics

data class BenchmarkMetrics(
    val providerId: String,
    val imageId: String? = null,
    val runNumber: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val e2eMs: Double,
    val inferenceMs: Double? = null,
    val ramPeakMb: Double? = null,
    val energyMwh: Double? = null,
    val costUsd: Double? = null,
    val totalTokens: Int? = null,
    val captionText: String,
    val modelName: String? = null,
    val modelSizeMb: Double? = null,
    val success: Boolean = true,
    val error: String? = null
) {
    val isLocal: Boolean
        get() = inferenceMs != null && costUsd == null
    
    val isCloud: Boolean
        get() = costUsd != null
    
    val throughput: Double
        get() = if (e2eMs > 0) 1000.0 / e2eMs else 0.0
    
    companion object {
        fun fromCaptionResult(
            providerId: String,
            captionText: String,
            extra: Map<String, Any?>,
            imageId: String? = null,
            runNumber: Int = 0,
            ramPeakMb: Double? = null,
            modelSizeMb: Double? = null
        ): BenchmarkMetrics {
            // Extract inference time
            // - Local models: sum of enc_ms + dec_ms (or vision_enc_ms + text_enc_ms + dec_ms for Florence-2)
            // - Cloud models: http_ms
            val inferenceMs = when {
                // Cloud: use http_ms directly
                extra["http_ms"] != null -> extra["http_ms"] as? Double
                
                // Florence-2: sum vision_enc_ms + text_enc_ms + dec_ms
                extra["vision_enc_ms"] != null -> {
                    val visionEnc = (extra["vision_enc_ms"] as? Double) ?: 0.0
                    val textEnc = (extra["text_enc_ms"] as? Double) ?: 0.0
                    val dec = (extra["dec_ms"] as? Double) ?: 0.0
                    visionEnc + textEnc + dec
                }
                
                // BLIP/ViT-GPT2: sum enc_ms + dec_ms
                extra["enc_ms"] != null -> {
                    val enc = (extra["enc_ms"] as? Double) ?: 0.0
                    val dec = (extra["dec_ms"] as? Double) ?: 0.0
                    enc + dec
                }
                
                else -> null
            }
            
            // Extract e2e time (required)
            val e2eMs = (extra["e2e_ms"] as? Double) ?: 0.0
            
            // Extract token information
            val totalTokens = (extra["total_tokens"] as? Number)?.toInt()
                ?: (extra["tokens_generated"] as? Number)?.toInt()
            
            // Extract cost (cloud only)
            val costUsd = extra["cost_usd"] as? Double
            
            return BenchmarkMetrics(
                providerId = providerId,
                imageId = imageId,
                runNumber = runNumber,
                timestamp = System.currentTimeMillis(),
                captionText = captionText,
                
                // Performance
                e2eMs = e2eMs,
                inferenceMs = inferenceMs,
                
                // Resources
                ramPeakMb = ramPeakMb,
                energyMwh = null, // TODO: Implement energy measurement if needed
                
                // Cost
                costUsd = costUsd,
                totalTokens = totalTokens,
                
                // Model info
                modelName = extra["model"] as? String,
                modelSizeMb = modelSizeMb,
                
                // Status
                success = true,
                error = null
            )
        }
    }
}
