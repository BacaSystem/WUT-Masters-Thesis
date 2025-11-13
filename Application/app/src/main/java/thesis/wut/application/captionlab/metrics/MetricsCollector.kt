package thesis.wut.application.captionlab.metrics

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.delay
import thesis.wut.application.captionlab.providers.manager.ProviderManager

class MetricsCollector(
    private val context: Context,
    private val providerManager: ProviderManager
) {
    companion object {
        private const val TAG = "MetricsCollector"
    }
    
    suspend fun collectSingle(
        providerId: String,
        bitmap: Bitmap,
        imageId: String? = null,
        runNumber: Int = 0
    ): BenchmarkMetrics {
        val provider = providerManager.getProvider(providerId)
            ?: throw IllegalArgumentException("Provider not found: $providerId")
        
        val memoryMonitor = MemoryMonitor()
        memoryMonitor.start()
        
        val powerMonitor = PowerMonitor(context)
        powerMonitor.start()
        
        val modelSize = if (providerManager.isLocalProvider(providerId)) {
            getModelSize(providerId)
        } else null
        
        val modelName = getModelName(providerId)
        
        val result = try {
            val captionResult = provider.caption(bitmap)

            val energyMwh = powerMonitor.stop()
            memoryMonitor.stop()
            val memoryIncreaseMb = memoryMonitor.getMemoryIncreaseMb()

            val costEstimate = if (providerManager.isCloudProvider(providerId)) {
                estimateCost(providerId, captionResult.extra)
            } else null
            
            BenchmarkMetrics.fromCaptionResult(
                providerId = providerId,
                captionText = captionResult.text,
                extra = captionResult.extra.toMutableMap().apply {
                    if (!containsKey("model") && modelName != null) {
                        put("model", modelName)
                    }
                },
                imageId = imageId,
                runNumber = runNumber,
                ramPeakMb = memoryIncreaseMb,
                modelSizeMb = modelSize
            ).copy(
                energyMwh = energyMwh,
                costUsd = costEstimate,
                ramPeakMb = memoryIncreaseMb
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during caption generation: ${e.message}", e)

            memoryMonitor.stop()
            powerMonitor.stop()
            
            BenchmarkMetrics(
                providerId = providerId,
                imageId = imageId,
                runNumber = runNumber,
                captionText = "",
                e2eMs = 0.0,
                error = e.message ?: "Unknown error",
                success = false
            )
        }
        
        return result
    }
    
    suspend fun collectMultiple(
        providerId: String,
        bitmap: Bitmap,
        numRuns: Int = 10,
        warmupRuns: Int = 3,
        imageId: String? = null
    ): AggregatedMetrics {
        require(numRuns > 0) { "numRuns must be positive" }
        require(warmupRuns >= 0) { "warmupRuns must be non-negative" }

        Log.i(TAG, "Starting benchmark: $providerId, warmup=$warmupRuns, runs=$numRuns")

        val provider = providerManager.getProvider(providerId)
            ?: throw IllegalArgumentException("Provider not found: $providerId")
        
        if (warmupRuns > 0) {
            Log.d(TAG, "Performing $warmupRuns warm-up runs...")
            repeat(warmupRuns) { i ->
                try {
                    provider.caption(bitmap)
                    Log.v(TAG, "Warm-up run ${i + 1}/$warmupRuns completed")
                } catch (e: Exception) {
                    Log.w(TAG, "Warm-up run ${i + 1} failed: ${e.message}")
                }
            }
            
            delay(500)
        }
        
        Log.d(TAG, "Performing $numRuns measurement runs...")
        val metrics = mutableListOf<BenchmarkMetrics>()
        
        repeat(numRuns) { i ->
            val metric = collectSingle(
                providerId = providerId,
                bitmap = bitmap,
                imageId = imageId,
                runNumber = i + 1
            )
            metrics.add(metric)
            
            // Brief pause between runs to avoid thermal throttling
            if (i < numRuns - 1) {
                delay(100)
            }
        }
        
        val aggregated = AggregatedMetrics.fromRuns(
            providerId = providerId,
            runs = metrics,
            imageId = imageId
        )
        
        return aggregated
    }
    
    suspend fun collectBatch(
        providerId: String,
        images: List<Pair<Bitmap, String?>>,
        runsPerImage: Int = 5,
        warmupRuns: Int = 3
    ): List<AggregatedMetrics> {
        Log.i(TAG, "Starting batch benchmark: $providerId, ${images.size} images")
        
        return images.mapIndexed { index, (bitmap, imageId) ->
            Log.d(TAG, "Processing image ${index + 1}/${images.size}: $imageId")
            
            collectMultiple(
                providerId = providerId,
                bitmap = bitmap,
                numRuns = runsPerImage,
                warmupRuns = if (index == 0) warmupRuns else 0, // Only warm-up on first image
                imageId = imageId
            )
        }
    }
    
    suspend fun compareProviders(
        providerIds: List<String>,
        bitmap: Bitmap,
        numRuns: Int = 10,
        warmupRuns: Int = 3,
        imageId: String? = null
    ): Map<String, AggregatedMetrics> {
        Log.i(TAG, "Comparing ${providerIds.size} providers on image: $imageId")
        
        return providerIds.associateWith { providerId ->
            collectMultiple(
                providerId = providerId,
                bitmap = bitmap,
                numRuns = numRuns,
                warmupRuns = warmupRuns,
                imageId = imageId
            )
        }
    }

    private fun getModelSize(providerId: String): Double? {
        return try {
            val modelPath = when (providerId) {
                "florence2_onnx_local" -> "models/florence2-base"
                "vit_gpt2_onnx_local" -> "models/vit-gpt2"
                "blip_onnx_local" -> "models/blip"
                else -> return null
            }
            
            // List all files in the model directory
            var totalSize = 0L
            try {
                val files = context.assets.list(modelPath) ?: emptyArray()
                files.forEach { filename ->
                    try {
                        context.assets.open("$modelPath/$filename").use { stream ->
                            totalSize += stream.available()
                        }
                    } catch (e: Exception) {
                        Log.v(TAG, "Could not read file $modelPath/$filename: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not list directory $modelPath: ${e.message}")
            }
            
            if (totalSize > 0) {
                val sizeMb = totalSize / (1024.0 * 1024.0)
                Log.d(TAG, "Model size for $providerId: ${"%.2f".format(sizeMb)} MB")
                sizeMb
            } else {
                Log.w(TAG, "No model files found for $providerId in $modelPath")
                null
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine model size for $providerId: ${e.message}")
            null
        }
    }
    
    private fun getModelName(providerId: String): String? {
        return when (providerId) {
            ProviderManager.FLORENCE2_LOCAL -> "Florence-2-base"
            ProviderManager.VITGPT2_LOCAL -> "ViT-GPT2"
            ProviderManager.BLIP_LOCAL -> "BLIP-base"
            ProviderManager.OPENAI_CLOUD -> "gpt-4o-mini"
            ProviderManager.AZURE_VISION -> "Azure Computer Vision"
            ProviderManager.GEMINI_CLOUD -> "Gemini 2.5 Flash Lite"
            else -> null
        }
    }
    
    private fun estimateCost(providerId: String, extra: Map<String, Any?>): Double? {
        return when (providerId) {
            "openai_cloud" -> {
                val promptTokens = (extra["prompt_tokens"] as? Number)?.toInt() ?: 0
                val completionTokens = (extra["completion_tokens"] as? Number)?.toInt() ?: 0
                (promptTokens / 1_000_000.0 * 0.15) + (completionTokens / 1_000_000.0 * 0.6)
            }
            "azure_vision_cloud" -> {
                1.5 / 1000.0 // $1.5 per 1000 images
            }
            "gemini_cloud" -> {
                val promptTokens = (extra["prompt_tokens"] as? Number)?.toInt() ?: 0
                val completionTokens = (extra["completion_tokens"] as? Number)?.toInt() ?: 0
                (promptTokens / 1_000_000.0 * 0.1) + (completionTokens / 1_000_000.0 * 0.4)
            }
            else -> null
        }
    }
}
