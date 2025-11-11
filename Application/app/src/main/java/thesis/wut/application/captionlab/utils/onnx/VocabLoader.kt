package thesis.wut.application.captionlab.utils.onnx

import android.content.Context


object VocabLoader {
    fun load(context: Context, assetPath: String): List<String> =
        context.assets.open(assetPath).bufferedReader().useLines { it.toList() }
}