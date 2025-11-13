package thesis.wut.application.captionlab.benchmark

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import thesis.wut.application.captionlab.export.DataExporter
import thesis.wut.application.captionlab.metrics.AggregatedMetrics
import thesis.wut.application.captionlab.metrics.MetricsCollector
import thesis.wut.application.captionlab.providers.manager.ProviderManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class BenchmarkRunner(
    private val context: Context,
    private val providerManager: ProviderManager,
    private val metricsCollector: MetricsCollector
) {
    private val dataExporter = DataExporter(context)
    
    @Volatile
    private var currentStatus = BenchmarkStatus.NOT_STARTED
    
    @Volatile
    private var isCancelled = false
    
    private var currentJob: Job? = null
    
    suspend fun runBenchmark(
        config: BenchmarkConfig,
        images: List<Pair<Bitmap, String?>>,
        progressListener: BenchmarkProgressListener? = null
    ): BenchmarkResult = withContext(Dispatchers.Default) {
        config.validate().getOrElse { error ->
            Log.e(TAG, "Invalid configuration: ${error.message}")
            return@withContext createErrorResult(config, error.message ?: "Invalid configuration")
        }
        
        if (images.isEmpty()) {
            return@withContext createErrorResult(config, "No images provided")
        }
        
        currentStatus = BenchmarkStatus.RUNNING
        isCancelled = false
        currentJob = coroutineContext[Job]
        
        val startTime = System.currentTimeMillis()
        Log.i(TAG, "Starting benchmark: ${config.name ?: "Unnamed"}")
        Log.i(TAG, "Providers: ${config.providerIds}, Images: ${images.size}")
        
        progressListener?.onBenchmarkStart(config)
        
        try {
            val testImages = images.take(config.maxImages)
            val deviceInfo = getDeviceInfo()
            
            val providerResults = mutableMapOf<String, ProviderBenchmarkResult>()
            var totalRuns = 0
            var successfulRuns = 0
            var failedRuns = 0
            
            config.providerIds.forEachIndexed { providerIndex, providerId ->
                if (isCancelled) {
                    Log.i(TAG, "Benchmark cancelled")
                    progressListener?.onBenchmarkCancelled()
                    currentStatus = BenchmarkStatus.CANCELLED
                    return@withContext createCancelledResult(
                        config, providerResults, deviceInfo, startTime, totalRuns, successfulRuns, failedRuns
                    )
                }
                
                Log.i(TAG, "Testing provider ${providerIndex + 1}/${config.providerIds.size}: $providerId")
                progressListener?.onProviderStart(providerId, providerIndex, config.providerIds.size)
                
                if (providerId.contains("florence", ignoreCase = true)) {
                    Log.d(TAG, "Pre-GC for Florence-2")
                    System.gc()
                    Thread.sleep(200)
                }
                
                try {
                    val providerResult = runProviderBenchmark(
                        config = config,
                        providerId = providerId,
                        images = testImages,
                        providerIndex = providerIndex,
                        totalProviders = config.providerIds.size,
                        progressListener = progressListener
                    )
                    
                    providerResults[providerId] = providerResult
                    totalRuns += providerResult.perImageResults.sumOf { it.numRuns }
                    successfulRuns += providerResult.perImageResults.sumOf { it.successCount }
                    failedRuns += providerResult.perImageResults.sumOf { it.failureCount }
                    
                    progressListener?.onProviderComplete(
                        providerId,
                        providerIndex,
                        config.providerIds.size,
                        providerResult
                    )
                    
                    Log.i(TAG, "Provider $providerId completed: ${providerResult.successRate * 100}% success")
                    
                } catch (e: CancellationException) {
                    throw e // Re-throw cancellation
                } catch (e: Exception) {
                    Log.e(TAG, "Provider $providerId failed: ${e.message}", e)
                    progressListener?.onError(providerId, null, e)
                    
                    if (!config.continueOnError) {
                        throw e
                    }
                }
            }
            
            val endTime = System.currentTimeMillis()
            currentStatus = BenchmarkStatus.COMPLETED
            
            val result = BenchmarkResult(
                config = config,
                startTime = startTime,
                endTime = endTime,
                providerResults = providerResults,
                totalImages = testImages.size,
                totalRuns = totalRuns,
                successfulRuns = successfulRuns,
                failedRuns = failedRuns,
                deviceInfo = deviceInfo,
                status = BenchmarkStatus.COMPLETED
            )
            
            Log.i(TAG, "Benchmark completed successfully")
            Log.i(TAG, "Total runs: $totalRuns, Success: $successfulRuns, Failed: $failedRuns")
            
            progressListener?.onBenchmarkComplete(result)
            
            if (config.autoExport) {
                exportResults(result, config)
            }
            
            return@withContext result
            
        } catch (e: CancellationException) {
            Log.i(TAG, "Benchmark cancelled")
            currentStatus = BenchmarkStatus.CANCELLED
            progressListener?.onBenchmarkCancelled()
            throw e
            
        } catch (e: Exception) {
            Log.e(TAG, "Benchmark failed: ${e.message}", e)
            currentStatus = BenchmarkStatus.FAILED
            
            val result = createErrorResult(config, e.message ?: "Unknown error")
            progressListener?.onBenchmarkComplete(result)
            return@withContext result
        } finally {
            currentJob = null
        }
    }
    
    private suspend fun runProviderBenchmark(
        config: BenchmarkConfig,
        providerId: String,
        images: List<Pair<Bitmap, String?>>,
        providerIndex: Int,
        totalProviders: Int,
        progressListener: BenchmarkProgressListener?
    ): ProviderBenchmarkResult {
        val providerStartTime = System.currentTimeMillis()
        val perImageResults = mutableListOf<AggregatedMetrics>()
        val errors = mutableListOf<String>()
        var consecutiveErrors = 0
        
        val providerName = providerManager.getProviderDisplayName(providerId)
        val category = providerManager.getProviderCategory(providerId)
        
        images.forEachIndexed { imageIndex, (bitmap, imageId) ->
            if (isCancelled) {
                throw CancellationException("Benchmark cancelled")
            }
            
            Log.d(TAG, "Provider $providerId: Image ${imageIndex + 1}/${images.size} ($imageId)")
            progressListener?.onImageStart(providerId, imageId, imageIndex, images.size)
            
            try {
                val warmupRuns = if (imageIndex == 0) config.warmupRuns else 0
                
                val aggregated = metricsCollector.collectMultiple(
                    providerId = providerId,
                    bitmap = bitmap,
                    numRuns = config.runsPerImage,
                    warmupRuns = warmupRuns,
                    imageId = imageId
                )
                
                perImageResults.add(aggregated)
                consecutiveErrors = 0
                
                progressListener?.onImageComplete(providerId, imageId, imageIndex, images.size, aggregated)
                
                val totalCompleted = (providerIndex * images.size) + imageIndex + 1
                val totalImages = totalProviders * images.size
                val progress = (totalCompleted.toFloat() / totalImages * 100).roundToInt()
                
                progressListener?.onProgress(
                    completedRuns = totalCompleted * config.runsPerImage,
                    totalRuns = totalImages * config.runsPerImage,
                    progressPercent = progress.toFloat()
                )
                
                if (providerId.contains("florence", ignoreCase = true)) {
                    System.gc()
                    Thread.sleep(100)
                }
                
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error processing image $imageId: ${e.message}", e)
                errors.add("Image $imageId: ${e.message}")
                consecutiveErrors++
                
                progressListener?.onError(providerId, imageId, e)
                
                if (consecutiveErrors >= config.maxConsecutiveErrors) {
                    Log.e(TAG, "Max consecutive errors reached for $providerId")
                    if (!config.continueOnError) {
                        throw Exception("Max consecutive errors reached: $consecutiveErrors")
                    }
                }
            }
        }
        
        val providerEndTime = System.currentTimeMillis()
        val durationMs = providerEndTime - providerStartTime
        
        val overallAggregated = if (perImageResults.isNotEmpty()) {
            val allRuns = perImageResults.flatMap { it.runs }
            if (allRuns.isNotEmpty()) {
                AggregatedMetrics.fromRuns(providerId, allRuns, null)
            } else null
        } else null
        
        return ProviderBenchmarkResult(
            providerId = providerId,
            providerName = providerName,
            category = category,
            aggregated = overallAggregated,
            perImageResults = perImageResults,
            totalImages = images.size,
            successfulImages = perImageResults.size,
            failedImages = images.size - perImageResults.size,
            errors = errors,
            durationMs = durationMs
        )
    }
    
    fun cancel() {
        Log.i(TAG, "Cancelling benchmark...")
        isCancelled = true
        currentJob?.cancel()
    }
    
    fun isRunning(): Boolean = currentStatus == BenchmarkStatus.RUNNING
    
    fun getStatus(): BenchmarkStatus = currentStatus
    
    private fun getDeviceInfo(): DeviceInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            cpuAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            totalMemoryMb = memoryInfo.totalMem / (1024 * 1024),
            availableMemoryMb = memoryInfo.availMem / (1024 * 1024)
        )
    }
    
    private fun exportResults(result: BenchmarkResult, config: BenchmarkConfig) {
        if (!config.autoExport) {
            return
        }
        
        try {
            val baseName = config.name?.replace(" ", "_") ?: "benchmark"
            val outputDir = config.outputDirectory?.let { File(it) }
                ?: context.getExternalFilesDir("exports")
                ?: context.filesDir
            
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }
            
            Log.i(TAG, "Exporting results to: ${outputDir.absolutePath}")
            
            val exportedFiles = dataExporter.exportBenchmarkResult(result, baseName, outputDir)
            
            exportedFiles.forEach { file ->
                Log.i(TAG, "Exported: ${file.name} (${file.length()} bytes)")
            }
            
            Log.i(TAG, "Export completed: ${exportedFiles.size} files")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export results: ${e.message}", e)
        }
    }
    
    private fun createErrorResult(config: BenchmarkConfig, error: String): BenchmarkResult {
        return BenchmarkResult(
            config = config,
            startTime = System.currentTimeMillis(),
            endTime = System.currentTimeMillis(),
            providerResults = emptyMap(),
            totalImages = 0,
            totalRuns = 0,
            successfulRuns = 0,
            failedRuns = 0,
            deviceInfo = getDeviceInfo(),
            status = BenchmarkStatus.FAILED,
            errorMessage = error
        )
    }
    
    private fun createCancelledResult(
        config: BenchmarkConfig,
        providerResults: Map<String, ProviderBenchmarkResult>,
        deviceInfo: DeviceInfo,
        startTime: Long,
        totalRuns: Int,
        successfulRuns: Int,
        failedRuns: Int
    ): BenchmarkResult {
        return BenchmarkResult(
            config = config,
            startTime = startTime,
            endTime = System.currentTimeMillis(),
            providerResults = providerResults,
            totalImages = providerResults.values.sumOf { it.totalImages },
            totalRuns = totalRuns,
            successfulRuns = successfulRuns,
            failedRuns = failedRuns,
            deviceInfo = deviceInfo,
            status = BenchmarkStatus.CANCELLED,
            errorMessage = "Benchmark was cancelled by user"
        )
    }
    
    companion object {
        private const val TAG = "BenchmarkRunner"
    }
}
