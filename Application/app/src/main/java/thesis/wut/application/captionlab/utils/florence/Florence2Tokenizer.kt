package thesis.wut.application.captionlab.utils.florence

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * Simple tokenizer for Florence-2 model.
 * Loads vocabulary from tokenizer.json and provides encoding/decoding methods.
 */
internal class Florence2Tokenizer(
    context: Context, 
    tokenizerPath: String,
    private val bosTokenId: Long = 0L,
    private val eosTokenId: Long = 2L,
    private val unkTokenId: Long = 3L
) {

    companion object {
        private const val TAG = "Florence2Tokenizer"
    }

    private val vocab: Map<String, Long>
    private val reverseVocab: Map<Long, String>

    init {
        val tokenizerJson = context.assets.open(tokenizerPath).bufferedReader().use { it.readText() }
        val json = JSONObject(tokenizerJson)

        // Load base vocab
        val vocabJson = json.getJSONObject("model").getJSONObject("vocab")
        val vocabMap = mutableMapOf<String, Long>()
        val reverseMap = mutableMapOf<Long, String>()

        vocabJson.keys().forEach { key ->
            val value = vocabJson.getLong(key)
            vocabMap[key] = value
            reverseMap[value] = key
        }

        // Load added tokens
        val addedTokensArray = json.getJSONArray("added_tokens")
        for (i in 0 until addedTokensArray.length()) {
            val tokenObj = addedTokensArray.getJSONObject(i)
            val content = tokenObj.getString("content")
            val id = tokenObj.getLong("id")
            vocabMap[content] = id
            reverseMap[id] = content
        }

        vocab = vocabMap
        reverseVocab = reverseMap
    }

    fun encode(text: String): List<Long> {
        val ids = mutableListOf(bosTokenId)

        // Simple word-level tokenization
        val words = text.split(" ")

        for ((index, word) in words.withIndex()) {
            // Determine initial search term
            val wordToFind = if (index == 0) {
                // First word: try as-is, lowercase, capitalized
                word
            } else {
                // Subsequent words: add GPT-2 style space prefix
                "Ġ${word.lowercase()}"
            }

            // Try different variations (matching original fallback chain)
            val id = vocab[wordToFind]
                ?: vocab[word.lowercase()]
                ?: vocab[word]
                    ?: vocab["Ġ${word}"]
                    ?: vocab["Ġ${word.lowercase()}"]
                    ?: run {
                        Log.w(TAG, "Word '$word' (tried '$wordToFind') not in vocab, using UNK")
                        unkTokenId
                    }

            ids.add(id)
        }

        return ids
    }

    fun decode(ids: List<Long>, skipSpecialTokens: Boolean = false): String {
        val tokens = ids
            .filter { !skipSpecialTokens || !isSpecialToken(it) }
            .mapNotNull { reverseVocab[it] }

        // Match original implementation exactly
        val result = tokens
            .joinToString(" ")   // WITH separator (matching original)
            .replace(" ##", "")  // Handle BERT-style subwords
            .replace("Ġ", " ")   // Replace GPT-2 style space marker
            .trim()

        return result
    }

    fun getBosTokenId() = bosTokenId
    fun getEosTokenId() = eosTokenId

    private fun isSpecialToken(id: Long): Boolean {
        return id == bosTokenId || id == eosTokenId || id == 1L
    }
}