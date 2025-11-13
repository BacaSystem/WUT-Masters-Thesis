package thesis.wut.application.captionlab.benchmark

import com.squareup.moshi.JsonClass
import thesis.wut.application.captionlab.metrics.AggregatedMetrics

@JsonClass(generateAdapter = true)
data class BenchmarkResult(
    val config: BenchmarkConfig,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val durationMs: Long? = endTime?.let { it - startTime },
    val providerResults: Map<String, ProviderBenchmarkResult>,
    val totalImages: Int,
    val totalRuns: Int,
    val successfulRuns: Int,
    val failedRuns: Int,
    val successRate: Double = if (totalRuns > 0) successfulRuns.toDouble() / totalRuns else 0.0,
    val deviceInfo: DeviceInfo,
    val status: BenchmarkStatus,
    val errorMessage: String? = null
) {
    fun getSummary(): String {
        return buildString {
            appendLine("=== Benchmark Results ===")
            config.name?.let { appendLine("Name: $it") }
            appendLine("Status: $status")
            appendLine("Duration: ${durationMs?.let { it / 1000.0 }}s")
            appendLine()
            appendLine("Providers Tested: ${providerResults.size}")
            appendLine("Total Images: $totalImages")
            appendLine("Total Runs: $totalRuns")
            appendLine("Success Rate: ${"%.1f".format(successRate * 100)}%")
            appendLine()
            appendLine("Provider Results:")
            providerResults.forEach { (id, result) ->
                appendLine("  $id:")
                appendLine("    Median Latency: ${"%.2f".format(result.aggregated?.e2eMedian ?: 0.0)} ms")
                appendLine("    Success Rate: ${"%.1f".format(result.successRate * 100)}%")
            }
            errorMessage?.let {
                appendLine()
                appendLine("Error: $it")
            }
        }
    }
}

@JsonClass(generateAdapter = true)
data class ProviderBenchmarkResult(
    val providerId: String,
    val providerName: String,
    val category: String,
    val aggregated: AggregatedMetrics?,
    val perImageResults: List<AggregatedMetrics>,
    val totalImages: Int,
    val successfulImages: Int,
    val failedImages: Int,
    val successRate: Double = if (totalImages > 0) successfulImages.toDouble() / totalImages else 0.0,
    val errors: List<String> = emptyList(),
    val durationMs: Long
)

@JsonClass(generateAdapter = true)
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdkInt: Int,
    val cpuAbi: String,
    val totalMemoryMb: Long,
    val availableMemoryMb: Long
)

/**
 * Benchmark execution status
 */
enum class BenchmarkStatus {
    NOT_STARTED,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}
