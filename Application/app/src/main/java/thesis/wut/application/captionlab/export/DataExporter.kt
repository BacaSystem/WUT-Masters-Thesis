package thesis.wut.application.captionlab.export

import android.content.Context
import android.util.Log
import thesis.wut.application.captionlab.metrics.BenchmarkMetrics
import thesis.wut.application.captionlab.benchmark.BenchmarkResult
import thesis.wut.application.captionlab.benchmark.ProviderBenchmarkResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class DataExporter(private val context: Context) {
    
    companion object {
        private const val TAG = "DataExporter"
        private val DATE_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        private val TIMESTAMP_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }
    
    fun exportBenchmarkResult(
        result: BenchmarkResult,
        baseName: String? = null,
        outputDir: File? = null
    ): List<File> {
        val timestamp = DATE_FORMAT.format(Date(result.startTime))
        val name = baseName ?: result.config.name?.replace(" ", "_") ?: "benchmark"
        val fileName = "${name}_${timestamp}"
        
        val exportDir = outputDir ?: getDefaultExportDirectory()
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        
        Log.i(TAG, "Exporting benchmark results to: ${exportDir.absolutePath}")
        
        val exportedFiles = mutableListOf<File>()
        
        try {
            // Export based on config format
            when (result.config.exportFormat) {
                thesis.wut.application.captionlab.benchmark.ExportFormat.CSV -> {
                    val csvFile = File(exportDir, "$fileName.csv")
                    exportToCSV(result, csvFile)
                    exportedFiles.add(csvFile)
                }
                thesis.wut.application.captionlab.benchmark.ExportFormat.JSON -> {
                    val jsonFile = File(exportDir, "$fileName.json")
                    exportToJSON(result, jsonFile)
                    exportedFiles.add(jsonFile)
                }
                thesis.wut.application.captionlab.benchmark.ExportFormat.BOTH -> {
                    val csvFile = File(exportDir, "$fileName.csv")
                    val jsonFile = File(exportDir, "$fileName.json")
                    exportToCSV(result, csvFile)
                    exportToJSON(result, jsonFile)
                    exportedFiles.add(csvFile)
                    exportedFiles.add(jsonFile)
                }
            }
            
            // Always export summary
            val summaryFile = File(exportDir, "${fileName}_summary.txt")
            exportSummary(result, summaryFile)
            exportedFiles.add(summaryFile)
            
            Log.i(TAG, "Successfully exported ${exportedFiles.size} files")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting results: ${e.message}", e)
            throw e
        }
        
        return exportedFiles
    }
    
    fun exportToCSV(result: BenchmarkResult, outputFile: File) {
        Log.d(TAG, "Exporting to CSV: ${outputFile.absolutePath}")
        
        val csv = StringBuilder()
        
        // Simplified header
        csv.appendLine(
            "timestamp,provider_id,provider_name,category,image_id,run_number," +
            "e2e_ms,inference_ms,throughput," +
            "ram_peak_mb,energy_mwh,model_size_mb," +
            "total_tokens,cost_usd," +
            "caption_text,model_name,success,error"
        )
        
        // Data rows - one per individual run
        result.providerResults.forEach { (providerId, provResult) ->
            provResult.perImageResults.forEach { imageResult ->
                imageResult.runs.forEach { run ->
                    csv.appendLine(buildCSVRow(run, provResult))
                }
            }
        }
        
        outputFile.writeText(csv.toString())
        Log.i(TAG, "CSV exported: ${outputFile.length()} bytes")
    }
    
    fun exportToJSON(result: BenchmarkResult, outputFile: File) {
        Log.d(TAG, "Exporting to JSON: ${outputFile.absolutePath}")
        
        val json = buildJSONObject(result)
        
        outputFile.writeText(json)
        Log.i(TAG, "JSON exported: ${outputFile.length()} bytes")
    }
    
    fun exportSummary(result: BenchmarkResult, outputFile: File) {
        Log.d(TAG, "Exporting summary: ${outputFile.absolutePath}")
        
        val summary = buildString {
            appendLine("=" .repeat(70))
            appendLine("BENCHMARK RESULTS SUMMARY")
            appendLine("=" .repeat(70))
            appendLine()
            
            // Metadata
            appendLine("Benchmark Information:")
            appendLine("  Name: ${result.config.name ?: "Unnamed"}")
            result.config.description?.let { appendLine("  Description: $it") }
            appendLine("  Dataset: ${result.config.datasetName ?: "Unknown"}")
            appendLine("  Start Time: ${TIMESTAMP_FORMAT.format(Date(result.startTime))}")
            result.endTime?.let {
                appendLine("  End Time: ${TIMESTAMP_FORMAT.format(Date(it))}")
            }
            result.durationMs?.let {
                appendLine("  Duration: ${"%.2f".format(it / 1000.0)} seconds")
            }
            appendLine("  Status: ${result.status}")
            appendLine()
            
            // Device Info
            appendLine("Device Information:")
            appendLine("  Manufacturer: ${result.deviceInfo.manufacturer}")
            appendLine("  Model: ${result.deviceInfo.model}")
            appendLine("  Android: ${result.deviceInfo.androidVersion} (SDK ${result.deviceInfo.sdkInt})")
            appendLine("  CPU ABI: ${result.deviceInfo.cpuAbi}")
            appendLine("  Total RAM: ${result.deviceInfo.totalMemoryMb} MB")
            appendLine("  Available RAM: ${result.deviceInfo.availableMemoryMb} MB")
            appendLine()
            
            // Overall Statistics
            appendLine("Overall Statistics:")
            appendLine("  Total Images: ${result.totalImages}")
            appendLine("  Total Runs: ${result.totalRuns}")
            appendLine("  Successful: ${result.successfulRuns} (${result.successRate * 100}%)")
            appendLine("  Failed: ${result.failedRuns}")
            appendLine("  Providers Tested: ${result.providerResults.size}")
            appendLine()
            
            // Per-Provider Results
            appendLine("-".repeat(70))
            appendLine("PROVIDER RESULTS")
            appendLine("-".repeat(70))
            appendLine()
            
            result.providerResults.forEach { (providerId, provResult) ->
                appendLine("Provider: ${provResult.providerName} ($providerId)")
                appendLine("  Category: ${provResult.category}")
                appendLine("  Success Rate: ${"%.1f".format(provResult.successRate * 100)}%")
                appendLine("  Images: ${provResult.successfulImages}/${provResult.totalImages}")
                appendLine("  Duration: ${"%.2f".format(provResult.durationMs / 1000.0)} seconds")
                appendLine()
                
                provResult.aggregated?.let { agg ->
                    appendLine("  Performance Metrics:")
                    appendLine("    Latency (p50): ${"%.2f".format(agg.e2eMedian)} ms")
                    appendLine("    Latency (p90): ${"%.2f".format(agg.e2eP90)} ms")
                    appendLine("    Latency (mean): ${"%.2f".format(agg.e2eMean)} ± ${"%.2f".format(agg.e2eStd)} ms")
                    appendLine("    Latency (range): [${"%.2f".format(agg.e2eMin)}, ${"%.2f".format(agg.e2eMax)}] ms")
                    appendLine("    Throughput (median): ${"%.2f".format(agg.throughputMedian)} img/s")
                    appendLine()
                    
                    agg.inferMedian?.let {
                        appendLine("    Inference Time (median): ${"%.2f".format(it)} ms")
                    }
                    
                    agg.ramPeakMeanMb?.let {
                        appendLine("    RAM Peak (mean): ${"%.1f".format(it)} MB")
                        agg.ramPeakMaxMb?.let { max ->
                            appendLine("    RAM Peak (max): ${"%.1f".format(max)} MB")
                        }
                    }
                    
                    agg.energyMeanMwh?.let {
                        appendLine("    Energy (mean): ${"%.3f".format(it)} mWh per image")
                        agg.energyTotalMwh?.let { total ->
                            appendLine("    Energy (total sum): ${"%.2f".format(total)} mWh")
                        }
                    }
                    
                    agg.costMeanUsd?.let {
                        appendLine("    Cost (mean): ${"%.6f".format(it)} USD per image")
                        agg.costTotalUsd?.let { total ->
                            appendLine("    Cost (total): ${"%.4f".format(total)} USD")
                        }
                    }
                }
                
                if (provResult.errors.isNotEmpty()) {
                    appendLine()
                    appendLine("  Errors (${provResult.errors.size}):")
                    provResult.errors.take(5).forEach { error ->
                        appendLine("    - $error")
                    }
                    if (provResult.errors.size > 5) {
                        appendLine("    ... and ${provResult.errors.size - 5} more")
                    }
                }
                
                appendLine()
            }
            
            // Comparison Table
            if (result.providerResults.size > 1) {
                appendLine("-".repeat(70))
                appendLine("PROVIDER COMPARISON")
                appendLine("-".repeat(70))
                appendLine()
                appendLine("%-25s %12s %12s %12s %10s".format(
                    "Provider", "p50 (ms)", "p90 (ms)", "RAM (MB)", "Success %"
                ))
                appendLine("-".repeat(70))
                
                result.providerResults.forEach { (_, provResult) ->
                    provResult.aggregated?.let { agg ->
                        appendLine("%-25s %12.2f %12.2f %12.1f %9.1f%%".format(
                            provResult.providerName,
                            agg.e2eMedian,
                            agg.e2eP90,
                            agg.ramPeakMeanMb ?: 0.0,
                            provResult.successRate * 100
                        ))
                    }
                }
                appendLine()
            }
            
            appendLine("=" .repeat(70))
            appendLine("END OF REPORT")
            appendLine("=" .repeat(70))
        }
        
        outputFile.writeText(summary)
        Log.i(TAG, "Summary exported: ${outputFile.length()} bytes")
    }
    
    private fun buildCSVRow(metrics: BenchmarkMetrics, provResult: ProviderBenchmarkResult): String {
        return listOf(
            TIMESTAMP_FORMAT.format(Date(metrics.timestamp)),
            metrics.providerId,
            provResult.providerName,
            provResult.category,
            metrics.imageId ?: "",
            metrics.runNumber.toString(),
            
            // Performance (RQ1)
            metrics.e2eMs.toString(),
            metrics.inferenceMs?.toString() ?: "",
            metrics.throughput.toString(),
            
            // Resources (RQ2)
            metrics.ramPeakMb?.toString() ?: "",
            metrics.energyMwh?.toString() ?: "",
            metrics.modelSizeMb?.toString() ?: "",
            
            // Cost (RQ3)
            metrics.totalTokens?.toString() ?: "",
            metrics.costUsd?.toString() ?: "",
            
            // Result (RQ4)
            "\"${metrics.captionText.replace("\"", "\"\"")}\"", // Escape quotes
            metrics.modelName ?: "",
            if (metrics.success) "1" else "0",
            "\"${(metrics.error ?: "").replace("\"", "\"\"")}\"" // Escape quotes
        ).joinToString(",")
    }
    
    private fun buildJSONObject(result: BenchmarkResult): String {
        return buildString {
            appendLine("{")
            appendLine("  \"metadata\": {")
            appendLine("    \"name\": ${jsonString(result.config.name)},")
            appendLine("    \"description\": ${jsonString(result.config.description)},")
            appendLine("    \"dataset\": ${jsonString(result.config.datasetName)},")
            appendLine("    \"start_time\": \"${TIMESTAMP_FORMAT.format(Date(result.startTime))}\",")
            result.endTime?.let {
                appendLine("    \"end_time\": \"${TIMESTAMP_FORMAT.format(Date(it))}\",")
            }
            appendLine("    \"duration_ms\": ${result.durationMs},")
            appendLine("    \"status\": \"${result.status}\"")
            appendLine("  },")
            appendLine("  \"device\": {")
            appendLine("    \"manufacturer\": ${jsonString(result.deviceInfo.manufacturer)},")
            appendLine("    \"model\": ${jsonString(result.deviceInfo.model)},")
            appendLine("    \"android_version\": ${jsonString(result.deviceInfo.androidVersion)},")
            appendLine("    \"sdk_int\": ${result.deviceInfo.sdkInt},")
            appendLine("    \"cpu_abi\": ${jsonString(result.deviceInfo.cpuAbi)},")
            appendLine("    \"total_memory_mb\": ${result.deviceInfo.totalMemoryMb},")
            appendLine("    \"available_memory_mb\": ${result.deviceInfo.availableMemoryMb}")
            appendLine("  },")
            appendLine("  \"statistics\": {")
            appendLine("    \"total_images\": ${result.totalImages},")
            appendLine("    \"total_runs\": ${result.totalRuns},")
            appendLine("    \"successful_runs\": ${result.successfulRuns},")
            appendLine("    \"failed_runs\": ${result.failedRuns},")
            appendLine("    \"success_rate\": ${result.successRate}")
            appendLine("  },")
            appendLine("  \"providers\": [")
            
            result.providerResults.entries.forEachIndexed { index, (_, provResult) ->
                if (index > 0) appendLine(",")
                append(buildProviderJSON(provResult, indent = 4))
            }
            
            appendLine()
            appendLine("  ]")
            appendLine("}")
        }
    }
    
    private fun buildProviderJSON(provResult: ProviderBenchmarkResult, indent: Int): String {
        val pad = " ".repeat(indent)
        return buildString {
            appendLine("$pad{")
            appendLine("$pad  \"provider_id\": ${jsonString(provResult.providerId)},")
            appendLine("$pad  \"provider_name\": ${jsonString(provResult.providerName)},")
            appendLine("$pad  \"category\": ${jsonString(provResult.category)},")
            appendLine("$pad  \"success_rate\": ${provResult.successRate},")
            appendLine("$pad  \"total_images\": ${provResult.totalImages},")
            appendLine("$pad  \"successful_images\": ${provResult.successfulImages},")
            appendLine("$pad  \"duration_ms\": ${provResult.durationMs}")
            append("$pad}")
        }
    }
    
    private fun jsonString(value: String?): String {
        return if (value == null) "null" 
        else "\"${value.replace("\"", "\\\"").replace("\n", "\\n")}\""
    }
    
    private fun getDefaultExportDirectory(): File {
        return context.getExternalFilesDir("exports")
            ?: File(context.filesDir, "exports")
    }
}
