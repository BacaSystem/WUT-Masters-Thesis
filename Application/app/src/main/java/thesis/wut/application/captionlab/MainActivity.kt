package thesis.wut.application.captionlab

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import coil.load
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import thesis.wut.application.captionlab.databinding.ActivityMainBinding
import thesis.wut.application.captionlab.metrics.BenchmarkMetrics
import thesis.wut.application.captionlab.metrics.MetricsCollector
import thesis.wut.application.captionlab.providers.manager.ProviderManager

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var providerManager: ProviderManager
    private lateinit var metricsCollector: MetricsCollector
    
    private var selectedImageUri: Uri? = null
    private var lastMetrics: BenchmarkMetrics? = null

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedImageUri = result.data?.data
            selectedImageUri?.let { uri ->
                binding.imgPreview.load(uri) {
                    crossfade(true)
                    placeholder(android.R.color.darker_gray)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        initializeComponents()
        setupListeners()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                showSettingsDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initializeComponents() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        providerManager = ProviderManager(this) { key -> prefs.getString(key, null) }
        metricsCollector = MetricsCollector(this, providerManager)
    }

    private fun setupListeners() {
        binding.btnPick.setOnClickListener { pickImage() }
        binding.btnGenerate.setOnClickListener { generateCaption() }
        binding.btnBatchTests.setOnClickListener { openBatchTests() }
        binding.btnExport.setOnClickListener { openExportsDirectory() }
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
        imagePickerLauncher.launch(intent)
    }

    private fun generateCaption() {
        val uri = selectedImageUri
        if (uri == null) {
            Toast.makeText(this, getString(R.string.no_image_selected), Toast.LENGTH_SHORT).show()
            return
        }

        val providerId = getSelectedProviderId() ?: return
        val provider = providerManager.getProvider(providerId)
        
        if (provider == null) {
            Toast.makeText(this, "Provider not available", Toast.LENGTH_SHORT).show()
            return
        }

        setGeneratingState(true, provider.id)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri).use {
                        BitmapFactory.decodeStream(it)
                    }
                }

                val metrics = withContext(Dispatchers.IO) {
                    metricsCollector.collectSingle(
                        providerId = providerId,
                        bitmap = bitmap,
                        imageId = uri.lastPathSegment ?: "unknown"
                    )
                }

                lastMetrics = metrics
                displayResults(metrics)

            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            } finally {
                setGeneratingState(false, null)
            }
        }
    }

    private fun getSelectedProviderId(): String? {
        return when (binding.rgProviders.checkedRadioButtonId) {
            R.id.rbFlorence -> ProviderManager.FLORENCE2_LOCAL
            R.id.rbViTGPT2 -> ProviderManager.VITGPT2_LOCAL
            R.id.rbBlip -> ProviderManager.BLIP_LOCAL
            R.id.rbOpenAI -> ProviderManager.OPENAI_CLOUD
            R.id.rbAzureVision -> ProviderManager.AZURE_VISION
            R.id.rbGemini -> ProviderManager.GEMINI_CLOUD
            else -> {
                Toast.makeText(this, "Wybierz provider", Toast.LENGTH_SHORT).show()
                null
            }
        }
    }

    private fun setGeneratingState(isGenerating: Boolean, providerId: String?) {
        if (isGenerating) {
            binding.cardResults.visibility = View.GONE
        }
        binding.btnGenerate.isEnabled = !isGenerating
        binding.btnGenerate.text = if (isGenerating) {
            "${getString(R.string.processing)} ($providerId)"
        } else {
            getString(R.string.generate_caption)
        }
    }

    private fun displayResults(metrics: BenchmarkMetrics) {
        binding.cardResults.visibility = View.VISIBLE
        binding.tvProvider.text = "${getString(R.string.provider_label)} ${metrics.providerId}"
        binding.tvCaption.text = metrics.captionText

        binding.tvMetrics.text = formatMetrics(metrics)

        binding.root.post {
            val scrollView = binding.root.getChildAt(1) as? androidx.core.widget.NestedScrollView
            scrollView?.smoothScrollTo(0, binding.cardResults.top)
        }
    }

    private fun formatMetrics(metrics: BenchmarkMetrics): String = buildString {
        appendLine("Performance:")
        appendLine("  E2E Time: ${"%.1f".format(metrics.e2eMs)} ms")
        appendLine("  Throughput: ${"%.2f".format(metrics.throughput)} img/s")

        metrics.inferenceMs?.let {
            appendLine("  Inference: ${"%.1f".format(it)} ms")
        }

        appendLine()
        appendLine("Resources:")
        metrics.ramPeakMb?.let {
            appendLine("  RAM Peak: ${"%.1f".format(it)} MB")
        }
        metrics.energyMwh?.let {
            appendLine("  Energy: ${"%.4f".format(it)} mWh")
        }

        if (metrics.isCloud) {
            appendLine()
            appendLine("Cloud Details:")
            metrics.totalTokens?.let { appendLine("  Total Tokens: $it") }
            metrics.costUsd?.let { appendLine("  Cost: $${"%.4f".format(it)}") }
            metrics.modelName?.let { appendLine("  Model: $it") }
        } else if (metrics.isLocal) {
            appendLine()
            appendLine("Local Model:")
            metrics.modelName?.let { appendLine("  Model: $it") }
            metrics.modelSizeMb?.let { appendLine("  Size: ${"%.1f".format(it)} MB") }
        }

        if (!metrics.success) {
            appendLine()
            appendLine("Error: ${metrics.error ?: "Unknown error"}")
        }
    }

    private fun showSettingsDialog() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val layout = createSettingsLayout(prefs)

        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings))
            .setView(layout)
            .setPositiveButton(getString(R.string.save_key)) { _, _ ->
                saveApiKeys(layout, prefs)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun createSettingsLayout(prefs: android.content.SharedPreferences): android.widget.LinearLayout {
        return android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)

            addView(createKeyInputSection("OpenAI API Key:", "openai_key", prefs))
            addView(createKeyInputSection("Azure Vision API Key:", "azure_key", prefs))
            addView(createKeyInputSection("Google Gemini API Key:", "gemini_key", prefs))
        }
    }

    private fun createKeyInputSection(
        label: String,
        prefKey: String,
        prefs: android.content.SharedPreferences
    ): android.view.View {
        return android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL

            addView(android.widget.TextView(this@MainActivity).apply {
                text = label
                setPadding(0, if (prefKey != "openai_key") 32 else 0, 0, 8)
            })

            addView(android.widget.EditText(this@MainActivity).apply {
                tag = prefKey
                hint = if (prefKey == "openai_key") "sk-..." else "API Key"
                setText(prefs.getString(prefKey, ""))
                inputType = android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            })
        }
    }

    private fun saveApiKeys(layout: android.widget.LinearLayout, prefs: android.content.SharedPreferences) {
        prefs.edit {
            for (key in listOf("openai_key", "azure_key", "gemini_key")) {
                val editText = layout.findViewWithTag<android.widget.EditText>(key)
                putString(key, editText?.text?.toString())
            }
        }
        Toast.makeText(this, getString(R.string.key_saved), Toast.LENGTH_SHORT).show()
    }

    private fun openBatchTests() {
        startActivity(Intent(this, BatchTestActivity::class.java))
    }

    private fun openExportsDirectory() {
        val exportsDir = getExternalFilesDir("exports")

        if (exportsDir == null || !exportsDir.exists() || exportsDir.listFiles()?.isEmpty() != false) {
            Toast.makeText(this, "No exports found. Run batch tests first.", Toast.LENGTH_LONG).show()
            return
        }

        val files = exportsDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
        val fileDescriptions = files.map { formatFileDescription(it) }.toTypedArray()

        android.app.AlertDialog.Builder(this)
            .setTitle("Exported Results (${files.size})")
            .setItems(fileDescriptions) { _, index -> openFile(files[index]) }
            .setNegativeButton("Close", null)
            .setNeutralButton("Open Folder") { _, _ -> openExportsFolderInFileManager() }
            .show()
    }

    private fun formatFileDescription(file: java.io.File): String {
        val sizeKb = file.length() / 1024
        val date = android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", file.lastModified())
        return "${file.name}\n  ${sizeKb}KB - $date"
    }

    private fun openFile(file: java.io.File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )

            val mimeType = getMimeType(file.extension)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getMimeType(extension: String): String {
        return when (extension.lowercase()) {
            "csv" -> "text/csv"
            "json" -> "application/json"
            "txt" -> "text/plain"
            else -> "*/*"
        }
    }

    private fun openExportsFolderInFileManager() {
        try {
            val exportsDir = getExternalFilesDir("exports")!!
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                exportsDir
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intent, "Open folder with"))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open folder: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val PREFS_NAME = "captionlab"
    }
}