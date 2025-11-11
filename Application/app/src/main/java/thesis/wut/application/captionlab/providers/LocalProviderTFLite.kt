package thesis.wut.application.captionlab.providers

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.classifier.Classifications
import org.tensorflow.lite.task.vision.classifier.ImageClassifier

class LocalProviderTFLite(private val context: Context) : CaptioningProvider {
    override val id: String = "tflite_local"

    private val classifier: ImageClassifier by lazy {
        val options = ImageClassifier.ImageClassifierOptions.builder()
            .setMaxResults(3)
            .setScoreThreshold(0.15f)
            .build()
        ImageClassifier.createFromFileAndOptions(context, "mobilenet_v2_1.0_224_quant.tflite", options)
    }

    override suspend fun caption(bitmap: Bitmap): CaptionResult {
        val tensor = TensorImage.fromBitmap(bitmap)
        val results: List<Classifications> = classifier.classify(tensor)
        val top = results.firstOrNull()?.categories ?: emptyList()
        val sentence = if (top.isNotEmpty()) {
            val parts = top.joinToString { it.label }
            "Obraz prawdopodobnie przedstawia: $parts."
        } else "Brak pewnej klasyfikacji."
        return CaptionResult(text = sentence)
    }
}