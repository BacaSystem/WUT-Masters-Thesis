package thesis.wut.application.captionlab.benchmark

import android.graphics.Bitmap
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BenchmarkConfig(
    val providerIds: List<String>,
    val includeLocal: Boolean = true,
    val includeCloud: Boolean = true,
    val numRuns: Int = 10,
    val warmupRuns: Int = 3,
    val runsPerImage: Int = 5,
    val timeoutMs: Long = 30_000,
    val cooldownMs: Long = 100,
    val datasetName: String? = null,
    val maxImages: Int = 100,
    val autoExport: Boolean = true,
    val exportFormat: ExportFormat = ExportFormat.BOTH,
    val outputDirectory: String? = null,
    val monitorMemory: Boolean = true,
    val monitorEnergy: Boolean = true,
    val continueOnError: Boolean = true,
    val maxConsecutiveErrors: Int = 3,
    val deviceModel: String? = null,
    val androidVersion: String? = null,
    val testEnvironment: String? = null,
    val name: String? = null,
    val description: String? = null,
    val tags: List<String> = emptyList()
) {
    fun validate(): Result<Unit> {
        return when {
            providerIds.isEmpty() -> Result.failure(
                IllegalArgumentException("At least one provider must be specified")
            )
            numRuns < 1 -> Result.failure(
                IllegalArgumentException("numRuns must be at least 1")
            )
            warmupRuns < 0 -> Result.failure(
                IllegalArgumentException("warmupRuns cannot be negative")
            )
            runsPerImage < 1 -> Result.failure(
                IllegalArgumentException("runsPerImage must be at least 1")
            )
            timeoutMs < 1000 -> Result.failure(
                IllegalArgumentException("timeoutMs must be at least 1000ms")
            )
            maxImages < 1 -> Result.failure(
                IllegalArgumentException("maxImages must be at least 1")
            )
            maxConsecutiveErrors < 1 -> Result.failure(
                IllegalArgumentException("maxConsecutiveErrors must be at least 1")
            )
            else -> Result.success(Unit)
        }
    }
    
    companion object {
        /**
         * Create a default configuration for quick testing
         */
        fun default(providerIds: List<String>): BenchmarkConfig {
            return BenchmarkConfig(
                providerIds = providerIds,
                numRuns = 10,
                warmupRuns = 3,
                runsPerImage = 5
            )
        }
        
        /**
         * Create a configuration for thesis evaluation (high quality)
         */
        fun forThesis(providerIds: List<String>, datasetName: String): BenchmarkConfig {
            return BenchmarkConfig(
                providerIds = providerIds,
                numRuns = 20,          // More runs for statistical significance
                warmupRuns = 5,        // More warm-up for stability
                runsPerImage = 10,     // Multiple runs per image
                maxImages = 100,       // COCO subset size
                autoExport = true,
                exportFormat = ExportFormat.BOTH,
                datasetName = datasetName
            )
        }
        
        /**
         * Create a configuration for quick development testing
         */
        fun forDevelopment(providerIds: List<String>): BenchmarkConfig {
            return BenchmarkConfig(
                providerIds = providerIds,
                numRuns = 3,           // Fewer runs
                warmupRuns = 1,        // Minimal warm-up
                runsPerImage = 2,
                maxImages = 10,        // Small dataset
                autoExport = false
            )
        }
    }
}

/**
 * Export format options
 */
enum class ExportFormat {
    CSV,
    JSON,
    BOTH
}
