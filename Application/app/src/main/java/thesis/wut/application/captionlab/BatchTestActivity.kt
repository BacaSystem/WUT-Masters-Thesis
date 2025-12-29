package thesis.wut.application.captionlab

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import thesis.wut.application.captionlab.benchmark.BenchmarkConfig
import thesis.wut.application.captionlab.benchmark.BenchmarkProgressAdapter
import thesis.wut.application.captionlab.benchmark.BenchmarkProgressListener
import thesis.wut.application.captionlab.benchmark.BenchmarkResult
import thesis.wut.application.captionlab.benchmark.BenchmarkRunner
import thesis.wut.application.captionlab.benchmark.ExportFormat
import thesis.wut.application.captionlab.data.DatasetLoader
import thesis.wut.application.captionlab.databinding.ActivityBatchTestBinding
import thesis.wut.application.captionlab.export.DataExporter
import thesis.wut.application.captionlab.metrics.BenchmarkMetrics
import thesis.wut.application.captionlab.metrics.MetricsCollector
import thesis.wut.application.captionlab.providers.manager.ProviderManager

class BatchTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBatchTestBinding
    private lateinit var providerManager: ProviderManager
    private lateinit var metricsCollector: MetricsCollector
    private lateinit var benchmarkRunner: BenchmarkRunner
    private lateinit var dataExporter: DataExporter
    private lateinit var datasetLoader: DatasetLoader

    private var loadedImages: List<Pair<Bitmap, String>> = emptyList()
    private var currentResult: BenchmarkResult? = null
    private var benchmarkStartTime: Long = 0
    private var isRunning: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBatchTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeComponents()
        setupListeners()
        loadSavedConfiguration()
        updateUIState()
    }

    override fun onPause() {
        super.onPause()
        saveConfiguration()
    }

    private fun initializeComponents() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        providerManager = ProviderManager(this) { key -> prefs.getString(key, null) }
        metricsCollector = MetricsCollector(this, providerManager)
        benchmarkRunner = BenchmarkRunner(this, providerManager, metricsCollector)
        dataExporter = DataExporter(this)
        datasetLoader = DatasetLoader(this)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        setupSpinners()
    }
    
    private fun setupSpinners() {
        setupDatasetSpinner()
        setupPresetSpinner()
        setupExportFormatSpinner()
    }

    private fun setupDatasetSpinner() {
        val datasets = arrayOf("COCO Validation 2017", "Custom Images")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, datasets)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerDataset.adapter = adapter
    }

    private fun setupPresetSpinner() {
        val presets = arrayOf("Custom", "Fast", "Quality", "Large")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, presets)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPreset.adapter = adapter

        binding.spinnerPreset.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                applyPreset(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupExportFormatSpinner() {
        val formats = arrayOf("CSV", "JSON", "Both (CSV + JSON)")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, formats)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerExportFormat.adapter = adapter
        binding.spinnerExportFormat.setSelection(2)
    }

    private fun applyPreset(presetIndex: Int) {
        when (presetIndex) {
            0 -> Unit
            1 -> {
                binding.editNumRuns.setText("3")
                binding.editWarmupRuns.setText("1")
                binding.editTimeoutSeconds.setText("30")
                binding.editImageCount.setText("10")
            }
            2 -> {
                binding.editNumRuns.setText("10")
                binding.editWarmupRuns.setText("3")
                binding.editTimeoutSeconds.setText("60")
                binding.editImageCount.setText("100")
            }
            3 -> {
                binding.editNumRuns.setText("20")
                binding.editWarmupRuns.setText("5")
                binding.editTimeoutSeconds.setText("120")
                binding.editImageCount.setText("500")
            }
        }
    }

    private fun setupListeners() {
        binding.buttonLoadDataset.setOnClickListener { loadDataset() }
        binding.buttonSelectAll.setOnClickListener { selectAllProviders(true) }
        binding.buttonSelectNone.setOnClickListener { selectAllProviders(false) }
        binding.buttonStartBenchmark.setOnClickListener { startBenchmark() }
        binding.buttonCancelBenchmark.setOnClickListener { cancelBenchmark() }
        binding.buttonExportResults.setOnClickListener { exportResults() }
        binding.buttonShareResults.setOnClickListener { shareResults() }
    }

    private fun selectAllProviders(selected: Boolean) {
        binding.checkboxFlorence2.isChecked = selected
        binding.checkboxVitGpt2.isChecked = selected
        binding.checkboxBlip.isChecked = selected
        binding.checkboxOpenAI.isChecked = selected
        binding.checkboxAzure.isChecked = selected
        binding.checkboxGemini.isChecked = selected
    }

    private fun getSelectedProviders(): List<String> {
        val selected = mutableListOf<String>()
        if (binding.checkboxFlorence2.isChecked) selected.add(ProviderManager.FLORENCE2_LOCAL)
        if (binding.checkboxVitGpt2.isChecked) selected.add(ProviderManager.VITGPT2_LOCAL)
        if (binding.checkboxBlip.isChecked) selected.add(ProviderManager.BLIP_LOCAL)
        if (binding.checkboxOpenAI.isChecked) selected.add(ProviderManager.OPENAI_CLOUD)
        if (binding.checkboxAzure.isChecked) selected.add(ProviderManager.AZURE_VISION)
        if (binding.checkboxGemini.isChecked) selected.add(ProviderManager.GEMINI_CLOUD)
        return selected
    }

    private fun loadDataset() {
        lifecycleScope.launch {
            try {
                binding.textDatasetStatus.text = "Loading dataset..."
                binding.textDatasetStatus.setTextColor(getColor(android.R.color.holo_orange_dark))

                val datasetType = binding.spinnerDataset.selectedItem.toString()
                val imageCount = binding.editImageCount.text.toString().toIntOrNull() ?: 10
                val startIndex = binding.editStartIndex.text.toString().toIntOrNull() ?: 0
                val endIndex = binding.editEndIndex.text.toString().toIntOrNull() ?: -1

                loadedImages = when {
                    datasetType.contains("COCO") -> loadCocoDataset(datasetType, imageCount, startIndex, endIndex)
                    else -> loadCustomDataset(imageCount, startIndex, endIndex)
                }

                binding.textDatasetStatus.text = "Dataset loaded: ${loadedImages.size} images (indices: $startIndex-${if(endIndex < 0) "auto" else endIndex})"
                binding.textDatasetStatus.setTextColor(getColor(android.R.color.holo_green_dark))
                updateUIState()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to load dataset", e)
                binding.textDatasetStatus.text = "Error loading dataset: ${e.message}"
                binding.textDatasetStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            }
        }
    }

    private suspend fun loadCocoDataset(type: String, count: Int, startIndex: Int, endIndex: Int): List<Pair<Bitmap, String>> {
        return try {
            datasetLoader.loadCocoDataset("val2017", count, startIndex, endIndex)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load COCO dataset", e)
            showDatasetInstructions()
            emptyList()
        }
    }

    private suspend fun loadCustomDataset(count: Int, startIndex: Int, endIndex: Int): List<Pair<Bitmap, String>> {
        return try {
            datasetLoader.loadCustomDataset("custom", count, startIndex, endIndex)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load custom dataset", e)
            showDatasetInstructions()
            emptyList()
        }
    }

    private fun showDatasetInstructions() {
        runOnUiThread {
            val instructions = datasetLoader.getDownloadInstructions()
            AlertDialog.Builder(this)
                .setTitle("Dataset Not Found")
                .setMessage(instructions)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun buildBenchmarkConfig(): BenchmarkConfig {
        val selectedProviders = getSelectedProviders()
        val numRuns = binding.editNumRuns.text.toString().toIntOrNull() ?: 10
        val warmupRuns = binding.editWarmupRuns.text.toString().toIntOrNull() ?: 3
        val timeoutSeconds = binding.editTimeoutSeconds.text.toString().toIntOrNull() ?: 30
        val benchmarkName = binding.editBenchmarkName.text.toString().takeIf { it.isNotBlank() }
        val autoExport = binding.checkboxAutoExport.isChecked

        val exportFormat = when (binding.spinnerExportFormat.selectedItemPosition) {
            0 -> ExportFormat.CSV
            1 -> ExportFormat.JSON
            else -> ExportFormat.BOTH
        }

        val datasetName = when (binding.spinnerDataset.selectedItem.toString()) {
            "COCO Validation 2017" -> "COCO_val2017"
            else -> "Custom"
        }

        return BenchmarkConfig(
            providerIds = selectedProviders,
            numRuns = numRuns,
            warmupRuns = warmupRuns,
            runsPerImage = numRuns,
            timeoutMs = timeoutSeconds * 1000L,
            cooldownMs = 100,
            datasetName = datasetName,
            maxImages = loadedImages.size,
            autoExport = autoExport,
            exportFormat = exportFormat,
            continueOnError = true,
            maxConsecutiveErrors = 3,
            name = benchmarkName,
            description = "Batch test via BatchTestActivity"
        )
    }

    private fun startBenchmark() {
        val selectedProviders = getSelectedProviders()
        if (selectedProviders.isEmpty()) {
            Toast.makeText(this, "Please select at least one provider", Toast.LENGTH_SHORT).show()
            return
        }

        if (loadedImages.isEmpty()) {
            Toast.makeText(this, "Please load a dataset first", Toast.LENGTH_SHORT).show()
            return
        }

        val config = buildBenchmarkConfig()
        config.validate().onFailure { error ->
            Toast.makeText(this, "Invalid configuration: ${error.message}", Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch {
            try {
                isRunning = true
                benchmarkStartTime = System.currentTimeMillis()
                updateUIState()

                currentResult = benchmarkRunner.runBenchmark(
                    config = config,
                    images = loadedImages,
                    progressListener = createProgressListener()
                )

                isRunning = false
                displayResults(currentResult!!)
                updateUIState()

                Toast.makeText(this@BatchTestActivity, "Benchmark completed successfully!", Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                Log.e(TAG, "Benchmark failed", e)
                isRunning = false
                updateUIState()
                Toast.makeText(this@BatchTestActivity, "Benchmark failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun cancelBenchmark() {
        benchmarkRunner.cancel()
        isRunning = false
        updateUIState()
        Toast.makeText(this, "Benchmark cancelled", Toast.LENGTH_SHORT).show()
    }

    private fun createProgressListener(): BenchmarkProgressListener {
        return object : BenchmarkProgressAdapter() {
            override fun onBenchmarkStart(config: BenchmarkConfig) {
                runOnUiThread {
                    binding.textProgress.text = "Starting benchmark..."
                    binding.progressBar.progress = 0
                }
            }

            override fun onProviderStart(providerId: String, providerIndex: Int, totalProviders: Int) {
                runOnUiThread {
                    val providerInfo = providerManager.getProviderInfo(providerId)
                    binding.textCurrentProvider.text = "Provider: ${providerInfo?.displayName ?: providerId} ($providerIndex/$totalProviders)"
                }
            }

            override fun onImageStart(providerId: String, imageId: String?, imageIndex: Int, totalImages: Int) {
                runOnUiThread {
                    binding.textCurrentImage.text = "Image: ${imageIndex + 1}/$totalImages (ID: ${imageId ?: "unknown"})"
                }
            }

            override fun onRunComplete(
                providerId: String,
                imageId: String?,
                runNumber: Int,
                totalRuns: Int,
                metrics: BenchmarkMetrics
            ) {
                runOnUiThread {
                    val elapsed = (System.currentTimeMillis() - benchmarkStartTime) / 1000
                    binding.textElapsedTime.text = "Elapsed: ${formatDuration(elapsed)}"
                }
            }

            override fun onProgress(completedRuns: Int, totalRuns: Int, progressPercent: Float) {
                runOnUiThread {
                    binding.progressBar.progress = progressPercent.toInt()
                    binding.textProgress.text = "Progress: $completedRuns/$totalRuns runs (${progressPercent.toInt()}%)"
                }
            }

            override fun onError(providerId: String?, imageId: String?, error: Throwable) {
                runOnUiThread {
                    Log.e(TAG, "Benchmark error on $providerId/$imageId", error)
                }
            }

            override fun onBenchmarkComplete(result: BenchmarkResult) {
                runOnUiThread {
                    binding.textProgress.text = "Benchmark completed!"
                    binding.progressBar.progress = 100
                }
            }

            override fun onBenchmarkCancelled() {
                runOnUiThread {
                    binding.textProgress.text = "Benchmark cancelled"
                    binding.progressBar.progress = 0
                }
            }
        }
    }

    private fun displayResults(result: BenchmarkResult) {
        binding.textResults.text = formatBenchmarkResults(result)
        binding.scrollViewResults.visibility = View.VISIBLE
    }

    private fun formatBenchmarkResults(result: BenchmarkResult): String = buildString {
        appendLine("=".repeat(50))
        appendLine("BENCHMARK RESULTS")
        appendLine("=".repeat(50))
        appendLine()

        appendLine("Status: ${result.status}")
        appendLine("Total Images: ${result.totalImages}")
        appendLine("Total Runs: ${result.totalRuns}")
        appendLine("Success Rate: ${"%.1f".format(result.successRate * 100)}%")
        appendLine()

        appendLine("Device: ${result.deviceInfo.manufacturer} ${result.deviceInfo.model}")
        appendLine("Android: ${result.deviceInfo.androidVersion}")
        appendLine()

        appendLine("-".repeat(50))
        appendLine("PROVIDER RESULTS")
        appendLine("-".repeat(50))
        appendLine()

        result.providerResults.forEach { (_, provResult) ->
            appendLine("${provResult.providerName} (${provResult.category})")
            appendLine("  Success: ${provResult.successfulImages}/${provResult.totalImages}")

            provResult.aggregated?.let { agg ->
                appendLine("  Latency (p50): ${"%.2f".format(agg.e2eMedian)} ms")
                appendLine("  Latency (p90): ${"%.2f".format(agg.e2eP90)} ms")
                appendLine("  Throughput: ${"%.2f".format(agg.throughputMedian)} img/s")

                agg.ramPeakMeanMb?.let { appendLine("  RAM: ${"%.1f".format(it)} MB") }
                agg.costTotalUsd?.let { appendLine("  Cost: ${"%.4f".format(it)} USD") }
            }

            appendLine()
        }
    }

    private fun exportResults() {
        val result = currentResult
        if (result == null) {
            Toast.makeText(this, "No results to export", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val outputDir = getExternalFilesDir("manual_exports")
                val exportedFiles = dataExporter.exportBenchmarkResult(result, "manual_export", outputDir)
                val message = "Exported ${exportedFiles.size} files:\n${exportedFiles.joinToString("\n") { it.name }}"
                Toast.makeText(this@BatchTestActivity, message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                Toast.makeText(this@BatchTestActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun shareResults() {
        val result = currentResult
        if (result == null) {
            Toast.makeText(this, "No results to share", Toast.LENGTH_SHORT).show()
            return
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "CaptionLab Benchmark Results")
            putExtra(Intent.EXTRA_TEXT, result.getSummary())
        }

        startActivity(Intent.createChooser(shareIntent, "Share Results"))
    }

    private fun updateUIState() {
        val hasDataset = loadedImages.isNotEmpty()
        val hasResults = currentResult != null

        binding.buttonStartBenchmark.isEnabled = !isRunning && hasDataset
        binding.buttonCancelBenchmark.isEnabled = isRunning

        binding.buttonLoadDataset.isEnabled = !isRunning
        binding.spinnerDataset.isEnabled = !isRunning
        binding.editImageCount.isEnabled = !isRunning

        binding.checkboxFlorence2.isEnabled = !isRunning
        binding.checkboxVitGpt2.isEnabled = !isRunning
        binding.checkboxBlip.isEnabled = !isRunning
        binding.checkboxOpenAI.isEnabled = !isRunning
        binding.checkboxAzure.isEnabled = !isRunning
        binding.checkboxGemini.isEnabled = !isRunning
        binding.buttonSelectAll.isEnabled = !isRunning
        binding.buttonSelectNone.isEnabled = !isRunning

        binding.editNumRuns.isEnabled = !isRunning
        binding.editWarmupRuns.isEnabled = !isRunning
        binding.editTimeoutSeconds.isEnabled = !isRunning
        binding.spinnerPreset.isEnabled = !isRunning
        binding.checkboxAutoExport.isEnabled = !isRunning
        binding.spinnerExportFormat.isEnabled = !isRunning
        binding.editBenchmarkName.isEnabled = !isRunning

        binding.buttonExportResults.isEnabled = hasResults && !isRunning
        binding.buttonShareResults.isEnabled = hasResults && !isRunning
    }

    private fun loadSavedConfiguration() {
        val prefs = getSharedPreferences(PREFS_CONFIG, MODE_PRIVATE)
        binding.editNumRuns.setText(prefs.getInt("numRuns", 10).toString())
        binding.editWarmupRuns.setText(prefs.getInt("warmupRuns", 3).toString())
        binding.editTimeoutSeconds.setText(prefs.getInt("timeoutSeconds", 30).toString())
        binding.editStartIndex.setText(prefs.getInt("startIndex", 0).toString())
        binding.editEndIndex.setText(prefs.getInt("endIndex", -1).toString())
        binding.checkboxAutoExport.isChecked = prefs.getBoolean("autoExport", true)
        binding.spinnerExportFormat.setSelection(prefs.getInt("exportFormat", 2))
    }

    private fun saveConfiguration() {
        getSharedPreferences(PREFS_CONFIG, MODE_PRIVATE).edit().apply {
            putInt("numRuns", binding.editNumRuns.text.toString().toIntOrNull() ?: 10)
            putInt("warmupRuns", binding.editWarmupRuns.text.toString().toIntOrNull() ?: 3)
            putInt("timeoutSeconds", binding.editTimeoutSeconds.text.toString().toIntOrNull() ?: 30)
            putInt("startIndex", binding.editStartIndex.text.toString().toIntOrNull() ?: 0)
            putInt("endIndex", binding.editEndIndex.text.toString().toIntOrNull() ?: -1)
            putBoolean("autoExport", binding.checkboxAutoExport.isChecked)
            putInt("exportFormat", binding.spinnerExportFormat.selectedItemPosition)
            apply()
        }
    }

    private fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return when {
            hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, secs)
            minutes > 0 -> String.format("%d:%02d", minutes, secs)
            else -> "${secs}s"
        }
    }

    companion object {
        private const val TAG = "BatchTestActivity"
        private const val PREFS_NAME = "captionlab"
        private const val PREFS_CONFIG = "batch_test_config"
    }
}