package thesis.wut.application.captionlab

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil.load
import thesis.wut.application.captionlab.databinding.ActivityMainBinding
import thesis.wut.application.captionlab.providers.CaptioningProvider
import thesis.wut.application.captionlab.providers.CloudProviderOpenAI
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import thesis.wut.application.captionlab.providers.LocalProviderTFLite
import thesis.wut.application.captionlab.providers.LocalProviderTFLiteNoMeta
import thesis.wut.application.captionlab.providers.OnnxFlorenceProvider

class MainActivity : AppCompatActivity() {
    private lateinit var vb: ActivityMainBinding
    private var pickedUri: Uri? = null


    private lateinit var localProvider: CaptioningProvider
    private lateinit var cloudProvider: CaptioningProvider
    private lateinit var onnxProvider: CaptioningProvider


    private val pickLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            pickedUri = res.data?.data
            vb.imgPreview.load(pickedUri)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vb = ActivityMainBinding.inflate(layoutInflater)
        setContentView(vb.root)


        val prefs = getSharedPreferences("captionlab", MODE_PRIVATE)
        vb.etApiKey.setText(prefs.getString("openai_key", ""))


//        localProvider = LocalProviderTFLite(this)
        localProvider = LocalProviderTFLiteNoMeta(this)
        cloudProvider = CloudProviderOpenAI { prefs.getString("openai_key", null) }
        onnxProvider = OnnxFlorenceProvider(this)


        vb.btnPick.setOnClickListener { pickImage() }
        vb.btnSaveKey.setOnClickListener {
            prefs.edit { putString("openai_key", vb.etApiKey.text.toString()) }
            Toast.makeText(this, "Saved key", Toast.LENGTH_SHORT).show()
        }


//        vb.btnLocal.setOnClickListener { runProvider(localProvider) }
        vb.btnLocal.setOnClickListener { runProvider(onnxProvider) }
        vb.btnOpenAI.setOnClickListener { runProvider(cloudProvider) }
    }


    private fun pickImage() {
        val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
        pickLauncher.launch(intent)
    }


    private fun runProvider(provider: CaptioningProvider) {
        val uri = pickedUri ?: return Toast.makeText(this, "Pick Image!", Toast.LENGTH_SHORT).show()
        vb.tvOut.text = "Processing… (${provider.id})"


        CoroutineScope(Dispatchers.Main).launch {
            val start = SystemClock.elapsedRealtimeNanos()
            val bmp = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
            }
            val preEnd = SystemClock.elapsedRealtimeNanos()
            val result = withContext(Dispatchers.IO) { provider.caption(bmp) }
            val end = SystemClock.elapsedRealtimeNanos()


            val preMs = (preEnd - start) / 1e6
            val e2eMs = (end - start) / 1e6
            vb.tvOut.text = buildString {
                appendLine("Provider: ${provider.id}")
                appendLine("Opis: ${result.text}")
                result.extra["pre_ms"]?.let { appendLine("pre_ms: ${"%.1f".format(it as Double)}") }
                result.extra["infer_ms"]?.let { appendLine("infer_ms: ${"%.1f".format(it as Double)}") }
                result.extra["post_ms"]?.let { appendLine("post_ms: ${"%.1f".format(it as Double)}") }
                result.extra["e2e_ms"]?.let { appendLine("e2e_ms: ${"%.1f".format(it as Double)}") }            }
        }
    }
}