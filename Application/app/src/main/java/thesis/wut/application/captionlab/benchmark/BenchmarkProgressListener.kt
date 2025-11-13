package thesis.wut.application.captionlab.benchmark

import thesis.wut.application.captionlab.metrics.AggregatedMetrics
import thesis.wut.application.captionlab.metrics.BenchmarkMetrics

/**
 * Progress callback interface for benchmark execution
 * 
 * Allows UI to receive updates during long-running benchmarks
 */
interface BenchmarkProgressListener {
    /**
     * Called when benchmark starts
     */
    fun onBenchmarkStart(config: BenchmarkConfig) {}
    
    /**
     * Called when a provider benchmark starts
     */
    fun onProviderStart(providerId: String, providerIndex: Int, totalProviders: Int) {}
    
    /**
     * Called when an image is being processed
     */
    fun onImageStart(
        providerId: String,
        imageId: String?,
        imageIndex: Int,
        totalImages: Int
    ) {}
    
    /**
     * Called when a single run completes
     */
    fun onRunComplete(
        providerId: String,
        imageId: String?,
        runNumber: Int,
        totalRuns: Int,
        metrics: BenchmarkMetrics
    ) {}
    
    /**
     * Called when an image processing completes (all runs)
     */
    fun onImageComplete(
        providerId: String,
        imageId: String?,
        imageIndex: Int,
        totalImages: Int,
        aggregated: AggregatedMetrics
    ) {}
    
    /**
     * Called when a provider benchmark completes
     */
    fun onProviderComplete(
        providerId: String,
        providerIndex: Int,
        totalProviders: Int,
        result: ProviderBenchmarkResult
    ) {}
    
    /**
     * Called on errors (non-fatal if continueOnError is true)
     */
    fun onError(
        providerId: String?,
        imageId: String?,
        error: Throwable
    ) {}
    
    /**
     * Called when benchmark completes (success or failure)
     */
    fun onBenchmarkComplete(result: BenchmarkResult) {}
    
    /**
     * Called when benchmark is cancelled
     */
    fun onBenchmarkCancelled() {}
    
    /**
     * Called periodically with overall progress
     */
    fun onProgress(
        completedRuns: Int,
        totalRuns: Int,
        progressPercent: Float
    ) {}
}

/**
 * Simple adapter for BenchmarkProgressListener
 * Allows implementing only needed callbacks
 */
abstract class BenchmarkProgressAdapter : BenchmarkProgressListener {
    // All methods have default implementations in interface
}
