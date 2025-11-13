package thesis.wut.application.captionlab.utils.blip

import android.content.Context
import android.util.Log
import org.json.JSONObject

internal class BlipTokenizer(
    context: Context,
    modelPath: String,
    private val clsTokenId: Long = 30522L,
    private val sepTokenId: Long = 102L
) {

    companion object {
        private const val TAG = "BlipTokenizer"
    }

    private val vocab: Map<String, Long>
    private val reverseVocab: Map<Long, String>

    init {
        val vocabJson = context.assets.open("$modelPath/vocab.json")
            .bufferedReader().use { it.readText() }
        val vocabObject = JSONObject(vocabJson)

        val vocabMap = mutableMapOf<String, Long>()
        val reverseMap = mutableMapOf<Long, String>()

        vocabObject.keys().forEach { key ->
            val value = vocabObject.getLong(key)
            vocabMap[key] = value
            reverseMap[value] = key
        }

        vocab = vocabMap
        reverseVocab = reverseMap

        Log.d(TAG, "Loaded vocabulary: ${vocab.size} tokens")
    }

    fun encode(text: String, addCls: Boolean = true): List<Long> {
        val ids = mutableListOf<Long>()

        if (addCls) {
            ids.add(clsTokenId)
        }

        val words = text.lowercase().split("\\s+".toRegex())

        for (word in words) {
            val id = vocab[word]
                ?: vocab["##$word"]
                ?: vocab["[UNK]"]
                ?: clsTokenId

            ids.add(id)
        }

        return ids
    }

    fun decode(ids: List<Long>, skipSpecialTokens: Boolean = true): String {
        val tokens = ids
            .filter { !skipSpecialTokens || !isSpecialToken(it) }
            .mapNotNull { id ->
                reverseVocab[id] ?: run {
                    Log.w(TAG, "Unknown token ID: $id")
                    null
                }
            }

        val result = tokens
            .joinToString(" ")
            .replace(" ##", "")
            .trim()

        return result
    }

    fun getClsTokenId() = clsTokenId
    fun getSepTokenId() = sepTokenId

    private fun isSpecialToken(id: Long): Boolean {
        return id == clsTokenId || id == sepTokenId
    }
}
