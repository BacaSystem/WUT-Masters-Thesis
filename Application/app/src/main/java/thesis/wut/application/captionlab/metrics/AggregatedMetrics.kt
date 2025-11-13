package thesis.wut.application.captionlab.metrics

import com.squareup.moshi.JsonClass
import kotlin.math.sqrt

@JsonClass(generateAdapter = true)
data class AggregatedMetrics(
    val providerId: String,
    val imageId: String? = null,
    val numRuns: Int,
    
    val e2eMean: Double,
    val e2eMedian: Double,
    val e2eStd: Double,
    val e2eMin: Double,
    val e2eMax: Double,
    val e2eP90: Double,
    val e2eP95: Double? = null,
    val e2eP99: Double? = null,
    
    val inferMean: Double? = null,
    val inferMedian: Double? = null,
    val inferStd: Double? = null,
    val inferMin: Double? = null,
    val inferMax: Double? = null,
    
    val preMean: Double? = null,
    val preMedian: Double? = null,
    
    val postMean: Double? = null,
    val postMedian: Double? = null,
    
    val throughputMean: Double,
    val throughputMedian: Double,
    
    val ramPeakMeanMb: Double? = null,
    val ramPeakMaxMb: Double? = null,
    val energyMeanMwh: Double? = null,
    val energyTotalMwh: Double? = null,
    
    val tokensMean: Double? = null,
    val tokensTotal: Int? = null,
    
    val costMeanUsd: Double? = null,
    val costTotalUsd: Double? = null,
    
    val captionLengthMean: Double,
    val captionLengthStd: Double? = null,
    
    val successCount: Int,
    val failureCount: Int,
    val successRate: Double = if (numRuns > 0) successCount.toDouble() / numRuns else 0.0,
    
    val runs: List<BenchmarkMetrics>
) {
    fun getSummary(): String {
        return buildString {
            appendLine("Provider: $providerId")
            if (imageId != null) appendLine("Image: $imageId")
            appendLine("Runs: $numRuns (Success: $successCount, Failure: $failureCount)")
            appendLine()
            appendLine("E2E Time:")
            appendLine("  Median (p50): ${"%.2f".format(e2eMedian)} ms")
            appendLine("  Mean: ${"%.2f".format(e2eMean)} ms (±${"%.2f".format(e2eStd)})")
            appendLine("  p90: ${"%.2f".format(e2eP90)} ms")
            appendLine("  Range: [${"%.2f".format(e2eMin)}, ${"%.2f".format(e2eMax)}] ms")
            appendLine()
            inferMedian?.let {
                appendLine("Inference Time:")
                appendLine("  Median: ${"%.2f".format(it)} ms")
                inferMean?.let { m -> appendLine("  Mean: ${"%.2f".format(m)} ms") }
            }
            appendLine()
            appendLine("Throughput:")
            appendLine("  Median: ${"%.2f".format(throughputMedian)} img/s")
            appendLine("  Mean: ${"%.2f".format(throughputMean)} img/s")
            ramPeakMeanMb?.let {
                appendLine()
                appendLine("RAM:")
                appendLine("  Mean: ${"%.1f".format(it)} MB")
                ramPeakMaxMb?.let { max -> appendLine("  Peak: ${"%.1f".format(max)} MB") }
            }
            energyMeanMwh?.let {
                appendLine()
                appendLine("Energy per inference: ${"%.3f".format(it)} mWh")
                energyTotalMwh?.let { total ->
                    appendLine("Total energy (sum): ${"%.2f".format(total)} mWh")
                }
            }
            costMeanUsd?.let {
                appendLine()
                appendLine("Cost: ${"%.6f".format(it)} USD per image")
            }
        }
    }
    
    companion object {
        /**
         * Aggregate multiple benchmark runs into statistics
         */
        fun fromRuns(
            providerId: String,
            runs: List<BenchmarkMetrics>,
            imageId: String? = null
        ): AggregatedMetrics {
            require(runs.isNotEmpty()) { "Cannot aggregate empty runs list" }
            
            val successfulRuns = runs.filter { it.success }
            val e2eTimes = successfulRuns.map { it.e2eMs }.sorted()
            val inferTimes = successfulRuns.mapNotNull { it.inferenceMs }.sorted()
            val ramPeaks = successfulRuns.mapNotNull { it.ramPeakMb }
            val energies = successfulRuns.mapNotNull { it.energyMwh }
            val tokens = successfulRuns.mapNotNull { it.totalTokens }
            val costs = successfulRuns.mapNotNull { it.costUsd }
            val captionLengths = successfulRuns.map { it.captionText.length }
            
            return AggregatedMetrics(
                providerId = providerId,
                imageId = imageId,
                numRuns = runs.size,
                
                // E2E statistics
                e2eMean = e2eTimes.average(),
                e2eMedian = e2eTimes.percentile(50.0),
                e2eStd = e2eTimes.standardDeviation(),
                e2eMin = e2eTimes.minOrNull() ?: 0.0,
                e2eMax = e2eTimes.maxOrNull() ?: 0.0,
                e2eP90 = e2eTimes.percentile(90.0),
                e2eP95 = if (e2eTimes.size >= 20) e2eTimes.percentile(95.0) else null,
                e2eP99 = if (e2eTimes.size >= 100) e2eTimes.percentile(99.0) else null,
                
                // Inference statistics
                inferMean = inferTimes.takeIf { it.isNotEmpty() }?.average(),
                inferMedian = inferTimes.takeIf { it.isNotEmpty() }?.percentile(50.0),
                inferStd = inferTimes.takeIf { it.isNotEmpty() }?.standardDeviation(),
                inferMin = inferTimes.minOrNull(),
                inferMax = inferTimes.maxOrNull(),
                
                // Pre/post statistics (deprecated, kept for compatibility)
                preMean = null,
                preMedian = null,
                postMean = null,
                postMedian = null,
                
                // Throughput
                throughputMean = 1000.0 / e2eTimes.average(),
                throughputMedian = 1000.0 / e2eTimes.percentile(50.0),
                
                // Resources
                ramPeakMeanMb = ramPeaks.takeIf { it.isNotEmpty() }?.average(),
                ramPeakMaxMb = ramPeaks.maxOrNull(),
                energyMeanMwh = energies.takeIf { it.isNotEmpty() }?.average(),
                energyTotalMwh = energies.takeIf { it.isNotEmpty() }?.sum(),
                
                // Tokens
                tokensMean = tokens.takeIf { it.isNotEmpty() }?.average(),
                tokensTotal = tokens.takeIf { it.isNotEmpty() }?.sum(),
                
                // Cost
                costMeanUsd = costs.takeIf { it.isNotEmpty() }?.average(),
                costTotalUsd = costs.takeIf { it.isNotEmpty() }?.sum(),
                
                // Caption
                captionLengthMean = captionLengths.average(),
                captionLengthStd = captionLengths.map { it.toDouble() }.standardDeviation(),
                
                // Success
                successCount = successfulRuns.size,
                failureCount = runs.size - successfulRuns.size,
                
                // Store all runs
                runs = runs
            )
        }
    }
}

/**
 * Calculate percentile of a sorted list
 */
private fun List<Double>.percentile(p: Double): Double {
    require(p in 0.0..100.0) { "Percentile must be between 0 and 100" }
    if (isEmpty()) return 0.0
    if (size == 1) return first()
    
    val rank = (p / 100.0) * (size - 1)
    val lowerIndex = rank.toInt()
    val upperIndex = minOf(lowerIndex + 1, size - 1)
    val fraction = rank - lowerIndex
    
    return this[lowerIndex] + fraction * (this[upperIndex] - this[lowerIndex])
}

/**
 * Calculate standard deviation
 */
private fun List<Double>.standardDeviation(): Double {
    if (size < 2) return 0.0
    val mean = average()
    val variance = map { (it - mean) * (it - mean) }.average()
    return sqrt(variance)
}
